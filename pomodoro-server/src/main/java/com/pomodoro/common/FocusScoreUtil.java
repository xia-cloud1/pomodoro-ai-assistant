package com.pomodoro.common;

/**
 * 效率得分（focus_score）公式：三因子加权，0-100
 */
public final class FocusScoreUtil {

    /** 标准番茄时长（分钟） */
    private static final int STANDARD_MINUTES = 25;

    /** 充实工作日的番茄数 */
    private static final int FULL_DAY_POMODOROS = 8;

    private FocusScoreUtil() {
    }

    /**
     * 计算某日效率得分（0-100）
     * 完成率 0.5 + 专注质量 0.3 + 专注量 0.2；无完成番茄返回 0
     *
     * @param completed       完成番茄数
     * @param interrupted     中断番茄数
     * @param avgFocusMinutes 平均单次专注（分钟）
     */
    public static int calc(int completed, int interrupted, int avgFocusMinutes) {
        int total = completed + interrupted;
        if (total == 0 || completed == 0) {
            return 0;
        }
        double completionRate = completed / (double) total;
        double quality = Math.min(avgFocusMinutes / (double) STANDARD_MINUTES, 1.0);
        double volume = Math.min(completed / (double) FULL_DAY_POMODOROS, 1.0);
        return (int) Math.round(100.0 * (0.5 * completionRate + 0.3 * quality + 0.2 * volume));
    }
}
