/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.model.Netlist;

/**
 * 회로 모델을 규약의 JSON으로 적는다(docs/engine-api.md 5절 Snapshot·Component). 속성 값은 .circ에 저장되는 글자
 * ({@link Attribute#toStandardString}) 그대로다. 넷은 앱의 {@link Netlist}(연결 탐색 엔진, GUI 없음)를 쓴다.
 */
public final class ModelJson {
    /** 위→아래, 왼쪽→오른쪽, 이름, id 순(같은 모델이면 같은 글자). */
    static final Comparator<JsonObject> ORDER = Comparator
            .<JsonObject>comparingInt(o -> at(o).get(1).getAsInt())
            .thenComparingInt(o -> at(o).get(0).getAsInt())
            .thenComparing(o -> o.has("name") ? o.get("name").getAsString() : "")
            .thenComparing(o -> o.get("id").getAsString());

    private final Ids ids;
    private final LogisimFile file;
    private final Map<ComponentFactory, String> libNames = new IdentityHashMap<>();

    public ModelJson(Ids ids, LogisimFile file) {
        this.ids = ids;
        this.file = file;
    }

    public Ids ids() {
        return ids;
    }

    /** 회로 하나의 전체 모습. */
    public JsonObject snapshot(Circuit c, Netlist nl) {
        JsonObject o = new JsonObject();
        o.addProperty("circuitId", ids.of(c));
        o.addProperty("name", c.getName());
        List<JsonObject> comps = new ArrayList<>();
        for (Component x : c.getNonWires()) {
            comps.add(component(c, x));
        }
        comps.sort(ORDER);
        List<JsonObject> wires = new ArrayList<>();
        for (Wire w : c.getWires()) {
            wires.add(wire(w));
        }
        wires.sort(ORDER);
        o.add("components", toArray(comps));
        o.add("wires", toArray(wires));
        o.add("nets", nets(c, nl));
        o.add("junctions", junctions(c));
        return o;
    }

    public JsonObject component(Component c) {
        return component(null, c);
    }

    /**
     * 부품 하나. owner(부품이 든 회로)가 이 파일의 회로면 학생이 직접 정한 확장 정보(터널 색, 스플리터 팔 이름,
     * hcs:ext)를 {@code ext}로 싣는다(N-12, D-150). 그 정보가 바뀌면 부품이 {@code model.changed}의 added로 다시 온다.
     */
    public JsonObject component(Circuit owner, Component c) {
        JsonObject o = new JsonObject();
        o.addProperty("id", ids.of(c));
        ComponentFactory f = c.getFactory();
        String lib = libraryOf(f);
        if (lib == null) {
            o.add("lib", JsonNull.INSTANCE);
        } else {
            o.addProperty("lib", lib);
        }
        o.addProperty("name", f.getName());
        o.add("loc", point(c.getLocation()));
        Bounds b = c.getBounds();
        JsonArray bounds = new JsonArray();
        bounds.add(b.getX());
        bounds.add(b.getY());
        bounds.add(b.getWidth());
        bounds.add(b.getHeight());
        o.add("bounds", bounds);
        AttributeSet as = c.getAttributeSet();
        JsonObject attrs = attrs(as);
        o.add("facing", attrs.has("facing") ? attrs.get("facing") : JsonNull.INSTANCE);
        o.add("attrs", attrs);
        JsonArray ports = new JsonArray();
        List<EndData> ends = c.getEnds();
        for (int i = 0; i < ends.size(); i++) {
            EndData e = ends.get(i);
            JsonObject p = new JsonObject();
            p.addProperty("i", i);
            p.add("loc", point(e.getLocation()));
            p.addProperty("width", e.getWidth().getWidth());
            p.addProperty("dir", e.isInput() && e.isOutput() ? "inout" : e.isOutput() ? "out" : "in");
            String name = portName(c, i);
            if (name != null) {
                p.addProperty("name", name);
            }
            ports.add(p);
        }
        o.add("ports", ports);
        if (f instanceof SubcircuitFactory) {
            Circuit sub = ((SubcircuitFactory) f).getSubcircuit();
            o.addProperty("subcircuit", ids.of(sub));
            // 화면이 인스턴스를 원조 모양대로 그리는 도형(N-05, D-137). 모양이 바뀌면 인스턴스가 바뀐 부품으로 온다
            o.add("appearance", AppearanceJson.of(sub));
        }
        JsonObject ext = ExtJson.of(file, owner, c);
        if (ext != null) {
            o.add("ext", ext);
        }
        return o;
    }

    public JsonObject wire(Wire w) {
        JsonObject o = new JsonObject();
        o.addProperty("id", ids.of(w));
        o.add("a", point(w.getEnd0()));
        o.add("b", point(w.getEnd1()));
        return o;
    }

