/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;

import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.model.Trace;

/**
 * 신호 흐름 경로(P-07). 누른 부품·선에서 신호가 가는 길을 영향 경로와 같은 엔진({@link Trace})으로 구하고, 애니메이션이
 * 쓸 모양(선 구간과 시작 시각, 터널 점프, 서브회로 경계, 끝점)으로 바꾼다. GUI와 무관하다.
 * <p>
 * 시각은 회로 좌표 거리 단위다: 선을 따라 간 길이에, 부품을 지날 때 {@link #DWELL}, 터널 점프에 {@link #JUMP},
 * 서브회로 경계에 {@link #BOUNDARY}를 더한다. 화면에서는 배율을 곱해 화면 px로 바꾸므로 흐름 속도는 배율과 무관하다.
 * 뒤로(Backward) 구한 경로도 흐름은 실제 신호 방향이다: 출처에서 출발해 누른 곳에 도착한다.
 */
public final class SignalFlowPath {
    /** 부품을 지날 때 머무는 거리. */
    public static final double DWELL = 30;
    /** 같은 이름 터널로 건너뛰는 거리. */
    public static final double JUMP = 30;
    /** 서브회로 경계를 넘는 거리. */
    public static final double BOUNDARY = 20;
    /** 스플리터를 지나는 거리. */
    public static final double SPLIT = 10;

    /** 선 구간(신호 방향, from → to). */
    public static final class Segment {
        public final List<Component> instances;
        public final Circuit circuit;
        public final Location from;
        public final Location to;
        public final double start;
        public final double length;
        /** 이 구간 넷의 폭(비트). */
        public final int width;
        /** 상태 부품을 넘은 수(1 이상이면 다음 사이클 경로: 점선 흐름). */
        public final int cycle;

        Segment(List<Component> instances, Circuit circuit, Location from, Location to, double start, int width,
                int cycle) {
            this.instances = instances;
            this.circuit = circuit;
            this.from = from;
            this.to = to;
            this.start = start;
            this.length = Math.abs(from.getX() - to.getX()) + Math.abs(from.getY() - to.getY());
            this.width = width;
            this.cycle = cycle;
        }

        public double end() {
            return start + length;
        }

        @Override
        public String toString() {
            return from + "->" + to + "@" + Math.round(start);
        }
    }

    /** 같은 이름 터널 점프(점선 호). */
    public static final class Jump {
        public final List<Component> instances;
        public final Circuit circuit;
        public final Location from;
        public final Location to;
        public final double start;

        Jump(List<Component> instances, Circuit circuit, Location from, Location to, double start) {
            this.instances = instances;
            this.circuit = circuit;
            this.from = from;
            this.to = to;
            this.start = start;
        }

        @Override
        public String toString() {
            return "jump " + from + "->" + to + "@" + Math.round(start);
        }
    }

    /** 부품을 지난 때(외곽선이 잠깐 빛남). 서브회로 부품이면 경계(들어감·나옴). */
    public static final class Pass {
        public final List<Component> instances;
        public final Circuit circuit;
        public final Component component;
        public final double time;
        public final boolean boundary;
        /** 상태 부품을 넘은 수(다음 사이클 경로 표시). */
        public final int cycle;

        Pass(List<Component> instances, Circuit circuit, Component component, double time, boolean boundary,
                int cycle) {
            this.instances = instances;
            this.circuit = circuit;
            this.component = component;
            this.time = time;
            this.boundary = boundary;
            this.cycle = cycle;
        }
    }

    public enum EndKind {
        /** 출력 핀·프로브처럼 값을 받기만 하는 곳. */
        OUTPUT,
        /** 순차 부품의 입력(여기서 멈춤). */
        STATE,
        /** 아무 데도 이어지지 않은 포트. */
        UNCONNECTED,
        /** 뒤로: 입력 핀·상수·클럭 같은 출처. */
        SOURCE
    }

