package com.pomodoro.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 效率得分公式边界单测：三因子权重 0.5/0.3/0.2，结果必须落在 0-100。
 * <p>
 * 只挑不落在四舍五入半数点上的输入（如 82.5、57.5），避免断言依赖浮点表示细节。
 */
class FocusScoreUtilTest {

    @Test
    void noCompletedPomodoroScoresZero() {
        assertEquals(0, FocusScoreUtil.calc(0, 0, 0), "完全没数据");
        assertEquals(0, FocusScoreUtil.calc(0, 5, 25), "全中断不应给分");
        assertEquals(0, FocusScoreUtil.calc(0, 5, 0), "中断且零时长");
    }

    @Test
    void idealDayScoresHundred() {
        assertEquals(100, FocusScoreUtil.calc(8, 0, 25), "8 个番茄、每次 25 分钟、零中断 = 满分");
        assertEquals(100, FocusScoreUtil.calc(16, 0, 25), "专注量超过 8 个后被封顶，仍是满分");
        assertEquals(100, FocusScoreUtil.calc(8, 0, 120), "单次时长超过 25 分钟被封顶，仍是满分");
    }

    @Test
    void eachFactorContributesItsWeight() {
        // 完成率 1.0 -> 0.5；质量 min(25/25,1)=1 -> 0.3；量 2/8=0.25 -> 0.05
        assertEquals(85, FocusScoreUtil.calc(2, 0, 25));
        // 完成率 2/4=0.5 -> 0.25；质量 0.3；量 0.05
        assertEquals(60, FocusScoreUtil.calc(2, 2, 25));
        // 完成率 1/4=0.25 -> 0.125；质量 0.3；量 0.025
        assertEquals(45, FocusScoreUtil.calc(1, 3, 25));
        // 完成率 1 -> 0.5；质量 13/25=0.52 -> 0.156；量 0.025
        assertEquals(68, FocusScoreUtil.calc(1, 0, 13));
        // 完成率 4/6 -> 0.333；质量 0；量 0.1
        assertEquals(43, FocusScoreUtil.calc(4, 2, 0));
        // 单次很长但只有 2 个番茄：质量封顶 0.3，量 0.05
        assertEquals(85, FocusScoreUtil.calc(2, 0, 1000));
        // 完成率 8/12 -> 0.333；质量 0.3；量 0.2
        assertEquals(83, FocusScoreUtil.calc(8, 4, 25));
    }

    @Test
    void scoreStaysInRangeAndGrowsWithVolume() {
        int previous = -1;
        for (int completed = 0; completed <= 20; completed++) {
            int score = FocusScoreUtil.calc(completed, 0, 25);
            assertTrue(score >= 0 && score <= 100, "得分越界: " + score);
            assertTrue(score >= previous, "同口径下完成数越多得分不应下降");
            previous = score;
        }
    }
}
