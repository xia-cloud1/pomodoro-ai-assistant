package com.pomodoro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pomodoro.common.FocusScoreUtil;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统计口径与日期边界集成测试（README「测试完善」第 8 项）。
 * <p>
 * 覆盖三类容易出错的地方：
 * ① 归属——记录按 start_time 归到哪天/哪月/哪周（跨天、跨月、跨周边界）；
 * ② 口径——RUNNING 不计入任何统计、INTERRUPTED 计入日统计但不计入热力图/时段分布；
 * ③ 边界——月天数（闰年/世纪年）、跨度上限、参数校验、空数据必须补 0 而不是缺字段。
 * <p>
 * 造数据用 mapper 直接插记录 + 调 recalc 接口聚合：完成/中断接口由服务端时钟算时长，
 * 无法造出「昨天 23:50 开始、今天 00:10 结束」这类记录（写入侧边界见最后一个用例）。
 */
@SpringBootTest(properties = "deepseek.api-key=your-deepseek-api-key")
@AutoConfigureMockMvc
@Transactional
class StatBoundaryIntegrationTest {

    private static final int SUCCESS = 200;
    private static final String COMPLETED = "COMPLETED";
    private static final String INTERRUPTED = "INTERRUPTED";
    private static final String RUNNING = "RUNNING";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PomodoroRecordMapper pomodoroRecordMapper;

