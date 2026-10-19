package com.pomodoro.dto.task;

import lombok.Data;

/**
 * 任务分页查询请求体
 */
@Data
public class TaskQueryRequest {

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 四象限过滤（可选） */
    private Integer quadrant;

    /** 状态过滤（可选） */
    private Integer status;

    /** 标题关键字模糊搜索（可选） */
    private String keyword;

    /** 父任务 id 过滤（查某任务的直接子任务，可选） */
    private Long parentId;

    /** 只查顶层任务（parent_id 为空，可选） */
    private Boolean topLevel;

    /** 标签 id 过滤（查关联了该标签的任务，可选） */
    private Long tagId;
}
