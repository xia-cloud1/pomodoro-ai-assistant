package com.pomodoro.controller;

import com.pomodoro.common.PageResult;
import com.pomodoro.common.Result;
import com.pomodoro.dto.pomodoro.PomodoroInterruptRequest;
import com.pomodoro.dto.pomodoro.PomodoroQueryRequest;
import com.pomodoro.dto.pomodoro.PomodoroStartRequest;
import com.pomodoro.service.PomodoroService;
import com.pomodoro.vo.pomodoro.PomodoroVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 番茄钟接口
 */
@RestController
@RequestMapping("/api/pomodoro")
@RequiredArgsConstructor
public class PomodoroController {

    private final PomodoroService pomodoroService;

    /**
     * 开始番茄钟
     */
    @PostMapping("/start")
    public Result<PomodoroVO> start(@Valid @RequestBody PomodoroStartRequest request) {
        return Result.success(pomodoroService.start(request));
    }

    /**
     * 完成番茄钟
     */
    @PostMapping("/{id}/complete")
    public Result<PomodoroVO> complete(@PathVariable Long id) {
        return Result.success(pomodoroService.complete(id));
    }

    /**
     * 中断番茄钟
     */
    @PostMapping("/{id}/interrupt")
    public Result<PomodoroVO> interrupt(@PathVariable Long id,
                                        @RequestBody(required = false) PomodoroInterruptRequest request) {
        return Result.success(pomodoroService.interrupt(id, request != null ? request.getInterruptReason() : null));
    }

    /**
     * 番茄记录详情
     */
    @GetMapping("/{id}")
    public Result<PomodoroVO> detail(@PathVariable Long id) {
        return Result.success(pomodoroService.detail(id));
    }

    /**
     * 分页查询番茄记录
     */
    @GetMapping("/page")
    public Result<PageResult<PomodoroVO>> page(PomodoroQueryRequest request) {
        return Result.success(pomodoroService.page(request));
    }
}
