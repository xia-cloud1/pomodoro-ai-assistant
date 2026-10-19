package com.pomodoro.service;

import com.pomodoro.common.PageResult;
import com.pomodoro.dto.task.TaskCreateRequest;
import com.pomodoro.dto.task.TaskQueryRequest;
import com.pomodoro.dto.task.TaskUpdateRequest;
import com.pomodoro.vo.tag.TagVO;
import com.pomodoro.vo.task.TaskVO;

import java.util.List;

/**
 * 任务服务
 */
public interface TaskService {

    /**
     * 创建任务
     */
    TaskVO create(TaskCreateRequest request);

    /**
     * 任务详情
     */
    TaskVO detail(Long id);

    /**
     * 分页查询任务（按象限/状态/关键字/父任务过滤）
     */
    PageResult<TaskVO> page(TaskQueryRequest request);

    /**
     * 查询某任务的直接子任务列表
     */
    List<TaskVO> children(Long id);

    /**
     * 查询任务的标签列表
     */
    List<TagVO> taskTags(Long taskId);

    /**
     * 设置任务的标签（整体替换）
     */
    void bindTags(Long taskId, List<Long> tagIds);

    /**
     * 更新任务（部分更新）
     */
    TaskVO update(Long id, TaskUpdateRequest request);

    /**
     * 逻辑删除任务
     */
    void delete(Long id);

    /**
     * 重复任务打卡（DAILY 按天 / WEEKLY 按周），更新连续打卡天数
     */
    TaskVO checkIn(Long id);
}
