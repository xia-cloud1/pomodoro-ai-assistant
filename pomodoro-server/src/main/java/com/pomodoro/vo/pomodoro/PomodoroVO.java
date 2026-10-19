package com.pomodoro.vo.pomodoro;

import com.pomodoro.entity.PomodoroRecord;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 番茄记录出参
 */
@Data
public class PomodoroVO {

    private Long id;
    private Long taskId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer duration;
    private String status;
    private String interruptReason;
    private String whiteNoise;
    private LocalDateTime createdAt;

    /** 从实体转换为出参 */
    public static PomodoroVO of(PomodoroRecord record) {
        PomodoroVO vo = new PomodoroVO();
        vo.setId(record.getId());
        vo.setTaskId(record.getTaskId());
        vo.setStartTime(record.getStartTime());
        vo.setEndTime(record.getEndTime());
        vo.setDuration(record.getDuration());
        vo.setStatus(record.getStatus());
        vo.setInterruptReason(record.getInterruptReason());
        vo.setWhiteNoise(record.getWhiteNoise());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }
}
