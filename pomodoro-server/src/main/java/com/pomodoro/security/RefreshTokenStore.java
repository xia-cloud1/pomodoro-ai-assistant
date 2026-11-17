package com.pomodoro.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * refresh token 白名单存储：配合轮换策略，每个 jti 一次性使用（用即消费）。
 * <p>
 * Redis 可用时，白名单记录当前有效的那个 jti；消费用 GETDEL 把「校验归属 + 删除」
 * 做成一个原子操作——如果拆成先 GET 再 DELETE，就还是「先查后插」式的竞态：
 * 同一旧 token 的并发 refresh 会全部通过校验并各自签出新 token，白名单被分裂成多个「有效」。
 * <p>
 * Redis 不可用时降级为「进程内已消费集合」：未消费过的 jti 放行（与历史无状态语义一致），
 * 已消费过的拒绝（重放）。集合无需 TTL——过期的 refresh token 在签名解析阶段就会被拒，
 * 走不到这里；容量设上限（超出按最旧驱逐）纯为内存兜底。
 * <b>降级边界</b>：进程内集合重启即失效、多实例间不共享，且降级期间签发的 token 在 Redis
 * 恢复后查无白名单同样会被要求重新登录——这是「Redis 可选」的既有代价，换来主流程永不被
 * 白名单组件缺席挡住。
 */
@Slf4j
@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    /** 降级已消费集合的容量上限（内存兜底，超出按最旧驱逐） */
    private static final int DEGRADED_MAX = 10_000;

    private final StringRedisTemplate stringRedisTemplate;
    private final long refreshExpireSeconds;

    /**
     * 降级期间已消费的 jti。synchronizedMap 的 putIfAbsent 是原子的：
     * 同一 jti 并发消费只有一个线程拿到 null（胜出），其余视为重放。
     */
    @SuppressWarnings("serial")
    private final Map<String, Boolean> degradedConsumed = Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(64, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > DEGRADED_MAX;
                }
            });

    public RefreshTokenStore(StringRedisTemplate stringRedisTemplate,
                             @Value("${jwt.refresh-expire}") long refreshExpireSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.refreshExpireSeconds = refreshExpireSeconds;
    }

    /** 登录 / 轮换时写入新 jti；Redis 不可用则静默降级（不影响登录） */
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
     * 校验并原子消费一个 refresh token（一次性，供轮换使用）。
     * <p>
     * Redis 正常：jti 必须在白名单中且归属该用户，GETDEL 保证并发同一 jti 只有一个成功；
     * Redis 不可用：查进程内已消费集合——未消费过则消费并放行，已消费过则拒绝。
     * <p>
     * 返回 false 的场景（已轮换过的旧 token 重放 / 已吊销 / 伪造 / 降级窗口签发后 Redis 恢复）
     * 统一按「无效」处理，不对外区分原因，避免给攻击者反馈。
     */
    public boolean consume(String jti, Long userId) {
        try {
            String stored = stringRedisTemplate.opsForValue().getAndDelete(KEY_PREFIX + jti);
            return stored != null && stored.equals(String.valueOf(userId));
        } catch (Exception e) {
            log.warn("Redis 不可用，refresh 消费降级为进程内集合: {}", e.getMessage());
            return degradedConsumed.putIfAbsent(jti, Boolean.TRUE) == null;
        }
    }
}
