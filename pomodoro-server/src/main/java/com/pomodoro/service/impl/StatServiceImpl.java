package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pomodoro.common.BusinessException;
import com.pomodoro.common.FocusScoreUtil;
import com.pomodoro.common.ResultCode;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.mapper.PomodoroRecordMapper;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.StatService;
import com.pomodoro.vo.stat.BestDayVO;
import com.pomodoro.vo.stat.HeatmapCellVO;
import com.pomodoro.vo.stat.HourDistributionVO;
import com.pomodoro.vo.stat.ReportDayVO;
import com.pomodoro.vo.stat.ReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统计可视化服务实现：直接读 pomodoro_record 实时聚合，不依赖 daily_stat
 */
@Service
@RequiredArgsConstructor
public class StatServiceImpl implements StatService {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_INTERRUPTED = "INTERRUPTED";
    /** 时间跨度上限（天） */
    private static final int MAX_RANGE_DAYS = 366;

    private final PomodoroRecordMapper pomodoroRecordMapper;

    @Override
    public List<HeatmapCellVO> heatmap(LocalDate start, LocalDate end) {
        Long userId = UserContext.requireUserId();
        LocalDate[] range = normalizeRange(start, end, 365);
        start = range[0];
        end = range[1];

        List<PomodoroRecord> records = selectCompleted(userId, start, end);
        Map<LocalDate, Long> countByDate = records.stream()
                .collect(Collectors.groupingBy(r -> r.getStartTime().toLocalDate(), Collectors.counting()));

        List<HeatmapCellVO> result = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            HeatmapCellVO cell = new HeatmapCellVO();
            cell.setDate(d);
            cell.setCount(countByDate.getOrDefault(d, 0L).intValue());
            result.add(cell);
        }
        return result;
    }

    @Override
    public List<HourDistributionVO> hourDistribution(LocalDate start, LocalDate end) {
        Long userId = UserContext.requireUserId();
        LocalDate[] range = normalizeRange(start, end, 30);
        start = range[0];
        end = range[1];

        List<PomodoroRecord> records = selectCompleted(userId, start, end);
        Map<Integer, List<PomodoroRecord>> byHour = records.stream()
                .collect(Collectors.groupingBy(r -> r.getStartTime().getHour()));

        List<HourDistributionVO> result = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            List<PomodoroRecord> list = byHour.getOrDefault(hour, List.of());
            long seconds = list.stream()
                    .mapToLong(r -> r.getDuration() == null ? 0 : r.getDuration()).sum();
            HourDistributionVO vo = new HourDistributionVO();
            vo.setHour(hour);
            vo.setCount(list.size());
            vo.setMinutes((int) Math.round(seconds / 60.0));
            result.add(vo);
        }
        return result;
    }

    @Override
    public ReportVO report(Integer year, Integer month) {
        Long userId = UserContext.requireUserId();
        if (year == null || year < 2000 || year > 2100) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "年份需在 2000-2100 之间");
        }
        boolean isMonth = month != null;
        if (isMonth && (month < 1 || month > 12)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "月份需在 1-12 之间");
        }

        LocalDate start;
        LocalDate end;
        if (isMonth) {
            start = LocalDate.of(year, month, 1);
            end = start.withDayOfMonth(start.lengthOfMonth());
        } else {
            start = LocalDate.of(year, 1, 1);
            end = LocalDate.of(year, 12, 31);
        }

        List<PomodoroRecord> records = pomodoroRecordMapper.selectList(
                new LambdaQueryWrapper<PomodoroRecord>()
                        .eq(PomodoroRecord::getUserId, userId)
                        .in(PomodoroRecord::getStatus, STATUS_COMPLETED, STATUS_INTERRUPTED)
                        .ge(PomodoroRecord::getStartTime, start.atStartOfDay())
                        .lt(PomodoroRecord::getStartTime, end.plusDays(1).atStartOfDay()));
        Map<LocalDate, List<PomodoroRecord>> byDate = records.stream()
                .collect(Collectors.groupingBy(r -> r.getStartTime().toLocalDate()));

        int completed = 0;
        int interrupted = 0;
        long totalSeconds = 0;
        int activeDays = 0;
        long focusScoreSum = 0;
        int bestCount = 0;
        LocalDate bestDate = null;

        List<ReportDayVO> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            List<PomodoroRecord> list = byDate.getOrDefault(d, List.of());
            int c = 0;
            int i = 0;
            long seconds = 0;
            for (PomodoroRecord r : list) {
                int dur = r.getDuration() == null ? 0 : r.getDuration();
                if (STATUS_COMPLETED.equals(r.getStatus())) {
                    c++;
                } else if (STATUS_INTERRUPTED.equals(r.getStatus())) {
                    i++;
                }
                seconds += dur;
            }
            int total = c + i;
            int minutes = (int) Math.round(seconds / 60.0);
            int avgMinutes = total == 0 ? 0 : (int) Math.round(seconds / 60.0 / total);
            int focusScore = FocusScoreUtil.calc(c, i, avgMinutes);

            ReportDayVO day = new ReportDayVO();
            day.setDate(d);
            day.setCompletedCount(c);
            day.setInterruptedCount(i);
            day.setTotalMinutes(minutes);
            day.setFocusScore(focusScore);
            days.add(day);

            if (total > 0) {
                activeDays++;
                completed += c;
                interrupted += i;
                totalSeconds += seconds;
                focusScoreSum += focusScore;
                if (c > bestCount) {
                    bestCount = c;
                    bestDate = d;
                }
            }
        }

        ReportVO vo = new ReportVO();
        vo.setType(isMonth ? "MONTH" : "YEAR");
        vo.setStartDate(start);
        vo.setEndDate(end);
        vo.setDailyBreakdown(days);
        vo.setCompletedCount(completed);
        vo.setInterruptedCount(interrupted);
        vo.setTotalPomodoros(completed + interrupted);
        vo.setTotalMinutes((int) Math.round(totalSeconds / 60.0));
        vo.setActiveDays(activeDays);
        vo.setAvgDailyMinutes(activeDays == 0 ? 0 : (int) Math.round(totalSeconds / 60.0 / activeDays));
        vo.setCompletionRate(completed + interrupted == 0 ? 0 : (int) Math.round(completed * 100.0 / (completed + interrupted)));
        vo.setAvgFocusScore(activeDays == 0 ? 0 : (int) Math.round(focusScoreSum / (double) activeDays));
        if (bestDate != null) {
            BestDayVO best = new BestDayVO();
            best.setDate(bestDate);
            best.setCount(bestCount);
            vo.setBestDay(best);
        }
        return vo;
    }

    /** 取范围内当前用户已完成番茄记录 */
    private List<PomodoroRecord> selectCompleted(Long userId, LocalDate start, LocalDate end) {
        return pomodoroRecordMapper.selectList(
                new LambdaQueryWrapper<PomodoroRecord>()
                        .eq(PomodoroRecord::getUserId, userId)
                        .eq(PomodoroRecord::getStatus, STATUS_COMPLETED)
                        .ge(PomodoroRecord::getStartTime, start.atStartOfDay())
                        .lt(PomodoroRecord::getStartTime, end.plusDays(1).atStartOfDay()));
    }

    /** 归一化日期范围：补默认值、校验顺序、限制跨度 */
    private LocalDate[] normalizeRange(LocalDate start, LocalDate end, int defaultDays) {
        LocalDate today = LocalDate.now();
        if (end == null) {
            end = today;
        }
        if (start == null) {
            start = end.minusDays(defaultDays - 1L);
        }
        if (start.isAfter(end)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "时间跨度不能超过 " + MAX_RANGE_DAYS + " 天");
        }
        return new LocalDate[]{start, end};
    }
}
