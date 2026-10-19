package com.pomodoro.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.mapper.PomodoroRecordMapper;
import com.pomodoro.service.DailyStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时兜底：每日凌晨重算前一日日统计，兜底实时聚合可能遗漏的情况。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyStatRepairTask {

    private final PomodoroRecordMapper pomodoroRecordMapper;
    private final DailyStatService dailyStatService;

    @Scheduled(cron = "0 30 0 * * ?")
    public void repairYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end = yesterday.plusDays(1).atStartOfDay();

        List<Object> objs = pomodoroRecordMapper.selectObjs(
                new QueryWrapper<PomodoroRecord>()
                        .select("DISTINCT user_id")
                        .ge("start_time", start)
                        .lt("start_time", end));

        int count = 0;
        for (Object obj : objs) {
            if (obj == null) {
                continue;
            }
            Long userId = ((Number) obj).longValue();
            try {
                dailyStatService.aggregate(userId, yesterday);
                count++;
            } catch (Exception e) {
                log.warn("重算昨日日统计失败 userId={}", userId, e);
            }
        }
        log.info("昨日日统计兜底重算完成，用户数={}", count);
    }
}
