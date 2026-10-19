package com.pomodoro.vo.achievement;

import com.pomodoro.entity.Achievement;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成就出参：成就定义 + 当前用户解锁状态
 */
@Data
public class AchievementVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String icon;
    private String conditionType;
    private Integer conditionValue;
    /** 当前用户是否已解锁 */
    private boolean unlocked;
    /** 解锁时间（未解锁为 null） */
    private LocalDateTime unlockedAt;

    public static AchievementVO of(Achievement a, boolean unlocked, LocalDateTime unlockedAt) {
        AchievementVO vo = new AchievementVO();
        vo.setId(a.getId());
        vo.setCode(a.getCode());
        vo.setName(a.getName());
        vo.setDescription(a.getDescription());
        vo.setIcon(a.getIcon());
        vo.setConditionType(a.getConditionType());
        vo.setConditionValue(a.getConditionValue());
        vo.setUnlocked(unlocked);
        vo.setUnlockedAt(unlockedAt);
        return vo;
    }
}
