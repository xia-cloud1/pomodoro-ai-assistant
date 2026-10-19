package com.pomodoro.vo.stat;

import lombok.Data;

import java.time.LocalDate;

/**
 * 报表「完成最多的一天」
 */
@Data
public class BestDayVO {

    private LocalDate date;
    private int count;
}
