package com.pomodoro.service;

import com.pomodoro.vo.achievement.AchievementVO;

import java.util.List;

/**
 * 成就服务
 */
public interface AchievementService {

    /**
     * 成就列表（含当前用户解锁状态，读时惰性补检）
     */
    List<AchievementVO> listAchievements();

    /**
     * 按当前数据解锁满足条件的成就（幂等，靠 user_achievement 唯一键防重）。
     * 由番茄完成 / 打卡 / 补签等触发点调用。
     */
    void checkAndUnlock(Long userId);
}
