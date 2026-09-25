/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.circuit.WidthIncompatibilityData;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;

import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;

/**
 * 정적 진단(PLAN.md 4.1·4.2, #25). 시뮬레이션 없이 회로 모델의 연결 구조만 보고 "동작할 수 없는 회로"를 찾는다. 동작은
 * 하는데 결과만 틀린 회로는 판단하지 않는다(1장 설계 원칙). 정상 회로에서는 메시지가 0건이어야 한다(#26).
 * <p>
 * 검사는 회로 정의마다 한 번이다(인스턴스가 여럿이어도 원인은 한 곳). 값이 있는지는 비트 단위로 본다: 스플리터가 잇는
 * 비트끼리 한 마디로 묶고, 값을 내는 포트가 닿은 마디를 "값 있음"으로 한다.
 */
public final class StaticCheck {
    /** 한 줄로 읽을 수 없는 부품: 값을 내지 않거나(터널·프로브) 방향이 없는(스플리터) 것. */
    private static final Set<String> PASSIVE = new HashSet<>(Arrays.asList("Tunnel", "Probe", "Radix Probe",
            "Splitter", "Text", "Pull Resistor"));
    /** 출력이 떠 있을 수 있는 부품: 한 선에 여럿 이어도 합선이 아니다(PLAN.md 6.2: Data·Stack ReadData 공유). */
    private static final Set<String> MAY_FLOAT = new HashSet<>(Arrays.asList("Controlled Buffer",
            "Controlled Inverter", "RAM", "ROM", "Transistor", "Transmission Gate", "Instruction Memory",
            "Data Memory", "Stack"));
    /** 비어 있으면 동작할 수 없는 입력(포트 이름). 등록표 Kinds의 포트 이름을 쓴다. */
    private static final Map<String, Set<String>> REQUIRED = new HashMap<>();
    /** 클럭 입력이 비면 값이 바뀌지 않는 부품. */
    private static final Set<String> CLOCKED = new HashSet<>(Arrays.asList("Register", "Counter", "D Flip-Flop",
            "T Flip-Flop", "J-K Flip-Flop", "S-R Flip-Flop", "Random", "Data Memory", "Stack", "Console"));

    static {
        req("Register", "D");
        req("D Flip-Flop", "D");
        req("T Flip-Flop", "T");
        req("Adder", "a", "b");
        req("Subtractor", "a", "b");
        req("Multiplier", "a", "b");
        req("Divider", "lo", "divisor");
        req("Negator", "in");
        req("Comparator", "a", "b");
        req("Shifter", "data", "dist");
        req("Bit Extender", "in");
        req("NOT Gate", "in");
        req("Buffer", "in");
        req("Multiplexer", "sel");
        req("Demultiplexer", "sel", "in");
        req("Decoder", "sel");
        req("BitSelector", "in", "sel");
        req("Instruction Memory", "Addr");
        // MIPS 부품(PLAN.md 4.2·6.2, #28): 떠 있는 제어 입력은 쓰기·읽기·syscall을 하지 않는다
        req("Data Memory", "Addr", "MemWrite", "MemRead");
        req("Stack", "Addr", "MemWrite", "MemRead");
        req("Console", "Syscall");
    }

    private static void req(String factory, String... ports) {
        REQUIRED.put(factory, new HashSet<>(Arrays.asList(ports)));
    }

    private StaticCheck() {
    }

    /** 파일의 모든 회로. 순서: 회로 순서, 그 안에서 검사 종류 순서. */
    public static List<Diagnostic> run(LogisimFile file) {
        List<Diagnostic> ret = new ArrayList<>();
        Map<Circuit, boolean[][]> summaries = new IdentityHashMap<>();
        for (Circuit c : file.getCircuits()) {
            ret.addAll(new One(c, summaries, gateUndefinedIsError(file)).run());
        }
        ret.addAll(memoryOverlaps(file));
        return ret;
    }

