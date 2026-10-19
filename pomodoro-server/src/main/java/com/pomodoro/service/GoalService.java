package com.pomodoro.service;

import com.pomodoro.dto.goal.GoalSetRequest;
import com.pomodoro.vo.goal.GoalVO;

import java.time.LocalDate;

/**
 * 周目标服务
 */
public interface GoalService {

    /**
     * 设置/更新周目标（按周一起始 upsert）
     */
    GoalVO setGoal(Long userId, GoalSetRequest request);

    /**
     * 查询周目标状态（weekStart 为空则默认本周）
     */
    GoalVO getGoal(Long userId, LocalDate weekStart);
}
