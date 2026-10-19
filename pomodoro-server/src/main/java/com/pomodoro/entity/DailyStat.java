package com.pomodoro.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日统计聚合实体（daily_stat）：定时任务写入，供报表/热力图查询
 */
@Data
@TableName("daily_stat")
public class DailyStat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate statDate;

    private Integer totalPomodoros;

    private Integer totalMinutes;

    private Integer completedCount;

    private Integer interruptedCount;

    private Integer maxFocusMinutes;

    private Integer avgFocusMinutes;

    /** 效率得分（0-100） */
    private Integer focusScore;

    /** 分类分布（JSON 文本） */
    private String categoryDistribution;

    /** 时段分布（JSON 文本） */
    private String hourDistribution;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