    /**
     * Data Memory·Stack 영역 겹침(PLAN.md 4.2·6.2, #28). 두 부품은 한 주소 선을 나눠 쓰고 주소가 속한 쪽만 답하므로,
     * 영역이 겹치면 어느 쪽이 답할지 정할 수 없다. 영역은 속성만으로 정해진다(Data: [base, base+size), Stack:
     * [top+4−size, top+4)).
     */
    static List<Diagnostic> memoryOverlaps(LogisimFile file) {
        List<Component> mems = new ArrayList<>();
        Map<Component, Circuit> where = new IdentityHashMap<>();
        for (Circuit c : file.getCircuits()) {
            for (Component x : sorted(c.getNonWires())) {
                String f = x.getFactory().getName();
                if ((f.equals("Data Memory") || f.equals("Stack")) && region(x) != null) {
                    mems.add(x);
                    where.put(x, c);
                }
            }
        }
        List<Diagnostic> ret = new ArrayList<>();
        for (int i = 0; i < mems.size(); i++) {
            for (int j = i + 1; j < mems.size(); j++) {
                long[] a = region(mems.get(i));
                long[] b = region(mems.get(j));
                long lo = Math.max(a[0], b[0]);
                long hi = Math.min(a[1], b[1]);
                if (lo < hi) {
                    Component x = mems.get(i);
                    Component y = mems.get(j);
                    Circuit cx = where.get(x);
                    Circuit cy = where.get(y);
                    ret.add(new Diagnostic(Diagnostic.Kind.MEMORY_OVERLAP, cx, Arrays.asList(x, y),
                            Collections.emptyList(), x.getLocation(),
                            Names.path(cx.getName(), Names.name(cx, x)), Names.path(cy.getName(), Names.name(cy, y)),
                            String.format("%08x-%08x", lo, hi - 1)));
                }
            }
        }
        return ret;
    }

    /** 속성으로 정한 영역 [low, high). 속성이 없으면 null. */
    static long[] region(Component c) {
        Object size = One.attr(c, "size");
        Object top = One.attr(c, "top");
        Object base = One.attr(c, "base");
        if (!(size instanceof Integer)) {
            return null;
        }
        long s = (Integer) size & 0xffffffffL;
        if (top instanceof Integer) {
            long high = ((Integer) top & 0xffffffffL) + 4;
            return new long[] {Math.max(0, high - s), high};
        }
        if (base instanceof Integer) {
            long low = (Integer) base & 0xffffffffL;
            return new long[] {low, Math.min(low + s, 0x100000000L)};
        }
        return null;
    }

    /** 프로젝트 옵션(Project › Options › Simulation "Gate Output When Undefined")이 error인가. 기본은 ignore. */
    static boolean gateUndefinedIsError(LogisimFile file) {
        return file.getOptions() != null && com.cburch.logisim.file.Options.GATE_UNDEFINED_ERROR.equals(
                file.getOptions().getAttributeSet().getValue(com.cburch.logisim.file.Options.ATTR_GATE_UNDEFINED));
    }

    /** 회로 하나(옵션은 기본 ignore로 본다). */
    public static List<Diagnostic> run(Circuit circuit) {
        return new One(circuit, new IdentityHashMap<>(), false).run();
    }

    /** 한 회로의 검사. */
    private static final class One {
        final Circuit circuit;
        final Netlist nl;
        final Map<Circuit, boolean[][]> summaries;
        /** 프로젝트 옵션 gateUndefined = error: 게이트의 빈 입력이 출력을 E로 만든다(검토 반영, D-052). */
        final boolean gateError;
        /** 넷 id → 첫 비트 마디 번호. */
        final int[] base;
        final int[] parent;
        final boolean[] driven;
        final Set<Netlist.Net> loneTunnelNets = new HashSet<>();
        final List<Diagnostic> out = new ArrayList<>();
        /** 이미 말한 포트(원인 한 곳만). */
        final Set<Netlist.PortRef> told = new HashSet<>();