    /** 넷 목록. id는 "n" + 넷리스트 번호(모델이 바뀔 때마다 다시 매긴다). */
    public JsonArray nets(Circuit c, Netlist nl) {
        JsonArray a = new JsonArray();
        for (Netlist.Net n : nl.nets()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", netId(n));
            o.addProperty("width", netWidth(c, n));
            JsonArray ws = new JsonArray();
            for (Wire w : n.wires()) {
                ws.add(ids.of(w));
            }
            o.add("wires", ws);
            JsonArray ps = new JsonArray();
            for (Netlist.PortRef p : n.ports()) {
                JsonArray pr = new JsonArray();
                pr.add(ids.of(p.component));
                pr.add(p.end);
                ps.add(pr);
            }
            o.add("ports", ps);
            a.add(o);
        }
        return a;
    }

    public static String netId(Netlist.Net n) {
        return "n" + n.id();
    }

    /** 넷의 폭: 선 묶음이 정한 폭, 없으면 포트 폭 중 가장 큰 것, 그것도 없으면 1. */
    public static int netWidth(Circuit c, Netlist.Net n) {
        Location at = point(n);
        int w = at == null ? 0 : c.getWidth(at).getWidth();
        if (w <= 0) {
            w = n.width();
        }
        return w <= 0 ? 1 : w;
    }

    /** 넷의 값을 읽을 자리: 첫 선의 끝, 선이 없으면 첫 포트. */
    public static Location point(Netlist.Net n) {
        if (!n.wires().isEmpty()) {
            return n.wires().get(0).getEnd0();
        }
        return n.ports().isEmpty() ? null : n.ports().get(0).location();
    }

    /** 연결점: 선 끝 가운데 세 갈래 이상(선·포트)이 만나는 점(원조가 점을 그리는 조건). */
    public static JsonArray junctions(Circuit c) {
        TreeSet<Location> points = new TreeSet<>(Comparator.comparingInt(Location::getY).thenComparingInt(Location::getX));
        for (Wire w : c.getWires()) {
            for (Location at : new Location[] {w.getEnd0(), w.getEnd1()}) {
                if (c.getComponents(at).size() > 2) {
                    points.add(at);
                }
            }
        }
        JsonArray a = new JsonArray();
        for (Location at : points) {
            a.add(point(at));
        }
        return a;
    }

    /** 속성들: 이름 → .circ에 저장되는 글자. 저장하지 않는 속성과 값이 없는 속성은 뺀다. */
    public static JsonObject attrs(AttributeSet as) {
        JsonObject o = new JsonObject();
        if (as == null) {
            return o;
        }
        for (Attribute<?> a : as.getAttributes()) {
            if (!as.isToSave(a)) {
                continue;
            }
            String v = text(as, a);
            if (v != null) {
                o.addProperty(a.getName(), v);
            }
        }
        return o;
    }

    @SuppressWarnings("unchecked")
    public static String text(AttributeSet as, Attribute<?> a) {
        Object v = as.getValue(a);
        return v == null ? null : ((Attribute<Object>) a).toStandardString(v);
    }

    /** 부품 팩토리가 든 라이브러리 이름. 이 파일의 회로(서브회로)면 null. */
    public String libraryOf(ComponentFactory f) {
        String name = libNames.get(f);
        if (name != null) {
            return name;
        }
        if (f instanceof SubcircuitFactory && file.contains(((SubcircuitFactory) f).getSubcircuit())) {
            return null;
        }
        for (Library lib : file.getLibraries()) {
            if (provides(lib, f)) {
                libNames.put(f, lib.getName());
                return lib.getName();
            }
        }
        return null;
    }

    /**
     * lib의 도구가 f를 만드는가. 원조 {@link Library#contains}는 도구마다 팩토리를 불러오는데(지연 로딩), 불러온
     * 도구는 다음 저장에서 {@code <tool>} 속성 묶음으로 적힌다(2.7.1 XmlWriter). 그러면 엔진이 연 것만으로 저장 결과가
     * 원조와 달라지므로 이미 불러온 팩토리만 비교한다. 부품이 있으면 그 팩토리는 이미 불러온 것이다.
     */
    static boolean provides(Library lib, ComponentFactory f) {
        for (Tool t : lib.getTools()) {
            if (t instanceof AddTool && ((AddTool) t).getFactory(false) == f) {
                return true;
            }
        }
        return false;
    }

    public static JsonArray point(Location at) {
        JsonArray a = new JsonArray();
        a.add(at.getX());
        a.add(at.getY());
        return a;
    }

    private static String portName(Component c, int end) {
        try {
            return Kinds.portName(c, end);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonArray at(JsonObject o) {
        return o.has("loc") ? o.getAsJsonArray("loc") : o.getAsJsonArray("a");
    }

    static JsonArray toArray(List<JsonObject> items) {
        JsonArray a = new JsonArray();
        for (JsonObject o : items) {
            a.add(o);
        }
        return a;
    }
}
