package com.pomodoro.vo.dailystat;

import com.pomodoro.entity.DailyStat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日统计出参
 */
@Data
public class DailyStatVO {

    private Long id;
    private LocalDate statDate;
    private Integer totalPomodoros;
    private Integer totalMinutes;
    private Integer completedCount;
    private Integer interruptedCount;
    private Integer maxFocusMinutes;
    private Integer avgFocusMinutes;
    private Integer focusScore;
    private String categoryDistribution;
    private String hourDistribution;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 从实体转换 */
    public static DailyStatVO of(DailyStat stat) {
        DailyStatVO vo = new DailyStatVO();
        vo.setId(stat.getId());
        vo.setStatDate(stat.getStatDate());
        vo.setTotalPomodoros(stat.getTotalPomodoros());
        vo.setTotalMinutes(stat.getTotalMinutes());
        vo.setCompletedCount(stat.getCompletedCount());
        vo.setInterruptedCount(stat.getInterruptedCount());
        vo.setMaxFocusMinutes(stat.getMaxFocusMinutes());
        vo.setAvgFocusMinutes(stat.getAvgFocusMinutes());
        vo.setFocusScore(stat.getFocusScore());
        vo.setCategoryDistribution(stat.getCategoryDistribution());
        vo.setHourDistribution(stat.getHourDistribution());
        vo.setCreatedAt(stat.getCreatedAt());
        vo.setUpdatedAt(stat.getUpdatedAt());
        return vo;
    }

    /** 无数据日返回全 0 */
    public static DailyStatVO zero(LocalDate date) {
        DailyStatVO vo = new DailyStatVO();
        vo.setStatDate(date);
        vo.setTotalPomodoros(0);
        vo.setTotalMinutes(0);
        vo.setCompletedCount(0);
        vo.setInterruptedCount(0);
        vo.setMaxFocusMinutes(0);
        vo.setAvgFocusMinutes(0);
        vo.setFocusScore(0);
        return vo;
    }
}
