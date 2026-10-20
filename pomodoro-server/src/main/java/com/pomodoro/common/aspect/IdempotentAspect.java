package com.pomodoro.common.aspect;

import com.pomodoro.common.BusinessException;
import com.pomodoro.common.ResultCode;
import com.pomodoro.common.annotation.Idempotent;
import com.pomodoro.observability.BusinessMetrics;
import com.pomodoro.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等切面：以「用户 + 方法 + 参数」为 key 做窗口去重，
 * 窗口内重复请求直接拒绝，防止双击重复提交；过期 key 懒清理。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private static final long CLEAN_INTERVAL_MS = 60_000L;
    private static final long KEY_TTL_MS = 120_000L;

    private final BusinessMetrics metrics;
    private final ConcurrentHashMap<String, Long> store = new ConcurrentHashMap<>();
    private volatile long lastClean = 0L;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        Long userId = UserContext.requireUserId();
        String key = userId + ":" + pjp.getSignature().toShortString() + ":" + fingerprint(pjp.getArgs());
        long now = System.currentTimeMillis();
        long window = idempotent.seconds() * 1000L;

        Long prev = store.putIfAbsent(key, now);
        if (prev != null && now - prev <= window) {
            metrics.idempotentRejected(pjp.getSignature().getName());
            throw new BusinessException(ResultCode.CONFLICT, "请勿重复提交");
        }
        if (prev != null) {
            // 已过期则刷新时间戳，重新进入窗口
            store.replace(key, prev, now);
        }
        try {
            return pjp.proceed();
        } finally {
            lazyClean(now);
        }
    }

    private void lazyClean(long now) {
        if (now - lastClean < CLEAN_INTERVAL_MS) {
            return;
        }
        lastClean = now;
        store.entrySet().removeIf(e -> now - e.getValue() > KEY_TTL_MS);
    }

    private String fingerprint(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object a : args) {
            sb.append(a == null ? "null" : a.toString()).append('|');
        }
        return sb.toString();
    }
}
