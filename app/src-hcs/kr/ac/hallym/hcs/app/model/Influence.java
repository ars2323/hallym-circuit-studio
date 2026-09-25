/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;

/**
 * 영향 경로(P-01, PLAN.md 11.12). 고른 부품·선에서 앞(값이 흘러가는 쪽)·뒤(값이 오는 쪽)·양쪽으로 닿는 곳을
 * {@link Trace} 하나로 구한다. 기본은 상태 부품(레지스터·메모리)에서 멈추고 "Through Registers"로 넘는다. 두 부품
 * 사이 경로는 앞쪽(A에서) ∩ 뒤쪽(B에서)이다. GUI 없이 회로 모델만 본다. 결과는 보여 줄 회로마다
 * {@link #view(Circuit)}로 꺼낸다.
 */
public final class Influence {
    public enum Mode { FORWARD, BACKWARD, BOTH }

    /** 보여 줄 회로 하나에서의 모습. */
    public static final class View {
        public final Set<Wire> forwardWires = new LinkedHashSet<>();
        public final Set<Wire> backwardWires = new LinkedHashSet<>();
        public final Set<Component> forwardParts = new LinkedHashSet<>();
        public final Set<Component> backwardParts = new LinkedHashSet<>();
        /** 멈춘 상태 부품(끝점). */
        public final Set<Component> stops = new LinkedHashSet<>();
        /** 서브회로 부품 → 그 안에서 닿은 부품 수("alu: 3 places"). */
        public final Map<Component, Integer> inside = new LinkedHashMap<>();
        /**
         * 같은 이름 터널 사이 점선: 터널이 딱 둘인 넷의 두 위치(위치 순). 셋 이상이면 선이 거미줄이 되므로 점선 없이
         * 터널만 강조한다. 클럭 넷은 데이터 흐름이 아니라서 잇지 않는다.
         */
        public final List<List<Location>> tunnelLinks = new ArrayList<>();
        /** 닿은 넷의 터널(선명하게 다시 그리고 테두리). */
        public final Set<Component> tunnels = new LinkedHashSet<>();
        /** 시작 부품·선(강조 테두리). */
        public final Set<Component> origin = new LinkedHashSet<>();

        public boolean touches(Component c) {
            return tunnels.contains(c) || forwardParts.contains(c) || backwardParts.contains(c) || stops.contains(c) || origin.contains(c)
                    || inside.containsKey(c);
        }
    }

    private final Circuit top;
    private final Trace.Result forward;
    private final Trace.Result backward;
    private final Collection<? extends Component> origin;

    private Influence(Circuit top, Trace.Result forward, Trace.Result backward,
            Collection<? extends Component> origin) {
        this.top = top;
        this.forward = forward;
        this.backward = backward;
        this.origin = origin;
    }

    public Trace.Result forward() {
        return forward;
    }

    public Trace.Result backward() {
        return backward;
    }

    /** 가장 깊이 닿은 곳(건넌 부품 수). 키로 넓히고 좁힐 때 끝. */
    public int maxDepth() {
        int d = 0;
        for (Trace.Result r : new Trace.Result[] {forward, backward}) {
            if (r != null) {
                for (int x : r.depth.values()) {
                    d = Math.max(d, x);
                }
            }
        }
        return d;
    }

    /**
     * 부품들(부품의 출력에서 앞으로, 입력에서 뒤로)과 선들(그 넷에서)에서 시작한다. depth가 음수면 끝까지.
     */
    public static Influence of(Circuit top, Collection<? extends Component> start, Mode mode, boolean through,
            int depth) {
        return of(new Trace(), top, start, mode, through, depth);
    }

    private static Influence of(Trace t, Circuit top, Collection<? extends Component> start, Mode mode,
            boolean through, int depth) {
        List<Trace.Node> outs = new ArrayList<>();
        List<Trace.Node> ins = new ArrayList<>();
        for (Component c : sorted(start)) {
            if (c instanceof Wire) {
                Trace.Node n = t.node(top, (Wire) c);
                outs.add(n);
                ins.add(n);
                continue;
            }
            for (int i = 0; i < c.getEnds().size(); i++) {
                boolean out = c.getEnds().get(i).isOutput();
                boolean in = c.getEnds().get(i).isInput();
                Trace.Node n = t.node(top, c, i);
                if (out || c.getFactory() instanceof SubcircuitFactory && !in) {
                    outs.add(n);
                }
                if (in) {
                    ins.add(n);
                }
                if (Kinds.of(c).factory().equals("Tunnel") || Kinds.of(c).factory().equals("Splitter")) {
                    outs.add(n);
                    ins.add(n);
                }
            }
        }
        Trace.Options o = new Trace.Options();
        o.crossState = through;
        o.maxDepth = depth;
        Trace.Result f = mode == Mode.BACKWARD ? null : t.forward(outs, o);
        Trace.Result b = mode == Mode.FORWARD ? null : t.backward(ins, o);
        return new Influence(top, f, b, new ArrayList<>(start));
    }

