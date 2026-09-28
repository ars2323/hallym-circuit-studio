/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.find;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.find.NameIndex;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.model.ModelJson;

/**
 * 찾기(find.query, N-12, D-150). v1 Ctrl+F(D-036, #135, S-09)의 색인 {@link NameIndex}를 그대로 쓴다: 라벨·터널
 * 이름·서브회로 이름을 주 회로에서 서브회로 부품을 따라 내려가며(깊이 32) 모으고, 주 회로에서 닿지 않는 회로도
 * 넣는다. 대소문자 없는 부분 일치, 이름이 정확히 같은 것 먼저, 종류·글·경로가 같은 것은 한 묶음(위→아래, 왼쪽→오른쪽),
 * 묶음 안 자리마다 v1 위치 줄(붙은 포트 {@code next to main › PC (D)}, 없으면 {@code main › Tunnel #3}).
 * <p>
 * v2가 더한 것: 라벨이 붙은 핀은 {@code pin}으로 가르고, 부품을 원조 부품 이름({@code Register},
 * {@code Instruction Memory})으로도 찾는다({@code part}, 같은 회로 경로의 같은 종류가 한 묶음, 자리 글은 라벨이나
 * 번호 이름). 터널과 서브회로 부품은 이름으로 이미 찾으므로 {@code part}에 넣지 않는다. 색인은 부를 때마다 지금 모델로 새로 만든다(편집 뒤에도 맞다).
 * 모델을 읽기만 한다.
 */
public final class Find {
    /** 한 번에 돌려주는 묶음 수(넘으면 {@code more:true}). */
    public static final int LIMIT = 100;

    private Find() {
    }

    /** 찾은 자리 하나. */
    static final class Hit {
        final String kind;
        final String text;
        final String path;
        final Circuit top;
        final List<Component> instances;
        final Circuit circuit;
        final Component component;
        final NameIndex.Entry entry; // v1 색인의 항목(위치 줄), part는 null
        String key;                  // 차례 열쇠(walkKey), 정렬 앞에 한 번 만든다

        Hit(String kind, String text, String path, Circuit top, List<Component> instances, Circuit circuit,
                Component component, NameIndex.Entry entry) {
            this.kind = kind;
            this.text = text;
            this.path = path;
            this.top = top;
            this.instances = instances;
            this.circuit = circuit;
            this.component = component;
            this.entry = entry;
        }
    }

    public static JsonObject query(Doc d, String text, int limit) {
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        List<Hit> found = new ArrayList<>();
        if (!q.isEmpty()) {
            List<Hit> exact = new ArrayList<>();
            List<Hit> rest = new ArrayList<>();
            for (NameIndex.Entry e : NameIndex.of(d.file()).entries()) {
                String lower = e.text.toLowerCase(Locale.ROOT);
                if (!lower.contains(q)) {
                    continue;
                }
                String kind = e.kind == NameIndex.Kind.TUNNEL ? "tunnel"
                        : e.kind == NameIndex.Kind.SUBCIRCUIT ? "subcircuit"
                        : e.component.getFactory().getName().equals("Pin") ? "pin" : "label";
                Hit h = new Hit(kind, e.text, e.path, e.top, e.instances, e.circuit, e.component, e);
                (lower.equals(q) ? exact : rest).add(h);
            }
            for (Hit h : parts(d.file())) {
                String lower = h.text.toLowerCase(Locale.ROOT);
                if (lower.contains(q)) {
                    (lower.equals(q) ? exact : rest).add(h);
                }
            }
            // v1의 차례(주 회로부터 위→아래, 왼쪽→오른쪽, 인스턴스 안은 그 자리에서)를 같은 자리의 부품까지 정해진 차례로
            for (List<Hit> l : List.of(exact, rest)) {
                for (Hit h : l) {
                    h.key = walkKey(d.file(), h);
                }
            }
            Comparator<Hit> walk = Comparator.comparing(h -> h.key);
            exact.sort(walk);
            rest.sort(walk);
            found.addAll(exact);
            found.addAll(rest);
        }
        // 종류·글·경로가 같은 것을 한 묶음으로(처음 나온 차례), 묶음 안은 위→아래, 왼쪽→오른쪽(v1 NameIndex.group)
        Map<String, List<Hit>> groups = new LinkedHashMap<>();
        for (Hit h : found) {
            groups.computeIfAbsent(h.kind + "\u0000" + h.text + "\u0000" + h.path, k -> new ArrayList<>()).add(h);
        }
        JsonArray out = new JsonArray();
        int n = 0;
        for (List<Hit> g : groups.values()) {
            if (n++ >= limit) {
                break;
            }
            g.sort((a, b) -> a.component.getLocation().getY() != b.component.getLocation().getY()
                    ? a.component.getLocation().getY() - b.component.getLocation().getY()
                    : a.component.getLocation().getX() - b.component.getLocation().getX());
            Hit first = g.get(0);
            JsonObject o = new JsonObject();
            o.addProperty("kind", first.kind);
            o.addProperty("text", first.text);
            o.addProperty("path", first.path);
            JsonArray places = new JsonArray();
            for (Hit h : g) {
                places.add(place(d, h));
            }
            o.add("places", places);
            out.add(o);
        }
        JsonObject r = new JsonObject();
        r.addProperty("fileId", d.id());
        r.addProperty("text", text == null ? "" : text);
        r.add("groups", out);
        r.addProperty("more", groups.size() > limit);
        return r;
    }

