package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pomodoro.common.BusinessException;
import com.pomodoro.common.ResultCode;
import com.pomodoro.entity.PomodoroRecord;
import com.pomodoro.mapper.PomodoroRecordMapper;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.CheckInService;
import com.pomodoro.service.DailyStatService;
import com.pomodoro.service.PointsService;
import com.pomodoro.service.ShareService;
import com.pomodoro.vo.checkin.CheckInStatusVO;
import com.pomodoro.vo.dailystat.DailyStatVO;
import com.pomodoro.vo.points.PointsSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 分享海报实现：Java2D 渲染 PNG
 */
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private static final int WIDTH = 720;
    private static final int HEIGHT = 1080;
    private static final String[] FONT_CANDIDATES = {
            "Microsoft YaHei", "Noto Sans CJK SC", "PingFang SC", "Source Han Sans SC"
    };
    private static volatile String resolvedFont;
    private static final String STATUS_COMPLETED = "COMPLETED";

    private static final Color COLOR_LABEL = new Color(0xB8B8CC);
    private static final Color COLOR_ACCENT = new Color(0xFFC36B);
    private static final Color COLOR_DIVIDER = new Color(0x4A4A66);

    private final DailyStatService dailyStatService;
    private final CheckInService checkInService;
    private final PointsService pointsService;
    private final PomodoroRecordMapper pomodoroRecordMapper;

    @Override
    public byte[] poster() {
        Long userId = UserContext.requireUserId();
        DailyStatVO today = dailyStatService.getByDate(userId, LocalDate.now());
        CheckInStatusVO checkin = checkInService.status();
        PointsSummaryVO points = pointsService.summary();
        long totalCompleted = countCompleted(userId);

        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 背景渐变
            g.setPaint(new GradientPaint(0, 0, new Color(0x3B3B6D), 0, HEIGHT, new Color(0x17172B)));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // 标题
            g.setColor(Color.WHITE);
            drawCentered(g, "我的专注报告", WIDTH / 2, 130, bold(58));

            // 效率得分
            g.setColor(COLOR_LABEL);
            drawCentered(g, "效率得分", WIDTH / 2, 215, font(28));
            g.setColor(COLOR_ACCENT);
            drawCentered(g, String.valueOf(nvl(today.getFocusScore())), WIDTH / 2, 355, bold(150));
            g.setColor(COLOR_LABEL);
            drawCentered(g, "/ 100", WIDTH / 2, 420, font(30));

            // 分隔线
            g.setColor(COLOR_DIVIDER);
            g.fillRoundRect(120, 500, WIDTH - 240, 3, 3, 3);

            // 明细行
            int y = 580;
            y = drawRow(g, "今日完成", today.getCompletedCount() + " 个番茄", y);
            y = drawRow(g, "今日专注", today.getTotalMinutes() + " 分钟", y);
            y = drawRow(g, "累计完成", totalCompleted + " 个番茄", y);
            y = drawRow(g, "连续打卡", nvl(checkin.getStreakDays()) + " 天", y);
            drawRow(g, "等级积分", "Lv " + points.getLevel() + " · " + points.getPoints() + " 分", y);

            // 页脚
            g.setColor(COLOR_LABEL);
            drawCentered(g, "番茄钟 · AI 效率分析系统", WIDTH / 2, HEIGHT - 90, font(24));
            drawCentered(g, LocalDate.now().toString(), WIDTH / 2, HEIGHT - 50, font(22));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "海报生成失败");
        } finally {
            g.dispose();
        }
    }

    private long countCompleted(Long userId) {
        Long count = pomodoroRecordMapper.selectCount(
                new LambdaQueryWrapper<PomodoroRecord>()
                        .eq(PomodoroRecord::getUserId, userId)
                        .eq(PomodoroRecord::getStatus, STATUS_COMPLETED));
        return count == null ? 0 : count;
    }

    /** 一行「左标签右数值」，返回下一行 y */
    private int drawRow(Graphics2D g, String label, String value, int y) {
        g.setColor(COLOR_LABEL);
        g.setFont(font(30));
        g.drawString(label, 120, y);

        g.setColor(Color.WHITE);
        g.setFont(bold(30));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(value, WIDTH - 120 - fm.stringWidth(value), y);
        return y + 82;
    }

    private void drawCentered(Graphics2D g, String text, int cx, int y, Font f) {
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics(f);
        g.drawString(text, cx - fm.stringWidth(text) / 2, y);
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private Font font(int size) {
        return new Font(resolveFont(), Font.PLAIN, size);
    }

    private Font bold(int size) {
        return new Font(resolveFont(), Font.BOLD, size);
    }

    /** 选择系统可用的中文字体，保证跨平台中文渲染 */
    private static String resolveFont() {
        String r = resolvedFont;
        if (r != null) {
            return r;
        }
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String candidate : FONT_CANDIDATES) {
            if (available.contains(candidate)) {
                resolvedFont = candidate;
                return candidate;
            }
        }
        resolvedFont = Font.SANS_SERIF;
        return resolvedFont;
    }
}