    /** 끝점(링과 짧은 라벨). */
    public static final class Endpoint {
        public final List<Component> instances;
        public final Circuit circuit;
        public final Component component;
        public final int end;
        public final Location at;
        public final double time;
        public final EndKind kind;
        public final String label;

        Endpoint(List<Component> instances, Circuit circuit, Component component, int end, Location at, double time,
                EndKind kind, String label) {
            this.instances = instances;
            this.circuit = circuit;
            this.component = component;
            this.end = end;
            this.at = at;
            this.time = time;
            this.kind = kind;
            this.label = label;
        }

        @Override
        public String toString() {
            return kind + " " + label + "@" + Math.round(time);
        }
    }

    /** 탐색 설정. */
    public static final class Options {
        public boolean backward;
        public boolean throughRegisters;
        /** null이 아니면 Active Path Only. */
        public ValueSource activeValues;
        /** 시작 넷에서 따라갈 비트(예: Instruction[25:21]). null이면 넷 전체. */
        public java.util.BitSet startBits;
    }

    public final Circuit top;
    public final boolean backward;
    public final List<Segment> segments = new ArrayList<>();
    public final List<Jump> jumps = new ArrayList<>();
    public final List<Pass> passes = new ArrayList<>();
    public final List<Endpoint> endpoints = new ArrayList<>();
    /** 조합 고리가 닫힌 부품(작은 고리 표시만, 진단은 하지 않음). */
    public final List<Component> loops = new ArrayList<>();
    /** Active Path Only에서 선택이 확정되지 않은 부품. */
    public final List<Component> undetermined = new ArrayList<>();
    /** 누른 점(선을 눌렀을 때). */
    public final Location click;
    /** 흐름이 모든 끝점에 닿는 시각. */
    public double total;

    private SignalFlowPath(Circuit top, boolean backward, Location click) {
        this.top = top;
        this.backward = backward;
        this.click = click;
    }

    // ---- 계산 ----

    /**
     * 부품 c(end가 0 이상이면 그 포트에서만, 음수면 앞으로는 모든 출력·뒤로는 모든 입력)에서 시작한다.
     */
    public static SignalFlowPath fromComponent(Circuit top, Component c, int end, Options o) {
        Trace t = new Trace();
        List<Trace.Node> starts = new ArrayList<>();
        Map<Trace.Node, List<Location>> entries = new LinkedHashMap<>();
        for (int i = 0; i < c.getEnds().size(); i++) {
            if (end >= 0 && i != end) {
                continue;
            }
            boolean out = c.getEnds().get(i).isOutput();
            boolean in = c.getEnds().get(i).isInput();
            boolean wanted = end >= 0 || (o.backward ? in : out);
            if (!wanted) {
                continue;
            }
            Trace.Node n = t.node(top, c, i);
            if (n == null || n.net == null) {
                continue;
            }
            starts.add(n);
            entries.computeIfAbsent(n, k -> new ArrayList<>()).add(c.getEnd(i).getLocation());
        }
        return build(t, top, starts, entries, o, null);
    }

    /** 선 w에서 시작한다. 앞으로는 그 넷의 드라이버에서 넷 전체를 덮으며 퍼지고, 누른 점은 따로 표시한다. */
    public static SignalFlowPath fromWire(Circuit top, Wire w, Location click, Options o) {
        Trace t = new Trace();
        Trace.Node n = t.node(top, w);
        Map<Trace.Node, List<Location>> entries = new LinkedHashMap<>();
        List<Location> at = new ArrayList<>();
        if (!o.backward) {
            for (Netlist.PortRef p : n.net.drivers()) {
                at.add(p.location());
            }
        }
        if (at.isEmpty()) {
            at.add(click != null ? nearestOn(w, click) : w.getEnd0());
        }
        entries.put(n, at);
        return build(t, top, Collections.singletonList(n), entries, o, click);
    }

