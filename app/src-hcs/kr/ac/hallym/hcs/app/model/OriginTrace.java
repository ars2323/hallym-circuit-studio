/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;

/**
 * E·X 출처 추적(D-01, PLAN.md 4.3). E·X가 보이는 넷에서 입력 쪽으로 거슬러 올라가 처음 생긴 한 곳에서 멈춘다.
 * <ol>
 * <li>넷을 구동하는 부품의 입력 중 정해지지 않은 것을 따라간다. MUX는 고른 입력만(선택이 정해지지 않았으면 선택
 * 입력을) 본다.</li>
 * <li>입력은 모두 정해졌는데 출력이 E·X인 부품, 구동자가 없는 선, 구동자가 둘 이상인 선에서 멈춘다.</li>
 * <li>서브회로 경계는 안으로(출력 포트) 또는 밖으로(입력 핀) 건넌다. 스플리터는 따라가는 비트로 건넌다.</li>
 * <li>메모리(RAM, Data Memory 등)는 읽기가 조합이라 주소·읽기 입력을 따라가고, 입력이 모두 정해졌는데 출력이 떠
 * 있으면 그 메모리에서 멈춘다.</li>
 * </ol>
 * 4.3의 "레지스터를 만나면 시간을 거슬러"는 이 도구에서 X 기록 감지(D-03)가 맡는다: 원조 Register·플립플롭은 X를
 * 담지 않고 옛 값을 지킨다(Register.propagate, AbstractFlipFlop.propagate). 그래서 쓰기를 시도한 에지 직전 스텝의
 * 기록값으로 이 추적을 돈다({@link Values}의 step). 값은 읽기만 한다. GUI 없이 테스트한다.
 */
public final class OriginTrace {
    /** 값 읽기: 지금 상태 또는 기록. */
    public interface Values {
        /** 경로 path 안 점 at의 step 때 값. 모르면 null. */
        Value value(List<Component> path, Location at, int step);
    }

    /** 지금 시뮬레이션 상태(시간을 거슬러 가지 않는다). step은 무시한다. */
    public static Values live(CircuitState top) {
        return (path, at, step) -> {
            CircuitState s = InstancePaths.stateFor(top, path);
            return s == null ? null : s.getValue(at);
        };
    }

    /** 멈춘 까닭. */
    public enum Cause {
        /** 입력은 모두 정해졌는데 출력이 E·X인 부품. */
        COMPONENT,
        /** 값을 내는 부품이 없는 선. */
        UNDRIVEN,
        /** 둘 이상이 서로 다른 값으로 구동하는 선(E). */
        CONFLICT,
        /** 이어진 부품이 모두 값을 내지 않는 선(X, 예: 꺼진 3상태 버퍼들). */
        ALL_OFF,
        /** 맨 위 회로의 입력 핀 값이 정해지지 않았다. */
        INPUT_PIN,
        /** 상태 부품의 출력이 E·X다(원조 부품은 그렇지 않다. 외부 라이브러리 부품 대비). */
        STORED,
        /** 따라가다 제자리로 돌아왔다(조합 루프). */
        LOOP
    }

    /** 찾은 원인 한 곳. */
    public static final class Origin {
        public final Cause cause;
        /** 원인이 있는 곳(서브회로 경로, 회로, 넷). */
        public final Trace.Node node;
        /** 원인 부품(선이 원인이면 null). */
        public final Component component;
        /** CONFLICT·ALL_OFF: 그 선에 값을 내는 부품들. */
        public final List<Component> drivers;
        /** 원인을 본 스텝. */
        public final int step;
        /** 그곳의 값. */
        public final Value value;
        /** 시작에서 원인까지 지난 넷들과 그때의 스텝(강조용). */
        public final List<Trace.Node> chain;
        public final List<Integer> chainSteps;

        Origin(Cause cause, Trace.Node node, Component component, List<Component> drivers, int step, Value value,
                List<Trace.Node> chain, List<Integer> chainSteps) {
            this.cause = cause;
            this.node = node;
            this.component = component;
            this.drivers = Collections.unmodifiableList(new ArrayList<>(drivers));
            this.step = step;
            this.value = value;
            this.chain = Collections.unmodifiableList(new ArrayList<>(chain));
            this.chainSteps = Collections.unmodifiableList(new ArrayList<>(chainSteps));
        }

        /** 원인이 E(충돌)인가. 아니면 X(정해지지 않음). */
        public boolean isError() {
            return value != null && value.isErrorValue() || hasError(value);
        }

