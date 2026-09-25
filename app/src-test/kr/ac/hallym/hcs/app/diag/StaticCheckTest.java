/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #25·#26: 정적 진단. 정상 회로에서는 0건이다. */
class StaticCheckTest {
    static final File CIRC = new File(System.getProperty("hcs.circDir"));
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));
    static final File REF_MIPS = new File(System.getProperty("hcs.refMips"));

    @TempDir
    Path tmp;

    /** 회로를 jar 옆에 복사해 연다(원조처럼 jar를 회로 옆에서 찾는다). */
    LogisimFile open(File circ) throws Exception {
        Path dir = Files.createTempDirectory(tmp, "c");
        Files.copy(MIPS_JAR.toPath(), dir.resolve("hcs-mips.jar"), StandardCopyOption.REPLACE_EXISTING);
        Path to = dir.resolve(circ.getName());
        Files.copy(circ.toPath(), to);
        return new Loader(null).openLogisimFile(to.toFile());
    }

    @Test
    void normalCircuitsHaveNoMessages() throws Exception {
        List<File> files = new ArrayList<>();
        for (File f : CIRC.listFiles((d, n) -> n.endsWith(".circ"))) {
            files.add(f);
        }
        files.add(REF_MIPS);
        files.removeIf(f -> f.getName().equals("values.circ")); // 일부러 E·x를 만드는 엔진 회귀 회로(아래)
        assertTrue(files.size() >= 6, files.toString()); // 엔진 회귀 4개 + 데모 + 참조 CPU
        List<String> found = new ArrayList<>();
        for (File f : files) {
            for (Diagnostic d : StaticCheck.run(open(f))) {
                found.add(f.getName() + ": " + d + " — " + d.message());
            }
        }
        assertEquals(new ArrayList<String>(), found);
    }

    /** 엔진 회귀의 values.circ는 일부러 합선(E)과 떠 있는 출력(x)을 만든다: 그 두 원인만 말한다. */
    @Test
    void valuesCircuitReportsItsDeliberateFaults() throws Exception {
        List<String> got = new ArrayList<>();
        for (Diagnostic d : StaticCheck.run(open(new File(CIRC, "values.circ")))) {
            got.add(d.toString());
        }
        assertEquals(List.of("SHORT [main, main › Const #1.out, main › Const #2.out]",
                "TUNNEL_UNPAIRED [main, floating, -]"), got);
    }

    // ---- 고장 회로 모음(#26): 하나씩 원인 한 곳 ----

    LogisimFile fresh() throws Exception {
        return CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
    }

    static List<Diagnostic> only(LogisimFile file) {
        return StaticCheck.run(file);
    }

    static void one(List<Diagnostic> ds, Diagnostic.Kind kind, Object... args) {
        assertEquals(1, ds.size(), ds.toString());
        assertEquals(kind, ds.get(0).kind);
        assertEquals(List.of(args), ds.get(0).args());
        assertTrue(!ds.get(0).message().startsWith("diag."), "message text exists");
        assertTrue(!ds.get(0).components.isEmpty() && ds.get(0).location != null, "click target");
    }

    @Test
    void registerWithoutClock() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component r = b.add("Memory", "Register", 300, 200, "width", "8", "label", "PC");
        b.input("d", 8, 100, 100);
        b.output("q", 8, 500, 100);
        b.tunnel(r, 1, "d");
        b.tunnel(r, 0, "q");
        b.commit();
        one(only(f), Diagnostic.Kind.CLOCK_UNCONNECTED, "main › PC", "Register");
    }

    @Test
    void twoGatesDriveOneWire() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        b.input("a", 1, 100, 100);
        Component g1 = b.add("Gates", "NOT Gate", 300, 200);
        Component g2 = b.add("Gates", "NOT Gate", 300, 300);
        b.tunnel(g1, 1, "a");
        b.tunnel(g2, 1, "a");
        b.tunnel(g1, 0, "y");
        b.tunnel(g2, 0, "y");
        b.output("y", 1, 500, 100);
        b.commit();
        one(only(f), Diagnostic.Kind.SHORT, "main", "main › NOT #1.out", "main › NOT #2.out");
    }

    @Test
    void widthMismatch() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component in = b.add("Wiring", "Pin", 100, 100, "width", "32", "label", "Instr");
        Component out = b.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "width", "5",
                "label", "rs");
        b.wire(in.getLocation(), out.getLocation());
        b.commit();
        List<Diagnostic> ds = only(f);
        one(ds, Diagnostic.Kind.WIDTH_MISMATCH, "main", "main › Instr", 32, "main › rs", 5);
        assertTrue(ds.get(0).message().contains("32") && ds.get(0).message().contains("5"), ds.get(0).message());
    }

    @Test
    void adderInputLeftOpen() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component add = b.add("Arithmetic", "Adder", 300, 200, "width", "8");
        b.input("a", 8, 100, 100);
        b.output("s", 8, 500, 100);
        b.tunnel(add, 0, "a");
        b.tunnel(add, 2, "s");
        b.commit();
        one(only(f), Diagnostic.Kind.INPUT_UNCONNECTED, "main › Add #1", "b");
    }

    @Test
    void gateInputOnADanglingWire() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component and = b.add("Gates", "AND Gate", 300, 200, "inputs", "2");
        b.input("a", 1, 100, 100);
        b.output("y", 1, 500, 100);
        b.tunnel(and, 1, "a");
        b.tunnel(and, 0, "y");
        Location in1 = and.getEnds().get(2).getLocation();
        b.wire(in1, Location.create(in1.getX() - 60, in1.getY())); // 끝이 아무 데도 닿지 않은 선
        b.commit();
        one(only(f), Diagnostic.Kind.INPUT_UNDRIVEN, "main › AND #1.in1");
    }

    @Test
    void misspelledTunnelNamesTheCandidate() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component ctl = b.add("Wiring", "Pin", 100, 100, "label", "ctl");
        b.tunnel(ctl, 0, "RegDst");
        Component mux = b.add("Plexers", "Multiplexer", 300, 300, "width", "5", "enable", "false");
        b.input("x", 5, 100, 200);
        b.input("z", 5, 100, 250);
        b.tunnel(mux, 0, "x");
        b.tunnel(mux, 1, "z");
        b.tunnel(mux, 2, "RegDest");
        b.output("w", 5, 500, 100);
        b.tunnel(mux, 3, "w");
        b.commit();
        one(only(f), Diagnostic.Kind.TUNNEL_UNPAIRED, "main", "RegDest", "RegDst");
    }

    Circuit buffer(LogisimFile f) {
        Circuit sub = new Circuit("buf");
        f.addCircuit(sub);
        CircuitBuilder sb = new CircuitBuilder(f, sub);
        Component g = sb.add("Gates", "Buffer", 300, 200);
        sb.input("A", 1, 100, 100);
        sb.output("Y", 1, 500, 100);
        sb.tunnel(g, 1, "A");
        sb.tunnel(g, 0, "Y");
        sb.commit();
        return sub;
    }

    static int end(Component inst, String port) {
        for (int i = 0; i < inst.getEnds().size(); i++) {
            if (kr.ac.hallym.hcs.app.model.Kinds.portName(inst, i).equals(port)) {
                return i;
            }
        }
        throw new IllegalArgumentException(port);
    }

    @Test
    void subcircuitInputLeftOpen() throws Exception {
        LogisimFile f = fresh();
        Circuit sub = buffer(f);
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component inst = b.addSubcircuit(sub, 300, 300);
        b.commit();
        b = new CircuitBuilder(f, f.getMainCircuit());
        b.tunnel(inst, end(inst, "Y"), "y");
        b.output("y", 1, 600, 100);
        b.commit();
        one(only(f), Diagnostic.Kind.SUBCIRCUIT_PORT_UNCONNECTED, "main › buf #1", "A");
    }

    @Test
    void ringOfGatesIsACombinationalLoop() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component x = b.add("Gates", "XOR Gate", 300, 200, "inputs", "2");
        Component n = b.add("Gates", "NOT Gate", 500, 200);
        b.input("a", 1, 100, 100);
        b.tunnel(x, 1, "a");
        b.tunnel(x, 0, "p");
        b.tunnel(n, 1, "p");
        b.tunnel(n, 0, "q");
        b.tunnel(x, 2, "q");
        b.output("q", 1, 700, 100);
        b.commit();
        one(only(f), Diagnostic.Kind.COMBINATIONAL_LOOP, "main", "XOR #1 → NOT #1");
    }

    /** 서브회로 안을 지나는 루프도 찾는다. 레지스터를 지나는 되먹임은 루프가 아니다. */
    @Test
    void loopThroughASubcircuitButNotThroughARegister() throws Exception {
        LogisimFile f = fresh();
        Circuit sub = buffer(f);
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component inst = b.addSubcircuit(sub, 300, 300);
        b.commit();
        b = new CircuitBuilder(f, f.getMainCircuit());
        Component not = b.add("Gates", "NOT Gate", 600, 300);
        b.tunnel(inst, end(inst, "Y"), "y");
        b.tunnel(not, 1, "y");
        b.tunnel(not, 0, "back");
        b.tunnel(inst, end(inst, "A"), "back");
        b.commit();
        List<Diagnostic> ds = only(f);
        one(ds, Diagnostic.Kind.COMBINATIONAL_LOOP, "main", "buf #1 → NOT #1");

        LogisimFile g = fresh();
        CircuitBuilder rb = new CircuitBuilder(g, g.getMainCircuit());
        Component reg = rb.add("Memory", "Register", 300, 300, "width", "8");
        Component add = rb.add("Arithmetic", "Adder", 500, 300, "width", "8");
        rb.add("Wiring", "Clock", 100, 500);
        rb.tunnel(reg, 0, "q");
        rb.tunnel(add, 0, "q");
        rb.constant("one", 8, 1, 100, 100);
        rb.tunnel(add, 1, "one");
        rb.tunnel(add, 2, "next");
        rb.tunnel(reg, 1, "next");
        rb.tunnel(reg, 2, "clk");
        Component clk = rb.add("Wiring", "Clock", 100, 600);
        rb.tunnel(clk, 0, "clk");
        rb.commit();
        assertEquals(new ArrayList<Diagnostic>(), only(g).stream().filter(d -> d.kind
                == Diagnostic.Kind.COMBINATIONAL_LOOP).collect(java.util.stream.Collectors.toList()));
    }
}
