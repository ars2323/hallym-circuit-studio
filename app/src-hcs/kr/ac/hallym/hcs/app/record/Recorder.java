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
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.proj.Project;

/**
 * 프로젝트의 시뮬레이터에 붙어 {@link Recording}을 채운다(C-01). 원조 엔진은 고치지 않고 청취자로만 얹는다.
 * <ul>
 * <li><b>틱:</b> 원조 Simulator는 틱 하나마다 전파한 뒤 tickCompleted를 알린다. 이때 스텝을 하나 올리고 캡처한다.</li>
 * <li><b>틱 없는 전파:</b> 사용자가 입력을 바꿨다(Poke). 지금 스텝을 다시 캡처하고 체크포인트를 둔다. 지난 스텝을
 * 보고 있었으면 그 뒤 기록은 이미 버려졌다(C-03).</li>
 * <li><b>리셋:</b> 원조 리셋은 전파 완료만 알리므로, 리셋을 요청하는 곳이 {@link #requestReset}을 부른다. 다음 전파에서
 * 스텝 0부터 새로 기록한다.</li>
 * <li><b>회로 편집:</b> 넷 구조가 바뀌면 옛 기록과 체크포인트를 이어 쓸 수 없다. 편집 뒤 첫 전파에서 지금 스텝부터
 * 새로 기록한다(사이클 번호는 이어진다).</li>
 * </ul>
 * 최상위 회로마다 기록이 따로 있다. 시뮬레이터 스레드에서 캡처한다.
 */
public final class Recorder {
    private static final Map<Project, Recorder> ALL = new WeakHashMap<>();

    private final Project proj;
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
        this.proj = proj;
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
        proj.getSimulator().addSimulatorListener(simListener);
        proj.addLibraryListener(libraryListener);
        listenToCircuits();
    }

    private void listenToCircuits() {
        if (proj.getLogisimFile() == null) {
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
        Simulator sim = proj.getSimulator();
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
                int step = r.isEmpty() || resetPending ? 0 : r.last() + 1;
                resetPending = false;
                edited = false;
                r.restart(root, step);
            } else {
                r.capture(root, r.last() + 1, false);
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
                r.restart(root, r.last());
            } else {
                // 입력을 바꿨다: 지금 스텝을 다시 적고 체크포인트를 둔다
                r.capture(root, r.last(), true);
            }
            changed = r;
        }
        fire(changed);
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
