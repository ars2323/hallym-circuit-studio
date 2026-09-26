/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.window;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import com.cburch.logisim.util.VerticalSplitPane;

import kr.ac.hallym.hcs.app.props.AttrDock;

/**
 * 좁은 창의 칸 비율(X-03, D-107). 캔버스는 창 폭의 절반(최소 480px) 이상을 가진다. 모자라면 왼쪽 칸과 Attributes 칸을
 * 비율로 줄이고, Attributes 칸이 최소 폭(160px) 아래로 내려가야 하면 접는다. 학생이 직접 정한 칸 폭(왼쪽 비율,
 * Attributes 폭)은 그대로 기억해 두었다가 창이 다시 넓어지면 되돌린다. 계산된 값은 설정에 저장하지 않는다.
 */
public final class PanelBalance {
    public static final int CANVAS_MIN = 480;
    public static final int LEFT_MIN = 180;
    public static final int DOCK_MIN = 160;

    private static final Map<Object, PanelBalance> ALL = new WeakHashMap<>();

    private final VerticalSplitPane split;
    private final JComponent left;
    private final AttrDock dock;
    private double userFraction;
    private boolean applying;

    private PanelBalance(VerticalSplitPane split, JComponent left, AttrDock dock, double userFraction) {
        this.split = split;
        this.left = left;
        this.dock = dock;
        this.userFraction = userFraction;
    }

    /** 창에 붙인다: 창 크기가 바뀔 때마다 균형을 다시 잡는다. 왼쪽 칸을 직접 끌면 그 비율을 기억한다. */
    public static PanelBalance install(Object owner, VerticalSplitPane split, JComponent left, AttrDock dock,
            double userFraction) {
        PanelBalance b = new PanelBalance(split, left, dock, userFraction);
        synchronized (ALL) {
            ALL.put(owner, b);
        }
        split.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                b.apply();
            }
        });
        left.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (!b.applying && split.getWidth() > 0) {
                    double f = split.getFraction();
                    if (!b.balanced && Math.abs(f - b.userFraction) > 1e-6) {
                        b.userFraction = f; // 학생이 끈 값
                    }
                }
            }
        });
        return b;
    }

    public static PanelBalance of(Object owner) {
        synchronized (ALL) {
            return ALL.get(owner);
        }
    }

    /** 학생이 정한 왼쪽 칸 비율(저장용). */
    public double userFraction() {
        return userFraction;
    }

    private boolean balanced;

    /** 지금 창 폭에서 자동으로 줄였는가(테스트). */
    public boolean isBalanced() {
        return balanced;
    }

    private String last = "";

    /** 마지막 계산(테스트·로그). */
    public String last() {
        return last;
    }

    /** 결과 계산(GUI 없이 테스트): {왼쪽 폭, Attributes 폭(0이면 접음)}. */
    public static int[] plan(int width, double userFraction, int dockWidth, boolean dockCollapsed, int stripWidth,
            int divider) {
        int canvasMin = Math.min(width, Math.max(CANVAS_MIN, width / 2));
        int leftW = (int) Math.round(width * userFraction);
        int rightW = dockCollapsed ? 0 : dockWidth + divider;
        int chrome = dockCollapsed ? stripWidth : 0;
        if (width - leftW - rightW - chrome >= canvasMin) {
            return new int[] {leftW, dockCollapsed ? 0 : dockWidth};
        }
        int need = canvasMin - (width - leftW - rightW - chrome);
        int total = leftW + rightW;
        int newLeft = total == 0 ? leftW : leftW - (int) Math.round((double) need * leftW / total);
        int newRight = total == 0 ? rightW : rightW - (int) Math.round((double) need * rightW / total);
        if (!dockCollapsed && newRight - divider < DOCK_MIN) {
            // Attributes 칸을 접고 남는 폭을 왼쪽 칸에 준다
            newLeft = Math.max(LEFT_MIN, width - canvasMin - stripWidth);
            newLeft = Math.min(newLeft, leftW);
            return new int[] {newLeft, 0};
        }
        newLeft = Math.max(Math.min(LEFT_MIN, leftW), newLeft);
        return new int[] {newLeft, dockCollapsed ? 0 : newRight - divider};
    }

    /** 지금 창 폭에 맞춰 칸을 잡는다. */
    public void apply() {
        int width = split.getWidth();
        if (width <= 0) {
            return;
        }
        int[] p = plan(width, userFraction, dock.userWidth(), dock.isUserCollapsed(), dock.stripWidth(),
                dock.dividerSize());
        boolean auto = p[0] != (int) Math.round(width * userFraction)
                || (!dock.isUserCollapsed() && (p[1] == 0 || p[1] != dock.userWidth()));
        last = "width " + width + " fraction " + userFraction + " dock " + dock.userWidth() + (dock.isUserCollapsed()
                ? " collapsed" : "") + " -> " + p[0] + "/" + p[1] + (auto ? " auto" : "");
        applying = true;
        try {
            balanced = auto;
            split.setFraction((double) p[0] / width);
            dock.balance(auto ? (p[1] == 0 ? -1 : p[1]) : 0);
        } finally {
            SwingUtilities.invokeLater(() -> applying = false);
        }
    }
}
