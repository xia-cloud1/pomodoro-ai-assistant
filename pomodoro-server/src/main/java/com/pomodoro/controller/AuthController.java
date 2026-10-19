package com.pomodoro.controller;

import com.pomodoro.common.Result;
import com.pomodoro.common.annotation.RateLimit;
import com.pomodoro.dto.auth.LoginRequest;
import com.pomodoro.dto.auth.RefreshRequest;
import com.pomodoro.dto.auth.RegisterRequest;
import com.pomodoro.service.AuthService;
import com.pomodoro.vo.auth.LoginVO;
import com.pomodoro.vo.auth.RefreshVO;
import com.pomodoro.vo.auth.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    @RateLimit(limit = 10, windowSeconds = 60)
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh")
    public Result<RefreshVO> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request));
    }
}
