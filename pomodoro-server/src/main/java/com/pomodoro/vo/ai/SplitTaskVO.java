package com.pomodoro.vo.ai;

import lombok.Data;

import java.util.List;

/**
 * AI 任务拆分出参：子任务列表（未配置或失败为空列表）
 */
@Data
public class SplitTaskVO {

    private List<String> subtasks;
}
