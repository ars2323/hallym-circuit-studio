/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.diag.Diagnostic;
import kr.ac.hallym.hcs.app.diag.DiagnosticSet;
import kr.ac.hallym.hcs.app.diag.DynamicCheck;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.model.ModelJson;
import kr.ac.hallym.hcs.engine.rpc.Server;

/**
 * 한 파일의 Messages(D-143). v1과 같은 {@link DiagnosticSet}(정적 검사, 기록 엔진이 적는 스텝의 동적 진단, 진동)을
 * 들고, 규약 JSON으로 적는다. 목록이 바뀌면(편집 뒤 정적 검사, 시뮬레이션 스텝의 동적 진단·진동) 화면 프레임에 묶어
 * {@code diag.changed}를 보낸다. 화면이 마지막으로 받은 목록(diag.list의 결과나 앞의 diag.changed)과 같으면 보내지
 * 않는다. 파일을 연 직후 화면이 아는 목록은 빈 목록이다.
 */
public final class DiagSession {
    /** 편집이 멈췄다고 보고 정적 검사를 다시 도는 시간(ms). 한 번의 의도 뒤에 곧, 이어지는 의도는 묶어서. */
    static final long STATIC_DELAY_MS = 120;
    private static final AtomicLong NEXT_ID = new AtomicLong();

    private final Doc doc;
    private final Server server;
    private final DiagnosticSet set;
    /** 동적 진단·진동이 바뀌었다(시뮬레이터 스레드가 세운다). */
    private volatile boolean dynamicChanged;
    private boolean staticDirty = true;
    private long editedAt;
    /** 화면이 마지막으로 받은 목록. */
    private JsonArray told = new JsonArray();
    /** 메시지 열쇠 → 안정된 id("d…"). 목록에서 사라진 열쇠는 잊는다. */
    private final Map<String, String> ids = new HashMap<>();

    /** 파일을 연 뒤 기록 엔진이 처음 상태(스텝 0)를 적기를 기다리는 한도(ms). */
    static final long FIRST_RECORD_MS = 2000;

    DiagSession(Doc doc, Server server) {
        this.doc = doc;
        this.server = server;
        this.set = new DiagnosticSet(doc.project(), () -> dynamicChanged = true);
        awaitFirstRecord();
    }

