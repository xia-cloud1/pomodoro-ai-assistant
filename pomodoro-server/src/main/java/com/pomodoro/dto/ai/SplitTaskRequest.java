package com.pomodoro.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 任务拆分请求体
 */
@Data
public class SplitTaskRequest {

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 255, message = "任务标题长度不能超过 255")
    private String title;

    @Size(max = 1000, message = "任务描述长度不能超过 1000")
    private String description;
}