        @Override
        public String toString() {
            return cause + " " + (component == null ? Trace.describe(node) : component.getFactory().getName()) + " @"
                    + step;
        }
    }

    static boolean hasError(Value v) {
        if (v == null) {
            return false;
        }
        for (Value b : v.getAll()) {
            if (b == Value.ERROR) {
                return true;
            }
        }
        return false;
    }

    static boolean undefined(Value v) {
        return v != null && !v.isFullyDefined() && v.getWidth() > 0;
    }

    private final Circuit top;
    private final Values values;
    private final Trace trace = new Trace();

    public OriginTrace(Circuit top, Values values) {
        this.top = top;
        this.values = values;
    }

    /** 맨 위 회로(또는 경로 안)의 넷 노드. */
    public Trace.Node node(List<Component> path, Circuit circuit, Location at) {
        Netlist.Net net = trace.netlist(circuit).netAt(at);
        return net == null ? null : trace.node(top, path, circuit, net);
    }

    public Trace.Node node(Component c, int end) {
        return trace.node(top, c, end);
    }

    /** 넷의 대표 자리(기록이 값을 두는 곳과 같은 규칙). */
    static Location rep(Netlist.Net net) {
        if (!net.ports().isEmpty()) {
            return net.ports().get(0).location();
        }
        return net.wires().isEmpty() ? null : net.wires().get(0).getEnd0();
    }

    Value value(Trace.Node n, int step) {
        Location at = rep(n.net);
        return at == null ? null : values.value(n.instances, at, step);
    }

    private Value portValue(Trace.Node n, Component c, int end, int step) {
        return values.value(n.instances, c.getEnd(end).getLocation(), step);
    }

    /**
     * start 넷의 step 때 E·X의 출처. start가 정해진 값이면 null.
     */
    public Origin find(Trace.Node start, int step) {
        if (start == null || !undefined(value(start, step))) {
            return null;
        }
        List<Trace.Node> chain = new ArrayList<>();
        List<Integer> steps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Trace.Node n = start;
        int s = step;
        int bit = -1; // 스플리터를 건넌 뒤 따라가는 비트(-1: 넷 전체)
        Trace.Node hopFrom = null; // 스플리터로 건너오기 전 넷(구동자 없는 곳을 가리킬 때)
        Component last = null;
        for (int guard = 0; guard < 100_000; guard++) {
            Value v = value(n, s);
            if (!seen.add(key(n, s, bit))) {
                return new Origin(Cause.LOOP, n, last, Collections.<Component>emptyList(), s, v, chain, steps);
            }
            chain.add(n);
            steps.add(s);
            List<Netlist.PortRef> drivers = n.net.drivers();
            // 서브회로 안의 입력 핀: 바깥 넷으로
            if (drivers.size() == 1 && Trace.isPin(drivers.get(0).component) && !n.instances.isEmpty()
                    && !isOutputPin(drivers.get(0).component)) {
                Trace.Node out = trace.outward(n, drivers.get(0).component);
                if (out != null) {
                    n = out;
                    continue;
                }
            }
            if (drivers.isEmpty()) {
                Hop h = splitterHop(n, v, bit, s, seen);
                if (h != null) {
                    if (hopFrom == null) {
                        hopFrom = n;
                    }
                    n = h.node;
                    bit = h.bit;
                    continue;
                }
                Trace.Node at = hopFrom != null ? hopFrom : n;
                return new Origin(Cause.UNDRIVEN, at, null, Collections.<Component>emptyList(), s, value(at, s), chain,
                        steps);
            }
            hopFrom = null;
            if (drivers.size() >= 2) {
                List<Component> ds = new ArrayList<>();
                for (Netlist.PortRef p : drivers) {
                    ds.add(p.component);
                }
                ds.sort(java.util.Comparator.comparing(Component::getLocation)); // 문구 순서를 고정한다(V-02)
                return new Origin(hasError(v) ? Cause.CONFLICT : Cause.ALL_OFF, n, null, ds, s, v, chain, steps);
            }
            Netlist.PortRef d = drivers.get(0);
            Component c = d.component;
            last = c;
            if (c.getFactory() instanceof SubcircuitFactory) {
                Trace.Node in = trace.inward(n, c, d.end);
                if (in == null) {
                    return new Origin(Cause.COMPONENT, n, c, Collections.<Component>emptyList(), s, v, chain, steps);
                }
                n = in;
                continue;
            }
            if (Trace.isPin(c)) {
                return new Origin(Cause.INPUT_PIN, n, c, Collections.<Component>emptyList(), s, v, chain, steps);
            }
            Kinds.Kind kind = Kinds.of(c);
            if (kind.stateful() && !readsCombinationally(c)) {
                return new Origin(Cause.STORED, n, c, Collections.<Component>emptyList(), s, v, chain, steps);
            }
            Integer next = undefinedInput(n, c, s);
            if (next == null) {
                return new Origin(kind.stateful() ? Cause.STORED : Cause.COMPONENT, n, c,
                        Collections.<Component>emptyList(), s, v, chain, steps);
            }
            Netlist.Net to = trace.netlist(n.circuit).netOf(c, next);
            if (to == null) {
                return new Origin(Cause.COMPONENT, n, c, Collections.<Component>emptyList(), s, v, chain, steps);
            }
            n = trace.node(top, n.instances, n.circuit, to);
            bit = -1;
        }
        return null;
    }

