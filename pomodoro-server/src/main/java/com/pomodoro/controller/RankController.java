package com.pomodoro.controller;

import com.pomodoro.common.Result;
import com.pomodoro.service.RankService;
import com.pomodoro.vo.rank.RankVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 排行榜接口
 */
@RestController
@RequestMapping("/api/rank")
@RequiredArgsConstructor
public class RankController {

    private final RankService rankService;

    /**
     * 全局排行榜（默认前 20 名）
     */
    @GetMapping
    public Result<RankVO> rank(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(rankService.rank(limit));
    }
}
