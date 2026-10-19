package com.pomodoro.vo.ai;

import lombok.Data;

/**
 * 效率问答出参：问题 + AI 回答（未配置或失败为 null）
 */
@Data
public class AskVO {

    private String question;

    /** AI 生成的回答；未配置或失败为 null */
    private String answer;
}
