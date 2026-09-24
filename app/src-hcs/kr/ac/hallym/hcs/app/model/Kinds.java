/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;

/**
 * 부품 종류 등록표(PLAN.md 7.0, #24). 부품 종류별 처리(분류, 상태를 가지는지, 포트 이름, 빠른 속성)를
 * 흩어진 {@code instanceof} 대신 여기 한 곳에 둔다. 키는 부품 팩토리의 저장 이름(.circ의 {@code name})이라
 * UI 언어와 무관하다. 향후 Logisim ↔ Verilog 매핑표(7.5)가 같은 표에 붙는다.
 */
public final class Kinds {
    public enum Category {
        WIRING, GATE, PLEXER, ARITHMETIC, MEMORY, IO, BASE, MIPS, SUBCIRCUIT, OTHER
    }

    /** 포트 이름 규칙: 부품과 포트 번호 → 이름. */
    interface PortNamer {
        String name(Component c, int end);
    }

    /** 한 종류. */
    public static final class Kind {
        final String factory;
        final String shortName;
        final Category category;
        final boolean stateful;
        final PortNamer ports;
        final List<String> quickAttrs;

        Kind(String factory, String shortName, Category category, boolean stateful, PortNamer ports,
                String... quickAttrs) {
            this.factory = factory;
            this.shortName = shortName;
            this.category = category;
            this.stateful = stateful;
            this.ports = ports;
            this.quickAttrs = Collections.unmodifiableList(Arrays.asList(quickAttrs));
        }

        /** 저장 이름. 예: "AND Gate". */
        public String factory() {
            return factory;
        }

        /** 경로·식별자에 쓰는 짧은 이름. 예: "AND", "Reg". 번역하지 않는다. */
        public String shortName() {
            return shortName;
        }

        public Category category() {
            return category;
        }

        /** 클럭으로 값을 기억하는 부품(레지스터, 메모리 등). 연결 탐색은 기본적으로 여기서 멈춘다. */
        public boolean stateful() {
            return stateful;
        }

        /** 빠른 속성 창(11.4)에 먼저 보일 속성의 저장 이름. */
        public List<String> quickAttrs() {
            return quickAttrs;
        }
    }

    private static final Map<String, Kind> BY_FACTORY = new HashMap<>();
    static final Kind SUBCIRCUIT = new Kind("", "", Category.SUBCIRCUIT, false, Kinds::subcircuitPort);

    private Kinds() {
    }

    private static void add(Kind k) {
        BY_FACTORY.put(k.factory, k);
    }

    private static PortNamer fixed(String... names) {
        return (c, i) -> i < names.length ? names[i] : generic(c, i);
    }

    /** 하나뿐인 포트: 라벨이 있으면 라벨, 없으면 기본 이름. */
    private static PortNamer single(String dflt) {
        return (c, i) -> {
            String label = Names.label(c);
            return label != null ? label : dflt;
        };
    }

    /** 게이트: [0] out, [1..] in0, in1, ... */
    private static final PortNamer GATE = (c, i) -> i == 0 ? "out" : "in" + (i - 1);

