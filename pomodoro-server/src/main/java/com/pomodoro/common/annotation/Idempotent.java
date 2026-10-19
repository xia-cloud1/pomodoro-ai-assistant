package com.pomodoro.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等：窗口内相同「用户 + 方法 + 参数」的重复请求直接拒绝，防止双击重复提交。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /** 去重窗口（秒） */
    int seconds() default 5;
}
