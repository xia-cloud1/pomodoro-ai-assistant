package com.pomodoro.service;

import com.pomodoro.vo.ai.AskVO;
import com.pomodoro.vo.ai.DailyReportVO;
import com.pomodoro.vo.ai.GoldenHourVO;
import com.pomodoro.vo.ai.MonthlyReportVO;
import com.pomodoro.vo.ai.SplitTaskVO;
import com.pomodoro.vo.ai.WeeklyReportVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;

/**
 * AI 分析服务
 */
public interface AiAnalysisService {

    /**
     * 生成周报（结构化数据 + 可选 AI 分析），weekStart 为空默认本周
     */
    WeeklyReportVO weeklyReport(Long userId, LocalDate weekStart);

    /**
     * 流式生成周报（SSE 推送 AI 文本），未配置 key 时降级发一条提示
     */
    SseEmitter streamWeeklyReport(Long userId, LocalDate weekStart);

    /**
     * 生成日报（结构化数据 + 可选 AI 分析），date 为空默认今天
     */
    DailyReportVO dailyReport(Long userId, LocalDate date);

    /**
     * 生成月报（结构化数据 + 可选 AI 分析），year/month 为空默认当月
     */
    MonthlyReportVO monthlyReport(Long userId, Integer year, Integer month);

    /**
     * 效率问答（RAG）：近 7 天概览 + 近 90 天历史记录 BM25 检索结果作上下文；
     * sources 返回检索命中的记录引用，未配置 key 时 answer 为 null
     */
    AskVO ask(Long userId, String question);

    /**
     * AI 任务拆分：把任务拆成子任务列表，未配置 key 时返回空列表
     */
    SplitTaskVO splitTask(Long userId, String title, String description);

    /**
     * 黄金专注时段：近 30 天峰值时段 + 可选 AI 建议
     */
    GoldenHourVO goldenHours(Long userId);
}
