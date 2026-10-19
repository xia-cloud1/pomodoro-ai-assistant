package com.pomodoro.vo.stat;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 月/年报表出参
 */
@Data
public class ReportVO {

    /** MONTH / YEAR */
    private String type;
    private LocalDate startDate;
    private LocalDate endDate;

    private int completedCount;
    private int interruptedCount;
    private int totalPomodoros;
    private int totalMinutes;
    private int activeDays;
    private int avgDailyMinutes;
    private int completionRate;
    private int avgFocusScore;

    private BestDayVO bestDay;
    private List<ReportDayVO> dailyBreakdown;
}
