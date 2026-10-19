package com.pomodoro.ai.rag;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人历史数据的轻量 RAG 检索器。
 *
 * <p>不依赖向量库与外部 Embedding 服务，用 BM25（关键词相关度）+ 时间衰减排序：</p>
 * <ol>
 *   <li>中文按「单字 + bigram」切词，避免引入分词库；</li>
 *   <li>BM25 计算问题与每条历史记录的相关度，抑制高频词的权重；</li>
 *   <li>乘上时间衰减因子，近期记录优先（老数据仍然可能因关键词命中胜出）；</li>
 *   <li>按字符预算截断，控制提示词长度与调用成本。</li>
 * </ol>
 * <p>若问题与所有记录都不相关，则回退到最近 N 条，保证模型仍能看到用户近况。</p>
 */
@Component
public class MemoryRetriever {

    private static final double K1 = 1.2;
    private static final double B = 0.75;
    /** 时间衰减窗口：90 天前的记录不再享受加成 */
    private static final int RECENCY_WINDOW_DAYS = 90;
    /** 最近记录的加成上限：最新的记录相关度 ×1.4 */
    private static final double RECENCY_BOOST = 0.4;

    /**
     * 检索与问题最相关的历史记录。
     *
     * @param docs     候选文档
     * @param query    用户问题
     * @param today    当前日期（用于时间衰减，显式传入便于测试）
     * @param topK     最多返回条数
     * @param maxChars 上下文最大字符数（含引用前缀）
     */
    public RetrievalResult retrieve(List<MemoryDocument> docs, String query,
                                    LocalDate today, int topK, int maxChars) {
        if (docs == null || docs.isEmpty() || topK <= 0 || maxChars <= 0) {
            return RetrievalResult.empty();
        }

        List<String> queryTokens = ChineseTokenizer.tokenize(query);
        List<MemoryDocument> ranked;
        if (queryTokens.isEmpty()) {
            ranked = recentFirst(docs);
        } else {
            ranked = rankByBm25(docs, queryTokens, today);
            // 完全没命中关键词：退化为最近记录，而不是给模型空上下文
            if (ranked.isEmpty()) {
                ranked = recentFirst(docs);
            }
        }
        return pack(ranked, topK, maxChars);
    }

    /**
     * BM25 打分 + 时间衰减，只保留得分 > 0 的文档
     */
    private List<MemoryDocument> rankByBm25(List<MemoryDocument> docs, List<String> queryTokens, LocalDate today) {
        int n = docs.size();
        List<List<String>> docTokens = new ArrayList<>(n);
        Map<String, Integer> docFreq = new HashMap<>();
        int totalLength = 0;

        for (MemoryDocument doc : docs) {
            List<String> tokens = ChineseTokenizer.tokenize(doc.text());
            docTokens.add(tokens);
            totalLength += tokens.size();
            for (String token : new java.util.HashSet<>(tokens)) {
                docFreq.merge(token, 1, Integer::sum);
            }
        }
        double avgLength = n == 0 ? 0 : (double) totalLength / n;

        List<ScoredDoc> scored = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            MemoryDocument doc = docs.get(i);
            List<String> tokens = docTokens.get(i);
            Map<String, Integer> termFreq = new HashMap<>();
            for (String token : tokens) {
                termFreq.merge(token, 1, Integer::sum);
            }

            double score = 0;
            for (String term : queryTokens) {
                Integer tf = termFreq.get(term);
                if (tf == null) {
                    continue;
                }
                int df = docFreq.getOrDefault(term, 0);
                // BM25 idf：越少见的词权重越高
                double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                double lengthNorm = 1 - B + B * (tokens.isEmpty() ? 0 : tokens.size() / avgLength);
                score += idf * (tf * (K1 + 1)) / (tf + K1 * lengthNorm);
            }
            if (score > 0) {
                scored.add(new ScoredDoc(doc, score * recencyFactor(doc.date(), today)));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredDoc::score).reversed()
                .thenComparing(s -> s.doc().date(), Comparator.reverseOrder()));
        return scored.stream().map(ScoredDoc::doc).toList();
    }

    /**
     * 时间衰减：当天 1.4 倍，90 天前 1.0 倍，线性过渡
     */
    private double recencyFactor(LocalDate date, LocalDate today) {
        if (date == null) {
            return 1.0;
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(date, today));
        return 1.0 + RECENCY_BOOST * (1.0 - Math.min(days, RECENCY_WINDOW_DAYS)
                / (double) RECENCY_WINDOW_DAYS);
    }

    private List<MemoryDocument> recentFirst(List<MemoryDocument> docs) {
        return docs.stream()
                .sorted(Comparator.comparing(MemoryDocument::date,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * 按字符预算打包：逐条追加，超预算即停止；单条过长时截断，保证至少有一条上下文
     */
    private RetrievalResult pack(List<MemoryDocument> ranked, int topK, int maxChars) {
        List<MemoryDocument> picked = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (MemoryDocument doc : ranked) {
            if (picked.size() >= topK) {
                break;
            }
            String line = "- [" + doc.citation() + "] " + doc.text();
            String separator = sb.length() == 0 ? "" : "\n";
            if (sb.length() + separator.length() + line.length() > maxChars) {
                // 第一条就超长时截断，保证模型至少拿到一条上下文
                if (picked.isEmpty()) {
                    sb.append(line, 0, maxChars);
                    picked.add(doc);
                }
                break;
            }
            sb.append(separator).append(line);
            picked.add(doc);
        }
        return new RetrievalResult(List.copyOf(picked), sb.toString());
    }

    private record ScoredDoc(MemoryDocument doc, double score) {
    }
}
