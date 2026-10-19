package com.pomodoro.vo.goal;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 周目标出参（achieved/progress 实时计算）
 */
@Data
public class GoalVO {

    private Long id;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private Integer targetPomodoros;
    private Integer achievedPomodoros;
    private Integer progressPercent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