        One(Circuit circuit, Map<Circuit, boolean[][]> summaries, boolean gateError) {
            this.circuit = circuit;
            this.summaries = summaries;
            this.gateError = gateError;
            this.nl = Netlist.of(circuit);
            base = new int[nl.nets().size() + 1];
            for (Netlist.Net n : nl.nets()) {
                base[n.id() + 1] = base[n.id()] + Math.max(1, n.width());
            }
            int nodes = base[nl.nets().size()];
            parent = new int[nodes];
            for (int i = 0; i < nodes; i++) {
                parent[i] = i;
            }
            for (Netlist.BitLink l : nl.bitLinks()) {
                if (l.combined != null && l.arm != null && l.bit < width(l.combined)
                        && l.armBit < width(l.arm)) {
                    union(node(l.combined, l.bit), node(l.arm, l.armBit));
                }
            }
            driven = new boolean[nodes];
            for (Netlist.Net n : nl.nets()) {
                for (Netlist.PortRef p : n.ports()) {
                    if (drives(p)) {
                        for (int b = 0; b < Math.min(p.width(), width(n)); b++) {
                            driven[find(node(n, b))] = true;
                        }
                    }
                }
            }
        }

        int width(Netlist.Net n) {
            return base[n.id() + 1] - base[n.id()];
        }

        int node(Netlist.Net n, int bit) {
            return base[n.id()] + bit;
        }

        int find(int x) {
            while (parent[x] != x) {
                parent[x] = parent[parent[x]];
                x = parent[x];
            }
            return x;
        }

        void union(int a, int b) {
            parent[find(a)] = find(b);
        }

        /** 넷에 값을 내는 포트: 출력 전용 포트, 풀 저항, 방향 없는 포트(스플리터·터널 제외). */
        static boolean drives(Netlist.PortRef p) {
            String f = p.component.getFactory().getName();
            if (f.equals("Pull Resistor")) {
                return true;
            }
            EndData d = p.data();
            if (d.isOutput() && !d.isInput()) {
                return true;
            }
            return d.isOutput() && d.isInput() && !f.equals("Splitter") && !f.equals("Tunnel");
        }

        /** 포트의 비트가 하나도 값을 받지 못하는가. */
        boolean undriven(Netlist.PortRef p) {
            Netlist.Net n = nl.netOf(p.component, p.end);
            if (n == null) {
                return true;
            }
            for (int b = 0; b < Math.min(Math.max(1, p.width()), width(n)); b++) {
                if (driven[find(node(n, b))]) {
                    return false;
                }
            }
            return true;
        }

        String name(Component c) {
            return Names.path(circuit.getName(), Names.name(circuit, c));
        }

        String port(Netlist.PortRef p) {
            return Names.path(circuit.getName(), Names.port(circuit, p.component, p.end));
        }

        int endNamed(Component c, String port) {
            for (int i = 0; i < c.getEnds().size(); i++) {
                if (Kinds.portName(c, i).equals(port)) {
                    return i;
                }
            }
            return -1;
        }

        boolean inLoneTunnelNet(Netlist.PortRef p) {
            return loneTunnelNets.contains(nl.netOf(p.component, p.end));
        }

        void add(Diagnostic.Kind kind, List<Component> comps, List<Wire> wires, Location at, Object... args) {
            out.add(new Diagnostic(kind, circuit, comps, wires, at, args));
        }

        List<Diagnostic> run() {
            tunnels(); // 먼저: 짝 없는 터널이 원인인 입력은 터널 하나로만 말한다
            clocks();
            shorts();
            widths();
            inputs();
            undrivenNets();
            loops();
            Collections.sort(out, Comparator.comparingInt(d -> d.kind.ordinal()));
            return out;
        }

        // ---- 짝 없는 터널 ----

        void tunnels() {
            Map<String, List<Component>> byLabel = new LinkedHashMap<>();
            for (Component c : sorted(circuit.getNonWires())) {
                if (c.getFactory().getName().equals("Tunnel") && Names.label(c) != null) {
                    byLabel.computeIfAbsent(Names.label(c), k -> new ArrayList<>()).add(c);
                }
            }
            for (Map.Entry<String, List<Component>> e : byLabel.entrySet()) {
                if (e.getValue().size() != 1) {
                    continue;
                }
                Component t = e.getValue().get(0);
                // 받는 쪽만 말한다: 값이 없는 넷에서 입력으로 이어지는 터널. 보내기만 하는 터널(값 있음)이나 이름만
                // 붙인 터널(읽는 입력 없음)은 동작을 막지 않는다. 이름이 틀린 짝은 받는 쪽 메시지의 비슷한 이름에 나온다
                Netlist.PortRef tp = new Netlist.PortRef(t, 0);
                Netlist.Net tn = nl.netOf(t, 0);
                boolean feeds = false;
                if (tn != null) {
                    for (Netlist.PortRef r : tn.readers()) {
                        feeds |= !PASSIVE.contains(r.component.getFactory().getName());
                    }
                }
                if (!undriven(tp) || !feeds) {
                    continue;
                }
                List<String> similar = new ArrayList<>();
                for (String other : byLabel.keySet()) {
                    if (!other.equals(e.getKey()) && similar(e.getKey(), other)) {
                        similar.add(other);
                    }
                }
                loneTunnelNets.add(nl.netOf(t, 0));
                add(Diagnostic.Kind.TUNNEL_UNPAIRED, Collections.singletonList(t), Collections.emptyList(),
                        t.getLocation(), circuit.getName(), e.getKey(),
                        similar.isEmpty() ? "-" : String.join(", ", similar));
            }
        }

