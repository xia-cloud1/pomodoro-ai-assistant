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
 * 周目标实体（goal）
 */
@Data
@TableName("goal")
public class Goal {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 周起始日期（周一） */
    private LocalDate weekStart;

    private Integer targetPomodoros;

    private Integer achievedPomodoros;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
