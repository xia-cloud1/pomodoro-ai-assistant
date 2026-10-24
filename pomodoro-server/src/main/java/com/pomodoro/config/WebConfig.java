package com.pomodoro.config;

import com.pomodoro.security.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册 JWT 拦截器 + 白名单 + CORS
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    /**
     * 允许跨域的来源，逗号分隔；留空表示不开放跨域。
     * <p>
     * 开发环境默认只放行 H5 开发服务器；生产环境（application-prod.yml）默认为空——
     * 因为线上 H5 与 /api 由同一个 Nginx 提供，属于同源请求，根本不需要 CORS。
     */
    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 留空则不注册任何 CORS 规则。注意不能退化成 allowedOriginPatterns("*")：
        // 通配来源 + allowCredentials(true) 意味着任意网站都能带着用户凭据读接口数据，
        // 这是把「同源保护」整个让出去，比不配 CORS 危险得多。
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return;
        }
        String[] origins = allowedOrigins.split("\\s*,\\s*");
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 前端跨域时需要读取 traceId 用于报错反馈
                .exposedHeaders("Authorization", "X-Trace-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
