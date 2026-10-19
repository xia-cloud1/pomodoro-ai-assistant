package com.pomodoro.vo.ai;

import com.pomodoro.vo.dailystat.DailyStatVO;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 周报出参：结构化数据 + 可选 AI 分析文本
 */
@Data
public class WeeklyReportVO {

    private LocalDate weekStart;
    private LocalDate weekEnd;
    private Integer totalPomodoros;
    private Integer completedCount;
    private Integer interruptedCount;
    private Integer totalMinutes;
    private Integer maxFocusMinutes;
    private Integer avgFocusMinutes;

    private Integer goalTarget;
    private Integer goalAchieved;
    private Integer goalProgress;

    private List<DailyStatVO> dailyBreakdown;
    private List<TaskStatVO> topTasks;

    /** AI 生成的周报文本；未配置或失败为 null */
    private String aiAnalysis;
}