        /** 비슷한 이름: 대소문자만 다르거나 편집 거리 2 이하(짧은 이름은 1). */
        static boolean similar(String a, String b) {
            if (a.equalsIgnoreCase(b)) {
                return true;
            }
            int limit = Math.min(a.length(), b.length()) <= 3 ? 1 : 2;
            return distance(a.toLowerCase(), b.toLowerCase()) <= limit;
        }

        static int distance(String a, String b) {
            int[] prev = new int[b.length() + 1];
            int[] cur = new int[b.length() + 1];
            for (int j = 0; j <= b.length(); j++) {
                prev[j] = j;
            }
            for (int i = 1; i <= a.length(); i++) {
                cur[0] = i;
                for (int j = 1; j <= b.length(); j++) {
                    int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                    cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                }
                int[] t = prev;
                prev = cur;
                cur = t;
            }
            return prev[b.length()];
        }

        // ---- 클럭 ----

        void clocks() {
            for (Component c : sorted(circuit.getNonWires())) {
                String f = c.getFactory().getName();
                if (!CLOCKED.contains(f)) {
                    continue;
                }
                int clk = endNamed(c, "clk");
                if (clk < 0) {
                    continue;
                }
                // 메모리는 쓰기가 있을 때만, Console은 syscall이 있을 때만 클럭이 필요하다
                if ((f.equals("Data Memory") || f.equals("Stack")) && !used(c, "MemWrite")
                        || f.equals("Console") && !used(c, "Syscall")) {
                    continue;
                }
                Netlist.PortRef p = new Netlist.PortRef(c, clk);
                if (undriven(p) && !inLoneTunnelNet(p)) {
                    told.add(p);
                    add(Diagnostic.Kind.CLOCK_UNCONNECTED, Collections.singletonList(c), Collections.emptyList(),
                            p.location(), name(c), Kinds.of(c).factory());
                }
            }
        }

        boolean used(Component c, String port) {
            int i = endNamed(c, port);
            return i >= 0 && !undriven(new Netlist.PortRef(c, i));
        }

        // ---- 합선 ----

        void shorts() {
            for (Netlist.Net n : nl.nets()) {
                List<Netlist.PortRef> hard = new ArrayList<>();
                for (Netlist.PortRef p : n.drivers()) {
                    if (!mayFloat(p.component)) {
                        hard.add(p);
                    }
                }
                if (hard.size() >= 2) {
                    hard.sort(Comparator.comparing(p -> p.location()));
                    Netlist.PortRef a = hard.get(0);
                    Netlist.PortRef b = hard.get(1);
                    add(Diagnostic.Kind.SHORT, Arrays.asList(a.component, b.component), n.wires(), a.location(),
                            circuit.getName(), port(a), port(b));
                }
            }
        }

        static boolean mayFloat(Component c) {
            String f = c.getFactory().getName();
            if (MAY_FLOAT.contains(f)) {
                return true;
            }
            if (Boolean.TRUE.equals(attr(c, "tristate"))) {
                return true; // 3상태 핀·디먹스·디코더
            }
            return Boolean.TRUE.equals(attr(c, "enable")) && Kinds.of(c).category() == Kinds.Category.PLEXER;
        }

