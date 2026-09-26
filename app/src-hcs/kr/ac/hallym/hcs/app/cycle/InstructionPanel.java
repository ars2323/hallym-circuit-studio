/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.JComponent;

import com.cburch.logisim.data.Value;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * Instruction 탭(C-07): 보고 있는 사이클의 명령어를 Hallym MIPS Instruction Inspector처럼 펼친다. 디스어셈블과 형식,
 * 워드와 주소, 필드별 비트 범위·비트·이름·값을 필드 색으로. 레지스터 필드(rs, rt, rd)는 레지스터 이름도 보인다. 값은
 * 워드를 풀어 보일 뿐 판단하지 않는다.
 */
final class InstructionPanel extends JComponent {
    private static final long serialVersionUID = 1L;
    static final int LINE = 20;

    /** 한 필드의 칸. */
    static final class Cell {
        final String name;
        final String range;
        final String bits;
        final String value;
        final Color color;

        Cell(String name, String range, String bits, String value, Color color) {
            this.name = name;
            this.range = range;
            this.bits = bits;
            this.value = value;
            this.color = color;
        }
    }

    private final Supplier<CycleModel> model;

    InstructionPanel(Supplier<CycleModel> model) {
        this.model = model;
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, Tokens.FONT_UI));
    }

    /** 보고 있는 사이클의 명령어 워드. 정해지지 않았으면 null. */
    Integer word() {
        CycleModel m = model.get();
        if (m == null || m.isEmpty()) {
            return null;
        }
        Value v = m.instruction(m.cursorCycle());
        return v == null || !v.isFullyDefined() ? null : v.toIntValue();
    }

    static String formatName(int word) {
        switch (MipsText.format(word)) {
        case R:
            return "R-type";
        case J:
            return "J-type";
        default:
            return "I-type";
        }
    }

    /** 필드 칸들(높은 비트부터). */
    static List<Cell> cells(int word) {
        List<Cell> out = new ArrayList<>();
        for (MipsText.Field f : MipsText.fields(word)) {
            int v = f.of(word);
            StringBuilder bits = new StringBuilder();
            for (int b = f.hi; b >= f.lo; b--) {
                bits.append((word >>> b) & 1);
            }
            String value;
            if (f.name.equals("rs") || f.name.equals("rt") || f.name.equals("rd")) {
                value = v + " " + MipsText.REG[v];
            } else if (f.name.equals("imm")) {
                value = (short) v + " (0x" + Integer.toHexString(v) + ")";
            } else if (f.name.equals("addr")) {
                value = "0x" + Integer.toHexString(v);
            } else {
                value = Integer.toString(v);
            }
            String range = f.hi == f.lo ? Integer.toString(f.hi) : f.hi + "–" + f.lo;
            out.add(new Cell(f.name, range, bits.toString(), value, FieldPaths.color(f.name)));
        }
        return out;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(520, LINE * 7 + 8);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Tokens.WHITE);
        g.fillRect(0, 0, getWidth(), getHeight());
        Font ui = new Font(Tokens.UI_FONT, Font.PLAIN, Tokens.FONT_SMALL);
        CycleModel m = model.get();
        Integer word = word();
        FontMetrics fm = g.getFontMetrics();
        int base = fm.getAscent() + 4;
        if (m == null || word == null) {
            g.setFont(ui);
            g.setColor(Tokens.TEXT_2);
            g.drawString(Messages.get("inspect.none"), 8, base);
            return;
        }
        int c = m.cursorCycle();
        int x = 8;
        g.setColor(Tokens.TEXT);
        String text = m.instructionText(c);
        g.drawString(text, x, base);
        g.setColor(Tokens.TEXT_2);
        g.drawString(formatName(word), x + Math.max(fm.stringWidth(text) + 24, 300), base);
        g.drawString(String.format("0x%08x", word) + "  at " + m.pcText(c), x, base + LINE);
        // 필드: 범위, 비트, 이름, 값을 칸마다(칸 폭은 가장 긴 글에 맞춘다)
        List<Cell> cells = cells(word);
        int col = x;
        for (Cell cell : cells) {
            int w = Math.max(Math.max(fm.stringWidth(cell.bits), fm.stringWidth(cell.value)),
                    Math.max(fm.stringWidth(cell.range), fm.stringWidth(cell.name))) + 12;
            g.setColor(Tokens.TEXT_MUTED);
            g.drawString(cell.range, col, base + 2 * LINE + 6);
            g.setColor(cell.color);
            g.drawString(cell.bits, col, base + 3 * LINE + 6);
            g.fillRect(col, base + 3 * LINE + 10, fm.stringWidth(cell.bits), 3);
            g.drawString(cell.name, col, base + 4 * LINE + 8);
            g.setColor(Tokens.TEXT);
            g.drawString(cell.value, col, base + 5 * LINE + 8);
            col += w;
        }
    }
}
