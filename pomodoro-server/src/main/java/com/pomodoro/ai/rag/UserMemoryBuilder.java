package com.pomodoro.ai.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pomodoro.entity.EnergyLog;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.entity.Task;
import com.pomodoro.mapper.EnergyLogMapper;
import com.pomodoro.mapper.PomodoroRecordMapper;
import com.pomodoro.mapper.TaskMapper;
import com.pomodoro.service.DailyStatService;
import com.pomodoro.vo.dailystat.DailyStatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 把用户历史数据构造成 RAG 可检索的文档集合。
 *
 * <p>只取「结论型」数据（日报聚合、任务聚合、精力自评），不把每条番茄记录都塞进检索库：
 * 文档数量可控、单条信息密度高，既能回答「哪天效率高」，也能回答「哪个任务花时间最多」。</p>
 */
@Component
@RequiredArgsConstructor
public class UserMemoryBuilder {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_INTERRUPTED = "INTERRUPTED";
    /** 单条精力备注最多保留的字符数 */
    private static final int NOTE_MAX_CHARS = 40;

    private final DailyStatService dailyStatService;
    private final PomodoroRecordMapper pomodoroRecordMapper;
    private final TaskMapper taskMapper;
    private final EnergyLogMapper energyLogMapper;

    /**
     * 构建 [start, end] 区间内的历史文档。
     */
    public List<MemoryDocument> build(Long userId, LocalDate start, LocalDate end) {
        List<MemoryDocument> docs = new ArrayList<>();
        docs.addAll(dailyDocs(userId, start, end));
        docs.addAll(taskDocs(userId, start, end));
        docs.addAll(energyDocs(userId, start, end));
        return docs;
    }

    /** 每天一条日报文档，跳过没有番茄记录的日期 */
    private List<MemoryDocument> dailyDocs(Long userId, LocalDate start, LocalDate end) {
        return dailyStatService.getRange(userId, start, end).stream()
                .filter(s -> s.getTotalPomodoros() != null && s.getTotalPomodoros() > 0)
                .map(s -> new MemoryDocument("日报", s.getStatDate(),
                        "完成 " + s.getCompletedCount() + " 个番茄，中断 " + s.getInterruptedCount()
                                + " 个，专注 " + s.getTotalMinutes() + " 分钟，效率分 "
                                + s.getFocusScore() + "。"))
                .collect(Collectors.toList());
    }

    /**
     * 按任务聚合近 90 天记录：完成数 + 中断数 + 累计专注分钟 + 最近一次记录日期。
     * 中断也计入文档：用户问「某任务最近进展」时，多次中断同样是有效信息。
     */
    private List<MemoryDocument> taskDocs(Long userId, LocalDate start, LocalDate end) {
        List<PomodoroRecord> records = pomodoroRecordMapper.selectList(
                new LambdaQueryWrapper<PomodoroRecord>()
                        .select(PomodoroRecord::getTaskId, PomodoroRecord::getDuration,
                                PomodoroRecord::getStatus, PomodoroRecord::getStartTime)
                        .eq(PomodoroRecord::getUserId, userId)
                        .in(PomodoroRecord::getStatus, STATUS_COMPLETED, STATUS_INTERRUPTED)
                        .ge(PomodoroRecord::getStartTime, start.atStartOfDay())
                        .lt(PomodoroRecord::getStartTime, end.plusDays(1).atStartOfDay()));
        if (records.isEmpty()) {
            return List.of();
        }

        Map<Long, TaskAgg> aggs = new HashMap<>();
        for (PomodoroRecord r : records) {
            Long taskId = r.getTaskId();
            if (taskId == null) {
                continue;
            }
            TaskAgg agg = aggs.computeIfAbsent(taskId, k -> new TaskAgg());
            if (STATUS_COMPLETED.equals(r.getStatus())) {
                agg.completed++;
            } else {
                agg.interrupted++;
            }
            agg.seconds += r.getDuration() == null ? 0 : r.getDuration();
            LocalDate day = r.getStartTime() == null ? null : r.getStartTime().toLocalDate();
            if (day != null && (agg.lastDate == null || day.isAfter(agg.lastDate))) {
                agg.lastDate = day;
            }
        }
        if (aggs.isEmpty()) {
            return List.of();
        }

        // 一次性取任务标题，避免按任务逐条查询
        Map<Long, String> titles = taskMapper.selectBatchIds(aggs.keySet()).stream()
                .collect(Collectors.toMap(Task::getId, Task::getTitle, (a, b) -> a));

        return aggs.entrySet().stream()
                .sorted((a, b) -> Integer.compare(
                        b.getValue().completed + b.getValue().interrupted,
                        a.getValue().completed + a.getValue().interrupted))
                .map(e -> {
                    TaskAgg agg = e.getValue();
                    String title = titles.get(e.getKey());
                    String name = title != null ? "「" + title + "」" : "#" + e.getKey() + "（已删除）";
                    return new MemoryDocument("任务", agg.lastDate,
                            "任务" + name + "完成 " + agg.completed + " 个番茄、中断 "
                                    + agg.interrupted + " 个，累计专注 "
                                    + Math.round(agg.seconds / 60.0) + " 分钟。");
                })
                .collect(Collectors.toList());
    }

    /** 每天一条精力自评文档 */
    private List<MemoryDocument> energyDocs(Long userId, LocalDate start, LocalDate end) {
        List<EnergyLog> logs = energyLogMapper.selectList(
                new LambdaQueryWrapper<EnergyLog>()
                        .eq(EnergyLog::getUserId, userId)
                        .ge(EnergyLog::getLogDate, start)
                        .le(EnergyLog::getLogDate, end));
        return logs.stream()
                .map(log -> {
                    StringBuilder text = new StringBuilder("精力自评 ").append(log.getEnergyLevel()).append("/5");
                    if (log.getMood() != null && !log.getMood().isBlank()) {
                        text.append("，心情：").append(log.getMood());
                    }
                    if (log.getNote() != null && !log.getNote().isBlank()) {
                        text.append("，备注：").append(truncate(log.getNote(), NOTE_MAX_CHARS));
                    }
                    text.append("。");
                    return new MemoryDocument("精力", log.getLogDate(), text.toString());
                })
                .collect(Collectors.toList());
    }

    private String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…";
    }

    /** 任务聚合的可变累加器 */
    private static final class TaskAgg {
        private int completed;
        private int interrupted;
        private long seconds;
        private LocalDate lastDate;
    }
}
