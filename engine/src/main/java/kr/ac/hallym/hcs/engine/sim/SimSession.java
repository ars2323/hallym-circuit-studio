/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.sim;

import java.awt.HeadlessException;
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
    private long ticks;
    private Pacer pacer;
    private JsonObject lastState;

    // 보고 있는 회로
    private Circuit watchRoot;
    private List<Component> watchPath = new ArrayList<>();
    private CircuitState watchState;
    private Netlist sentNetlist;
    private final Map<String, String> sent = new HashMap<>();

    // Poke 도구의 캐럿
    private Caret caret;
    private Component caretComponent;

    public SimSession(Doc doc, Server server) {
        this.doc = doc;
        this.server = server;
        this.sim = doc.project().getSimulator();
        sim.addSimulatorListener(this);
    }

    // ---- 시뮬레이터 스레드의 사건 ----

    @Override
    public void propagationCompleted(SimulatorEvent e) {
        valuesDirty = true;
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
        if (pacer != null) {
            pacer.onTick();
        }
    }

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

    /** sim.reset: 원조 Reset Simulation. 사이클 수를 0으로. */
    public void reset() {
        if (pacer != null) {
            pacer.finish(false);
        }
        sim.requestReset();
        ticks = 0;
        valuesDirty = true;
    }

    /** sim.cycles: n 사이클(틱 2n번). 곧바로 돌아오고 끝나면 sim.state를 보낸다. */
    public void cycles(int n) throws RpcError {
        if (n <= 0) {
            throw RpcError.params("n must be positive");
        }
        requireRunning();
        if (pacer == null || pacer.finished) {
            pacer = new Pacer(2L * n);
            pacer.start();
        } else {
            pacer.left += 2L * n;
        }
        pacer.pump();
    }

    /** sim.run: 틱을 켜고 끈다(원조 Ticks Enabled), hz는 원조 틱 주파수(틱/초). */
    public void run(boolean on, Double hz) throws RpcError {
        if (hz != null) {
            if (!(hz > 0) || hz > 1_000_000) {
                throw RpcError.params("hz must be between 0 and 1000000");
            }
            sim.setTickFrequency(hz);
        }
        if (on) {
            requireRunning();
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
        valuesDirty = true;
    }

    /** 모델이 바뀐 뒤: 보던 인스턴스 경로가 사라졌으면 보기를 멈춘다. */
    public void modelChanged() {
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

    /** 시뮬레이션 상태(sim.state의 params). */
    public JsonObject state() {
        JsonObject o = new JsonObject();
        o.addProperty("fileId", doc.id());
        o.addProperty("running", sim.isRunning());
        o.addProperty("ticking", sim.isTicking());
        o.addProperty("cycle", ticks / 2);
        o.addProperty("oscillating", sim.isOscillating());
        o.addProperty("hz", sim.getTickFrequency());
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
        if (nets.size() == 0) {
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
        server.notify("sim.values", o);
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
        private ScheduledFuture<?> timer;

        Pacer(long tickCount) {
            this.left = tickCount;
        }

        void start() {
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

        void finish(boolean report) {
            if (finished) {
                return;
            }
            finished = true;
            if (timer != null) {
                timer.cancel(false);
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
