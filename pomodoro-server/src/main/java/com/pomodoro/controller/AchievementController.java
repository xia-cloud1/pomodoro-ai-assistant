package com.pomodoro.controller;

import com.pomodoro.common.Result;
import com.pomodoro.service.AchievementService;
import com.pomodoro.vo.achievement.AchievementVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 成就接口
 */
@RestController
@RequestMapping("/api/achievement")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    /**
     * 成就列表（含当前用户解锁状态）
     */
    @GetMapping
    public Result<List<AchievementVO>> list() {
        return Result.success(achievementService.listAchievements());
    }
}
