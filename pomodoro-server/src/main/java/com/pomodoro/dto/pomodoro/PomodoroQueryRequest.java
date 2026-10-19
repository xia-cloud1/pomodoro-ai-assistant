package com.pomodoro.dto.pomodoro;

import lombok.Data;

/**
 * 番茄记录分页查询请求体
 */
@Data
public class PomodoroQueryRequest {

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 状态过滤（RUNNING/COMPLETED/INTERRUPTED，可选） */
    private String status;

    /** 关联任务过滤（可选） */
    private Long taskId;
}
