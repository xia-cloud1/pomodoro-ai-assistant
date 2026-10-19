package com.pomodoro.vo.points;

import lombok.Data;

/**
 * 积分等级总览出参
 */
@Data
public class PointsSummaryVO {

    private int points;

    private int level;

    /** 距下一级还差多少分 */
    private int pointsToNextLevel;

    /** 当前等级内进度 0-100 */
    private int progressPercent;
}
