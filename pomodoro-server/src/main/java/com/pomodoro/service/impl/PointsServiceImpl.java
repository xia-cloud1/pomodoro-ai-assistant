package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pomodoro.common.PageResult;
import com.pomodoro.entity.PointsLog;
import com.pomodoro.entity.User;
import com.pomodoro.mapper.PointsLogMapper;
import com.pomodoro.mapper.UserMapper;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.PointsService;
import com.pomodoro.vo.points.PointsLogVO;
import com.pomodoro.vo.points.PointsSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 积分服务实现：level = points/100 + 1，读-改-写 user.points/level
 */
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

    private static final int LEVEL_POINTS = 100;

    private final UserMapper userMapper;
    private final PointsLogMapper pointsLogMapper;

    @Override
    public PointsSummaryVO summary() {
        Long userId = UserContext.requireUserId();
        User user = userMapper.selectById(userId);
        int points = user == null || user.getPoints() == null ? 0 : user.getPoints();

        PointsSummaryVO vo = new PointsSummaryVO();
        vo.setPoints(points);
        vo.setLevel(points / LEVEL_POINTS + 1);
        vo.setPointsToNextLevel(LEVEL_POINTS - points % LEVEL_POINTS);
        vo.setProgressPercent(points % LEVEL_POINTS);
        return vo;
    }

    @Override
    public PageResult<PointsLogVO> log(int pageNum, int pageSize) {
        Long userId = UserContext.requireUserId();
        Page<PointsLog> page = new Page<>(pageNum, pageSize);
        Page<PointsLog> result = pointsLogMapper.selectPage(page,
                new LambdaQueryWrapper<PointsLog>()
                        .eq(PointsLog::getUserId, userId)
                        .orderByDesc(PointsLog::getId));
        List<PointsLogVO> records = result.getRecords().stream().map(PointsLogVO::of).collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    @Transactional
    public void addPoints(Long userId, int change, String pointsType, Long refId, String note) {
        if (userId == null) {
            return;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        int newPoints = (user.getPoints() == null ? 0 : user.getPoints()) + change;
        user.setPoints(newPoints);
        user.setLevel(newPoints / LEVEL_POINTS + 1);
        userMapper.updateById(user);

        PointsLog log = new PointsLog();
        log.setUserId(userId);
        log.setPointsChange(change);
        log.setPointsType(pointsType);
        log.setRefId(refId);
        log.setNote(note);
        pointsLogMapper.insert(log);
    }
}
