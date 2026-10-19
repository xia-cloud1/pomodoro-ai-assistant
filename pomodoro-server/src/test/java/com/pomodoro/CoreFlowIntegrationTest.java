package com.pomodoro;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.mapper.PomodoroRecordMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 核心链路集成测试：真实 Spring 上下文 + MockMvc + MySQL。
 * 每个测试方法运行在事务中，结束后回滚，不污染数据库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoreFlowIntegrationTest {

    private static final int SUCCESS = 200;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PomodoroRecordMapper pomodoroRecordMapper;

    private String username;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        username = "it_" + System.nanoTime();
        postJson("/api/auth/register", Map.of("username", username, "password", "123456"), null);
        JsonNode login = postJson("/api/auth/login", Map.of("username", username, "password", "123456"), null);
        assertEquals(SUCCESS, login.path("code").asInt());
        token = login.path("data").path("accessToken").asText();
        assertTrue(!token.isEmpty());
    }

    @Test
    void authFlow_duplicateRegisterWrongPasswordAndRefresh() throws Exception {
        JsonNode duplicate = postJson("/api/auth/register", Map.of("username", username, "password", "123456"), null);
        assertEquals(409, duplicate.path("code").asInt());

        JsonNode wrong = postJson("/api/auth/login", Map.of("username", username, "password", "bad"), null);
        assertEquals(401, wrong.path("code").asInt());

        JsonNode noToken = getJson("/api/goal", null);
        assertEquals(401, noToken.path("code").asInt());

        JsonNode login = postJson("/api/auth/login", Map.of("username", username, "password", "123456"), null);
        String refreshToken = login.path("data").path("refreshToken").asText();
        JsonNode refreshed = postJson("/api/auth/refresh", Map.of("refreshToken", refreshToken), null);
        assertEquals(SUCCESS, refreshed.path("code").asInt());
        assertTrue(refreshed.path("data").path("accessToken").asText().length() > 0);
    }

    @Test
    void taskTagSubtaskAndCheckinFlow() throws Exception {
        // 标签：创建 -> 绑定 -> 按标签过滤
        JsonNode tag = postJson("/api/tag", Map.of("name", "集成测试标签", "color", "#123456"), token);
        assertEquals(SUCCESS, tag.path("code").asInt());
        long tagId = tag.path("data").path("id").asLong();

        JsonNode task = postJson("/api/task", Map.of("title", "集成测试任务"), token);
        assertEquals(SUCCESS, task.path("code").asInt());
        long taskId = task.path("data").path("id").asLong();

        JsonNode bound = putJson("/api/task/" + taskId + "/tags", Map.of("tagIds", new long[]{tagId}), token);
        assertEquals(SUCCESS, bound.path("code").asInt());

        JsonNode byTag = getJson("/api/task/page?pageNum=1&pageSize=10&tagId=" + tagId, token);
        assertEquals(1, byTag.path("data").path("total").asInt());

        // 重复任务打卡：首次成功，重复 409
        JsonNode repeat = postJson("/api/task", Map.of("title", "每日复盘", "repeatType", "DAILY"), token);
        long repeatId = repeat.path("data").path("id").asLong();
        JsonNode checkin = postJson("/api/task/" + repeatId + "/checkin", null, token);
        assertEquals(SUCCESS, checkin.path("code").asInt());
        assertEquals(1, checkin.path("data").path("streakCount").asInt());
        JsonNode duplicate = postJson("/api/task/" + repeatId + "/checkin", null, token);
        assertEquals(409, duplicate.path("code").asInt());

        JsonNode notRepeat = postJson("/api/task/" + taskId + "/checkin", null, token);
        assertEquals(400, notRepeat.path("code").asInt());

        // 父子任务：children 返回子任务，topLevel 不返回子任务
        JsonNode parent = postJson("/api/task", Map.of("title", "毕业论文"), token);
        long parentId = parent.path("data").path("id").asLong();
        JsonNode child = postJson("/api/task", Map.of("title", "写开题报告", "parentId", parentId), token);
        long childId = child.path("data").path("id").asLong();
        assertEquals(parentId, child.path("data").path("parentId").asLong());

        JsonNode children = getJson("/api/task/" + parentId + "/children", token);
        assertEquals(1, children.path("data").size());
        assertEquals("写开题报告", children.path("data").get(0).path("title").asText());

        JsonNode topLevel = getJson("/api/task/page?pageNum=1&pageSize=50&topLevel=true", token);
        boolean containsChild = false;
        for (JsonNode item : topLevel.path("data").path("records")) {
            if (item.path("id").asLong() == childId) containsChild = true;
        }
        assertTrue(!containsChild, "子任务不应出现在顶层列表");
    }

    @Test
    void pomodoroCompleteGoalStatPointsAndReportFlow() throws Exception {
        long taskId = postJson("/api/task", Map.of("title", "写周报"), token)
                .path("data").path("id").asLong();

        JsonNode goal = putJson("/api/goal", Map.of("targetPomodoros", 2), token);
        assertEquals(SUCCESS, goal.path("code").asInt());

        // 开始番茄（绑定任务）
        JsonNode start = postJson("/api/pomodoro/start", Map.of("taskId", taskId), token);
        assertEquals(SUCCESS, start.path("code").asInt());
        long recordId = start.path("data").path("id").asLong();

        // 把开始时间改到 120 秒前，绕过「完成需 ≥30 秒」的真实等待。
        // 注意 DATETIME(0) 会把小数秒四舍五入，只回拨 60 秒可能落成 59 秒，故留出余量。
        pomodoroRecordMapper.update(null, new LambdaUpdateWrapper<PomodoroRecord>()
                .eq(PomodoroRecord::getId, recordId)
                .set(PomodoroRecord::getStartTime, LocalDateTime.now().withNano(0).minusSeconds(120)));

        JsonNode complete = postJson("/api/pomodoro/" + recordId + "/complete", null, token);
        assertEquals(SUCCESS, complete.path("code").asInt());
        assertEquals("COMPLETED", complete.path("data").path("status").asText());
        assertTrue(complete.path("data").path("duration").asInt() >= 60);

        // 任务实际番茄数 +1
        JsonNode taskDetail = getJson("/api/task/" + taskId, token);
        assertEquals(1, taskDetail.path("data").path("actualPomodoros").asInt());

        // 周目标进度实时计算
        JsonNode goalNow = getJson("/api/goal", token);
        assertEquals(1, goalNow.path("data").path("achievedPomodoros").asInt());
        assertEquals(50, goalNow.path("data").path("progressPercent").asInt());

        // 日统计
        JsonNode daily = getJson("/api/daily-stat?date=" + LocalDate.now(), token);
        assertEquals(1, daily.path("data").path("completedCount").asInt());

        // 番茄历史分页
        JsonNode history = getJson("/api/pomodoro/page?pageNum=1&pageSize=10", token);
        assertEquals(1, history.path("data").path("total").asInt());
        assertEquals("COMPLETED", history.path("data").path("records").get(0).path("status").asText());

        // 积分：完成番茄 +10，首次番茄成就 +20
        JsonNode points = getJson("/api/points/summary", token);
        assertEquals(30, points.path("data").path("points").asInt());

        // 月报表
        LocalDate now = LocalDate.now();
        JsonNode report = getJson("/api/stat/report?year=" + now.getYear() + "&month=" + now.getMonthValue(), token);
        assertEquals(SUCCESS, report.path("code").asInt());
        assertEquals(1, report.path("data").path("completedCount").asInt());
        assertEquals(now.toString(), report.path("data").path("bestDay").path("date").asText());

        // CSV 导出：UTF-8 BOM + 任务标题
        byte[] csv = mockMvc.perform(get("/api/export/pomodoro")
                        .param("start", now.toString())
                        .param("end", now.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals((byte) 0xEF, csv[0]);
        assertEquals((byte) 0xBB, csv[1]);
        assertEquals((byte) 0xBF, csv[2]);
        String csvText = new String(csv, StandardCharsets.UTF_8);
        assertTrue(csvText.contains("写周报"));
    }

    @Test
    void runningPomodoroCanBeFoundInterruptedAndCrossUserIsolated() throws Exception {
        JsonNode start = postJson("/api/pomodoro/start", Map.of(), token);
        assertEquals(SUCCESS, start.path("code").asInt());
        long recordId = start.path("data").path("id").asLong();

        // 同一用户重复开始 → 409
        JsonNode again = postJson("/api/pomodoro/start", Map.of(), token);
        assertEquals(409, again.path("code").asInt());

        // 刷新/切页后靠这个查询恢复进行中状态
        JsonNode running = getJson("/api/pomodoro/page?pageNum=1&pageSize=1&status=RUNNING", token);
        assertEquals(1, running.path("data").path("total").asInt());
        assertEquals(recordId, running.path("data").path("records").get(0).path("id").asLong());

        JsonNode interrupted = postJson("/api/pomodoro/" + recordId + "/interrupt",
                Map.of("interruptReason", "临时开会"), token);
        assertEquals(SUCCESS, interrupted.path("code").asInt());
        assertEquals("INTERRUPTED", interrupted.path("data").path("status").asText());

        JsonNode runningAfter = getJson("/api/pomodoro/page?pageNum=1&pageSize=1&status=RUNNING", token);
        assertEquals(0, runningAfter.path("data").path("total").asInt());

        // 跨用户隔离：另一个用户看不到/改不了这条记录
        String other = "it_other_" + System.nanoTime();
        postJson("/api/auth/register", Map.of("username", other, "password", "123456"), null);
        JsonNode otherLogin = postJson("/api/auth/login", Map.of("username", other, "password", "123456"), null);
        String otherToken = otherLogin.path("data").path("accessToken").asText();
        assertNotNull(otherToken);

        JsonNode detail = getJson("/api/pomodoro/" + recordId, otherToken);
        assertEquals(404, detail.path("code").asInt());
    }

    private JsonNode postJson(String url, Object body, String authToken) throws Exception {
        MockHttpServletRequestBuilder request = post(url).contentType(MediaType.APPLICATION_JSON);
        if (body != null) request.content(objectMapper.writeValueAsString(body));
        if (authToken != null) request.header("Authorization", "Bearer " + authToken);
        return perform(request);
    }

    private JsonNode putJson(String url, Object body, String authToken) throws Exception {
        MockHttpServletRequestBuilder request = put(url).contentType(MediaType.APPLICATION_JSON);
        if (body != null) request.content(objectMapper.writeValueAsString(body));
        if (authToken != null) request.header("Authorization", "Bearer " + authToken);
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
