package com.pomodoro.vo.task;

import com.pomodoro.entity.Task;
import com.pomodoro.vo.tag.TagVO;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务出参
 */
@Data
public class TaskVO {

    private Long id;
    private Long parentId;
    private String title;
    private String description;
    private Integer quadrant;
    private Integer status;
    private Integer estimatedPomodoros;
    private Integer actualPomodoros;
    private LocalDate dueDate;
    private String repeatType;
    private Integer streakCount;
    private LocalDate lastCheckinDate;
    /** 任务标签，仅在列表/子任务查询里批量填充（见 TaskServiceImpl#fillTags） */
    private List<TagVO> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 从实体转换为出参 */
    public static TaskVO of(Task task) {
        TaskVO vo = new TaskVO();
        vo.setId(task.getId());
        vo.setParentId(task.getParentId());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setQuadrant(task.getQuadrant());
        vo.setStatus(task.getStatus());
        vo.setEstimatedPomodoros(task.getEstimatedPomodoros());
        vo.setActualPomodoros(task.getActualPomodoros());
        vo.setDueDate(task.getDueDate());
        vo.setRepeatType(task.getRepeatType());
        vo.setStreakCount(task.getStreakCount());
        vo.setLastCheckinDate(task.getLastCheckinDate());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }
}
