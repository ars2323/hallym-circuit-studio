/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.record;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.proj.Action;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.cycle.CycleModel;
import kr.ac.hallym.hcs.app.cycle.RegisterFile;
import kr.ac.hallym.hcs.app.cycle.RunUntil;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.record.Recorder;
import kr.ac.hallym.hcs.app.record.Recording;
import kr.ac.hallym.hcs.app.sim.PcMark;
import kr.ac.hallym.hcs.app.sim.StatusModel;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.mips.MemoryTable;
import kr.ac.hallym.hcs.engine.mips.MipsParts;
import kr.ac.hallym.hcs.engine.model.ModelJson;
import kr.ac.hallym.hcs.engine.rpc.RpcError;
import kr.ac.hallym.hcs.engine.rpc.Server;
import kr.ac.hallym.hcs.engine.sim.SimSession;
import kr.ac.hallym.hcs.mips.disasm.Disassembler;

/**
 * 한 파일의 기록(record.*, N-14, D-144). v1의 GUI 없는 기록 엔진을 그대로 쓴다: {@link Recorder}가 원조 시뮬레이터에
 * 청취자로 붙어 틱마다 모든 넷의 바뀐 값을 {@link Recording}에 적고(C-01, D-073), 지난 사이클은 체크포인트에서 다시
 * 만든 상태로 바꿔 끼운다(C-03, D-074). 사이클 표·PC·명령어는 {@link CycleModel}, 레지스터 파일 찾기와 대응은
 * {@link RegisterFile}(Mark as Register File, Register Mapping), PC 판별은 {@link StatusModel}·{@link PcMark}(Mark
 * as PC), Run Until 조건은 {@link RunUntil}, 메모리 표는 {@link MemoryTable}(D-140)이다. 명령어 글과 필드는 lib-mips
 * 디스어셈블러(D-127)와 {@link InstructionFields}에서 온다. 값은 보이기만 하고 판단하지 않는다(규칙 2.6), 학생
 * 레지스터에 쓰지 않는다.
 *
 * <p>모든 메서드는 엔진 스레드에서 돈다. 기록기의 청취자(시뮬레이터 스레드)는 Run Until이 도는 동안만 엔진 스레드로
 * 일을 넘긴다. 화면에 알리는 {@code record.state}는 화면 프레임(16ms)마다 바뀐 것만 보낸다.
 */
public final class RecordSession {
    /** 사이클 표를 따로 묻지 않았을 때 보내는 열 수(마지막 열까지). */
    static final int DEFAULT_COLUMNS = 60;
    /** 한 번에 보내는 열의 한계. */
    static final int MAX_COLUMNS = 400;

    /** Hallym MIPS 레지스터 창의 묶음(이름·순서·든 레지스터가 그 창과 같다). */
    static final String[] GROUP_TITLES = {"Special", "Constant", "Return values", "Arguments", "Temporaries",
        "Saved", "Pointers", "Return address", "Reserved"};
    static final int[][] GROUP_REGS = {{}, {0}, {2, 3}, {4, 5, 6, 7}, {8, 9, 10, 11, 12, 13, 14, 15, 24, 25},
        {16, 17, 18, 19, 20, 21, 22, 23}, {28, 29, 30}, {31}, {1, 26, 27}};
    /** 번호가 없는(또는 번호가 겹친) 레지스터의 묶음. */
    static final String OTHER = "Other registers";

    /** 줄 하나(사용자가 더한 줄, 또는 메시지를 눌러 고정한 임시 줄). */
    static final class Row {
        final String id;
        final CycleModel.Signal signal;
        final boolean temp;

        Row(String id, CycleModel.Signal signal, boolean temp) {
            this.id = id;
            this.signal = signal;
            this.temp = temp;
        }
    }

    private final Doc doc;
    private final Server server;
    private final Recorder recorder;
    private SimSession sim;
    private final List<Row> rows = new ArrayList<>();
    private final List<Row> pinned = new ArrayList<>();
    private Recording pinnedRecording;
    private int pinnedGeneration;
    private int pinnedCycle = -1;
    private int nextRow = 1;
    private Until until;
    private int untilGeneration;
    private volatile boolean untilActive;
    private JsonObject lastState;
    private ProgramSymbols symbols;
    // PC 레지스터 찾기(넷리스트를 새로 만든다)는 모델·표시가 바뀔 때만 다시 한다
    private Object[] pcKey;
    private Component pcRegister;
    // 기록기는 청취자를 강하게 잡는다: 파일을 닫으면 뗀다
    private final Recorder.Listener listener;

    /**
     * 기록기를 붙인다. 시뮬레이션 세션(SimSession)보다 먼저 붙여야 틱 완료 알림을 먼저 받아, 세션이 사이클 수를 세거나
     * N Cycles를 끝낼 때 그 틱이 이미 기록돼 있다.
     */
    RecordSession(Doc doc, Server server) {
        this.doc = doc;
        this.server = server;
        this.recorder = Recorder.of(doc.project());
        this.listener = r -> {
            if (untilActive) {
                server.submit(() -> onRecorded(r));
            }
        };
        recorder.addListener(listener);
    }

    /**
     * 파일을 연 요청 안에서, 다른 청취자(진단, D-143)가 모두 붙은 뒤: 연 회로의 첫 전파가 붙기 전에 끝났을 수 있으니
     * 한 번 더 전파를 요청하고 스텝 0이 적힐 때까지 기다린다. 그 뒤에 오는 첫 틱은 스텝 1이 된다.
     */
    void ready() {
        // 스텝 0을 모든 청취자가 붙은 지금 다시 적는다: 첫 전파가 진단이 붙기 전에 끝났으면 진단은 스텝 0을 못 봤다
        recorder.restartAtNextPropagation();
        doc.project().getSimulator().requestPropagate();
        settle();
    }

    /** 새로 적기를 기다리는 한도(ms). 원조 전파 스레드 한 번이면 된다. */
    static final long SETTLE_MS = 5000;