        static Object attr(Component c, String name) {
            Attribute<?> a = c.getAttributeSet().getAttribute(name);
            return a == null ? null : c.getAttributeSet().getValue(a);
        }

        // ---- 비트 폭 불일치(원조 계산 그대로) ----

        void widths() {
            Set<WidthIncompatibilityData> all = circuit.getWidthIncompatibilityData();
            if (all == null) {
                return; // 불일치 없음(원조는 이때 null)
            }
            for (WidthIncompatibilityData d : all) {
                List<Netlist.PortRef> at = new ArrayList<>();
                List<Integer> w = new ArrayList<>();
                for (int i = 0; i < d.size(); i++) {
                    Location p = d.getPoint(i);
                    int width = d.getBitWidth(i).getWidth();
                    for (Component c : circuit.getNonWires()) {
                        for (int e = 0; e < c.getEnds().size(); e++) {
                            if (c.getEnd(e).getLocation().equals(p) && c.getEnd(e).getWidth().getWidth() == width
                                    && !w.contains(width)) {
                                at.add(new Netlist.PortRef(c, e));
                                w.add(width);
                            }
                        }
                    }
                }
                if (at.size() < 2) {
                    continue;
                }
                at.sort(Comparator.comparing(p -> p.location()));
                Netlist.PortRef a = at.get(0);
                Netlist.PortRef b = at.get(1);
                Netlist.Net n = nl.netOf(a.component, a.end);
                add(Diagnostic.Kind.WIDTH_MISMATCH, Arrays.asList(a.component, b.component),
                        n == null ? Collections.emptyList() : n.wires(), a.location(), circuit.getName(), port(a),
                        a.width(), port(b), b.width());
            }
        }

        // ---- 입력 ----

        void inputs() {
            for (Component c : sorted(circuit.getNonWires())) {
                if (c.getFactory() instanceof SubcircuitFactory) {
                    for (int i = 0; i < c.getEnds().size(); i++) {
                        EndData d = c.getEnd(i);
                        Netlist.PortRef p = new Netlist.PortRef(c, i);
                        if (d.isInput() && !d.isOutput() && undriven(p) && !inLoneTunnelNet(p) && told.add(p)) {
                            add(Diagnostic.Kind.SUBCIRCUIT_PORT_UNCONNECTED, Collections.singletonList(c),
                                    Collections.emptyList(), p.location(), name(c), Kinds.portName(c, i));
                        }
                    }
                    continue;
                }
                String f = c.getFactory().getName();
                Set<String> req = REQUIRED.get(f);
                if (req != null) {
                    for (int i = 0; i < c.getEnds().size(); i++) {
                        Netlist.PortRef p = new Netlist.PortRef(c, i);
                        if (req.contains(Kinds.portName(c, i)) && undriven(p) && !inLoneTunnelNet(p)
                                && told.add(p)) {
                            add(Diagnostic.Kind.INPUT_UNCONNECTED, Collections.singletonList(c),
                                    Collections.emptyList(), p.location(), name(c), Kinds.portName(c, i));
                        }
                    }
                } else if (Kinds.of(c).category() == Kinds.Category.GATE) {
                    gate(c);
                }
            }
        }

        /** 입력이 하나도 없는데 출력은 쓰이는 게이트(쓰지 않는 입력은 원조가 무시하므로 알리지 않는다). */
        void gate(Component c) {
            if (gateError) {
                gateAllInputs(c);
                return;
            }
            boolean any = false;
            int first = -1;
            for (int i = 0; i < c.getEnds().size(); i++) {
                EndData d = c.getEnd(i);
                if (d.isInput() && !d.isOutput()) {
                    first = first < 0 ? i : first;
                    any |= !undriven(new Netlist.PortRef(c, i));
                }
            }
            if (any || first < 0) {
                return;
            }
            Netlist.Net outNet = nl.netOf(c, 0);
            if (outNet == null || outNet.ports().size() < 2 && outNet.wires().isEmpty()) {
                return; // 놓아만 둔 게이트
            }
            Netlist.PortRef p = new Netlist.PortRef(c, first);
            if (!inLoneTunnelNet(p) && told.add(p)) {
                add(Diagnostic.Kind.INPUT_UNCONNECTED, Collections.singletonList(c), Collections.emptyList(),
                        p.location(), name(c), Kinds.portName(c, first));
            }
        }

