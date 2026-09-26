/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.demo;

import java.util.LinkedHashMap;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.app.diag.Diagnostic;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 고장 회로 모음(D-06, Q-05) tests/circ/faults/의 생성기. 회로마다 "동작하지 않는" 원인이 한 곳 있고, 정적 검사와
 * 몇 사이클 시뮬레이션 뒤 Messages에 기대한 메시지가 한 줄만 나와야 한다. 입력은 3상태 입력 핀 대신 상수로 준다(핀은
 * 처음에 X라 그것도 원인이 된다). 원인을 일부러 둔 곳만 떠 있는 핀을 쓴다.
 */
public final class FaultCircuits {
    /** 한 회로를 만든다. */
    interface Maker {
        void build(LogisimFile file, Library mips) throws Exception;
    }

    /** 이름 → 기대 종류. */
    public static final Map<String, Diagnostic.Kind> EXPECTED = new LinkedHashMap<>();
    static final Map<String, Maker> MAKERS = new LinkedHashMap<>();

    private static void add(String name, Diagnostic.Kind kind, Maker m) {
        EXPECTED.put(name, kind);
        MAKERS.put(name, m);
    }

    private FaultCircuits() {
    }

    public static void build(String name, LogisimFile file, Library mips) throws Exception {
        MAKERS.get(name).build(file, mips);
    }

    /** 이 회로가 lib-mips 부품을 쓰는가. */
    public static boolean usesMips(String name) {
        return name.startsWith("mips-") || name.equals("static-memory-overlap");
    }

