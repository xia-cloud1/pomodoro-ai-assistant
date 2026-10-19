package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pomodoro.common.BusinessException;
import com.pomodoro.common.ResultCode;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.entity.Task;
import com.pomodoro.mapper.PomodoroRecordMapper;
import com.pomodoro.mapper.TaskMapper;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据导出服务实现：番茄记录 CSV
 */
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_INTERRUPTED = "INTERRUPTED";

    private final PomodoroRecordMapper pomodoroRecordMapper;
    private final TaskMapper taskMapper;

    @Override
    public byte[] exportPomodoroCsv(LocalDate start, LocalDate end) {
        Long userId = UserContext.requireUserId();
        LocalDate today = LocalDate.now();
        if (end == null) {
            end = today;
        }
        if (start == null) {
            start = end.minusDays(29);
        }
        if (start.isAfter(end)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }

        List<PomodoroRecord> records = pomodoroRecordMapper.selectList(
                new LambdaQueryWrapper<PomodoroRecord>()
                        .eq(PomodoroRecord::getUserId, userId)
                        .in(PomodoroRecord::getStatus, STATUS_COMPLETED, STATUS_INTERRUPTED)
                        .ge(PomodoroRecord::getStartTime, start.atStartOfDay())
                        .lt(PomodoroRecord::getStartTime, end.plusDays(1).atStartOfDay())
                        .orderByAsc(PomodoroRecord::getStartTime));

        Map<Long, String> taskTitles = loadTaskTitles(records);

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM，Excel 打开中文不乱码
        sb.append('﻿');
        sb.append("开始时间,结束时间,时长(秒),状态,任务,中断原因,白噪音\n");
        for (PomodoroRecord r : records) {
            sb.append(format(r.getStartTime())).append(',');
            sb.append(format(r.getEndTime())).append(',');
            sb.append(r.getDuration() == null ? 0 : r.getDuration()).append(',');
            sb.append(mapStatus(r.getStatus())).append(',');
            sb.append(escape(r.getTaskId() == null ? null : taskTitles.get(r.getTaskId()))).append(',');
            sb.append(escape(r.getInterruptReason())).append(',');
            sb.append(escape(r.getWhiteNoise())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Map<Long, String> loadTaskTitles(List<PomodoroRecord> records) {
        Set<Long> taskIds = records.stream()
                .map(PomodoroRecord::getTaskId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return taskMapper.selectBatchIds(taskIds).stream()
                .collect(Collectors.toMap(Task::getId, Task::getTitle));
    }

    private String format(LocalDateTime t) {
        return t == null ? "" : DT.format(t);
    }

    private String mapStatus(String status) {
        if (STATUS_COMPLETED.equals(status)) {
            return "已完成";
        }
        if (STATUS_INTERRUPTED.equals(status)) {
            return "已中断";
        }
        return status == null ? "" : status;
    }

    /** CSV 字段转义：含逗号/引号/换行时加引号并转义内部引号 */
    private String escape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