    /**
     * 기록기가 새로 적기(붙인 뒤 첫 전파, Reset의 재설정 전파)를 마칠 때까지 기다린다(엔진 스레드, 한도 {@link
     * #SETTLE_MS}). 원조 시뮬레이터는 그 전파와 먼저 온 틱을 한 번에 처리할 수 있고, 그러면 틱 뒤 상태가 스텝 0으로
     * 적힌다(파일을 열자마자 1 Cycle). Reset 바로 뒤의 Run Until은 새 기록을 기준으로 삼아야 한다. 전파 스레드는 엔진
     * 스레드를 기다리지 않으므로 막히지 않는다.
     */
    private void settle() {
        long end = System.nanoTime() + SETTLE_MS * 1_000_000L;
        while (recorder.resetPending() && System.nanoTime() < end) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    void bind(SimSession s) {
        this.sim = s;
    }

    void close() {
        recorder.removeListener(listener);
        boolean held = until != null; // 빠른 틱은 쥔 수를 센다(N-07, D-145): 쥔 것만 놓는다
        until = null;
        untilActive = false;
        if (sim != null && held) {
            sim.fastTicks(false);
        }
    }

    /** 지금 시뮬레이터가 도는 최상위 회로의 기록(테스트). */
    public Recording recording() {
        return recorder.current();
    }

    // ---- 모델 ----

    private CycleModel model(Recording r) {
        Circuit root = r.circuit();
        return new CycleModel(root, r, CycleModel.findCpu(root), null);
    }

    private ProgramSymbols symbols(Circuit root) {
        symbols = ProgramSymbols.of(doc.loader().getMainFile(), root, symbols);
        return symbols;
    }

    /** 레지스터·카운터 가운데 PC(D-103, X-04): Mark as PC → 라벨 PC → Instruction Memory Addr을 내는 레지스터. */
    private Component pcRegister(Circuit root) {
        Object[] key = {root, doc.tracker().netlist(root), PcMark.marked(doc.file(), root)};
        if (pcKey == null || pcKey[0] != key[0] || pcKey[1] != key[1] || pcKey[2] != key[2]) {
            pcKey = key;
            pcRegister = StatusModel.pcRegister(doc.file(), root);
        }
        return pcRegister;
    }

    // ---- record.state ----

    /** record.state: 기록 범위, 보고 있는 사이클, PC, Run Until. */
    public JsonObject state() {
        JsonObject o = new JsonObject();
        o.addProperty("fileId", doc.id());
        Recording r = recorder.current();
        boolean empty = r == null || r.isEmpty();
        o.addProperty("empty", empty);
        if (empty) {
            o.addProperty("first", 0);
            o.addProperty("last", 0);
            o.addProperty("cycle", 0);
            o.addProperty("past", false);
            o.addProperty("generation", r == null ? 0 : r.generation());
            o.add("pc", JsonNull.INSTANCE);
            o.addProperty("cpu", r != null && CycleModel.findCpu(r.circuit()) != null);
        } else {
            CycleModel m = model(r);
            int cycle = m.cursorCycle();
            o.addProperty("first", m.firstCycle());
            o.addProperty("last", m.lastCycle());
            o.addProperty("cycle", cycle);
            o.addProperty("past", r.isViewingPast());
            o.addProperty("generation", r.generation());
            o.addProperty("cpu", m.cpu() != null);
            String pc = pcText(r, m, cycle);
            if (pc == null) {
                o.add("pc", JsonNull.INSTANCE);
            } else {
                o.addProperty("pc", pc);
            }
        }
        o.addProperty("rows", rows.size());
        o.addProperty("pinned", pinned.size());
        if (until == null) {
            o.add("runUntil", JsonNull.INSTANCE);
        } else {
            JsonObject u = new JsonObject();
            u.addProperty("kind", until.kind);
            if (until.value != null) {
                u.addProperty("value", until.value);
            }
            u.addProperty("from", until.startCycle);
            o.add("runUntil", u);
        }
        return o;
    }

    /** 상태 표시줄의 PC(D-103): 최상위의 PC 부품 첫 포트 값, 없으면 Instruction Memory의 Addr. 정해지지 않았으면 null. */
    private String pcText(Recording r, CycleModel m, int cycle) {
        Circuit root = r.circuit();
        Component pc = StatusModel.pcComponent(doc.file(), root);
        if (pc != null && !pc.getEnds().isEmpty()) {
            return ValueText.hex(r.value(new ArrayList<Component>(), pc.getEnd(0).getLocation(),
                    CycleModel.stepOf(cycle)));
        }
        return ValueText.hex(m.pc(cycle));
    }

    /** 프레임마다(엔진 스레드): 멈춘 시뮬레이션, 임시 줄의 수명, 바뀐 상태. */
    void frame() {
        if (until != null && !doc.project().getSimulator().isRunning()) {
            finish("off");
        }
        checkPinned();
        JsonObject s = state();
        if (!s.equals(lastState)) {
            lastState = s;
            server.notify("record.state", s);
        }
    }

    // ---- 사이클 표 ----

    /** record.table: from~to 열(없으면 마지막 {@link #DEFAULT_COLUMNS}열). */
    public JsonObject table(Integer fromParam, Integer toParam) {
        JsonObject o = new JsonObject();
        o.addProperty("fileId", doc.id());
        Recording r = recorder.current();
        JsonArray columns = new JsonArray();
        JsonArray out = new JsonArray();
        o.add("columns", columns);
        o.add("rows", out);
        if (r == null || r.isEmpty()) {
            o.addProperty("empty", true);
            for (Row row : allRows()) {
                out.add(rowHead(row));
            }
            return o;
        }
        CycleModel m = model(r);
        int first = m.firstCycle();
        int last = m.lastCycle();
        int to = toParam == null ? last : Math.max(first, Math.min(last, toParam));
        int from = fromParam == null ? Math.max(first, to - DEFAULT_COLUMNS + 1) : Math.max(first, Math.min(to,
                fromParam));
        if (to - from + 1 > MAX_COLUMNS) {
            from = to - MAX_COLUMNS + 1;
        }
        o.addProperty("empty", false);
        o.addProperty("first", first);
        o.addProperty("last", last);
        o.addProperty("cycle", m.cursorCycle());
        o.addProperty("from", from);
        o.addProperty("to", to);
        o.addProperty("cpu", m.cpu() != null);
        o.addProperty("pinnedCycle", pinned.isEmpty() ? -1 : pinnedCycle);
        java.util.Map<Integer, String> names = symbols(r.circuit()).byAddress;
        for (int c = from; c <= to; c++) {
            JsonObject col = new JsonObject();
            col.addProperty("cycle", c);
            Value pc = m.pc(c);
            Value in = m.instruction(c);
            String pcHex = ValueText.hex(pc);
            String word = ValueText.hex(in);
            put(col, "pc", pcHex);
            put(col, "word", word);
            col.addProperty("text", pcHex != null && word != null
                    ? Disassembler.text(in.toIntValue(), pc.toIntValue(), names) : "");
            columns.add(col);
        }
        for (Row row : allRows()) {
            JsonObject j = rowHead(row);
            JsonArray values = new JsonArray();
            JsonArray halves = new JsonArray();
            int w = row.signal.width;
            for (int c = from; c <= to; c++) {
                addText(values, m.value(row.signal, CycleModel.stepOf(c)), w);
                if (w == 1) {
                    addText(halves, m.value(row.signal, m.halfSteps(c)[0]), w);
                }
            }
            j.add("values", values);
            if (w == 1) {
                j.add("halves", halves);
            }
            out.add(j);
        }
        return o;
    }

    private static void put(JsonObject o, String k, String v) {
        if (v == null) {
            o.add(k, JsonNull.INSTANCE);
        } else {
            o.addProperty(k, v);
        }
    }

    private static void addText(JsonArray a, Value v, int w) {
        String t = ValueText.of(v, w);
        if (t == null) {
            a.add(JsonNull.INSTANCE);
        } else {
            a.add(t);
        }
    }

    private List<Row> allRows() {
        List<Row> all = new ArrayList<>(pinned);
        all.addAll(rows);
        return all;
    }

    private JsonObject rowHead(Row row) {
        JsonObject j = new JsonObject();
        j.addProperty("id", row.id);
        j.addProperty("name", row.signal.name);
        j.addProperty("width", row.signal.width);
        j.addProperty("bits", row.signal.bits);
        j.addProperty("temp", row.temp);
        return j;
    }

    // ---- 줄 ----

    /** record.addRow: 인스턴스 경로 안의 선·자리·넷을 줄로(Add to Cycle View). 이미 있으면 그 줄. */
    public JsonObject addRow(Circuit circuit, List<Component> path, Location at, Wire wire, String netId)
            throws RpcError {
        CycleModel.Signal s = signal(circuit, path, at, wire, netId);
        JsonObject o = new JsonObject();
        for (Row row : rows) {
            if (row.signal.equals(s)) {
                o.addProperty("id", row.id);
                o.addProperty("added", false);
                return o;
            }
        }
        Row row = new Row("r" + nextRow++, s, false);
        rows.add(row);
        o.addProperty("id", row.id);
        o.addProperty("added", true);
        o.addProperty("name", s.name);
        o.addProperty("width", s.width);
        return o;
    }

    private CycleModel.Signal signal(Circuit circuit, List<Component> path, Location at, Wire wire, String netId)
            throws RpcError {
        Circuit root = root(circuit, path);
        CycleModel.Signal s = null;
        if (wire != null) {
            if (!circuit.contains(wire)) {
                throw RpcError.notFound("wire", doc.ids().peek(wire));
            }
            s = CycleModel.signalFor(root, path, circuit, wire);
        } else if (netId != null) {
            for (Netlist.Net n : doc.tracker().netlist(circuit).nets()) {
                if (ModelJson.netId(n).equals(netId) && ModelJson.point(n) != null) {
                    s = CycleModel.signalFor(root, path, circuit, ModelJson.point(n));
                }
            }
            if (s == null) {
                throw RpcError.notFound("net", netId);
            }
        } else if (at != null) {
            s = CycleModel.signalFor(root, path, circuit, at);
            if (s == null) {
                throw RpcError.notFound("net", "(" + at.getX() + "," + at.getY() + ")");
            }
        } else {
            throw RpcError.params("one of wireId, netId, at is required");
        }
        return s;
    }

    /** 경로가 시작하는 최상위 회로: 기록하는 회로(경로가 있으면 반드시), 없으면 circuit 자신. */
    private Circuit root(Circuit circuit, List<Component> path) throws RpcError {
        Recording r = recorder.current();
        if (path.isEmpty()) {
            return circuit;
        }
        if (r == null) {
            throw RpcError.simState("empty", "nothing is recorded yet");
        }
        Circuit cur = r.circuit();
        for (Component inst : path) {
            if (!cur.contains(inst) || !(inst.getFactory() instanceof SubcircuitFactory)) {
                throw RpcError.notFound("instance", doc.ids().peek(inst));
            }
            cur = ((SubcircuitFactory) inst.getFactory()).getSubcircuit();
        }
        if (cur != circuit) {
            throw RpcError.params("the path does not lead to circuitId");
        }
        return r.circuit();
    }

    /** record.removeRow: 사용자 줄 또는 임시 줄 하나를 걷는다. */
    public boolean removeRow(String id) {
        boolean removed = rows.removeIf(row -> row.id.equals(id));
        if (pinned.removeIf(row -> row.id.equals(id))) {
            removed = true;
            if (pinned.isEmpty()) {
                unpin();
            }
        }
        return removed;
    }

    /** record.rowBits: 버스 줄을 비트로 펼쳐 보이기(화면 표시만, v1 Show Bits). */
    public boolean rowBits(String id, boolean bits) {
        for (Row row : allRows()) {
            if (row.id.equals(id)) {
                row.signal.bits = bits;
                return true;
            }
        }
        return false;
    }

    /** 임시 줄로 둘 자리 하나(record.pin). */
    public static final class Spot {
        final Circuit circuit;
        final List<Component> path;
        final Location at;

        public Spot(Circuit circuit, List<Component> path, Location at) {
            this.circuit = circuit;
            this.path = path;
            this.at = at;
        }
    }

    /**
     * record.pin(V-03, D-098): 메시지의 원인 신호와 E·X가 생긴 자리를 표 맨 위 임시 줄로 둔다(있던 임시 줄은 대체).
     * cycle이 0 이상이면 그 사이클을 본다. 임시 줄은 저장하지 않고, 기록이 바뀌면(Reset, 다시 열기) 걷힌다(D-114).
     * 신호를 찾지 못한 자리는 빠진다(v1처럼, 메시지를 누른 학생에게 오류를 보이지 않는다).
     */
    public JsonObject pin(int cycle, List<Spot> spots) throws RpcError {
        List<Row> next = new ArrayList<>();
        for (Spot sp : spots) {
            CycleModel.Signal s;
            try {
                s = signal(sp.circuit, sp.path, sp.at, null, null);
            } catch (RpcError e) {
                continue; // 그 자리에 넷이 없거나 기록 밖의 인스턴스: 그 줄만 빠진다(v1 CycleView.pin)
            }
            boolean dup = false;
            for (Row row : next) {
                dup |= row.signal.equals(s);
            }
            if (!dup) {
                next.add(new Row("p" + nextRow++, s, true));
            }
        }
        pinned.clear();
        pinned.addAll(next);
        Recording r = recorder.current();
        pinnedRecording = r;
        pinnedGeneration = r == null ? 0 : r.generation();
        pinnedCycle = cycle;
        JsonObject o = new JsonObject();
        JsonArray ids = new JsonArray();
        for (Row row : pinned) {
            ids.add(row.id);
        }
        o.add("ids", ids);
        if (cycle >= 0 && r != null && !r.isEmpty()) {
            o.add("view", view(cycle, false));
        }
        return o;
    }

    /** record.unpin: 임시 줄을 걷는다(메시지가 사라졌을 때 Messages가 부른다, D-114). */
    public void unpin() {
        pinned.clear();
        pinnedCycle = -1;
        pinnedRecording = null;
    }

    /** D-114 (2): 기록이 바뀌었으면(Reset, 다시 시작, 임시 줄의 사이클이 기록 밖) 임시 줄을 걷는다. */
    private void checkPinned() {
        if (pinned.isEmpty()) {
            return;
        }
        Recording now = recorder.current();
        if (now == null || now != pinnedRecording || now.generation() != pinnedGeneration
                || pinnedCycle >= 0 && now.last() < CycleModel.stepOf(pinnedCycle)) {
            unpin();
        }
    }

    // ---- 지난 사이클(C-03) ----

    /**
     * record.view: 그 사이클(latest면 마지막 스텝)의 회로 상태를 보인다. 지난 사이클이면 체크포인트에서 다시 만든
     * 상태를 프로젝트에 바꿔 끼우고 클럭을 멈춘다(v1 Recorder.view). 값 스트림과 sim.state의 사이클도 그 시점이 된다.
     */
    public JsonObject view(int cycle, boolean latest) throws RpcError {
        if (until != null || sim != null && sim.busy()) {
            throw RpcError.simState("busy", "the clock is running (N Cycles or Run Until)");
        }
        settle();
        Recording r = recorder.current();
        if (r == null || r.isEmpty()) {
            throw RpcError.simState("empty", "nothing is recorded yet");
        }
        int step = latest ? r.last() : CycleModel.stepOf(cycle);
        // 바꿔 끼우기는 끼울 상태의 더러운 부품 집합(원조 SmallSet)을 고친다. 지금으로 돌아올 때 끼울 떼어 둔 상태는
        // 시뮬레이터 스레드가 아직 전파하고 있을 수 있으므로(앞서 지금으로 돌아올 때 요청한 전파), 편집처럼 시뮬레이터를
        // 전파 밖에 세워 둔 채 한다(SimGate, D-143; D-171)
        int at = sim == null ? recorder.view(step) : sim.quiet(() -> recorder.view(step));
        if (sim != null && at >= 0) {
            sim.syncTicks(at);
            sim.rewatch();
        }
        JsonObject o = new JsonObject();
        o.addProperty("cycle", CycleModel.cycleOf(Math.max(0, at)));
        o.addProperty("past", r.isViewingPast());
        return o;
    }

    /** record.values: 기록에서 읽은 그 사이클의 넷 값(다시 돌리지 않는다). 기록에 없는 넷은 빠진다. */
    public JsonObject values(int cycle, Circuit circuit, List<Component> path) throws RpcError {
        root(circuit, path);
        Recording r = recorder.current();
        JsonObject o = new JsonObject();
        o.addProperty("fileId", doc.id());
        o.addProperty("circuitId", doc.ids().of(circuit));
        o.addProperty("cycle", cycle);
        JsonObject nets = new JsonObject();
        if (r != null && !r.isEmpty()) {
            int step = CycleModel.stepOf(cycle);
            for (Netlist.Net n : doc.tracker().netlist(circuit).nets()) {
                Location at = ModelJson.point(n);
                Value v = at == null ? null : r.value(path, at, step);
                String t = ValueText.of(v, ModelJson.netWidth(circuit, n));
                if (t != null) {
                    nets.addProperty(ModelJson.netId(n), t);
                }
            }
        }
        o.add("nets", nets);
        return o;
    }

    // ---- Run Until(C-04) ----

    /**
     * record.runUntil: 한 사이클(틱 두 번)씩 요청하고, 기록이 그 사이클을 적은 뒤 조건을 보고 다음 사이클을 요청한다
     * (D-075, D-123: 쌓이는 틱이 2를 넘지 않는다). 곧바로 돌아오고, 끝나면 {@code record.runUntil}을 알린다.
     */
    public void runUntil(String kind, String value, int maxCycles) throws RpcError {
        if (until != null) {
            throw RpcError.simState("busy", "Run Until is already running");
        }
        if (sim != null && sim.busy()) {
            throw RpcError.simState("busy", "N Cycles is running");
        }
        settle(); // Reset 바로 뒤: 새 기록이 기준이다
        Simulator s = doc.project().getSimulator();
        if (!s.isRunning()) {
            throw s.isOscillating() ? RpcError.simState("oscillating", "the simulation stopped because the circuit"
                    + " oscillates") : RpcError.simState("off", "the simulation is off");
        }
        Recording r = recorder.current();
        if (r == null || r.isEmpty()) {
            throw RpcError.simState("empty", "nothing is recorded yet");
        }
        int max = maxCycles <= 0 ? RunUntil.DEFAULT_MAX_CYCLES : maxCycles;
        RunUntil v1 = null;
        String mnemonic = null;
        switch (kind) {
            case "pc": {
                Long pc = parsePc(value, symbols(r.circuit()));
                if (pc == null) {
                    throw badUntil("badPc", "cannot read the PC value: " + value);
                }
                v1 = RunUntil.pc((int) (long) pc, max);
                break;
            }
            case "instruction":
                if (value == null || value.trim().isEmpty()) {
                    throw badUntil("badInstruction", "an instruction name is required");
                }
                mnemonic = value;
                break;
            case "row": {
                Row row = null;
                for (Row x : allRows()) {
                    if (x.id.equals(value)) {
                        row = x;
                    }
                }
                if (row == null) {
                    throw badUntil("noRow", "no such row: " + value);
                }
                v1 = RunUntil.rowChanges(row.signal, max);
                break;
            }
            case "errorOrX":
                v1 = RunUntil.errorOrX(max);
                break;
            case "halt":
                v1 = RunUntil.halt(max);
                break;
            default:
                throw RpcError.params("kind must be pc, instruction, row, errorOrX or halt");
        }
        s.setIsTicking(false);
        if (sim != null) {
            sim.fastTicks(true); // 틱 스레드가 사이클마다 한 주기씩 자지 않게(D-144)
        }
        until = new Until(kind, value, v1, mnemonic, max, r, model(r).cursorCycle());
        untilGeneration = r.generation();
        untilActive = true;
        requestCycle();
    }

    private static RpcError badUntil(String reason, String message) {
        JsonObject d = new JsonObject();
        d.addProperty("reason", reason);
        return new RpcError(RpcError.INVALID_PARAMS, message, d);
    }

    /** PC 글(0x00400034, 400034, 실행 이미지의 기호 이름)을 주소로. 읽을 수 없으면 null. */
    static Long parsePc(String text, ProgramSymbols symbols) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return null;
        }
        Long named = symbols == null ? null : symbols.address(t);
        if (named != null) {
            return named;
        }
        if (t.startsWith("0x") || t.startsWith("0X")) {
            t = t.substring(2);
        }
        if (t.isEmpty() || t.length() > 8) {
            return null;
        }
        try {
            return Long.parseLong(t, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void requestCycle() {
        Until u = until;
        Simulator s = doc.project().getSimulator();
        if (!s.isRunning()) {
            finish("off");
            return;
        }
        int step = u.recording.cursor();
        int target = CycleModel.stepOf(CycleModel.cycleOf(step) + 1);
        u.requestedTo = target;
        for (int i = step; i < target; i++) {
            s.tick();
        }
    }

    private void onRecorded(Recording r) {
        Until u = until;
        if (u == null || r != u.recording) {
            return;
        }
        if (r.generation() != untilGeneration) {
            finish("stopped"); // Reset, 회로 편집: 기록이 새로 시작했다
            return;
        }
        int step = r.cursor();
        if (step < u.requestedTo || step % 2 != 0) {
            return;
        }
        int cycle = CycleModel.cycleOf(step);
        RunUntil.Result res = u.check(model(r), cycle);
        if (res == RunUntil.Result.RUNNING) {
            requestCycle();
        } else {
            finish(res == RunUntil.Result.MET ? "met" : "limit");
        }
    }

    /** record.stop: Run Until을 멈춘다(다음 사이클을 요청하지 않는다). 돌고 있지 않았으면 false. */
    public boolean stop() {
        if (until == null) {
            return false;
        }
        finish("stopped");
        return true;
    }

    private void finish(String result) {
        Until u = until;
        if (u == null) {
            return;
        }
        until = null;
        untilActive = false;
        if (sim != null) {
            sim.fastTicks(false);
        }
        JsonObject o = new JsonObject();
        o.addProperty("fileId", doc.id());
        o.addProperty("result", result);
        o.addProperty("cycle", CycleModel.cycleOf(Math.max(0, u.recording.cursor())));
        o.addProperty("from", u.startCycle);
        o.addProperty("kind", u.kind);
        if (u.value != null) {
            o.addProperty("value", u.value);
        }
        server.notify("record.runUntil", o);
    }

    /** Run Until이 도는가(테스트). */
    public boolean untilRunning() {
        return until != null;
    }

    // ---- Registers(C-05) ----

    /** record.registers: 보고 있는 사이클(또는 cycle)의 레지스터들. */
    public JsonObject registers(Integer cycleParam) {
        JsonObject o = new JsonObject();
        o.addProperty("fileId", doc.id());
        JsonArray out = new JsonArray();
        Recording r = recorder.current();
        if (r == null || r.isEmpty()) {
            o.addProperty("mode", "none");
            o.add("rows", out);
            return o;
        }
        CycleModel m = model(r);
        int cycle = cycleParam == null ? m.cursorCycle() : cycleParam;
        o.addProperty("cycle", cycle);
        o.addProperty("circuitId", doc.ids().of(r.circuit())); // 줄의 componentId가 든 최상위 회로(Mark as PC)
        List<Reg> regs = registerRows(r, m, cycle);
        Circuit rf = RegisterFile.marked(doc.file());
        boolean fileMode = rf != null && RegisterFile.pathTo(r.circuit(), rf) != null;
        o.addProperty("mode", fileMode ? "file" : regs.isEmpty() ? "none" : "all");
        if (fileMode) {
            JsonObject f = new JsonObject();
            f.addProperty("circuitId", doc.ids().of(rf));
            f.addProperty("name", rf.getName());
            o.add("registerFile", f);
        } else {
            boolean numbered = false;
            for (Reg g : regs) {
                numbered |= g.number >= 0;
            }
            o.addProperty("unmapped", !regs.isEmpty() && !numbered);
            o.add("candidates", candidates(r.circuit()));
        }
        for (Reg g : regs) {
            JsonObject j = new JsonObject();
            j.addProperty("key", g.key);
            j.addProperty("name", g.name);
            j.addProperty("number", g.number);
            j.addProperty("group", g.group);
            put(j, "value", g.value);
            j.addProperty("changed", g.changed);
            if (g.alias != null) {
                j.addProperty("alias", g.alias);
            }
            if (g.componentId != null) {
                j.addProperty("componentId", g.componentId);
            }
            if (g.markable) {
                j.addProperty("markable", true);
            }
            if (g.markedPc) {
                j.addProperty("markedPc", true);
            }
            out.add(j);
        }
        o.add("rows", out);
        return o;
    }

    /** 레지스터 한 줄(화면용). */
    static final class Reg {
        String key;
        String name;
        int number = -1;
        String group;
        String value;
        boolean changed;
        String alias;
        String componentId;
        boolean markable;
        boolean markedPc;
    }

    /**
     * v1 MachineState.registers와 같은 규칙(C-05, D-076, D-108): 레지스터 파일을 표시했으면 $0~$31(대응: 라벨 숫자 →
     * 라벨 이름 → 위치, 수동 대응 regmap), 아니면 모든 Register 부품(라벨이 $5·t0 같으면 번호도). PC(D-103)는
     * Special 묶음. 번호가 있는 줄은 Hallym MIPS 레지스터 창의 묶음과 순서로 둔다.
     */
    List<Reg> registerRows(Recording r, CycleModel m, int cycle) {
        Circuit root = r.circuit();
        int step = CycleModel.stepOf(cycle);
        int prev = CycleModel.stepOf(cycle - 1);
        boolean hasPrev = cycle > m.firstCycle();
        Map<Integer, Reg> byNumber = new TreeMap<>();
        List<Reg> others = new ArrayList<>();
        Reg pcRow = null;
        Component pc = pcRegister(root);
        Component marked = PcMark.marked(doc.file(), root);
        Circuit rf = RegisterFile.marked(doc.file());
        List<Component> rfPath = rf == null ? null : RegisterFile.pathTo(root, rf);
        if (rfPath != null) {
            Map<Integer, Component> map = RegisterFile.mapping(doc.file(), rf);
            for (int n = 0; n < 32; n++) {
                Component c = map.get(n);
                Reg g = reg(r, rfPath, c, step, prev, hasPrev);
                g.key = InstructionFields.REG[n];
                g.name = InstructionFields.REG[n];
                g.number = n;
                String label = c == null ? null : Names.label(c);
                if (label != null && !label.equals(g.name)) {
                    g.alias = label;
                }
                byNumber.put(n, g);
            }
            if (pc != null) {
                pcRow = reg(r, new ArrayList<Component>(), pc, step, prev, hasPrev);
                pcRow.alias = aliasOf(root, pc);
                pcRow.markable = PcMark.markable(pc);
                pcRow.markedPc = pc == marked;
            }
        } else {
            int i = 0;
            for (RegisterFile.Found f : RegisterFile.all(root)) {
                Reg g = reg(r, f.path, f.register, step, prev, hasPrev);
                boolean top = f.path.isEmpty();
                g.markable = top && PcMark.markable(f.register);
                g.markedPc = top && f.register == marked;
                if (top && f.register == pc && pcRow == null) {
                    g.alias = f.name.equalsIgnoreCase("PC") ? null : f.name;
                    pcRow = g;
                    continue;
                }
                int n = RegisterFile.numberOf(Names.label(f.register));
                if (n >= 0 && !byNumber.containsKey(n)) {
                    g.key = InstructionFields.REG[n];
                    g.name = InstructionFields.REG[n];
                    g.number = n;
                    if (!f.name.equals(g.name)) {
                        g.alias = f.name;
                    }
                    byNumber.put(n, g);
                } else {
                    g.key = "reg" + i++;
                    g.name = f.name;
                    g.number = n;
                    g.group = OTHER;
                    others.add(g);
                }
            }
        }
        List<Reg> out = new ArrayList<>();
        if (pcRow != null) {
            pcRow.key = "PC";
            pcRow.name = "PC";
            pcRow.group = GROUP_TITLES[0];
            out.add(pcRow);
        }
        for (int gi = 1; gi < GROUP_TITLES.length; gi++) {
            for (int n : GROUP_REGS[gi]) {
                Reg g = byNumber.get(n);
                if (g != null) {
                    g.group = GROUP_TITLES[gi];
                    out.add(g);
                }
            }
        }
        out.addAll(others);
        return out;
    }

    private Reg reg(Recording r, List<Component> path, Component c, int step, int prev, boolean hasPrev) {
        Reg g = new Reg();
        if (c == null || c.getEnds().isEmpty()) {
            return g;
        }
        Location q = c.getEnd(0).getLocation();
        Value v = r.value(path, q, step);
        Value p = hasPrev ? r.value(path, q, prev) : null;
        g.value = ValueText.of(v);
        g.changed = p != null && v != null && !p.equals(v);
        g.componentId = doc.ids().of(c);
        return g;
    }

    private static String aliasOf(Circuit root, Component c) {
        String label = Names.label(c);
        String name = label != null ? label : Names.title(root, c);
        return name.equalsIgnoreCase("PC") ? null : name;
    }

    /** 레지스터 파일로 표시할 수 있는 회로: root 아래에서 쓰이고 Register가 든 서브회로(Mark as Register File). */
    private JsonArray candidates(Circuit root) {
        JsonArray out = new JsonArray();
        for (Circuit c : doc.file().getCircuits()) {
            if (c == root || RegisterFile.pathTo(root, c) == null) {
                continue;
            }
            int n = 0;
            for (Component x : c.getNonWires()) {
                if (x.getFactory().getName().equals("Register")) {
                    n++;
                }
            }
            if (n > 0) {
                JsonObject o = new JsonObject();
                o.addProperty("circuitId", doc.ids().of(c));
                o.addProperty("name", c.getName());
                o.addProperty("registers", n);
                out.add(o);
            }
        }
        return out;
    }

    // ---- Memory(C-06, D-140) ----

    /** record.memory: 보고 있는 사이클의 회로 상태에서 Data Memory(옛 Stack 포함)를 Hallym MIPS Data 탭 같은 한 표로. */
    public JsonObject memory() {
        JsonObject o = new JsonObject();
        o.addProperty("fileId", doc.id());
        CircuitState root = doc.project().getSimulator().getCircuitState();
        Recording r = recorder.current();
        if (root == null) {
            o.add("rows", new JsonArray());
            o.addProperty("parts", 0);
            return o;
        }
        Map<String, Long> pointers = new LinkedHashMap<>();
        if (r != null && !r.isEmpty()) {
            CycleModel m = model(r);
            o.addProperty("cycle", m.cursorCycle());
            for (Reg g : registerRows(r, m, m.cursorCycle())) {
                String name = g.number == 29 ? "$sp" : g.number == 30 ? "$fp" : g.number == 28 ? "$gp" : null;
                if (name != null && !pointers.containsKey(name) && g.value != null && g.value.matches("[01]{32}")) {
                    pointers.put(name, Long.parseLong(g.value, 2));
                }
            }
        }
        Map<Long, List<String>> labels = symbols(root.getCircuit()).labels;
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                List<MemoryTable.Part> parts = MipsParts.collect(root.getCircuit(), root);
                o.addProperty("parts", parts.size());
                o.add("rows", MemoryTable.json(MemoryTable.build(parts, labels, pointers)));
                return o;
            } catch (ConcurrentModificationException | IllegalStateException e) {
                last = e; // 시뮬레이터 스레드가 쓰는 중에 읽었다: 다시 읽는다
            }
        }
        throw last;
    }

