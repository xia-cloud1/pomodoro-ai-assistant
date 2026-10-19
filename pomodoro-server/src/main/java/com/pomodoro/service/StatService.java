package com.pomodoro.service;

import com.pomodoro.vo.stat.HeatmapCellVO;
import com.pomodoro.vo.stat.HourDistributionVO;
import com.pomodoro.vo.stat.ReportVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计可视化服务
 */
public interface StatService {

    /**
     * 每日完成番茄数热力图（按 startTime 归属日期，缺失补 0）
     */
    List<HeatmapCellVO> heatmap(LocalDate start, LocalDate end);

    /**
     * 时段分布（按 startTime 归属小时，0-23 全桶）
     */
    List<HourDistributionVO> hourDistribution(LocalDate start, LocalDate end);

    /**
     * 月/年报表（year 必填；month 缺省为年报，传值=月报）
     */
    ReportVO report(Integer year, Integer month);
}