        /**
         * gateUndefined = error: 원조는 게이트의 빈 입력 하나만 있어도 출력을 E로 낸다. 그래서 출력이 쓰이는 게이트의 빈
         * 입력을 모두 한 메시지로 알린다(원인 한 곳 = 그 게이트).
         */
        void gateAllInputs(Component c) {
            Netlist.Net outNet = nl.netOf(c, 0);
            if (outNet == null || outNet.ports().size() < 2 && outNet.wires().isEmpty()) {
                return; // 놓아만 둔 게이트
            }
            List<String> ports = new ArrayList<>();
            Location at = null;
            for (int i = 0; i < c.getEnds().size(); i++) {
                EndData d = c.getEnd(i);
                Netlist.PortRef p = new Netlist.PortRef(c, i);
                if (d.isInput() && !d.isOutput() && undriven(p) && !inLoneTunnelNet(p) && told.add(p)) {
                    ports.add(Kinds.portName(c, i));
                    at = at == null ? p.location() : at;
                }
            }
            if (!ports.isEmpty()) {
                add(Diagnostic.Kind.INPUT_UNCONNECTED, Collections.singletonList(c), Collections.emptyList(), at,
                        name(c), String.join(", ", ports));
            }
        }

        /** 입력이 이어진 선에 값을 내는 것이 없다(끊긴 선). 포트만 덩그러니 있는 것은 뺀다. */
        void undrivenNets() {
            for (Netlist.Net n : nl.nets()) {
                if (loneTunnelNets.contains(n)) {
                    continue;
                }
                List<Netlist.PortRef> readers = new ArrayList<>();
                boolean anyDriven = false;
                for (int b = 0; b < width(n); b++) {
                    anyDriven |= driven[find(node(n, b))];
                }
                if (anyDriven) {
                    continue;
                }
                for (Netlist.PortRef p : n.readers()) {
                    if (!PASSIVE.contains(p.component.getFactory().getName()) && !told.contains(p)) {
                        readers.add(p);
                    }
                }
                // 선이나 다른 포트가 닿아 있을 때만(아무것도 닿지 않은 선택 입력은 원조가 기본값으로 둔다)
                boolean attached = !n.wires().isEmpty() || n.ports().size() > 1;
                if (readers.isEmpty() || !attached) {
                    continue;
                }
                readers.sort(Comparator.comparing(p -> p.location()));
                List<String> names = new ArrayList<>();
                List<Component> comps = new ArrayList<>();
                for (Netlist.PortRef p : readers.subList(0, Math.min(3, readers.size()))) {
                    names.add(port(p));
                    comps.add(p.component);
                    told.add(p);
                }
                add(Diagnostic.Kind.INPUT_UNDRIVEN, comps, n.wires(), readers.get(0).location(),
                        String.join(", ", names));
            }
        }

        // ---- 조합 루프 ----

        void loops() {
            Map<Integer, Set<Integer>> edges = new HashMap<>();
            Map<Long, Set<Component>> via = new HashMap<>();
            for (Component c : circuit.getNonWires()) {
                boolean[][] dep = dependency(c);
                if (dep == null) {
                    continue;
                }
                for (int i = 0; i < c.getEnds().size(); i++) {
                    for (int o = 0; o < c.getEnds().size(); o++) {
                        if (!dep[i][o]) {
                            continue;
                        }
                        for (int from : roots(new Netlist.PortRef(c, i))) {
                            for (int to : roots(new Netlist.PortRef(c, o))) {
                                edges.computeIfAbsent(from, k -> new HashSet<>()).add(to);
                                via.computeIfAbsent(((long) from << 32) | to, k -> new LinkedHashSet<>()).add(c);
                            }
                        }
                    }
                }
            }
            for (Set<Integer> scc : sccs(edges)) {
                Set<Component> comps = new LinkedHashSet<>();
                for (int a : scc) {
                    for (int b : edges.getOrDefault(a, Collections.emptySet())) {
                        if (scc.contains(b)) {
                            comps.addAll(via.get(((long) a << 32) | b));
                        }
                    }
                }
                if (comps.isEmpty()) {
                    continue;
                }
                List<Component> list = sorted(comps);
                List<String> names = new ArrayList<>();
                for (Component c : list) {
                    names.add(Names.name(circuit, c));
                }
                add(Diagnostic.Kind.COMBINATIONAL_LOOP, list, Collections.emptyList(), list.get(0).getLocation(),
                        circuit.getName(), String.join(" → ", names));
            }
        }