    // ---- Instruction(C-07) ----

    /** record.instruction: 그 사이클의 명령어를 필드로(Hallym MIPS Inspector 이름). */
    public JsonObject instruction(Integer cycleParam) {
        JsonObject o = new JsonObject();
        o.addProperty("fileId", doc.id());
        Recording r = recorder.current();
        if (r == null || r.isEmpty()) {
            o.addProperty("none", "empty");
            return o;
        }
        CycleModel m = model(r);
        int cycle = cycleParam == null ? m.cursorCycle() : cycleParam;
        o.addProperty("cycle", cycle);
        if (m.cpu() == null) {
            o.addProperty("none", "noCpu");
            return o;
        }
        Value pc = m.pc(cycle);
        Value in = m.instruction(cycle);
        put(o, "pc", ValueText.hex(pc));
        if (in == null || !in.isFullyDefined() || pc == null || !pc.isFullyDefined()) {
            o.addProperty("none", "undefined");
            return o;
        }
        int word = in.toIntValue();
        int at = pc.toIntValue();
        Map<Integer, String> names = symbols(r.circuit()).byAddress;
        o.addProperty("word", String.format("0x%08x", word));
        o.addProperty("text", Disassembler.text(word, at, names));
        String name = Disassembler.mnemonic(word);
        put(o, "mnemonic", name);
        o.addProperty("format", InstructionFields.format(word));
        JsonArray fields = new JsonArray();
        for (InstructionFields.Field f : InstructionFields.fields(word)) {
            JsonObject j = new JsonObject();
            j.addProperty("name", f.name);
            j.addProperty("hi", f.hi);
            j.addProperty("lo", f.lo);
            j.addProperty("bits", f.bits());
            j.addProperty("value", InstructionFields.value(f));
            j.addProperty("meaning", InstructionFields.meaning(f, word, at, names));
            fields.add(j);
        }
        o.add("fields", fields);
        return o;
    }