    static int port(Component inst, String name) {
        for (int i = 0; i < inst.getEnds().size(); i++) {
            if (kr.ac.hallym.hcs.app.model.Kinds.portName(inst, i).equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException(name);
    }

    static CircuitBuilder main(LogisimFile f) {
        return new CircuitBuilder(f, f.getMainCircuit());
    }

    static Component clock(CircuitBuilder b) {
        Component clk = b.add("Wiring", "Clock", 100, 700);
        b.tunnel(clk, 0, "clk");
        return clk;
    }

    static {
        // ---- 정적(PLAN.md 4.2) ----
        add("static-clock-unconnected", Diagnostic.Kind.CLOCK_UNCONNECTED, (f, mips) -> {
            CircuitBuilder b = main(f);
            Component r = b.add("Memory", "Register", 300, 200, "width", "8", "label", "PC");
            b.constant("d", 8, 4, 100, 100);
            b.tunnel(r, 1, "d");
            b.tunnel(r, 0, "q");
            b.output("q", 8, 500, 100);
            b.commit();
        });
        add("static-short", Diagnostic.Kind.SHORT, (f, mips) -> {
            CircuitBuilder b = main(f);
            b.constant("a", 1, 1, 100, 100);
            Component g1 = b.add("Gates", "NOT Gate", 300, 200);
            Component g2 = b.add("Gates", "Buffer", 300, 300);
            b.tunnel(g1, 1, "a");
            b.tunnel(g2, 1, "a");
            b.tunnel(g1, 0, "y");
            b.tunnel(g2, 0, "y");
            b.output("y", 1, 500, 100);
            b.commit();
        });
        add("static-width-mismatch", Diagnostic.Kind.WIDTH_MISMATCH, (f, mips) -> {
            CircuitBuilder b = main(f);
            Component k = b.add("Wiring", "Constant", 100, 100, "width", "32", "value", "0x1");
            Component out = b.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "width", "5",
                    "label", "rs");
            b.wire(k.getLocation(), out.getLocation());
            b.commit();
        });
        add("static-input-unconnected", Diagnostic.Kind.INPUT_UNCONNECTED, (f, mips) -> {
            CircuitBuilder b = main(f);
            Component add = b.add("Arithmetic", "Adder", 300, 200, "width", "8");
            b.constant("a", 8, 1, 100, 100);
            b.tunnel(add, 0, "a");
            b.tunnel(add, 2, "s");
            b.output("s", 8, 500, 100);
            b.commit();
        });
        add("static-input-undriven", Diagnostic.Kind.INPUT_UNDRIVEN, (f, mips) -> {
            CircuitBuilder b = main(f);
            Component and = b.add("Gates", "AND Gate", 300, 200, "inputs", "2");
            b.constant("a", 1, 1, 100, 100);
            b.tunnel(and, 1, "a");
            b.tunnel(and, 0, "y");
            b.output("y", 1, 500, 100);
            Location in1 = and.getEnds().get(2).getLocation();
            b.wire(in1, Location.create(in1.getX() - 60, in1.getY())); // 끝이 아무 데도 닿지 않은 선
            b.commit();
        });
        add("static-tunnel-unpaired", Diagnostic.Kind.TUNNEL_UNPAIRED, (f, mips) -> {
            CircuitBuilder b = main(f);
            b.constant("RegDst", 1, 0, 100, 100);
            Component mux = b.add("Plexers", "Multiplexer", 300, 300, "width", "5", "enable", "false");
            b.constant("x", 5, 1, 100, 200);
            b.constant("z", 5, 2, 100, 250);
            b.tunnel(mux, 0, "x");
            b.tunnel(mux, 1, "z");
            b.tunnel(mux, 2, "RegDest");
            b.tunnel(mux, 3, "w");
            b.output("w", 5, 500, 100);
            b.commit();
        });
        add("static-subcircuit-port-unconnected", Diagnostic.Kind.SUBCIRCUIT_PORT_UNCONNECTED, (f, mips) -> {
            Circuit sub = new Circuit("buf");
            f.addCircuit(sub);
            CircuitBuilder sb = new CircuitBuilder(f, sub);
            Component g = sb.add("Gates", "Buffer", 300, 200);
            Component a = sb.add("Wiring", "Pin", 100, 100, "tristate", "false", "label", "A");
            sb.tunnel(a, 0, "A");
            sb.tunnel(g, 1, "A");
            sb.tunnel(g, 0, "Y");
            sb.output("Y", 1, 500, 100);
            sb.commit();
            CircuitBuilder b = main(f);
            Component inst = b.addSubcircuit(sub, 300, 300);
            b.commit();
            b = main(f);
            b.tunnel(inst, port(inst, "Y"), "y");
            b.output("y", 1, 600, 100);
            b.commit();
        });
        add("static-combinational-loop", Diagnostic.Kind.COMBINATIONAL_LOOP, (f, mips) -> {
            // 멈추는 고리: AND(0, NOT p) = 0. 진동하지 않는다
            CircuitBuilder b = main(f);
            Component and = b.add("Gates", "AND Gate", 300, 200, "inputs", "2");
            Component not = b.add("Gates", "NOT Gate", 500, 200);
            b.constant("zero", 1, 0, 100, 100);
            b.tunnel(and, 1, "zero");
            b.tunnel(and, 0, "p");
            b.tunnel(not, 1, "p");
            b.tunnel(not, 0, "q");
            b.tunnel(and, 2, "q");
            b.output("q", 1, 700, 100);
            b.commit();
        });
        add("static-memory-overlap", Diagnostic.Kind.MEMORY_OVERLAP, (f, mips) -> {
            CircuitBuilder b = main(f);
            clock(b);
            b.constant("addr", 32, 0x10010000, 80, 100);
            b.constant("zero", 1, 0, 80, 240);
            Component data = b.add(mips, "Data Memory", 600, 200);
            Component stack = b.add(mips, "Stack", 600, 500, "top", "0x1001003c");
            for (Component m : new Component[] {data, stack}) {
                b.tunnel(m, 0, "addr");
                b.tunnel(m, 1, "addr");
                b.tunnel(m, 2, "zero");
                b.tunnel(m, 3, "zero");
                b.tunnel(m, 4, "clk");
            }
            b.commit();
        });

        // ---- 동적(PLAN.md 4.3, D-01·D-02·D-03) ----
        add("dynamic-e-conflict", Diagnostic.Kind.E_APPEARED, (f, mips) -> {
            // 3상태 버퍼는 떠 있을 수 있어 정적 검사는 말하지 않는다. 클럭이 1이면 버퍼가 켜져 상수 1과 부딪힌다
            CircuitBuilder b = main(f);
            clock(b);
            Component buf = b.add("Gates", "Controlled Buffer", 300, 200);
            b.constant("zero", 1, 0, 100, 100);
            b.tunnel(buf, 1, "zero");
            b.tunnel(buf, 2, "clk");
            b.tunnel(buf, 0, "w");
            b.constant("w", 1, 1, 100, 300);
            Component not = b.add("Gates", "NOT Gate", 500, 300);
            b.tunnel(not, 1, "w");
            b.tunnel(not, 0, "y");
            b.output("y", 1, 700, 100);
            b.commit();
        });
        add("dynamic-e-undefined-input", Diagnostic.Kind.E_APPEARED, (f, mips) -> {
            // V-02 (b): 정해지지 않은 입력 핀(RegWrite)이 AND를 거쳐 E가 된다. 충돌이 아니다
            CircuitBuilder b = main(f);
            clock(b);
            Component and = b.add("Gates", "AND Gate", 400, 200);
            b.input("RegWrite", 1, 100, 100);
            b.tunnel(and, 1, "RegWrite");
            b.tunnel(and, 2, "clk");
            b.tunnel(and, 0, "we");
            b.output("we", 1, 700, 100);
            b.commit();
        });
        add("dynamic-x-write-control", Diagnostic.Kind.X_WRITE_CONTROL, (f, mips) -> {
            // RegWrite 입력 핀을 3상태로 두어 값이 정해지지 않음(PLAN.md 4.4 예)
            CircuitBuilder b = main(f);
            clock(b);
            Component reg = b.add("Memory", "Register", 400, 200, "width", "8", "label", "R1");
            b.constant("d", 8, 7, 100, 100);
            b.tunnel(reg, 1, "d");
            b.tunnel(reg, 2, "clk");
            b.tunnel(reg, 4, "RegWrite");
            b.input("RegWrite", 1, 100, 300);
            b.tunnel(reg, 0, "q");
            b.output("q", 8, 700, 100);
            b.commit();
        });
        add("dynamic-x-write-data", Diagnostic.Kind.X_WRITE_DATA, (f, mips) -> {
            CircuitBuilder b = main(f);
            clock(b);
            Component reg = b.add("Memory", "Register", 400, 200, "width", "8", "label", "PC");
            b.tunnel(reg, 1, "next");
            b.input("next", 8, 100, 100); // 3상태 입력 핀: X
            b.tunnel(reg, 2, "clk");
            b.tunnel(reg, 0, "q");
            b.output("q", 8, 700, 100);
            b.commit();
        });
        add("dynamic-oscillation", Diagnostic.Kind.OSCILLATION, (f, mips) -> {
            // NAND(clk, q) → q: 클럭이 0이면 q = 1로 멈추고, 1이 되면 값이 멈추지 않는다
            CircuitBuilder b = main(f);
            clock(b);
            Component nand = b.add("Gates", "NAND Gate", 400, 200, "inputs", "2");
            b.tunnel(nand, 1, "clk");
            b.tunnel(nand, 2, "q");
            b.tunnel(nand, 0, "q");
            b.output("q", 1, 700, 100);
            b.commit();
        });

        // ---- MIPS 부품 값(D-04, #41). 문구는 몸체의 빨간 글자 ----
        add("mips-unaligned", Diagnostic.Kind.MIPS_STATUS, (f, mips) -> memory(f, mips, "Data Memory", 0x10010002));
        add("mips-no-region", Diagnostic.Kind.MIPS_STATUS, (f, mips) -> memory(f, mips, "Data Memory", 0x20000000));
        add("mips-stack-limit", Diagnostic.Kind.MIPS_STATUS, (f, mips) -> memory(f, mips, "Stack", 0x7FFFFFEC,
                "size", "0x10"));
        add("mips-imem-unaligned", Diagnostic.Kind.MIPS_STATUS, (f, mips) -> {
            CircuitBuilder b = main(f);
            Component im = b.add(mips, "Instruction Memory", 400, 200);
            b.constant("pc", 32, 0x00400002, 100, 100);
            b.tunnel(im, 0, "pc");
            b.tunnel(im, 1, "instr");
            b.output("instr", 32, 700, 100);
            b.commit();
        });
        add("mips-console-syscall", Diagnostic.Kind.MIPS_STATUS, (f, mips) -> {
            CircuitBuilder b = main(f);
            clock(b);
            Component con = b.add(mips, "Console", 500, 300);
            b.constant("one", 1, 1, 100, 100);
            b.constant("v0", 32, 99, 100, 200); // 지원하지 않는 syscall
            b.constant("a0", 32, 0, 100, 300);
            b.tunnel(con, 0, "one");
            b.tunnel(con, 1, "v0");
            b.tunnel(con, 2, "a0");
            b.tunnel(con, 3, "clk");
            b.commit();
        });
    }

    /** 한 주소를 읽는 메모리 하나(쓰기 없음). */
    static void memory(LogisimFile f, Library mips, String kind, int addr, String... attrs) {
        CircuitBuilder b = main(f);
        clock(b);
        b.constant("addr", 32, addr, 80, 100);
        b.constant("one", 1, 1, 80, 200);
        b.constant("zero", 1, 0, 80, 240);
        Component m = b.add(mips, kind, 600, 300, attrs);
        b.tunnel(m, 0, "addr");
        b.tunnel(m, 1, "addr");
        b.tunnel(m, 2, "zero");
        b.tunnel(m, 3, "one");
        b.tunnel(m, 4, "clk");
        b.tunnel(m, 5, "data");
        b.output("data", 32, 900, 100);
        b.commit();
    }
}
