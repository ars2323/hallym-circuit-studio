/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.wiring;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import com.cburch.logisim.data.Value;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 선 색 범례(E-03): 상태 표시줄의 "Wire Colors"를 누르면 색마다 뜻과 버스 모양 선택(굵은 버스, 비트 수 표시)이 뜬다.
 * 선에 마우스를 올리면 그 선 값의 뜻도 보인다(HoverInfo). 선 색은 원조 그대로다(0·1·X·E·폭 불일치·여러 비트).
 */
public final class WireLegend {
    /** 범례 한 줄: 색, 굵기, 뜻 문구 키. */
    static final Object[][] ROWS = {
        {Value.TRUE_COLOR, false, "legend.one"},
        {Value.FALSE_COLOR, false, "legend.zero"},
        {Value.UNKNOWN_COLOR, false, "legend.x"},
        {Value.ERROR_COLOR, false, "legend.e"},
        {Value.WIDTH_ERROR_COLOR, false, "legend.width"},
        {Color.BLACK, true, "legend.bus"},
    };

    private WireLegend() {
    }

    /** 색 견본(짧은 선). */
    static JComponent swatch(Color c, boolean bus) {
        JComponent s = new JComponent() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(c);
                g.setStroke(new BasicStroke(bus ? BusStyle.BUS_WIDTH : com.cburch.logisim.circuit.Wire.WIDTH,
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int y = getHeight() / 2;
                g.drawLine(4, y, getWidth() - 4, y);
                g.dispose();
            }
        };
        s.setPreferredSize(new Dimension(36, 18));
        return s;
    }

    /** 범례 판(창과 테스트). */
    static JPanel panel() {
        JPanel p = new JPanel(new GridLayout(ROWS.length, 1, 0, 2));
        p.setName("legend.panel");
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 12));
        p.setOpaque(false);
        for (Object[] r : ROWS) {
            JPanel row = new JPanel(new java.awt.BorderLayout(8, 0));
            row.setOpaque(false);
            row.add(swatch((Color) r[0], (Boolean) r[1]), java.awt.BorderLayout.WEST);
            JLabel l = new JLabel(Messages.get((String) r[2]));
            l.setForeground(Tokens.TEXT);
            row.add(l, java.awt.BorderLayout.CENTER);
            p.add(row);
        }
        return p;
    }

    /** 범례 창(누를 때마다 새로). */
    static JPopupMenu popup(Runnable changed) {
        JPopupMenu m = new JPopupMenu();
        m.add(panel());
        m.addSeparator();
        JCheckBoxMenuItem thick = new JCheckBoxMenuItem(Messages.get("legend.thick"), BusStyle.thick());
        thick.addActionListener(e -> {
            BusStyle.setThick(thick.isSelected());
            changed.run();
        });
        JCheckBoxMenuItem widths = new JCheckBoxMenuItem(Messages.get("legend.widths"), BusStyle.widths());
        widths.addActionListener(e -> {
            BusStyle.setWidths(widths.isSelected());
            changed.run();
        });
        m.add(thick);
        m.add(widths);
        return m;
    }

    /** 상태 표시줄의 "Wire Colors"(누르면 범례). changed는 캔버스를 다시 그린다. */
    public static JLabel statusLabel(Runnable changed) {
        JLabel legend = new JLabel(Messages.get("bar.legend"));
        legend.setForeground(Tokens.BLUE);
        legend.setToolTipText(Messages.get("bar.legendTip"));
        legend.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        legend.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JPopupMenu m = popup(changed);
                m.show(legend, 0, -m.getPreferredSize().height);
            }
        });
        return legend;
    }

    /** 값의 뜻 문구 키(마우스 오버). 폭 불일치는 따로. */
    public static String meaningKey(Value v, boolean widthError) {
        if (widthError) {
            return "legend.width";
        }
        if (v == null || v.getWidth() == 0) {
            return null;
        }
        if (v.getWidth() == 1) {
            if (v == Value.TRUE) {
                return "legend.one";
            }
            if (v == Value.FALSE) {
                return "legend.zero";
            }
            return v.isErrorValue() ? "legend.e" : "legend.x";
        }
        if (v.isFullyDefined()) {
            return "legend.bus";
        }
        for (Value b : v.getAll()) {
            if (b == Value.ERROR) {
                return "legend.e";
            }
        }
        return "legend.x";
    }
}
