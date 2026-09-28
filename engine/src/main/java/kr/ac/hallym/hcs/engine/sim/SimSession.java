/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.sim;

import java.awt.HeadlessException;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.SimulatorEvent;
import com.cburch.logisim.circuit.SimulatorListener;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.Caret;
import com.cburch.logisim.tools.Pokable;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.model.InstancePaths;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.sim.CyclePacer;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.model.ModelJson;
import kr.ac.hallym.hcs.engine.rpc.RpcError;
import kr.ac.hallym.hcs.engine.rpc.Server;

/**
 * 한 파일의 시뮬레이션(sim.*, D-134). 원조 {@link Simulator}를 그대로 쓰고 사건만 듣는다. 모든 상태는 엔진 스레드에서
 * 바꾸고, 시뮬레이터 스레드의 사건은 엔진 스레드로 넘긴다.
 * <ul>
 * <li>값 스트림: 전파가 끝나면 표시만 해 두고, 화면 프레임(16ms)마다 보고 있는 회로의 넷 값을 읽어 바뀐 넷만
 * {@code sim.values}로 보낸다. 넷 번호는 모델 알림과 같은 넷리스트에서 온다.</li>
 * <li>N 사이클: D-123의 {@link CyclePacer}와 같은 규칙. 처리 중인 틱이 {@link CyclePacer#MAX_PENDING}개 미만일
 * 때만 다음 틱을 요청하고 엔진의 틱 완료 알림으로 센다(원조의 16개 한도를 넘지 않아 틱이 빠지지 않는다).</li>
 * <li>Poke: 원조 Poke 도구처럼 부품의 {@link Pokable} 캐럿에 누름·뗌을 보낸다.</li>
 * </ul>
 */
public final class SimSession implements SimulatorListener {
    /** 값·상태를 묶어 보내는 간격(ms, 화면 프레임). */
    public static final int FRAME_MS = 16;
    /** N 사이클 틱 요청 간격(ms, CyclePacer와 같다). 틱 완료 때도 곧바로 다음 틱을 요청한다. */
    static final int PUMP_MS = 5;

    private final Doc doc;
    private final Server server;
    private final Simulator sim;
    private volatile boolean valuesDirty = true;
    // 시뮬레이터 스레드만 는다(읽기는 아무 스레드)
    private volatile long propagations;
    private long ticks;
    private Pacer pacer;
    private JsonObject lastState;

    // 보고 있는 회로
    private Circuit watchRoot;
    private List<Component> watchPath = new ArrayList<>();
    private CircuitState watchState;
    private Netlist sentNetlist;
    private final Map<String, String> sent = new HashMap<>();
    // 몸체 상태(N-05, D-137): 부품 id → 마지막으로 보낸 JSON 글자
    private final Bodies bodies = new Bodies();
    private final Map<String, String> sentBodies = new HashMap<>();

    // Poke 도구의 캐럿
    private Caret caret;
    private Component caretComponent;
    /** 모델 편집·Poke를 원조 전파와 겹치지 않게(D-143). */
    private final SimGate gate;

    public SimSession(Doc doc, Server server) {
        this.doc = doc;
        this.server = server;
        this.sim = doc.project().getSimulator();
        sim.addSimulatorListener(this);
        this.gate = new SimGate(sim);
    }

    /**
     * 엔진 스레드에서 원조 모델을 바꾸는 일(편집, Poke)을 원조 전파와 겹치지 않게 돈다({@link SimGate}, D-143): 원조
     * CircuitState의 더러운 부품·점 집합을 두 스레드가 함께 고치지 않게 한다.
     */
    public <T, X extends Exception> T quiet(SimGate.Body<T, X> body) throws X {
        return gate.hold(body);
    }

    // ---- 시뮬레이터 스레드의 사건 ----

    @Override
    public void propagationCompleted(SimulatorEvent e) {
        valuesDirty = true;
        propagations++;
        if (draining) {
            server.submit(this::onDrainStep);
        }
    }

    /** 원조 시뮬레이터가 알린 전파 완료 수(시험 자료가 전파가 끝난 자리를 기다릴 때, CanvasFixtures.settle). */
    public long propagations() {
        return propagations;
    }

