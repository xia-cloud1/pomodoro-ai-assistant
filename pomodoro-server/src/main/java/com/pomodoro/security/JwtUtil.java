package com.pomodoro.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：生成 / 解析 access + refresh 双令牌（HS256）
 */
@Component
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessExpire;
    private final long refreshExpire;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.access-expire}") long accessExpire,
                   @Value("${jwt.refresh-expire}") long refreshExpire) {
        this.key = buildKey(secret);
        this.accessExpire = accessExpire;
        this.refreshExpire = refreshExpire;
    }

    /**
     * 把配置里的字符串转成签名密钥。
     * <p>
     * 空值这里主动拦一道：jjwt 对空密钥抛的是英文的 {@code WeakKeyException}（"The specified key
     * byte array is 0 bits..."），运维排查时看不出是哪个配置项没填。生产环境还会由
     * {@link JwtSecretGuard} 进一步拒绝「沿用仓库默认密钥」的情况。
     */
    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret 为空：请注入 JWT_SECRET（生产部署见 pomodoro-server/.env.example）");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 生成 access token */
    public String generateAccessToken(Long userId, String username) {
        return buildToken(userId, username, TYPE_ACCESS, null, accessExpire);
    }

    /** 生成 refresh token（jti 作为唯一标识，用于白名单吊销） */
    public String generateRefreshToken(Long userId, String jti) {
        return buildToken(userId, null, TYPE_REFRESH, jti, refreshExpire);
    }

    private String buildToken(Long userId, String username, String type, String jti, long expireSeconds) {
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireSeconds * 1000))
                .signWith(key);
        if (username != null) {
            builder.claim("username", username);
        }
        if (jti != null) {
            builder.id(jti);
        }
        return builder.compact();
    }

    /** 解析 token，非法/过期会抛 JwtException / ExpiredJwtException */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(Claims claims) {
        String sub = claims.getSubject();
        return sub == null ? null : Long.parseLong(sub);
    }

    public String getTokenType(Claims claims) {
        return claims.get("type", String.class);
    }

    public String getJti(Claims claims) {
        return claims.getId();
    }
}
