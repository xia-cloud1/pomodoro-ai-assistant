package com.pomodoro.controller;

import com.pomodoro.common.BusinessException;
import com.pomodoro.common.Result;
import com.pomodoro.common.ResultCode;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.DailyStatService;
import com.pomodoro.vo.dailystat.DailyStatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

/**
 * 日统计接口
 */
@RestController
@RequestMapping("/api/daily-stat")
@RequiredArgsConstructor
public class DailyStatController {

    private final DailyStatService dailyStatService;

    /**
     * 查询单日统计（默认今天）
     */
    @GetMapping
    public Result<DailyStatVO> getByDate(@RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate date) {
        Long userId = UserContext.requireUserId();
        LocalDate d = date != null ? date : LocalDate.now();
        return Result.success(dailyStatService.getByDate(userId, d));
    }

    /**
     * 查询区间统计（默认近 30 天）
     */
    @GetMapping("/range")
    public Result<List<DailyStatVO>> getRange(@RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate start,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate end) {
        Long userId = UserContext.requireUserId();
        LocalDate e = end != null ? end : LocalDate.now();
        LocalDate s = start != null ? start : e.minusDays(29);
        if (s.isAfter(e)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        return Result.success(dailyStatService.getRange(userId, s, e));
    }

    /**
     * 手动重算某日（默认今天）
     */
    @PostMapping("/recalc")
    public Result<DailyStatVO> recalc(@RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate date) {
        Long userId = UserContext.requireUserId();
        LocalDate d = date != null ? date : LocalDate.now();
        return Result.success(dailyStatService.recalc(userId, d));
    }
}
