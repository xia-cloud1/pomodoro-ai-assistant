package com.pomodoro.vo.checkin;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 打卡状态出参（全局每日打卡）
 */
@Data
public class CheckInStatusVO {

    /** 今日是否已打卡 */
    private Boolean checkedToday;

    /** 连续打卡天数（断签为 0） */
    private Integer streakDays;

    /** 最近一次打卡日期 */
    private LocalDate lastCheckinDate;

    /** 最近打卡记录（近 30 条，日期倒序） */
    private List<CheckInVO> records;
}
