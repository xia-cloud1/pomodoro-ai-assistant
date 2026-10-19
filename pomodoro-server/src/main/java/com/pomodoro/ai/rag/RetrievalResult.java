package com.pomodoro.ai.rag;

import java.util.List;

/**
 * 检索结果：命中的文档 + 已按字符预算拼好的上下文。
 *
 * @param documents 实际放入上下文的文档（按相关度倒序），可直接作为引用来源返回
 * @param context   拼接后的上下文文本，直接塞进提示词
 */
public record RetrievalResult(List<MemoryDocument> documents, String context) {

    public static RetrievalResult empty() {
        return new RetrievalResult(List.of(), "");
    }
}
