package com.pomodoro.vo.energy;

import com.pomodoro.entity.EnergyLog;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 精力自评出参
 */
@Data
public class EnergyVO {

    private Long id;
    private LocalDate date;
    private Integer energyLevel;
    private String mood;
    private String note;
    private LocalDateTime createdAt;

    public static EnergyVO of(EnergyLog log) {
        EnergyVO vo = new EnergyVO();
        vo.setId(log.getId());
        vo.setDate(log.getLogDate());
        vo.setEnergyLevel(log.getEnergyLevel());
        vo.setMood(log.getMood());
        vo.setNote(log.getNote());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }
}
