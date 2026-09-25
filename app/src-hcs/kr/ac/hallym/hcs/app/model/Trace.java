/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;

/**
 * 넷을 따라 앞(값이 흘러가는 쪽)·뒤(값이 오는 쪽)로 탐색한다(#71). 터널은 넷에서 이미 합쳐져 있고, 스플리터는
 * 비트 대응으로 건너고, 서브회로는 안쪽 핀으로 들어가고 바깥 포트로 나온다. 상태를 가진 부품(등록표의
 * stateful: 레지스터, 메모리 등)에서는 기본적으로 멈추고, 옵션으로 넘는다. 영향 경로(2c), E·X 추적(4), 넷 정보가
 * 이 하나를 쓴다.
 */
public final class Trace {
    /** 계층 속 넷: 맨 위 회로부터 거쳐 온 서브회로 부품들과 그 안의 넷. */
    public static final class Node {
        public final List<Component> instances;
        public final Circuit circuit;
        public final Netlist.Net net;

        Node(List<Component> instances, Circuit circuit, Netlist.Net net) {
            this.instances = Collections.unmodifiableList(new ArrayList<>(instances));
            this.circuit = circuit;
            this.net = net;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Node)) {
                return false;
            }
            Node n = (Node) o;
            return n.net == net && n.circuit == circuit && sameInstances(n.instances, instances);
        }

        private static boolean sameInstances(List<Component> a, List<Component> b) {
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                if (a.get(i) != b.get(i)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int h = System.identityHashCode(net);
            for (Component c : instances) {
                h = h * 31 + System.identityHashCode(c);
            }
            return h;
        }
    }

    /** 한 걸음의 종류. */
    public enum StepKind {
        /** 부품 입력 → 출력(조합, 또는 "레지스터 넘어서"의 상태 부품). */
        COMPONENT,
        /** 스플리터 비트 대응. */
        SPLITTER,
        /** 서브회로 부품 포트 → 안쪽 핀. */
        INTO,
        /** 안쪽 경계 핀 → 바깥 부품 포트. */
        OUT_OF
    }

    /** 넷에서 넷으로 가는 한 걸음(신호 방향과 무관하게 탐색 방향으로 적는다). */
    public static final class Step {
        public final Node from;
        public final Node to;
        /** 건넌 부품(스플리터, 서브회로 부품, 경계 핀 포함). */
        public final Component via;
        public final StepKind kind;
        /** 부품을 몇 번 건넜나(스플리터·서브회로 경계는 세지 않는다). */
        public final int depth;
        /** 상태 부품을 몇 번 넘었나(0 = 이번 사이클, 1 = 다음 사이클 …). */
        public final int cycle;
        /** 이 걸음으로 옮겨 간 비트(to 넷 기준). */
        public final BitSet bits;

        Step(Node from, Node to, Component via, StepKind kind, int depth, int cycle, BitSet bits) {
            this.from = from;
            this.to = to;
            this.via = via;
            this.kind = kind;
            this.depth = depth;
            this.cycle = cycle;
            this.bits = (BitSet) bits.clone();
        }

        @Override
        public String toString() {
            return kind + " " + via.getFactory().getName() + "@" + via.getLocation() + " d" + depth + " c" + cycle;
        }
    }

    /** 탐색 설정. */
    public static final class Options {
        /** 상태 부품(레지스터·메모리 등)을 넘어 다음 사이클로 간다("Through Registers"). */
        public boolean crossState;
        /** 이 깊이(건넌 부품 수)까지만. 음수면 끝까지. */
        public int maxDepth = -1;
        /** 시작 넷에서 따라갈 비트. null이면 넷 전체. */
        public BitSet startBits;
        /** 부품을 건널지 묻는다(Active Path Only 등). null이면 모두 건넌다. */
        public Pass pass;
    }

    /** 부품 c를 inEnd 입력(앞으로) 또는 outEnd 출력(뒤로)으로 건널 수 있는가. */
    public interface Pass {
        /**
         * @param instances 서브회로 경로
         * @param end 도착한 포트(앞으로면 입력, 뒤로면 출력)
         * @param other 건너갈 포트(앞으로면 출력, 뒤로면 입력)
         */
        boolean passes(List<Component> instances, Component c, int end, int other);
    }

    /** 탐색 결과: 지나간 넷과, 멈춘 상태 부품. */
    public static final class Result {
        public final Set<Node> nets = new LinkedHashSet<>();
        public final Set<Component> stoppedAt = new LinkedHashSet<>();
        public final Set<Component> components = new LinkedHashSet<>();
        /** 넷마다 처음 닿은 깊이. */
        public final Map<Node, Integer> depth = new HashMap<>();
        /** 넷마다 닿은 비트. */
        public final Map<Node, BitSet> bits = new HashMap<>();
        /** 넷마다 처음 닿은 사이클(상태 부품을 넘은 수). */
        public final Map<Node, Integer> cycle = new HashMap<>();
        /** 걸음(탐색 순서, 결정적). */
        public final List<Step> steps = new ArrayList<>();
        /** 조합 고리가 닫힌 부품(이미 지난 넷으로 되돌아온 곳). 진단은 하지 않는다. */
        public final Set<Component> loops = new LinkedHashSet<>();
        /** 멈춘 상태 부품마다 멈춘 사이클과 깊이(끝점 표시용). */
        public final Map<Component, Integer> stopDepth = new LinkedHashMap<>();
    }

    private final Map<Circuit, Netlist> cache = new HashMap<>();

    public Netlist netlist(Circuit c) {
        return cache.computeIfAbsent(c, Netlist::of);
    }

    /** 맨 위 회로 top의 부품 c, end번 포트가 속한 넷에서 시작한다. */
    public Node node(Circuit top, Component c, int end) {
        this.top = top;
        return new Node(Collections.<Component>emptyList(), top, netlist(top).netOf(c, end));
    }

    /** 맨 위 회로 top의 선 w가 속한 넷에서 시작한다. */
    public Node node(Circuit top, com.cburch.logisim.circuit.Wire w) {
        this.top = top;
        return new Node(Collections.<Component>emptyList(), top, netlist(top).netOf(w));
    }

    /** 계층 속 넷(서브회로 안에서 시작할 때). top은 맨 위 회로. */
    public Node node(Circuit top, List<Component> instances, Circuit circuit, Netlist.Net net) {
        this.top = top;
        return new Node(instances, circuit, net);
    }

    public Result forward(Node start, boolean crossState) {
        Options o = new Options();
        o.crossState = crossState;
        return walk(Collections.singletonList(start), true, o);
    }

    public Result backward(Node start, boolean crossState) {
        Options o = new Options();
        o.crossState = crossState;
        return walk(Collections.singletonList(start), false, o);
    }

    public Result forward(List<Node> starts, Options o) {
        return walk(starts, true, o);
    }

    public Result backward(List<Node> starts, Options o) {
        return walk(starts, false, o);
    }

    private static final class Todo {
        final Node node;
        final BitSet bits;
        final int depth;
        final int cycle;
        final Step via;

        Todo(Node node, BitSet bits, int depth, int cycle, Step via) {
            this.node = node;
            this.bits = bits;
            this.depth = depth;
            this.cycle = cycle;
            this.via = via;
        }
    }

    /**
     * 넓이 우선(깊이 = 건넌 부품 수). 한 넷에 새 비트가 오면 그 비트만 다시 퍼뜨린다. 같은 깊이 안의 순서는 포트
     * 위치(y, x), 부품 이름, 포트 번호로 정해 결정적이다.
     */
    private Result walk(List<Node> starts, boolean forward, Options o) {
        Result r = new Result();
        // 깊이별 큐: 스플리터·경계 걸음은 같은 깊이, 부품 걸음은 다음 깊이
        java.util.TreeMap<Integer, Deque<Todo>> levels = new java.util.TreeMap<>();
        Map<Node, Node> parent = new HashMap<>();
        for (Node s : starts) {
            if (s == null || s.net == null) {
                continue;
            }
            BitSet b = o.startBits != null ? (BitSet) o.startBits.clone() : all(s.net);
            levels.computeIfAbsent(0, k -> new ArrayDeque<>()).add(new Todo(s, b, 0, 0, null));
        }
        while (!levels.isEmpty()) {
            Map.Entry<Integer, Deque<Todo>> first = levels.firstEntry();
            Todo t = first.getValue().poll();
            if (first.getValue().isEmpty()) {
                levels.remove(first.getKey());
            }
            if (t == null || t.node.net == null) {
                continue;
            }
            Node n = t.node;
            BitSet seen = r.bits.get(n);
            BitSet fresh = (BitSet) t.bits.clone();
            if (seen != null) {
                fresh.andNot(seen);
                if (fresh.isEmpty()) {
                    // 이미 지난 넷: 고리가 닫히는지 본다(도착 넷이 지금 걸음의 조상인가)
                    if (t.via != null && t.via.kind == StepKind.COMPONENT && ancestor(parent, t.via.from, n)) {
                        r.loops.add(t.via.via);
                    }
                    continue;
                }
                seen.or(fresh);
            } else {
                r.bits.put(n, (BitSet) fresh.clone());
                r.depth.put(n, t.depth);
                r.cycle.put(n, t.cycle);
                if (t.via != null) {
                    parent.put(n, t.via.from);
                }
            }
            r.nets.add(n);
            if (t.via != null) {
                r.steps.add(t.via);
            }
            Netlist nl = netlist(n.circuit);
            // 스플리터: 따라가는 비트만 건넌다(양쪽)
            Map<Netlist.Net, BitSet> across = new java.util.LinkedHashMap<>();
            Map<Netlist.Net, Component> acrossVia = new HashMap<>();
            for (Netlist.BitLink l : nl.bitLinks()) {
                if (l.combined == n.net && l.arm != null && fresh.get(l.bit)) {
                    across.computeIfAbsent(l.arm, k -> new BitSet()).set(l.armBit);
                    acrossVia.put(l.arm, l.splitter);
                } else if (l.arm == n.net && l.combined != null && fresh.get(l.armBit)) {
                    across.computeIfAbsent(l.combined, k -> new BitSet()).set(l.bit);
                    acrossVia.put(l.combined, l.splitter);
                }
            }
            for (Map.Entry<Netlist.Net, BitSet> e : sortNets(across)) {
                Node to = new Node(n.instances, n.circuit, e.getKey());
                Step st = new Step(n, to, acrossVia.get(e.getKey()), StepKind.SPLITTER, t.depth, t.cycle, e.getValue());
                push(levels, new Todo(to, e.getValue(), t.depth, t.cycle, st));
            }
            List<Netlist.PortRef> ports = new ArrayList<>(forward ? n.net.readers() : n.net.drivers());
            ports.sort(PORT_ORDER);
            for (Netlist.PortRef p : ports) {
                step(n, t, p, forward, o, r, levels);
            }
            // 바깥으로 나가기: 이 회로가 서브회로이고 넷에 그 경계 핀이 있으면
            if (!n.instances.isEmpty()) {
                List<Netlist.PortRef> all = new ArrayList<>(n.net.ports());
                all.sort(PORT_ORDER);
                for (Netlist.PortRef p : all) {
                    if (isPin(p.component) && isBoundary(p.component, forward)) {
                        Node out = outward(n, p.component);
                        if (out != null) {
                            Step st = new Step(n, out, n.instances.get(n.instances.size() - 1), StepKind.OUT_OF,
                                    t.depth, t.cycle, fresh);
                            push(levels, new Todo(out, fresh, t.depth, t.cycle, st));
                        }
                    }
                }
            }
        }
        return r;
    }

    private static void push(java.util.TreeMap<Integer, Deque<Todo>> levels, Todo t) {
        levels.computeIfAbsent(t.depth, k -> new ArrayDeque<>()).add(t);
    }

    private static boolean ancestor(Map<Node, Node> parent, Node from, Node target) {
        Node x = from;
        for (int guard = 0; x != null && guard < 100000; guard++) {
            if (x.equals(target)) {
                return true;
            }
            x = parent.get(x);
        }
        return false;
    }

    static BitSet all(Netlist.Net net) {
        BitSet b = new BitSet();
        b.set(0, Math.max(1, net.width()));
        return b;
    }

    /** 포트 순서: 위치(y, x), 부품 이름, 포트 번호. */
    static final java.util.Comparator<Netlist.PortRef> PORT_ORDER = java.util.Comparator
            .<Netlist.PortRef>comparingInt(p -> p.location().getY())
            .thenComparingInt(p -> p.location().getX())
            .thenComparing(p -> p.component.getFactory().getName())
            .thenComparingInt(p -> p.end);

    private static List<Map.Entry<Netlist.Net, BitSet>> sortNets(Map<Netlist.Net, BitSet> m) {
        List<Map.Entry<Netlist.Net, BitSet>> ret = new ArrayList<>(m.entrySet());
        ret.sort(java.util.Comparator.comparingInt(e -> e.getKey().id()));
        return ret;
    }

    private void step(Node n, Todo t, Netlist.PortRef p, boolean forward, Options o, Result r,
            java.util.TreeMap<Integer, Deque<Todo>> levels) {
        Component c = p.component;
        if (Kinds.of(c).factory().equals("Splitter")) {
            return; // 비트 대응으로 이미 건넜다
        }
        r.components.add(c);
        if (c.getFactory() instanceof SubcircuitFactory) {
            Node in = inward(n, c, p.end);
            if (in != null) {
                Step st = new Step(n, in, c, StepKind.INTO, t.depth, t.cycle, all(in.net));
                push(levels, new Todo(in, all(in.net), t.depth, t.cycle, st));
            }
            return;
        }
        Kinds.Kind kind = Kinds.of(c);
        int cycle = t.cycle;
        if (kind.stateful()) {
            if (!o.crossState) {
                r.stoppedAt.add(c);
                r.stopDepth.putIfAbsent(c, t.depth);
                return;
            }
            cycle++;
        }
        int depth = t.depth + 1;
        if (o.maxDepth >= 0 && depth > o.maxDepth) {
            return;
        }
        Netlist nl = netlist(n.circuit);
        for (int i = 0; i < c.getEnds().size(); i++) {
            boolean next = forward ? c.getEnds().get(i).isOutput() && !c.getEnds().get(i).isInput()
                    : c.getEnds().get(i).isInput() && !c.getEnds().get(i).isOutput();
            if (!next || (o.pass != null && !o.pass.passes(n.instances, c, p.end, i))) {
                continue;
            }
            Netlist.Net to = nl.netOf(c, i);
            if (to == null) {
                continue;
            }
            Node tn = new Node(n.instances, n.circuit, to);
            BitSet b = all(to);
            Step st = new Step(n, tn, c, StepKind.COMPONENT, depth, cycle, b);
            push(levels, new Todo(tn, b, depth, cycle, st));
        }
    }

    static boolean isPin(Component c) {
        return Kinds.of(c).factory().equals("Pin");
    }

    /** 경계 핀: 앞으로 가면 출력 핀(바깥으로 값을 냄), 뒤로 가면 입력 핀(바깥에서 값을 받음). */
    private static boolean isBoundary(Component pin, boolean forward) {
        boolean output = Boolean.TRUE.equals(pin.getAttributeSet().getValue(
                com.cburch.logisim.std.wiring.Pin.ATTR_TYPE));
        return forward == output;
    }

    /** 서브회로 부품의 end번 포트로 들어가 안쪽 핀의 넷으로. */
    private Node inward(Node n, Component inst, int end) {
        Circuit sub = ((SubcircuitFactory) inst.getFactory()).getSubcircuit();
        Instance pin = pinFor(inst, end);
        if (pin == null) {
            return null;
        }
        List<Component> path = new ArrayList<>(n.instances);
        path.add(inst);
        return new Node(path, sub, netlist(sub).netOf(Instance.getComponentFor(pin), 0));
    }

    /** 안쪽 경계 핀에서 바깥 서브회로 부품의 해당 포트 넷으로. */
    private Node outward(Node n, Component pin) {
        Component inst = n.instances.get(n.instances.size() - 1);
        Circuit parent = parentCircuit(n);
        int end = endFor(inst, pin);
        if (end < 0 || parent == null) {
            return null;
        }
        List<Component> path = new ArrayList<>(n.instances.subList(0, n.instances.size() - 1));
        return new Node(path, parent, netlist(parent).netOf(inst, end));
    }

    private Circuit parentCircuit(Node n) {
        // 부모 회로는 경로 바로 앞 부품의 서브회로, 경로가 하나뿐이면 탐색 시작 회로다
        if (n.instances.size() >= 2) {
            return ((SubcircuitFactory) n.instances.get(n.instances.size() - 2).getFactory()).getSubcircuit();
        }
        return top;
    }

    private Circuit top;

    /** 원조 SubcircuitFactory와 같은 순서로, 부품의 end번 포트에 대응하는 안쪽 핀. */
    static Instance pinFor(Component inst, int end) {
        int i = 0;
        for (Instance pin : ports(inst).values()) {
            if (i++ == end) {
                return pin;
            }
        }
        return null;
    }

    static int endFor(Component inst, Component pin) {
        int i = 0;
        for (Instance p : ports(inst).values()) {
            if (Instance.getComponentFor(p) == pin) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static SortedMap<Location, Instance> ports(Component inst) {
        SubcircuitFactory f = (SubcircuitFactory) inst.getFactory();
        Direction facing = inst.getAttributeSet().getValue(StdAttr.FACING);
        return f.getSubcircuit().getAppearance().getPortOffsets(facing == null ? Direction.EAST : facing);
    }

    /** 결과의 넷들을 사람이 읽을 이름으로(테스트·진단용): 경로 › 넷의 첫 포트 이름. */
    public static String describe(Node n) {
        List<String> parts = new ArrayList<>();
        Circuit c = n.circuit;
        for (Component inst : n.instances) {
            parts.add(inst.getFactory().getName());
        }
        String port = n.net.ports().isEmpty() ? "net" + n.net.id() : Objects.toString(n.net.ports().get(0));
        parts.add(port);
        return c.getName() + ":" + Names.path(parts);
    }
}
