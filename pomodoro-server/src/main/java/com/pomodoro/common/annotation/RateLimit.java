package com.pomodoro.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流：固定窗口内最多允许 limit 次请求（按 IP + 方法统计）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 窗口内最大请求次数 */
    int limit() default 10;

    /** 窗口时长（秒） */
    int windowSeconds() default 60;
}
