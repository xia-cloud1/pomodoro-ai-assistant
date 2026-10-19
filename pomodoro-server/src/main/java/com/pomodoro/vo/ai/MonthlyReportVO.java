package com.pomodoro.vo.ai;

import com.pomodoro.vo.stat.ReportVO;
import lombok.Data;

import java.util.List;

/**
 * 月报出参：月度汇总 + 任务统计 + 可选 AI 分析文本
 */
@Data
public class MonthlyReportVO {

    /** 月度汇总（含逐日明细、bestDay、avgFocusScore 等） */
    private ReportVO summary;

    private List<TaskStatVO> topTasks;

    /** AI 生成的月报文本；未配置或失败为 null */
    private String aiAnalysis;
}
