package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pomodoro.common.BusinessException;
import com.pomodoro.common.ResultCode;
import com.pomodoro.entity.CheckIn;
import com.pomodoro.mapper.CheckInMapper;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.AchievementService;
import com.pomodoro.service.CheckInService;
import com.pomodoro.service.PointsService;
import com.pomodoro.vo.checkin.CheckInStatusVO;
import com.pomodoro.vo.checkin.CheckInVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 打卡服务实现：连续天数从 check_in 表实时计算，断签即 0
 */
@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {

    private final CheckInMapper checkInMapper;
    private final AchievementService achievementService;
    private final PointsService pointsService;

    @Override
    public CheckInStatusVO checkIn() {
        Long userId = UserContext.requireUserId();
        LocalDate today = LocalDate.now();

        Long count = checkInMapper.selectCount(
                new LambdaQueryWrapper<CheckIn>()
                        .eq(CheckIn::getUserId, userId)
                        .eq(CheckIn::getCheckDate, today));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "今天已打卡");
        }

        CheckIn checkIn = new CheckIn();
        checkIn.setUserId(userId);
        checkIn.setCheckDate(today);
        checkIn.setIsMakeup(0);
        checkInMapper.insert(checkIn);
        pointsService.addPoints(userId, PointsService.POINTS_CHECKIN,
                PointsService.TYPE_CHECKIN, checkIn.getId(), "每日打卡");
        achievementService.checkAndUnlock(userId);

        return buildStatus(userId);
    }

    @Override
    public CheckInStatusVO makeup(LocalDate date) {
        Long userId = UserContext.requireUserId();
        LocalDate today = LocalDate.now();

        if (date == null || !date.isBefore(today)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "补签日期必须是过去的日期");
        }

        Long count = checkInMapper.selectCount(
                new LambdaQueryWrapper<CheckIn>()
                        .eq(CheckIn::getUserId, userId)
                        .eq(CheckIn::getCheckDate, date));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该日期已打卡");
        }

        CheckIn checkIn = new CheckIn();
        checkIn.setUserId(userId);
        checkIn.setCheckDate(date);
        checkIn.setIsMakeup(1);
        checkInMapper.insert(checkIn);
        pointsService.addPoints(userId, PointsService.POINTS_CHECKIN,
                PointsService.TYPE_CHECKIN, checkIn.getId(), "补签打卡");
        achievementService.checkAndUnlock(userId);

        return buildStatus(userId);
    }

    @Override
    public CheckInStatusVO status() {
        return buildStatus(UserContext.requireUserId());
    }

    private CheckInStatusVO buildStatus(Long userId) {
        LocalDate today = LocalDate.now();
        List<CheckIn> all = checkInMapper.selectList(
                new LambdaQueryWrapper<CheckIn>()
                        .eq(CheckIn::getUserId, userId)
                        .orderByDesc(CheckIn::getCheckDate));

        Set<LocalDate> dates = all.stream().map(CheckIn::getCheckDate).collect(Collectors.toSet());

        CheckInStatusVO vo = new CheckInStatusVO();
        vo.setCheckedToday(dates.contains(today));
        vo.setStreakDays(computeStreak(today, dates));
        vo.setLastCheckinDate(all.isEmpty() ? null : all.get(0).getCheckDate());
        vo.setRecords(all.stream().limit(30).map(CheckInVO::of).collect(Collectors.toList()));
        return vo;
    }

    /**
     * 连续天数：今天已打卡从今天起算，否则从昨天起算；昨天也没打则断签为 0
     */
    private int computeStreak(LocalDate today, Set<LocalDate> dates) {
        LocalDate cursor = dates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
