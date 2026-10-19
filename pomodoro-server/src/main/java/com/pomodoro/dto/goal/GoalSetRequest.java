package com.pomodoro.dto.goal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 设置周目标请求体
 */
@Data
public class GoalSetRequest {

    /** 周起始日期（可选，默认本周；任意日期归一化到周一） */
    private LocalDate weekStart;

    /** 目标番茄数 */
    @NotNull(message = "目标番茄数不能为空")
    @Min(value = 1, message = "目标番茄数至少为 1")
    @Max(value = 200, message = "目标番茄数不能超过 200")
    private Integer targetPomodoros;
}
