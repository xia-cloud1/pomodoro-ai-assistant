package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pomodoro.ai.DeepSeekClient;
import com.pomodoro.ai.rag.MemoryDocument;
import com.pomodoro.ai.rag.MemoryRetriever;
import com.pomodoro.ai.rag.RetrievalResult;
import com.pomodoro.ai.rag.UserMemoryBuilder;
import com.pomodoro.entity.EnergyLog;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.entity.Task;
import com.pomodoro.mapper.EnergyLogMapper;
import com.pomodoro.mapper.PomodoroRecordMapper;
import com.pomodoro.mapper.TaskMapper;
import com.pomodoro.observability.BusinessMetrics;
import com.pomodoro.service.AiAnalysisService;
import com.pomodoro.service.DailyStatService;
import com.pomodoro.service.GoalService;
import com.pomodoro.service.StatService;
import com.pomodoro.vo.ai.DailyReportVO;
import com.pomodoro.vo.ai.MonthlyReportVO;
import com.pomodoro.vo.ai.AskVO;
import com.pomodoro.vo.ai.GoldenHourVO;
import com.pomodoro.vo.ai.GoldenPeriodVO;
import com.pomodoro.vo.ai.SplitTaskVO;
import com.pomodoro.vo.ai.TaskStatVO;
import com.pomodoro.vo.ai.WeeklyReportVO;
import com.pomodoro.vo.dailystat.DailyStatVO;
import com.pomodoro.vo.goal.GoalVO;
import com.pomodoro.vo.stat.HourDistributionVO;
import com.pomodoro.vo.stat.ReportVO;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 分析服务实现：本地聚合本周数据 + 调用 DeepSeek 生成分析
 */