    private static Location nearestOn(Wire w, Location p) {
        if (w.isVertical()) {
            int y0 = Math.min(w.getEnd0().getY(), w.getEnd1().getY());
            int y1 = Math.max(w.getEnd0().getY(), w.getEnd1().getY());
            return Location.create(w.getEnd0().getX(), Math.max(y0, Math.min(y1, p.getY() / 10 * 10)));
        }
        int x0 = Math.min(w.getEnd0().getX(), w.getEnd1().getX());
        int x1 = Math.max(w.getEnd0().getX(), w.getEnd1().getX());
        return Location.create(Math.max(x0, Math.min(x1, p.getX() / 10 * 10)), w.getEnd0().getY());
    }

    private static SignalFlowPath build(Trace t, Circuit top, List<Trace.Node> starts,
            Map<Trace.Node, List<Location>> entries, Options o, Location click) {
        SignalFlowPath path = new SignalFlowPath(top, o.backward, click);
        Trace.Options to = new Trace.Options();
        to.crossState = o.throughRegisters;
        to.startBits = o.startBits;
        ActivePath active = o.activeValues == null ? null : new ActivePath(o.activeValues);
        to.pass = active;
        Trace.Result r = o.backward ? t.backward(starts, to) : t.forward(starts, to);
        new Timing(t, r, path, entries, o).run();
        if (active != null) {
            path.undetermined.addAll(active.undetermined());
        }
        path.loops.addAll(r.loops);
        return path;
    }

    /** 넷마다 선을 따라 거리(탐색 방향)를 재고, 뒤로면 신호 방향으로 뒤집는다. */
    private static final class Timing {
        final Trace t;
        final Trace.Result r;
        final SignalFlowPath path;
        final Map<Trace.Node, List<Location>> startEntries;
        final Options o;
        /** 넷마다 점 → 거리(탐색 방향). */
        final Map<Trace.Node, Map<Location, Double>> dist = new HashMap<>();
        final Map<Trace.Node, Double> entryTime = new HashMap<>();
        final List<Segment> segs = new ArrayList<>();
        final List<Jump> jumps = new ArrayList<>();
        final List<Pass> passes = new ArrayList<>();
        /** 부품마다 한 번만 빛난다(여러 입력으로 닿아도). */
        final Set<String> passed = new HashSet<>();
        final List<Endpoint> ends = new ArrayList<>();

        Timing(Trace t, Trace.Result r, SignalFlowPath path, Map<Trace.Node, List<Location>> entries, Options o) {
            this.t = t;
            this.r = r;
            this.path = path;
            this.startEntries = entries;
            this.o = o;
        }

        void run() {
            // 넷마다 처음 닿은 걸음(Trace가 처리한 순서)
            Map<Trace.Node, Trace.Step> firstStep = new LinkedHashMap<>();
            for (Trace.Step s : r.steps) {
                firstStep.putIfAbsent(s.to, s);
            }
            for (Trace.Node n : r.nets) {
                Map<Location, Double> seeds = new LinkedHashMap<>();
                if (startEntries.containsKey(n) && !firstStep.containsKey(n)) {
                    for (Location l : startEntries.get(n)) {
                        seeds.put(l, 0.0);
                    }
                } else {
                    Trace.Step s = firstStep.get(n);
                    if (s == null) {
                        continue;
                    }
                    double base = arrival(s);
                    if (Double.isNaN(base)) {
                        continue;
                    }
                    for (Location l : entryPoints(s)) {
                        seeds.put(l, base + cost(s));
                    }
                    if ((s.kind == Trace.StepKind.COMPONENT || s.kind == Trace.StepKind.INTO
                            || s.kind == Trace.StepKind.OUT_OF)
                            && passed.add(key(s.from.instances, s.via, s.kind == Trace.StepKind.COMPONENT ? 0 : 1))) {
                        passes.add(new Pass(s.from.instances, s.from.circuit, s.via, base,
                                s.kind != Trace.StepKind.COMPONENT, s.cycle));
                    }
                }
                walkNet(n, seeds, r.cycle.getOrDefault(n, 0));
            }
            endpoints(firstStep);
            double total = 0;
            for (Segment s : segs) {
                total = Math.max(total, s.end());
            }
            for (Endpoint e : ends) {
                total = Math.max(total, e.time);
            }
            if (o.backward) {
                flip(total);
            }
            path.total = total;
            Comparator<Segment> bySeg = Comparator.<Segment>comparingDouble(s -> s.start)
                    .thenComparingInt(s -> s.instances.size()).thenComparingInt(s -> s.from.getY())
                    .thenComparingInt(s -> s.from.getX()).thenComparingInt(s -> s.to.getY())
                    .thenComparingInt(s -> s.to.getX());
            segs.sort(bySeg);
            jumps.sort(Comparator.<Jump>comparingDouble(j -> j.start).thenComparingInt(j -> j.from.getY())
                    .thenComparingInt(j -> j.from.getX()).thenComparingInt(j -> j.to.getY())
                    .thenComparingInt(j -> j.to.getX()));
            passes.sort(Comparator.<Pass>comparingDouble(p -> p.time).thenComparingInt(p -> p.instances.size())
                    .thenComparingInt(p -> p.component.getLocation().getY())
                    .thenComparingInt(p -> p.component.getLocation().getX())
                    .thenComparing(p -> p.component.getFactory().getName()));
            ends.sort(Comparator.<Endpoint>comparingDouble(e -> e.time).thenComparingInt(e -> e.instances.size())
                    .thenComparingInt(e -> e.at.getY()).thenComparingInt(e -> e.at.getX())
                    .thenComparing(e -> e.label));
            path.segments.addAll(segs);
            path.jumps.addAll(jumps);
            path.passes.addAll(passes);
            path.endpoints.addAll(ends);
        }

