package com.pomodoro.service;

/**
 * 用户账号服务（账号注销）
 */
public interface UserService {

    /**
     * 注销当前账号：密码二次确认后，删除全部个人数据
     *
     * @param password 明文密码（用于二次确认）
     */
    void deleteAccount(String password);
}