        Set<Integer> roots(Netlist.PortRef p) {
            Set<Integer> ret = new HashSet<>();
            Netlist.Net n = nl.netOf(p.component, p.end);
            if (n == null) {
                return ret;
            }
            for (int b = 0; b < Math.min(Math.max(1, p.width()), width(n)); b++) {
                ret.add(find(node(n, b)));
            }
            return ret;
        }

        /**
         * 부품 안에서 입력 i가 출력 o에 바로(같은 전파 안에서) 영향을 주는가. 클럭으로 기억하는 부품과 값을 만들지 않는
         * 부품은 null(루프를 끊는다). 서브회로는 안쪽을 보고 정한다.
         */
        boolean[][] dependency(Component c) {
            int n = c.getEnds().size();
            if (c.getFactory() instanceof SubcircuitFactory) {
                return summary(c);
            }
            Kinds.Kind k = Kinds.of(c);
            Kinds.Category cat = k.category();
            boolean comb = cat == Kinds.Category.GATE || cat == Kinds.Category.PLEXER
                    || cat == Kinds.Category.ARITHMETIC || k.factory().equals("Bit Extender")
                    || k.factory().equals("Transistor") || k.factory().equals("Transmission Gate");
            if (!comb || k.stateful()) {
                return null;
            }
            boolean[][] dep = new boolean[n][n];
            for (int i = 0; i < n; i++) {
                for (int o = 0; o < n; o++) {
                    EndData a = c.getEnd(i);
                    EndData b = c.getEnd(o);
                    dep[i][o] = i != o && a.isInput() && !a.isOutput() && b.isOutput();
                }
            }
            return dep;
        }

        /** 서브회로 인스턴스의 끝 i → 끝 o 조합 의존(안쪽 회로에서 입력 핀이 출력 핀에 닿는가). */
        boolean[][] summary(Component inst) {
            Circuit sub = ((SubcircuitFactory) inst.getFactory()).getSubcircuit();
            boolean[][] pins = summaries.get(sub);
            if (pins == null) {
                summaries.put(sub, new boolean[0][0]); // 순환 참조 방어
                pins = new One(sub, summaries, false).reach();
                summaries.put(sub, pins);
            }
            // 인스턴스 끝 순서 = 모양의 포트 순서(Kinds.subcircuitPort와 같다)
            Direction facing = inst.getAttributeSet().getValue(StdAttr.FACING);
            SortedMap<Location, Instance> order = sub.getAppearance().getPortOffsets(
                    facing == null ? Direction.EAST : facing);
            List<Component> pinOrder = new ArrayList<>();
            for (Instance pin : order.values()) {
                pinOrder.add(Instance.getComponentFor(pin));
            }
            List<Component> subPins = pinsOf(sub);
            int n = inst.getEnds().size();
            boolean[][] dep = new boolean[n][n];
            for (int i = 0; i < n && i < pinOrder.size(); i++) {
                for (int o = 0; o < n && o < pinOrder.size(); o++) {
                    int a = subPins.indexOf(pinOrder.get(i));
                    int b = subPins.indexOf(pinOrder.get(o));
                    dep[i][o] = a >= 0 && b >= 0 && a < pins.length && b < pins.length && pins[a][b];
                }
            }
            return dep;
        }

        static List<Component> pinsOf(Circuit c) {
            List<Component> ret = new ArrayList<>();
            for (Component x : sorted(c.getNonWires())) {
                if (x.getFactory().getName().equals("Pin")) {
                    ret.add(x);
                }
            }
            return ret;
        }