        /** 걸음이 시작 넷에서 건넌 부품 포트에 닿은 거리. */
        double arrival(Trace.Step s) {
            Map<Location, Double> d = dist.get(s.from);
            if (d == null) {
                return Double.NaN;
            }
            double best = Double.NaN;
            for (Location l : exitPoints(s)) {
                Double x = d.get(l);
                if (x != null && (Double.isNaN(best) || x < best)) {
                    best = x;
                }
            }
            return best;
        }

        double cost(Trace.Step s) {
            switch (s.kind) {
            case COMPONENT:
                return DWELL;
            case SPLITTER:
                return SPLIT;
            default:
                return BOUNDARY;
            }
        }

        /** 걸음이 시작 넷을 떠나는 점(건넌 부품의 포트 중 시작 넷에 닿은 것). */
        List<Location> exitPoints(Trace.Step s) {
            List<Location> ret = new ArrayList<>();
            switch (s.kind) {
            case COMPONENT:
            case SPLITTER:
                ret.addAll(portsOn(s.from, s.via));
                break;
            case INTO:
                ret.addAll(portsOn(s.from, s.via));
                break;
            case OUT_OF:
                // 안쪽 경계 핀
                for (Netlist.PortRef p : s.from.net.ports()) {
                    if (Kinds.of(p.component).factory().equals("Pin")) {
                        ret.add(p.location());
                    }
                }
                break;
            default:
                break;
            }
            return ret;
        }

        /** 걸음이 도착 넷에 들어오는 점. */
        List<Location> entryPoints(Trace.Step s) {
            List<Location> ret = new ArrayList<>();
            switch (s.kind) {
            case COMPONENT:
            case SPLITTER:
            case OUT_OF:
                ret.addAll(portsOn(s.to, s.via));
                break;
            case INTO: {
                for (Netlist.PortRef p : s.to.net.ports()) {
                    if (Kinds.of(p.component).factory().equals("Pin")) {
                        ret.add(p.location());
                    }
                }
                break;
            }
            default:
                break;
            }
            return ret;
        }

        List<Location> portsOn(Trace.Node n, Component c) {
            List<Location> ret = new ArrayList<>();
            for (Netlist.PortRef p : n.net.ports()) {
                if (p.component == c) {
                    ret.add(p.location());
                }
            }
            return ret;
        }

