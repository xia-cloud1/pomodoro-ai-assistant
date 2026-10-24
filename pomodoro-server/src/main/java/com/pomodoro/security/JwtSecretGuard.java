package com.pomodoro.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 生产环境签名密钥自检：只在 {@code prod} profile 下生效，不通过就直接启动失败。
 * <p>
 * 为什么必须 fail-fast 而不是打个警告日志：HS256 是<b>对称</b>签名，密钥一旦泄露，
 * 任何人都能自己签发任意 userId 的 access token，直接绕过整个认证体系，而且服务端
 * 无法区分真假。仓库里的默认密钥是公开的，一旦被带到线上就等于没有认证。
 * 启动失败虽然刺眼，但比「悄悄用一个公开密钥跑在生产上」安全得多。
 * <p>
 * 本地开发与测试（默认 profile）不受影响，仍然是开箱即用。
 */
@Component
public class JwtSecretGuard {

    /**
     * application.yml 里的默认密钥，公开在仓库中，绝不允许用于生产。
     * 与配置文件中的默认值保持一致由 {@code JwtSecretGuardTest} 守护。
     */
    public static final String DEFAULT_SECRET =
            "pomodoro-ai-efficiency-system-jwt-secret-key-please-change-me-2026";

    /** HS256 要求密钥至少 256 位（32 字节） */
    private static final int MIN_SECRET_BYTES = 32;

    public JwtSecretGuard(Environment environment, @Value("${jwt.secret:}") String secret) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            validate(secret);
        }
    }

    /** 抽成静态方法，便于单测直接覆盖各分支，不必真的以 prod profile 启动整个上下文 */
    static void validate(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "生产环境必须注入 JWT_SECRET（例如 docker compose 的 .env 或容器环境变量）："
                            + "缺少签名密钥时任何人都能伪造 token");
        }
        if (DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET 仍是仓库中的默认值，该值已公开，等同于没有认证；请换成一串随机长字符串");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 至少需要 " + MIN_SECRET_BYTES + " 字节（HS256 要求 256 位密钥）");
        }
    }
}