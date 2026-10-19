package com.pomodoro.vo.points;

import com.pomodoro.entity.PointsLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水出参
 */
@Data
public class PointsLogVO {

    private Long id;
    private Integer pointsChange;
    private String pointsType;
    private Long refId;
    private String note;
    private LocalDateTime createdAt;

    public static PointsLogVO of(PointsLog log) {
        PointsLogVO vo = new PointsLogVO();
        vo.setId(log.getId());
        vo.setPointsChange(log.getPointsChange());
        vo.setPointsType(log.getPointsType());
        vo.setRefId(log.getRefId());
        vo.setNote(log.getNote());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }
}