        /** 한 넷 안에서 선을 따라 거리를 잰다(선 끝·포트가 마디, 같은 이름 터널은 점프). */
        void walkNet(Trace.Node n, Map<Location, Double> seeds, int cycle) {
            List<Wire> wires = new ArrayList<>(n.net.wires());
            wires.sort(Comparator.<Wire>comparingInt(w -> w.getEnd0().getY()).thenComparingInt(w -> w.getEnd0().getX())
                    .thenComparingInt(w -> w.getEnd1().getY()).thenComparingInt(w -> w.getEnd1().getX()));
            Set<Location> points = new HashSet<>(seeds.keySet());
            for (Wire w : wires) {
                points.add(w.getEnd0());
                points.add(w.getEnd1());
            }
            List<Location> tunnels = new ArrayList<>();
            for (Netlist.PortRef p : n.net.ports()) {
                points.add(p.location());
                if (Kinds.of(p.component).factory().equals("Tunnel")) {
                    tunnels.add(p.location());
                }
            }
            tunnels.sort(Comparator.comparingInt(Location::getY).thenComparingInt(Location::getX));
            // 선마다 그 위의 마디(끝점과 한가운데 포트)를 순서대로
            Map<Location, List<Location[]>> adj = new HashMap<>();
            for (Wire w : wires) {
                List<Location> on = new ArrayList<>();
                for (Location p : points) {
                    if (w.contains(p)) {
                        on.add(p);
                    }
                }
                on.sort(Comparator.comparingInt(Location::getY).thenComparingInt(Location::getX));
                for (int i = 1; i < on.size(); i++) {
                    Location a = on.get(i - 1);
                    Location b = on.get(i);
                    adj.computeIfAbsent(a, k -> new ArrayList<>()).add(new Location[] {a, b});
                    adj.computeIfAbsent(b, k -> new ArrayList<>()).add(new Location[] {b, a});
                }
            }
            Map<Location, Double> d = new HashMap<>();
            Map<Location, Location> via = new HashMap<>();
            Set<Location> jumpedFrom = new HashSet<>();
            PriorityQueue<Object[]> pq = new PriorityQueue<>(Comparator.<Object[]>comparingDouble(e -> (Double) e[1])
                    .thenComparingInt(e -> ((Location) e[0]).getY()).thenComparingInt(e -> ((Location) e[0]).getX()));
            for (Map.Entry<Location, Double> e : seeds.entrySet()) {
                pq.add(new Object[] {e.getKey(), e.getValue(), null});
            }
            List<Component> inst = n.instances;
            int width = Math.max(1, n.net.width());
            while (!pq.isEmpty()) {
                Object[] e = pq.poll();
                Location p = (Location) e[0];
                double x = (Double) e[1];
                if (d.containsKey(p)) {
                    continue;
                }
                d.put(p, x);
                Location from = (Location) e[2];
                if (from != null) {
                    if (e.length > 3 && Boolean.TRUE.equals(e[3])) {
                        jumps.add(new Jump(inst, n.circuit, from, p, x - JUMP));
                    } else {
                        segs.add(new Segment(inst, n.circuit, from, p, x - dist(from, p), width, cycle));
                    }
                }
                for (Location[] edge : adj.getOrDefault(p, Collections.emptyList())) {
                    if (!d.containsKey(edge[1])) {
                        pq.add(new Object[] {edge[1], x + dist(edge[0], edge[1]), p});
                    }
                }
                if (tunnels.contains(p) && jumpedFrom.add(p)) {
                    for (Location q : tunnels) {
                        if (!q.equals(p) && !d.containsKey(q)) {
                            pq.add(new Object[] {q, x + JUMP, p, Boolean.TRUE});
                        }
                    }
                }
            }
            dist.put(n, d);
        }

        static double dist(Location a, Location b) {
            return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
        }

