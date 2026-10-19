package com.pomodoro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 任务-标签关联实体（task_tag）
 */
@Data
@TableName("task_tag")
public class TaskTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long tagId;
}
