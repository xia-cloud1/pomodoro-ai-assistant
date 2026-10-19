package com.pomodoro.vo.ai;

import lombok.Data;

import java.util.List;

/**
 * 效率问答出参：问题 + AI 回答（未配置或失败为 null）
 */
@Data
public class AskVO {

    private String question;

    /** AI 生成的回答；未配置或失败为 null */
    private String answer;

    /** 本次回答检索到的历史记录引用，如「日报 2026-10-18」「任务 2026-10-17」 */
    private List<String> sources;
}