    private Long userId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String username = "it_stat_" + System.nanoTime();
        // 每次用独立 IP 登录：登录限流计数在内存里、按 Spring context 共享，
        // 本类与 AiAndAspectIntegrationTest 的 @SpringBootTest 配置完全一致会复用同一个
        // context，若都用 MockMvc 默认的 127.0.0.1，8 个用例的登录会在 60 秒窗口内累加，
        // 第 11 次起整批在 setUp 就 429 全红。
        String ip = "10.2." + (System.nanoTime() % 250 + 1) + ".1";
        RequestPostProcessor fromIp = r -> {
            r.setRemoteAddr(ip);
            return r;
        };
        postJson("/api/auth/register", Map.of("username", username, "password", "123456"), null, fromIp);
        JsonNode login = postJson("/api/auth/login", Map.of("username", username, "password", "123456"), null, fromIp);
        assertEquals(SUCCESS, login.path("code").asInt());
        userId = login.path("data").path("user").path("id").asLong();
        token = login.path("data").path("accessToken").asText();
        assertTrue(!token.isEmpty());
    }

    /**
     * 跨天：一条 23:50 开始、次日 00:10 结束的记录，所有统计都必须归到「开始那天」，
     * 时段分布的桶也取开始小时（23），否则热力图与日统计会对不上。
     */
    @Test
    void recordIsAttributedToItsStartDateAcrossMidnight() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        insertRecord(yesterday.atTime(23, 50), 1200, COMPLETED, null);
        recalc(yesterday);
        recalc(today);

        JsonNode yesterdayStat = getJson("/api/daily-stat?date=" + yesterday, token).path("data");
        assertEquals(1, yesterdayStat.path("completedCount").asInt());
        assertEquals(0, yesterdayStat.path("interruptedCount").asInt());
        assertEquals(1, yesterdayStat.path("totalPomodoros").asInt());
        assertEquals(20, yesterdayStat.path("totalMinutes").asInt());
        assertEquals(20, yesterdayStat.path("maxFocusMinutes").asInt());

        // 结束时间在今天，但今天不该有任何统计；重算今天也不会把它捞进来
        JsonNode todayStat = getJson("/api/daily-stat?date=" + today, token).path("data");
        assertEquals(0, todayStat.path("totalPomodoros").asInt());
        assertEquals(0, todayStat.path("totalMinutes").asInt());

        JsonNode heatmap = getJson("/api/stat/heatmap?start=" + yesterday + "&end=" + today, token).path("data");
        assertEquals(1, cell(heatmap, yesterday.toString()).path("count").asInt());
        assertEquals(0, cell(heatmap, today.toString()).path("count").asInt());

        JsonNode hours = getJson("/api/stat/hour-distribution?start=" + yesterday + "&end=" + today, token).path("data");
        assertEquals(24, hours.size());
        assertEquals(1, hours.get(23).path("count").asInt(), "应计入开始小时 23 点桶");
        assertEquals(20, hours.get(23).path("minutes").asInt());
        assertEquals(0, hours.get(0).path("count").asInt(), "结束小时的 0 点桶不应有值");

        // 若昨天落在上一个自然月，跨月报表同样归在那个月
        JsonNode report = getJson("/api/stat/report?year=" + yesterday.getYear()
                + "&month=" + yesterday.getMonthValue(), token).path("data");
        assertEquals(1, cell(report.path("dailyBreakdown"), yesterday.toString()).path("completedCount").asInt());
        assertEquals(yesterday.toString(), report.path("bestDay").path("date").asText());
    }

    /**
     * 跨月：月末 23:59 的记录属于本月，不能因为结束时间跨到月初就被下个月捞走；
     * 区间过滤必须是「含首日含末日的左闭右开 +1 天」。
     */
    @Test
    void monthReportSplitsRecordsAtMonthBoundaryByStartTime() throws Exception {
        // 用固定过去的月份，避免依赖运行日期
        LocalDate endOfAug = LocalDate.of(2026, 8, 31);
        LocalDate firstOfSep = LocalDate.of(2026, 9, 1);
        insertRecord(endOfAug.atTime(23, 59), 120, COMPLETED, null);   // 结束于 09-01 00:01
        insertRecord(firstOfSep.atTime(0, 0), 120, COMPLETED, null);   // 恰好是月末后一秒的下一日
        insertRecord(LocalDate.of(2026, 7, 31).atTime(23, 59), 120, COMPLETED, null);

        JsonNode august = getJson("/api/stat/report?year=2026&month=8", token).path("data");
        assertEquals("2026-08-01", august.path("startDate").asText());
        assertEquals("2026-08-31", august.path("endDate").asText());
        assertEquals(31, august.path("dailyBreakdown").size());
        assertEquals(1, august.path("completedCount").asInt(), "8 月只应有 08-31 那一条");
        assertEquals(1, cell(august.path("dailyBreakdown"), "2026-08-31").path("completedCount").asInt());
        assertFalse(hasCell(august.path("dailyBreakdown"), "2026-09-01"), "9 月记录不得出现在 8 月报表");

        JsonNode september = getJson("/api/stat/report?year=2026&month=9", token).path("data");
        assertEquals(1, september.path("completedCount").asInt(), "09-01 00:00 开始的记录属于 9 月");
        assertEquals(1, cell(september.path("dailyBreakdown"), "2026-09-01").path("completedCount").asInt());

        // 热力图/时段分布同样按左闭右开裁剪
        JsonNode heatmap = getJson("/api/stat/heatmap?start=2026-08-31&end=2026-09-01", token).path("data");
        assertEquals(1, cell(heatmap, "2026-08-31").path("count").asInt());
        assertEquals(1, cell(heatmap, "2026-09-01").path("count").asInt());
        assertEquals(2, heatmap.size(), "跨月两天区间应只有两天格子");
    }

    /**
     * 跨周：目标与达成数按周聚合，任意一周内日期都要归一化到周一；
     * 周日 23:xx 的记录属于上一周，周一 00:00 的记录属于本周（边界包含）。
     */
    @Test
    void goalWeekIsNormalizedToMondayAndCountedByWeek() throws Exception {
        LocalDate thisMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate lastSunday = thisMonday.minusDays(1);
        LocalDate lastMonday = thisMonday.minusDays(7);

        insertRecord(lastSunday.atTime(20, 0), 600, COMPLETED, null);
        recalc(lastSunday);

        // 用周日当 weekStart 传进去，应被归一化到该周周一
        JsonNode set = putJson("/api/goal", Map.of("weekStart", lastSunday.toString(), "targetPomodoros", 3), token);
        assertEquals(SUCCESS, set.path("code").asInt());
        assertEquals(lastMonday.toString(), set.path("data").path("weekStart").asText());
        assertEquals(lastSunday.toString(), set.path("data").path("weekEnd").asText());

        JsonNode lastWeek = getJson("/api/goal?weekStart=" + lastMonday, token).path("data");
        assertEquals(3, lastWeek.path("targetPomodoros").asInt());
        assertEquals(1, lastWeek.path("achievedPomodoros").asInt(), "周日记录属于上一周");
        assertEquals(33, lastWeek.path("progressPercent").asInt(), "1/3 四舍五入为 33%");

        JsonNode thisWeek = getJson("/api/goal?weekStart=" + thisMonday, token).path("data");
        assertEquals(0, thisWeek.path("targetPomodoros").asInt(), "本周还没设目标");
        assertEquals(0, thisWeek.path("achievedPomodoros").asInt(), "上周记录不得算进本周");

        // 周一 00:00 整点开始：属于本周（区间下界必须包含）
        insertRecord(thisMonday.atStartOfDay(), 600, COMPLETED, null);
        recalc(thisMonday);
        JsonNode thisWeekNow = getJson("/api/goal?weekStart=" + thisMonday, token).path("data");
        assertEquals(1, thisWeekNow.path("achievedPomodoros").asInt());

        // 周报：传周中任意一天都归一化到周一，且 7 天明细补全
        JsonNode weekly = getJson("/api/ai/weekly-report?weekStart=" + lastMonday.plusDays(2), token).path("data");
        assertEquals(lastMonday.toString(), weekly.path("weekStart").asText());
        assertEquals(lastSunday.toString(), weekly.path("weekEnd").asText());
        assertEquals(7, weekly.path("dailyBreakdown").size());
        assertEquals(lastSunday.toString(), weekly.path("dailyBreakdown").get(6).path("statDate").asText());
        assertEquals(1, weekly.path("completedCount").asInt(), "本周内只有上周日那条记录");
        assertEquals(10, weekly.path("totalMinutes").asInt(), "本周一 00:00 的那条属于本周，不进上周周报");
        assertEquals(10, weekly.path("avgFocusMinutes").asInt(), "10 分钟 / 1 个");
    }

    /**
     * 空数据必须补 0 而不是缺字段/报 404：前端直接渲染，任何 null 都会显示成 NaN。
     */
    @Test
    void emptyRangesAreZeroFilledNotMissing() throws Exception {
        LocalDate today = LocalDate.now();

        JsonNode heatmap = getJson("/api/stat/heatmap", token).path("data");
        assertEquals(365, heatmap.size(), "热力图默认近 365 天");
        assertEquals(today.toString(), heatmap.get(364).path("date").asText());
        for (JsonNode c : heatmap) {
            assertEquals(0, c.path("count").asInt(), "新用户热力图应全 0");
        }

        JsonNode hours = getJson("/api/stat/hour-distribution", token).path("data");
        assertEquals(24, hours.size(), "时段分布必须始终 24 桶");
        for (int h = 0; h < 24; h++) {
            assertEquals(h, hours.get(h).path("hour").asInt());
            assertEquals(0, hours.get(h).path("count").asInt());
            assertEquals(0, hours.get(h).path("minutes").asInt());
        }

        JsonNode daily = getJson("/api/daily-stat", token).path("data");
        assertEquals(today.toString(), daily.path("statDate").asText());
        assertEquals(0, daily.path("totalPomodoros").asInt());
        assertEquals(0, daily.path("focusScore").asInt(), "无完成番茄时效率得分是 0 而不是 null");
        // 未来日期同样补 0，不报 404
        assertEquals(0, getJson("/api/daily-stat?date=" + today.plusDays(30), token)
                .path("data").path("totalPomodoros").asInt());

        // 区间日统计只返回已有行（新用户一条都没有），热力图才做补 0
        JsonNode range = getJson("/api/daily-stat/range", token).path("data");
        assertEquals(0, range.size());
        assertEquals(400, getJson("/api/daily-stat/range?start=" + today + "&end=" + today.minusDays(1), token)
                .path("code").asInt(), "start 晚于 end 应 400");

        JsonNode month = getJson("/api/stat/report?year=" + today.getYear() + "&month=" + today.getMonthValue(), token)
                .path("data");
        assertEquals(today.lengthOfMonth(), month.path("dailyBreakdown").size());
        assertEquals(0, month.path("completedCount").asInt());
        assertEquals(0, month.path("activeDays").asInt());
        assertEquals(0, month.path("completionRate").asInt(), "无记录时完成率是 0 而不是除零异常");
        assertEquals(0, month.path("avgFocusScore").asInt());
        assertEquals(0, month.path("avgDailyMinutes").asInt());
        assertFalse(month.has("bestDay"), "无记录时不应返回 bestDay 字段");

        JsonNode year = getJson("/api/stat/report?year=2025", token).path("data");
        assertEquals("YEAR", year.path("type").asText());
        assertEquals(365, year.path("dailyBreakdown").size());

        // 空日重算会落一条全 0 行（幂等 upsert），区间查询随即能看到它
        JsonNode recalcResult = postJson("/api/daily-stat/recalc?date=" + today, null, token).path("data");
        assertEquals(0, recalcResult.path("totalPomodoros").asInt());
        assertEquals(1, getJson("/api/daily-stat/range?start=" + today + "&end=" + today, token)
                .path("data").size(), "重算会物化一条 0 行");
    }

    /**
     * 月/年长度与参数边界：闰年 2 月 29 天、世纪年 2100 不是闰年、12 月 31 天；
     * 年份 2000-2100 闭区间；月份 1-12；非法值统一 400。
     */
    @Test
    void reportPeriodLengthsAndParamValidation() throws Exception {
        assertBreakdownSize(2024, 2, 29, "闰年 2 月");
        assertBreakdownSize(2026, 2, 28, "今年 2 月");
        assertBreakdownSize(2000, 2, 29, "能被 400 整除的世纪闰年");
        assertBreakdownSize(2100, 2, 28, "能被 100 整除但不能被 400 整除，不是闰年");
        assertBreakdownSize(2026, 12, 31, "12 月");
        assertBreakdownSize(2026, 1, 31, "1 月");
        assertBreakdownSize(2024, 1, 366, "闰年年报", "YEAR");
        assertBreakdownSize(2023, 1, 365, "平年年报", "YEAR");

        JsonNode december = getJson("/api/stat/report?year=2026&month=12", token).path("data");
        assertEquals("2026-12-01", december.path("startDate").asText());
        assertEquals("2026-12-31", december.path("endDate").asText(), "月末边界");

        // 未来的月份不报错，只是全 0
        LocalDate future = LocalDate.now().plusMonths(6);
        assertEquals(future.lengthOfMonth(), getJson("/api/stat/report?year=" + future.getYear()
                + "&month=" + future.getMonthValue(), token).path("data").path("dailyBreakdown").size());

        // 参数校验
        assertEquals(400, getJson("/api/stat/report?year=1999", token).path("code").asInt());
        assertEquals(400, getJson("/api/stat/report?year=2101", token).path("code").asInt());
        assertEquals(SUCCESS, getJson("/api/stat/report?year=2000", token).path("code").asInt(), "2000 在闭区间内");
        assertEquals(SUCCESS, getJson("/api/stat/report?year=2100", token).path("code").asInt(), "2100 在闭区间内");
        assertEquals(400, getJson("/api/stat/report?year=2026&month=0", token).path("code").asInt());
        assertEquals(400, getJson("/api/stat/report?year=2026&month=13", token).path("code").asInt());
        assertEquals(400, getJson("/api/stat/report?year=abc", token).path("code").asInt(), "非数字年份应 400");
        assertEquals(400, getJson("/api/stat/report", token).path("code").asInt(), "year 必填");
    }

    /**
     * 跨度上限 366 天：正好 366 天放行，多一天拒绝；start>end 拒绝。
     * 热力图和时段分布共用同一套校验，两个接口都要覆盖。
     */
    @Test
    void rangeLimitIsEnforcedOnBothHeatmapAndDistribution() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate start366 = today.minusDays(365);
        LocalDate start367 = today.minusDays(366);

        assertEquals(366, getJson("/api/stat/heatmap?start=" + start366 + "&end=" + today, token)
                .path("data").size(), "366 天正好在上限内");
        assertEquals(400, getJson("/api/stat/heatmap?start=" + start367 + "&end=" + today, token)
                .path("code").asInt(), "367 天超上限");
        assertEquals(400, getJson("/api/stat/heatmap?start=" + today + "&end=" + start366, token)
                .path("code").asInt(), "start 晚于 end");
        assertEquals(24, getJson("/api/stat/hour-distribution?start=" + start366 + "&end=" + today, token)
                .path("data").size(), "时段分布固定 24 桶");
        assertEquals(400, getJson("/api/stat/hour-distribution?start=" + start367 + "&end=" + today, token)
                .path("code").asInt());
        assertEquals(400, getJson("/api/stat/hour-distribution?start=2026-02-30&end=2026-03-01", token)
                .path("code").asInt(), "非法日期");
    }

    /**
     * 口径：RUNNING 不进任何统计（否则前端挂着的计时会虚增数据），
     * INTERRUPTED 进日统计与报表（体现失败次数、拉低完成率）但不进热力图/时段分布。
     */
    @Test
    void runningIsExcludedEverywhereAndInterruptedOnlyCountsInSomeViews() throws Exception {
        LocalDate today = LocalDate.now();
        insertRecord(today.atTime(9, 0), 1500, COMPLETED, null);
        insertRecord(today.atTime(10, 0), 300, INTERRUPTED, "临时开会");
        insertRecord(today.atTime(11, 0), 0, RUNNING, null);
        recalc(today);

        JsonNode daily = getJson("/api/daily-stat?date=" + today, token).path("data");
        assertEquals(1, daily.path("completedCount").asInt());
        assertEquals(1, daily.path("interruptedCount").asInt());
        assertEquals(2, daily.path("totalPomodoros").asInt(), "RUNNING 不计入");
        assertEquals(30, daily.path("totalMinutes").asInt(), "25 分钟 + 5 分钟");
        assertEquals(FocusScoreUtil.calc(1, 1, 15), daily.path("focusScore").asInt(),
                "日统计落库得分应与公式一致（平均 30/2=15 分钟）");

        JsonNode heatmap = cell(getJson("/api/stat/heatmap?start=" + today + "&end=" + today, token)
                .path("data"), today.toString());
        assertEquals(1, heatmap.path("count").asInt(), "热力图只数完成");

        JsonNode hours = getJson("/api/stat/hour-distribution?start=" + today + "&end=" + today, token).path("data");
        assertEquals(1, hours.get(9).path("count").asInt());
        assertEquals(0, hours.get(10).path("count").asInt(), "时段分布不含中断");
        assertEquals(0, hours.get(11).path("count").asInt(), "时段分布不含进行中");

        JsonNode report = getJson("/api/stat/report?year=" + today.getYear() + "&month=" + today.getMonthValue(), token)
                .path("data");
        assertEquals(1, report.path("completedCount").asInt());
        assertEquals(1, report.path("interruptedCount").asInt());
        assertEquals(2, report.path("totalPomodoros").asInt());
        assertEquals(50, report.path("completionRate").asInt(), "完成率按完成/(完成+中断)");
        assertEquals(1, report.path("activeDays").asInt());

        // 进行中仍然能在历史分页里查到（前端刷新时靠它恢复计时）
        assertEquals(1, getJson("/api/pomodoro/page?pageNum=1&pageSize=1&status=RUNNING", token)
                .path("data").path("total").asInt());
    }

    /**
     * 写入侧对账边界（防作弊）：
     * ① 不足 30 秒不能算完成，但记录必须留在 RUNNING，客户端还能中断；
     * ② 超过 4 小时不能算完成（防虚报时长拿积分）；
     * ③ 中断是「释放锁」的唯一出路，绝不能因为时长异常而失败——
     *    否则挂机过夜的番茄既不能完成也不能中断，start 会永远 409，用户被锁死；
     *    为保护统计口径，中断落库的时长封顶到 4 小时。
     */
    @Test
    void reconcileBoundsNeverLockTheUserOut() throws Exception {
        // ① 立刻完成 -> 时长过短
        long first = postJson("/api/pomodoro/start", Map.of(), token).path("data").path("id").asLong();
        JsonNode tooShort = postJson("/api/pomodoro/" + first + "/complete", null, token);
        assertEquals(400, tooShort.path("code").asInt(), "不足 30 秒不能算完成");
        assertEquals(RUNNING, getJson("/api/pomodoro/" + first, token).path("data").path("status").asText(),
                "被拒后应仍是进行中，不能把记录卡成终态");

        // 秒退场景不得写出负时长（start_time 列为 DATETIME(0) 会进位，历史缺陷）
        JsonNode interruptedEarly = postJson("/api/pomodoro/" + first + "/interrupt", Map.of(), token);
        assertEquals(SUCCESS, interruptedEarly.path("code").asInt());
        assertTrue(interruptedEarly.path("data").path("duration").asInt() >= 0, "时长不得为负");

        // ② 回拨 5 小时 -> 完成被上限拦住
        long second = postJson("/api/pomodoro/start", Map.of(), token).path("data").path("id").asLong();
        LocalDateTime stale = LocalDateTime.now().withNano(0).minusHours(5);
        pomodoroRecordMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update
                .LambdaUpdateWrapper<PomodoroRecord>()
                .eq(PomodoroRecord::getId, second)
                .set(PomodoroRecord::getStartTime, stale));
        JsonNode tooLong = postJson("/api/pomodoro/" + second + "/complete", null, token);
        assertEquals(400, tooLong.path("code").asInt(), "超过 4 小时不能算完成");

        // ③ 但中断必须成功并释放锁
        JsonNode released = postJson("/api/pomodoro/" + second + "/interrupt", Map.of(), token);
        assertEquals(SUCCESS, released.path("code").asInt(),
                "超时记录也必须能中断，否则用户再也无法开始新番茄：" + released);
        assertEquals(INTERRUPTED, released.path("data").path("status").asText());
        assertEquals(4 * 3600, released.path("data").path("duration").asInt(), "中断时长封顶 4 小时");

        // 中断已按 start_time 归到那天并自动重算：封顶后单日最多贡献 240 分钟
        LocalDate statDate = stale.toLocalDate();
        JsonNode daily = getJson("/api/daily-stat?date=" + statDate, token).path("data");
        boolean firstSharesDate = statDate.equals(LocalDate.now());
        assertEquals(firstSharesDate ? 2 : 1, daily.path("interruptedCount").asInt(),
                "今天秒退的那条（0 秒）是否与超时那条同日，取决于当前钟点");
        assertEquals(240, daily.path("totalMinutes").asInt(), "封顶后单日最多贡献 240 分钟");

        // ④ 锁已释放，可以重新开始
        assertEquals(SUCCESS, postJson("/api/pomodoro/start", Map.of(), token).path("code").asInt());
    }

    // ---------- 造数据与断言辅助 ----------

    /** 直接插一条番茄记录（读侧统计用例需要精确控制 start_time） */
    private void insertRecord(LocalDateTime start, int durationSeconds, String status, String reason) {
        PomodoroRecord record = new PomodoroRecord();
        record.setUserId(userId);
        record.setStartTime(start.withNano(0));
        record.setDuration(durationSeconds);
        record.setStatus(status);
        record.setInterruptReason(reason);
        if (durationSeconds > 0) {
            record.setEndTime(start.withNano(0).plusSeconds(durationSeconds));
        }
        pomodoroRecordMapper.insert(record);
    }

    private void recalc(LocalDate date) throws Exception {
        JsonNode res = postJson("/api/daily-stat/recalc?date=" + date, null, token);
        assertEquals(SUCCESS, res.path("code").asInt(), "重算失败: " + res);
    }

    private void assertBreakdownSize(int year, int month, int expectedDays, String label) throws Exception {
        assertBreakdownSize(year, month, expectedDays, label, "MONTH");
    }

    private void assertBreakdownSize(int year, int month, int expectedDays, String label, String type) throws Exception {
        String url = type.equals("YEAR")
                ? "/api/stat/report?year=" + year
                : "/api/stat/report?year=" + year + "&month=" + month;
        JsonNode response = getJson(url, token);
        assertEquals(SUCCESS, response.path("code").asInt(), label);
        JsonNode data = response.path("data");
        assertEquals(type, data.path("type").asText(), label + " 的 type");
        assertEquals(expectedDays, data.path("dailyBreakdown").size(), label + " 的天数");
    }

    private JsonNode cell(JsonNode array, String date) {
        JsonNode found = findCell(array, date);
        if (found == null) {
            throw new AssertionError("数组里没有找到日期 " + date + " 的格子");
        }
        return found;
    }

    private boolean hasCell(JsonNode array, String date) {
        return findCell(array, date) != null;
    }

    private JsonNode findCell(JsonNode array, String date) {
        for (JsonNode node : array) {
            if (date.equals(node.path("date").asText(node.path("statDate").asText()))) {
                return node;
            }
        }
        return null;
    }

    // ---------- HTTP 辅助 ----------

    private JsonNode postJson(String url, Object body, String authToken) throws Exception {
        return postJson(url, body, authToken, null);
    }

    private JsonNode postJson(String url, Object body, String authToken, RequestPostProcessor fromIp)
            throws Exception {
        MockHttpServletRequestBuilder request = post(url).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(objectMapper.writeValueAsString(body));
        }
        if (authToken != null) {
            request.header("Authorization", "Bearer " + authToken);
        }
        if (fromIp != null) {
            request.with(fromIp);
        }
        return perform(request);
    }

    private JsonNode putJson(String url, Object body, String authToken) throws Exception {
        MockHttpServletRequestBuilder request = put(url).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(objectMapper.writeValueAsString(body));
        }
        if (authToken != null) {
            request.header("Authorization", "Bearer " + authToken);
        }
        return perform(request);
    }

    private JsonNode getJson(String url, String authToken) throws Exception {
        MockHttpServletRequestBuilder request = get(url);
        if (authToken != null) {
            request.header("Authorization", "Bearer " + authToken);
        }
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
