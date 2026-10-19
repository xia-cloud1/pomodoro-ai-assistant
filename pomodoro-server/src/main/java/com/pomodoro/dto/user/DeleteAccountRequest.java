package com.pomodoro.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注销账号请求体（需密码二次确认）
 */
@Data
public class DeleteAccountRequest {

    @NotBlank(message = "密码不能为空")
    private String password;
}