    private static String key(Trace.Node n, int step, int bit) {
        return n.hashCode() + ":" + System.identityHashCode(n.net) + ":" + n.instances.size() + "@" + step + "#" + bit;
    }

    /** 메모리처럼 읽기는 조합으로 하는 상태 부품: 주소·읽기 입력이 정해지지 않았으면 그쪽을 먼저 따라간다. */
    static boolean readsCombinationally(Component c) {
        String f = Kinds.of(c).factory();
        return f.equals("RAM") || f.equals("ROM") || f.equals("Instruction Memory") || f.equals("Data Memory")
                || f.equals("Stack");
    }

    private static boolean isOutputPin(Component pin) {
        return Boolean.TRUE.equals(pin.getAttributeSet().getValue(com.cburch.logisim.std.wiring.Pin.ATTR_TYPE));
    }

    /**
     * 부품 c의 입력 중 step 때 정해지지 않은 첫 포트(포트 번호 순). MUX는 고른 입력만 본다. 없으면 null.
     */
    Integer undefinedInput(Trace.Node n, Component c, int step) {
        int ends = c.getEnds().size();
        String f = Kinds.of(c).factory();
        if (f.equals("Multiplexer")) {
            int k = 1 << selectWidth(c);
            if (k < ends) {
                Value sel = portValue(n, c, k, step);
                if (undefined(sel)) {
                    return k;
                }
                for (int i = k + 1; i < ends - 1; i++) { // enable
                    if (undefined(portValue(n, c, i, step))) {
                        return i;
                    }
                }
                int i = sel == null ? -1 : sel.toIntValue();
                return i >= 0 && i < k && undefined(portValue(n, c, i, step)) ? Integer.valueOf(i) : null;
            }
        }
        for (int i = 0; i < ends; i++) {
            if (!c.getEnds().get(i).isInput() || c.getEnds().get(i).isOutput()) {
                continue;
            }
            if (undefined(portValue(n, c, i, step))) {
                return i;
            }
        }
        return null;
    }

    private static int selectWidth(Component c) {
        Object sel = c.getAttributeSet().getValue(com.cburch.logisim.std.plexers.Plexers.ATTR_SELECT);
        return sel instanceof BitWidth ? ((BitWidth) sel).getWidth() : 1;
    }

    private static final class Hop {
        final Trace.Node node;
        final int bit;

        Hop(Trace.Node node, int bit) {
            this.node = node;
            this.bit = bit;
        }
    }

    /** 구동자가 없는 넷: 스플리터 건너편에서 따라가는 비트(정해지지 않은 비트)가 오는 넷으로. 없으면 null. */
    private Hop splitterHop(Trace.Node n, Value v, int bit, int step, Set<String> seen) {
        Netlist nl = trace.netlist(n.circuit);
        for (Netlist.BitLink l : nl.bitLinks()) {
            Netlist.Net other;
            int mine;
            int theirs;
            if (l.arm == n.net && l.combined != null) {
                other = l.combined;
                mine = l.armBit;
                theirs = l.bit;
            } else if (l.combined == n.net && l.arm != null) {
                other = l.arm;
                mine = l.bit;
                theirs = l.armBit;
            } else {
                continue;
            }
            if (bit >= 0 && mine != bit) {
                continue;
            }
            if (v != null && mine < v.getWidth() && v.get(mine).isFullyDefined()) {
                continue;
            }
            Trace.Node to = trace.node(top, n.instances, n.circuit, other);
            if (seen.contains(key(to, step, theirs))) {
                continue;
            }
            return new Hop(to, theirs);
        }
        return null;
    }
}
