package com.pomodoro.vo.ai;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 日报出参：结构化数据 + 可选 AI 分析文本
 */
@Data
public class DailyReportVO {

    private LocalDate date;
    private Integer completedCount;
    private Integer interruptedCount;
    private Integer totalPomodoros;
    private Integer totalMinutes;
    private Integer maxFocusMinutes;
    private Integer avgFocusMinutes;
    private Integer focusScore;

    /** 当天精力自评（无则 null） */
    private Integer energyLevel;
    private String mood;

    private List<TaskStatVO> topTasks;

    /** AI 生成的日报文本；未配置或失败为 null */
    private String aiAnalysis;
}
