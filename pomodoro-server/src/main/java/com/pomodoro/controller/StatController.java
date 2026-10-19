package com.pomodoro.controller;

import com.pomodoro.common.Result;
import com.pomodoro.service.StatService;
import com.pomodoro.vo.stat.HeatmapCellVO;
import com.pomodoro.vo.stat.HourDistributionVO;
import com.pomodoro.vo.stat.ReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

/**
 * 统计可视化接口
 */
@RestController
@RequestMapping("/api/stat")
@RequiredArgsConstructor
public class StatController {

    private final StatService statService;

    /**
     * 每日完成番茄数热力图（默认近 365 天）
     */
    @GetMapping("/heatmap")
    public Result<List<HeatmapCellVO>> heatmap(
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate end) {
        return Result.success(statService.heatmap(start, end));
    }

    /**
     * 时段分布（默认近 30 天）
     */
    @GetMapping("/hour-distribution")
    public Result<List<HourDistributionVO>> hourDistribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate end) {
        return Result.success(statService.hourDistribution(start, end));
    }

    /**
     * 月/年报表（year 必填；month 缺省=年报，传值=月报）
     */
    @GetMapping("/report")
    public Result<ReportVO> report(
            @RequestParam Integer year,
            @RequestParam(required = false) Integer month) {
        return Result.success(statService.report(year, month));
    }
}
