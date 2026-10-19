package com.pomodoro.vo.ai;

import lombok.Data;

/**
 * 黄金专注时段（一段连续的高效小时区间）
 */
@Data
public class GoldenPeriodVO {

    /** 起始小时（0-23，含） */
    private Integer startHour;

    /** 结束小时（0-23，含） */
    private Integer endHour;

    /** 该时段完成番茄总数 */
    private Integer totalCount;

    /** 该时段专注分钟数 */
    private Integer totalMinutes;

    /** 展示标签，如 09:00 - 11:00 */
    private String label;
}