        void endpoints(Map<Trace.Node, Trace.Step> firstStep) {
            Set<String> seen = new HashSet<>();
            Set<Trace.Node> goesOn = new HashSet<>();
            for (Trace.Step s : r.steps) {
                goesOn.add(s.from);
            }
            for (Trace.Node n : r.nets) {
                Map<Location, Double> d = dist.get(n);
                if (d == null) {
                    continue;
                }
                List<Netlist.PortRef> ports = new ArrayList<>(o.backward ? n.net.drivers() : n.net.readers());
                boolean continues = false;
                for (Netlist.PortRef p : ports) {
                    Component c = p.component;
                    String f = Kinds.of(c).factory();
                    if (f.equals("Splitter") || c.getFactory() instanceof SubcircuitFactory) {
                        continues = true;
                        continue;
                    }
                    Double x = d.get(p.location());
                    if (x == null) {
                        continue;
                    }
                    EndKind kind = null;
                    if (r.stoppedAt.contains(c)) {
                        kind = EndKind.STATE;
                    } else if (!hasOther(c, !o.backward)) {
                        boolean boundaryPin = f.equals("Pin") && !n.instances.isEmpty();
                        if (!boundaryPin) {
                            kind = o.backward ? EndKind.SOURCE : EndKind.OUTPUT;
                        } else {
                            continues = true;
                        }
                    } else {
                        continues = true;
                    }
                    if (kind != null && seen.add(key(n.instances, c, p.end))) {
                        ends.add(new Endpoint(n.instances, n.circuit, c, p.end, p.location(), x, kind,
                                label(n, c, p.end, kind)));
                    }
                }
                // 이어지는 곳이 없는 넷(드라이버만 있는 출력 등): 가장 먼 점을 끝점으로
                boolean tunnelsOnly = true;
                for (Netlist.PortRef p : n.net.ports()) {
                    tunnelsOnly &= Kinds.of(p.component).factory().equals("Tunnel");
                }
                if (ports.isEmpty() && !continues && !goesOn.contains(n) && !tunnelsOnly) {
                    Location far = null;
                    double best = -1;
                    for (Map.Entry<Location, Double> e : d.entrySet()) {
                        if (e.getValue() > best || e.getValue() == best && far != null
                                && (e.getKey().getY() < far.getY() || e.getKey().getY() == far.getY()
                                        && e.getKey().getX() < far.getX())) {
                            best = e.getValue();
                            far = e.getKey();
                        }
                    }
                    Trace.Step s = firstStep.get(n);
                    Component c = s != null ? s.via : null;
                    int end = -1;
                    for (Netlist.PortRef p : n.net.ports()) {
                        if (p.component == c) {
                            end = p.end;
                        }
                    }
                    if (far != null && c != null && seen.add("u" + key(n.instances, c, end) + far)) {
                        ends.add(new Endpoint(n.instances, n.circuit, c, end, far, best, EndKind.UNCONNECTED,
                                end >= 0 ? label(n, c, end, EndKind.UNCONNECTED)
                                        : Names.label(c) != null ? Names.label(c) : c.getFactory().getName()));
                    }
                }
            }
        }

        static boolean hasOther(Component c, boolean outputs) {
            for (int i = 0; i < c.getEnds().size(); i++) {
                boolean in = c.getEnds().get(i).isInput();
                boolean out = c.getEnds().get(i).isOutput();
                if (outputs ? out && !in : in && !out) {
                    return true;
                }
            }
            return false;
        }

        static String key(List<Component> inst, Component c, int end) {
            StringBuilder b = new StringBuilder();
            for (Component i : inst) {
                b.append(System.identityHashCode(i)).append('/');
            }
            return b.append(System.identityHashCode(c)).append('#').append(end).toString();
        }

