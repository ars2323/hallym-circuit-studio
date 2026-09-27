/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.side;

import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;

import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;

/**
 * 왼쪽 칸(S-11): 위는 부품 검색과 트리, 아래는 탭(Tunnels, Minimap). 트리 아래가 크게 비어 있던 자리를 쓴다.
 * 처음 보일 때 칸 높이의 절반에서 나누고(트리의 선호 크기가 커서 기본 나눔은 아래를 1/3로 줄인다, S-11 검토),
 * 끌어 바꿀 수 있다.
 */
public final class SidePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JTabbedPane tabs = new JTabbedPane();
    private final TunnelList tunnels;
    private final Minimap minimap;
    private final JSplitPane split;

    public SidePanel(Project proj, Canvas canvas, JComponent top) {
        super(new BorderLayout());
        tunnels = new TunnelList(proj, canvas);
        minimap = new Minimap(proj, canvas);
        tabs.putClientProperty("JTabbedPane.tabType", "underlined");
        tabs.addTab(Messages.get("side.tunnels"), tunnels);
        tabs.addTab(Messages.get("side.minimap"), minimap);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, tabs);
        split.setResizeWeight(0.5);
        split.setBorder(null);
        split.setContinuousLayout(true);
        tabs.setMinimumSize(new java.awt.Dimension(0, 0));
        // Y-01: 트리가 왼쪽 칸 높이의 절반 이상을 갖는다. 아래 탭은 학생이 정한 높이(처음엔 절반)에서 줄이고, 모자라면
        // 탭 줄만 남기고 접는다. 접힌 탭을 누르면 편다. 직접 끈 높이는 창이 커지면 되돌아온다
        javax.swing.plaf.basic.BasicSplitPaneUI ui = split.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI
                ? (javax.swing.plaf.basic.BasicSplitPaneUI) split.getUI() : null;
        if (ui != null) {
            ui.getDivider().addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    dragging = true;
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    dragging = false;
                    userTabs = tabsHeight();
                    holdOpen = true;
                    autoCollapsed = false;
                }
            });
        }
        tabs.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (autoCollapsed && tabs.indexAtLocation(e.getX(), e.getY()) >= 0) {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        holdOpen = true;
                        autoCollapsed = false;
                        int h = split.getHeight();
                        split.setDividerLocation(Math.max(h / 3, h - userTabs - split.getDividerSize()));
                    });
                }
            }
        });
        split.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                holdOpen = false;
                balance();
            }
        });
        this.split = split;
        add(split, BorderLayout.CENTER);
    }

    private boolean dragging;
    private boolean holdOpen;
    private boolean autoCollapsed;
    /** 학생이 정한 아래 탭 높이. 0이면 아직 없음(처음엔 왼쪽 칸의 절반). */
    private int userTabs;

    JSplitPane split() {
        return split;
    }

    /** 아래 탭(Tunnels·Minimap) 칸의 지금 높이. */
    public int tabsHeight() {
        java.awt.Component top = split.getTopComponent();
        if (top != null && top.getParent() == split && split.getHeight() > 0 && split.isValid()) {
            return split.getHeight() - top.getHeight() - split.getDividerSize(); // 실제 자리로
        }
        return split.getHeight() - split.getDividerLocation() - split.getDividerSize();
    }

    /** 트리 칸의 지금 높이(테스트). */
    public int treeHeight() {
        java.awt.Component top = split.getTopComponent();
        return top != null && top.getParent() == split && split.isValid() ? top.getHeight() : split.getDividerLocation();
    }

    public boolean isAutoCollapsed() {
        return autoCollapsed;
    }

    private int stripHeight() {
        try {
            java.awt.Rectangle r = tabs.getBoundsAt(0);
            return r == null ? 30 : r.y + r.height + 2;
        } catch (RuntimeException e) {
            return 30;
        }
    }

    /** 왼쪽 칸 높이에 맞춰 아래 탭을 잡는다(Y-01). */
    void balance() {
        int h = split.getHeight();
        if (h <= 0 || dragging) {
            return;
        }
        if (userTabs <= 0) {
            userTabs = h / 2 - split.getDividerSize() / 2; // 처음: 절반(S-11)
        }
        int strip = stripHeight();
        int want = holdOpen ? userTabs
                : kr.ac.hallym.hcs.app.window.VerticalBalance.sideTabs(h, userTabs, split.getDividerSize(), strip);
        want = Math.min(want, Math.max(0, h - split.getDividerSize()));
        autoCollapsed = !holdOpen && kr.ac.hallym.hcs.app.window.VerticalBalance.collapsed(want, strip);
        if (Math.abs(tabsHeight() - want) > 1) {
            split.setDividerLocation(h - want - split.getDividerSize());
            split.doLayout(); // 값과 실제 자리가 어긋나지 않게(Y-01 CI)
            split.validate();
        }
    }

    /** 창 안의 왼쪽 칸(테스트). */
    public static SidePanel of(java.awt.Component root) {
        if (root instanceof SidePanel) {
            return (SidePanel) root;
        }
        if (root instanceof java.awt.Container) {
            for (java.awt.Component c : ((java.awt.Container) root).getComponents()) {
                SidePanel p = of(c);
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }

    public TunnelList tunnels() {
        return tunnels;
    }

    public Minimap minimap() {
        return minimap;
    }

    public JTabbedPane tabs() {
        return tabs;
    }
}
