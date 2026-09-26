/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.memo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.main.Canvas;

import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 영역 메모 그리기(E-08): 부품을 그리기 전에(뒤에 깔리게) 옅은 색 상자와 색 테두리, 왼쪽 위 메모 글을 그린다. 회로
 * 좌표라 배율을 따라 커진다(교재 그림의 영역 표시처럼). 인쇄 보기·그림 내보내기에도 같은 자리에 들어간다.
 */
public final class MemoOverlay {
    static final int FILL_ALPHA = 28;
    static final float BORDER = 2f;
    static final int TEXT_SIZE = 14;
    static final int PAD = 6;

    private MemoOverlay() {
    }

    /** CanvasPainter가 부품을 그리기 전에 부른다. */
    public static void paintBehind(Canvas canvas, Graphics g0, Circuit circuit) {
        if (!(g0 instanceof Graphics2D) || canvas.getProject() == null) {
            return;
        }
        paint((Graphics2D) g0, AreaMemos.of(canvas.getProject().getLogisimFile(), circuit));
    }

    public static void paint(Graphics2D g0, java.util.List<AreaMemos.Memo> memos) {
        if (memos.isEmpty()) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font font = new Font(Tokens.UI_FONT, Font.BOLD, TEXT_SIZE);
            for (AreaMemos.Memo m : memos) {
                Bounds b = m.bounds;
                Color c = m.color();
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), FILL_ALPHA));
                g.fillRoundRect(b.getX(), b.getY(), b.getWidth(), b.getHeight(), 12, 12);
                g.setColor(c);
                g.setStroke(new BasicStroke(BORDER));
                g.drawRoundRect(b.getX(), b.getY(), b.getWidth(), b.getHeight(), 12, 12);
                if (!m.text.isEmpty()) {
                    g.setFont(font);
                    g.setColor(c.darker());
                    g.drawString(m.text, b.getX() + PAD + 2, b.getY() + PAD + g.getFontMetrics().getAscent());
                }
            }
        } finally {
            g.dispose();
        }
    }
}
