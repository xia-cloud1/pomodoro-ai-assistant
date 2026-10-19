package com.pomodoro.controller;

import com.pomodoro.common.Result;
import com.pomodoro.dto.goal.GoalSetRequest;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.GoalService;
import com.pomodoro.vo.goal.GoalVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

/**
 * 周目标接口
 */
@RestController
@RequestMapping("/api/goal")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    /**
     * 设置/更新周目标
     */
    @PutMapping
    public Result<GoalVO> set(@Valid @RequestBody GoalSetRequest request) {
        return Result.success(goalService.setGoal(UserContext.requireUserId(), request));
    }

    /**
     * 查询周目标状态（默认本周）
     */
    @GetMapping
    public Result<GoalVO> get(@RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate weekStart) {
        return Result.success(goalService.getGoal(UserContext.requireUserId(), weekStart));
    }
}