        /** "PC (D)", "ALUResult", "regfile › WD": 학생 라벨을 먼저, 서브회로 안이면 경로를 붙인다. */
        String label(Trace.Node n, Component c, int end, EndKind kind) {
            String name = Names.label(c);
            String port = Kinds.of(c).factory().equals("Splitter") && end > 0 ? armBits(c, end - 1)
                    : Kinds.portName(c, end);
            String here;
            if (Kinds.of(c).factory().equals("Pin")) {
                here = name != null ? name : port;
            } else if (name != null) {
                here = port == null || port.isEmpty() || port.equals(name) ? name
                        : port.startsWith("[") ? name + " " + port : name + " (" + port + ")";
            } else if (port != null && port.startsWith("[")) {
                here = c.getFactory().getName() + " " + port; // 스플리터 팔: "Splitter [31:26]"
            } else {
                here = c.getFactory().getName() + (port == null || port.isEmpty() ? "" : " (" + port + ")");
            }
            if (n.instances.isEmpty()) {
                return here;
            }
            List<String> parts = new ArrayList<>();
            for (Component i : n.instances) {
                String l = Names.label(i);
                parts.add(l != null ? l : i.getFactory().getName());
            }
            parts.add(here);
            return Names.path(parts);
        }

        /** 스플리터 팔이 맡은 비트: "[31:26]"(여러 구간이면 쉼표). */
        static String armBits(Component splitter, int arm) {
            int[] arms = Netlist.splitterArms(splitter);
            List<String> parts = new ArrayList<>();
            int lo = -1;
            for (int i = 0; i <= arms.length; i++) {
                boolean in = i < arms.length && arms[i] == arm;
                if (in && lo < 0) {
                    lo = i;
                } else if (!in && lo >= 0) {
                    parts.add(0, i - 1 == lo ? Integer.toString(lo) : (i - 1) + ":" + lo);
                    lo = -1;
                }
            }
            return "[" + String.join(",", parts) + "]";
        }

        /** 뒤로: 탐색 거리 x를 신호 시각 total − x로, 구간 방향을 뒤집는다. */
        void flip(double total) {
            List<Segment> out = new ArrayList<>();
            for (Segment s : segs) {
                out.add(new Segment(s.instances, s.circuit, s.to, s.from, total - s.end(), s.width, s.cycle));
            }
            segs.clear();
            segs.addAll(out);
            List<Jump> js = new ArrayList<>();
            for (Jump j : jumps) {
                js.add(new Jump(j.instances, j.circuit, j.to, j.from, total - j.start - JUMP));
            }
            jumps.clear();
            jumps.addAll(js);
            List<Pass> ps = new ArrayList<>();
            for (Pass p : passes) {
                ps.add(new Pass(p.instances, p.circuit, p.component, total - p.time - DWELL, p.boundary, p.cycle));
            }
            passes.clear();
            passes.addAll(ps);
            List<Endpoint> es = new ArrayList<>();
            for (Endpoint e : ends) {
                es.add(new Endpoint(e.instances, e.circuit, e.component, e.end, e.at, total - e.time, e.kind,
                        e.label));
            }
            ends.clear();
            ends.addAll(es);
        }
    }

    // ---- 보기 ----

    /** 회로 shown(서브회로 경로 instances)에서 보일 구간. instances가 null이면 그 회로의 모든 인스턴스. */
    public List<Segment> segmentsIn(Circuit shown, List<Component> instances) {
        List<Segment> ret = new ArrayList<>();
        for (Segment s : segments) {
            if (s.circuit == shown && (instances == null || same(s.instances, instances))) {
                ret.add(s);
            }
        }
        return ret;
    }

    static boolean same(List<Component> a, List<Component> b) {
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

    /** 끝점 요약(테스트·기대값 파일): "종류 라벨" 목록, 시각 순. */
    public List<String> endpointSummary() {
        List<String> ret = new ArrayList<>();
        for (Endpoint e : endpoints) {
            ret.add(e.kind + " " + e.label);
        }
        return ret;
    }

    /** 결정성 확인용 전체 요약. */
    public String fingerprint() {
        StringBuilder b = new StringBuilder();
        for (Segment s : segments) {
            b.append(s).append(s.instances.size()).append(';');
        }
        for (Jump j : jumps) {
            b.append(j).append(';');
        }
        for (Endpoint e : endpoints) {
            b.append(e).append(';');
        }
        for (Pass p : passes) {
            b.append(p.component.getFactory().getName()).append(p.component.getLocation()).append('@')
                    .append(Math.round(p.time)).append(';');
        }
        return b.toString();
    }
}
