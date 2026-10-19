package com.pomodoro.dto.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 更新任务请求体（字段可部分更新，null 表示不修改）
 */
@Data
public class TaskUpdateRequest {

    @Size(max = 255, message = "任务标题长度不能超过 255")
    private String title;

    private String description;

    /** 四象限：1-4 */
    @Min(value = 1, message = "象限取值需在 1-4 之间")
    @Max(value = 4, message = "象限取值需在 1-4 之间")
    private Integer quadrant;

    /** 状态：0未开始 1进行中 2已完成 3已归档 */
    @Min(value = 0, message = "状态取值需在 0-3 之间")
    @Max(value = 3, message = "状态取值需在 0-3 之间")
    private Integer status;

    /** 预估番茄数 */
    @Min(value = 1, message = "预估番茄数至少为 1")
    private Integer estimatedPomodoros;

    /** 截止日期 */
    private LocalDate dueDate;

    /** 重复类型：NONE/DAILY/WEEKLY */
    @Pattern(regexp = "^(NONE|DAILY|WEEKLY)$", message = "重复类型取值需为 NONE/DAILY/WEEKLY")
    private String repeatType;
}
