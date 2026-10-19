package com.pomodoro.controller;

import com.pomodoro.common.PageResult;
import com.pomodoro.common.Result;
import com.pomodoro.dto.tag.TaskTagBindRequest;
import com.pomodoro.dto.task.TaskCreateRequest;
import com.pomodoro.dto.task.TaskQueryRequest;
import com.pomodoro.dto.task.TaskUpdateRequest;
import com.pomodoro.service.TaskService;
import com.pomodoro.vo.tag.TagVO;
import com.pomodoro.vo.task.TaskVO;
import jakarta.validation.Valid;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务接口
 */
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * 创建任务
     */
    @PostMapping
    public Result<TaskVO> create(@Valid @RequestBody TaskCreateRequest request) {
        return Result.success(taskService.create(request));
    }

    /**
     * 任务详情
     */
    @GetMapping("/{id}")
    public Result<TaskVO> detail(@PathVariable Long id) {
        return Result.success(taskService.detail(id));
    }

    /**
     * 重复任务打卡
     */
    @PostMapping("/{id}/checkin")
    public Result<TaskVO> checkIn(@PathVariable Long id) {
        return Result.success(taskService.checkIn(id));
    }

    /**
     * 分页查询任务
     */
    @GetMapping("/page")
    public Result<PageResult<TaskVO>> page(TaskQueryRequest request) {
        return Result.success(taskService.page(request));
    }

    /**
     * 查询任务的直接子任务列表
     */
    @GetMapping("/{id}/children")
    public Result<List<TaskVO>> children(@PathVariable Long id) {
        return Result.success(taskService.children(id));
    }

    /**
     * 查询任务的标签列表
     */
    @GetMapping("/{id}/tags")
    public Result<List<TagVO>> taskTags(@PathVariable Long id) {
        return Result.success(taskService.taskTags(id));
    }

    /**
     * 设置任务的标签（整体替换）
     */
    @PutMapping("/{id}/tags")
    public Result<Void> bindTags(@PathVariable Long id, @Valid @RequestBody TaskTagBindRequest request) {
        taskService.bindTags(id, request.getTagIds());
        return Result.success();
    }

    /**
     * 更新任务
     */
    @PutMapping("/{id}")
    public Result<TaskVO> update(@PathVariable Long id, @Valid @RequestBody TaskUpdateRequest request) {
        return Result.success(taskService.update(id, request));
    }

    /**
     * 逻辑删除任务
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return Result.success();
    }
}
