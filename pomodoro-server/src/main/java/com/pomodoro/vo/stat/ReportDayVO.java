package com.pomodoro.vo.stat;

import lombok.Data;

import java.time.LocalDate;

/**
 * 月/年报表逐日明细
 */
@Data
public class ReportDayVO {

    private LocalDate date;
    private int completedCount;
    private int interruptedCount;
    private int totalMinutes;
    private int focusScore;
}
