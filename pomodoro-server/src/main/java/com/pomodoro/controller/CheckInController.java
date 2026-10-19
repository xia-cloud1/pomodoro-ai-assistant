package com.pomodoro.controller;

import com.pomodoro.common.Result;
import com.pomodoro.common.annotation.Idempotent;
import com.pomodoro.service.CheckInService;
import com.pomodoro.vo.checkin.CheckInStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 打卡接口（全局每日打卡）
 */
@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    /**
     * 今日打卡
     */
    @PostMapping
    @Idempotent(seconds = 5)
    public Result<CheckInStatusVO> checkIn() {
        return Result.success(checkInService.checkIn());
    }

    /**
     * 补签历史日期
     */
    @PostMapping("/makeup")
    public Result<CheckInStatusVO> makeup(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(checkInService.makeup(date));
    }

    /**
     * 查询打卡状态
     */
    @GetMapping("/status")
    public Result<CheckInStatusVO> status() {
        return Result.success(checkInService.status());
    }
}
