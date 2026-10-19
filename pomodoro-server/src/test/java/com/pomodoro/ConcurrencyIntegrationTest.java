package com.pomodoro;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pomodoro.entity.CheckIn;
import com.pomodoro.entity.PointsLog;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.mapper.CheckInMapper;
import com.pomodoro.mapper.PointsLogMapper;
import com.pomodoro.mapper.PomodoroRecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 并发场景集成测试：验证「同一用户只能有一个进行中番茄」在并发下仍成立，
 * 以及每日打卡在并发下只产生一条记录。
 * <p>
 * 注意：本类<b>不能</b>加 @Transactional——并发请求需要各自提交事务并互相可见；
 * 因此每个用例创建独立用户，结束后通过「注销账号」物理清理全部数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrencyIntegrationTest {

    private static final int SUCCESS = 200;
    private static final int CONFLICT = 409;
    private static final int THREADS = 8;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PomodoroRecordMapper pomodoroRecordMapper;

    @Autowired
    private CheckInMapper checkInMapper;

    @Autowired
    private PointsLogMapper pointsLogMapper;

    private Long userId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String username = "it_cc_" + System.nanoTime();
        // 每次使用独立 IP，避免登录限流计数在用例间累积
        String ip = "10.1." + (System.nanoTime() % 250 + 1) + ".1";
        RequestPostProcessor fromIp = r -> {
            r.setRemoteAddr(ip);
            return r;
        };
        postJson("/api/auth/register", Map.of("username", username, "password", "123456"), null, fromIp);
        JsonNode login = postJson("/api/auth/login",
                Map.of("username", username, "password", "123456"), null, fromIp);
        assertEquals(SUCCESS, login.path("code").asInt());
        userId = login.path("data").path("user").path("id").asLong();
        token = login.path("data").path("accessToken").asText();
        assertNotNull(token);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (token == null) {
            return;
        }
        // 通过真实业务接口物理清理用户及其全部数据（含测试中创建的 RUNNING 记录）
        postJson("/api/user/account/delete", Map.of("password", "123456"), token, null);
    }

    /**
     * 8 个线程同时开始番茄：只允许 1 个成功，其余全部 409，数据库只有 1 条 RUNNING。
     * 仅靠「先 selectCount 再 insert」存在竞态窗口，必须依赖数据库唯一约束兜底。
     */
    @Test
    void concurrentStartOnlyOneRunningRecordIsCreated() throws Exception {
        List<Integer> codes = runConcurrently(() -> postJson("/api/pomodoro/start", Map.of(), token, null));

        assertEquals(1, countCodes(codes, SUCCESS), "并发开始应只有 1 个成功，实际: " + codes);
        assertEquals(THREADS - 1, countCodes(codes, CONFLICT), "其余请求应返回 409，实际: " + codes);

        Long running = pomodoroRecordMapper.selectCount(
                new LambdaQueryWrapper<PomodoroRecord>()
                        .eq(PomodoroRecord::getUserId, userId)
                        .eq(PomodoroRecord::getStatus, "RUNNING"));
        assertEquals(1L, running, "数据库应只有一条进行中的番茄记录");
    }

    /**
     * 8 个线程同时每日打卡：幂等切面 + 唯一约束兜底，只产生 1 条打卡和 1 条打卡积分流水。
     */
    @Test
    void concurrentCheckinOnlyCreatesOneRecord() throws Exception {
        List<Integer> codes = runConcurrently(() -> postJson("/api/checkin", null, token, null));

        assertEquals(1, countCodes(codes, SUCCESS), "并发打卡应只有 1 个成功，实际: " + codes);
        assertEquals(THREADS - 1, countCodes(codes, CONFLICT), "其余请求应返回 409，实际: " + codes);

        Long checkins = checkInMapper.selectCount(
                new LambdaQueryWrapper<CheckIn>().eq(CheckIn::getUserId, userId));
        assertEquals(1L, checkins, "数据库应只有一条打卡记录");

        Long checkinPoints = pointsLogMapper.selectCount(
                new LambdaQueryWrapper<PointsLog>()
                        .eq(PointsLog::getUserId, userId)
                        .eq(PointsLog::getPointsType, "CHECKIN"));
        assertEquals(1L, checkinPoints, "打卡积分只应发放一次");

        JsonNode status = getJson("/api/checkin/status", token);
        assertTrue(status.path("data").path("checkedToday").asBoolean());
    }

    /** 用 CyclicBarrier 让所有线程尽量同一时刻发起请求 */
    private List<Integer> runConcurrently(Callable<JsonNode> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return action.call().path("code").asInt();
                }));
            }
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> future : futures) {
                codes.add(future.get(30, TimeUnit.SECONDS));
            }
            return codes;
        } finally {
            pool.shutdownNow();
        }
    }

    private long countCodes(List<Integer> codes, int code) {
        return codes.stream().filter(c -> c == code).count();
    }

    private JsonNode postJson(String url, Object body, String authToken, RequestPostProcessor postProcessor) throws Exception {
        MockHttpServletRequestBuilder request = post(url).contentType(MediaType.APPLICATION_JSON);
        if (body != null) request.content(objectMapper.writeValueAsString(body));
        if (authToken != null) request.header("Authorization", "Bearer " + authToken);
        if (postProcessor != null) request.with(postProcessor);
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
