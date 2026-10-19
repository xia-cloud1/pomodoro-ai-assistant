package com.pomodoro.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一状态码
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    CONFLICT(409, "资源冲突"),
    BUSINESS_ERROR(500, "业务处理失败"),
    SERVER_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;
}
