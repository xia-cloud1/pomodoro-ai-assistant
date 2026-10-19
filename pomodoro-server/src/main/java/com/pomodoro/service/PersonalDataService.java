package com.pomodoro.service;

/**
 * 个人信息数据服务（个人信息权 / 数据可携带）
 */
public interface PersonalDataService {

    /**
     * 导出当前用户全部个人数据（JSON 字节，不含密码）
     */
    byte[] exportPersonalData();
}
