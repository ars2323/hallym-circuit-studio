/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.record;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.SimulatorEvent;
import com.cburch.logisim.circuit.SimulatorListener;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.proj.Project;

/**
 * 프로젝트의 시뮬레이터에 붙어 {@link Recording}을 채운다(C-01). 원조 엔진은 고치지 않고 청취자로만 얹는다.
 * <ul>
 * <li><b>틱:</b> 원조 Simulator는 틱 하나마다 전파한 뒤 tickCompleted를 알린다. 이때 스텝을 하나 올리고 캡처한다.</li>
 * <li><b>틱 없는 전파:</b> 사용자가 입력을 바꿨다(Poke). 값이 바뀌었으면 지금 스텝을 다시 캡처하고 체크포인트를
 * 둔다.</li>
 * <li><b>지난 사이클 보기(C-03):</b> {@link #view}가 그 스텝의 회로 상태를 다시 만들어 프로젝트 상태로 바꿔 끼운다
 * (서브회로 안을 보고 있으면 같은 인스턴스 안으로). 지금 상태는 떼어 두었다가 마지막 스텝으로 돌아오면 다시 쓴다.
 * 지난 스텝에서 틱하거나 입력·회로를 바꾸면 그 뒤 기록을 버리고 거기서 다시 진행한다.</li>
 * <li><b>리셋:</b> 원조 리셋은 전파 완료만 알리므로, 리셋을 요청하는 곳이 {@link #requestReset}을 부른다. 다음 전파에서
 * 스텝 0부터 새로 기록한다.</li>
 * <li><b>회로 편집:</b> 넷 구조가 바뀌면 옛 기록과 체크포인트를 이어 쓸 수 없다. 편집 뒤 첫 전파에서 지금 스텝부터
 * 새로 기록한다(사이클 번호는 이어진다).</li>
 * </ul>
 * 최상위 회로마다 기록이 따로 있다. 시뮬레이터 스레드에서 캡처한다.
 */
public final class Recorder {
    private static final Map<Project, Recorder> ALL = new WeakHashMap<>();

    /** 약한 참조: 모든 기록기를 모은 ALL(WeakHashMap)의 값이 키인 프로젝트를 붙잡지 않게(닫은 파일의 기록이 풀린다). */
    private final java.lang.ref.WeakReference<Project> projRef;
    private final Map<Circuit, Recording> recordings = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private volatile boolean resetPending = true;
    private volatile boolean edited;
    private boolean ticked;
    private int maxSteps = Recording.DEFAULT_MAX_STEPS;

    /** 기록이 바뀌었다(캡처, 새로 시작). 시뮬레이터 스레드에서 부른다. */
    public interface Listener {
        void recordingChanged(Recording r);
    }

    // 원조 Project·Circuit·LogisimFile은 청취자를 약하게 잡으므로 필드로 붙잡아 둔다
    private final SimulatorListener simListener = new SimulatorListener() {
        public void propagationCompleted(SimulatorEvent e) {
            onPropagation();
        }

        public void tickCompleted(SimulatorEvent e) {
            onTick();
        }

        public void simulatorStateChanged(SimulatorEvent e) {
        }
    };
    private final CircuitListener circuitListener = new CircuitListener() {
        public void circuitChanged(CircuitEvent e) {
            // INVALIDATE는 시뮬레이션 중에도 오는 다시 그리기 알림이고, 이름 바꾸기는 넷을 바꾸지 않는다
            if (e.getAction() != CircuitEvent.ACTION_INVALIDATE && e.getAction() != CircuitEvent.ACTION_SET_NAME) {
                edited = true;
            }
        }
    };
    private final LibraryListener libraryListener = new LibraryListener() {
        public void libraryChanged(LibraryEvent e) {
            listenToCircuits();
            edited = true;
        }
    };

    private Recorder(Project proj) {
        this.projRef = new java.lang.ref.WeakReference<>(proj);
    }

    /** 프로젝트의 기록기(없으면 만들어 붙인다). */
    public static Recorder of(Project proj) {
        synchronized (ALL) {
            Recorder r = ALL.get(proj);
            if (r == null) {
                r = new Recorder(proj);
                ALL.put(proj, r);
                r.attach();
            }
            return r;
        }
    }

    /** 이미 붙은 기록기. 없으면 null. */
    public static Recorder peek(Project proj) {
        synchronized (ALL) {
            return ALL.get(proj);
        }
    }

    /** 원조 리셋 대신 이것을 부른다: 다음 전파에서 스텝 0부터 새로 기록한다. */
    public static void requestReset(Project proj) {
        Recorder r = peek(proj);
        if (r != null) {
            r.resetPending = true;
        }
        Simulator sim = proj.getSimulator();
        if (sim != null) {
            sim.requestReset();
        }
    }

    private void attach() {
        Project proj = projRef.get();
        proj.getSimulator().addSimulatorListener(simListener);
        proj.addLibraryListener(libraryListener);
        listenToCircuits();
    }

    private void listenToCircuits() {
        Project proj = projRef.get();
        if (proj == null || proj.getLogisimFile() == null) {
            return;
        }
        for (Circuit c : proj.getLogisimFile().getCircuits()) {
            c.removeCircuitListener(circuitListener);
            c.addCircuitListener(circuitListener);
        }
    }

    public synchronized void addListener(Listener l) {
        listeners.add(l);
    }

    public synchronized void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** 테스트·측정: 보관 상한(스텝). 새로 만드는 기록부터 쓴다. */
    public synchronized void setMaxSteps(int steps) {
        maxSteps = steps;
    }

    /** 지금 시뮬레이터가 도는 최상위 회로의 기록. 없으면 null. */
    public synchronized Recording current() {
        CircuitState root = root();
        return root == null ? null : recordings.get(root.getCircuit());
    }

