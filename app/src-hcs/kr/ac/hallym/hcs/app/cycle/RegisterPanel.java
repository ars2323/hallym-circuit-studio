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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

import com.cburch.logisim.data.Value;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 레지스터 패널(C-05, PLAN.md 5.1). Hallym MIPS 레지스터 창과 같은 모양: 역할별 묶음, {@code $name}과 번호, 값. 값은
 * 16진·10진·2진을 한 줄에 보이고, 레지스터마다 앞에 두는 주 진법을 누를 때마다 바꾼다. 10진은 부호 있음·없음을
 * 고르고, 2진은 4비트씩 끊는다. 이번 사이클에 바뀐 값은 청록 글자와 옅은 청록 바탕이다(굵기는 쓰지 않는다).
 * 레지스터 파일을 표시하지 않았으면 모든 레지스터를 인스턴스 경로별로 나열한다.
 */
final class RegisterPanel extends JComponent implements Scrollable {
    private static final long serialVersionUID = 1L;
    static final int ROW_H = 18;

    /** 주 진법. */
    enum Radix {
        HEX, DEC, BIN
    }

    /** 그릴 줄: 묶음 머리, 안내, 레지스터. */
    static final class Line {
        final String head;
        final MachineState.Reg reg;
        final String text;

        Line(String head, MachineState.Reg reg, String text) {
            this.head = head;
            this.reg = reg;
            this.text = text;
        }
    }

    private final Supplier<MachineState> state;
    private final Map<String, Radix> radix = new HashMap<>();
    private boolean signed = true;
    private List<Line> lines = new ArrayList<>();
    private boolean listMode;

