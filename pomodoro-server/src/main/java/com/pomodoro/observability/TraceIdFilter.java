package com.pomodoro.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 请求级 traceId：每个请求生成（或沿用上游传入的）链路 ID，
 * 写入 MDC 供日志格式输出，同时回写到响应头，前端报错时可带上该 ID 定位问题。
 * <p>
 * 注意：
 * 1. 入站 X-Trace-Id 必须做白名单校验，防止被注入超长/控制字符污染日志；
 * 2. 线程池异步任务不会自动继承 MDC，需要业务代码手动传递（见 AiAnalysisServiceImpl 流式周报）；
 * 3. finally 中必须清理，Tomcat 线程复用否则会串号。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    /** 仅允许字母、数字、下划线、中划线，长度 8~64 */
    private static final Pattern VALID_TRACE_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 优先沿用上游传入的合法 traceId（便于跨服务串联），否则生成 16 位十六进制
     */
    private String resolveTraceId(HttpServletRequest request) {
        String incoming = request.getHeader(TRACE_HEADER);
        if (incoming != null && VALID_TRACE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