    /** 최상위 회로 c의 기록. */
    public synchronized Recording of(Circuit c) {
        return recordings.get(c);
    }

    private CircuitState root() {
        Project proj = projRef.get();
        Simulator sim = proj == null ? null : proj.getSimulator();
        CircuitState s = sim == null ? null : sim.getCircuitState();
        return s;
    }

    private Recording recordingFor(CircuitState root) {
        Recording r = recordings.get(root.getCircuit());
        if (r == null) {
            r = new Recording(root.getCircuit(), maxSteps);
            recordings.put(root.getCircuit(), r);
        }
        return r;
    }

    void onTick() {
        Recording changed;
        synchronized (this) {
            CircuitState root = root();
            if (root == null) {
                return;
            }
            ticked = true;
            Recording r = recordingFor(root);
            if (r.isEmpty() || resetPending || edited) {
                // 기록 없이 틱이 왔다: 지금 상태부터 시작한다
                int step = r.isEmpty() || resetPending ? 0 : r.cursor() + 1;
                resetPending = false;
                edited = false;
                r.restart(root, step);
            } else {
                if (r.isViewingPast()) {
                    r.truncateAfter(r.cursor()); // 지난 사이클에서 다시 진행: 뒤 기록을 버린다
                }
                r.capture(root, r.cursor() + 1, false);
            }
            changed = r;
        }
        fire(changed);
    }

    void onPropagation() {
        Recording changed;
        synchronized (this) {
            if (ticked) {
                ticked = false; // 틱 뒤의 전파 완료 알림: 이미 캡처했다
                return;
            }
            CircuitState root = root();
            if (root == null) {
                return;
            }
            Recording r = recordingFor(root);
            if (r.isEmpty() || resetPending) {
                resetPending = false;
                edited = false;
                r.restart(root, 0);
            } else if (edited) {
                edited = false;
                r.restart(root, r.cursor());
            } else if (r.differs(root, r.cursor())) {
                // 입력을 바꿨다: 보던 스텝을 다시 적고 체크포인트를 둔다(지난 스텝이면 그 뒤를 버린다)
                if (r.isViewingPast()) {
                    r.truncateAfter(r.cursor());
                }
                r.capture(root, r.cursor(), true);
            } else {
                return; // 값이 그대로인 전파 알림
            }
            changed = r;
        }
        fire(changed);
    }

    /**
     * 스텝 step의 회로 상태를 보인다(GUI 스레드). 지난 스텝이면 체크포인트에서 다시 만든 상태로 바꿔 끼우고 클럭
     * 자동 진행을 멈춘다. 마지막 스텝이면 떼어 둔 지금 상태로 돌아온다. 보는 스텝을 돌려준다(기록이 없으면 -1).
     */
    public int view(int step) {
        Recording r;
        int at;
        // 커서 옮기기와 상태 바꿔 끼우기를 한 번에: 그 사이에 시뮬레이터 스레드의 전파 알림이 옛 상태를 새 커서와
        // 비교하면 "입력이 바뀌었다"로 보고 뒤 기록을 버린다(시뮬레이터는 락 없이 청취자를 부르므로 교착은 없다)
        synchronized (this) {
            Project proj = projRef.get();
            r = current();
            if (proj == null || r == null || r.isEmpty()) {
                return -1;
            }
            at = Math.max(r.first(), Math.min(r.last(), step));
            if (at == r.cursor()) {
                return at;
            }
            CircuitState target;
            if (at == r.last()) {
                target = r.live();
                r.setLive(null);
            } else {
                target = r.reconstruct(at);
                if (target == null) {
                    return r.cursor();
                }
                if (r.live() == null) {
                    r.setLive(root());
                }
                proj.getSimulator().setIsTicking(false);
            }
            r.moveCursor(at);
            if (target != null) {
                swapTo(proj, target);
            }
        }
        fire(r);
        return at;
    }

    /** 지금 보는 곳(서브회로 인스턴스 안이면 그 경로)을 유지한 채 최상위 상태를 newRoot로 바꾼다. */
    private static void swapTo(Project proj, CircuitState newRoot) {
        CircuitState cur = proj.getCircuitState();
        CircuitState target = newRoot;
        if (cur != null) {
            for (Component c : pathOf(cur)) {
                Object d = target.getData(c);
                if (!(d instanceof CircuitState)) {
                    break;
                }
                target = (CircuitState) d;
            }
        }
        proj.setCircuitState(target);
        // 바꿔 끼운 상태의 부품이 실제 시뮬레이션 쪽에 다시 등록되게(MIPS 메모리) 한 번 다시 전파한다. 값은 그대로라
        // 기록은 바뀌지 않는다(onPropagation이 걸러낸다)
        Recording.prime(newRoot);
        proj.getSimulator().requestPropagate();
        proj.repaintCanvas();
    }

    /** 최상위에서 state까지 서브회로 인스턴스 경로. */
    public static List<Component> pathOf(CircuitState state) {
        java.util.LinkedList<Component> path = new java.util.LinkedList<>();
        CircuitState s = state;
        while (s.getParentState() != null) {
            CircuitState parent = s.getParentState();
            Component holder = null;
            for (Component c : parent.getCircuit().getNonWires()) {
                if (parent.getData(c) == s) {
                    holder = c;
                    break;
                }
            }
            if (holder == null) {
                break;
            }
            path.addFirst(holder);
            s = parent;
        }
        return new ArrayList<>(path);
    }

    private void fire(Recording r) {
        List<Listener> ls;
        synchronized (this) {
            ls = new ArrayList<>(listeners);
        }
        for (Listener l : ls) {
            l.recordingChanged(r);
        }
    }
}