        /** 이 회로의 핀끼리 조합 도달: [입력 핀][출력 핀]. 핀 순서는 pinsOf. */
        boolean[][] reach() {
            Map<Integer, Set<Integer>> edges = new HashMap<>();
            for (Component c : circuit.getNonWires()) {
                boolean[][] dep = dependency(c);
                if (dep == null) {
                    continue;
                }
                for (int i = 0; i < dep.length; i++) {
                    for (int o = 0; o < dep.length; o++) {
                        if (dep[i][o]) {
                            for (int from : roots(new Netlist.PortRef(c, i))) {
                                edges.computeIfAbsent(from, k -> new HashSet<>()).addAll(
                                        roots(new Netlist.PortRef(c, o)));
                            }
                        }
                    }
                }
            }
            List<Component> pins = pinsOf(circuit);
            boolean[][] ret = new boolean[pins.size()][pins.size()];
            for (int a = 0; a < pins.size(); a++) {
                Set<Integer> seen = new HashSet<>();
                ArrayDeque<Integer> todo = new ArrayDeque<>(roots(new Netlist.PortRef(pins.get(a), 0)));
                seen.addAll(todo);
                while (!todo.isEmpty()) {
                    for (int nx : edges.getOrDefault(todo.poll(), Collections.emptySet())) {
                        if (seen.add(nx)) {
                            todo.add(nx);
                        }
                    }
                }
                for (int b = 0; b < pins.size(); b++) {
                    if (b != a) {
                        for (int r : roots(new Netlist.PortRef(pins.get(b), 0))) {
                            ret[a][b] |= seen.contains(r);
                        }
                    }
                }
            }
            return ret;
        }

        /** 강하게 연결된 묶음 중 고리가 있는 것(원소 둘 이상이거나 자기 자신으로 가는 간선). 반복형 Tarjan. */
        static List<Set<Integer>> sccs(Map<Integer, Set<Integer>> edges) {
            Map<Integer, Integer> index = new HashMap<>();
            Map<Integer, Integer> low = new HashMap<>();
            Set<Integer> onStack = new HashSet<>();
            ArrayDeque<Integer> stack = new ArrayDeque<>();
            List<Set<Integer>> ret = new ArrayList<>();
            int[] counter = {0};
            List<Integer> all = new ArrayList<>(edges.keySet());
            Collections.sort(all);
            for (int start : all) {
                if (index.containsKey(start)) {
                    continue;
                }
                ArrayDeque<int[]> work = new ArrayDeque<>(); // {node, next child position}
                Map<Integer, List<Integer>> kids = new HashMap<>();
                work.push(new int[] {start, 0});
                while (!work.isEmpty()) {
                    int[] top = work.peek();
                    int v = top[0];
                    if (top[1] == 0 && !index.containsKey(v)) {
                        index.put(v, counter[0]);
                        low.put(v, counter[0]++);
                        stack.push(v);
                        onStack.add(v);
                        List<Integer> ks = new ArrayList<>(edges.getOrDefault(v, Collections.emptySet()));
                        Collections.sort(ks);
                        kids.put(v, ks);
                    }
                    List<Integer> ks = kids.get(v);
                    if (top[1] < ks.size()) {
                        int w = ks.get(top[1]++);
                        if (!index.containsKey(w)) {
                            work.push(new int[] {w, 0});
                        } else if (onStack.contains(w)) {
                            low.put(v, Math.min(low.get(v), index.get(w)));
                        }
                        continue;
                    }
                    work.pop();
                    if (!work.isEmpty()) {
                        int u = work.peek()[0];
                        low.put(u, Math.min(low.get(u), low.get(v)));
                    }
                    if (low.get(v).equals(index.get(v))) {
                        Set<Integer> scc = new HashSet<>();
                        int w;
                        do {
                            w = stack.pop();
                            onStack.remove(w);
                            scc.add(w);
                        } while (w != v);
                        if (scc.size() > 1 || edges.getOrDefault(v, Collections.emptySet()).contains(v)) {
                            ret.add(scc);
                        }
                    }
                }
            }
            return ret;
        }
    }

    /** 위→아래, 왼쪽→오른쪽(메시지 순서가 늘 같게). */
    static List<Component> sorted(Collection<? extends Component> comps) {
        List<Component> ret = new ArrayList<>(comps);
        ret.sort(Comparator.comparingInt((Component c) -> c.getLocation().getY())
                .thenComparingInt(c -> c.getLocation().getX()));
        return ret;
    }
}
