/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;

import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.regress.CircNormalizer;

/**
 * P-07 경로 계산(GUI 없이): tests/circ/flow/의 작은 회로와 demo-datapath에서 누른 결과의 끝점·지난 부품·점프를
 * 고정 기대값 파일(tests/circ/flow/*.flow)과 비교한다. {@code -Phcs.update=true}로 기대값을 다시 쓴다.
 */
class SignalFlowPathTest {
    static final File DIR = new File(System.getProperty("hcs.circDir"), "flow");
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));
    static final boolean UPDATE = Boolean.getBoolean("hcs.update");

    @TempDir
    Path tmp;

    // ---- 회로 파일 ----

    @Test
    void committedCircuitsMatchTheGenerator() throws Exception {
        for (String name : FlowCircuits.ALL.keySet()) {
            File dir = Files.createTempDirectory(tmp, name).toFile();
            File fresh = FlowCircuits.make(name, dir);
            File committed = new File(DIR, name + ".circ");
            // 기대값을 다시 쓸 때도 내용(정규화)이 같으면 파일을 건드리지 않는다(저장 순서만 다른 변경을 막는다)
            if (UPDATE && (!committed.exists() || !CircNormalizer.normalize(read(committed)).equals(
                    CircNormalizer.normalize(read(fresh))))) {
                Files.createDirectories(DIR.toPath());
                Files.copy(fresh.toPath(), committed.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            assertEquals(CircNormalizer.normalize(read(committed)), CircNormalizer.normalize(read(fresh)), name);
        }
    }

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    LogisimFile open(String name) throws Exception {
        Path dir = Files.createTempDirectory(tmp, "o");
        Files.copy(new File(DIR, name + ".circ").toPath(), dir.resolve(name + ".circ"));
        return new Loader(null).openLogisimFile(dir.resolve(name + ".circ").toFile());
    }

    LogisimFile openDemo() throws Exception {
        return openDemo(tmp);
    }

    static LogisimFile openDemo(Path tmp) throws Exception {
        Path dir = Files.createTempDirectory(tmp, "demo");
        Files.copy(MIPS_JAR.toPath(), dir.resolve("hcs-mips.jar"), StandardCopyOption.REPLACE_EXISTING);
        Path circ = dir.resolve("demo-datapath.circ");
        Files.copy(new File(System.getProperty("hcs.circDir"), "demo-datapath.circ").toPath(), circ);
        return new Loader(null).openLogisimFile(circ.toFile());
    }

    static Component byLabel(Circuit c, String label, String factory) {
        Component found = null;
        for (Component x : c.getNonWires()) {
            if (label.equals(x.getAttributeSet().getValue(StdAttr.LABEL))
                    && (factory == null || x.getFactory().getName().equals(factory))) {
                found = x;
            }
        }
        return found;
    }

    static Component firstSub(Circuit c, String name) {
        for (Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals(name)) {
                return x;
            }
        }
        return null;
    }

    // ---- 기대값 ----

    /** 사람이 읽는 요약: 끝점(종류, 라벨, 시각), 지난 부품(시각), 점프 수, 구간 수, 고리, 선택 미확정. */
    static String summary(SignalFlowPath p) {
        StringBuilder b = new StringBuilder();
        b.append("direction: ").append(p.backward ? "backward" : "forward").append('\n');
        b.append("endpoints:\n");
        for (SignalFlowPath.Endpoint e : p.endpoints) {
            b.append("  ").append(e.kind).append(' ').append(e.label).append(" @").append(Math.round(e.time))
                    .append('\n');
        }
        b.append("passes:\n");
        for (SignalFlowPath.Pass x : p.passes) {
            String name = Names.label(x.component);
            b.append("  ").append(name != null ? name : x.component.getFactory().getName())
                    .append(x.boundary ? " (boundary)" : "").append(" @").append(Math.round(x.time)).append('\n');
        }
        b.append("jumps: ").append(p.jumps.size()).append('\n');
        b.append("segments: ").append(p.segments.size()).append('\n');
        b.append("next-cycle segments: ").append(p.segments.stream().filter(s -> s.cycle > 0).count()).append('\n');
        b.append("next-cycle passes: ").append(p.passes.stream().filter(x -> x.cycle > 0).count()).append('\n');
        List<String> loops = new ArrayList<>();
        for (Component c : p.loops) {
            loops.add(Names.label(c) != null ? Names.label(c) : c.getFactory().getName());
        }
        b.append("loops: ").append(loops).append('\n');
        List<String> und = new ArrayList<>();
        for (Component c : p.undetermined) {
            und.add(Names.label(c) != null ? Names.label(c) : c.getFactory().getName());
        }
        b.append("undetermined: ").append(und).append('\n');
        b.append("total: ").append(Math.round(p.total)).append('\n');
        return b.toString();
    }

    void expect(String name, SignalFlowPath p) throws Exception {
        File f = new File(DIR, name + ".flow");
        String got = summary(p);
        if (UPDATE) {
            Files.write(f.toPath(), got.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(read(f), got, name);
    }

    static SignalFlowPath.Options forward() {
        return new SignalFlowPath.Options();
    }

    static SignalFlowPath.Options backward() {
        SignalFlowPath.Options o = new SignalFlowPath.Options();
        o.backward = true;
        return o;
    }

    // ---- 작은 회로 ----

    @Test
    void gateChain() throws Exception {
        Circuit c = open("gate-chain").getMainCircuit();
        SignalFlowPath p = SignalFlowPath.fromComponent(c, byLabel(c, "A", "Pin"), -1, forward());
        expect("gate-chain-forward", p);
        assertEquals(List.of("OUTPUT Y"), p.endpointSummary());
    }

    @Test
    void backwardFlowsFromTheSourcesToTheClick() throws Exception {
        Circuit c = open("gate-chain").getMainCircuit();
        SignalFlowPath p = SignalFlowPath.fromComponent(c, byLabel(c, "Y", "Pin"), -1, backward());
        expect("gate-chain-backward", p);
        assertEquals(3, p.endpoints.size(), "A, B, C");
        // 흐름은 실제 신호 방향: 출처가 먼저(시각 작음), 누른 Y 쪽 구간이 마지막
        for (SignalFlowPath.Endpoint e : p.endpoints) {
            assertEquals(SignalFlowPath.EndKind.SOURCE, e.kind);
        }
        // 가장 먼 출처 A가 0에서 떠나고, 누른 Y 앞의 g2가 마지막에 빛난다
        assertEquals(0, Math.round(p.endpoints.get(0).time));
        SignalFlowPath.Pass lastPass = p.passes.get(p.passes.size() - 1);
        assertEquals("g2", Names.label(lastPass.component));
        for (SignalFlowPath.Pass x : p.passes) {
            assertTrue(x.time < p.total, "every pass happens before the flow reaches Y");
        }
    }

    @Test
    void registerStopsAndThroughRegistersGoesOn() throws Exception {
        Circuit c = open("register").getMainCircuit();
        Component a = byLabel(c, "A", "Pin");
        SignalFlowPath stop = SignalFlowPath.fromComponent(c, a, -1, forward());
        expect("register-stop", stop);
        assertEquals(List.of("STATE R (D)"), stop.endpointSummary());
        SignalFlowPath.Options o = forward();
        o.throughRegisters = true;
        SignalFlowPath through = SignalFlowPath.fromComponent(c, a, -1, o);
        expect("register-through", through);
        assertTrue(through.passes.stream().anyMatch(x -> x.cycle == 1 && "n2".equals(Names.label(x.component))),
                "after the register: next cycle");
        assertTrue(through.endpointSummary().contains("OUTPUT Y"));
    }

    @Test
    void tunnelsJumpToEverySameNameTunnel() throws Exception {
        Circuit c = open("tunnels").getMainCircuit();
        SignalFlowPath p = SignalFlowPath.fromComponent(c, byLabel(c, "A", "Pin"), -1, forward());
        expect("tunnels", p);
        assertEquals(2, p.jumps.stream().filter(j -> j.from.equals(p.jumps.get(0).from)).count(),
                "from the A tunnel to both NOT tunnels");
        assertEquals(List.of("OUTPUT Y1", "OUTPUT Y2"), p.endpointSummary());
    }

    @Test
    void subcircuitLetsTheFlowOutOnlyWhereItIsConnectedInside() throws Exception {
        Circuit c = open("subcircuit").getMainCircuit();
        SignalFlowPath p = SignalFlowPath.fromComponent(c, byLabel(c, "A", "Pin"), -1, forward());
        expect("subcircuit", p);
        assertEquals(List.of("OUTPUT Y"), p.endpointSummary(), "not Z");
        assertTrue(p.passes.stream().anyMatch(x -> x.boundary), "the chip border lights up");
        assertTrue(p.passes.stream().anyMatch(x -> !x.instances.isEmpty() && "ny".equals(Names.label(x.component))),
                "the path inside is computed");
        assertFalse(p.passes.stream().anyMatch(x -> "nz".equals(Names.label(x.component))), "not through nz");
    }

    @Test
    void splitterFollowsOnlyTheBitsOfInstruction25to21() throws Exception {
        Circuit c = open("splitter").getMainCircuit();
        SignalFlowPath.Options o = forward();
        o.startBits = new BitSet();
        o.startBits.set(21, 26);
        SignalFlowPath p = SignalFlowPath.fromComponent(c, byLabel(c, "Instruction", "Pin"), -1, o);
        expect("splitter-rs", p);
        assertEquals(List.of("OUTPUT LO"), p.endpointSummary(), "rs goes to [4:0] only, not HI or OP");
        SignalFlowPath all = SignalFlowPath.fromComponent(c, byLabel(c, "Instruction", "Pin"), -1, forward());
        assertTrue(all.endpointSummary().containsAll(List.of("OUTPUT LO", "OUTPUT HI", "OUTPUT OP")));
    }

    /** 가짜 값: 선택 입력 S의 점에서만 sel을 돌려준다. */
    static ValueSource selectIs(Location at, Value sel) {
        return (instances, p) -> p.equals(at) ? sel : Value.createKnown(
                com.cburch.logisim.data.BitWidth.create(4), 0);
    }

    @Test
    void activePathOnlyFollowsTheSelectedMuxInput() throws Exception {
        Circuit c = open("mux").getMainCircuit();
        Component m = null;
        for (Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals("Multiplexer")) {
                m = x;
            }
        }
        Location sel = m.getEnd(2).getLocation();
        Component a = byLabel(c, "A", "Pin");
        String[][] cases = {{"0", "mux-active-sel0"}, {"1", "mux-active-sel1"}, {"x", "mux-active-selx"}};
        for (String[] k : cases) {
            SignalFlowPath.Options o = forward();
            o.activeValues = selectIs(sel, k[0].equals("x") ? Value.UNKNOWN : Value.createKnown(
                    com.cburch.logisim.data.BitWidth.ONE, Integer.parseInt(k[0])));
            SignalFlowPath p = SignalFlowPath.fromComponent(c, a, -1, o);
            expect(k[1], p);
            switch (k[0]) {
            case "0":
                assertEquals(List.of("OUTPUT Y"), p.endpointSummary(), "A is selected");
                assertTrue(p.undetermined.isEmpty());
                break;
            case "1":
                assertEquals(List.of(), p.endpointSummary(), "B is selected: A goes nowhere");
                break;
            default:
                assertEquals(List.of("OUTPUT Y"), p.endpointSummary(), "undetermined: all branches");
                assertEquals(List.of(m), p.undetermined);
            }
        }
    }

    @Test
    void activePathReadsTheRunningSimulation() throws Exception {
        LogisimFile f = open("mux");
        Circuit c = f.getMainCircuit();
        com.cburch.logisim.proj.Project proj = new com.cburch.logisim.proj.Project(f);
        proj.getSimulator().setIsRunning(false);
        com.cburch.logisim.circuit.CircuitState st = proj.getCircuitState();
        Component s = byLabel(c, "S", "Pin");
        com.cburch.logisim.instance.InstanceState is = st.getInstanceState(s);
        st.getPropagator().propagate(); // 먼저 한 번 돌려 둔 상태에서
        ((com.cburch.logisim.std.wiring.Pin) s.getFactory()).setValue(is, Value.TRUE);
        is.fireInvalidated(); // 조작 도구처럼 핀이 새 값을 내보내게
        st.getPropagator().propagate();
        SignalFlowPath.Options o = forward();
        o.activeValues = ValueSource.of(st);
        SignalFlowPath fromA = SignalFlowPath.fromComponent(c, byLabel(c, "A", "Pin"), -1, o);
        SignalFlowPath fromB = SignalFlowPath.fromComponent(c, byLabel(c, "B", "Pin"), -1, o);
        assertEquals(List.of(), fromA.endpointSummary(), "S = 1 selects B");
        assertEquals(List.of("OUTPUT Y"), fromB.endpointSummary());
    }

    @Test
    void combinationalLoopEnds() throws Exception {
        Circuit c = open("loop").getMainCircuit();
        SignalFlowPath p = SignalFlowPath.fromComponent(c, byLabel(c, "A", "Pin"), -1, forward());
        expect("loop", p);
        assertFalse(p.loops.isEmpty(), "the loop closure is marked");
        assertEquals(List.of("OUTPUT Y"), p.endpointSummary());
    }

    @Test
    void wireClickStartsAtTheDriverAndMarksTheClick() throws Exception {
        Circuit c = open("gate-chain").getMainCircuit();
        com.cburch.logisim.circuit.Wire w = null;
        for (com.cburch.logisim.circuit.Wire x : c.getWires()) {
            w = w == null || x.getEnd0().getX() > w.getEnd0().getX() ? x : w;
        }
        if (w == null) {
            return; // 터널로만 이은 회로: 선이 없으면 부품 클릭으로 본다
        }
        SignalFlowPath p = SignalFlowPath.fromWire(c, w, w.getEnd1(), forward());
        assertEquals(w.getEnd1(), p.click);
        assertFalse(p.segments.isEmpty());
    }

    // ---- 데모 ----

    @Test
    void demoPcAluAndMuxInput() throws Exception {
        Circuit c = openDemo().getMainCircuit();
        Component pc = byLabel(c, "PC", "Register");
        expect("demo-pc", SignalFlowPath.fromComponent(c, pc, 0, forward()));
        Component alu = firstSub(c, "alu");
        int result = -1;
        for (int i = 0; i < alu.getEnds().size(); i++) {
            if (kr.ac.hallym.hcs.app.model.Kinds.portName(alu, i).equals("Result")) {
                result = i;
            }
        }
        expect("demo-alu-result", SignalFlowPath.fromComponent(c, alu, result, forward()));
        // 데모에는 RegDst MUX가 없어(rd를 바로 WR로) 메인의 유일한 MUX(MemtoReg)의 입력 1(ReadData)을 누른다(D-063)
        Component mux = null;
        for (Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals("Multiplexer")) {
                mux = x;
            }
        }
        expect("demo-mux-input", SignalFlowPath.fromComponent(c, mux, 1, backward()));
        // Instruction Memory의 Instr 출력: 스플리터 팔 중 쓰지 않는 것(op, funct)은 비트 범위로 적는다
        Component im = null;
        for (Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals("Instruction Memory")) {
                im = x;
            }
        }
        int instr = -1;
        for (int i = 0; i < im.getEnds().size(); i++) {
            if (im.getEnds().get(i).isOutput()) {
                instr = i;
            }
        }
        SignalFlowPath ip = SignalFlowPath.fromComponent(c, im, instr, forward());
        expect("demo-im-instr", ip);
        assertTrue(ip.endpointSummary().contains("UNCONNECTED Splitter [31:26]"), ip.endpointSummary().toString());
    }

    @Test
    void sameClickGivesTheSamePathEveryTime() throws Exception {
        Circuit c = openDemo().getMainCircuit();
        Component pc = byLabel(c, "PC", "Register");
        SignalFlowPath.Options o = forward();
        o.throughRegisters = true;
        String first = SignalFlowPath.fromComponent(c, pc, 0, o).fingerprint();
        for (int i = 0; i < 100; i++) {
            assertEquals(first, SignalFlowPath.fromComponent(c, pc, 0, o).fingerprint());
        }
    }
}