    static {
        String width = "width";
        String label = "label";
        String facing = "facing";
        for (String g : new String[] {"AND", "OR", "NAND", "NOR", "XOR", "XNOR"}) {
            add(new Kind(g + " Gate", g, Category.GATE, false, GATE, "inputs", width, "size", facing, label));
        }
        add(new Kind("Odd Parity", "Odd", Category.GATE, false, GATE, "inputs", width, facing, label));
        add(new Kind("Even Parity", "Even", Category.GATE, false, GATE, "inputs", width, facing, label));
        add(new Kind("NOT Gate", "NOT", Category.GATE, false, fixed("out", "in"), width, "size", facing, label));
        add(new Kind("Buffer", "Buf", Category.GATE, false, fixed("out", "in"), width, facing, label));
        add(new Kind("Controlled Buffer", "CtrlBuf", Category.GATE, false, fixed("out", "in", "ctrl"), width,
                facing, "control"));
        add(new Kind("Controlled Inverter", "CtrlInv", Category.GATE, false, fixed("out", "in", "ctrl"), width,
                facing, "control"));

        add(new Kind("Pin", "Pin", Category.WIRING, false, single("pin"), label, width, "output", "tristate",
                facing));
        add(new Kind("Probe", "Probe", Category.WIRING, false, single("probe"), "radix", facing, label));
        add(new Kind("Tunnel", "Tunnel", Category.WIRING, false, single("tunnel"), label, width, facing));
        add(new Kind("Splitter", "Split", Category.WIRING, false,
                (c, i) -> i == 0 ? "combined" : "arm" + (i - 1), "fanout", "incoming", facing));
        add(new Kind("Pull Resistor", "Pull", Category.WIRING, false, single("pull"), "pull", facing));
        add(new Kind("Clock", "Clock", Category.WIRING, false, single("clk"), label, "highDuration",
                "lowDuration", facing));
        add(new Kind("Constant", "Const", Category.WIRING, false, fixed("out"), width, "value", facing));
        add(new Kind("Power", "Power", Category.WIRING, false, fixed("out"), width, facing));
        add(new Kind("Ground", "Ground", Category.WIRING, false, fixed("out"), width, facing));
        add(new Kind("Transistor", "Trans", Category.WIRING, false, fixed("out", "in", "gate"), "type", width,
                facing));
        add(new Kind("Transmission Gate", "TGate", Category.WIRING, false, fixed("out", "in", "gate_p", "gate_n"),
                width, facing));
        add(new Kind("Bit Extender", "Ext", Category.WIRING, false, fixed("out", "in", "ext"), "in_width",
                "out_width", "type"));

        add(new Kind("Multiplexer", "Mux", Category.PLEXER, false, Kinds::muxPort, "select", width, "enable",
                facing));
        add(new Kind("Demultiplexer", "Demux", Category.PLEXER, false, Kinds::demuxPort, "select", width,
                "enable", facing));
        add(new Kind("Decoder", "Dec", Category.PLEXER, false, Kinds::decoderPort, "select", "enable", facing));
        add(new Kind("Priority Encoder", "PriEnc", Category.PLEXER, false, Kinds::encoderPort, "select",
                facing));
        add(new Kind("BitSelector", "BitSel", Category.PLEXER, false, fixed("out", "in", "sel"), width, "group",
                facing));

        add(new Kind("Adder", "Add", Category.ARITHMETIC, false, fixed("a", "b", "sum", "cin", "cout"), width));
        add(new Kind("Subtractor", "Sub", Category.ARITHMETIC, false, fixed("a", "b", "diff", "bin", "bout"),
                width));
        add(new Kind("Multiplier", "Mul", Category.ARITHMETIC, false, fixed("a", "b", "prod", "cin", "cout"),
                width));
        add(new Kind("Divider", "Div", Category.ARITHMETIC, false, fixed("lo", "divisor", "quot", "hi", "rem"),
                width));
        add(new Kind("Negator", "Neg", Category.ARITHMETIC, false, fixed("in", "out"), width));
        add(new Kind("Comparator", "Cmp", Category.ARITHMETIC, false, fixed("a", "b", "gt", "eq", "lt"), width,
                "mode"));
        add(new Kind("Shifter", "Shift", Category.ARITHMETIC, false, fixed("data", "dist", "out"), width,
                "shift"));
        add(new Kind("BitAdder", "BitAdd", Category.ARITHMETIC, false, (c, i) -> i == 0 ? "out" : "in" + (i - 1),
                width, "inputs"));
        add(new Kind("BitFinder", "BitFind", Category.ARITHMETIC, false, fixed("present", "index", "in"), width,
                "type"));

        String[] ff = {"clk", "Q", "Qn", "clr", "pre", "en"};
        add(new Kind("D Flip-Flop", "DFF", Category.MEMORY, true, fixed(cat("D", ff)), "trigger", label));
        add(new Kind("T Flip-Flop", "TFF", Category.MEMORY, true, fixed(cat("T", ff)), "trigger", label));
        add(new Kind("J-K Flip-Flop", "JKFF", Category.MEMORY, true, fixed(cat("J", cat("K", ff))), "trigger",
                label));
        add(new Kind("S-R Flip-Flop", "SRFF", Category.MEMORY, true, fixed(cat("S", cat("R", ff))), "trigger",
                label));
        add(new Kind("Register", "Reg", Category.MEMORY, true, fixed("Q", "D", "clk", "clr", "en"), width,
                "trigger", label));
        add(new Kind("Counter", "Ctr", Category.MEMORY, true,
                fixed("Q", "D", "clk", "clr", "load", "count", "carry"), width, "max", "ongoal", "trigger", label));
        add(new Kind("Shift Register", "ShiftReg", Category.MEMORY, true, Kinds::generic, width, "length",
                "parallel", "trigger", label));
        add(new Kind("Random", "Rand", Category.MEMORY, true, fixed("out", "clk", "en", "clr"), width, "seed",
                "trigger", label));
        add(new Kind("RAM", "RAM", Category.MEMORY, true, fixed("D", "A", "sel", "ld", "clr", "clk", "str", "Din"),
                "addrWidth", "dataWidth", "bus"));
        add(new Kind("ROM", "ROM", Category.MEMORY, true, fixed("D", "A", "sel"), "addrWidth", "dataWidth",
                "contents"));

        add(new Kind("Button", "Button", Category.IO, false, single("button"), facing, "color", label));
        add(new Kind("Joystick", "Joystick", Category.IO, false, fixed("x", "y"), "bits", "color"));
        add(new Kind("Keyboard", "Keyboard", Category.IO, true, fixed("clr", "clk", "ren", "avail", "data"),
                "buflen", "trigger"));
        add(new Kind("LED", "LED", Category.IO, false, single("led"), facing, "color", label));
        add(new Kind("7-Segment Display", "Seg7", Category.IO, false,
                fixed("a", "b", "c", "d", "e", "f", "g", "dp"), "color"));
        add(new Kind("Hex Digit Display", "Hex", Category.IO, false, fixed("in", "dp"), "color"));
        add(new Kind("DotMatrix", "Matrix", Category.IO, false, Kinds::generic, "inputtype", "matrixcols",
                "matrixrows"));
        add(new Kind("TTY", "TTY", Category.IO, true, fixed("clr", "clk", "wen", "data"), "rows", "cols",
                "trigger"));

        add(new Kind("Text", "Text", Category.BASE, false, Kinds::generic, "text", "font", "halign"));

        // MIPS 부품: 포트 이름은 lg_imem·lg_dmem·lg_console 모듈 포트와 같다(PLAN.md 7.0)
        add(new Kind("Instruction Memory", "IMem", Category.MIPS, true, fixed("Addr", "Instr"), label));
        String[] mem = {"Addr", "WriteData", "MemWrite", "MemRead", "clk", "ReadData"};
        add(new Kind("Data Memory", "DMem", Category.MIPS, true, fixed(mem), "limit", label));
        add(new Kind("Stack", "Stack", Category.MIPS, true, fixed(mem), "limit", label));
        add(new Kind("Console", "Console", Category.MIPS, true, fixed("Syscall", "V0", "A0", "clk", "Exit"),
                label));
        add(new Kind("Radix Probe", "RProbe", Category.MIPS, false, single("probe"), width, label));
    }

