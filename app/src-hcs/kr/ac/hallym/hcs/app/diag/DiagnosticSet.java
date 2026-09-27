/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SimulatorEvent;
import com.cburch.logisim.circuit.SimulatorListener;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.record.Recorder;
import kr.ac.hallym.hcs.app.record.Recording;

/**
 * 한 프로젝트의 진단 목록에서 화면이 없는 부분(D-143: v1 {@link Diagnostics}에서 떼어 냈다). 정적 진단(편집 뒤 다시
 * 돈다), 동적 진단(기록 엔진이 적는 스텝마다 새 스텝만 본다, D-01·D-03·D-04), 진동(D-02)을 모으고, 원인이 같은 곳이면
 * 한 줄로 합친 목록을 준다(PLAN.md 4.4). Swing 앱의 {@link Diagnostics}와 v2 엔진이 같은 이것을 쓴다.
 * <p>
 * 스레드: 정적 검사는 부르는 쪽(모델을 바꾸는 스레드)에서 돈다. 동적 진단과 진동은 원조 시뮬레이터 스레드의
 * 청취자에서 본다(기록이 그 스레드에서 적히므로 읽는 동안 바뀌지 않는다). 그때 회로 모델(부품·선·넷·이름)을 읽으므로
 * 원조 회로 읽기 잠금을 쥐고 본다({@link Recorder#readModel}): 모델 스레드의 편집과 겹치지 않는다(D-143). 바뀌면
 * {@code changed}를 그 스레드에서 부른다: 받는 쪽이 자기 스레드로 넘긴다.
 */
public final class DiagnosticSet {
    private final Project proj;
    private final Runnable changed;
    /** 이 프로젝트의 기록기: 기록을 받고, 시뮬레이터 스레드에서 모델을 읽을 때 그 읽기 잠금을 쓴다. */
    private final Recorder recorder;
    private volatile List<Diagnostic> statics = Collections.emptyList();

    private final Object dynLock = new Object();
    /** 동적 진단과 그것을 찾은 스텝. */
    private final List<Diagnostic> dynamic = new ArrayList<>();
    private final List<Integer> dynamicAt = new ArrayList<>();
    /** 이미 말한 원인 열쇠 → 처음 말한 스텝. */
    private final Map<Object, Integer> seen = new HashMap<>();
    private Recording dynRecording;
    private int scanned = Integer.MIN_VALUE;
    /** 진동(D-02): 원조가 전파를 그만둔 동안의 진단 하나. 없으면 null. */
    private volatile Diagnostic oscillation;
    private final SimulatorListener simListener = new SimulatorListener() {
        @Override
        public void propagationCompleted(SimulatorEvent e) {
            checkOscillation();
        }

        @Override
        public void tickCompleted(SimulatorEvent e) {
        }

        @Override
        public void simulatorStateChanged(SimulatorEvent e) {
            checkOscillation();
        }
    };
    /** 원조처럼 기록기는 청취자를 강하게 잡는다: 약한 참조로 이 객체를 가리키는 청취자를 필드로 둔다. */
    private final Recorder.Listener recListener;
    private boolean attached;

    /**
     * 프로젝트의 기록기(없으면 만든다)와 시뮬레이터에 붙는다. changed: 동적 진단·진동이 바뀌었을 때(시뮬레이터 스레드).
     */
    public DiagnosticSet(Project proj, Runnable changed) {
        this.proj = proj;
        this.changed = changed;
        java.lang.ref.WeakReference<DiagnosticSet> me = new java.lang.ref.WeakReference<>(this);
        recListener = r -> {
            DiagnosticSet d = me.get();
            if (d != null) {
                d.onRecording(r);
            }
        };
        recorder = Recorder.of(proj);
        recorder.addListener(recListener);
        if (proj.getSimulator() != null) {
            proj.getSimulator().addSimulatorListener(simListener);
        }
        attached = true;
    }

    /** 듣기를 그만둔다(v2 엔진이 파일을 닫을 때). */
    public void detach() {
        if (!attached) {
            return;
        }
        attached = false;
        Recorder r = Recorder.peek(proj);
        if (r != null) {
            r.removeListener(recListener);
        }
        if (proj.getSimulator() != null) {
            proj.getSimulator().removeSimulatorListener(simListener);
        }
    }

    public Project project() {
        return proj;
    }

    /** 정적 검사를 지금 다시 돈다(부르는 스레드). */
    public void refreshStatic() {
        List<Diagnostic> next = proj.getLogisimFile() == null ? Collections.<Diagnostic>emptyList()
                : StaticCheck.run(proj.getLogisimFile());
        statics = Collections.unmodifiableList(new ArrayList<>(next));
    }

    /** 마지막 정적 검사의 진단. */
    public List<Diagnostic> statics() {
        return statics;
    }

    /** 맨 위 회로의 지금 상태(시뮬레이터가 돌리는 것). */
    private CircuitState liveRoot() {
        CircuitState s = proj.getSimulator() == null ? null : proj.getSimulator().getCircuitState();
        while (s != null && s.getParentState() != null) {
            s = s.getParentState();
        }
        return s;
    }

