package com.pomodoro.vo.stat;

import lombok.Data;

import java.time.LocalDate;

/**
 * 热力图单元格出参：某天完成的番茄数
 */
@Data
public class HeatmapCellVO {

    private LocalDate date;

    /** 当天完成番茄数 */
    private int count;
}
