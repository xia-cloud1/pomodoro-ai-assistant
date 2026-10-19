package com.pomodoro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 降级与横切能力（限流 / 幂等）集成测试。
 * 通过 properties 强制把 DeepSeek key 设为占位值，确保测试不访问外网。
 */
@SpringBootTest(properties = "deepseek.api-key=your-deepseek-api-key")
@AutoConfigureMockMvc
@Transactional
class AiAndAspectIntegrationTest {

    private static final int SUCCESS = 200;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String username;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        username = "it_ai_" + System.nanoTime();
        postJson("/api/auth/register", Map.of("username", username, "password", "123456"), null);
        JsonNode login = postJson("/api/auth/login", Map.of("username", username, "password", "123456"), null);
        token = login.path("data").path("accessToken").asText();
        assertTrue(token.length() > 0);
    }

    /**
     * 未配置 DeepSeek Key 时，所有 AI 接口都应正常返回结构化数据，AI 文本字段为 null（JSON 中被省略）。
     */
    @Test
    void aiEndpointsDegradeWhenKeyMissing() throws Exception {
        // 这些接口未登录时统一 401，避免 AI 接口被匿名调用
        assertEquals(401, getJson("/api/ai/weekly-report", null).path("code").asInt());

        JsonNode weekly = getJson("/api/ai/weekly-report", token);
        assertEquals(SUCCESS, weekly.path("code").asInt());
        assertEquals(0, weekly.path("data").path("completedCount").asInt());
        assertEquals(7, weekly.path("data").path("dailyBreakdown").size());
        assertFalse(weekly.path("data").has("aiAnalysis"), "未配置 Key 时不应返回 AI 文本");

        JsonNode daily = getJson("/api/ai/daily-report", token);
        assertEquals(SUCCESS, daily.path("code").asInt());
        assertFalse(daily.path("data").has("aiAnalysis"));

        JsonNode monthly = getJson("/api/ai/monthly-report", token);
        assertEquals(SUCCESS, monthly.path("code").asInt());
        assertTrue(monthly.path("data").path("summary").isObject());
        assertFalse(monthly.path("data").has("aiAnalysis"));

        JsonNode ask = postJson("/api/ai/ask", Map.of("question", "我这周效率怎么样"), token);
        assertEquals(SUCCESS, ask.path("code").asInt());
        assertEquals("我这周效率怎么样", ask.path("data").path("question").asText());
        assertFalse(ask.path("data").has("answer"));

        JsonNode split = postJson("/api/ai/split-task", Map.of("title", "写毕业论文"), token);
        assertEquals(SUCCESS, split.path("code").asInt());
        assertEquals(0, split.path("data").path("subtasks").size());

        JsonNode golden = getJson("/api/ai/golden-hours", token);
        assertEquals(SUCCESS, golden.path("code").asInt());
        assertEquals(0, golden.path("data").path("periods").size());
        assertFalse(golden.path("data").has("aiSuggestion"));
    }

    /**
     * 登录限流：同一 IP 60 秒内第 11 次登录返回业务码 429。
     * 每次使用独立 IP，避免与其它测试共享内存计数。
     */
    @Test
    void loginIsRateLimitedPerClientIp() throws Exception {
        String ip = "10.0.0." + (System.nanoTime() % 200 + 1);
        for (int i = 0; i < 10; i++) {
            JsonNode res = postJsonFromIp("/api/auth/login",
                    Map.of("username", username, "password", "wrong-" + i), null, ip);
            assertEquals(401, res.path("code").asInt(), "第 " + (i + 1) + " 次应正常进入业务校验");
        }
        JsonNode blocked = postJsonFromIp("/api/auth/login",
                Map.of("username", username, "password", "123456"), null, ip);
        assertEquals(429, blocked.path("code").asInt());
    }

    /**
     * 幂等：5 秒窗口内重复调用每日打卡被切面拦截（业务层「今天已打卡」不会先执行）；
     * 幂等 key 带 userId，另一个用户同期打卡不应被误伤。
     */
    @Test
    void duplicateCheckinIsRejectedByIdempotentAspect() throws Exception {
        JsonNode first = postJson("/api/checkin", null, token);
        assertEquals(SUCCESS, first.path("code").asInt());

        JsonNode duplicate = postJson("/api/checkin", null, token);
        assertEquals(409, duplicate.path("code").asInt());
        assertTrue(duplicate.path("message").asText().contains("重复提交"),
                "第二次应被幂等切面拦截，而不是走到业务层");

        // 另一个用户使用相同接口，key 不同，应正常打卡
        String other = "it_ai_other_" + System.nanoTime();
        postJson("/api/auth/register", Map.of("username", other, "password", "123456"), null);
        String otherToken = postJson("/api/auth/login",
                Map.of("username", other, "password", "123456"), null)
                .path("data").path("accessToken").asText();
        JsonNode otherCheckin = postJson("/api/checkin", null, otherToken);
        assertEquals(SUCCESS, otherCheckin.path("code").asInt());
    }

    private JsonNode postJson(String url, Object body, String authToken) throws Exception {
        return postJsonFromIp(url, body, authToken, null);
    }

    private JsonNode postJsonFromIp(String url, Object body, String authToken, String ip) throws Exception {
        MockHttpServletRequestBuilder request = post(url).contentType(MediaType.APPLICATION_JSON);
        if (body != null) request.content(objectMapper.writeValueAsString(body));
        if (authToken != null) request.header("Authorization", "Bearer " + authToken);
        if (ip != null) request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
        return perform(request);
    }

    private JsonNode getJson(String url, String authToken) throws Exception {
        MockHttpServletRequestBuilder request = get(url);
        if (authToken != null) request.header("Authorization", "Bearer " + authToken);
        return perform(request);
    }

    private JsonNode perform(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
