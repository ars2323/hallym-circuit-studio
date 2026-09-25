/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;

import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 캔버스 아래 Messages 탭(PLAN.md 6.8: 아래쪽 Console / Messages, #27). 진단 목록을 보이고, 한 줄을 누르면 그곳으로
 * 가 강조한다(4.4). 상태 표시줄의 개수를 누르면 이 탭을 편다. 3단계에서 Console 탭이 옆에 붙는다.
 */
public final class MessagesPanel {
    /** 펼쳤을 때 아래 패널 높이. */
    static final int HEIGHT = 150;

    private final Diagnostics diags;
    private final DefaultListModel<Diagnostic> model = new DefaultListModel<>();
    private final JList<Diagnostic> list = new JList<>(model);
    private final JLabel empty = new JLabel(Messages.get("messages.none"));
    private final JPanel body = new JPanel(new BorderLayout());
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
    private final JLabel status = new JLabel();
    private JSplitPane split;

    MessagesPanel(Project proj) {
        diags = Diagnostics.of(proj);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setToolTipText(Messages.get("messages.tip"));
        list.setCellRenderer((l, d, i, sel, focus) -> {
            JLabel lab = new JLabel(row(d));
            lab.setOpaque(true);
            lab.setBackground(sel ? Tokens.ERROR_TINT : Tokens.WHITE);
            lab.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            return lab;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i >= 0 && list.getCellBounds(i, i).contains(e.getPoint())) {
                    diags.go(model.get(i));
                }
            }
        });
        empty.setForeground(Tokens.TEXT_2);
        empty.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        tabs.addTab(Messages.get("messages.tab"), body);
        status.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        status.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                open();
            }
        });
        diags.addListener(this::update);
        update();
    }

    /** 목록 한 줄: 빨간 점과 문장. */
    static String row(Diagnostic d) {
        String red = String.format("%06X", Tokens.ERROR.getRGB() & 0xFFFFFF);
        return "<html><span style='color:#" + red + "'>●</span>&nbsp; " + esc(d.message()) + "</html>";
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 상태 표시줄 글자: 개수(이름, 영어). */
    static String statusText(int n) {
        return Messages.get("messages.count", n);
    }

    private void update() {
        List<Diagnostic> ds = diags.list();
        Diagnostic keep = list.getSelectedValue();
        model.clear();
        for (Diagnostic d : ds) {
            model.addElement(d);
        }
        if (keep != null) {
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i).kind == keep.kind && model.get(i).args().equals(keep.args())) {
                    list.setSelectedIndex(i);
                }
            }
        }
        body.removeAll();
        body.add(ds.isEmpty() ? empty : new JScrollPane(list), ds.isEmpty() ? BorderLayout.NORTH : BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
        status.setText(statusText(ds.size()));
        status.setForeground(ds.isEmpty() ? Tokens.TEXT_2 : Tokens.ERROR_TEXT);
    }

    /** 아래 패널을 펴고 Messages 탭을 고른다. */
    void open() {
        tabs.setSelectedIndex(0);
        if (split != null && split.getHeight() - split.getDividerLocation() < HEIGHT / 2) {
            split.setDividerLocation(Math.max(0, split.getHeight() - HEIGHT));
        }
    }

    /** 캔버스 영역 아래에 붙인다. */
    JComponent wrap(JComponent center) {
        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, center, tabs);
        split.setResizeWeight(1.0);
        split.setBorder(null);
        split.setContinuousLayout(true);
        tabs.setMinimumSize(new java.awt.Dimension(0, 0));
        tabs.setPreferredSize(new java.awt.Dimension(100, HEIGHT));
        return split;
    }

    /** 지금 목록(테스트). */
    List<Diagnostic> rows() {
        java.util.List<Diagnostic> ret = new java.util.ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            ret.add(model.get(i));
        }
        return ret;
    }

    public JLabel statusLabel() {
        return status;
    }

    /** 창을 만들 때: center(캔버스와 속성 패널) 아래에 Messages 탭을 붙인 컴포넌트를 돌려준다. */
    public static MessagesPanel install(Frame frame) {
        return new MessagesPanel(frame.getProject());
    }

    public JComponent around(JComponent center) {
        return wrap(center);
    }
}