    // ---- 필드 경로(C-07 데이터: 캔버스의 필드 색 띠는 N-15가 그린다) ----

    /** v1 스플리터 팔 이름(FieldPaths) → Hallym MIPS 필드 이름(화면의 색 이름). */
    static String hallymField(String v1) {
        switch (v1) {
            case "op":
                return "opcode";
            case "imm":
                return "immediate";
            case "addr":
                return "target";
            default:
                return v1;
        }
    }

    /**
     * record.fieldPaths(v1 C-07, D-078의 데이터): 보고 있는(또는 cycle) 사이클 명령어의 형식에 있는 필드마다, 회로
     * shown 안에서 그 이름을 붙인 스플리터 팔의 선들(터널로 이어진 곳 포함, 첫 부품 입력까지; v1 FieldPaths). 이름
     * 붙인 팔만 따라가고 경로가 맞는지는 판단하지 않는다. 명령어가 정해지지 않았으면 모든 필드.
     */
    public JsonObject fieldPaths(Circuit shown, Integer cycleParam) {
        JsonObject o = new JsonObject();
        o.addProperty("fileId", doc.id());
        o.addProperty("circuitId", doc.ids().of(shown));
        Recording r = recorder.current();
        List<String> fields = kr.ac.hallym.hcs.app.cycle.FieldPaths.FIELDS;
        if (r != null && !r.isEmpty()) {
            CycleModel m = model(r);
            int cycle = cycleParam == null ? m.cursorCycle() : cycleParam;
            o.addProperty("cycle", cycle);
            Value in = m.instruction(cycle);
            if (in != null && in.isFullyDefined()) {
                fields = kr.ac.hallym.hcs.app.cycle.FieldPaths.fieldsOf(in.toIntValue());
                o.addProperty("format", InstructionFields.format(in.toIntValue()));
            }
        }
        JsonObject out = new JsonObject();
        for (Map.Entry<String, Set<Wire>> e : kr.ac.hallym.hcs.app.cycle.FieldPaths.of(doc.file(), shown, fields)
                .entrySet()) {
            JsonArray ids = new JsonArray();
            for (Wire w : e.getValue()) {
                ids.add(doc.ids().of(w));
            }
            out.add(hallymField(e.getKey()), ids);
        }
        o.add("fields", out);
        return o;
    }

