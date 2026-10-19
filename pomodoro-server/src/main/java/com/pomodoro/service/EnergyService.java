package com.pomodoro.service;

import com.pomodoro.dto.energy.EnergySaveRequest;
import com.pomodoro.vo.energy.EnergyVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 精力自评服务
 */
public interface EnergyService {

    /**
     * 提交/更新某日精力自评（按 user_id + log_date upsert）
     */
    EnergyVO save(EnergySaveRequest request);

    /**
     * 查询某日自评（默认今天，无记录返回 null）
     */
    EnergyVO get(LocalDate date);

    /**
     * 查询范围自评（升序，供精力趋势图）
     */
    List<EnergyVO> range(LocalDate start, LocalDate end);
}