    /**
     * 찾은 자리의 차례 열쇠: 맨 위 회로(주 회로 먼저, 그다음 파일의 회로 차례), 그리고 거쳐 온 서브회로 부품과 그 부품마다
     * 위치(y, x), 종류(label·pin·tunnel·subcircuit·part), 부품 이름, 이름. v1 색인은 같은 자리에 선 부품(핀과 그 포트의
     * 터널)의 차례가 원조 회로의 HashSet 차례를 따라 실행마다 달랐다: 여기서 정한다.
     */
    static String walkKey(LogisimFile file, Hit h) {
        StringBuilder sb = new StringBuilder();
        Circuit main = file.getMainCircuit();
        int top = h.top == main ? 0 : 1 + file.getCircuits().indexOf(h.top);
        sb.append(String.format("%04d", top));
        for (Component inst : h.instances) {
            segment(sb, inst, 3, ((SubcircuitFactory) inst.getFactory()).getSubcircuit().getName());
        }
        segment(sb, h.component, KINDS.indexOf(h.kind), h.text);
        return sb.toString();
    }

    static final List<String> KINDS = List.of("label", "pin", "tunnel", "subcircuit", "part");

    private static void segment(StringBuilder sb, Component c, int kind, String text) {
        sb.append(String.format("%08d%08d%02d", c.getLocation().getY(), c.getLocation().getX(), kind))
                .append(c.getFactory().getName()).append('\u0000').append(text).append('\u0000');
    }

    /** 한 자리: 보일 회로(맨 위 회로와 거기서 내려간 서브회로 부품들), 부품, 자리 글. */
    static JsonObject place(Doc d, Hit h) {
        JsonObject o = new JsonObject();
        o.addProperty("circuitId", d.ids().of(h.circuit));
        o.addProperty("root", d.ids().of(h.top));
        JsonArray path = new JsonArray();
        for (Component inst : h.instances) {
            path.add(d.ids().of(inst));
        }
        o.add("path", path);
        o.addProperty("componentId", d.ids().of(h.component));
        o.add("at", ModelJson.point(h.component.getLocation()));
        if (h.entry != null) {
            boolean near = NameIndex.attached(h.entry) != null;
            o.addProperty("place", NameIndex.place(h.entry));
            o.addProperty("near", near);
        } else {
            o.addProperty("place", Names.path(h.path, Names.title(h.circuit, h.component)));
            o.addProperty("near", false);
        }
        return o;
    }

    /**
     * 부품(터널·서브회로 부품 밖)을 원조 부품 이름으로: v1 색인과 같은 길(주 회로부터 서브회로 부품을 따라
     * 깊이 32까지, 닿지 않는 회로도)로 모은다. path는 그 부품이 든 회로의 경로다.
     */
    static List<Hit> parts(LogisimFile file) {
        List<Hit> ret = new ArrayList<>();
        Set<Circuit> reached = new LinkedHashSet<>();
        Circuit main = file.getMainCircuit();
        if (main != null) {
            walk(ret, main, main, new ArrayList<>(), new ArrayList<>(Collections.singletonList(main.getName())),
                    reached, 0);
        }
        for (Circuit c : file.getCircuits()) {
            if (!reached.contains(c)) {
                walk(ret, c, c, new ArrayList<>(), new ArrayList<>(Collections.singletonList(c.getName())), reached,
                        0);
            }
        }
        return ret;
    }

    private static void walk(List<Hit> out, Circuit top, Circuit c, List<Component> instances, List<String> path,
            Set<Circuit> reached, int depth) {
        reached.add(c);
        if (depth > 32) {
            return;
        }
        List<Component> comps = new ArrayList<>(c.getNonWires());
        comps.sort((a, b) -> a.getLocation().getY() != b.getLocation().getY()
                ? a.getLocation().getY() - b.getLocation().getY() : a.getLocation().getX() - b.getLocation().getX());
        String here = Names.path(path);
        for (Component comp : comps) {
            if (comp.getFactory() instanceof SubcircuitFactory) {
                Circuit sub = ((SubcircuitFactory) comp.getFactory()).getSubcircuit();
                List<Component> deeper = new ArrayList<>(instances);
                deeper.add(comp);
                List<String> p = new ArrayList<>(path);
                p.add(Names.name(c, comp));
                walk(out, top, sub, deeper, p, reached, depth + 1);
                continue;
            }
            if (comp.getFactory().getName().equals("Tunnel")) {
                continue; // 이름으로 이미 찾는다(v1 색인)
            }
            out.add(new Hit("part", comp.getFactory().getDisplayName(), here, top,
                    Collections.unmodifiableList(new ArrayList<>(instances)), c, comp, null));
        }
    }
}
