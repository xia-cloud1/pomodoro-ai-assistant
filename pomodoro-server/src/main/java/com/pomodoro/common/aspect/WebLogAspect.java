package com.pomodoro.common.aspect;

import com.pomodoro.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 请求日志切面：记录 Controller 方法调用（方法、用户、耗时、成败）。
 * 不记录请求参数，避免登录密码等敏感信息落日志。
 */
@Aspect
@Component
@Slf4j
public class WebLogAspect {

    @Around("execution(public * com.pomodoro.controller..*.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String method = pjp.getSignature().toShortString();
        Long userId = UserContext.getUserId();
        try {
            Object result = pjp.proceed();
            log.info("请求 {} | userId={} | {}ms | 成功", method, userId, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable t) {
            log.warn("请求 {} | userId={} | {}ms | 失败: {}", method, userId, System.currentTimeMillis() - start, t.getMessage());
            throw t;
        }
    }
}
