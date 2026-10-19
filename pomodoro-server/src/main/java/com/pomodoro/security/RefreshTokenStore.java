package com.pomodoro.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * refresh token 白名单存储：Redis 可用时记录 jti 以支持吊销；不可用时自动降级为无状态校验
 */
@Slf4j
@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate stringRedisTemplate;
    private final long refreshExpireSeconds;

    public RefreshTokenStore(StringRedisTemplate stringRedisTemplate,
                             @Value("${jwt.refresh-expire}") long refreshExpireSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.refreshExpireSeconds = refreshExpireSeconds;
    }

    /** 登录时写入 jti 白名单；Redis 不可用则静默降级（不影响登录） */
    public void save(String jti, Long userId) {
        try {
            stringRedisTemplate.opsForValue().set(
                    KEY_PREFIX + jti, String.valueOf(userId),
                    refreshExpireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，refresh 白名单写入降级: {}", e.getMessage());
        }
    }

    /**
     * 校验 refresh token 是否仍在白名单。
     * Redis 正常且存在 -> true；正常但不存在 -> false（已吊销）；
     * Redis 不可用 -> true（降级为无状态，业务不崩）
     */
    public boolean validate(String jti, Long userId) {
        try {
            String stored = stringRedisTemplate.opsForValue().get(KEY_PREFIX + jti);
            return stored != null && stored.equals(String.valueOf(userId));
        } catch (Exception e) {
            log.warn("Redis 不可用，refresh 校验降级为无状态通过: {}", e.getMessage());
            return true;
        }
    }
}