    @Override
    public void tickCompleted(SimulatorEvent e) {
        server.submit(this::onTick);
    }

    @Override
    public void simulatorStateChanged(SimulatorEvent e) {
        valuesDirty = true;
        server.submit(this::onStateChanged);
    }

    // ---- 엔진 스레드 ----

    private void onTick() {
        ticks++;
        if (pacer != null && pacer.waiting) {
            drainSawTick = true; // Run이 쌓아 둔 자동 틱: 실행기의 것이 아니다(사이클에는 센다)
        } else if (pacer != null) {
            pacer.onTick();
        }
    }

    /**
     * Run을 멈춘 뒤 원조가 이미 받아 둔 자동 틱(PropagationManager, 16개까지)을 다 치르기를 기다린다: 틱 없이 끝난 전파
     * 한 바퀴가 오면(그때 원조의 틱 요청 수가 0) 실행기를 시작한다. 틱이 있던 바퀴면 전파를 다시 요청해 한 바퀴 더 본다.
     * 틱 완료 알림은 같은 바퀴의 전파 완료 알림보다 먼저 엔진 스레드에 온다(원조 run 루프의 차례, 같은 실행기).
     */
    private void onDrainStep() {
        if (!draining || pacer == null || !pacer.waiting) {
            return;
        }
        if (drainSawTick) {
            drainSawTick = false;
            sim.requestPropagate();
            return;
        }
        draining = false;
        pacer.go();
    }

    /** 자동 틱을 치르기를 기다리는 중(시뮬레이터 스레드도 읽는다). */
    private volatile boolean draining;
    /** 기다리는 동안 틱이 있었다(엔진 스레드). */
    private boolean drainSawTick;
    /**
     * 원조 틱 스레드(SimulatorTicker)는 깨어 있음을 읽은 뒤 틱을 요청하고 1~100ms 잔다. 멈춘 뒤 이만큼 지나면 그 스레드는
     * 꺼진 것을 읽었다(그 뒤로 자동 틱을 요청하지 않는다).
     */
    static final int TICKER_SETTLE_MS = 150;

    private void onStateChanged() {
        if (pacer != null) {
            pacer.pump(); // 꺼졌으면 멈춘다
        }
        sendState(false);
    }

    /** 프레임마다: 바뀐 값과 상태를 보낸다. */
    public void frame() {
        if (watchState != null && valuesDirty) {
            valuesDirty = false;
            sendValues();
        }
        sendState(false);
    }

    /**
     * sim.reset: 원조 Reset Simulation. 사이클 수를 0으로. N 사이클이 요청해 둔 틱(8개 이하)이 아직 처리 중이면 더
     * 요청하지 않고, 그 틱이 끝난 뒤에 재설정한다(원조 엔진은 재설정 뒤에도 쌓인 틱을 처리해 재설정 직후 몇 틱이 더
     * 돌기 때문이다). 곧바로 재설정했으면 true, 미뤘으면 false(그때는 재설정할 때 sim.state를 보낸다).
     */
    public boolean reset() {
        if (pacer != null && !pacer.finished && pacer.pending > 0) {
            pacer.left = 0;
            pacer.resetWhenDrained = true;
            return false;
        }
        if (pacer != null) {
            pacer.finish(false);
        }
        resetNow();
        return true;
    }

    private void resetNow() {
        // 원조 Reset과 같고, 기록 엔진에도 스텝 0부터 새로 적으라고 알린다(동적 진단이 걷힌다 D-143, record.* N-14 D-144)
        kr.ac.hallym.hcs.app.record.Recorder.requestReset(doc.project());
        ticks = 0;
        valuesDirty = true;
    }

    /**
     * record.view(N-14): 기록이 지난 사이클의 상태를 프로젝트에 바꿔 끼운 뒤(또는 지금으로 돌아온 뒤) 틱 수를 그 스텝에
     * 맞춘다. 거기서 진행하면 기록이 뒤를 버리고 그 스텝부터 이어 적으므로 sim.state의 cycle도 거기서 이어진다.
     */
    public void syncTicks(long step) {
        ticks = Math.max(0, step);
    }

