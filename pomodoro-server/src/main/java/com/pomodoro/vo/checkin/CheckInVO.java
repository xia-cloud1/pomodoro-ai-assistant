package com.pomodoro.vo.checkin;

import com.pomodoro.entity.CheckIn;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡记录出参
 */
@Data
public class CheckInVO {

    private Long id;
    private LocalDate checkDate;
    private Integer isMakeup;
    private LocalDateTime createdAt;

    /** 从实体转换为出参 */
    public static CheckInVO of(CheckIn checkIn) {
        CheckInVO vo = new CheckInVO();
        vo.setId(checkIn.getId());
        vo.setCheckDate(checkIn.getCheckDate());
        vo.setIsMakeup(checkIn.getIsMakeup());
        vo.setCreatedAt(checkIn.getCreatedAt());
        return vo;
    }
}
