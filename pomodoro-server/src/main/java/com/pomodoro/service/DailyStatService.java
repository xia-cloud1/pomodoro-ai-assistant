package com.pomodoro.service;

import com.pomodoro.vo.dailystat.DailyStatVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 日统计聚合服务
 */
public interface DailyStatService {

    /**
     * 重算并落库某日的统计（按 start_time 归属日期，只统计已完成/已中断）
     */
    void aggregate(Long userId, LocalDate date);

    /**
     * 查询单日统计，无数据返回全 0
     */
    DailyStatVO getByDate(Long userId, LocalDate date);

    /**
     * 查询区间统计列表（升序）
     */
    List<DailyStatVO> getRange(Long userId, LocalDate start, LocalDate end);

    /**
     * 手动重算某日并返回结果
     */
    DailyStatVO recalc(Long userId, LocalDate date);
}
