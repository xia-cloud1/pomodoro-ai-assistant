package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pomodoro.common.BusinessException;
import com.pomodoro.common.ResultCode;
import com.pomodoro.entity.CheckIn;
import com.pomodoro.entity.DailyStat;
import com.pomodoro.entity.EnergyLog;
import com.pomodoro.entity.Goal;
import com.pomodoro.entity.PointsLog;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.entity.Tag;
import com.pomodoro.entity.User;
import com.pomodoro.entity.UserAchievement;
import com.pomodoro.mapper.CheckInMapper;
import com.pomodoro.mapper.DailyStatMapper;
import com.pomodoro.mapper.EnergyLogMapper;
import com.pomodoro.mapper.GoalMapper;
import com.pomodoro.mapper.PointsLogMapper;
import com.pomodoro.mapper.PomodoroRecordMapper;
import com.pomodoro.mapper.TagMapper;
import com.pomodoro.mapper.TaskMapper;
import com.pomodoro.mapper.TaskTagMapper;
import com.pomodoro.mapper.UserAchievementMapper;
import com.pomodoro.mapper.UserMapper;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号注销实现：密码二次确认后，事务内物理删除用户全部个人数据。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final TaskMapper taskMapper;
    private final TagMapper tagMapper;
    private final TaskTagMapper taskTagMapper;
    private final PomodoroRecordMapper pomodoroRecordMapper;
    private final CheckInMapper checkInMapper;
    private final DailyStatMapper dailyStatMapper;
    private final EnergyLogMapper energyLogMapper;
    private final GoalMapper goalMapper;
    private final PointsLogMapper pointsLogMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void deleteAccount(String password) {
        Long userId = UserContext.requireUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不存在");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码错误");
        }

        // 1. 清理任务-标签关联（物理删除，含已软删任务）
        taskTagMapper.deleteByUserId(userId);

        // 2. 物理删除任务（绕过 @TableLogic 软删）
        taskMapper.physicallyDeleteByUserId(userId);

        // 3. 删除其余业务数据（均按 userId 物理删除）
        tagMapper.delete(new LambdaQueryWrapper<Tag>().eq(Tag::getUserId, userId));
        pomodoroRecordMapper.delete(new LambdaQueryWrapper<PomodoroRecord>().eq(PomodoroRecord::getUserId, userId));
        checkInMapper.delete(new LambdaQueryWrapper<CheckIn>().eq(CheckIn::getUserId, userId));
        dailyStatMapper.delete(new LambdaQueryWrapper<DailyStat>().eq(DailyStat::getUserId, userId));
        energyLogMapper.delete(new LambdaQueryWrapper<EnergyLog>().eq(EnergyLog::getUserId, userId));
        goalMapper.delete(new LambdaQueryWrapper<Goal>().eq(Goal::getUserId, userId));
        pointsLogMapper.delete(new LambdaQueryWrapper<PointsLog>().eq(PointsLog::getUserId, userId));
        userAchievementMapper.delete(new LambdaQueryWrapper<UserAchievement>().eq(UserAchievement::getUserId, userId));

        // 4. 最后删除用户
        userMapper.deleteById(userId);
    }
}
