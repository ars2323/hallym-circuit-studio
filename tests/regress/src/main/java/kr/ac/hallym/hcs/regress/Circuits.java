/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.regress;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

/**
 * 엔진 회귀용 작은 회로들(tests/circ/). 원조 2.7.1 API로 만들고 원조 2.7.1의 저장 코드로 저장한다.
 *
 * <p>{@code -tty table}은 입력 핀을 0으로 둔 채 클럭만 돌린다. 그래서 회로는 Counter나 Clock으로
 * 스스로 입력을 만들고, {@code halt} 출력 핀이 1이 되면 멈춘다.
 */
final class Circuits {
    interface Body {
        void build(LogisimFile file, CircuitBuilder b);
    }

    private Circuits() {
    }

    static void generateAll(File dir) throws IOException {
        write(dir, "gates", Circuits::gates);
        write(dir, "register", Circuits::register);
        write(dir, "memory", Circuits::memory);
        write(dir, "values", Circuits::values);
        write(dir, "subcircuit", Circuits::subcircuit);
        writeRamImage(new File(dir, "memory.ram"));
        try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(dir, "memory.args")),
                StandardCharsets.UTF_8)) {
            w.write("-load memory.ram\n");
        }
    }

    private static void write(File dir, String name, Body body) throws IOException {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null));
        body.build(file, new CircuitBuilder(file, file.getMainCircuit()));
        CircuitBuilder.save(file, new File(dir, name + ".circ"));
    }

    /** 클럭으로 도는 카운터. 반환값은 Counter 부품이다(0 Q, 2 clk, 3 clr, 4 load, 5 ct, 6 carry). */
    private static Component counter(CircuitBuilder b, int width, int max, String out, int x, int y) {
        Component clock = b.add("Wiring", "Clock", x - 120, y + 20);
        b.tunnel(clock, 0, "clk");
        Component c = b.add("Memory", "Counter", x, y,
                "width", Integer.toString(width), "max", "0x" + Integer.toHexString(max));
        b.tunnel(c, 0, out);
        b.tunnel(c, 2, "clk");
        b.tunnel(c, 3, "zero");
        b.tunnel(c, 4, "zero");
        b.tunnel(c, 5, "one");
        b.tunnel(c, 6, "halt");
        b.constant("zero", 1, 0, x - 200, y + 100);
        b.constant("one", 1, 1, x - 200, y + 140);
        b.output("halt", 1, x + 400, y + 400);
        return c;
    }

    /** 3비트 카운터로 게이트와 MUX의 진리표 8행을 모두 돈다. */
    private static void gates(LogisimFile file, CircuitBuilder b) {
        counter(b, 3, 7, "n", 200, 100);
        Component split = b.add("Wiring", "Splitter", 300, 100,
                "fanout", "3", "incoming", "3", "bit0", "0", "bit1", "1", "bit2", "2");
        b.tunnel(split, 0, "n");
        b.tunnel(split, 1, "a");
        b.tunnel(split, 2, "b");
        b.tunnel(split, 3, "c");
        b.output("n", 3, 900, 60);

        String[][] twoInput = {
            {"AND Gate", "and"}, {"OR Gate", "or"}, {"NAND Gate", "nand"},
            {"NOR Gate", "nor"}, {"XNOR Gate", "xnor"},
        };
        int y = 200;
        for (String[] g : twoInput) {
            Component gate = b.add("Gates", g[0], 500, y, "inputs", "2");
            b.tunnel(gate, 0, g[1]);
            b.tunnel(gate, 1, "a");
            b.tunnel(gate, 2, "b");
            b.output(g[1], 1, 900, y);
            y += 80;
        }
        Component xor3 = b.add("Gates", "XOR Gate", 500, y, "inputs", "3");
        b.tunnel(xor3, 0, "xor3");
        b.tunnel(xor3, 1, "a");
        b.tunnel(xor3, 2, "b");
        b.tunnel(xor3, 3, "c");
        b.output("xor3", 1, 900, y);
        y += 80;
        Component not = b.add("Gates", "NOT Gate", 500, y);
        b.tunnel(not, 0, "notc");
        b.tunnel(not, 1, "c");
        b.output("notc", 1, 900, y);
        y += 80;
        Component mux = b.add("Plexers", "Multiplexer", 500, y, "enable", "false");
        b.tunnel(mux, 0, "a");
        b.tunnel(mux, 1, "b");
        b.tunnel(mux, 2, "c");
        b.tunnel(mux, 3, "mux");
        b.output("mux", 1, 900, y);
        b.commit();
    }

    /** PC처럼 3씩 늘어나는 8비트 레지스터. 30이 되면 멈춘다. */
    private static void register(LogisimFile file, CircuitBuilder b) {
        Component clock = b.add("Wiring", "Clock", 80, 120);
        b.tunnel(clock, 0, "clk");
        Component reg = b.add("Memory", "Register", 300, 100, "width", "8");
        b.tunnel(reg, 0, "q");
        b.tunnel(reg, 1, "next");
        b.tunnel(reg, 2, "clk");
        b.tunnel(reg, 3, "zero");
        b.tunnel(reg, 4, "one");
        b.constant("zero", 1, 0, 80, 200);
        b.constant("one", 1, 1, 80, 240);

        Component adder = b.add("Arithmetic", "Adder", 500, 100, "width", "8");
        b.tunnel(adder, 0, "q");
        b.tunnel(adder, 1, "step");
        b.tunnel(adder, 2, "next");
        b.constant("step", 8, 3, 360, 300);

        Component cmp = b.add("Arithmetic", "Comparator", 500, 400, "width", "8", "mode", "unsigned");
        b.tunnel(cmp, 0, "q");
        b.tunnel(cmp, 1, "limit");
        b.tunnel(cmp, 2, "gt");
        b.tunnel(cmp, 3, "halt");
        b.tunnel(cmp, 4, "lt");
        b.constant("limit", 8, 30, 360, 460);

        b.output("q", 8, 900, 100);
        b.output("next", 8, 900, 180);
        b.output("lt", 1, 900, 260);
        b.output("gt", 1, 900, 340);
        b.output("halt", 1, 900, 420);
        b.commit();
    }

    /**
     * 5비트 카운터의 아래 4비트로 ROM을 읽고, 분리 버스 RAM의 같은 주소에 ROM 값의 반전을 쓴다. 첫 바퀴는
     * RAM 초기값(-load memory.ram)을, 둘째 바퀴는 첫 바퀴에 쓴 값을 읽는다.
     */
    private static void memory(LogisimFile file, CircuitBuilder b) {
        counter(b, 5, 31, "n", 200, 100);
        Component split = b.add("Wiring", "Splitter", 300, 100, "fanout", "2", "incoming", "5",
                "bit0", "0", "bit1", "0", "bit2", "0", "bit3", "0", "bit4", "1");
        b.tunnel(split, 0, "n");
        b.tunnel(split, 1, "addr");
        b.tunnel(split, 2, "pass");
        StringBuilder rom = new StringBuilder("addr/data: 4 8\n");
        for (int i = 0; i < 16; i += 1) {
            rom.append(Integer.toHexString((i * i + 1) & 0xff)).append(i == 15 ? "\n" : " ");
        }
        Component romc = b.add("Memory", "ROM", 600, 300, "addrWidth", "4", "dataWidth", "8",
                "contents", rom.toString());
        b.tunnel(romc, 0, "rom");
        b.tunnel(romc, 1, "addr");
        b.tunnel(romc, 2, "one");

        Component inv = b.add("Gates", "NOT Gate", 700, 500, "width", "8");
        b.tunnel(inv, 0, "din");
        b.tunnel(inv, 1, "rom");

        // 0 data, 1 addr, 2 sel, 3 ld, 4 clr, 5 clk, 6 str, 7 din
        Component ram = b.add("Memory", "RAM", 600, 700, "addrWidth", "4", "dataWidth", "8",
                "bus", "separate");
        b.tunnel(ram, 0, "ram");
        b.tunnel(ram, 1, "addr");
        b.tunnel(ram, 2, "one");
        b.tunnel(ram, 3, "one");
        b.tunnel(ram, 4, "zero");
        b.tunnel(ram, 5, "clk");
        b.tunnel(ram, 6, "one");
        b.tunnel(ram, 7, "din");

        b.output("n", 5, 1000, 100);
        b.output("rom", 8, 1000, 180);
        b.output("ram", 8, 1000, 260);
        b.commit();
    }

    /** 정의되지 않은 값: 합선(E), 떠 있는 출력(X), 떠 있는 게이트 입력(gateUndefined=ignore). */
    private static void values(LogisimFile file, CircuitBuilder b) {
        b.constant("short", 1, 0, 100, 100);
        b.constant("short", 1, 1, 100, 160);
        b.output("short", 1, 600, 100);

        b.output("floating", 1, 600, 180);
        b.add("Wiring", "Tunnel", 400, 180, "label", "nothing"); // 짝 없는 터널

        Component and = b.add("Gates", "AND Gate", 400, 300, "inputs", "2");
        b.tunnel(and, 0, "andfloat");
        b.tunnel(and, 1, "one");
        b.constant("one", 1, 1, 100, 300);
        b.output("andfloat", 1, 600, 300);

        Component split = b.add("Wiring", "Splitter", 300, 450,
                "fanout", "2", "incoming", "4", "bit0", "0", "bit1", "0", "bit2", "1", "bit3", "1");
        b.tunnel(split, 0, "partial");
        b.tunnel(split, 1, "low");
        b.constant("low", 2, 2, 100, 420);
        b.output("partial", 4, 600, 450);

        // 곧은 선으로 이은 상수와 핀(터널이 아닌 선 연결)
        Component k = b.add("Wiring", "Constant", 100, 600, "width", "8", "value", "0xa5");
        Component pin = b.add("Wiring", "Pin", 600, 600,
                "facing", "west", "output", "true", "width", "8", "label", "wired");
        b.wire(CircuitBuilder.port(k, 0), CircuitBuilder.port(pin, 0));

        b.constant("halt", 1, 1, 100, 700);
        b.output("halt", 1, 600, 700);
        b.commit();
    }

    /** 반가산기 서브회로 두 개와 OR로 만든 전가산기. 3비트 카운터로 8가지 입력을 모두 돈다. */
    private static void subcircuit(LogisimFile file, CircuitBuilder b) {
        Circuit ha = new Circuit("half_adder");
        file.addCircuit(ha);
        CircuitBuilder h = new CircuitBuilder(file, ha);
        h.input("a", 1, 100, 100);
        h.input("b", 1, 100, 200);
        Component x = h.add("Gates", "XOR Gate", 400, 100, "inputs", "2");
        h.tunnel(x, 0, "s");
        h.tunnel(x, 1, "a");
        h.tunnel(x, 2, "b");
        Component a = h.add("Gates", "AND Gate", 400, 200, "inputs", "2");
        h.tunnel(a, 0, "c");
        h.tunnel(a, 1, "a");
        h.tunnel(a, 2, "b");
        h.output("s", 1, 600, 100);
        h.output("c", 1, 600, 200);
        h.commit();

        counter(b, 3, 7, "n", 200, 100);
        Component split = b.add("Wiring", "Splitter", 300, 100,
                "fanout", "3", "incoming", "3", "bit0", "0", "bit1", "1", "bit2", "2");
        b.tunnel(split, 0, "n");
        b.tunnel(split, 1, "a");
        b.tunnel(split, 2, "b");
        b.tunnel(split, 3, "cin");

        Component ha1 = b.addSubcircuit(ha, 500, 300);
        connectHalfAdder(b, ha1, "a", "b", "s1", "c1");
        Component ha2 = b.addSubcircuit(ha, 500, 500);
        connectHalfAdder(b, ha2, "s1", "cin", "sum", "c2");
        Component or = b.add("Gates", "OR Gate", 800, 700, "inputs", "2");
        b.tunnel(or, 0, "cout");
        b.tunnel(or, 1, "c1");
        b.tunnel(or, 2, "c2");

        b.output("n", 3, 1000, 100);
        b.output("sum", 1, 1000, 200);
        b.output("cout", 1, 1000, 300);
        b.commit();
    }

    /** 서브회로 포트: 입력은 위에서부터 a, b, 출력은 위에서부터 s, c(핀의 y 순서). */
    private static void connectHalfAdder(CircuitBuilder b, Component ha,
            String inA, String inB, String outS, String outC) {
        java.util.List<Integer> inputs = new java.util.ArrayList<Integer>();
        java.util.List<Integer> outputs = new java.util.ArrayList<Integer>();
        for (int i = 0; i < ha.getEnds().size(); i += 1) {
            (ha.getEnds().get(i).isInput() ? inputs : outputs).add(i);
        }
        java.util.Comparator<Integer> byY = (p, q) ->
                CircuitBuilder.port(ha, p).getY() - CircuitBuilder.port(ha, q).getY();
        inputs.sort(byY);
        outputs.sort(byY);
        b.tunnel(ha, inputs.get(0), inA);
        b.tunnel(ha, inputs.get(1), inB);
        b.tunnel(ha, outputs.get(0), outS);
        b.tunnel(ha, outputs.get(1), outC);
    }

    /** memory 회로의 RAM 초기값(원조 2.7.1 -load 형식). */
    private static void writeRamImage(File dest) throws IOException {
        StringBuilder sb = new StringBuilder("v2.0 raw\n");
        for (int i = 0; i < 16; i += 1) {
            sb.append(Integer.toHexString(0x80 + i)).append(i % 8 == 7 ? "\n" : " ");
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(dest), StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        }
    }
}
