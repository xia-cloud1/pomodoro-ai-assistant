package com.pomodoro.service;

import com.pomodoro.common.PageResult;
import com.pomodoro.vo.points.PointsLogVO;
import com.pomodoro.vo.points.PointsSummaryVO;

/**
 * 积分服务
 */
public interface PointsService {

    /** 积分规则：完成番茄 */
    int POINTS_POMODORO = 10;
    /** 积分规则：每日打卡（含补签） */
    int POINTS_CHECKIN = 5;
    /** 积分规则：解锁成就 */
    int POINTS_ACHIEVEMENT = 20;

    String TYPE_POMODORO = "POMODORO";
    String TYPE_CHECKIN = "CHECKIN";
    String TYPE_ACHIEVEMENT = "ACHIEVEMENT";

    /**
     * 积分等级总览（level = points/100 + 1）
     */
    PointsSummaryVO summary();

    /**
     * 积分流水分页（倒序）
     */
    PageResult<PointsLogVO> log(int pageNum, int pageSize);

    /**
     * 增减积分并写流水（读-改-写 user.points/level）
     */
    void addPoints(Long userId, int change, String pointsType, Long refId, String note);
}
