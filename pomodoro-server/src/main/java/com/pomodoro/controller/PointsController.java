package com.pomodoro.controller;

import com.pomodoro.common.PageResult;
import com.pomodoro.common.Result;
import com.pomodoro.service.PointsService;
import com.pomodoro.vo.points.PointsLogVO;
import com.pomodoro.vo.points.PointsSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 积分接口
 */
@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    /**
     * 积分等级总览
     */
    @GetMapping("/summary")
    public Result<PointsSummaryVO> summary() {
        return Result.success(pointsService.summary());
    }

    /**
     * 积分流水分页
     */
    @GetMapping("/log")
    public Result<PageResult<PointsLogVO>> log(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(pointsService.log(pageNum, pageSize));
    }
}