    /** 두 부품 사이 경로: a에서 앞으로 닿고 b에서 뒤로도 닿는 넷·부품만. */
    public static Influence between(Circuit top, Component a, Component b, boolean through) {
        Trace t = new Trace(); // 같은 넷리스트여야 두 결과의 넷을 맞댈 수 있다
        Influence fa = of(t, top, Collections.singletonList(a), Mode.FORWARD, through, -1);
        Influence bb = of(t, top, Collections.singletonList(b), Mode.BACKWARD, through, -1);
        Trace.Result f = new Trace.Result();
        for (Trace.Node n : fa.forward.nets) {
            if (bb.backward.nets.contains(n)) {
                f.nets.add(n);
                f.depth.put(n, fa.forward.depth.get(n));
            }
        }
        for (Trace.Step s : fa.forward.steps) {
            if (f.nets.contains(s.from) && f.nets.contains(s.to)) {
                f.steps.add(s);
                f.components.add(s.via);
            }
        }
        if (bb.backward.stoppedAt.contains(a) || fa.forward.stoppedAt.contains(b)) {
            f.stoppedAt.add(b);
        }
        List<Component> ends = new ArrayList<>();
        ends.add(a);
        ends.add(b);
        return new Influence(top, f, null, ends);
    }

    /**
     * 회로 shown에서의 모습. shown이 맨 위 회로면 서브회로 안쪽은 부품 테두리와 "안 n곳"으로, shown이 서브회로면 그
     * 회로 안에서 닿은 넷(어느 인스턴스든)을 보인다.
     */
    public View view(Circuit shown) {
        View v = new View();
        for (Component c : origin) {
            v.origin.add(c);
        }
        fill(v, shown, forward, v.forwardWires, v.forwardParts);
        fill(v, shown, backward, v.backwardWires, v.backwardParts);
        // 터널 점선: 강조한 넷에 같은 이름 터널이 둘 이상이면
        Set<Integer> done = new HashSet<>();
        for (Trace.Result r : new Trace.Result[] {forward, backward}) {
            if (r == null) {
                continue;
            }
            for (Trace.Node n : r.nets) {
                if (n.circuit != shown || !done.add(n.net.id())) {
                    continue;
                }
                List<Location> tunnels = new ArrayList<>();
                boolean clock = false;
                for (Netlist.PortRef p : n.net.ports()) {
                    String f = Kinds.of(p.component).factory();
                    if (f.equals("Tunnel")) {
                        tunnels.add(p.location());
                        if (n.instances.isEmpty() || shown != top) {
                            v.tunnels.add(p.component);
                        }
                    }
                    clock |= f.equals("Clock");
                }
                if (tunnels.size() == 2 && !clock) {
                    tunnels.sort(java.util.Comparator.comparingInt(Location::getY).thenComparingInt(Location::getX));
                    v.tunnelLinks.add(tunnels);
                }
            }
        }
        v.tunnelLinks.sort(java.util.Comparator.comparingInt((List<Location> l) -> l.get(0).getY())
                .thenComparingInt(l -> l.get(0).getX()));
        return v;
    }

    private void fill(View v, Circuit shown, Trace.Result r, Set<Wire> wires, Set<Component> parts) {
        if (r == null) {
            return;
        }
        boolean isTop = shown == top;
        for (Trace.Node n : r.nets) {
            if (n.circuit == shown && (!isTop || n.instances.isEmpty())) {
                wires.addAll(n.net.wires());
                // 서브회로 안에서는 닿은 넷의 경계 핀도 부품으로(경로가 경계에서 끊겨 보이지 않게, ui-reviewer #246)
                if (!isTop) {
                    for (Netlist.PortRef p : n.net.ports()) {
                        if (Kinds.of(p.component).factory().equals("Pin")) {
                            parts.add(p.component);
                        }
                    }
                }
            }
        }
        Map<Component, Set<Component>> inside = new LinkedHashMap<>();
        for (Trace.Step s : r.steps) {
            if (s.kind != Trace.StepKind.COMPONENT) {
                if (s.kind == Trace.StepKind.INTO && s.from.circuit == shown
                        && (!isTop || s.from.instances.isEmpty())) {
                    parts.add(s.via);
                    inside.computeIfAbsent(s.via, k -> new LinkedHashSet<>());
                }
                continue;
            }
            Trace.Node at = s.from;
            if (at.circuit == shown && (!isTop || at.instances.isEmpty())) {
                parts.add(s.via);
            } else if (isTop && !at.instances.isEmpty()) {
                inside.computeIfAbsent(at.instances.get(0), k -> new LinkedHashSet<>()).add(s.via);
            }
        }
        for (Map.Entry<Component, Set<Component>> e : inside.entrySet()) {
            v.inside.merge(e.getKey(), e.getValue().size(), Math::max);
        }
        for (Component c : r.stoppedAt) {
            if (shown.contains(c)) {
                v.stops.add(c);
            }
        }
        // 멈춘 부품의 포트에 닿은 넷에서 온 부품도 부품으로(입력만 닿은 레지스터 등)
        for (Component c : r.components) {
            if (shown.contains(c) && !(c.getFactory() instanceof SubcircuitFactory) && !r.stoppedAt.contains(c)
                    && isTop) {
                parts.add(c);
            }
        }
    }

    private static List<Component> sorted(Collection<? extends Component> cs) {
        List<Component> ret = new ArrayList<>(cs);
        ret.sort(java.util.Comparator.<Component>comparingInt(c -> c.getLocation().getY())
                .thenComparingInt(c -> c.getLocation().getX())
                .thenComparing(c -> c.getFactory().getName()));
        return ret;
    }
}