    // ---- Mark as PC, Mark as Register File, Register Mapping (hcs:ext, v1 그대로) ----

    private JsonObject act(Action a) throws RpcError {
        if (doc.isReadOnly()) {
            throw RpcError.notEditable("readOnly", "the file is open read-only");
        }
        doc.project().doAction(a);
        JsonObject o = new JsonObject();
        o.addProperty("changed", true);
        o.addProperty("dirty", doc.isDirty());
        return o;
    }

    /** record.markPc(V-08, D-103): 레지스터·카운터를 이 회로의 PC로 표시하거나 푼다(되돌리기 한 번). */
    public JsonObject markPc(Circuit circuit, Component c, boolean on) throws RpcError {
        if (on && !PcMark.markable(c)) {
            throw RpcError.params("only a Register or a Counter can be marked as PC");
        }
        boolean now = PcMark.marked(doc.file(), circuit) == c;
        if (now == on) {
            JsonObject o = new JsonObject();
            o.addProperty("changed", false);
            o.addProperty("dirty", doc.isDirty());
            return o;
        }
        return act(PcMark.action(doc.file(), circuit, c, on));
    }

    /** record.markRegisterFile(C-05, D-076): 서브회로를 레지스터 파일로 표시하거나 푼다(하나만, 되돌리기 한 번). */
    public JsonObject markRegisterFile(Circuit rf, boolean on) throws RpcError {
        if (!doc.file().contains(rf)) {
            throw RpcError.notEditable("cannotModify", "a circuit of a library cannot be marked");
        }
        boolean now = RegisterFile.marked(doc.file()) == rf;
        if (now == on) {
            JsonObject o = new JsonObject();
            o.addProperty("changed", false);
            o.addProperty("dirty", doc.isDirty());
            return o;
        }
        return act(RegisterFile.markAction(doc.file(), rf, on));
    }

