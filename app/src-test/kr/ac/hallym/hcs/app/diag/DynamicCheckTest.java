/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.cycle.CycleModel;
import kr.ac.hallym.hcs.app.cycle.RunUntil;
import kr.ac.hallym.hcs.app.record.Recording;
import kr.ac.hallym.hcs.app.record.RecordingTestSupport;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * D-01·D-03 동적 진단: E가 생기면 처음 생긴 곳 한 번, 클럭 에지의 X 쓰기는 에지 직전 기록값으로 원인을 찾는다.
 * 정상 회로(ref-mips + 예제 .s, demo 회로들)를 돌리면 0건이다. 지난 스텝을 다시 쓰면 그 뒤 진단을 버리고 다시 본다.
 */
class DynamicCheckTest {
    @TempDir
    Path tmp;

    LogisimFile fresh() throws Exception {
        return CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
    }

    /** 회로를 steps 스텝 돌리며 기록한다. */
    static final class Run {
        final Project proj;
        final CircuitState root;
        final Recording rec;

        Run(LogisimFile f) {
            proj = new Project(f);
            proj.getSimulator().setIsRunning(false);
            root = new CircuitState(proj, f.getMainCircuit());
            root.getPropagator().propagate();
            rec = new Recording(f.getMainCircuit());
            rec.restart(root, 0);
        }

        void steps(int n) {
            for (int i = 0; i < n; i++) {
                Propagator p = root.getPropagator();
                p.tick();
                p.propagate();
                rec.capture(root, rec.last() + 1, false);
            }
        }

        List<Diagnostic> scan() {
            DynamicCheck c = new DynamicCheck(rec.circuit(), rec);
            Map<String, Integer> seen = new HashMap<>();
            List<Diagnostic> out = new ArrayList<>();
            for (int s = rec.first(); s <= rec.last(); s++) {
                out.addAll(c.step(s, seen));
            }
            return out;
        }
    }

    @Test
    void bufferAndConstantFightOnceTheBufferTurnsOn() throws Exception {
        // 3상태 버퍼는 떠 있을 수 있어 정적 검사가 합선으로 보지 않는다. 클럭이 1일 때 버퍼가 켜져 0과 1이 부딪힌다
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component clock = b.add("Wiring", "Clock", 100, 400);
        b.tunnel(clock, 0, "clk");
        Component buf = b.add("Gates", "Controlled Buffer", 300, 200);
        b.constant("zero", 1, 0, 100, 100);
        b.tunnel(buf, 1, "zero");
        b.tunnel(buf, 2, "clk");
        b.tunnel(buf, 0, "w");
        Component one = b.constant("w", 1, 1, 100, 300);
        Component not = b.add("Gates", "NOT Gate", 500, 300);
        b.tunnel(not, 1, "w");
        b.tunnel(not, 0, "y");
        b.output("y", 1, 700, 100);
        b.commit();
        assertEquals(List.of(), StaticCheck.run(f), "the static check allows a buffer that may float");
        Run r = new Run(f);
        r.steps(8);
        List<Diagnostic> ds = r.scan();
        assertEquals(1, ds.size(), "one cause even though E comes back every cycle: " + ds);
        Diagnostic d = ds.get(0);
        assertEquals(Diagnostic.Kind.E_APPEARED, d.kind);
        assertEquals(1, d.step, "the first rising edge");
        assertEquals(1, d.args().get(0), "cycle 1");
        assertTrue(d.components.contains(buf) && d.components.contains(one), d.components.toString());
        assertTrue(d.message().contains(Messages.get("diag.causePrefix", "").trim()), d.message());
    }

    @Test
    void registerEnableFromAnUndefinedPin() throws Exception {
        // PLAN.md 4.4 예: RegWrite가 정해지지 않은 채 레지스터의 en에 가면, 클럭 에지에 그 사이클과 원인을 말한다
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component clock = b.add("Wiring", "Clock", 100, 400);
        b.tunnel(clock, 0, "clk");
        Component reg = b.add("Memory", "Register", 400, 200, "width", "8", "label", "R1");
        b.constant("d", 8, 7, 100, 100);
        b.tunnel(reg, 1, "d");
        b.tunnel(reg, 2, "clk");
        b.tunnel(reg, 4, "RegWrite");
        Component pin = b.input("RegWrite", 1, 100, 300); // 3상태 기본값: X
        b.tunnel(reg, 0, "q");
        b.output("q", 8, 700, 100);
        b.commit();
        Run r = new Run(f);
        r.steps(6);
        List<Diagnostic> ds = r.scan();
        assertEquals(1, ds.size(), ds.toString());
        Diagnostic d = ds.get(0);
        assertEquals(Diagnostic.Kind.X_WRITE_CONTROL, d.kind);
        assertEquals(0, d.step, "inputs just before the first rising edge");
        assertEquals("main › R1", d.args().get(1));
        assertEquals("en", d.args().get(2));
        assertTrue(d.components.contains(pin), "points at the cause: " + d.components);
        assertEquals(Messages.get("diag.causePrefix", Messages.get("diag.cause.INPUT_PIN", "main › RegWrite")),
                d.args().get(3));
    }

