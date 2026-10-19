package com.pomodoro.dto.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 创建任务请求体
 */
@Data
public class TaskCreateRequest {

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 255, message = "任务标题长度不能超过 255")
    private String title;

    private String description;

    /** 四象限：1紧急重要 2重要不紧急 3紧急不重要 4不紧急不重要，缺省 2 */
    @Min(value = 1, message = "象限取值需在 1-4 之间")
    @Max(value = 4, message = "象限取值需在 1-4 之间")
    private Integer quadrant;

    /** 预估番茄数，缺省 1 */
    @Min(value = 1, message = "预估番茄数至少为 1")
    private Integer estimatedPomodoros;

    /** 截止日期 */
    private LocalDate dueDate;

    /** 父任务 id（子任务指向父任务） */
    private Long parentId;

    /** 重复类型：NONE/DAILY/WEEKLY，缺省 NONE */
    @Pattern(regexp = "^(NONE|DAILY|WEEKLY)$", message = "重复类型取值需为 NONE/DAILY/WEEKLY")
    private String repeatType;
}
