package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pomodoro.common.FocusScoreUtil;
import com.pomodoro.entity.DailyStat;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.mapper.DailyStatMapper;
import com.pomodoro.mapper.PomodoroRecordMapper;
import com.pomodoro.service.DailyStatService;
import com.pomodoro.vo.dailystat.DailyStatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 日统计聚合实现：从番茄记录重算当日聚合行
 */
@Service
@RequiredArgsConstructor
public class DailyStatServiceImpl implements DailyStatService {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_INTERRUPTED = "INTERRUPTED";

    private final DailyStatMapper dailyStatMapper;
    private final PomodoroRecordMapper pomodoroRecordMapper;

    @Override
    public void aggregate(Long userId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<PomodoroRecord> records = pomodoroRecordMapper.selectList(
                new LambdaQueryWrapper<PomodoroRecord>()
                        .eq(PomodoroRecord::getUserId, userId)
                        .ge(PomodoroRecord::getStartTime, start)
                        .lt(PomodoroRecord::getStartTime, end)
                        .in(PomodoroRecord::getStatus, STATUS_COMPLETED, STATUS_INTERRUPTED));

        DailyStat stat = compute(userId, date, records);

        DailyStat existing = dailyStatMapper.selectOne(
                new LambdaQueryWrapper<DailyStat>()
                        .eq(DailyStat::getUserId, userId)
                        .eq(DailyStat::getStatDate, date));
        if (existing == null) {
            dailyStatMapper.insert(stat);
        } else {
            stat.setId(existing.getId());
            dailyStatMapper.updateById(stat);
        }
    }

    @Override
    public DailyStatVO getByDate(Long userId, LocalDate date) {
        DailyStat stat = dailyStatMapper.selectOne(
                new LambdaQueryWrapper<DailyStat>()
                        .eq(DailyStat::getUserId, userId)
                        .eq(DailyStat::getStatDate, date));
        return stat == null ? DailyStatVO.zero(date) : DailyStatVO.of(stat);
    }

    @Override
    public List<DailyStatVO> getRange(Long userId, LocalDate start, LocalDate end) {
        List<DailyStat> list = dailyStatMapper.selectList(
                new LambdaQueryWrapper<DailyStat>()
                        .eq(DailyStat::getUserId, userId)
                        .ge(DailyStat::getStatDate, start)
                        .le(DailyStat::getStatDate, end)
                        .orderByAsc(DailyStat::getStatDate));
        return list.stream().map(DailyStatVO::of).collect(Collectors.toList());
    }

    @Override
    public DailyStatVO recalc(Long userId, LocalDate date) {
        aggregate(userId, date);
        return getByDate(userId, date);
    }

    /**
     * 从记录列表计算聚合指标（v1 核心指标，focus_score/分布留 v2）
     */
    private DailyStat compute(Long userId, LocalDate date, List<PomodoroRecord> records) {
        int completed = 0;
        int interrupted = 0;
        int maxSeconds = 0;
        long totalSeconds = 0;
        for (PomodoroRecord r : records) {
            int dur = r.getDuration() == null ? 0 : r.getDuration();
            if (STATUS_COMPLETED.equals(r.getStatus())) {
                completed++;
            } else if (STATUS_INTERRUPTED.equals(r.getStatus())) {
                interrupted++;
            }
            totalSeconds += dur;
            if (dur > maxSeconds) {
                maxSeconds = dur;
            }
        }
        int total = completed + interrupted;

        DailyStat stat = new DailyStat();
        stat.setUserId(userId);
        stat.setStatDate(date);
        stat.setCompletedCount(completed);
        stat.setInterruptedCount(interrupted);
        stat.setTotalPomodoros(total);
        stat.setTotalMinutes((int) Math.round(totalSeconds / 60.0));
        stat.setMaxFocusMinutes((int) Math.round(maxSeconds / 60.0));
        stat.setAvgFocusMinutes(total == 0 ? 0 : (int) Math.round(totalSeconds / 60.0 / total));
        stat.setFocusScore(FocusScoreUtil.calc(completed, interrupted, stat.getAvgFocusMinutes()));
        stat.setCategoryDistribution(null);
        stat.setHourDistribution(null);
        return stat;
    }
}