    RegisterPanel(Supplier<MachineState> state) {
        this.state = state;
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, Tokens.FONT_SMALL));
        setToolTipText(Messages.get("regs.tip"));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    menu(e);
                    return;
                }
                int i = e.getY() / ROW_H;
                if (i >= 0 && i < lines.size() && lines.get(i).reg != null) {
                    String key = lines.get(i).reg.name;
                    Radix now = radix.getOrDefault(key, Radix.HEX);
                    radix.put(key, Radix.values()[(now.ordinal() + 1) % Radix.values().length]);
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    menu(e);
                }
            }
        });
    }

    private void menu(MouseEvent e) {
        JPopupMenu m = new JPopupMenu();
        JCheckBoxMenuItem s = new JCheckBoxMenuItem(Messages.get("regs.signed"), signed);
        s.addActionListener(a -> {
            signed = s.isSelected();
            repaint();
        });
        m.add(s);
        m.show(this, e.getX(), e.getY());
    }

    /** 주 진법(테스트). */
    Radix radixOf(String name) {
        return radix.getOrDefault(name, Radix.HEX);
    }

    void setSigned(boolean v) {
        signed = v;
    }

    /** 다시 모은다(사이클이 바뀌거나 기록이 바뀔 때). */
    void refresh() {
        lines = build();
        revalidate();
        repaint();
    }

    List<Line> lines() {
        return lines;
    }

    List<Line> build() {
        List<Line> out = new ArrayList<>();
        MachineState ms = state.get();
        if (ms == null || ms.model().isEmpty()) {
            return out;
        }
        int c = ms.model().cursorCycle();
        Value sp = ms.sp(c);
        long depth = MachineState.depth(sp);
        if (sp != null && sp.isFullyDefined()) {
            out.add(new Line(null, null, depth >= 0
                    ? Messages.get("regs.spDepth", hex(sp), depth) : Messages.get("regs.sp", hex(sp))));
        }
        List<MachineState.Reg> regs = ms.registers(c);
        listMode = !ms.hasRegisterFile();
        if (!listMode) {
            out.add(new Line(Messages.get("regs.group.special"), null, null));
            Value pc = ms.model().pc(c);
            Value pcBefore = c > ms.model().firstCycle() ? ms.model().pc(c - 1) : null;
            out.add(new Line(null, new MachineState.Reg(-1, "PC", pc, pc != null && pcBefore != null
                    && !pc.equals(pcBefore)), null));
            for (String[] g : MachineState.GROUPS) {
                out.add(new Line(Messages.get("regs.group." + g[0].replace(' ', '_')), null, null));
                for (int i = 1; i < g.length; i++) {
                    out.add(new Line(null, regs.get(Integer.parseInt(g[i])), null));
                }
            }
        } else {
            out.add(new Line(null, null, Messages.get("regs.notMarked")));
            for (MachineState.Reg r : regs) {
                out.add(new Line(null, r, null));
            }
        }
        return out;
    }

    static String hex(Value v) {
        if (v == null) {
            return "";
        }
        if (!v.isFullyDefined()) {
            return v.toHexString();
        }
        int digits = Math.max(1, (v.getWidth() + 3) / 4);
        return String.format("0x%0" + digits + "x", unsigned(v));
    }

    static long unsigned(Value v) {
        return v.toIntValue() & (v.getWidth() >= 32 ? 0xffffffffL : (1L << v.getWidth()) - 1);
    }

    static String dec(Value v, boolean signed) {
        if (v == null) {
            return "";
        }
        if (!v.isFullyDefined()) {
            return v.isErrorValue() ? "E" : "X";
        }
        if (signed && v.getWidth() < 32) {
            long u = unsigned(v);
            long half = 1L << (v.getWidth() - 1);
            return Long.toString(u >= half ? u - 2 * half : u);
        }
        return signed ? Integer.toString(v.toIntValue()) : Long.toString(unsigned(v));
    }

    /** 2진: 4비트씩 끊는다(높은 비트부터). 정해지지 않은 비트는 x, 오류 비트는 E. */
    static String bin(Value v) {
        if (v == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int b = v.getWidth() - 1; b >= 0; b--) {
            Value bit = v.get(b);
            sb.append(bit == Value.TRUE ? '1' : bit == Value.FALSE ? '0' : bit.isErrorValue() ? 'E' : 'x');
            if (b > 0 && b % 4 == 0) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /** 한 줄 값: 주 진법이 앞, 나머지 둘이 뒤. */
    String[] valueTexts(MachineState.Reg r) {
        if (r.value == null) {
            return new String[] {"—", ""};
        }
        String h = hex(r.value);
        String d = dec(r.value, signed);
        String b = bin(r.value);
        switch (radixOf(r.name)) {
        case DEC:
            return new String[] {d, h + "  " + b};
        case BIN:
            return new String[] {b, h + "  " + d};
        default:
            return new String[] {h, d + "  " + b};
        }
    }

    /** 나열 모드의 이름 칸: 가장 긴 이름에 맞춘다(한 목록 안에서는 같은 폭). */
    private int listNameWidth(FontMetrics fm) {
        int w = 60;
        for (Line l : lines) {
            if (l.reg != null) {
                w = Math.max(w, fm.stringWidth(l.reg.name) + 8);
            }
        }
        return Math.min(w, 260);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(560, Math.max(1, lines.size()) * ROW_H + 4);
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
        for (int i = 0; i < lines.size(); i++) {
            int y = i * ROW_H;
            if (y > clip.y + clip.height || y + ROW_H < clip.y) {
                continue;
            }
            Line l = lines.get(i);
            if (l.head != null) {
                g.setFont(ui);
                g.setColor(Tokens.NAVY);
                g.drawString(l.head, 6, y + base);
                g.setFont(getFont());
                continue;
            }
            if (l.reg == null) {
                g.setFont(ui);
                g.setColor(Tokens.TEXT_2);
                g.drawString(l.text, 6, y + base);
                g.setFont(getFont());
                continue;
            }
            MachineState.Reg r = l.reg;
            if (r.changed) {
                g.setColor(Tokens.TEAL_TINT);
                g.fillRect(0, y, getWidth(), ROW_H);
            }
            // 표시한 레지스터 파일은 $name 칸이 좁고, 모두 나열할 때는 경로가 든 이름 칸이 넓다(한 목록 안에서는 같은 폭)
            int nameW = listMode ? listNameWidth(fm) : 60;
            int x = 18;
            g.setColor(r.changed ? Tokens.TEAL_TEXT : Tokens.TEXT);
            g.drawString(CycleView.fit(fm, r.name, nameW), x, y + base);
            x += nameW + 4;
            if (r.number >= 0 && r.name.startsWith("$")) {
                g.setColor(Tokens.TEXT_2);
                g.drawString("R" + r.number, x, y + base);
            }
            x += 40;
            String[] v = valueTexts(r);
            g.setColor(r.changed ? Tokens.TEAL_TEXT : Tokens.TEXT);
            g.drawString(v[0], x, y + base);
            x += Math.max(96, fm.stringWidth(v[0]) + 14);
            g.setColor(Tokens.TEXT_MUTED);
            g.drawString(v[1], x, y + base);
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
