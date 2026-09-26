/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.JComponent;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 메모리 패널(C-06, #98, PLAN.md 6.2): 보고 있는 사이클의 Data Memory·Stack 내용. Data는 낮은 주소부터 .data 라벨과
 * 함께(0인 칸이 이어지면 한 줄로 접는다), Stack은 높은 주소가 위이고 $sp가 가리키는 칸에 화살표, 머리에 지금 깊이와
 * 최고 수위. Hallym MIPS의 Data·Stack 창과 같은 모양이다.
 */
final class MemoryPanel extends JComponent implements Scrollable {
    private static final long serialVersionUID = 1L;
    static final int ROW_H = 18;

    /** 그릴 줄: 메모리 머리 또는 워드. */
    static final class Line {
        final MachineState.Memory memory;
        final MachineState.Word word;

        Line(MachineState.Memory memory, MachineState.Word word) {
            this.memory = memory;
            this.word = word;
        }
    }

    private final Supplier<List<MachineState.Memory>> source;
    private List<Line> lines = new ArrayList<>();

    MemoryPanel(Supplier<List<MachineState.Memory>> source) {
        this.source = source;
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, Tokens.FONT_SMALL));
    }

    void refresh() {
        List<Line> out = new ArrayList<>();
        List<MachineState.Memory> mems = source.get();
        if (mems != null) {
            for (MachineState.Memory m : mems) {
                out.add(new Line(m, null));
                for (MachineState.Word w : m.words) {
                    out.add(new Line(m, w));
                }
            }
        }
        lines = out;
        revalidate();
        repaint();
        // $sp가 가리키는 칸이 보이게(스택이 깊으면 목록 아래쪽에 있다)
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).word != null && lines.get(i).word.sp) {
                int row = i;
                javax.swing.SwingUtilities.invokeLater(() -> scrollRectToVisible(new Rectangle(0,
                        Math.max(0, row - 2) * ROW_H, 1, 5 * ROW_H)));
                break;
            }
        }
    }

    /** 고정 요약 줄: Stack의 깊이와 최고 수위(목록을 $sp 칸으로 스크롤해도 보인다). Stack이 없으면 빈 글. */
    String summary() {
        StringBuilder sb = new StringBuilder();
        for (Line l : lines) {
            if (l.word == null && l.memory.stack) {
                if (sb.length() > 0) {
                    sb.append("   ");
                }
                sb.append(head(l.memory));
            }
        }
        return sb.toString();
    }

    List<Line> lines() {
        return lines;
    }

    static String head(MachineState.Memory m) {
        if (!m.stack) {
            return m.name;
        }
        return m.depth >= 0 ? Messages.get("mem.stackHead", m.name, m.depth, m.peak)
                : Messages.get("mem.stackHeadNoSp", m.name, m.peak);
    }

    /** 워드 한 줄의 글: 주소, 라벨, 16진 값, 10진 값(또는 접은 구간). */
    static String text(MachineState.Word w) {
        if (w.skipped > 0) {
            return Messages.get("mem.zeros", String.format("0x%08x", w.addr),
                    String.format("0x%08x", w.addr + 4L * (w.skipped - 1)), w.skipped);
        }
        String value = w.defined ? String.format("0x%08x  %d", w.value, w.value) : "xxxxxxxx";
        String label = w.label == null ? "" : w.label + ":";
        return String.format("0x%08x  %-10s %s", w.addr, label, value);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(480, Math.max(1, lines.size()) * ROW_H + 4);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Rectangle clip = g.getClipBounds();
        g.setColor(Tokens.WHITE);
        g.fillRect(clip.x, clip.y, clip.width, clip.height);
        FontMetrics fm = g.getFontMetrics();
        Font ui = new Font(Tokens.UI_FONT, Font.PLAIN, Tokens.FONT_SMALL);
        int base = (ROW_H + fm.getAscent() - fm.getDescent()) / 2;
        if (lines.isEmpty()) {
            g.setFont(ui);
            g.setColor(Tokens.TEXT_2);
            g.drawString(Messages.get("mem.none"), 6, base);
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            int y = i * ROW_H;
            if (y > clip.y + clip.height || y + ROW_H < clip.y) {
                continue;
            }
            Line l = lines.get(i);
            if (l.word == null) {
                g.setFont(ui);
                g.setColor(Tokens.NAVY);
                // 깊이·최고 수위는 위 고정 요약 줄에 있다: 목록 머리는 이름만
                g.drawString(l.memory.name, 6, y + base);
                g.setFont(getFont());
                continue;
            }
            MachineState.Word w = l.word;
            if (w.sp) {
                g.setColor(Tokens.TEAL_TINT);
                g.fillRect(0, y, getWidth(), ROW_H);
            }
            g.setColor(w.skipped > 0 || !w.defined ? Tokens.TEXT_MUTED : Tokens.TEXT);
            String t = text(w);
            g.drawString(t, 18, y + base);
            if (w.sp) {
                g.setColor(Tokens.TEAL_TEXT);
                g.drawString("◀ $sp", 18 + fm.stringWidth(t) + 12, y + base);
            }
        }
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
        return ROW_H;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
        return o == SwingConstants.VERTICAL ? Math.max(ROW_H, r.height - ROW_H) : r.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() != null && getParent().getWidth() > getPreferredSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