    /** record.view(N-14): 프로젝트의 지금 상태(바꿔 끼운 상태)를 다시 보고 모든 넷을 다시 보낸다. */
    public void rewatch() {
        if (watchState == null) {
            return;
        }
        CircuitState now = doc.project().getCircuitState();
        if (now != null && now.getCircuit() == watchState.getCircuit()) {
            watchState = now;
        }
        sentNetlist = null;
        sent.clear();
        valuesDirty = true;
    }

    /** sim.cycles: n 사이클(틱 2n번). 곧바로 돌아오고 끝나면 sim.state를 보낸다. */
    public void cycles(int n) throws RpcError {
        if (n <= 0) {
            throw RpcError.params("n must be positive");
        }
        ticks(2L * n);
    }

    /** sim.tick: 원조 Simulate › Tick Once(틱 한 번, 반 사이클). N 사이클과 같은 실행기로 센다. */
    public void tickOnce() throws RpcError {
        ticks(1);
    }

    /**
     * 틱 count번(D-123 실행기). 도는 클럭(Run)은 먼저 멈춘다: 요청한 수만큼만 돌아야 사이클 수가 맞는다(N-07, D-145).
     * 실행기가 도는 동안 원조 틱 스레드가 틱 사이에 학생의 틱 주파수만큼 자지 않게 한다({@link #fastTicks}).
     */
    private void ticks(long count) throws RpcError {
        requireRunning();
        if (runUntilHolds()) {
            // Run Until이 한 사이클씩 요청하며 조건을 본다(D-075): 틱을 더 끼우면 조건을 넘는다
            throw RpcError.simState("busy", "Run Until is running");
        }
        boolean stopped = false;
        if (sim.isTicking()) {
            sim.setIsTicking(false);
            stopped = true;
        }
        if (pacer == null || pacer.finished) {
            pacer = new Pacer(count);
            pacer.whole = count % 2 == 0;
            if (stopped) {
                // 도는 클럭이 이미 쌓아 둔 자동 틱을 먼저 치른다: 그 틱 완료를 실행기의 것으로 세면 더 돈다(D-145)
                pacer.afterAutoTicks();
                return;
            }
            pacer.start();
        } else {
            pacer.left += count;
            pacer.whole &= count % 2 == 0;
        }
        pacer.pump();
    }

    /** 마지막 실행기: {시작할 때의 틱 수, 요청한 틱, 끝난 틱}(테스트: 요청한 만큼만 셌는지). 없으면 null. */
    public long[] lastPacer() {
        return pacer == null ? null : new long[] {pacer.startTicks, pacer.requested, pacer.completed};
    }

    /**
     * sim.step: 원조 Simulate › Step Simulation(시뮬레이션이 꺼져 있을 때만: 전파를 한 단계 진행). 켜져 있으면 오류 4
     * {@code running}(원조 메뉴 항목이 꺼져 있는 것과 같다).
     */
    public void step() throws RpcError {
        if (sim.isRunning()) {
            throw RpcError.simState("running", "Step Simulation works while the simulation is off");
        }
        sim.step();
        valuesDirty = true;
    }

    /**
     * N 사이클 동안 원조 틱 스레드(SimulatorTicker)는 틱 요청을 처리할 때마다 틱 주파수의 한 주기만큼(1 Hz면 최대
     * 100ms) 잔다. 그래서 틱 완료로 다음 틱을 요청해도 사이클마다 그만큼 기다렸다(ref-mips 1000 사이클 25초, D-145).
     * 실행기가 도는 동안만 원조 틱 주파수를 {@link #FAST_HZ}(한 주기 1ms)로 두고 sim.state에는 학생이 고른 값을 알린다.
     * 틱을 요청하는 것은 여전히 실행기뿐이다(클럭 자동 틱은 꺼져 있다). N-14 Run Until과 같은 방법(D-144)이고, 둘이
     * 겹쳐도 되게 쥔 수를 센다.
     */
    public void fastTicks(boolean on) {
        if (on) {
            if (fastHolders++ == 0) {
                heldHz = sim.getTickFrequency();
                sim.setTickFrequency(FAST_HZ);
            }
        } else if (fastHolders > 0 && --fastHolders == 0) {
            double hz = heldHz;
            heldHz = null;
            sim.setTickFrequency(hz);
        }
    }

