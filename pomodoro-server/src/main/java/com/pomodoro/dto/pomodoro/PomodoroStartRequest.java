package com.pomodoro.dto.pomodoro;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 开始番茄钟请求体
 */
@Data
public class PomodoroStartRequest {

    /** 关联任务（可选） */
    private Long taskId;

    /** 白噪音类型（可选） */
    @Size(max = 32, message = "白噪音类型长度不能超过 32")
    private String whiteNoise;
}