    @Test
    void registerDataUndefinedWhileEnabled() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component clock = b.add("Wiring", "Clock", 100, 400);
        b.tunnel(clock, 0, "clk");
        Component reg = b.add("Memory", "Register", 400, 200, "width", "8", "label", "PC");
        b.tunnel(reg, 1, "next"); // D: 3상태 입력 핀에서(X)
        b.input("next", 8, 100, 100);
        b.tunnel(reg, 2, "clk"); // en은 연결 안 함: 원조처럼 늘 쓴다
        b.tunnel(reg, 0, "q");
        b.output("q", 8, 700, 100);
        b.commit();
        Run r = new Run(f);
        r.steps(4);
        List<Diagnostic> ds = r.scan();
        assertEquals(1, ds.size(), ds.toString());
        assertEquals(Diagnostic.Kind.X_WRITE_DATA, ds.get(0).kind);
        assertEquals("D", ds.get(0).args().get(2));
        assertEquals(Value.createKnown(com.cburch.logisim.data.BitWidth.create(8), 0),
                r.rec.value(com.cburch.logisim.data.Location.create(700, 100), 4), "the register kept its value");
    }

    @Test
    void normalCircuitsStayQuiet() throws Exception {
        for (String program : new String[] {"mips/factorial.s", "mips/alu.s", "mips/branches.s", "mips/memory.s"}) {
            Path dir = Files.createTempDirectory(tmp, "m");
            LogisimFile file = RecordingTestSupport.openRefMips(dir);
            RecordingTestSupport.load(file, RecordingTestSupport.program(program));
            Circuit main = file.getMainCircuit();
            Run r = new Run(file);
            CycleModel.Cpu cpu = CycleModel.findCpu(main);
            CycleModel model = new CycleModel(main, r.rec, cpu, kr.ac.hallym.hcs.app.cycle.ProgramSource.EMPTY);
            for (int i = 0; i < 1500; i++) {
                r.steps(2);
                if (RunUntil.halted(model, r.rec.last() / 2)) {
                    break;
                }
            }
            assertEquals(List.of(), r.scan(), program);
        }
        for (String circ : new String[] {"demo-datapath.circ", "stack-demo.circ", "console-demo.circ"}) {
            LogisimFile file = RecordingTestSupport.openCirc(Files.createTempDirectory(tmp, "d"), circ);
            Run r = new Run(file);
            r.steps(24);
            assertEquals(List.of(), r.scan(), circ);
        }
    }

    /** 원인이 같으면 한 번만(PLAN.md 4.4): 떠 있는 RegWrite가 AND를 거쳐 E도 만들고 en X 쓰기도 만든다. */
    @Test
    void oneCauseOneMessageAcrossKinds() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component clock = b.add("Wiring", "Clock", 100, 400);
        b.tunnel(clock, 0, "clk");
        Component and = b.add("Gates", "AND Gate", 300, 300, "inputs", "2");
        b.tunnel(and, 1, "RegWrite");
        b.constant("sel", 1, 1, 100, 200);
        b.tunnel(and, 2, "sel");
        b.tunnel(and, 0, "we"); // 원조 AND는 X 입력에 E를 낸다
        Component reg = b.add("Memory", "Register", 500, 200, "width", "8", "label", "R1");
        b.constant("d", 8, 7, 100, 100);
        b.tunnel(reg, 1, "d");
        b.tunnel(reg, 2, "clk");
        b.tunnel(reg, 4, "we");
        Component pin = b.input("RegWrite", 1, 100, 300);
        b.tunnel(reg, 0, "q");
        b.output("q", 8, 700, 100);
        b.commit();
        Run r = new Run(f);
        r.steps(6);
        List<Diagnostic> ds = r.scan();
        assertEquals(1, ds.size(), ds.toString());
        assertTrue(ds.get(0).components.contains(pin));
    }

    /** 선 우클릭 Find E/X Origin: 보이는 상태에서 원인을 찾고 원인 문장을 알린다. 정해진 선은 메뉴가 꺼진다. */
    @Test
    void findOriginFromAWire() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component not = b.add("Gates", "NOT Gate", 300, 200);
        Component pin = b.input("a", 1, 100, 100); // 3상태 기본값: X
        b.tunnel(not, 1, "a");
        Component out = b.add("Wiring", "Pin", 400, 200, "facing", "west", "output", "true", "label", "y");
        b.wire(not.getEnd(0).getLocation(), out.getEnd(0).getLocation());
        b.commit();
        Project proj = new Project(f);
        proj.getCircuitState().getPropagator().propagate();
        com.cburch.logisim.circuit.Wire w = f.getMainCircuit().getWires().iterator().next();
        assertTrue(FindOrigin.undefined(proj, w));
        kr.ac.hallym.hcs.app.model.OriginTrace.Origin o = FindOrigin.find(proj, f.getMainCircuit(), w);
        assertEquals(kr.ac.hallym.hcs.app.model.OriginTrace.Cause.INPUT_PIN, o.cause);
        assertEquals(pin, o.component);
        FindOrigin.run(proj, f.getMainCircuit(), w);
        assertEquals(Messages.get("origin.found", Messages.get("diag.cause.INPUT_PIN", "main › a")),
                kr.ac.hallym.hcs.app.sim.SimControls.lastNotice(proj));
        com.cburch.logisim.std.wiring.Pin.FACTORY.setValue(proj.getCircuitState().getInstanceState(pin), Value.TRUE);
        proj.getCircuitState().getInstanceState(pin).fireInvalidated();
        proj.getCircuitState().getPropagator().propagate();
        assertTrue(!FindOrigin.undefined(proj, w), "defined now: the menu item is off");
    }

    /** 시뮬레이터 스레드에서 스텝마다 도므로 가볍게(PERFORMANCE.md D-01): ref-mips 한 스텝 평균 2ms 안. */
    @Test
    void scanningAStepIsCheap() throws Exception {
        LogisimFile file = RecordingTestSupport.openRefMips(tmp);
        RecordingTestSupport.load(file, RecordingTestSupport.program("mips/factorial.s"));
        Run r = new Run(file);
        r.steps(600);
        DynamicCheck c = new DynamicCheck(r.rec.circuit(), r.rec);
        Map<String, Integer> seen = new HashMap<>();
        for (int s = 1; s <= 100; s++) {
            c.step(s, seen); // 데우기
        }
        long t0 = System.nanoTime();
        for (int s = 101; s <= 600; s++) {
            c.step(s, seen);
        }
        double ms = (System.nanoTime() - t0) / 1e6 / 500;
        System.out.println("hcs-perf dynamic-check ms/step " + ms);
        assertTrue(ms < 2.0, "ms per step " + ms);
    }

    @Test
    void rewritingAPastStepDropsLaterMessages() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component clock = b.add("Wiring", "Clock", 100, 400);
        b.tunnel(clock, 0, "clk");
        Component reg = b.add("Memory", "Register", 400, 200, "width", "8", "label", "R1");
        b.constant("d", 8, 7, 100, 100);
        b.tunnel(reg, 1, "d");
        b.tunnel(reg, 2, "clk");
        b.tunnel(reg, 4, "RegWrite");
        Component pin = b.input("RegWrite", 1, 100, 300);
        b.tunnel(reg, 0, "q");
        b.output("q", 8, 700, 100);
        b.commit();
        Run r = new Run(f);
        Diagnostics diags = Diagnostics.of(r.proj);
        // 처음 한 번은 기록을 새로 본다. 스텝 0에서 RegWrite를 정해 두면 에지에서 문제가 없다
        com.cburch.logisim.std.wiring.Pin.FACTORY.setValue(r.root.getInstanceState(pin), Value.FALSE);
        r.root.getInstanceState(pin).fireInvalidated();
        r.root.getPropagator().propagate();
        r.rec.capture(r.root, 0, true);
        r.steps(4);
        diags.onRecording(r.rec);
        assertEquals(List.of(), diags.dynamic());
        // 스텝 2로 돌아가 RegWrite를 떠 있게 하고 다시 진행: 스텝 2 뒤를 버리고 새 기록을 본다
        r.rec.truncateAfter(2);
        com.cburch.logisim.std.wiring.Pin.FACTORY.setValue(r.root.getInstanceState(pin), Value.UNKNOWN);
        r.root.getInstanceState(pin).fireInvalidated();
        r.root.getPropagator().propagate();
        r.rec.capture(r.root, 2, true);
        r.steps(4);
        diags.onRecording(r.rec);
        List<Diagnostic> ds = diags.dynamic();
        assertEquals(1, ds.size(), ds.toString());
        assertTrue(ds.get(0).step >= 2, "found after the rewritten step");
        // 새로 시작하면 모두 버린다
        r.rec.restart(r.root, 0);
        diags.onRecording(r.rec);
        assertEquals(List.of(), diags.dynamic().stream().filter(d -> d.step > 0).toList());
    }
}
