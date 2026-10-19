package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pomodoro.common.BusinessException;
import com.pomodoro.common.ResultCode;
import com.pomodoro.dto.auth.LoginRequest;
import com.pomodoro.dto.auth.RefreshRequest;
import com.pomodoro.dto.auth.RegisterRequest;
import com.pomodoro.entity.User;
import com.pomodoro.mapper.UserMapper;
import com.pomodoro.security.JwtUtil;
import com.pomodoro.security.RefreshTokenStore;
import com.pomodoro.service.AuthService;
import com.pomodoro.vo.auth.LoginVO;
import com.pomodoro.vo.auth.RefreshVO;
import com.pomodoro.vo.auth.UserVO;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 认证服务实现
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenStore refreshTokenStore;

    @Override
    public UserVO register(RegisterRequest request) {
        // 1. 用户名唯一性校验（数据库 uk_username 唯一键兜底）
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }

        // 2. 构造用户，密码 BCrypt 加密；level/points 与库默认值保持一致
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setLevel(1);
        user.setPoints(0);

        // 3. 落库（createdAt 由 MyMetaObjectHandler 自动填充）
        userMapper.insert(user);

        // 4. 返回脱敏后的用户信息
        return UserVO.of(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        // 1. 按用户名查用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));

        // 2. 用户不存在或密码不匹配，统一提示（防止用户名枚举）
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // 3. 签发双令牌；refresh 带 jti 写入白名单（Redis 不可用自动降级）
        String jti = UUID.randomUUID().toString();
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), jti);
        refreshTokenStore.save(jti, user.getId());

        LoginVO vo = new LoginVO();
        vo.setUser(UserVO.of(user));
        vo.setAccessToken(jwtUtil.generateAccessToken(user.getId(), user.getUsername()));
        vo.setRefreshToken(refreshToken);
        return vo;
    }

    @Override
    public RefreshVO refresh(RefreshRequest request) {
        // 1. 解析 refresh token（签名/过期校验，失败抛 401）
        Claims claims;
        try {
            claims = jwtUtil.parseToken(request.getRefreshToken());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 无效或已过期");
        }

        // 2. 必须是 refresh 类型（防止用 access token 冒充）
        if (!JwtUtil.TYPE_REFRESH.equals(jwtUtil.getTokenType(claims))) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "令牌类型错误");
        }

        Long userId = jwtUtil.getUserId(claims);
        String jti = jwtUtil.getJti(claims);
        if (userId == null || jti == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 无效");
        }

        // 3. 白名单校验（Redis 不可用自动降级为无状态通过）
        if (!refreshTokenStore.validate(jti, userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 已失效，请重新登录");
        }

        // 4. 确认用户仍存在，签发新 access token
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }

        RefreshVO vo = new RefreshVO();
        vo.setAccessToken(jwtUtil.generateAccessToken(user.getId(), user.getUsername()));
        return vo;
    }
}
