package com.pomodoro.service;

import com.pomodoro.common.PageResult;
import com.pomodoro.dto.pomodoro.PomodoroQueryRequest;
import com.pomodoro.dto.pomodoro.PomodoroStartRequest;
import com.pomodoro.vo.pomodoro.PomodoroVO;

/**
 * 番茄钟服务
 */
public interface PomodoroService {

    /**
     * 开始番茄钟（服务端时钟锚定开始时间）
     */
    PomodoroVO start(PomodoroStartRequest request);

    /**
     * 完成番茄钟（服务端对账后结算）
     */
    PomodoroVO complete(Long id);

    /**
     * 中断番茄钟
     */
    PomodoroVO interrupt(Long id, String reason);

    /**
     * 番茄记录详情
     */
    PomodoroVO detail(Long id);

    /**
     * 分页查询番茄记录
     */
    PageResult<PomodoroVO> page(PomodoroQueryRequest request);
}