    /**
     * 기록 엔진(동적 진단이 읽는다)은 여기서 붙는데, 파일을 연 첫 전파는 그 전에 끝났을 수 있다. 그러면 다음 사건(첫
     * 틱)의 상태가 스텝 0이 되어 사이클 번호가 하나 밀린다. 한 번 더 전파를 요청하고 처음 상태가 적힐 때까지 짧게
     * 기다린다(파일을 여는 요청 안에서, v1은 창이 뜰 때 이미 붙어 있었다).
     */
    private void awaitFirstRecord() {
        com.cburch.logisim.circuit.Simulator sim = doc.project().getSimulator();
        kr.ac.hallym.hcs.app.record.Recorder rec = kr.ac.hallym.hcs.app.record.Recorder.of(doc.project());
        if (sim == null || !sim.isRunning()) {
            return;
        }
        sim.requestPropagate();
        long end = System.nanoTime() + FIRST_RECORD_MS * 1_000_000L;
        while (System.nanoTime() < end) {
            kr.ac.hallym.hcs.app.record.Recording r = rec.current();
            if (r != null && !r.isEmpty()) {
                return;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    void close() {
        set.detach();
    }

    /** 진단 모음(테스트). */
    public DiagnosticSet set() {
        return set;
    }

    void modelChanged() {
        staticDirty = true;
        editedAt = System.nanoTime();
    }

    /** diag.list: 지금 목록(정적 검사가 밀려 있으면 먼저 돈다). 화면이 이것을 받았다고 본다. */
    public JsonObject list() {
        if (staticDirty) {
            refreshStatic();
        }
        dynamicChanged = false;
        told = messages();
        JsonObject o = new JsonObject();
        o.addProperty("fileId", doc.id());
        o.add("messages", told.deepCopy());
        return o;
    }

    /** 지금 메시지 수(Create Submission의 점검, N-21): 화면이 받은 목록(told)은 건드리지 않는다. */
    public int count() {
        if (staticDirty) {
            refreshStatic();
            dynamicChanged = true; // 다음 프레임이 화면에 알린다(frame이 told와 견준다)
        }
        return messages().size();
    }

    private void refreshStatic() {
        staticDirty = false;
        set.refreshStatic();
    }

    /** 프레임마다(엔진 스레드). */
    void frame() {
        boolean check = dynamicChanged;
        if (staticDirty && System.nanoTime() - editedAt >= STATIC_DELAY_MS * 1_000_000L) {
            refreshStatic();
            check = true;
        }
        if (!check) {
            return;
        }
        dynamicChanged = false;
        JsonArray now = messages();
        if (!now.equals(told)) {
            told = now;
            JsonObject o = new JsonObject();
            o.addProperty("fileId", doc.id());
            o.add("messages", now.deepCopy());
            server.notify("diag.changed", o);
        }
    }

    /** 지금 목록을 규약 JSON으로. */
    JsonArray messages() {
        List<Diagnostic> list = set.list();
        JsonArray out = new JsonArray();
        Map<String, String> keep = new HashMap<>();
        Map<String, Integer> repeats = new HashMap<>();
        for (Diagnostic d : list) {
            if (gone(d)) {
                continue;
            }
            JsonObject m = message(d);
            String key = m.remove("key").getAsString();
            int k = repeats.merge(key, 1, Integer::sum);
            if (k > 1) {
                key = key + "#" + k; // 같은 열쇠가 둘이면(드물다) 차례로 가른다
            }
            String id = ids.get(key);
            if (id == null) {
                id = "d" + NEXT_ID.incrementAndGet();
            }
            keep.put(key, id);
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            for (Map.Entry<String, com.google.gson.JsonElement> e : m.entrySet()) {
                o.add(e.getKey(), e.getValue());
            }
            out.add(o);
        }
        ids.clear();
        ids.putAll(keep);
        return out;
    }

    /** 진단 하나(열쇠 "key"를 함께 두고, 부르는 쪽이 id로 바꾼다). */
    JsonObject message(Diagnostic d) {
        JsonObject m = new JsonObject();
        m.addProperty("code", d.kind.name());
        m.addProperty("kind", d.kind.dynamic() ? "dynamic" : "static");
        m.addProperty("severity", "error");
        String near = null;
        if (d.kind == Diagnostic.Kind.TUNNEL_UNPAIRED && !d.components.isEmpty()) {
            near = NearNames.forTunnel(d.circuit, d.components.get(0));
        }
        m.add("text", DiagText.both(d, near));
        if (near != null) {
            m.addProperty("near", near);
        }
        Circuit root = d.instances.isEmpty() ? d.circuit : parentOf(d.instances.get(0));
        JsonObject loc = new JsonObject();
        place(doc, loc, d.circuit, root == null ? d.circuit : root, d.instances, d.components, d.wires, d.location);
        if (d.kind.dynamic()) {
            loc.addProperty("cycle", DynamicCheck.cycleOf(d.step));
        }
        m.add("location", loc);
        Diagnostic.Spot spot = d.appeared();
        if (spot != null) {
            JsonObject a = new JsonObject();
            a.addProperty("circuitId", doc.ids().of(spot.circuit));
            Circuit r = spot.instances.isEmpty() ? spot.circuit : parentOf(spot.instances.get(0));
            a.addProperty("root", doc.ids().of(r == null ? spot.circuit : r));
            a.add("path", ids(doc, spot.instances));
            a.add("at", ModelJson.point(spot.at));
            String net = netAt(doc, spot.circuit, spot.at);
            if (net != null) {
                a.addProperty("netId", net);
            }
            m.add("appeared", a);
        }
        // 안정된 id의 열쇠: 종류, 회로, 경로, 부품·선, 자리. 문구(사이클 번호, 이름)는 바뀌어도 같은 메시지다
        StringBuilder key = new StringBuilder(d.kind.name()).append('|').append(loc.get("circuitId").getAsString())
                .append('|').append(loc.get("path")).append('|');
        List<String> cs = new ArrayList<>();
        for (Component c : d.components) {
            cs.add(doc.ids().of(c));
        }
        Collections.sort(cs);
        List<String> ws = new ArrayList<>();
        for (Wire w : d.wires) {
            ws.add(doc.ids().of(w));
        }
        Collections.sort(ws);
        key.append(cs).append('|').append(ws).append('|').append(d.location);
        m.addProperty("key", key.toString());
        return m;
    }

    /**
     * 진단이 가리키는 부품·선·서브회로 인스턴스 가운데 이제 모델에 없는 것이 있다: 편집한 뒤 아직 다시 보지 않은
     * 진단이다(정적 진단은 {@link #STATIC_DELAY_MS} 뒤에, 동적 진단은 기록이 새로 시작한 뒤에 다시 본다). 그 사이
     * 프레임에 보내면 사라진 부품에 새 id를 주어 화면이 모르는 id를 받고 id 표가 늘어난다. 그런 진단은 보내지 않는다.
     */
    private boolean gone(Diagnostic d) {
        for (Component c : d.components) {
            if (!inModel(d.circuit, c)) {
                return true;
            }
        }
        for (Wire w : d.wires) {
            if (!inModel(d.circuit, w)) {
                return true;
            }
        }
        Diagnostic.Spot spot = d.appeared();
        return !onPath(d.instances) || spot != null && !onPath(spot.instances);
    }

    /** c가 회로 circuit(진단의 회로, 라이브러리 회로일 수 있다)이나 이 파일의 회로 안에 있다. */
    private boolean inModel(Circuit circuit, Component c) {
        if (circuit.contains(c)) {
            return true;
        }
        for (Circuit x : doc.file().getCircuits()) {
            if (x.contains(c)) {
                return true;
            }
        }
        return false;
    }

    /** 서브회로 인스턴스 경로의 인스턴스마다 앞 인스턴스의 회로(맨 앞은 이 파일의 회로) 안에 있다. */
    private boolean onPath(List<Component> path) {
        Circuit cur = path.isEmpty() ? null : parentOf(path.get(0));
        for (Component inst : path) {
            if (cur == null || !cur.contains(inst) || !(inst.getFactory() instanceof SubcircuitFactory)) {
                return false;
            }
            cur = ((SubcircuitFactory) inst.getFactory()).getSubcircuit();
        }
        return true;
    }

    /** 부품 inst를 가진 회로(이 파일과 그 라이브러리의 회로 가운데). 없으면 null. */
    private Circuit parentOf(Component inst) {
        for (Circuit c : doc.file().getCircuits()) {
            if (c.contains(inst)) {
                return c;
            }
        }
        return null;
    }

    /**
     * 자리(location) 객체: circuitId(원인이 있는 회로), root·path(맨 위 회로에서 그 회로 인스턴스까지; 정적 진단은
     * 회로 정의라 빈 경로), components·wires·nets, at.
     */
    static void place(Doc doc, JsonObject o, Circuit circuit, Circuit root, List<Component> path,
            List<Component> comps, List<Wire> wires, Location at) {
        o.addProperty("circuitId", doc.ids().of(circuit));
        o.addProperty("root", doc.ids().of(root));
        o.add("path", ids(doc, path));
        JsonArray c = new JsonArray();
        for (Component x : comps) {
            c.add(doc.ids().of(x));
        }
        o.add("components", c);
        JsonArray w = new JsonArray();
        Set<String> nets = new LinkedHashSet<>();
        Netlist nl = doc.tracker().netlist(circuit);
        for (Wire x : wires) {
            w.add(doc.ids().of(x));
            Netlist.Net n = nl.netOf(x);
            if (n != null) {
                nets.add(ModelJson.netId(n));
            }
        }
        o.add("wires", w);
        if (at != null) {
            String n = netAt(doc, circuit, at);
            if (n != null) {
                nets.add(n);
            }
        }
        JsonArray ns = new JsonArray();
        nets.forEach(ns::add);
        o.add("nets", ns);
        if (at != null) {
            o.add("at", ModelJson.point(at));
        } else {
            o.add("at", com.google.gson.JsonNull.INSTANCE);
        }
    }

    static JsonArray ids(Doc doc, List<Component> path) {
        JsonArray a = new JsonArray();
        for (Component x : path) {
            a.add(doc.ids().of(x));
        }
        return a;
    }

    /** 회로 c의 점 at에 닿은 넷(선 끝·선 위·포트). 없으면 null. */
    static String netAt(Doc doc, Circuit c, Location at) {
        Netlist.Net n = doc.tracker().netlist(c).netAt(at);
        return n == null ? null : ModelJson.netId(n);
    }

    /** 다른 넷리스트(추적기가 만든 것)의 넷을 이 파일의 넷 id로: 그 넷의 값 자리로 찾는다. */
    static String netId(Doc doc, Circuit c, Netlist.Net net) {
        Location at = ModelJson.point(net);
        String id = at == null ? null : netAt(doc, c, at);
        return id != null ? id : ModelJson.netId(net);
    }
}