    /** 원조가 진동으로 전파를 그만뒀는지 본다(시뮬레이터 스레드). 바뀌면 알린다. */
    public void checkOscillation() {
        boolean osc = proj.getSimulator() != null && proj.getSimulator().isOscillating();
        Diagnostic before = oscillation;
        if (osc && before == null) {
            CircuitState root = liveRoot();
            Recording r = recorder.current();
            int step = r == null || r.isEmpty() ? 0 : r.last();
            // 고리를 찾으며 회로 모델을 읽는다: 원조 읽기 잠금 안에서(D-143)
            oscillation = root == null ? null
                    : recorder.readModel(() -> Oscillation.diagnose(root.getCircuit(), Oscillation.points(root), step));
        } else if (!osc && before != null) {
            oscillation = null;
        }
        if (oscillation != before) {
            changed.run();
        }
    }

    /**
     * 기록이 바뀌었다(시뮬레이터 스레드): 다시 적힌 스텝부터 끝까지 동적 진단을 다시 본다. 검사는 회로 모델을 읽으므로
     * 원조 읽기 잠금 안에서 돈다(D-143: 엔진 스레드가 편집하는 동안 부품 집합을 훑으면 ConcurrentModificationException).
     */
    public void onRecording(Recording r) {
        if (recorder.readModel(() -> scan(r))) {
            changed.run();
        }
    }

    /** 새 스텝의 동적 진단을 더한다. 목록이 바뀌었으면 true. */
    private boolean scan(Recording r) {
        boolean any = false;
        synchronized (dynLock) {
            int dirty = r.takeDirtyFrom();
            if (r != dynRecording) {
                dynRecording = r;
                dirty = Integer.MIN_VALUE;
            }
            if (dirty == Integer.MAX_VALUE && scanned >= r.last()) {
                return false;
            }
            if (dirty == Integer.MIN_VALUE) {
                any = !dynamic.isEmpty();
                dynamic.clear();
                dynamicAt.clear();
                seen.clear();
                scanned = r.first() - 1;
            } else if (dirty <= scanned) {
                for (int i = dynamic.size() - 1; i >= 0; i--) {
                    if (dynamicAt.get(i) >= dirty) {
                        dynamic.remove(i);
                        dynamicAt.remove(i);
                        any = true;
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
                    any = true;
                }
            }
            // MIPS 부품의 값 문제(D-04): 지금 상태가 마지막 스텝일 때만(지난 사이클을 보는 동안은 상태가 바뀌어 있다)
            CircuitState root = liveRoot();
            if (!r.isViewingPast() && root != null && root.getCircuit() == r.circuit() && !r.isEmpty()) {
                for (Diagnostic d : MipsCheck.check(r.circuit(), root, r, r.last(), seen)) {
                    dynamic.add(d);
                    dynamicAt.add(r.last());
                    any = true;
                }
            }
            scanned = r.last();
        }
        return any;
    }

    /** 동적 진단(찾은 차례). */
    public List<Diagnostic> dynamic() {
        synchronized (dynLock) {
            return new ArrayList<>(dynamic);
        }
    }

    /** 지금 진동 진단(없으면 null). */
    public Diagnostic oscillation() {
        return oscillation;
    }

    /**
     * 정적 진단 뒤에 동적 진단. 원인이 정적 진단과 같은 곳(같은 회로의 같은 부품이나 선)인 동적 진단은 뺀다: 원인은
     * 한 곳만 말한다(PLAN.md 4.4). 진동 중이면 같은 고리를 말하는 정적 "조합 루프"는 진동 한 줄로 바꾼다.
     */
    public List<Diagnostic> list() {
        List<Diagnostic> list = statics;
        List<Diagnostic> dyn = dynamic();
        Diagnostic osc = oscillation;
        if (dyn.isEmpty() && osc == null) {
            return list;
        }
        List<Diagnostic> ret = new ArrayList<>();
        for (Diagnostic s : list) {
            if (osc != null && s.kind == Diagnostic.Kind.COMBINATIONAL_LOOP && s.circuit == osc.circuit
                    && !Collections.disjoint(s.components, osc.components)) {
                continue;
            }
            ret.add(s);
        }
        if (osc != null) {
            ret.add(osc);
        }
        for (Diagnostic d : dyn) {
            if (!coveredByStatic(list, d)) {
                ret.add(d);
            }
        }
        return Collections.unmodifiableList(ret);
    }

    private static boolean coveredByStatic(List<Diagnostic> list, Diagnostic d) {
        for (Diagnostic s : list) {
            if (s.circuit != d.circuit) {
                continue;
            }
            for (Component c : d.components) {
                if (s.components.contains(c)) {
                    return true;
                }
            }
            for (Wire w : d.wires) {
                if (s.wires.contains(w)) {
                    return true;
                }
            }
        }
        return false;
    }
}
