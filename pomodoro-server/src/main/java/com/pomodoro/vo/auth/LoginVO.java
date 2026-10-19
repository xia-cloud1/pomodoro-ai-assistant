package com.pomodoro.vo.auth;

import lombok.Data;

/**
 * 登录响应：用户信息 + 双令牌
 */
@Data
public class LoginVO {

    private UserVO user;

    private String accessToken;

    private String refreshToken;
}
