package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pomodoro.common.BusinessException;
import com.pomodoro.common.PageResult;
import com.pomodoro.common.ResultCode;
import com.pomodoro.dto.pomodoro.PomodoroQueryRequest;
import com.pomodoro.dto.pomodoro.PomodoroStartRequest;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.entity.Task;
import com.pomodoro.mapper.PomodoroRecordMapper;
import com.pomodoro.mapper.TaskMapper;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.AchievementService;
import com.pomodoro.service.DailyStatService;
import com.pomodoro.service.PointsService;
import com.pomodoro.service.PomodoroService;
import com.pomodoro.vo.pomodoro.PomodoroVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 番茄钟服务实现：计时状态机 + 服务端对账防作弊
 */
@Service
@RequiredArgsConstructor
public class PomodoroServiceImpl implements PomodoroService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_INTERRUPTED = "INTERRUPTED";

    /** 对账边界：完成至少 30 秒；单次最多 4 小时 */
    private static final int MIN_COMPLETE_SECONDS = 30;
    private static final int MAX_DURATION_SECONDS = 4 * 3600;

    private final PomodoroRecordMapper pomodoroRecordMapper;
    private final TaskMapper taskMapper;
    private final DailyStatService dailyStatService;
    private final AchievementService achievementService;
    private final PointsService pointsService;

    @Override
    public PomodoroVO start(PomodoroStartRequest request) {
        Long userId = UserContext.requireUserId();

        // 同一用户同时只能有一个进行中的番茄钟
        Long running = pomodoroRecordMapper.selectCount(
                new LambdaQueryWrapper<PomodoroRecord>()
                        .eq(PomodoroRecord::getUserId, userId)
                        .eq(PomodoroRecord::getStatus, STATUS_RUNNING));
        if (running != null && running > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "已有进行中的番茄钟");
        }

        // 绑定任务需校验归属
        if (request.getTaskId() != null) {
            Task task = taskMapper.selectById(request.getTaskId());
            if (task == null || !task.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "任务不存在");
            }
        }

        PomodoroRecord record = new PomodoroRecord();
        record.setUserId(userId);
        record.setTaskId(request.getTaskId());
        record.setWhiteNoise(request.getWhiteNoise());
        // 截断到整秒：start_time 列为 DATETIME（无小数位），MySQL 会把 ≥0.5 秒的小数进位到
        // 下一秒，导致随后按 now() 对账时算出负时长。整秒写入可保证存取一致。
        record.setStartTime(LocalDateTime.now().withNano(0));
        record.setStatus(STATUS_RUNNING);
        record.setDuration(0);
        try {
            pomodoroRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 上面的 selectCount 到 insert 之间存在竞态窗口（并发点击/多端同时开始）。
            // 表上的生成列唯一索引 uk_pr_running_user 是最终兜底：同一用户第二条 RUNNING
            // 会触发 1062，这里统一转换为业务 409。
            throw new BusinessException(ResultCode.CONFLICT, "已有进行中的番茄钟");
        }
        return PomodoroVO.of(record);
    }

    @Override
    public PomodoroVO complete(Long id) {
        PomodoroRecord record = getOwnedRunning(id);
        LocalDateTime end = LocalDateTime.now();
        int duration = reconcile(record.getStartTime(), end, MIN_COMPLETE_SECONDS);

        record.setEndTime(end);
        record.setDuration(duration);
        record.setStatus(STATUS_COMPLETED);
        pomodoroRecordMapper.updateById(record);

        incrementTaskActual(record);
        dailyStatService.aggregate(record.getUserId(), record.getStartTime().toLocalDate());
        pointsService.addPoints(record.getUserId(), PointsService.POINTS_POMODORO,
                PointsService.TYPE_POMODORO, record.getId(), "完成番茄");
        achievementService.checkAndUnlock(record.getUserId());
        return PomodoroVO.of(record);
    }

    @Override
    public PomodoroVO interrupt(Long id, String reason) {
        PomodoroRecord record = getOwnedRunning(id);
        LocalDateTime end = LocalDateTime.now();
        int duration = reconcile(record.getStartTime(), end, 0);

        record.setEndTime(end);
        record.setDuration(duration);
        record.setStatus(STATUS_INTERRUPTED);
        record.setInterruptReason(reason);
        pomodoroRecordMapper.updateById(record);
        dailyStatService.aggregate(record.getUserId(), record.getStartTime().toLocalDate());
        return PomodoroVO.of(record);
    }

    @Override
    public PomodoroVO detail(Long id) {
        PomodoroRecord record = pomodoroRecordMapper.selectById(id);
        if (record == null || !record.getUserId().equals(UserContext.requireUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "番茄记录不存在");
        }
        return PomodoroVO.of(record);
    }

    @Override
    public PageResult<PomodoroVO> page(PomodoroQueryRequest request) {
        Long userId = UserContext.requireUserId();
        Page<PomodoroRecord> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<PomodoroRecord> wrapper = new LambdaQueryWrapper<PomodoroRecord>()
                .eq(PomodoroRecord::getUserId, userId)
                .eq(request.getStatus() != null, PomodoroRecord::getStatus, request.getStatus())
                .eq(request.getTaskId() != null, PomodoroRecord::getTaskId, request.getTaskId())
                .orderByDesc(PomodoroRecord::getStartTime);

        Page<PomodoroRecord> result = pomodoroRecordMapper.selectPage(page, wrapper);
        List<PomodoroVO> records = result.getRecords().stream().map(PomodoroVO::of).collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /**
     * 取当前用户进行中的番茄钟，不存在/非本人/已结束分别报错
     */
    private PomodoroRecord getOwnedRunning(Long id) {
        PomodoroRecord record = pomodoroRecordMapper.selectById(id);
        if (record == null || !record.getUserId().equals(UserContext.requireUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "番茄记录不存在");
        }
        if (!STATUS_RUNNING.equals(record.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "该番茄钟已结束");
        }
        return record;
    }

    /**
     * 服务端对账：计算时长并校验上下限（防作弊）
     */
    private int reconcile(LocalDateTime start, LocalDateTime end, int minSeconds) {
        // 兜底：start 取自数据库（秒精度）而 end 取自 now()，极端情况下可能算出负值
        long seconds = Math.max(0, Duration.between(start, end).getSeconds());
        if (seconds < minSeconds) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "专注时长过短，无法记录");
        }
        if (seconds > MAX_DURATION_SECONDS) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "专注时长异常，超过上限");
        }
        return (int) seconds;
    }

    /**
     * 完成时回写任务实际番茄数（供「预估 vs 实际」偏差分析）
     */
    private void incrementTaskActual(PomodoroRecord record) {
        if (record.getTaskId() == null) {
            return;
        }
        Task task = taskMapper.selectById(record.getTaskId());
        if (task == null || !task.getUserId().equals(record.getUserId())) {
            return;
        }
        int actual = task.getActualPomodoros() == null ? 0 : task.getActualPomodoros();
        task.setActualPomodoros(actual + 1);
        taskMapper.updateById(task);
    }
}