    private static String[] cat(String first, String[] rest) {
        String[] ret = new String[rest.length + 1];
        ret[0] = first;
        System.arraycopy(rest, 0, ret, 1, rest.length);
        return ret;
    }

    /** 등록표에 있는 모든 종류(서브회로 제외). */
    public static List<Kind> all() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(BY_FACTORY.values()));
    }

    public static Kind of(ComponentFactory f) {
        if (f instanceof SubcircuitFactory) {
            return SUBCIRCUIT;
        }
        Kind k = BY_FACTORY.get(f.getName());
        return k != null ? k : new Kind(f.getName(), f.getName(), Category.OTHER, false, Kinds::generic);
    }

    public static Kind of(Component c) {
        return of(c.getFactory());
    }

    /** 포트 이름. 서브회로는 안쪽 핀의 라벨이다. */
    public static String portName(Component c, int end) {
        Kind k = of(c);
        return k.ports.name(c, end);
    }

    /** 등록표에 규칙이 없을 때: 방향별 번호(in0, out0, io0). */
    static String generic(Component c, int end) {
        List<EndData> ends = c.getEnds();
        EndData e = ends.get(end);
        String prefix = e.isInput() && e.isOutput() ? "io" : e.isInput() ? "in" : "out";
        int n = 0;
        for (int i = 0; i < end; i++) {
            EndData o = ends.get(i);
            if (o.isInput() == e.isInput() && o.isOutput() == e.isOutput()) {
                n++;
            }
        }
        return prefix + n;
    }

    private static int selectBits(AttributeSet a) {
        Object v = a.getValue(attr(a, "select"));
        return v instanceof BitWidth ? ((BitWidth) v).getWidth() : 1;
    }

    private static boolean enable(AttributeSet a) {
        Object v = a.getValue(attr(a, "enable"));
        return v == null || Boolean.TRUE.equals(v); // 2.7.1 이전 파일은 인에이블 속성이 없고 늘 있다
    }

    @SuppressWarnings("unchecked")
    private static Attribute<Object> attr(AttributeSet a, String name) {
        Attribute<?> at = a.getAttribute(name);
        return (Attribute<Object>) (at != null ? at : StdAttr.LABEL);
    }

    /** 멀티플렉서: 입력 in0..in(n-1), sel, [en], out. */
    static String muxPort(Component c, int i) {
        int n = 1 << selectBits(c.getAttributeSet());
        if (i < n) {
            return "in" + i;
        }
        if (i == n) {
            return "sel";
        }
        return i == c.getEnds().size() - 1 ? "out" : "en";
    }

    /** 디멀티플렉서: 출력 out0..out(n-1), sel, [en], in. */
    static String demuxPort(Component c, int i) {
        int n = 1 << selectBits(c.getAttributeSet());
        if (i < n) {
            return "out" + i;
        }
        if (i == n) {
            return "sel";
        }
        return i == c.getEnds().size() - 1 ? "in" : "en";
    }

    /** 디코더: 출력 out0..out(n-1), sel, [en]. */
    static String decoderPort(Component c, int i) {
        int n = 1 << selectBits(c.getAttributeSet());
        return i < n ? "out" + i : i == n ? "sel" : "en";
    }

    /** 우선순위 인코더: 입력 in0..in(n-1), out, en_in, en_out, gs. */
    static String encoderPort(Component c, int i) {
        int n = 1 << selectBits(c.getAttributeSet());
        if (i < n) {
            return "in" + i;
        }
        switch (i - n) {
        case 0: return "out";
        case 1: return "en_in";
        case 2: return "en_out";
        default: return "gs";
        }
    }

    /** 서브회로 포트: 안쪽 핀 라벨(없으면 pin + 번호). 원조 SubcircuitFactory의 포트 순서와 같다. */
    static String subcircuitPort(Component c, int end) {
        SubcircuitFactory f = (SubcircuitFactory) c.getFactory();
        Direction facing = c.getAttributeSet().getValue(StdAttr.FACING);
        SortedMap<Location, Instance> pins = f.getSubcircuit().getAppearance().getPortOffsets(
                facing == null ? Direction.EAST : facing);
        int i = 0;
        for (Instance pin : pins.values()) {
            if (i++ == end) {
                String label = pin.getAttributeValue(StdAttr.LABEL);
                return label != null && !label.trim().isEmpty() ? label.trim() : "pin" + end;
            }
        }
        return "pin" + end;
    }
}
