package com.pomodoro;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.mapper.PomodoroRecordMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 可观测性集成测试：Actuator 端点、traceId、业务指标自增。
 * <p>
 * 指标注册表不随事务回滚，因此断言统一用「执行前后差值」，避免受测试顺序影响。
 */
@SpringBootTest(properties = "deepseek.api-key=your-deepseek-api-key")
@AutoConfigureMockMvc
@Transactional
class ObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private PomodoroRecordMapper pomodoroRecordMapper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String username = "it_obs_" + System.nanoTime();
        postJson("/api/auth/register", Map.of("username", username, "password", "123456"), null);
        JsonNode login = postJson("/api/auth/login", Map.of("username", username, "password", "123456"), null);
        token = login.path("data").path("accessToken").asText();
        assertTrue(token.length() > 0);
    }

    @Test
    void actuatorExposesHealthInfoAndPrometheus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("pomodoro-server"))
                .andExpect(jsonPath("$.app.version").exists());

        // Prometheus 文本格式：JVM 指标 + 应用标签都应存在
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("application=\"pomodoro-server\"")));
    }

    @Test
    void traceIdIsGeneratedAndUpstreamIdIsReused() throws Exception {
        // 未传时生成 16 位十六进制
        MvcResult generated = mockMvc.perform(get("/actuator/health")).andReturn();
        String generatedId = generated.getResponse().getHeader("X-Trace-Id");
        assertNotNull(generatedId);
        assertTrue(generatedId.matches("[0-9a-f]{16}"), "traceId 应为 16 位十六进制: " + generatedId);

        // 合法上游 ID 原样沿用
        MvcResult reused = mockMvc.perform(get("/actuator/health").header("X-Trace-Id", "upstream-trace-001"))
                .andReturn();
        assertEquals("upstream-trace-001", reused.getResponse().getHeader("X-Trace-Id"));

        // 非法字符（空格）不得进入日志，回退为服务端生成
        MvcResult rejected = mockMvc.perform(get("/actuator/health").header("X-Trace-Id", "bad trace id !"))
                .andReturn();
        String rejectedId = rejected.getResponse().getHeader("X-Trace-Id");
        assertNotNull(rejectedId);
        assertFalse(rejectedId.contains(" "), "非法 traceId 应被拒绝");
        assertTrue(rejectedId.matches("[0-9a-f]{16}"));
    }

    @Test
    void pomodoroLifecycleIncrementsBusinessMetrics() throws Exception {
        double startedBefore = counter("pomodoro.started", "bound_task", "no");
        double interruptedBefore = counter("pomodoro.interrupted");
        double completedBefore = counter("pomodoro.completed");
        double focusCountBefore = timerCount("pomodoro.focus.duration");

        // 启动 -> 中断
        long firstId = postJson("/api/pomodoro/start", Map.of(), token).path("data").path("id").asLong();
        assertEquals(startedBefore + 1, counter("pomodoro.started", "bound_task", "no"), 0.0001);
        JsonNode interrupted = postJson("/api/pomodoro/" + firstId + "/interrupt",
                Map.of("interruptReason", "测试中断"), token);
        assertEquals(200, interrupted.path("code").asInt());
        assertEquals(interruptedBefore + 1, counter("pomodoro.interrupted"), 0.0001);

        // 再启动 -> 回拨开始时间 -> 完成
        long secondId = postJson("/api/pomodoro/start", Map.of(), token).path("data").path("id").asLong();
        pomodoroRecordMapper.update(null, new LambdaUpdateWrapper<PomodoroRecord>()
                .eq(PomodoroRecord::getId, secondId)
                .set(PomodoroRecord::getStartTime, LocalDateTime.now().minusSeconds(120)));
        JsonNode completed = postJson("/api/pomodoro/" + secondId + "/complete", null, token);
        assertEquals(200, completed.path("code").asInt());
        assertEquals(completedBefore + 1, counter("pomodoro.completed"), 0.0001);
        assertEquals(focusCountBefore + 1, timerCount("pomodoro.focus.duration"), 0.0001);
    }

    @Test
    void aiDegradationAndCrossCuttingMetricsAreRecorded() throws Exception {
        // AI 未配置 Key -> 降级计数
        double degradedBefore = counter("ai.degraded", "feature", "weekly", "reason", "not_configured");
        assertEquals(200, getJson("/api/ai/weekly-report", token).path("code").asInt());
        assertEquals(degradedBefore + 1,
                counter("ai.degraded", "feature", "weekly", "reason", "not_configured"), 0.0001);

        // 幂等窗口内重复打卡 -> 幂等拦截计数
        double idempotentBefore = counter("idempotent.rejected", "method", "checkIn");
        assertEquals(200, postJson("/api/checkin", null, token).path("code").asInt());
        assertEquals(409, postJson("/api/checkin", null, token).path("code").asInt());
        assertEquals(idempotentBefore + 1,
                counter("idempotent.rejected", "method", "checkIn"), 0.0001);

        // 独立 IP 第 11 次登录 -> 限流计数
        String ip = "10.9.9." + (System.nanoTime() % 200 + 1);
        double rateLimitBefore = counter("rate.limit.triggered", "method", "login");
        for (int i = 0; i < 10; i++) {
            postJsonFromIp("/api/auth/login",
                    Map.of("username", "nobody", "password", "wrong" + i), null, ip);
        }
        JsonNode blocked = postJsonFromIp("/api/auth/login",
                Map.of("username", "nobody", "password", "wrong"), null, ip);
        assertEquals(429, blocked.path("code").asInt());
        assertEquals(rateLimitBefore + 1,
                counter("rate.limit.triggered", "method", "login"), 0.0001);
    }

    private double counter(String name, String... tags) {
        Counter counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private double timerCount(String name) {
        Timer timer = meterRegistry.find(name).timer();
        return timer == null ? 0 : timer.count();
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
