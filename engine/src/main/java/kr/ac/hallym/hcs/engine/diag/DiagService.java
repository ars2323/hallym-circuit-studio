/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.diag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.diag.OriginText;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.model.OriginTrace;
import kr.ac.hallym.hcs.app.model.Trace;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.doc.Files;
import kr.ac.hallym.hcs.engine.model.ModelJson;
import kr.ac.hallym.hcs.engine.rpc.RpcError;
import kr.ac.hallym.hcs.engine.rpc.Server;

/**
 * 진단(Messages)과 E/X 출처(D-143, docs/engine-api.md "diag·trace"): {@code diag.list}, {@code diag.changed},
 * {@code trace.origin}. 파일마다 {@link DiagSession} 하나. 모두 엔진 스레드에서 돈다(동적 진단을 찾는 일만 원조
 * 시뮬레이터 스레드에서 하고 표시만 넘긴다).
 */
public final class DiagService {
    private final Server server;
    private final Files files;
    private final Map<String, DiagSession> sessions = new HashMap<>();

    public DiagService(Server server, Files files) {
        this.server = server;
        this.files = files;
        server.register("diag.list", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            return session(d).list();
        });
        server.register("trace.origin", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            Circuit root = d.circuit(p.str("circuitId"));
            List<Component> path = path(d, root, p.optStrings("path"));
            return origin(d, root, path, p.str("netId"));
        });
    }

    /** 파일을 열었다(새 파일 포함). */
    public void attach(Doc d) {
        sessions.put(d.id(), new DiagSession(d, server));
    }

    /** 파일을 닫는다. */
    public void detach(Doc d) {
        DiagSession s = sessions.remove(d.id());
        if (s != null) {
            s.close();
        }
    }

    /** 편집·되돌리기 뒤(model.changed를 보낸 뒤): 정적 진단을 곧 다시 돈다. */
    public void modelChanged(Doc d) {
        DiagSession s = sessions.get(d.id());
        if (s != null) {
            s.modelChanged();
        }
    }

    /** 화면 프레임마다: 바뀐 목록이 있으면 diag.changed. */
    public void frame() {
        for (DiagSession s : sessions.values()) {
            try {
                s.frame();
            } catch (RuntimeException e) {
                server.log("error", "diag frame: " + e, false);
            }
        }
    }

    public void closeAll() {
        for (DiagSession s : sessions.values()) {
            s.close();
        }
        sessions.clear();
    }

    /** 파일의 진단(테스트). */
    public DiagSession session(Doc d) {
        DiagSession s = sessions.get(d.id());
        if (s == null) {
            s = new DiagSession(d, server);
            sessions.put(d.id(), s);
        }
        return s;
    }

    /** root에서 인스턴스 id들을 따라 내려간 경로(sim.watch와 같은 규칙). 없으면 오류 1. */
    static List<Component> path(Doc d, Circuit root, List<String> ids) throws RpcError {
        List<Component> path = new ArrayList<>();
        Circuit cur = root;
        for (String id : ids) {
            Component inst = d.ids().component(id);
            if (inst == null || !cur.contains(inst) || !(inst.getFactory() instanceof SubcircuitFactory)) {
                throw RpcError.notFound("instance", id);
            }
            path.add(inst);
            cur = ((SubcircuitFactory) inst.getFactory()).getSubcircuit();
        }
        return path;
    }

    static Circuit end(Circuit root, List<Component> path) {
        return path.isEmpty() ? root
                : ((SubcircuitFactory) path.get(path.size() - 1).getFactory()).getSubcircuit();
    }

    /**
     * trace.origin: 보이는 상태(맨 위 root, 경로 path 안)에서 넷 netId의 E·X가 처음 생긴 곳(v1 FindOrigin, D-01).
     * 사이클 뷰가 지난 사이클을 보이면 그 상태다(기록기가 상태를 바꿔 끼운다).
     */
    JsonObject origin(Doc d, Circuit root, List<Component> path, String netId) throws RpcError {
        Circuit c = end(root, path);
        Netlist nl = d.tracker().netlist(c);
        Netlist.Net net = null;
        for (Netlist.Net n : nl.nets()) {
            if (ModelJson.netId(n).equals(netId)) {
                net = n;
            }
        }
        if (net == null) {
            throw RpcError.notFound("net", netId);
        }
        Location at = ModelJson.point(net);
        CircuitState rootState = d.project().getCircuitState(root);
        OriginTrace t = new OriginTrace(root, OriginTrace.live(rootState));
        Trace.Node start = at == null ? null : t.node(path, c, at);
        OriginTrace.Origin o = start == null ? null : t.find(start, 0);
        JsonObject out = new JsonObject();
        out.addProperty("found", o != null);
        if (o == null) {
            out.add("text", DiagText.both("origin.none"));
            out.add("chain", new JsonArray());
            return out;
        }
        JsonObject origin = new JsonObject();
        origin.addProperty("cause", o.cause.name());
        origin.addProperty("value", o.isError() ? "E" : "x");
        kr.ac.hallym.hcs.app.diag.Diagnostic.Text cause = OriginText.causeText(root, o);
        JsonObject text = new JsonObject();
        text.addProperty("ko", DiagText.render(DiagText.KO, cause.key, cause.args().toArray()));
        text.addProperty("en", DiagText.render(DiagText.EN, cause.key, cause.args().toArray()));
        origin.add("text", text);
        DiagSession.place(d, origin, o.node.circuit, root, o.node.instances, OriginText.components(o),
                OriginText.wires(o), OriginText.location(o));
        out.add("origin", origin);
        JsonArray chain = new JsonArray();
        for (Trace.Node n : o.chain) {
            JsonObject step = new JsonObject();
            step.addProperty("circuitId", d.ids().of(n.circuit));
            step.add("path", DiagSession.ids(d, n.instances));
            step.addProperty("netId", DiagSession.netId(d, n.circuit, n.net));
            chain.add(step);
        }
        out.add("chain", chain);
        return out;
    }
}
