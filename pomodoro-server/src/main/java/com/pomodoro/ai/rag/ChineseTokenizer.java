package com.pomodoro.ai.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 轻量中英文分词器，专为 BM25 检索设计。
 *
 * <p>中文没有空格，引入分词库（jieba / IK）会明显增加部署体积；这里用「单字 + 相邻双字 bigram」
 * 的常用退化方案：既能命中「论文」「专注」这类词，也能覆盖分词边界不一致的情况。
 * 英文/数字按连续字母数字切分并转小写。</p>
 */
public final class ChineseTokenizer {

    /** 单字停用词：只过滤单字，双字 bigram 仍然保留，避免误伤「效率」等词 */
    private static final Set<String> STOP_CHARS = Set.of(
            "的", "了", "我", "你", "他", "她", "它", "是", "在", "有", "和", "与",
            "这", "那", "哪", "吗", "呢", "吧", "啊", "呀", "就", "都", "也", "很");

    private ChineseTokenizer() {
    }

    /**
     * 切分为检索词项：中文单字 + 相邻双字，英文/数字连续串
     */
    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        StringBuilder ascii = new StringBuilder();
        int prevCjk = -1;

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            if (isCjk(cp)) {
                flushAscii(ascii, tokens);
                String ch = new String(Character.toChars(cp));
                if (!STOP_CHARS.contains(ch)) {
                    tokens.add(ch);
                }
                // 双字 bigram 不做停用词过滤：像「效率」「论文」这类词由它命中
                if (prevCjk >= 0) {
                    tokens.add(new String(Character.toChars(prevCjk)) + ch);
                }
                prevCjk = cp;
            } else if (Character.isLetterOrDigit(cp)) {
                prevCjk = -1;
                ascii.appendCodePoint(Character.toLowerCase(cp));
            } else {
                flushAscii(ascii, tokens);
                prevCjk = -1;
            }
        }
        flushAscii(ascii, tokens);
        return tokens;
    }

    private static void flushAscii(StringBuilder ascii, List<String> tokens) {
        if (ascii.length() > 0) {
            tokens.add(ascii.toString().toLowerCase(Locale.ROOT));
            ascii.setLength(0);
        }
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF)      // 基本汉字
                || (cp >= 0x3400 && cp <= 0x4DBF);  // 扩展 A
    }
}
