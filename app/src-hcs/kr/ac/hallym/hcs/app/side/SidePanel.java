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
        split.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (split.getHeight() > 0) {
                    split.removeComponentListener(this);
                    split.setDividerLocation(0.5);
                }
            }
        });
        this.split = split;
        add(split, BorderLayout.CENTER);
    }

    JSplitPane split() {
        return split;
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
