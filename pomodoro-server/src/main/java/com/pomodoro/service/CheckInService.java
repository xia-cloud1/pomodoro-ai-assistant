package com.pomodoro.service;

import com.pomodoro.vo.checkin.CheckInStatusVO;

import java.time.LocalDate;

/**
 * 打卡服务（全局每日打卡）
 */
public interface CheckInService {

    /**
     * 今日打卡
     */
    CheckInStatusVO checkIn();

    /**
     * 补签历史日期（date 必须早于今天）
     */
    CheckInStatusVO makeup(LocalDate date);

    /**
     * 查询打卡状态
     */
    CheckInStatusVO status();
}