    /** 틱 스레드가 1ms마다 깨는 가장 낮은 원조 틱 주파수(1000/1024를 반올림하면 1ms). */
    static final double FAST_HZ = 1024;
    /** {@link #fastTicks} 동안 학생이 고른 틱 주파수. */
    private Double heldHz;
    private int fastHolders;

    /** N 사이클이 돌고 있다(record.view·Run Until이 기다리게 한다). */
    public boolean busy() {
        return pacer != null && !pacer.finished;
    }

    /** 빠른 틱을 N 사이클 말고 다른 것(record.runUntil, N-14)이 쥐고 있다: Run Until이 돌고 있다. */
    private boolean runUntilHolds() {
        return fastHolders - (busy() && pacer.fast ? 1 : 0) > 0;
    }

    /**
     * sim.run: 틱을 켜고 끈다(원조 Ticks Enabled), hz는 원조 틱 주파수(틱/초). 돌고 있는 N 사이클은 멈춘다: 남은 틱을
     * 더 요청하지 않고 처리 중인 틱(8개 이하)만 끝낸다(Run·Stop이 N Cycles를 멈추는 길, D-145).
     */
    public void run(boolean on, Double hz) throws RpcError {
        if (on && runUntilHolds()) {
            // Run Until이 한 사이클씩 돌리는 중: 원조 틱을 켜면 조건을 넘어 더 돈다(D-075). 속도만 바꾸는 것은 받는다
            throw RpcError.simState("busy", "Run Until is running");
        }
        if (hz != null) {
            if (!(hz > 0) || hz > 1_000_000) {
                throw RpcError.params("hz must be between 0 and 1000000");
            }
        }
        if (on) {
            requireRunning();
        }
        if (busy() && pacer.waiting) {
            pacer.finish(true); // 아직 하나도 요청하지 않았다
        } else if (busy()) {
            // 남은 틱은 요청하지 않는다. 처리 중인 틱이 끝났을 때 사이클 가운데(클럭이 1)면 한 틱 더: 사이클을 채워 멈춘다
            pacer.left = pacer.whole && (ticks + pacer.pending) % 2 == 1 ? 1 : 0;
            pacer.releaseFast();
        }
        if (hz != null) {
            if (heldHz != null) {
                heldHz = hz; // 빠른 틱이 끝나면 이 값으로
            } else {
                sim.setTickFrequency(hz);
            }
        }
        sim.setIsTicking(on);
    }

    /** sim.enable: 원조 Simulation Enabled(발진으로 꺼진 뒤 다시 켤 때). */
    public void enable(boolean on) {
        sim.setIsRunning(on);
        if (on) {
            sim.requestPropagate();
        }
    }

    /**
     * sim.watch: root 회로에서 path(서브회로 인스턴스 id들)를 따라 내려간 회로의 값을 보낸다. Swing 앱에서 그 회로를
     * (서브회로면 그 인스턴스의 상태를) 여는 것과 같이 시뮬레이션의 지금 상태로 삼는다.
     */
    public void watch(Circuit root, List<Component> path) throws RpcError {
        Project proj = doc.project();
        if (path.isEmpty()) {
            doc.show(root);
            watchState = proj.getCircuitState();
        } else {
            CircuitState rootState = proj.getCircuitState(root);
            CircuitState s = InstancePaths.stateFor(rootState, path);
            if (s == null) {
                throw RpcError.notFound("instance path", String.join("/", ids(path)));
            }
            proj.setCircuitState(s);
            watchState = s;
        }
        watchRoot = root;
        watchPath = new ArrayList<>(path);
        sentNetlist = null;
        sent.clear();
        sentBodies.clear();
        valuesDirty = true;
    }

