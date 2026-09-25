/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;

import kr.ac.hallym.hcs.app.model.InstancePaths;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.probe.QuickProbe;
import kr.ac.hallym.hcs.app.record.Recording;

/**
 * 사이클 표의 모델(C-02, PLAN.md 5.1). 열 하나가 한 사이클이다: 열 c는 사이클 c가 끝난 뒤(스텝 2c, 상태 표시줄의
 * "Cycle c"와 같다) 회로의 값이다. 머리는 PC와 명령어로, Instruction Memory의 Addr 입력과 Instr 출력이다(PLAN.md
 * 5.3, 따로 지정할 필요가 없다). 명령어 글은 학생이 쓴 .s의 원래 줄이고, .s가 없으면 디스어셈블이다. 줄은 사용자가
 * 고른 신호다. GUI 없이 테스트한다.
 */
public final class CycleModel {
    /** PC·명령어를 읽는 곳: Instruction Memory 하나(최상위에서 인스턴스 경로를 따라). */
    public static final class Cpu {
        public final List<Component> path;
        public final Component imem;
        public final Location pc;
        public final Location instr;

        Cpu(List<Component> path, Component imem) {
            this.path = Collections.unmodifiableList(new ArrayList<>(path));
            this.imem = imem;
            this.pc = imem.getEnd(0).getLocation();
            this.instr = imem.getEnd(1).getLocation();
        }
    }

    /** 표의 줄 하나: 인스턴스 경로의 넷 하나. */
    public static final class Signal {
        public final List<Component> path;
        public final Location at;
        public final String name;
        public final int width;
        /** 버스를 비트로 펼쳐 보인다. */
        public boolean bits;

        public Signal(List<Component> path, Location at, String name, int width) {
            this.path = Collections.unmodifiableList(new ArrayList<>(path));
            this.at = at;
            this.name = name;
            this.width = width;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Signal && ((Signal) o).path.equals(path) && ((Signal) o).at.equals(at);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, at);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final Circuit root;
    private final Recording rec;
    private final Cpu cpu;
    private final ProgramSource source;
    private final List<Signal> signals = new ArrayList<>();

    public CycleModel(Circuit root, Recording rec, Cpu cpu, ProgramSource source) {
        this.root = root;
        this.rec = rec;
        this.cpu = cpu;
        this.source = source == null ? ProgramSource.EMPTY : source;
    }

    public Recording recording() {
        return rec;
    }

    public Cpu cpu() {
        return cpu;
    }

    public ProgramSource source() {
        return source;
    }

    /** 열 c의 스텝. */
    public static int stepOf(int cycle) {
        return 2 * cycle;
    }

    /** 스텝이 속한 열(홀수 스텝은 그 사이클의 하강 전). */
    public static int cycleOf(int step) {
        return Math.floorDiv(step, 2);
    }

    public int firstCycle() {
        return (rec.first() + 1) / 2;
    }

    public int lastCycle() {
        return cycleOf(rec.last());
    }

    /** 보고 있는 열. */
    public int cursorCycle() {
        return cycleOf(rec.cursor());
    }

    public boolean isEmpty() {
        return rec.isEmpty() || lastCycle() < firstCycle();
    }

    public Value pc(int cycle) {
        return cpu == null ? null : rec.value(cpu.path, cpu.pc, stepOf(cycle));
    }

    public Value instruction(int cycle) {
        return cpu == null ? null : rec.value(cpu.path, cpu.instr, stepOf(cycle));
    }

    /** PC 글: 0x00400000, 정해지지 않았으면 빈 글. */
    public String pcText(int cycle) {
        Value v = pc(cycle);
        return v == null || !v.isFullyDefined() ? "" : String.format("0x%08x", v.toIntValue());
    }

    /**
     * 명령어 글: .s의 원래 줄, 없으면 디스어셈블. PC나 명령어가 정해지지 않았으면 빈 글. 한 줄이 워드 여럿으로
     * 바뀐 의사 명령어(la 등)는 같은 줄이 이어서 보인다.
     */
    public String instructionText(int cycle) {
        Value pc = pc(cycle);
        Value in = instruction(cycle);
        if (pc == null || !pc.isFullyDefined()) {
            return "";
        }
        String line = source.line(pc.toIntValue());
        if (line != null) {
            return line;
        }
        if (in == null || !in.isFullyDefined()) {
            return "";
        }
        return MipsText.disassemble(in.toIntValue(), pc.toIntValue(), MipsText.labels(source.labels()));
    }

    public List<Signal> signals() {
        return Collections.unmodifiableList(signals);
    }

    /** 줄을 더한다. 이미 있으면 false. */
    public boolean add(Signal s) {
        if (signals.contains(s)) {
            return false;
        }
        signals.add(s);
        return true;
    }

