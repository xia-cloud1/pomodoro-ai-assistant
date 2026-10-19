package com.pomodoro.service;

import java.time.LocalDate;

/**
 * 数据导出服务
 */
public interface ExportService {

    /**
     * 导出番茄记录 CSV（默认近 30 天，UTF-8 带 BOM）
     */
    byte[] exportPomodoroCsv(LocalDate start, LocalDate end);
}
