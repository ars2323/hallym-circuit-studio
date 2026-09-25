/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;

/**
 * 한 프로젝트의 진단 목록(PLAN.md 4.1: 편집을 멈추면 자동으로 돈다). 편집 동작·되돌리기·파일 바꾸기 뒤
 * {@link #DELAY_MS} 동안 조용하면 정적 검사를 다시 돌린다. 막지 않는다(4.4): 목록과 표시만 바꾼다.
 */
public final class Diagnostics {
    /** 편집이 멈췄다고 보는 시간. */
    public static final int DELAY_MS = 700;

    private static final Map<Project, Diagnostics> ALL = new WeakHashMap<>();

    private final Project proj;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final javax.swing.Timer timer;
    private List<Diagnostic> list = Collections.emptyList();
    /** 원조 Project는 리스너를 약하게 잡으므로 여기서 붙잡아 둔다(놓치면 GC 뒤 편집에 반응하지 않는다). */
    private final com.cburch.logisim.proj.ProjectListener onEdit;
    /** 마지막으로 누른 진단(캔버스에서 굵게 강조). */
    private Diagnostic focused;

    private Diagnostics(Project proj) {
        this.proj = proj;
        timer = new javax.swing.Timer(DELAY_MS, e -> refresh());
        timer.setRepeats(false);
        onEdit = e -> {
            int a = e.getAction();
            if (a == ProjectEvent.ACTION_COMPLETE || a == ProjectEvent.UNDO_COMPLETE
                    || a == ProjectEvent.ACTION_SET_FILE) {
                timer.restart();
            }
        };
        proj.addProjectListener(onEdit);
    }

    /** 프로젝트의 진단 목록(처음이면 만들고 한 번 돈다). */
    public static synchronized Diagnostics of(Project proj) {
        Diagnostics d = ALL.get(proj);
        if (d == null) {
            d = new Diagnostics(proj);
            ALL.put(proj, d);
            d.refresh();
        }
        return d;
    }

    /** 지금 다시 돈다. */
    public void refresh() {
        List<Diagnostic> next = proj.getLogisimFile() == null ? Collections.<Diagnostic>emptyList()
                : StaticCheck.run(proj.getLogisimFile());
        list = Collections.unmodifiableList(new ArrayList<>(next));
        if (focused != null && !contains(focused)) {
            focused = null;
        }
        for (Runnable r : listeners) {
            r.run();
        }
        if (proj.getFrame() != null) {
            proj.getFrame().getCanvas().repaint();
        }
    }

    private boolean contains(Diagnostic d) {
        for (Diagnostic x : list) {
            if (x.kind == d.kind && x.args().equals(d.args())) {
                return true;
            }
        }
        return false;
    }

    /** 편집 뒤 다시 돌기를 기다리는 중인가(테스트). */
    boolean pending() {
        return timer.isRunning();
    }

    public List<Diagnostic> list() {
        return list;
    }

    /** 한 회로의 진단(캔버스 표시). */
    public List<Diagnostic> in(Circuit c) {
        List<Diagnostic> ret = new ArrayList<>();
        for (Diagnostic d : list) {
            if (d.circuit == c) {
                ret.add(d);
            }
        }
        return ret;
    }

    public Diagnostic focused() {
        return focused;
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    /**
     * 진단으로 간다(4.4 클릭하면 간다): 그 회로를 열고, 원인 부품을 고르고, 보이는 곳으로 옮기고, 캔버스에서 굵게
     * 강조한다.
     */
    public void go(Diagnostic d) {
        focused = d;
        if (proj.getCurrentCircuit() != d.circuit) {
            proj.setCurrentCircuit(d.circuit);
        }
        Selection sel = proj.getSelection(); // 창이 있을 때만 있다
        if (sel != null) {
            proj.doAction(SelectionActions.dropAll(sel));
            sel.addAll(d.components);
        }
        if (proj.getFrame() != null) {
            com.cburch.logisim.gui.main.Canvas canvas = proj.getFrame().getCanvas();
            Bounds b = Bounds.EMPTY_BOUNDS;
            for (com.cburch.logisim.comp.Component c : d.components) {
                b = b.add(c.getBounds());
            }
            double z = canvas.getHcsZoom() == null ? 1.0 : canvas.getHcsZoom().zoomFactor();
            canvas.scrollRectToVisible(new java.awt.Rectangle((int) ((b.getX() - 80) * z),
                    (int) ((b.getY() - 80) * z), (int) ((b.getWidth() + 160) * z), (int) ((b.getHeight() + 160) * z)));
            canvas.repaint();
        }
        for (Runnable r : listeners) {
            r.run();
        }
    }
}
