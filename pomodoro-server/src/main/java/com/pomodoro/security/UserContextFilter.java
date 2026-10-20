package com.pomodoro.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 在每次请求前后清理 UserContext（ThreadLocal）。
 * <p>
 * 只靠 {@link JwtInterceptor#afterCompletion} 是不够的：Spring MVC 在 preHandle 抛异常时
 * 不会回调该拦截器的 afterCompletion（interceptorIndex 尚未推进到它），于是 token 过期 /
 * 账号注销等 401 场景下 ThreadLocal 会残留，被同一线程的下一个请求读到——
 * 典型表现是登录接口的日志里带着上一个请求的 userId。
 * <p>
 * 放在过滤器层用 try/finally 兜住，覆盖所有请求（含拦截器白名单内的 /api/auth/**）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class UserContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        UserContext.clear();
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
