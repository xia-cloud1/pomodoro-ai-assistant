package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pomodoro.dto.goal.GoalSetRequest;
import com.pomodoro.entity.Goal;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.mapper.GoalMapper;
import com.pomodoro.mapper.PomodoroRecordMapper;
import com.pomodoro.service.GoalService;
import com.pomodoro.vo.goal.GoalVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 周目标服务实现：目标落库 + 达成数实时计算
 */
@Service
@RequiredArgsConstructor
public class GoalServiceImpl implements GoalService {

    private static final String STATUS_COMPLETED = "COMPLETED";

    private final GoalMapper goalMapper;
    private final PomodoroRecordMapper pomodoroRecordMapper;

    @Override
    public GoalVO setGoal(Long userId, GoalSetRequest request) {
        LocalDate weekStart = mondayOf(request.getWeekStart() != null ? request.getWeekStart() : LocalDate.now());

        Goal existing = goalMapper.selectOne(
                new LambdaQueryWrapper<Goal>()
                        .eq(Goal::getUserId, userId)
                        .eq(Goal::getWeekStart, weekStart));
        if (existing == null) {
            Goal goal = new Goal();
            goal.setUserId(userId);
            goal.setWeekStart(weekStart);
            goal.setTargetPomodoros(request.getTargetPomodoros());
            goal.setAchievedPomodoros(0);
            goalMapper.insert(goal);
        } else {
            existing.setTargetPomodoros(request.getTargetPomodoros());
            goalMapper.updateById(existing);
        }
        return getGoal(userId, weekStart);
    }

    @Override
    public GoalVO getGoal(Long userId, LocalDate weekStart) {
        LocalDate start = mondayOf(weekStart != null ? weekStart : LocalDate.now());

        Goal goal = goalMapper.selectOne(
                new LambdaQueryWrapper<Goal>()
                        .eq(Goal::getUserId, userId)
                        .eq(Goal::getWeekStart, start));

        int target = (goal == null || goal.getTargetPomodoros() == null) ? 0 : goal.getTargetPomodoros();
        int achieved = countCompleted(userId, start);
        int progress = target <= 0 ? 0 : (int) Math.round(achieved * 100.0 / target);

        GoalVO vo = new GoalVO();
        vo.setId(goal == null ? null : goal.getId());
        vo.setWeekStart(start);
        vo.setWeekEnd(start.plusDays(6));
        vo.setTargetPomodoros(target);
        vo.setAchievedPomodoros(achieved);
        vo.setProgressPercent(progress);
        if (goal != null) {
            vo.setCreatedAt(goal.getCreatedAt());
            vo.setUpdatedAt(goal.getUpdatedAt());
        }
        return vo;
    }

    /**
     * 数本周已完成番茄数（按 start_time 归属，只计 COMPLETED）
     */
    private int countCompleted(Long userId, LocalDate weekStart) {
        Long count = pomodoroRecordMapper.selectCount(
                new LambdaQueryWrapper<PomodoroRecord>()
                        .eq(PomodoroRecord::getUserId, userId)
                        .eq(PomodoroRecord::getStatus, STATUS_COMPLETED)
                        .ge(PomodoroRecord::getStartTime, weekStart.atStartOfDay())
                        .lt(PomodoroRecord::getStartTime, weekStart.plusDays(7).atStartOfDay()));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 归一化到所在周的周一
     */
    private LocalDate mondayOf(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1);
    }
}
