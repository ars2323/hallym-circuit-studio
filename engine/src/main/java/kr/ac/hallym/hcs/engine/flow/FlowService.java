/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.flow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.cycle.ActiveBranches;
import kr.ac.hallym.hcs.app.flow.SignalFlowPath;
import kr.ac.hallym.hcs.app.flow.ValueSource;
import kr.ac.hallym.hcs.app.model.Influence;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.probe.QuickProbe;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.doc.Files;
import kr.ac.hallym.hcs.engine.model.ModelJson;
import kr.ac.hallym.hcs.engine.rpc.Params;
import kr.ac.hallym.hcs.engine.rpc.RpcError;
import kr.ac.hallym.hcs.engine.rpc.Server;
import kr.ac.hallym.hcs.engine.sim.SimSession;

/**
 * 캔버스 덧그림의 자료(N-15, D-151): 영향 경로({@code trace.influence}, v1 P-01), 넷 정보({@code trace.net}, v1 B-09),
 * Signal Flow({@code flow.path}, v1 P-07·V-06), 활성 경로({@code flow.activePath}, v1 C-08·V-04). 모두 v1의 GUI 없는
 * 코드({@link Influence}, {@link SignalFlowPath}, {@link ActiveBranches}, 공용 연결 엔진 {@link Netlist}·Trace)를 그대로
 * 부르고 결과를 규약 JSON으로 옮길 뿐이다. 회로 모델도 시뮬레이션 상태도 바꾸지 않는다: 값은 화면이 보고 있는 회로
 * 상태({@code sim.watch})에서 읽기만 하고, 서브회로 안 상태가 아직 없으면 만들지 않는다.
 */
public final class FlowService {
    private final Files files;
    private final Function<Doc, SimSession> sims;

    private FlowService(Files files, Function<Doc, SimSession> sims) {
        this.files = files;
        this.sims = sims;
    }

    public static void register(Server server, Files files, Function<Doc, SimSession> sims) {
        FlowService f = new FlowService(files, sims);
        server.register("trace.influence", (p, call) -> f.influence(p));
        server.register("trace.net", (p, call) -> f.net(p));
        server.register("flow.path", (p, call) -> f.path(p));
        server.register("flow.activePath", (p, call) -> f.activePath(p));
    }

    // ---- trace.influence ----

    JsonObject influence(Params p) throws RpcError {
        Doc d = files.get(p.str("fileId"));
        Circuit c = d.circuit(p.str("circuitId"));
        List<Component> from = d.components(c, p.strings("from"));
        if (from.isEmpty()) {
            throw RpcError.params("'from' must name at least one component or wire");
        }
        String mode = p.str("mode");
        boolean through = p.optBool("throughRegisters", false);
        int depth = p.optInt("depth", -1);
        if (depth < -1 || depth == 0) {
            throw RpcError.params("depth must be -1 (all) or from 1");
        }
        Influence inf;
        int max;
        if (mode.equals("between")) {
            if (from.size() != 2 || from.get(0) instanceof Wire || from.get(1) instanceof Wire) {
                throw RpcError.params("'between' needs two components");
            }
            inf = Influence.between(c, from.get(0), from.get(1), through);
            max = inf.maxDepth();
            depth = -1;
        } else {
            Influence.Mode m;
            switch (mode) {
            case "forward":
                m = Influence.Mode.FORWARD;
                break;
            case "backward":
                m = Influence.Mode.BACKWARD;
                break;
            case "both":
                m = Influence.Mode.BOTH;
                break;
            default:
                throw RpcError.params("mode must be forward, backward, both or between");
            }
            Influence full = Influence.of(c, from, m, through, -1);
            max = full.maxDepth();
            if (depth >= max) {
                depth = -1; // v1: 끝을 넘으면 끝까지
            }
            inf = depth < 0 ? full : Influence.of(c, from, m, through, depth);
        }
        Influence.View v = inf.view(c);
        JsonObject o = new JsonObject();
        o.addProperty("mode", mode);
        o.addProperty("depth", depth);
        o.addProperty("maxDepth", max);
        o.addProperty("throughRegisters", through);
        o.add("forward", side(d, v.forwardWires, v.forwardParts));
        o.add("backward", side(d, v.backwardWires, v.backwardParts));
        o.add("stops", ids(d, v.stops));
        o.add("origin", ids(d, v.origin));
        JsonArray inside = new JsonArray();
        for (Map.Entry<Component, Integer> e : v.inside.entrySet()) {
            JsonObject x = new JsonObject();
            x.addProperty("componentId", d.ids().of(e.getKey()));
            x.addProperty("name", e.getKey().getFactory().getName());
            x.addProperty("places", e.getValue());
            inside.add(x);
        }
        o.add("inside", inside);
        o.add("tunnels", ids(d, byPlace(v.tunnels))); // 넷의 포트 차례(해시 차례)에 기대지 않게 자리 순
        JsonArray links = new JsonArray();
        for (List<Location> link : v.tunnelLinks) {
            JsonArray l = new JsonArray();
            for (Location at : link) {
                l.add(ModelJson.point(at));
            }
            links.add(l);
        }
        o.add("links", links);
        return o;
    }

