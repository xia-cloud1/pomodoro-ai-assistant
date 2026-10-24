package com.pomodoro.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CORS 配置的单元测试：重点是「留空即完全不开放跨域」和「绝不退化成通配来源」。
 * <p>
 * 用子类把 {@link CorsRegistry#getCorsConfigurations()} 暴露出来直接读配置结果，
 * 比为了两种配置各起一个 Spring 上下文跑 MockMvc 快得多；跨域的真实行为另由
 * MockMvc 用例覆盖。
 */
class WebConfigCorsTest {

    private CorsRegistry corsOf(String allowedOrigins) {
        WebConfig webConfig = new WebConfig(null);   // addCorsMappings 用不到拦截器
        ReflectionTestUtils.setField(webConfig, "allowedOrigins", allowedOrigins);
        InspectableCorsRegistry registry = new InspectableCorsRegistry();
        webConfig.addCorsMappings(registry);
        return registry;
    }

    @Test
    void blankOriginsRegisterNothing() {
        assertTrue(((InspectableCorsRegistry) corsOf("")).configurations().isEmpty(),
                "留空时不应注册任何 CORS 规则");
        assertTrue(((InspectableCorsRegistry) corsOf("   ")).configurations().isEmpty());
        assertTrue(((InspectableCorsRegistry) corsOf(null)).configurations().isEmpty());
    }

    @Test
    void configuredOriginsAreListedNotWildcarded() {
        CorsConfiguration config = ((InspectableCorsRegistry) corsOf(
                "https://pomodoro.example.com, https://admin.example.com")).configurations().get("/**");

        assertEquals(List.of("https://pomodoro.example.com", "https://admin.example.com"),
                config.getAllowedOriginPatterns());
        assertFalse(config.getAllowedOriginPatterns().contains("*"),
                "通配来源 + allowCredentials 等于把同源保护让出去，必须显式列举");
        assertEquals(Boolean.TRUE, config.getAllowCredentials());
        assertTrue(config.getExposedHeaders().contains("X-Trace-Id"), "前端要能读到 traceId");
    }

    /** getCorsConfigurations() 是 protected，测试用子类暴露出来读取 */
    private static class InspectableCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}