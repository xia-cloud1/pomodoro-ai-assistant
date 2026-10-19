package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pomodoro.common.BusinessException;
import com.pomodoro.common.ResultCode;
import com.pomodoro.dto.energy.EnergySaveRequest;
import com.pomodoro.entity.EnergyLog;
import com.pomodoro.mapper.EnergyLogMapper;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.EnergyService;
import com.pomodoro.vo.energy.EnergyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 精力自评服务实现：按 (user_id, log_date) upsert
 */
@Service
@RequiredArgsConstructor
public class EnergyServiceImpl implements EnergyService {

    /** 时间跨度上限（天） */
    private static final int MAX_RANGE_DAYS = 366;

    private final EnergyLogMapper energyLogMapper;

    @Override
    public EnergyVO save(EnergySaveRequest request) {
        Long userId = UserContext.requireUserId();
        LocalDate date = request.getDate() != null ? request.getDate() : LocalDate.now();
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能自评未来日期");
        }

        EnergyLog existing = energyLogMapper.selectOne(
                new LambdaQueryWrapper<EnergyLog>()
                        .eq(EnergyLog::getUserId, userId)
                        .eq(EnergyLog::getLogDate, date));
        if (existing == null) {
            EnergyLog log = new EnergyLog();
            log.setUserId(userId);
            log.setLogDate(date);
            log.setEnergyLevel(request.getEnergyLevel());
            log.setMood(request.getMood());
            log.setNote(request.getNote());
            energyLogMapper.insert(log);
            return EnergyVO.of(log);
        }
        existing.setEnergyLevel(request.getEnergyLevel());
        existing.setMood(request.getMood());
        existing.setNote(request.getNote());
        // updateById 默认忽略 null 字段，改用显式 set 实现真正的「覆盖」（null 会清空 mood/note）
        energyLogMapper.update(null, new LambdaUpdateWrapper<EnergyLog>()
                .eq(EnergyLog::getId, existing.getId())
                .set(EnergyLog::getEnergyLevel, request.getEnergyLevel())
                .set(EnergyLog::getMood, request.getMood())
                .set(EnergyLog::getNote, request.getNote()));
        return EnergyVO.of(existing);
    }

    @Override
    public EnergyVO get(LocalDate date) {
        Long userId = UserContext.requireUserId();
        LocalDate d = date != null ? date : LocalDate.now();
        EnergyLog log = energyLogMapper.selectOne(
                new LambdaQueryWrapper<EnergyLog>()
                        .eq(EnergyLog::getUserId, userId)
                        .eq(EnergyLog::getLogDate, d));
        return log == null ? null : EnergyVO.of(log);
    }

    @Override
    public List<EnergyVO> range(LocalDate start, LocalDate end) {
        Long userId = UserContext.requireUserId();
        LocalDate today = LocalDate.now();
        if (end == null) {
            end = today;
        }
        if (start == null) {
            start = end.minusDays(29);
        }
        if (start.isAfter(end)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "时间跨度不能超过 " + MAX_RANGE_DAYS + " 天");
        }
        List<EnergyLog> list = energyLogMapper.selectList(
                new LambdaQueryWrapper<EnergyLog>()
                        .eq(EnergyLog::getUserId, userId)
                        .ge(EnergyLog::getLogDate, start)
                        .le(EnergyLog::getLogDate, end)
                        .orderByAsc(EnergyLog::getLogDate));
        return list.stream().map(EnergyVO::of).collect(Collectors.toList());
    }
}
