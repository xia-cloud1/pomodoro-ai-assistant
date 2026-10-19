package com.pomodoro.common.aspect;

import com.pomodoro.common.BusinessException;
import com.pomodoro.common.ResultCode;
import com.pomodoro.common.annotation.RateLimit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 限流切面：内存固定窗口计数（按 IP + 方法），超限抛 429。
 * 进程内单机限流，Redis 不可用时亦可用。
 */
@Aspect
@Component
public class RateLimitAspect {

    private final ConcurrentHashMap<String, Deque<Long>> store = new ConcurrentHashMap<>();

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String key = pjp.getSignature().toShortString() + ":" + clientIp();
        long now = System.currentTimeMillis();
        long window = rateLimit.windowSeconds() * 1000L;

        Deque<Long> queue = store.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > window) {
                queue.pollFirst();
            }
            if (queue.size() >= rateLimit.limit()) {
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
            }
            queue.addLast(now);
        }
        return pjp.proceed();
    }

    private String clientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? "unknown" : attrs.getRequest().getRemoteAddr();
    }
}
