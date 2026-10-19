package com.pomodoro.vo.ai;

import lombok.Data;

/**
 * 周报中的任务统计项
 */
@Data
public class TaskStatVO {

    private Long taskId;
    private String title;
    private Integer completedCount;
}
