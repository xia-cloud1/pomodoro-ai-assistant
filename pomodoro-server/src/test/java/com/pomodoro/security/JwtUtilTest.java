package com.pomodoro.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtUtil 单元测试：验证 token 生成 / 解析 / 过期 / 密钥校验
 */
class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234567890";

    private JwtUtil newUtil() {
        return new JwtUtil(SECRET, 7200, 604800);
    }

    @Test
    void generateAndParse_returnsCorrectClaims() {
        JwtUtil jwtUtil = newUtil();
        String token = jwtUtil.generateAccessToken(1001L, "demo");

        Claims claims = jwtUtil.parseToken(token);
        assertEquals(1001L, jwtUtil.getUserId(claims));
        assertEquals(JwtUtil.TYPE_ACCESS, jwtUtil.getTokenType(claims));
        assertEquals("demo", claims.get("username", String.class));
    }

    @Test
    void refreshToken_hasRefreshTypeAndJti() {
        JwtUtil jwtUtil = newUtil();
        String token = jwtUtil.generateRefreshToken(1001L, "test-jti");

        Claims claims = jwtUtil.parseToken(token);
        assertEquals(JwtUtil.TYPE_REFRESH, jwtUtil.getTokenType(claims));
        assertEquals("test-jti", jwtUtil.getJti(claims));
    }

    @Test
    void parseWithWrongSecret_throws() {
        JwtUtil jwtUtil = newUtil();
        String token = jwtUtil.generateAccessToken(1001L, "demo");

        // 用不同的密钥解析，应当失败
        JwtUtil other = new JwtUtil("another-secret-another-secret-another-secret!", 7200, 604800);
        assertThrows(JwtException.class, () -> other.parseToken(token));
    }

    @Test
    void expiredToken_throws() {
        // 负有效期 → 生成的 token 立即过期
        JwtUtil jwtUtil = new JwtUtil(SECRET, -10, -10);
        String token = jwtUtil.generateAccessToken(1001L, "demo");

        assertThrows(JwtException.class, () -> jwtUtil.parseToken(token));
    }
}