    /**
     * record.registerMapping: 표시한 레지스터 파일의 레지스터들(id, 이름, 자리)과 지금 대응·짐작한 대응(번호 → 부품 자리
     * {@code [x,y]}, 없으면 null). 대응은 v1이 {@code regmap}에 적는 것처럼 자리로 말한다: 엔진이 다시 시작해 부품 id가
     * 바뀌어도 되살리기(D-142)가 그대로 다시 보낼 수 있다.
     */
    public JsonObject registerMapping() throws RpcError {
        Circuit rf = RegisterFile.marked(doc.file());
        if (rf == null) {
            throw RpcError.notFound("registerFile", "(none marked)");
        }
        JsonObject o = new JsonObject();
        o.addProperty("circuitId", doc.ids().of(rf));
        o.addProperty("name", rf.getName());
        JsonArray regs = new JsonArray();
        List<Component> sorted = new ArrayList<>();
        for (Component x : rf.getNonWires()) {
            if (x.getFactory().getName().equals("Register")) {
                sorted.add(x);
            }
        }
        sorted.sort(java.util.Comparator.<Component>comparingInt(x -> x.getLocation().getY())
                .thenComparingInt(x -> x.getLocation().getX()));
        for (Component x : sorted) {
            JsonObject j = new JsonObject();
            j.addProperty("id", doc.ids().of(x));
            String label = Names.label(x);
            j.addProperty("name", label != null ? label : Names.title(rf, x));
            j.add("loc", ModelJson.point(x.getLocation()));
            regs.add(j);
        }
        o.add("registers", regs);
        o.add("map", mapJson(RegisterFile.mapping(doc.file(), rf)));
        o.add("guess", mapJson(RegisterFile.estimate(rf)));
        return o;
    }