    public void remove(Signal s) {
        signals.remove(s);
    }

    public Value value(Signal s, int step) {
        return rec.value(s.path, s.at, step);
    }

    /** 열 c에서 값이 앞 열과 다른가. */
    public boolean changed(Signal s, int cycle) {
        if (cycle <= firstCycle()) {
            return false;
        }
        Value a = value(s, stepOf(cycle - 1));
        Value b = value(s, stepOf(cycle));
        return a != null && b != null && !a.equals(b);
    }

    // ---- 찾기 ----

    /** 최상위 회로에서 가장 먼저 만나는 Instruction Memory(최상위 먼저, 그다음 서브회로 안). 없으면 null. */
    public static Cpu findCpu(Circuit root) {
        return findCpu(root, new ArrayList<Component>(), new java.util.HashSet<Circuit>());
    }

    private static Cpu findCpu(Circuit c, List<Component> path, java.util.Set<Circuit> seen) {
        if (!seen.add(c)) {
            return null;
        }
        List<Component> subs = new ArrayList<>();
        for (Component comp : c.getNonWires()) {
            if ("Instruction Memory".equals(comp.getFactory().getName()) && comp.getEnds().size() >= 2) {
                return new Cpu(path, comp);
            }
            if (comp.getFactory() instanceof SubcircuitFactory) {
                subs.add(comp);
            }
        }
        subs.sort(java.util.Comparator.<Component>comparingInt(x -> x.getLocation().getY())
                .thenComparingInt(x -> x.getLocation().getX()));
        for (Component s : subs) {
            path.add(s);
            Cpu found = findCpu(((SubcircuitFactory) s.getFactory()).getSubcircuit(), path, seen);
            path.remove(path.size() - 1);
            if (found != null) {
                return found;
            }
        }
        seen.remove(c);
        return null;
    }

    /** Instruction Memory가 불러온 .s 파일(속성 source는 .circ 파일 기준 상대 경로일 수 있다). 없으면 null. */
    public static File sourceFile(Cpu cpu, File circFile) {
        if (cpu == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Attribute<Object> a = (Attribute<Object>) cpu.imem.getAttributeSet().getAttribute("source");
        Object v = a == null ? null : cpu.imem.getAttributeSet().getValue(a);
        if (v == null || v.toString().isEmpty()) {
            return null;
        }
        File f = new File(v.toString());
        if (!f.isAbsolute() && circFile != null && circFile.getParentFile() != null) {
            f = new File(circFile.getParentFile(), v.toString());
        }
        return f;
    }

    /** 인스턴스 경로 path 안의 선 w를 줄로: 이름은 넷 이름(터널·핀), 없으면 그 넷을 내는 포트. */
    public static Signal signalFor(Circuit root, List<Component> path, Circuit circuit, Wire w) {
        Netlist nl = Netlist.of(circuit);
        return signal(root, path, circuit, nl.netOf(w), w.getEnd0());
    }

    /** 인스턴스 경로 path 안의 자리 at(포트나 선 끝)이 속한 넷을 줄로. 넷이 없으면 null. */
    public static Signal signalFor(Circuit root, List<Component> path, Circuit circuit, Location at) {
        Netlist nl = Netlist.of(circuit);
        for (Netlist.Net n : nl.nets()) {
            for (Netlist.PortRef p : n.ports()) {
                if (p.location().equals(at)) {
                    return signal(root, path, circuit, n, at);
                }
            }
            for (Wire w : n.wires()) {
                if (w.getEnd0().equals(at) || w.getEnd1().equals(at)) {
                    return signal(root, path, circuit, n, at);
                }
            }
        }
        return null;
    }

    private static Signal signal(Circuit root, List<Component> path, Circuit circuit, Netlist.Net net, Location at) {
        String name = QuickProbe.netName(circuit, net);
        if (name.isEmpty() && net != null && !net.drivers().isEmpty()) {
            Netlist.PortRef d = net.drivers().get(0);
            name = Names.portTitle(circuit, d.component, d.end);
        }
        if (name.isEmpty()) {
            name = Names.at(at);
        }
        if (!path.isEmpty()) {
            name = InstancePaths.describe(root, path).substring(root.getName().length() + Names.SEP.length())
                    + Names.SEP + name;
        }
        int width = net != null ? net.width() : circuit.getWidth(at).getWidth();
        // 같은 넷(같은 이름 터널 여럿 등)은 한 줄: 기록 엔진과 같은 대표 자리로 맞춘다
        Location rep = net == null ? at : !net.ports().isEmpty() ? net.ports().get(0).location()
                : !net.wires().isEmpty() ? net.wires().get(0).getEnd0() : at;
        return new Signal(path, rep, name, Math.max(1, width));
    }
}
