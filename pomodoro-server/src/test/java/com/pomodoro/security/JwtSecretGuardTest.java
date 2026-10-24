package com.pomodoro.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产环境密钥自检的单元测试。
 * <p>
 * 直接测静态方法，不必真的以 prod profile 启动上下文——启动失败本身就是被测行为，
 * 用 ApplicationContextRunner 只会更绕。
 */
class JwtSecretGuardTest {

    private static final String STRONG_SECRET = "a-strong-random-secret-for-production-use-0123456789";

    @Test
    void missingSecretFailsFast() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> JwtSecretGuard.validate(""));
        assertTrue(e.getMessage().contains("JWT_SECRET"), "错误信息要指明是哪个配置项: " + e.getMessage());
        assertThrows(IllegalStateException.class, () -> JwtSecretGuard.validate(null));
        assertThrows(IllegalStateException.class, () -> JwtSecretGuard.validate("   "));
    }

    @Test
    void repositoryDefaultSecretIsRejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> JwtSecretGuard.validate(JwtSecretGuard.DEFAULT_SECRET));
        assertTrue(e.getMessage().contains("默认值"), "要说明拒绝的原因: " + e.getMessage());
    }

    @Test
    void tooShortSecretIsRejected() {
        // HS256 要求 256 位密钥，短的即使不是默认值也不该放行
        assertThrows(IllegalStateException.class, () -> JwtSecretGuard.validate("short-secret"));
    }

    @Test
    void strongSecretPasses() {
        assertDoesNotThrow(() -> JwtSecretGuard.validate(STRONG_SECRET));
    }

    /**
     * JwtSecretGuard 里的默认密钥常量必须与 application.yml 中的默认值一致。
     * <p>
     * 这条断言防的是「静默失效」：如果有人改了配置文件里的默认值而忘了同步常量，
     * 自检就再也拦不住那个已经公开的密钥，而且不会有任何报错——只能靠测试守住。
     */
    @Test
    void defaultSecretConstantMatchesApplicationYml() throws Exception {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        Matcher matcher = Pattern.compile("secret:\\s*\\$\\{JWT_SECRET:([^}]*)}").matcher(yml);
        assertTrue(matcher.find(), "application.yml 里应能找到 jwt.secret 的默认值");
        assertEquals(JwtSecretGuard.DEFAULT_SECRET, matcher.group(1),
                "JwtSecretGuard.DEFAULT_SECRET 与 application.yml 的默认密钥已不一致，生产自检会失效");
    }
}