    /** 위→아래, 왼쪽→오른쪽, 부품 이름 순(같은 회로면 늘 같은 차례). */
    static List<Component> byPlace(Collection<? extends Component> cs) {
        List<Component> out = new ArrayList<>(cs);
        out.sort(java.util.Comparator.<Component>comparingInt(c -> c.getLocation().getY())
                .thenComparingInt(c -> c.getLocation().getX()).thenComparing(c -> c.getFactory().getName()));
        return out;
    }

    /** 포트 차례(v1 Trace.PORT_ORDER): 자리(y, x), 부품 이름, 포트 번호. */
    static final java.util.Comparator<Netlist.PortRef> PORT_ORDER = java.util.Comparator
            .<Netlist.PortRef>comparingInt(p -> p.location().getY()).thenComparingInt(p -> p.location().getX())
            .thenComparing(p -> p.component.getFactory().getName()).thenComparingInt(p -> p.end);

    private static JsonObject side(Doc d, Collection<Wire> wires, Collection<Component> parts) {
        JsonObject o = new JsonObject();
        o.add("wires", ids(d, wires));
        o.add("parts", ids(d, parts));
        return o;
    }

    private static JsonArray ids(Doc d, Collection<? extends Component> cs) {
        JsonArray a = new JsonArray();
        for (Component c : cs) {
            a.add(d.ids().of(c));
        }
        return a;
    }

    // ---- trace.net ----

    JsonObject net(Params p) throws RpcError {
        Doc d = files.get(p.str("fileId"));
        Circuit c = d.circuit(p.str("circuitId"));
        Netlist nl = d.tracker().netlist(c);
        Netlist.Net net = null;
        if (p.has("wire")) {
            Component w = d.component(c, p.str("wire"));
            if (!(w instanceof Wire)) {
                throw RpcError.params("'wire' must be a wire id");
            }
            net = nl.netOf((Wire) w);
        } else {
            String id = p.str("netId");
            for (Netlist.Net n : nl.nets()) {
                if (ModelJson.netId(n).equals(id)) {
                    net = n;
                }
            }
            if (net == null) {
                throw RpcError.notFound("net", id);
            }
        }
        JsonObject o = new JsonObject();
        o.addProperty("netId", ModelJson.netId(net));
        o.addProperty("width", ModelJson.netWidth(c, net));
        o.addProperty("name", QuickProbe.netName(c, net));
        List<Netlist.PortRef> drivers = new ArrayList<>(net.drivers());
        List<Netlist.PortRef> readers = new ArrayList<>(net.readers());
        List<Netlist.PortRef> others = new ArrayList<>();
        for (Netlist.PortRef r : net.ports()) {
            if (!drivers.contains(r) && !readers.contains(r)) {
                others.add(r);
            }
        }
        // 넷의 포트 차례는 해시 차례를 따를 수 있다: 자리 순으로(같은 회로면 같은 글)
        drivers.sort(PORT_ORDER);
        readers.sort(PORT_ORDER);
        others.sort(PORT_ORDER);
        o.add("drivers", ports(d, c, drivers));
        o.add("readers", ports(d, c, readers));
        o.add("others", ports(d, c, others));
        return o;
    }

    private static JsonArray ports(Doc d, Circuit c, List<Netlist.PortRef> refs) {
        JsonArray a = new JsonArray();
        for (Netlist.PortRef r : refs) {
            JsonObject o = new JsonObject();
            o.addProperty("componentId", d.ids().of(r.component));
            o.addProperty("port", r.end);
            o.addProperty("text", Names.portTitle(c, r.component, r.end)); // v1 넷 정보와 같은 글(공용 식별자)
            a.add(o);
        }
        return a;
    }

    // ---- flow.path ----

    JsonObject path(Params p) throws RpcError {
        Doc d = files.get(p.str("fileId"));
        Circuit c = d.circuit(p.str("circuitId"));
        SignalFlowPath.Options o = new SignalFlowPath.Options();
        o.backward = p.optBool("backward", false);
        o.throughRegisters = p.optBool("throughRegisters", false);
        if (p.optBool("activePathOnly", false)) {
            o.activeValues = values(d, c);
        }
        SignalFlowPath path;
        if (p.has("wire")) {
            Component w = d.component(c, p.str("wire"));
            if (!(w instanceof Wire)) {
                throw RpcError.params("'wire' must be a wire id");
            }
            int[] at = p.optPoint("at");
            path = SignalFlowPath.fromWire(c, (Wire) w, at == null ? null : Location.create(at[0], at[1]), o);
        } else {
            Component comp = d.component(c, p.str("componentId"));
            if (comp instanceof Wire) {
                throw RpcError.params("'componentId' must be a component, not a wire (use 'wire')");
            }
            int port = p.optInt("port", -1);
            if (port < -1 || port >= comp.getEnds().size()) {
                throw RpcError.params("no port " + port + " on " + d.ids().of(comp));
            }
            path = SignalFlowPath.fromComponent(c, comp, port, o);
        }
        return flowJson(d, path);
    }

