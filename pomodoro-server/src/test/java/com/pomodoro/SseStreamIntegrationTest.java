package com.pomodoro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSE 流式接口集成测试。
 * <p>
 * 用 {@code RANDOM_PORT} 起真实 Tomcat，而不是 MockMvc：SSE 的写与 complete 发生在
 * 线程池线程上，MockMvc 的 mock response 不能可靠反映「事件帧已刷出、连接已关闭」，
 * 只有真实 HTTP 才能验证前端 fetch 读流拿到的东西。
 * <p>
 * 同理，本类<b>不能</b>加 {@code @Transactional}（请求由容器线程处理，看不到测试线程的
 * 事务），每个用例创建独立用户，结束后通过「注销账号」接口物理清理。
 * 未配置 DeepSeek Key，走的是降级路径——这也正是前端在没 key 时实际遇到的链路。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "deepseek.api-key=your-deepseek-api-key")
class SseStreamIntegrationTest {

    private static final String STREAM_URI = "/api/ai/weekly-report/stream";
    private static final String DEGRADE_MESSAGE = "AI 分析未配置，暂不可用";
    private static final int SUCCESS = 200;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private RestTemplate rest;
    private String username;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        // 不做错误抛出：本项目所有业务错误都是 HTTP 200 + body.code，
        // 万一出现 4xx/5xx（如内容协商失败）也要能拿到状态码看清原因
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        rest = new RestTemplate(factory);
        rest.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });

        username = "it_sse_" + System.nanoTime();
        postJson("/api/auth/register", Map.of("username", username, "password", "123456"), null);
        JsonNode login = postJson("/api/auth/login", Map.of("username", username, "password", "123456"), null);
        token = login.path("data").path("accessToken").asText();
        assertTrue(!token.isEmpty());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (token != null) {
            postJson("/api/user/account/delete", Map.of("password", "123456"), token);
        }
    }

    /**
     * 未配置 Key 时的降级契约：仍然是一条正常的 SSE 流——
     * 200 + text/event-stream + 恰好一帧 data + 正常结束（不能挂住连接）。
     */
    @Test
    void streamDegradesToSingleEventAndCloses() throws Exception {
        long begin = System.nanoTime();
        ResponseEntity<byte[]> response = streamRaw(STREAM_URI, token, null);
        long elapsedMs = (System.nanoTime() - begin) / 1_000_000;

        assertEquals(SUCCESS, response.getStatusCode().value());
        String contentType = String.valueOf(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(contentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE),
                "SSE 响应必须是 text/event-stream，实际: " + contentType);

        // 按原始字节判编码：前端 fetch 固定用 UTF-8 解 SSE，服务端写成别的编码就是问号
        byte[] raw = response.getBody();
        assertNotNull(raw);
        String body = new String(raw, StandardCharsets.UTF_8);
        List<String> events = sseEvents(body);
        assertEquals(1, events.size(), "降级路径应只发一帧，原始字节: " + hex(raw));
        assertTrue(events.get(0).startsWith("data:"), "帧应为 data 行，实际: " + events.get(0));
        assertTrue(events.get(0).contains(DEGRADE_MESSAGE),
                "帧内容应是降级提示。UTF-8 解码结果: " + events.get(0) + " / 原始字节: " + hex(raw));
        assertTrue(elapsedMs < 20_000, "降级应立即结束流，耗时 " + elapsedMs + "ms");
    }

    /**
     * 帧里的中文必须是 UTF-8 字节：SSE 规范只认 UTF-8，
     * 若响应头不带 charset 导致写出端用别的编码，中文会变成 '?'，前端流式周报全程乱码。
     */
    @Test
    void streamDeclareCharsetSoChineseSurvives() {
        ResponseEntity<byte[]> response = streamRaw(STREAM_URI, token, null);

        // 先验字节：UTF-8 解码能还原中文，就说明写出端用的是 UTF-8（被替换成 '?' 时必然匹配不上）
        byte[] raw = response.getBody();
        assertTrue(new String(raw, StandardCharsets.UTF_8).contains(DEGRADE_MESSAGE),
                "中文应以 UTF-8 字节出现在帧里，原始字节: " + hex(raw));

        String contentType = String.valueOf(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(contentType.toLowerCase().contains("utf-8"),
                "text/event-stream 应显式声明 charset=UTF-8，实际: " + contentType);
    }

    /**
     * 前端 api/ai.js 发的是不带参数的 {@code Accept: text/event-stream}（fetch 读流）。
     * 端点上的 produces 一旦带参数（例如为了修中文而写 charset=UTF-8），这种请求就可能被
     * 内容协商判 406，整条流式周报直接不可用——所以按前端真实请求头固定回归一次，
     * 防止以后有人「顺手」把 charset 加回 produces。
     */
    @Test
    void streamAcceptsTheExactAcceptHeaderTheFrontendSends() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.parseMediaType(MediaType.TEXT_EVENT_STREAM_VALUE)));
        headers.setBearerAuth(token);
        ResponseEntity<byte[]> response = rest.exchange(
                new RequestEntity<>(headers, HttpMethod.GET, uri(STREAM_URI)), byte[].class);

        assertEquals(SUCCESS, response.getStatusCode().value(),
                "前端真实 Accept 头不得触发 406，实际响应头: " + response.getHeaders());
        assertTrue(new String(response.getBody(), StandardCharsets.UTF_8).contains(DEGRADE_MESSAGE));
    }

    /**
     * 每个响应都带 traceId（前端报障时能对齐日志），SSE 也不例外。
     */
    @Test
    void streamResponseCarriesTraceId() {
        ResponseEntity<String> response = rest.exchange(
                RequestEntity.get(uri(STREAM_URI))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Trace-Id", "sse-trace-000001")
                        .accept(MediaType.ALL)
                        .build(),
                String.class);

        assertEquals(SUCCESS, response.getStatusCode().value());
        assertEquals("sse-trace-000001", response.getHeaders().getFirst("X-Trace-Id"),
                "上游 traceId 应原样沿用，且必须在暴露头里");
    }

    /**
     * 未登录 / 坏 token：必须回业务 401（JSON），
     * 不能被端点上的 produces=text/event-stream 拖进 406 内容协商失败。
     */
    @Test
    void streamRejectsMissingAndMalformedTokenAsUnauthorized() throws Exception {
        JsonNode anonymous = getJson(STREAM_URI, null);
        assertEquals(401, anonymous.path("code").asInt(), "无 token 应 401，实际: " + anonymous);

        JsonNode garbage = getJson(STREAM_URI, "not-a-jwt");
        assertEquals(401, garbage.path("code").asInt(), "坏 token 应 401，实际: " + garbage);

        // refresh token 不能当 access token 用
        String refreshToken = postJson("/api/auth/login",
                Map.of("username", username, "password", "123456"), null)
                .path("data").path("refreshToken").asText();
        JsonNode wrongType = getJson(STREAM_URI, refreshToken);
        assertEquals(401, wrongType.path("code").asInt(), "refresh token 应被拒，实际: " + wrongType);
    }

    /**
     * 非法 weekStart 不能进入流式处理，应在参数绑定阶段就回 400。
     */
    @Test
    void streamRejectsMalformedWeekStart() throws Exception {
        JsonNode bad = getJson(STREAM_URI + "?weekStart=2026-13-45", token);
        assertEquals(400, bad.path("code").asInt(), "非法日期应 400，实际: " + bad);
    }

    /**
     * 两个流并发：异步线程池不能把第二条请求饿死，两条都要各自正常结束。
     */
    @Test
    void twoConcurrentStreamsBothComplete() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit((Callable<String>) () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    ResponseEntity<byte[]> response = streamRaw(STREAM_URI, token, null);
                    assertEquals(SUCCESS, response.getStatusCode().value());
                    return new String(response.getBody(), StandardCharsets.UTF_8);
                }));
            }
            for (Future<String> future : futures) {
                String body = future.get(30, TimeUnit.SECONDS);
                assertTrue(body.contains(DEGRADE_MESSAGE), "并发流应各自拿到完整降级帧: " + body);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** 取 SSE 原始响应（byte[]，编码判定必须看字节） */
    private ResponseEntity<byte[]> streamRaw(String path, String authToken, String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.ALL));
        if (authToken != null) {
            headers.setBearerAuth(authToken);
        }
        if (traceId != null) {
            headers.set("X-Trace-Id", traceId);
        }
        return rest.exchange(new RequestEntity<>(headers, HttpMethod.GET, uri(path)), byte[].class);
    }

    private String hex(byte[] raw) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(raw.length, 96);
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%02x ", raw[i]));
        }
        return sb.append("... 共 ").append(raw.length).append(" 字节").toString();
    }

    /** 按空行切分 SSE 事件帧（兼容 \r\n） */
    private List<String> sseEvents(String body) {
        List<String> events = new ArrayList<>();
        for (String block : body.replace("\r\n", "\n").split("\n\n")) {
            if (!block.isBlank()) {
                events.add(block.trim());
            }
        }
        return events;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private JsonNode postJson(String path, Object body, String authToken) throws Exception {
        return objectMapper.readTree(send(path, "POST", body, authToken).getBody());
    }

    private JsonNode getJson(String path, String authToken) throws Exception {
        return objectMapper.readTree(send(path, "GET", null, authToken).getBody());
    }

    private ResponseEntity<String> send(String path, String method, Object body, String authToken) {
        // 直接构造 RequestEntity，绕开 Builder 链的类型推导
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.ALL));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (authToken != null) {
            headers.setBearerAuth(authToken);
        }
        RequestEntity<Object> entity = new RequestEntity<>(body, headers,
                HttpMethod.valueOf(method), uri(path));
        return rest.exchange(entity, String.class);
    }
}