@Service
@RequiredArgsConstructor
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String SYSTEM_PROMPT = "你是专业的番茄钟效率分析助手，请基于用户数据给出客观、简洁、鼓励性的中文分析。";
    private static final String ASK_SYSTEM_PROMPT = "你是番茄钟效率分析助手，请仅基于提供的用户数据回答效率相关问题，数据不足时如实说明，不要编造。";
    private static final String SPLIT_SYSTEM_PROMPT = "你是任务管理助手，擅长把大任务拆分为可执行的小步骤。";
    private static final String GOLDEN_SYSTEM_PROMPT = "你是时间管理助手，请基于用户的高效时段给出简洁建议。";
    /** RAG 检索窗口：近 90 天历史 */
    private static final int MEMORY_DAYS = 90;
    /** 送给模型的相关历史记录条数上限 */
    private static final int MEMORY_TOP_K = 8;
    /** 检索上下文最大字符数，避免提示词过长导致调用变慢/变贵 */
    private static final int MEMORY_MAX_CHARS = 1800;

    private final DailyStatService dailyStatService;
    private final GoalService goalService;
    private final PomodoroRecordMapper pomodoroRecordMapper;
    private final TaskMapper taskMapper;
    private final EnergyLogMapper energyLogMapper;
    private final StatService statService;
    private final DeepSeekClient deepSeekClient;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final UserMemoryBuilder userMemoryBuilder;
    private final MemoryRetriever memoryRetriever;
    private final BusinessMetrics metrics;

    @Override
    public WeeklyReportVO weeklyReport(Long userId, LocalDate weekStart) {
        WeeklyReportVO vo = buildWeeklyReport(userId, weekStart);
        vo.setAiAnalysis(deepSeekClient.chat("weekly", SYSTEM_PROMPT, buildPrompt(vo)));
        return vo;
    }

    @Override
    public SseEmitter streamWeeklyReport(Long userId, LocalDate weekStart) {
        WeeklyReportVO vo = buildWeeklyReport(userId, weekStart);
        String prompt = buildPrompt(vo);
        SseEmitter emitter = new SseEmitter(120_000L);
        // 异步线程默认不继承 MDC，手动把当前请求的 traceId 带过去，保证流式日志可串联
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        taskExecutor.execute(() -> {
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            try {
                if (!deepSeekClient.isConfigured()) {
                    metrics.aiDegraded("weekly", "not_configured");
                    emitter.send(SseEmitter.event().data("AI 分析未配置，暂不可用"));
                } else {
                    deepSeekClient.streamChat("weekly", SYSTEM_PROMPT, prompt, chunk -> {
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (Exception ignored) {
                        }
                    });
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            } finally {
                // 线程池复用，必须清理，否则 traceId 会串到下一个任务
                MDC.clear();
            }
        });
        return emitter;
    }

    /** 组装周报结构化数据（不含 AI 文本） */
    private WeeklyReportVO buildWeeklyReport(Long userId, LocalDate weekStart) {
        LocalDate start = mondayOf(weekStart != null ? weekStart : LocalDate.now());
        LocalDate end = start.plusDays(6);

        // 1. 本周每日数据（缺省补全 7 天全 0）
        List<DailyStatVO> rows = dailyStatService.getRange(userId, start, end);
        Map<LocalDate, DailyStatVO> byDate = rows.stream()
                .collect(Collectors.toMap(DailyStatVO::getStatDate, r -> r));
        List<DailyStatVO> breakdown = new ArrayList<>();
        int completed = 0;
        int interrupted = 0;
        int totalMinutes = 0;
        int maxMinutes = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            DailyStatVO day = byDate.getOrDefault(d, DailyStatVO.zero(d));
            breakdown.add(day);
            completed += day.getCompletedCount();
            interrupted += day.getInterruptedCount();
            totalMinutes += day.getTotalMinutes();
            maxMinutes = Math.max(maxMinutes, day.getMaxFocusMinutes());
        }
        int total = completed + interrupted;

        GoalVO goal = goalService.getGoal(userId, start);

        WeeklyReportVO vo = new WeeklyReportVO();
        vo.setWeekStart(start);
        vo.setWeekEnd(end);
        vo.setTotalPomodoros(total);
        vo.setCompletedCount(completed);
        vo.setInterruptedCount(interrupted);
        vo.setTotalMinutes(totalMinutes);
        vo.setMaxFocusMinutes(maxMinutes);
        vo.setAvgFocusMinutes(total == 0 ? 0 : (int) Math.round(totalMinutes * 1.0 / total));
        vo.setGoalTarget(goal.getTargetPomodoros());
        vo.setGoalAchieved(goal.getAchievedPomodoros());
        vo.setGoalProgress(goal.getProgressPercent());
        vo.setDailyBreakdown(breakdown);
        vo.setTopTasks(topTasks(userId, start, end));
        return vo;
    }

    @Override
    public DailyReportVO dailyReport(Long userId, LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        DailyStatVO stat = dailyStatService.getByDate(userId, d);

        EnergyLog energy = energyLogMapper.selectOne(
                new LambdaQueryWrapper<EnergyLog>()
                        .eq(EnergyLog::getUserId, userId)
                        .eq(EnergyLog::getLogDate, d));

        DailyReportVO vo = new DailyReportVO();
        vo.setDate(d);
        vo.setCompletedCount(stat.getCompletedCount());
        vo.setInterruptedCount(stat.getInterruptedCount());
        vo.setTotalPomodoros(stat.getTotalPomodoros());
        vo.setTotalMinutes(stat.getTotalMinutes());
        vo.setMaxFocusMinutes(stat.getMaxFocusMinutes());
        vo.setAvgFocusMinutes(stat.getAvgFocusMinutes());
        vo.setFocusScore(stat.getFocusScore());
        vo.setEnergyLevel(energy != null ? energy.getEnergyLevel() : null);
        vo.setMood(energy != null ? energy.getMood() : null);
        vo.setTopTasks(topTasks(userId, d, d));
        vo.setAiAnalysis(deepSeekClient.chat("daily", SYSTEM_PROMPT, buildDailyPrompt(vo)));
        return vo;
    }

    @Override
    public MonthlyReportVO monthlyReport(Long userId, Integer year, Integer month) {
        int y = year != null ? year : LocalDate.now().getYear();
        int m = month != null ? month : LocalDate.now().getMonthValue();
        // 复用 StatService.report（内部按当前用户上下文聚合，与请求用户一致）
        ReportVO summary = statService.report(y, m);

        MonthlyReportVO vo = new MonthlyReportVO();
        vo.setSummary(summary);
        vo.setTopTasks(topTasks(userId, summary.getStartDate(), summary.getEndDate()));
        vo.setAiAnalysis(deepSeekClient.chat("monthly", SYSTEM_PROMPT, buildMonthlyPrompt(summary, vo.getTopTasks())));
        return vo;
    }

    /**
     * 效率问答：近 7 天概览（固定）+ 近 90 天历史记录（BM25 检索，按问题取最相关的 8 条）。
     * 检索不到相关记录时 Retriever 会回退到最近记录，保证模型仍能基于用户近况回答。
     */
    @Override
    public AskVO ask(Long userId, String question) {
        LocalDate today = LocalDate.now();
        List<MemoryDocument> history = userMemoryBuilder.build(
                userId, today.minusDays(MEMORY_DAYS - 1L), today);
        RetrievalResult memory = memoryRetriever.retrieve(
                history, question, today, MEMORY_TOP_K, MEMORY_MAX_CHARS);

        StringBuilder context = new StringBuilder("【近 7 天概览】\n").append(buildContext(userId));
        if (!memory.context().isEmpty()) {
            context.append("【相关历史记录】\n").append(memory.context()).append('\n');
        }

        AskVO vo = new AskVO();
        vo.setQuestion(question);
        vo.setSources(memory.documents().stream().map(MemoryDocument::citation).toList());
        vo.setAnswer(deepSeekClient.chat("ask", ASK_SYSTEM_PROMPT,
                context + "【用户问题】\n" + question));
        return vo;
    }

    @Override
    public SplitTaskVO splitTask(Long userId, String title, String description) {
        SplitTaskVO vo = new SplitTaskVO();
        String answer = deepSeekClient.chat("split", SPLIT_SYSTEM_PROMPT, buildSplitPrompt(title, description));
        vo.setSubtasks(parseSubtasks(answer));
        return vo;
    }

    @Override
    public GoldenHourVO goldenHours(Long userId) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(29);
        List<HourDistributionVO> distribution = statService.hourDistribution(start, end);

        GoldenHourVO vo = new GoldenHourVO();
        vo.setPeriods(buildGoldenPeriods(distribution));
        if (!deepSeekClient.isConfigured()) {
            metrics.aiDegraded("golden", "not_configured");
        } else if (!vo.getPeriods().isEmpty()) {
            vo.setAiSuggestion(deepSeekClient.chat("golden", GOLDEN_SYSTEM_PROMPT, buildGoldenPrompt(vo.getPeriods())));
        }
        return vo;
    }

    /** 检索用户近 7 天数据，组装问答上下文 */
    private String buildContext(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);

        List<DailyStatVO> rows = dailyStatService.getRange(userId, start, today);
        int completed = 0;
        int minutes = 0;
        for (DailyStatVO r : rows) {
            completed += r.getCompletedCount() == null ? 0 : r.getCompletedCount();
            minutes += r.getTotalMinutes() == null ? 0 : r.getTotalMinutes();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("近 7 天（").append(start).append(" ~ ").append(today).append("）完成番茄 ")
                .append(completed).append(" 个，专注 ").append(minutes).append(" 分钟；\n");

        GoalVO goal = goalService.getGoal(userId, mondayOf(today));
        if (goal.getTargetPomodoros() != null && goal.getTargetPomodoros() > 0) {
            sb.append("本周目标 ").append(goal.getTargetPomodoros()).append(" 个，已达成 ")
                    .append(goal.getAchievedPomodoros()).append(" 个；\n");
        }

        List<TaskStatVO> top = topTasks(userId, start, today);
        if (!top.isEmpty()) {
            sb.append("主要任务：");
            top.forEach(t -> sb.append(t.getTitle()).append("(").append(t.getCompletedCount()).append("次) "));
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 组装任务拆分提示词 */
    private String buildSplitPrompt(String title, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("请把下面的任务拆分成 3~6 个可执行的子任务，每个子任务占一行，简短具体（8 字内），不要编号、不要解释：\n");
        sb.append("任务：").append(title).append("\n");
        if (description != null && !description.isBlank()) {
            sb.append("描述：").append(description).append("\n");
        }
        return sb.toString();
    }

    /** 解析子任务：按行拆分，去掉编号/列表符 */
    private List<String> parseSubtasks(String answer) {
        List<String> result = new ArrayList<>();
        if (answer == null || answer.isBlank()) {
            return result;
        }
        for (String line : answer.split("\\R")) {
            String s = line.trim()
                    .replaceFirst("^[-*•·]\\s*", "")
                    .replaceFirst("^\\d+[.、)）]\\s*", "");
            if (!s.isBlank()) {
                result.add(s);
            }
            if (result.size() >= 6) {
                break;
            }
        }
        return result;
    }

    /** 从时段分布中提取峰值连续时段（按番茄数降序取前 2 段） */
    private List<GoldenPeriodVO> buildGoldenPeriods(List<HourDistributionVO> distribution) {
        if (distribution == null || distribution.isEmpty()) {
            return new ArrayList<>();
        }
        // 找出有专注记录的连续小时块
        List<List<HourDistributionVO>> blocks = new ArrayList<>();
        List<HourDistributionVO> current = null;
        for (HourDistributionVO h : distribution) {
            if (h.getCount() > 0) {
                if (current == null) {
                    current = new ArrayList<>();
                    blocks.add(current);
                }
                current.add(h);
            } else {
                current = null;
            }
        }
        return blocks.stream()
                .sorted((a, b) -> Integer.compare(blockCount(b), blockCount(a)))
                .limit(2)
                .map(this::toPeriod)
                .collect(Collectors.toList());
    }

    private int blockCount(List<HourDistributionVO> block) {
        return block.stream().mapToInt(HourDistributionVO::getCount).sum();
    }

    private GoldenPeriodVO toPeriod(List<HourDistributionVO> block) {
        int startHour = block.get(0).getHour();
        int endHour = block.get(block.size() - 1).getHour();
        GoldenPeriodVO p = new GoldenPeriodVO();
        p.setStartHour(startHour);
        p.setEndHour(endHour);
        p.setTotalCount(blockCount(block));
        p.setTotalMinutes(block.stream().mapToInt(HourDistributionVO::getMinutes).sum());
        p.setLabel(String.format("%02d:00 - %02d:00", startHour, endHour + 1));
        return p;
    }

    /** 组装黄金时段建议提示词 */
    private String buildGoldenPrompt(List<GoldenPeriodVO> periods) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是用户近 30 天的高效专注时段，请给出 1~2 句中文建议（50 字内）：\n");
        periods.forEach(p -> sb.append(p.getLabel()).append("（").append(p.getTotalCount()).append("个番茄）\n"));
        return sb.toString();
    }

    /** 组装日报 AI 提示词 */
    private String buildDailyPrompt(DailyReportVO vo) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下用户今日专注数据并给出中文日报（100 字内，含表现、亮点、建议）：\n");
        sb.append("日期：").append(vo.getDate()).append("\n");
        sb.append("完成番茄：").append(vo.getCompletedCount()).append(" 个，中断：")
                .append(vo.getInterruptedCount()).append(" 个\n");
        sb.append("专注总时长：").append(vo.getTotalMinutes()).append(" 分钟，效率得分：")
                .append(vo.getFocusScore()).append("/100\n");
        if (vo.getEnergyLevel() != null) {
            sb.append("精力自评：").append(vo.getEnergyLevel()).append("/5");
            if (vo.getMood() != null && !vo.getMood().isEmpty()) {
                sb.append("，心情：").append(vo.getMood());
            }
            sb.append("\n");
        }
        if (vo.getTopTasks() != null && !vo.getTopTasks().isEmpty()) {
            sb.append("主要任务：");
            vo.getTopTasks().forEach(t -> sb.append(t.getTitle()).append("(").append(t.getCompletedCount()).append("次) "));
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 组装月报 AI 提示词 */
    private String buildMonthlyPrompt(ReportVO summary, List<TaskStatVO> topTasks) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下用户本月专注数据并给出中文月报（150 字内，含整体表现、亮点、建议）：\n");
        sb.append("月份：").append(summary.getStartDate()).append(" ~ ").append(summary.getEndDate()).append("\n");
        sb.append("完成番茄：").append(summary.getCompletedCount()).append(" 个，中断：")
                .append(summary.getInterruptedCount()).append(" 个\n");
        sb.append("专注总时长：").append(summary.getTotalMinutes()).append(" 分钟，活跃 ")
                .append(summary.getActiveDays()).append(" 天\n");
        sb.append("完成率：").append(summary.getCompletionRate()).append("%，平均效率得分：")
                .append(summary.getAvgFocusScore()).append("/100\n");
        if (topTasks != null && !topTasks.isEmpty()) {
            sb.append("主要任务：");
            topTasks.forEach(t -> sb.append(t.getTitle()).append("(").append(t.getCompletedCount()).append("次) "));
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 本周完成次数最多的前 3 个任务
     */
    private List<TaskStatVO> topTasks(Long userId, LocalDate start, LocalDate end) {
        List<PomodoroRecord> completed = pomodoroRecordMapper.selectList(
                new LambdaQueryWrapper<PomodoroRecord>()
                        .eq(PomodoroRecord::getUserId, userId)
                        .eq(PomodoroRecord::getStatus, STATUS_COMPLETED)
                        .isNotNull(PomodoroRecord::getTaskId)
                        .ge(PomodoroRecord::getStartTime, start.atStartOfDay())
                        .lt(PomodoroRecord::getStartTime, end.plusDays(1).atStartOfDay()));

        Map<Long, Long> counts = completed.stream()
                .collect(Collectors.groupingBy(PomodoroRecord::getTaskId, Collectors.counting()));

        return counts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(3)
                .map(e -> {
                    TaskStatVO t = new TaskStatVO();
                    t.setTaskId(e.getKey());
                    t.setCompletedCount(e.getValue().intValue());
                    Task task = taskMapper.selectById(e.getKey());
                    t.setTitle(task != null ? task.getTitle() : null);
                    return t;
                })
                .collect(Collectors.toList());
    }

    /**
     * 组装 AI 提示词（基于结构化数据）
     */
    private String buildPrompt(WeeklyReportVO vo) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下用户本周专注数据并给出中文周报（150 字内，含整体表现、亮点、可改进建议）：\n");
        sb.append("本周：").append(vo.getWeekStart()).append(" ~ ").append(vo.getWeekEnd()).append("\n");
        sb.append("完成番茄：").append(vo.getCompletedCount()).append(" 个，中断：").append(vo.getInterruptedCount()).append(" 个\n");
        sb.append("专注总时长：").append(vo.getTotalMinutes()).append(" 分钟，最长单次：")
                .append(vo.getMaxFocusMinutes()).append(" 分钟，平均：").append(vo.getAvgFocusMinutes()).append(" 分钟\n");
        if (vo.getGoalTarget() != null && vo.getGoalTarget() > 0) {
            sb.append("周目标：").append(vo.getGoalTarget()).append(" 个，达成：")
                    .append(vo.getGoalAchieved()).append(" 个（").append(vo.getGoalProgress()).append("%）\n");
        } else {
            sb.append("周目标：未设置\n");
        }
        if (vo.getTopTasks() != null && !vo.getTopTasks().isEmpty()) {
            sb.append("主要任务：");
            vo.getTopTasks().forEach(t -> sb.append(t.getTitle()).append("(").append(t.getCompletedCount()).append("次) "));
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 归一化到所在周的周一
     */
    private LocalDate mondayOf(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1);
    }
}