    /** 모델이 바뀐 뒤: 보던 인스턴스 경로가 사라졌으면 보기를 멈춘다. */
    public void modelChanged() {
        if (caretComponent != null && !doc.project().getCurrentCircuit().contains(caretComponent)
                && (watchState == null || !watchState.getCircuit().contains(caretComponent))) {
            dropCaret(); // 누르던 부품이 지워졌거나 바뀌었다
        }
        if (watchState == null) {
            return;
        }
        Circuit parent = watchRoot;
        for (Component inst : watchPath) {
            if (!parent.contains(inst)) {
                server.log("warn", "the watched instance path is gone; watching stopped", true);
                watchState = null;
                return;
            }
            parent = ((SubcircuitFactory) inst.getFactory()).getSubcircuit();
        }
        valuesDirty = true;
    }

    /** 보고 있는 회로(없으면 null). */
    public Circuit watchedCircuit() {
        return watchState == null ? null : watchState.getCircuit();
    }

    /**
     * 화면이 보고 있는 회로 상태(sim.watch)가 회로 c의 것이면 그 상태, 아니면 null(N-15, D-151: Signal Flow의 Active
     * Path Only와 활성 경로가 값을 읽기만 한다). 상태를 새로 만들지 않는다.
     */
    public CircuitState watchedState(Circuit c) {
        CircuitState s = watchState;
        return s != null && s.getCircuit() == c ? s : null;
    }

    /**
     * sim.poke: 원조 Poke 도구의 누름·뗌. action은 "click"(누르고 뗌), "press", "release". at은 회로 좌표(없으면
     * 부품 가운데). 캐럿이 없는 부품(선, 게이트 등)이면 false.
     */
    public boolean poke(Circuit c, Component comp, Location at, String action) throws RpcError {
        if (!action.equals("click") && !action.equals("press") && !action.equals("release")) {
            throw RpcError.params("action must be click, press or release");
        }
        if (comp instanceof Wire) {
            return false;
        }
        Project proj = doc.project();
        if (watchState != null && watchState.getCircuit() == c) {
            if (proj.getCircuitState() != watchState) {
                proj.setCircuitState(watchState);
            }
        } else {
            doc.show(c);
        }
        if (comp.getFactory() instanceof Pin && proj.getCircuitState().isSubstate()
                && !Boolean.TRUE.equals(comp.getAttributeSet().getValue(Pin.ATTR_TYPE))) {
            // 원조는 서브회로 안의 입력 핀을 누르면 "상태를 복제할까" 창을 연다. 엔진은 그 창의 취소처럼 한다
            throw RpcError.simState("frozenPin", "an input pin inside a subcircuit follows the parent circuit");
        }
        Canvas canvas = doc.canvas();
        if (at == null) {
            Bounds b = comp.getBounds();
            at = Location.create(b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2);
        }
        boolean poked = false;
        try {
            poked = pokeCaret(canvas, comp, at, action);
        } catch (HeadlessException e) {
            throw RpcError.simState("needsDialog", "this poke needs a dialog in the original tool");
        }
        sim.requestPropagate(); // Canvas.completeAction
        valuesDirty = true;
        return poked;
    }

    /** PokeTool.mousePressed / mouseReleased. */
    private boolean pokeCaret(Canvas canvas, Component comp, Location at, String action) {
        boolean poked = false;
        if (!action.equals("release")) {
            if (caret != null && caretComponent != comp) {
                caret.stopEditing();
                caret = null;
                caretComponent = null;
            }
            if (caret == null) {
                Pokable p = (Pokable) comp.getFeature(Pokable.class);
                Caret k = p == null ? null : p.getPokeCaret(new ComponentUserEvent(canvas, at.getX(), at.getY()));
                if (k == null) {
                    return false;
                }
                caret = k;
                caretComponent = comp;
            }
            caret.mousePressed(mouse(canvas, MouseEvent.MOUSE_PRESSED, at));
            poked = true;
        }
        if (!action.equals("press") && caret != null && caretComponent == comp) {
            caret.mouseReleased(mouse(canvas, MouseEvent.MOUSE_RELEASED, at));
            poked = true;
        }
        return poked;
    }

