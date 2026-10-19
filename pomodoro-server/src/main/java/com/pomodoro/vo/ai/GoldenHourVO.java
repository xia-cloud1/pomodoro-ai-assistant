package com.pomodoro.vo.ai;

import lombok.Data;

import java.util.List;

/**
 * 黄金专注时段出参：峰值时段列表 + 可选 AI 建议
 */
@Data
public class GoldenHourVO {

    /** 高效时段（按番茄数降序，最多 2 段） */
    private List<GoldenPeriodVO> periods;

    /** AI 生成的简短建议；未配置或失败为 null */
    private String aiSuggestion;
}
