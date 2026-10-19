package com.pomodoro.ai.rag;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAG 检索器单元测试：不依赖 Spring / 数据库，验证分词、相关度排序与预算控制。
 */
class MemoryRetrieverTest {

    private final MemoryRetriever retriever = new MemoryRetriever();
    private final LocalDate today = LocalDate.of(2026, 10, 19);

    /** 中文单字 + bigram，英文按连续串切分 */
    @Test
    void tokenizesChineseBigramsAndAsciiWords() {
        List<String> tokens = ChineseTokenizer.tokenize("DeepSeek 效率 100分");
        assertTrue(tokens.contains("deepseek"));
        assertTrue(tokens.contains("100"));
        assertTrue(tokens.contains("效率"), "应保留双字 bigram");
        assertTrue(tokens.contains("效"));
        assertTrue(tokens.contains("率"));
    }

    /** 关键词命中的文档排在无关文档前面，即使无关文档更新 */
    @Test
    void ranksKeywordMatchedDocumentFirst() {
        MemoryDocument paper = new MemoryDocument("任务", LocalDate.of(2026, 10, 1),
                "任务「写毕业论文」完成 12 个番茄，累计专注 300 分钟。");
        MemoryDocument fitness = new MemoryDocument("任务", LocalDate.of(2026, 10, 18),
                "任务「健身」完成 5 个番茄，累计专注 100 分钟。");

        RetrievalResult result = retriever.retrieve(
                List.of(fitness, paper), "论文进度怎么样", today, 8, 1800);

        assertFalse(result.documents().isEmpty());
        assertTrue(result.documents().get(0).text().contains("毕业论文"));
        assertTrue(result.context().contains("毕业论文"));
    }

    /** 相关度相同时，时间更近的文档优先 */
    @Test
    void newerDocumentWinsOnEqualRelevance() {
        MemoryDocument older = new MemoryDocument("日报", LocalDate.of(2026, 10, 1),
                "完成 3 个番茄，专注 75 分钟。");
        MemoryDocument newer = new MemoryDocument("日报", LocalDate.of(2026, 10, 18),
                "完成 3 个番茄，专注 75 分钟。");

        RetrievalResult result = retriever.retrieve(
                List.of(older, newer), "番茄 专注", today, 8, 1800);

        assertEquals(LocalDate.of(2026, 10, 18), result.documents().get(0).date());
    }

    /** 问题与所有记录都不相关时，回退到最近记录，避免给模型空上下文 */
    @Test
    void fallsBackToRecentDocumentsWhenNothingMatches() {
        MemoryDocument older = new MemoryDocument("日报", LocalDate.of(2026, 9, 1), "完成 1 个番茄。");
        MemoryDocument newer = new MemoryDocument("日报", LocalDate.of(2026, 10, 18), "完成 2 个番茄。");

        RetrievalResult result = retriever.retrieve(
                List.of(older, newer), "量子力学", today, 8, 1800);

        assertEquals(2, result.documents().size());
        assertEquals(LocalDate.of(2026, 10, 18), result.documents().get(0).date());
    }

    /** 上下文不能超过字符预算；第一条就超长时截断但保留一条 */
    @Test
    void contextRespectsCharacterBudget() {
        MemoryDocument doc = new MemoryDocument("日报", LocalDate.of(2026, 10, 18),
                "完成 3 个番茄，专注 75 分钟，效率分 82。");

        RetrievalResult result = retriever.retrieve(List.of(doc), "番茄", today, 8, 30);

        assertTrue(result.context().length() <= 30);
        assertEquals(1, result.documents().size());
    }

    /** 空文档集合返回空结果，不抛异常 */
    @Test
    void emptyDocumentsReturnEmptyResult() {
        RetrievalResult result = retriever.retrieve(List.of(), "番茄", today, 8, 1800);
        assertTrue(result.documents().isEmpty());
        assertEquals("", result.context());
    }
}