    private static MouseEvent mouse(Canvas canvas, int id, Location at) {
        return new MouseEvent(canvas, id, System.currentTimeMillis(), 0, at.getX(), at.getY(), 1, false);
    }

    /** 부품에 Poke 캐럿이 남아 있다(키를 받을 수 있다: 레지스터·카운터 16진 글자, RAM·ROM 값, Keyboard 글자). */
    public boolean hasCaret(Component comp) {
        return caret != null && caretComponent == comp;
    }

    /**
     * sim.pokeKey: 원조 PokeTool이 캐럿에 넘기는 키(keyPressed, 글자가 있으면 keyTyped, keyReleased). key는 화면
     * KeyboardEvent.key 글자: 한 글자, 또는 Backspace·Enter·Tab·Delete·Escape·ArrowLeft·ArrowRight·ArrowUp·
     * ArrowDown·Home·End. 캐럿이 없으면(Poke로 누른 부품이 없음) false.
     */
    public boolean pokeKey(String key) throws RpcError {
        if (key == null || key.isEmpty()) {
            throw RpcError.params("key is required");
        }
        int vk;
        char ch;
        switch (key) {
        case "Backspace": vk = KeyEvent.VK_BACK_SPACE; ch = '\b'; break;
        case "Enter": vk = KeyEvent.VK_ENTER; ch = '\n'; break;
        case "Tab": vk = KeyEvent.VK_TAB; ch = '\t'; break;
        case "Delete": vk = KeyEvent.VK_DELETE; ch = '\u007f'; break;
        case "Escape": vk = KeyEvent.VK_ESCAPE; ch = '\u001b'; break;
        case "ArrowLeft": vk = KeyEvent.VK_LEFT; ch = KeyEvent.CHAR_UNDEFINED; break;
        case "ArrowRight": vk = KeyEvent.VK_RIGHT; ch = KeyEvent.CHAR_UNDEFINED; break;
        case "ArrowUp": vk = KeyEvent.VK_UP; ch = KeyEvent.CHAR_UNDEFINED; break;
        case "ArrowDown": vk = KeyEvent.VK_DOWN; ch = KeyEvent.CHAR_UNDEFINED; break;
        case "Home": vk = KeyEvent.VK_HOME; ch = KeyEvent.CHAR_UNDEFINED; break;
        case "End": vk = KeyEvent.VK_END; ch = KeyEvent.CHAR_UNDEFINED; break;
        default:
            if (key.codePointCount(0, key.length()) != 1 || key.length() != 1) {
                throw RpcError.params("key must be one character or a named key");
            }
            ch = key.charAt(0);
            vk = KeyEvent.getExtendedKeyCodeForChar(ch);
        }
        if (caret == null) {
            return false;
        }
        Canvas canvas = doc.canvas();
        long when = System.currentTimeMillis();
        final int code = vk;
        final char typed = ch;
        // 원조 poker의 키(RegisterPoker.keyTyped → fireInvalidated 등)도 원조 CircuitState를 고친다: 전파와 겹치지 않게(D-143)
        gate.hold(() -> {
            caret.keyPressed(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, when, 0, code, typed));
            if (typed != KeyEvent.CHAR_UNDEFINED && caret != null) {
                caret.keyTyped(new KeyEvent(canvas, KeyEvent.KEY_TYPED, when, 0, KeyEvent.VK_UNDEFINED, typed));
            }
            if (caret != null) {
                caret.keyReleased(new KeyEvent(canvas, KeyEvent.KEY_RELEASED, when, 0, code, typed));
            }
            return null;
        });
        sim.requestPropagate(); // Canvas.completeAction
        valuesDirty = true;
        return true;
    }

    /** Poke 캐럿을 닫는다(원조: 다른 도구를 고르거나 다른 곳을 누름, PokeTool.removeCaret). */
    public void dropCaret() {
        if (caret != null) {
            Caret c = caret;
            gate.hold(() -> { // 원조 캐럿을 닫는 것도 상태를 고친다(D-143)
                c.stopEditing();
                return null;
            });
            caret = null;
            caretComponent = null;
            valuesDirty = true;
        }
    }

    /** 시뮬레이션 상태(sim.state의 params). */
    public JsonObject state() {
        JsonObject o = new JsonObject();
        o.addProperty("fileId", doc.id());
        o.addProperty("running", sim.isRunning());
        o.addProperty("ticking", sim.isTicking());
        o.addProperty("cycle", ticks / 2);
        o.addProperty("oscillating", sim.isOscillating());
        o.addProperty("hz", heldHz != null ? heldHz : sim.getTickFrequency());
        o.addProperty("cyclesLeft", busy() ? (pacer.left + pacer.pending + 1) / 2 : 0);
        return o;
    }

    /** 바뀌었거나 force면 sim.state를 보낸다. */
    public void sendState(boolean force) {
        JsonObject s = state();
        if (force || !s.equals(lastState)) {
            lastState = s;
            server.notify("sim.state", s);
        }
    }

    /** 끝낼 때: 사이클 실행을 멈추고 듣기를 그만둔다. */
    public void close() {
        if (pacer != null) {
            pacer.finish(false);
        }
        sim.removeSimulatorListener(this);
        gate.close();
    }

    /** 처리 중인 틱 수(테스트). */
    public int pendingTicks() {
        return pacer == null ? 0 : pacer.pending;
    }

    /** 가장 많이 쌓였던 틱 수(테스트: D-123 한도 확인). */
    public int maxPendingTicks() {
        return pacer == null ? 0 : pacer.maxPending;
    }

    private void requireRunning() throws RpcError {
        if (!sim.isRunning()) {
            if (sim.isOscillating()) {
                throw RpcError.simState("oscillating", "the simulation stopped because the circuit oscillates");
            }
            throw RpcError.simState("off", "the simulation is off");
        }
    }

    private void sendValues() {
        Circuit c = watchState.getCircuit();
        Netlist nl = doc.tracker().netlist(c);
        if (nl != sentNetlist) {
            sentNetlist = nl;
            sent.clear();
            sentBodies.clear();
        }
        JsonObject nets = new JsonObject();
        for (Netlist.Net n : nl.nets()) {
            String id = ModelJson.netId(n);
            String v = value(watchState, c, n);
            if (!v.equals(sent.get(id))) {
                sent.put(id, v);
                nets.addProperty(id, v);
            }
        }
        JsonObject changedBodies = changedBodies(c);
        if (nets.size() == 0 && changedBodies.size() == 0) {
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("fileId", doc.id());
        o.addProperty("circuitId", doc.ids().of(c));
        if (!watchPath.isEmpty()) {
            o.addProperty("root", doc.ids().of(watchRoot));
            JsonArray p = new JsonArray();
            for (String id : ids(watchPath)) {
                p.add(id);
            }
            o.add("path", p);
        }
        o.add("nets", nets);
        if (changedBodies.size() > 0) {
            o.add("bodies", changedBodies);
        }
        server.notify("sim.values", o);
    }

    /**
     * 몸체 상태가 바뀐 부품들(docs/engine-api.md sim.values.bodies): 넷 값에 없는 몸체 글(RAM 표, Console 출력,
     * MIPS 메모리 몸체 줄 등, {@link Bodies}). 보는 회로의 부품만, 앞에 보낸 것과 다를 때만.
     */
    private JsonObject changedBodies(Circuit c) {
        JsonObject out = new JsonObject();
        for (Component x : c.getNonWires()) {
            JsonObject b = bodies.of(x, c, watchState);
            if (b == null) {
                continue;
            }
            String id = doc.ids().of(x);
            String text = b.toString();
            if (!text.equals(sentBodies.get(id))) {
                sentBodies.put(id, text);
                out.add(id, b);
            }
        }
        return out;
    }

    /** 넷 값 글자(docs/engine-api.md 4절): 폭만큼, 높은 비트부터 '0' '1' 'x' 'E'. */
    static String value(CircuitState state, Circuit c, Netlist.Net n) {
        int w = ModelJson.netWidth(c, n);
        Location at = ModelJson.point(n);
        Value v = at == null ? null : state.getValue(at);
        return text(v, w);
    }

    static String text(Value v, int width) {
        StringBuilder sb = new StringBuilder(width);
        for (int i = width - 1; i >= 0; i--) {
            if (v == null || i >= v.getWidth()) {
                sb.append('x');
            } else {
                Value b = v.get(i);
                sb.append(b == Value.TRUE ? '1' : b == Value.FALSE ? '0' : b == Value.ERROR ? 'E' : 'x');
            }
        }
        return sb.toString();
    }

    private List<String> ids(List<Component> path) {
        List<String> out = new ArrayList<>();
        for (Component c : path) {
            out.add(doc.ids().of(c));
        }
        return out;
    }

    /** N 사이클 실행기(엔진 스레드). */
    private final class Pacer {
        long left;
        int pending;
        int maxPending;
        long requested;
        long completed;
        boolean finished;
        /** 처리 중인 틱이 끝나면 재설정한다(sim.reset이 미룬 것). */
        boolean resetWhenDrained;
        /** 온 사이클만 요청했다(N Cycles; Tick Once가 아니다): 멈출 때 사이클 가운데면 채운다. */
        boolean whole = true;
        /** 빠른 틱({@link #fastTicks})을 쥐고 있다. */
        private boolean fast;
        private ScheduledFuture<?> timer;
        /** 멈춘 클럭의 자동 틱을 치르기를 기다린다(아직 요청하지 않는다). */
        boolean waiting;
        /** 요청을 시작할 때의 틱 수. */
        long startTicks;

        Pacer(long tickCount) {
            this.left = tickCount;
        }

        /** 멈춘 클럭의 틱 스레드가 꺼진 것을 읽을 때까지 기다린 뒤, 쌓인 자동 틱을 치르고 시작한다. */
        void afterAutoTicks() {
            waiting = true;
            server.executor().schedule(() -> {
                if (!finished && waiting) {
                    drainSawTick = false;
                    draining = true;
                    sim.requestPropagate();
                }
            }, TICKER_SETTLE_MS, TimeUnit.MILLISECONDS);
        }

        /** 기다림이 끝났다: 요청을 시작한다. */
        void go() {
            waiting = false;
            start();
            pump();
        }

        void start() {
            startTicks = ticks;
            fast = true;
            fastTicks(true);
            timer = server.executor().scheduleAtFixedRate(() -> {
                try {
                    pump();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }, PUMP_MS, PUMP_MS, TimeUnit.MILLISECONDS);
        }

        void pump() {
            if (finished) {
                return;
            }
            if (!sim.isRunning()) {
                // 꺼졌다(발진, Simulation Enabled 끔): 원조는 쌓인 틱을 버린다
                server.log("warn", "cycles stopped: the simulation is off" + (sim.isOscillating()
                        ? " (oscillation)" : "") + " after " + completed / 2 + " cycles", true);
                finish(true);
                return;
            }
            if (waiting) {
                if (left == 0) {
                    finish(true); // 요청하기 전에 멈췄다
                }
                return;
            }
            while (left > 0 && pending < CyclePacer.MAX_PENDING) {
                left--;
                pending++;
                requested++;
                maxPending = Math.max(maxPending, pending);
                sim.tick();
            }
            if (left == 0 && pending == 0) {
                finish(true);
            }
        }

        void onTick() {
            if (finished) {
                return;
            }
            completed++;
            if (pending > 0) {
                pending--;
            }
            pump();
        }

        /** 빠른 틱을 놓는다(한 번만). 남은 틱이 처리 중이어도 원조 틱 주파수를 학생의 값으로 돌린다. */
        void releaseFast() {
            if (fast) {
                fast = false;
                fastTicks(false);
            }
        }

        void finish(boolean report) {
            if (finished) {
                return;
            }
            finished = true;
            waiting = false;
            draining = false;
            releaseFast();
            if (timer != null) {
                timer.cancel(false);
            }
            if (resetWhenDrained) {
                resetNow();
                sendState(true);
                return;
            }
            if (report) {
                if (watchState != null) {
                    valuesDirty = false;
                    sendValues();
                }
                sendState(true);
            }
        }
    }
}
