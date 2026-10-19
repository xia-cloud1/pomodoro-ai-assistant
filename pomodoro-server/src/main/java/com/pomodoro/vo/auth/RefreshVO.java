package com.pomodoro.vo.auth;

import lombok.Data;

/**
 * 刷新令牌响应：新 access token
 */
@Data
public class RefreshVO {

    private String accessToken;
}
