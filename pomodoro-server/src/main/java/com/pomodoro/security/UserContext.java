package com.pomodoro.security;

import com.pomodoro.common.BusinessException;
import com.pomodoro.common.ResultCode;

/**
 * 用户上下文：基于 ThreadLocal 暂存当前登录用户 id
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /** 当前用户 id，未登录返回 null */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /** 当前用户 id，未登录抛 401 */
    public static Long requireUserId() {
        Long userId = USER_ID.get();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
