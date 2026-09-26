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

    // ---- 동적 진단(D-01·D-03): 기록 엔진이 적는 스텝마다 시뮬레이터 스레드에서 새 스텝만 본다 ----
    private final Object dynLock = new Object();
    /** 동적 진단과 그것을 찾은 스텝. */
    private final List<Diagnostic> dynamic = new ArrayList<>();
    private final List<Integer> dynamicAt = new ArrayList<>();
    /** 이미 말한 원인 열쇠 → 처음 말한 스텝. */
    private final java.util.Map<String, Integer> seen = new java.util.HashMap<>();
    private kr.ac.hallym.hcs.app.record.Recording dynRecording;
    private int scanned = Integer.MIN_VALUE;
    /** 원조처럼 기록기는 청취자를 강하게 잡는다: 약한 참조로 이 객체를 가리키는 청취자를 필드로 둔다. */
    private final kr.ac.hallym.hcs.app.record.Recorder.Listener recListener;

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
        java.lang.ref.WeakReference<Diagnostics> me = new java.lang.ref.WeakReference<>(this);
        recListener = r -> {
            Diagnostics d = me.get();
            if (d != null) {
                d.onRecording(r);
            }
        };
        kr.ac.hallym.hcs.app.record.Recorder.of(proj).addListener(recListener);
    }

    /** 기록이 바뀌었다(시뮬레이터 스레드): 다시 적힌 스텝부터 끝까지 동적 진단을 다시 본다. */
    void onRecording(kr.ac.hallym.hcs.app.record.Recording r) {
        boolean changed = false;
        synchronized (dynLock) {
            int dirty = r.takeDirtyFrom();
            if (r != dynRecording) {
                dynRecording = r;
                dirty = Integer.MIN_VALUE;
            }
            if (dirty == Integer.MAX_VALUE && scanned >= r.last()) {
                return;
            }
            if (dirty == Integer.MIN_VALUE) {
                changed = !dynamic.isEmpty();
                dynamic.clear();
                dynamicAt.clear();
                seen.clear();
                scanned = r.first() - 1;
            } else if (dirty <= scanned) {
                for (int i = dynamic.size() - 1; i >= 0; i--) {
                    if (dynamicAt.get(i) >= dirty) {
                        dynamic.remove(i);
                        dynamicAt.remove(i);
                        changed = true;
                    }
                }
                final int cut = dirty;
                seen.values().removeIf(v -> v >= cut);
                scanned = dirty - 1;
            }
            DynamicCheck check = new DynamicCheck(r.circuit(), r);
            for (int s = Math.max(scanned + 1, r.first()); s <= r.last(); s++) {
                for (Diagnostic d : check.step(s, seen)) {
                    dynamic.add(d);
                    dynamicAt.add(s);
                    changed = true;
                }
            }
            scanned = r.last();
        }
        if (changed) {
            javax.swing.SwingUtilities.invokeLater(this::fire);
        }
    }

    /** 동적 진단을 누르면 그 스텝을 보이는 쪽(사이클 뷰가 등록한다). */
    private static volatile java.util.function.BiConsumer<Project, Integer> stepViewer;

    public static void setStepViewer(java.util.function.BiConsumer<Project, Integer> viewer) {
        stepViewer = viewer;
    }

    /** 맨 위 상태에서 경로를 따라 내려간 인스턴스 상태. */
    private com.cburch.logisim.circuit.CircuitState instanceState(List<com.cburch.logisim.comp.Component> path) {
        com.cburch.logisim.circuit.CircuitState root = proj.getCircuitState();
        while (root != null && root.getParentState() != null) {
            root = root.getParentState();
        }
        return root == null ? null : kr.ac.hallym.hcs.app.model.InstancePaths.stateFor(root, path);
    }

    /** 동적 진단(찾은 차례). */
    public List<Diagnostic> dynamic() {
        synchronized (dynLock) {
            return new ArrayList<>(dynamic);
        }
    }

    private void fire() {
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
        for (Diagnostic x : list()) {
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

    /**
     * 정적 진단 뒤에 동적 진단. 원인이 정적 진단과 같은 곳(같은 회로의 같은 부품이나 선)인 동적 진단은 뺀다: 원인은
     * 한 곳만 말한다(PLAN.md 4.4).
     */
    public List<Diagnostic> list() {
        List<Diagnostic> dyn = dynamic();
        if (dyn.isEmpty()) {
            return list;
        }
        List<Diagnostic> ret = new ArrayList<>(list);
        for (Diagnostic d : dyn) {
            if (!coveredByStatic(d)) {
                ret.add(d);
            }
        }
        return Collections.unmodifiableList(ret);
    }

    private boolean coveredByStatic(Diagnostic d) {
        for (Diagnostic s : list) {
            if (s.circuit != d.circuit) {
                continue;
            }
            for (com.cburch.logisim.comp.Component c : d.components) {
                if (s.components.contains(c)) {
                    return true;
                }
            }
            for (com.cburch.logisim.circuit.Wire w : d.wires) {
                if (s.wires.contains(w)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 한 회로의 진단(캔버스 표시). */
    public List<Diagnostic> in(Circuit c) {
        List<Diagnostic> ret = new ArrayList<>();
        for (Diagnostic d : list()) {
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
        // 동적 진단: 사이클 뷰를 그 사이클로(D-05). 그다음 원인이 있는 서브회로 인스턴스로 들어간다
        java.util.function.BiConsumer<Project, Integer> viewer = stepViewer;
        if (d.step >= 0 && viewer != null) {
            viewer.accept(proj, d.step);
        }
        com.cburch.logisim.circuit.CircuitState inst = d.instances.isEmpty() ? null : instanceState(d.instances);
        if (inst != null) {
            if (proj.getCircuitState() != inst) {
                proj.setCircuitState(inst);
            }
        } else if (proj.getCurrentCircuit() != d.circuit) {
            proj.setCurrentCircuit(d.circuit);
        }
        // 프로그램이 고른 선택: 빠른 속성 창을 띄우지 않는다(2c 검토 반영). 사용자가 캔버스를 누르면 풀린다
        kr.ac.hallym.hcs.app.props.QuickBar.markQuiet(proj, d.components);
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
            // 이미 다 보이면 옮기지 않는다(화면 맞춤한 회로가 괜히 밀려 잘리지 않게, D-080)
            java.awt.Rectangle cause = canvas.hcsToScreen(new java.awt.Rectangle(b.getX(), b.getY(), b.getWidth(),
                    b.getHeight()));
            if (!canvas.getVisibleRect().contains(cause)) {
                canvas.scrollRectToVisible(canvas.hcsToScreen(new java.awt.Rectangle(b.getX() - 80, b.getY() - 80,
                        b.getWidth() + 160, b.getHeight() + 160)));
            }
            canvas.repaint();
        }
        for (Runnable r : listeners) {
            r.run();
        }
    }
}
