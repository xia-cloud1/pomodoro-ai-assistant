package com.pomodoro.dto.pomodoro;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 中断番茄钟请求体
 */
@Data
public class PomodoroInterruptRequest {

    /** 中断原因（可选） */
    @Size(max = 255, message = "中断原因长度不能超过 255")
    private String interruptReason;
}
