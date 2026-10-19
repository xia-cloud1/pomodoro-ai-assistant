package com.pomodoro.controller;

import com.pomodoro.common.Result;
import com.pomodoro.dto.energy.EnergySaveRequest;
import com.pomodoro.service.EnergyService;
import com.pomodoro.vo.energy.EnergyVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

/**
 * 精力自评接口
 */
@RestController
@RequestMapping("/api/energy")
@RequiredArgsConstructor
public class EnergyController {

    private final EnergyService energyService;

    /**
     * 提交/更新精力自评
     */
    @PostMapping
    public Result<EnergyVO> save(@Valid @RequestBody EnergySaveRequest request) {
        return Result.success(energyService.save(request));
    }

    /**
     * 查询单日自评（默认今天）
     */
    @GetMapping
    public Result<EnergyVO> get(@RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate date) {
        return Result.success(energyService.get(date));
    }

    /**
     * 查询范围自评（默认近 30 天）
     */
    @GetMapping("/range")
    public Result<List<EnergyVO>> range(
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate end) {
        return Result.success(energyService.range(start, end));
    }
}
