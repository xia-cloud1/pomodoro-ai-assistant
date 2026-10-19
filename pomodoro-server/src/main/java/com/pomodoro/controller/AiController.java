package com.pomodoro.controller;

import com.pomodoro.common.Result;
import com.pomodoro.dto.ai.AskRequest;
import com.pomodoro.dto.ai.SplitTaskRequest;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.AiAnalysisService;
import com.pomodoro.vo.ai.AskVO;
import com.pomodoro.vo.ai.DailyReportVO;
import com.pomodoro.vo.ai.GoldenHourVO;
import com.pomodoro.vo.ai.MonthlyReportVO;
import com.pomodoro.vo.ai.SplitTaskVO;
import com.pomodoro.vo.ai.WeeklyReportVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

/**
 * AI 分析接口
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAnalysisService aiAnalysisService;

    /**
     * 生成周报（默认本周）
     */
    @GetMapping("/weekly-report")
    public Result<WeeklyReportVO> weeklyReport(@RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate weekStart) {
        return Result.success(aiAnalysisService.weeklyReport(UserContext.requireUserId(), weekStart));
    }

    /**
     * 流式生成周报（SSE）
     */
    @GetMapping(value = "/weekly-report/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWeeklyReport(@RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate weekStart) {
        return aiAnalysisService.streamWeeklyReport(UserContext.requireUserId(), weekStart);
    }

    /**
     * 生成日报（默认今天）
     */
    @GetMapping("/daily-report")
    public Result<DailyReportVO> dailyReport(@RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate date) {
        return Result.success(aiAnalysisService.dailyReport(UserContext.requireUserId(), date));
    }

    /**
     * 生成月报（默认当月）
     */
    @GetMapping("/monthly-report")
    public Result<MonthlyReportVO> monthlyReport(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return Result.success(aiAnalysisService.monthlyReport(UserContext.requireUserId(), year, month));
    }

    /**
     * 效率问答（RAG）
     */
    @PostMapping("/ask")
    public Result<AskVO> ask(@Valid @RequestBody AskRequest request) {
        return Result.success(aiAnalysisService.ask(UserContext.requireUserId(), request.getQuestion()));
    }

    /**
     * AI 任务拆分
     */
    @PostMapping("/split-task")
    public Result<SplitTaskVO> splitTask(@Valid @RequestBody SplitTaskRequest request) {
        return Result.success(aiAnalysisService.splitTask(UserContext.requireUserId(), request.getTitle(), request.getDescription()));
    }

    /**
     * 黄金专注时段
     */
    @GetMapping("/golden-hours")
    public Result<GoldenHourVO> goldenHours() {
        return Result.success(aiAnalysisService.goldenHours(UserContext.requireUserId()));
    }
}
