package com.pomodoro.service;

import com.pomodoro.dto.auth.LoginRequest;
import com.pomodoro.dto.auth.RefreshRequest;
import com.pomodoro.dto.auth.RegisterRequest;
import com.pomodoro.vo.auth.LoginVO;
import com.pomodoro.vo.auth.RefreshVO;
import com.pomodoro.vo.auth.UserVO;

/**
 * 认证服务
 */
public interface AuthService {

    /**
     * 用户注册：校验用户名唯一，BCrypt 加密密码后落库
     */
    UserVO register(RegisterRequest request);

    /**
     * 用户登录：校验用户名密码，签发 access + refresh 双令牌
     */
    LoginVO login(LoginRequest request);

    /**
     * 刷新令牌：校验 refresh token，签发新 access token
     */
    RefreshVO refresh(RefreshRequest request);
}
