package com.pomodoro.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成就定义实体（achievement）：徽章静态配置
 */
@Data
@TableName("achievement")
public class Achievement {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 成就编码（唯一） */
    private String code;

    private String name;

    private String description;

    /** 图标（emoji 或图片地址） */
    private String icon;

    /** 条件类型：TOTAL_POMODOROS / STREAK_DAYS */
    private String conditionType;

    /** 条件阈值 */
    private Integer conditionValue;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
