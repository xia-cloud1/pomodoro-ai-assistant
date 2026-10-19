package com.pomodoro.security;

import com.pomodoro.common.BusinessException;
import com.pomodoro.common.ResultCode;
import com.pomodoro.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器：校验 access token，写入 UserContext
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    @Value("${jwt.header}")
    private String header;

    @Value("${jwt.prefix}")
    private String prefix;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader(header);
        if (token == null || token.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }

        // 去掉 "Bearer " 前缀（忽略大小写）
        if (prefix != null && !prefix.isBlank()
                && token.regionMatches(true, 0, prefix, 0, prefix.length())) {
            token = token.substring(prefix.length()).trim();
        }

        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }

        // 只接受 access token
        if (!JwtUtil.TYPE_ACCESS.equals(jwtUtil.getTokenType(claims))) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "令牌类型错误");
        }

        Long userId = jwtUtil.getUserId(claims);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "令牌无效");
        }

        // 账号注销后其 token 必须立即失效，否则会继续写入孤儿数据
        if (userMapper.selectById(userId) == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不存在或已注销");
        }

        UserContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 防止线程复用导致用户串号。这里只覆盖 preHandle 正常返回的情况；
        // preHandle 抛异常（401）时 Spring 不会回调本方法，由 UserContextFilter 兜底。
        UserContext.clear();
    }
}
