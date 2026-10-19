package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pomodoro.entity.Achievement;
import com.pomodoro.entity.CheckIn;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.entity.UserAchievement;
import com.pomodoro.mapper.AchievementMapper;
import com.pomodoro.mapper.CheckInMapper;
import com.pomodoro.mapper.PomodoroRecordMapper;
import com.pomodoro.mapper.UserAchievementMapper;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.AchievementService;
import com.pomodoro.service.PointsService;
import com.pomodoro.vo.achievement.AchievementVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 成就服务实现：条件实时算，满足即解锁，幂等防重
 */
@Service
@RequiredArgsConstructor
public class AchievementServiceImpl implements AchievementService {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String TYPE_TOTAL_POMODOROS = "TOTAL_POMODOROS";
    private static final String TYPE_STREAK_DAYS = "STREAK_DAYS";

    private final AchievementMapper achievementMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final PomodoroRecordMapper pomodoroRecordMapper;
    private final CheckInMapper checkInMapper;
    private final PointsService pointsService;

    @Override
    public List<AchievementVO> listAchievements() {
        Long userId = UserContext.requireUserId();
        // 读时惰性补检，保证漏触发也能看到已解锁徽章
        checkAndUnlock(userId);

        Map<Long, LocalDateTime> unlockedMap = userAchievementMapper.selectList(
                        new LambdaQueryWrapper<UserAchievement>().eq(UserAchievement::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(UserAchievement::getAchievementId, UserAchievement::getUnlockedAt));

        return achievementMapper.selectList(new LambdaQueryWrapper<Achievement>().orderByAsc(Achievement::getId))
                .stream()
                .map(a -> AchievementVO.of(a, unlockedMap.containsKey(a.getId()), unlockedMap.get(a.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public void checkAndUnlock(Long userId) {
        if (userId == null) {
            return;
        }
        int totalCompleted = countCompletedPomodoros(userId);
        int streakDays = computeStreak(userId);

        Set<Long> unlockedIds = userAchievementMapper.selectList(
                        new LambdaQueryWrapper<UserAchievement>().eq(UserAchievement::getUserId, userId))
                .stream().map(UserAchievement::getAchievementId).collect(Collectors.toSet());

        List<Achievement> all = achievementMapper.selectList(null);
        for (Achievement a : all) {
            if (unlockedIds.contains(a.getId()) || a.getConditionValue() == null) {
                continue;
            }
            boolean met = TYPE_TOTAL_POMODOROS.equals(a.getConditionType()) && totalCompleted >= a.getConditionValue()
                    || TYPE_STREAK_DAYS.equals(a.getConditionType()) && streakDays >= a.getConditionValue();
            if (!met) {
                continue;
            }
            UserAchievement ua = new UserAchievement();
            ua.setUserId(userId);
            ua.setAchievementId(a.getId());
            ua.setUnlockedAt(LocalDateTime.now());
            try {
                userAchievementMapper.insert(ua);
            } catch (DuplicateKeyException e) {
                // 并发下唯一键冲突，忽略
                continue;
            }
            pointsService.addPoints(userId, PointsService.POINTS_ACHIEVEMENT,
                    PointsService.TYPE_ACHIEVEMENT, a.getId(), "解锁成就：" + a.getName());
        }
    }

    /** 累计完成番茄数（只统计 COMPLETED） */
    private int countCompletedPomodoros(Long userId) {
        Long count = pomodoroRecordMapper.selectCount(
                new LambdaQueryWrapper<PomodoroRecord>()
                        .eq(PomodoroRecord::getUserId, userId)
                        .eq(PomodoroRecord::getStatus, STATUS_COMPLETED));
        return count == null ? 0 : count.intValue();
    }

    /** 连续打卡天数：今天已打卡从今天起算，否则从昨天起算；昨天也没打则断签为 0 */
    private int computeStreak(Long userId) {
        LocalDate today = LocalDate.now();
        Set<LocalDate> dates = checkInMapper.selectList(
                        new LambdaQueryWrapper<CheckIn>().eq(CheckIn::getUserId, userId))
                .stream().map(CheckIn::getCheckDate).collect(Collectors.toSet());
        LocalDate cursor = dates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
