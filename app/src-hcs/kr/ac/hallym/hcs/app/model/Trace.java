/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
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

    /** 탐색 결과: 지나간 넷과, 멈춘 상태 부품. */
    public static final class Result {
        public final Set<Node> nets = new LinkedHashSet<>();
        public final Set<Component> stoppedAt = new LinkedHashSet<>();
        public final Set<Component> components = new LinkedHashSet<>();
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

    public Result forward(Node start, boolean crossState) {
        return walk(start, true, crossState);
    }

    public Result backward(Node start, boolean crossState) {
        return walk(start, false, crossState);
    }

    private Result walk(Node start, boolean forward, boolean crossState) {
        Result r = new Result();
        Deque<Node> todo = new ArrayDeque<>();
        todo.add(start);
        while (!todo.isEmpty()) {
            Node n = todo.poll();
            if (n.net == null || !r.nets.add(n)) {
                continue;
            }
            Netlist nl = netlist(n.circuit);
            // 스플리터: 넷을 합치지 않고 비트 대응으로 건넌다(양쪽 모두)
            for (Netlist.BitLink l : nl.bitLinks()) {
                if (l.combined == n.net && l.arm != null) {
                    todo.add(new Node(n.instances, n.circuit, l.arm));
                } else if (l.arm == n.net && l.combined != null) {
                    todo.add(new Node(n.instances, n.circuit, l.combined));
                }
            }
            for (Netlist.PortRef p : forward ? n.net.readers() : n.net.drivers()) {
                step(n, p, forward, crossState, r, todo);
            }
            // 바깥으로 나가기: 이 회로가 서브회로이고 넷에 그 경계 핀이 있으면
            if (!n.instances.isEmpty()) {
                for (Netlist.PortRef p : n.net.ports()) {
                    if (isPin(p.component) && isBoundary(p.component, forward)) {
                        Node out = outward(n, p.component);
                        if (out != null) {
                            todo.add(out);
                        }
                    }
                }
            }
        }
        return r;
    }

    private void step(Node n, Netlist.PortRef p, boolean forward, boolean crossState, Result r, Deque<Node> todo) {
        Component c = p.component;
        r.components.add(c);
        if (c.getFactory() instanceof SubcircuitFactory) {
            Node in = inward(n, c, p.end);
            if (in != null) {
                todo.add(in);
            }
            return;
        }
        Kinds.Kind kind = Kinds.of(c);
        if (kind.stateful() && !crossState) {
            r.stoppedAt.add(c);
            return;
        }
        Netlist nl = netlist(n.circuit);
        for (int i = 0; i < c.getEnds().size(); i++) {
            boolean next = forward ? c.getEnds().get(i).isOutput() && !c.getEnds().get(i).isInput()
                    : c.getEnds().get(i).isInput() && !c.getEnds().get(i).isOutput();
            if (next) {
                todo.add(new Node(n.instances, n.circuit, nl.netOf(c, i)));
            }
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
