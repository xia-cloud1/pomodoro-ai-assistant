package com.pomodoro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 反向代理后的客户端 IP 归属（生产 profile 打开 {@code server.forward-headers-strategy} 的前提）。
 * <p>
 * 背景：登录限流按 {@code request.getRemoteAddr()} 分桶。应用部署在 Nginx 之后时，
 * 直连应用的只有代理，若不解析 {@code X-Forwarded-For}，所有用户的 remoteAddr 都是
 * 同一个容器 IP —— 限流会退化成「全站共享 10 次/分钟」，正常用户也会被 429 挡住。
 * <p>
 * 用真实 HTTP（RANDOM_PORT）而不是 MockMvc：这里验证的正是 Tomcat + Spring 对代理头的处理，
 * 必须让请求真正过一遍 servlet 容器。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "deepseek.api-key=your-deepseek-api-key",
                "server.forward-headers-strategy=framework"
        })
class ProxyForwardingIntegrationTest {

    /** RFC 5737 文档保留地址段，不会与真实网段冲突 */
    private static final String CLIENT_A = "203.0.113.7";
    private static final String CLIENT_B = "203.0.113.8";
    private static final int LOGIN_LIMIT = 10;

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rateLimitBucketsByForwardedClientIp() throws Exception {
        String username = "it_xff_" + System.nanoTime();
        post("/api/auth/register", Map.of("username", username, "password", "123456"), null, null);

        try {
            // 同一个「客户端」连打 LOGIN_LIMIT 次，应全部计入同一桶
            for (int i = 1; i <= LOGIN_LIMIT; i++) {
                JsonNode attempt = login(username, CLIENT_A);
                assertEquals(200, attempt.path("code").asInt(),
                        "第 " + i + " 次登录不该被限流，实际: " + attempt);
            }

            JsonNode overLimit = login(username, CLIENT_A);
            assertEquals(429, overLimit.path("code").asInt(),
                    "同一客户端第 " + (LOGIN_LIMIT + 1) + " 次应被限流，实际: " + overLimit);

            // 换一个客户端 IP 必须拿到独立配额：说明分桶用的是代理传来的真实 IP，
            // 而不是「所有请求都来自 127.0.0.1」——后者会让第 11 个用户起全站 429
            JsonNode otherClient = login(username, CLIENT_B);
            assertEquals(200, otherClient.path("code").asInt(),
                    "另一客户端不应受前一 IP 的计数影响，实际: " + otherClient);

            // 本用例不走事务（真实 HTTP + 限流计数不随事务回滚），结束时物理清理，
            // 否则每次跑测试都会在库里留一个用户
            cleanup(username, otherClient.path("data").path("accessToken").asText());
        } finally {
            // 即使断言失败也尽量清理；清理本身失败不应掩盖原始断言错误
            cleanupQuietly(username);
        }
    }

    private void cleanup(String username, String token) throws Exception {
        JsonNode deleted = post("/api/user/account/delete", Map.of("password", "123456"), token, null);
        assertEquals(200, deleted.path("code").asInt(), "注销测试用户应成功，实际: " + deleted);
    }

    private void cleanupQuietly(String username) {
        try {
            // 用例中途失败时配额可能已耗尽，此时只能靠新 IP 登录后再注销
            JsonNode retry = login(username, "203.0.113.99");
            if (retry.path("code").asInt() == 200) {
                cleanup(username, retry.path("data").path("accessToken").asText());
            }
        } catch (Exception ignored) {
            // 清理失败不影响用例结论；集成测试用户带 it_ 前缀，必要时可手工清理
        }
    }

    private JsonNode login(String username, String clientIp) throws Exception {
        return post("/api/auth/login", Map.of("username", username, "password", "123456"), null, clientIp);
    }

    private JsonNode post(String path, Object body, String token, String clientIp) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (clientIp != null) {
            // 与真实代理一致：最左是客户端真实 IP，后面是代理自身地址
            headers.set("X-Forwarded-For", clientIp + ", 172.18.0.5");
        }
        RequestEntity<Object> request = new RequestEntity<>(body, headers, HttpMethod.POST,
                URI.create("http://localhost:" + port + path));
        ResponseEntity<String> response = rest.exchange(request, String.class);
        return objectMapper.readTree(response.getBody());
    }
}