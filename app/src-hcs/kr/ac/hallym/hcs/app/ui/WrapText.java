/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

import javax.swing.JComponent;

/**
 * 어절 사이에서만 줄을 바꾸는 글(Z-17). 폭은 부모가 정하거나({@link #setWrapWidth}), 정하지 않으면 지금 폭을 쓴다. 높이는
 * 그 폭에서 필요한 줄 수로 정한다(줄 간격은 글자 크기 × lineHeight, Hallym MIPS CSS line-height와 같은 뜻).
 */
public class WrapText extends JComponent {
    private String text;
    private float lineHeight;
    private int wrapWidth;

    public WrapText(String text, Font font, Color color, float lineHeight) {
        this.text = text == null ? "" : text;
        this.lineHeight = lineHeight;
        setFont(font);
        setForeground(color);
        setOpaque(false);
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
        revalidate();
        repaint();
    }

    public String getText() {
        return text;
    }

    /** 이 폭에서 줄을 나눈다(0: 지금 폭). */
    public void setWrapWidth(int w) {
        if (w != wrapWidth) {
            wrapWidth = w;
            revalidate();
            repaint();
        }
    }

    int width() {
        return wrapWidth > 0 ? wrapWidth : getWidth();
    }

    /** 지금 줄들(테스트). */
    public List<String> lines() {
        return Wrap.lines(text, getFontMetrics(getFont()), width());
    }

    int lineStep() {
        return Math.round(getFont().getSize2D() * lineHeight);
    }

    /** 폭 w에서 필요한 높이. */
    public int heightFor(int w) {
        FontMetrics fm = getFontMetrics(getFont());
        int n = Math.max(1, Wrap.lines(text, fm, w).size());
        return (n - 1) * lineStep() + fm.getHeight() + Math.max(0, lineStep() - fm.getHeight());
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        FontMetrics fm = getFontMetrics(getFont());
        int w = width();
        if (w <= 0) {
            int max = 0;
            for (String l : text.split("\n", -1)) {
                max = Math.max(max, fm.stringWidth(l));
            }
            w = max;
        }
        return new Dimension(w, heightFor(w));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(getFont());
        g.setColor(getForeground());
        FontMetrics fm = g.getFontMetrics();
        int y = fm.getAscent() + Math.max(0, lineStep() - fm.getHeight()) / 2;
        for (String l : Wrap.lines(text, fm, getWidth())) {
            g.drawString(l, 0, y);
            y += lineStep();
        }
        g.dispose();
    }
}
