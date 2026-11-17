package com.pomodoro.vo.auth;

import lombok.Data;

/**
 * 刷新令牌响应：新 access token + 轮换后的新 refresh token。
 * 旧 refresh token 在签发本次响应时已被服务端消费（吊销），不能再使用。
 */
@Data
public class RefreshVO {

    private String accessToken;

    private String refreshToken;
}