    /** 화면이 보고 있는 회로 상태(sim.watch)가 이 회로면 그 값을, 아니면 값을 모른다(모든 가지, "?"). */
    private ValueSource values(Doc d, Circuit c) {
        SimSession s = sims.apply(d);
        CircuitState st = s == null ? null : s.watchedState(c);
        return st == null ? (instances, at) -> null : readOnly(st);
    }

    /**
     * 서브회로 안 값을 읽되 상태를 만들지 않는다(v1 {@code ValueSource.of}는 원조 getSubstate로 내려가 없으면 만든다):
     * 인스턴스의 데이터(원조가 이미 붙인 하위 상태)만 따라간다.
     */
    static ValueSource readOnly(CircuitState top) {
        return (instances, at) -> {
            CircuitState s = top;
            for (Component inst : instances) {
                if (s == null || !(inst.getFactory() instanceof SubcircuitFactory)) {
                    return null;
                }
                Object data = s.getData(inst);
                s = data instanceof CircuitState ? (CircuitState) data : null;
            }
            return s == null ? null : s.getValue(at);
        };
    }

    static JsonObject flowJson(Doc d, SignalFlowPath p) {
        JsonObject o = new JsonObject();
        o.addProperty("circuitId", d.ids().of(p.top));
        o.addProperty("backward", p.backward);
        o.addProperty("total", round(p.total));
        if (p.click != null) {
            o.add("click", ModelJson.point(p.click));
        }
        JsonArray segs = new JsonArray();
        for (SignalFlowPath.Segment s : p.segments) {
            JsonObject x = where(d, s.instances, s.circuit);
            x.add("from", ModelJson.point(s.from));
            x.add("to", ModelJson.point(s.to));
            x.addProperty("start", round(s.start));
            x.addProperty("length", round(s.length));
            x.addProperty("width", s.width);
            x.addProperty("cycle", s.cycle);
            segs.add(x);
        }
        o.add("segments", segs);
        JsonArray jumps = new JsonArray();
        for (SignalFlowPath.Jump j : p.jumps) {
            JsonObject x = where(d, j.instances, j.circuit);
            x.add("from", ModelJson.point(j.from));
            x.add("to", ModelJson.point(j.to));
            x.addProperty("start", round(j.start));
            jumps.add(x);
        }
        o.add("jumps", jumps);
        JsonArray passes = new JsonArray();
        for (SignalFlowPath.Pass x : p.passes) {
            JsonObject y = where(d, x.instances, x.circuit);
            y.addProperty("componentId", d.ids().of(x.component));
            y.addProperty("name", x.component.getFactory().getName());
            y.addProperty("time", round(x.time));
            y.addProperty("boundary", x.boundary);
            y.addProperty("cycle", x.cycle);
            passes.add(y);
        }
        o.add("passes", passes);
        JsonArray ends = new JsonArray();
        for (SignalFlowPath.Endpoint e : p.endpoints) {
            JsonObject y = where(d, e.instances, e.circuit);
            y.addProperty("componentId", d.ids().of(e.component));
            y.addProperty("port", e.end);
            y.add("at", ModelJson.point(e.at));
            y.addProperty("time", round(e.time));
            y.addProperty("kind", e.kind.name().toLowerCase());
            y.addProperty("label", e.label);
            ends.add(y);
        }
        o.add("endpoints", ends);
        o.add("loops", ids(d, p.loops));
        o.add("undetermined", ids(d, p.undetermined));
        return o;
    }

    private static JsonObject where(Doc d, List<Component> instances, Circuit circuit) {
        JsonObject x = new JsonObject();
        JsonArray path = new JsonArray();
        for (Component c : instances) {
            path.add(d.ids().of(c));
        }
        x.add("path", path);
        x.addProperty("circuitId", d.ids().of(circuit));
        return x;
    }

    /** 거리·시각은 회로 단위, 소수 한 자리까지(같은 경로면 같은 글자). */
    static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    // ---- flow.activePath ----

    JsonObject activePath(Params p) throws RpcError {
        Doc d = files.get(p.str("fileId"));
        Circuit c = d.circuit(p.str("circuitId"));
        SimSession s = sims.apply(d);
        CircuitState st = s == null ? null : s.watchedState(c);
        JsonObject o = new JsonObject();
        o.addProperty("circuitId", d.ids().of(c));
        JsonArray muxes = new JsonArray();
        if (st != null) {
            for (ActiveBranches.Branch b : ActiveBranches.of(c, st)) {
                JsonObject m = new JsonObject();
                m.addProperty("componentId", d.ids().of(b.mux));
                m.addProperty("input", b.input);
                JsonArray segs = new JsonArray();
                for (Location[] seg : b.segments) {
                    JsonArray pair = new JsonArray();
                    pair.add(ModelJson.point(seg[0]));
                    pair.add(ModelJson.point(seg[1]));
                    segs.add(pair);
                }
                m.add("segments", segs);
                muxes.add(m);
            }
        }
        o.add("muxes", muxes);
        o.addProperty("watched", st != null);
        return o;
    }
}
