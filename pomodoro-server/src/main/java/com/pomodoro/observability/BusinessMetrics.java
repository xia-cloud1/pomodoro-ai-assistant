package com.pomodoro.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 业务指标埋点。
 * <p>
 * 统一在这里定义指标名与标签，避免各处直接拼 MeterRegistry 造成命名漂移。
 * 指标经 /actuator/prometheus 暴露，Prometheus 会转换为下划线命名
 * （如 pomodoro.completed -> pomodoro_completed_total）。
 * <p>
 * 标签基数控制：只使用枚举型、低基数的值（feature / outcome / reason / method），
 * 不把 userId、任务标题等无限增长的值作为标签。
 */
@Component
@RequiredArgsConstructor
public class BusinessMetrics {

    private final MeterRegistry registry;

    /** 番茄钟启动：bound_task 区分是否关联任务 */
    public void pomodoroStarted(boolean boundTask) {
        Counter.builder("pomodoro.started")
                .description("番茄钟启动次数")
                .tag("bound_task", boundTask ? "yes" : "no")
                .register(registry)
                .increment();
    }

    /** 番茄钟完成：同时记录专注时长分布 */
    public void pomodoroCompleted(int durationSeconds) {
        Counter.builder("pomodoro.completed")
                .description("番茄钟完成次数")
                .register(registry)
                .increment();
        Timer.builder("pomodoro.focus.duration")
                .description("完成番茄的专注时长")
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.ofSeconds(durationSeconds));
    }

    /** 番茄钟中断 */
    public void pomodoroInterrupted() {
        Counter.builder("pomodoro.interrupted")
                .description("番茄钟中断次数")
                .register(registry)
                .increment();
    }

    /**
     * AI 调用结果：feature 为业务功能，mode 为 chat/stream，outcome 为 success/failed
     */
    public void aiCall(String feature, String mode, String outcome, long nanos) {
        Counter.builder("ai.call")
                .description("AI 调用次数")
                .tags("feature", feature, "mode", mode, "outcome", outcome)
                .register(registry)
                .increment();
        Timer.builder("ai.call.duration")
                .description("AI 调用耗时")
                .publishPercentileHistogram()
                .tags("feature", feature, "mode", mode, "outcome", outcome)
                .register(registry)
                .record(Duration.ofNanos(nanos));
    }

    /**
     * AI 降级：not_configured（未配 Key）/ call_failed（调用异常）
     */
    public void aiDegraded(String feature, String reason) {
        Counter.builder("ai.degraded")
                .description("AI 降级次数")
                .tags("feature", feature, "reason", reason)
                .register(registry)
                .increment();
    }

    /** 限流触发 */
    public void rateLimitTriggered(String method) {
        Counter.builder("rate.limit.triggered")
                .description("限流拦截次数")
                .tag("method", method)
                .register(registry)
                .increment();
    }

    /** 幂等拦截 */
    public void idempotentRejected(String method) {
        Counter.builder("idempotent.rejected")
                .description("幂等重复提交拦截次数")
                .tag("method", method)
                .register(registry)
                .increment();
    }
}
