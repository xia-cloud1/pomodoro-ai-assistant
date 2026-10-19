package com.pomodoro.vo.stat;

import lombok.Data;

/**
 * 时段分布出参：某小时（0-23）的完成番茄数与专注分钟数
 */
@Data
public class HourDistributionVO {

    /** 小时（0-23） */
    private int hour;

    /** 该小时完成番茄数 */
    private int count;

    /** 该小时专注分钟数（duration 秒总和四舍五入） */
    private int minutes;
}
