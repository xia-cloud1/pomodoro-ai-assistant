package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pomodoro.common.BusinessException;
import com.pomodoro.common.PageResult;
import com.pomodoro.common.ResultCode;
import com.pomodoro.dto.task.TaskCreateRequest;
import com.pomodoro.dto.task.TaskQueryRequest;
import com.pomodoro.dto.task.TaskUpdateRequest;
import com.pomodoro.entity.Tag;
import com.pomodoro.entity.Task;
import com.pomodoro.entity.TaskTag;
import com.pomodoro.mapper.TagMapper;
import com.pomodoro.mapper.TaskMapper;
import com.pomodoro.mapper.TaskTagMapper;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.TaskService;
import com.pomodoro.vo.tag.TagVO;
import com.pomodoro.vo.task.TaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务服务实现
 */
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private static final String REPEAT_NONE = "NONE";
    private static final String REPEAT_DAILY = "DAILY";
    private static final String REPEAT_WEEKLY = "WEEKLY";

    private final TaskMapper taskMapper;
    private final TagMapper tagMapper;
    private final TaskTagMapper taskTagMapper;

    @Override
    public TaskVO create(TaskCreateRequest request) {
        Long userId = UserContext.requireUserId();

        // 1. 父任务若指定，必须存在且属于当前用户
        if (request.getParentId() != null) {
            Task parent = taskMapper.selectById(request.getParentId());
            if (parent == null || !parent.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "父任务不存在");
            }
        }

        // 2. 构造任务；quadrant/estimatedPomodoros 缺省取库默认（2 / 1）
        Task task = new Task();
        task.setUserId(userId);
        task.setParentId(request.getParentId());
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setQuadrant(request.getQuadrant() != null ? request.getQuadrant() : 2);
        task.setEstimatedPomodoros(request.getEstimatedPomodoros() != null ? request.getEstimatedPomodoros() : 1);
        task.setDueDate(request.getDueDate());
        // status/actualPomodoros 不开放客户端写入，走库默认（0 / 0）；repeatType 开放（缺省 NONE）
        task.setStatus(0);
        task.setActualPomodoros(0);
        task.setRepeatType(request.getRepeatType() != null ? request.getRepeatType() : REPEAT_NONE);

        taskMapper.insert(task);
        return TaskVO.of(task);
    }

    @Override
    public TaskVO detail(Long id) {
        return TaskVO.of(getOwnedTask(id));
    }

    @Override
    public PageResult<TaskVO> page(TaskQueryRequest request) {
        Long userId = UserContext.requireUserId();

        Page<Task> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId)
                .eq(request.getQuadrant() != null, Task::getQuadrant, request.getQuadrant())
                .eq(request.getStatus() != null, Task::getStatus, request.getStatus())
                .like(StringUtils.hasText(request.getKeyword()), Task::getTitle, request.getKeyword())
                .orderByDesc(Task::getCreatedAt);

        // 子任务过滤：parentId 精确查子任务；topLevel 只查顶层（parent_id 为空）
        if (request.getParentId() != null) {
            wrapper.eq(Task::getParentId, request.getParentId());
        } else if (Boolean.TRUE.equals(request.getTopLevel())) {
            wrapper.isNull(Task::getParentId);
        }

        // 标签过滤：任务需关联指定标签（apply 参数化子查询，防 SQL 注入）
        if (request.getTagId() != null) {
            wrapper.apply("id IN (SELECT task_id FROM task_tag WHERE tag_id = {0})", request.getTagId());
        }

        Page<Task> result = taskMapper.selectPage(page, wrapper);
        List<TaskVO> records = result.getRecords().stream().map(TaskVO::of).collect(Collectors.toList());
        fillTags(records);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /**
     * 批量填充任务标签：固定 2 条查询（关联表 + 标签表），避免逐条 N+1。
     * 标签被删除时关联可能残留，按 tag 是否存在过滤掉。
     */
    private void fillTags(List<TaskVO> tasks) {
        if (tasks.isEmpty()) {
            return;
        }
        List<Long> taskIds = tasks.stream().map(TaskVO::getId).collect(Collectors.toList());
        List<TaskTag> links = taskTagMapper.selectList(
                new LambdaQueryWrapper<TaskTag>().in(TaskTag::getTaskId, taskIds));
        if (links.isEmpty()) {
            tasks.forEach(t -> t.setTags(List.of()));
            return;
        }

        List<Long> tagIds = links.stream().map(TaskTag::getTagId).distinct().collect(Collectors.toList());
        Map<Long, Tag> tagById = tagMapper.selectList(
                        new LambdaQueryWrapper<Tag>().in(Tag::getId, tagIds))
                .stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));

        Map<Long, List<TagVO>> tagsByTask = new HashMap<>();
        for (TaskTag link : links) {
            Tag tag = tagById.get(link.getTagId());
            if (tag == null) {
                continue;
            }
            tagsByTask.computeIfAbsent(link.getTaskId(), k -> new ArrayList<>()).add(TagVO.of(tag));
        }
        tasks.forEach(t -> t.setTags(tagsByTask.getOrDefault(t.getId(), List.of())));
    }

    @Override
    public List<TaskVO> children(Long id) {
        // 校验父任务存在且属于当前用户（否则 404）
        Task parent = getOwnedTask(id);
        List<Task> children = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getUserId, parent.getUserId())
                        .eq(Task::getParentId, id)
                        .orderByAsc(Task::getCreatedAt));
        List<TaskVO> vos = children.stream().map(TaskVO::of).collect(Collectors.toList());
        fillTags(vos);
        return vos;
    }

    @Override
    public List<TagVO> taskTags(Long taskId) {
        getOwnedTask(taskId);
        List<TaskTag> links = taskTagMapper.selectList(
                new LambdaQueryWrapper<TaskTag>().eq(TaskTag::getTaskId, taskId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> tagIds = links.stream().map(TaskTag::getTagId).collect(Collectors.toList());
        List<Tag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<Tag>().in(Tag::getId, tagIds).orderByAsc(Tag::getCreatedAt));
        return tags.stream().map(TagVO::of).collect(Collectors.toList());
    }

    @Override
    public void bindTags(Long taskId, List<Long> tagIds) {
        Long userId = UserContext.requireUserId();
        getOwnedTask(taskId);

        // 去重后校验标签都归属于当前用户
        List<Long> distinct = tagIds.stream().distinct().collect(Collectors.toList());
        if (!distinct.isEmpty()) {
            Long count = tagMapper.selectCount(
                    new LambdaQueryWrapper<Tag>()
                            .eq(Tag::getUserId, userId)
                            .in(Tag::getId, distinct));
            if (count == null || count != distinct.size()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "标签不存在");
            }
        }

        // 整体替换：先删后插
        taskTagMapper.delete(new LambdaQueryWrapper<TaskTag>().eq(TaskTag::getTaskId, taskId));
        for (Long tagId : distinct) {
            TaskTag link = new TaskTag();
            link.setTaskId(taskId);
            link.setTagId(tagId);
            taskTagMapper.insert(link);
        }
    }

    @Override
    public TaskVO update(Long id, TaskUpdateRequest request) {
        Task task = getOwnedTask(id);

        // 部分更新：字段为 null 不修改
        if (request.getTitle() != null) {
            task.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getQuadrant() != null) {
            task.setQuadrant(request.getQuadrant());
        }
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }
        if (request.getEstimatedPomodoros() != null) {
            task.setEstimatedPomodoros(request.getEstimatedPomodoros());
        }
        if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }
        if (request.getRepeatType() != null) {
            task.setRepeatType(request.getRepeatType());
        }

        taskMapper.updateById(task);
        return TaskVO.of(task);
    }

    @Override
    public void delete(Long id) {
        Task task = getOwnedTask(id);
        // isDeleted 由 @TableLogic 处理，这里走逻辑删除
        taskMapper.deleteById(task.getId());
    }

    /**
     * 取当前用户拥有的任务，不存在或非本人返回 404（不泄露他人资源存在性）
     */
    private Task getOwnedTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null || !task.getUserId().equals(UserContext.requireUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    /**
     * 重复任务打卡：本周期已打卡 409；上一周期有打卡则 streak+1，否则重启为 1
     */
    @Override
    public TaskVO checkIn(Long id) {
        Task task = getOwnedTask(id);
        String repeatType = task.getRepeatType() == null ? REPEAT_NONE : task.getRepeatType();
        if (REPEAT_NONE.equals(repeatType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该任务不是重复任务，无需打卡");
        }

        LocalDate today = LocalDate.now();
        LocalDate last = task.getLastCheckinDate();
        if (last != null && samePeriod(last, today, repeatType)) {
            throw new BusinessException(ResultCode.CONFLICT, "本周期已打卡");
        }

        int streak;
        if (last != null && samePeriod(last, previousPeriodStart(today, repeatType), repeatType)) {
            streak = (task.getStreakCount() == null ? 0 : task.getStreakCount()) + 1;
        } else {
            streak = 1;
        }

        task.setStreakCount(streak);
        task.setLastCheckinDate(today);
        taskMapper.updateById(task);
        return TaskVO.of(task);
    }

    /**
     * 判断两个日期是否同周期（DAILY 同天；WEEKLY 同周）
     */
    private boolean samePeriod(LocalDate a, LocalDate b, String repeatType) {
        return periodStart(a, repeatType).equals(periodStart(b, repeatType));
    }

    /**
     * 上一周期起始（DAILY 昨天；WEEKLY 上周一）
     */
    private LocalDate previousPeriodStart(LocalDate date, String repeatType) {
        if (REPEAT_WEEKLY.equals(repeatType)) {
            return periodStart(date, repeatType).minusDays(7);
        }
        return date.minusDays(1);
    }

    /**
     * 周期起始（DAILY 当天；WEEKLY 周一）
     */
    private LocalDate periodStart(LocalDate date, String repeatType) {
        if (REPEAT_WEEKLY.equals(repeatType)) {
            return date.minusDays(date.getDayOfWeek().getValue() - 1);
        }
        return date;
    }
}
