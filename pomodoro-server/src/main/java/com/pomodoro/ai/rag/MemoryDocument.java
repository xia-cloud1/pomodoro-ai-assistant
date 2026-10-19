package com.pomodoro.ai.rag;

import java.time.LocalDate;

/**
 * RAG 检索单元：把用户的一段历史数据包装成可检索文档。
 *
 * @param type 文档类型（日报 / 任务 / 精力），仅用于展示引用来源
 * @param date 文档对应日期，用于时间衰减与引用展示
 * @param text 文档正文，会参与分词与关键词匹配
 */
public record MemoryDocument(String type, LocalDate date, String text) {

    /** 引用标签，例如「日报 2026-10-18」 */
    public String citation() {
        return type + " " + date;
    }
}
