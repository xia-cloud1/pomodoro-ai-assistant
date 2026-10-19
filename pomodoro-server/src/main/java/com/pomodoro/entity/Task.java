package com.pomodoro.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务实体（task）：四象限 / 子任务 / 重复任务 / 习惯打卡
 */
@Data
@TableName("task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 父任务 id（子任务指向父任务） */
    private Long parentId;

    private String title;

    private String description;

    /** 四象限：1紧急重要 2重要不紧急 3紧急不重要 4不紧急不重要 */
    private Integer quadrant;

    /** 状态：0未开始 1进行中 2已完成 3已归档 */
    private Integer status;

    private Integer estimatedPomodoros;

    private Integer actualPomodoros;

    private LocalDate dueDate;

    /** 重复类型：NONE/DAILY/WEEKLY */
    private String repeatType;

    /** 连续打卡天数 */
    private Integer streakCount;

    /** 最近一次打卡日期 */
    private LocalDate lastCheckinDate;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
