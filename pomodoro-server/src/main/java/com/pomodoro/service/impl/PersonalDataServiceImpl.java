package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pomodoro.common.BusinessException;
import com.pomodoro.common.ResultCode;
import com.pomodoro.entity.CheckIn;
import com.pomodoro.entity.DailyStat;
import com.pomodoro.entity.EnergyLog;
import com.pomodoro.entity.Goal;
import com.pomodoro.entity.PointsLog;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.entity.Task;
import com.pomodoro.entity.User;
import com.pomodoro.entity.UserAchievement;
import com.pomodoro.mapper.CheckInMapper;
import com.pomodoro.mapper.DailyStatMapper;
import com.pomodoro.mapper.EnergyLogMapper;
import com.pomodoro.mapper.GoalMapper;
import com.pomodoro.mapper.PointsLogMapper;
import com.pomodoro.mapper.PomodoroRecordMapper;
import com.pomodoro.mapper.TaskMapper;
import com.pomodoro.mapper.UserAchievementMapper;
import com.pomodoro.mapper.UserMapper;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.PersonalDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 个人信息导出实现：聚合当前用户全部数据，序列化为 JSON（用户信息脱敏不含密码）。
 */
@Service
@RequiredArgsConstructor
public class PersonalDataServiceImpl implements PersonalDataService {

    private final UserMapper userMapper;
    private final TaskMapper taskMapper;
    private final PomodoroRecordMapper pomodoroRecordMapper;
    private final CheckInMapper checkInMapper;
    private final EnergyLogMapper energyLogMapper;
    private final PointsLogMapper pointsLogMapper;
    private final DailyStatMapper dailyStatMapper;
    private final GoalMapper goalMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final ObjectMapper objectMapper;

    @Override
    public byte[] exportPersonalData() {
        Long userId = UserContext.requireUserId();
        User user = userMapper.selectById(userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", LocalDateTime.now().toString());
        data.put("user", maskUser(user));
        data.put("tasks", taskMapper.selectList(new LambdaQueryWrapper<Task>().eq(Task::getUserId, userId)));
        data.put("pomodoroRecords", pomodoroRecordMapper.selectList(new LambdaQueryWrapper<PomodoroRecord>().eq(PomodoroRecord::getUserId, userId)));
        data.put("checkins", checkInMapper.selectList(new LambdaQueryWrapper<CheckIn>().eq(CheckIn::getUserId, userId)));
        data.put("energyLogs", energyLogMapper.selectList(new LambdaQueryWrapper<EnergyLog>().eq(EnergyLog::getUserId, userId)));
        data.put("pointsLogs", pointsLogMapper.selectList(new LambdaQueryWrapper<PointsLog>().eq(PointsLog::getUserId, userId)));
        data.put("dailyStats", dailyStatMapper.selectList(new LambdaQueryWrapper<DailyStat>().eq(DailyStat::getUserId, userId)));
        data.put("goals", goalMapper.selectList(new LambdaQueryWrapper<Goal>().eq(Goal::getUserId, userId)));
        data.put("userAchievements", userAchievementMapper.selectList(new LambdaQueryWrapper<UserAchievement>().eq(UserAchievement::getUserId, userId)));

        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "数据导出失败");
        }
    }

    /** 用户信息脱敏：不含密码 */
    private Map<String, Object> maskUser(User user) {
        Map<String, Object> u = new LinkedHashMap<>();
        if (user == null) {
            return u;
        }
        u.put("id", user.getId());
        u.put("username", user.getUsername());
        u.put("nickname", user.getNickname());
        u.put("email", user.getEmail());
        u.put("avatar", user.getAvatar());
        u.put("level", user.getLevel());
        u.put("points", user.getPoints());
        u.put("createdAt", user.getCreatedAt());
        return u;
    }
}