    private JsonObject mapJson(Map<Integer, Component> map) {
        JsonObject o = new JsonObject();
        for (int n = 0; n < 32; n++) {
            Component c = map.get(n);
            if (c == null) {
                o.add(Integer.toString(n), JsonNull.INSTANCE);
            } else {
                o.add(Integer.toString(n), ModelJson.point(c.getLocation()));
            }
        }
        return o;
    }

    /**
     * record.setRegisterMapping: 번호 → 레지스터 파일 안 Register 부품의 자리(null이면 없음). 빠진 번호는 지금 대응
     * 그대로. 짐작과 다른 것만 {@code regmap}으로 저장한다(v1 RegisterFile.mapAction, 되돌리기 한 번). circuit은 표시한
     * 레지스터 파일이어야 한다.
     */
    public JsonObject setRegisterMapping(Circuit circuit, Map<Integer, Location> chosen) throws RpcError {
        Circuit rf = RegisterFile.marked(doc.file());
        if (rf == null) {
            throw RpcError.notFound("registerFile", "(none marked)");
        }
        if (circuit != rf) {
            throw RpcError.params("circuitId is not the marked register file");
        }
        Map<Location, Component> at = new java.util.HashMap<>();
        for (Component x : rf.getNonWires()) {
            if (x.getFactory().getName().equals("Register")) {
                at.putIfAbsent(x.getLocation(), x);
            }
        }
        Map<Integer, Component> map = new TreeMap<>(RegisterFile.mapping(doc.file(), rf));
        Set<Component> seen = new HashSet<>();
        for (Map.Entry<Integer, Location> e : chosen.entrySet()) {
            int n = e.getKey();
            if (n < 0 || n > 31) {
                throw RpcError.params("register numbers are 0 to 31");
            }
            if (e.getValue() == null) {
                map.remove(n);
                continue;
            }
            Component c = at.get(e.getValue());
            if (c == null) {
                throw RpcError.notFound("register", "(" + e.getValue().getX() + "," + e.getValue().getY() + ")");
            }
            map.put(n, c);
        }
        for (Map.Entry<Integer, Component> e : map.entrySet()) {
            if (!seen.add(e.getValue())) {
                throw RpcError.params("a register component can have one number only");
            }
        }
        if (map.equals(RegisterFile.mapping(doc.file(), rf))) {
            JsonObject o = new JsonObject();
            o.addProperty("changed", false);
            o.addProperty("dirty", doc.isDirty());
            return o;
        }
        return act(RegisterFile.mapAction(doc.file(), rf, map));
    }
}
