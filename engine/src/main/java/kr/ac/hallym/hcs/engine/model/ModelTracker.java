/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.model.Netlist;

/**
 * 화면에 보낸 모델과 지금 모델의 차이(model.changed, D-134). 편집 의도·되돌리기·다시 실행이 끝날 때마다 파일의 모든
 * 회로를 규약 JSON으로 다시 적어 앞의 것과 비교한다. 사라진 id는 removed, 새 id와 제자리에서 바뀐 부품(같은 id)은
 * added로 보낸다. 다른 회로가 바뀐 경우(예: 서브회로 핀을 더해 인스턴스 포트가 바뀜)도 그 회로의 알림으로 나온다.
 * 넷 번호는 바뀐 회로마다 새로 매기고, 넷리스트는 값 스트림과 함께 쓴다(같은 번호).
 */
public final class ModelTracker {
    private final ModelJson json;
    private final LogisimFile file;
    private final Map<Circuit, Map<String, JsonObject>> last = new IdentityHashMap<>();
    private final Map<Circuit, Netlist> netlists = new IdentityHashMap<>();
    /** 마지막으로 알린 신호 그룹·영역 메모(hcs:ext, N-15 D-151): 그것만 바뀐 편집도 알린다. */
    private final Map<Circuit, JsonObject> lastExt = new IdentityHashMap<>();
    /** 화면이 본 .circ 라이브러리의 회로(읽기 전용). 그 부품 id도 살려 둔다(인스턴스 경로에 쓴다). */
    private final Set<Circuit> viewedLibraryCircuits = Collections.newSetFromMap(new IdentityHashMap<>());

    public ModelTracker(ModelJson json, LogisimFile file) {
        this.json = json;
        this.file = file;
    }

    public ModelJson json() {
        return json;
    }

    /** 지금 모델을 기준으로 삼는다(파일을 연 직후). */
    public void baseline() {
        last.clear();
        netlists.clear();
        lastExt.clear();
        for (Circuit c : file.getCircuits()) {
            last.put(c, state(c));
            lastExt.put(c, json.ext(c, () -> netlist(c)));
        }
    }

    /** 회로의 넷리스트(마지막으로 알린 모델 기준, 값 스트림과 같은 번호). */
    public Netlist netlist(Circuit c) {
        return netlists.computeIfAbsent(c, Netlist::of);
    }

    public JsonObject snapshot(Circuit c) {
        if (!file.contains(c)) {
            viewedLibraryCircuits.add(c);
        }
        return json.snapshot(c, netlist(c));
    }

    /** 앞의 알림 뒤 바뀐 회로마다 model.changed의 params 하나. 기준을 지금 모델로 옮긴다. */
    public List<JsonObject> changes(String fileId, boolean dirty) {
        List<JsonObject> out = new ArrayList<>();
        List<Component> live = new ArrayList<>();
        Map<Circuit, Map<String, JsonObject>> next = new IdentityHashMap<>();
        Map<Circuit, JsonObject> nextExt = new IdentityHashMap<>();
        for (Circuit c : file.getCircuits()) {
            live.addAll(c.getNonWires());
            live.addAll(c.getWires());
            Map<String, JsonObject> now = state(c);
            next.put(c, now);
            Map<String, JsonObject> before = last.get(c);
            if (before == null) {
                before = new LinkedHashMap<>();
            }
            List<String> removed = new ArrayList<>();
            for (String id : before.keySet()) {
                if (!now.containsKey(id)) {
                    removed.add(id);
                }
            }
            List<JsonObject> added = new ArrayList<>();
            for (Map.Entry<String, JsonObject> e : now.entrySet()) {
                if (!e.getValue().equals(before.get(e.getKey()))) {
                    added.add(e.getValue());
                }
            }
            boolean shape = !(removed.isEmpty() && added.isEmpty());
            if (shape) {
                netlists.remove(c);
            }
            // 신호 그룹·영역 메모만 바뀐 편집(edit.signalGroup, edit.areaMemo와 그 되돌리기)도 알린다
            JsonObject ext = json.ext(c, () -> netlist(c));
            nextExt.put(c, ext);
            JsonObject extBefore = lastExt.get(c);
            boolean extSame = extBefore == null
                    ? ext.getAsJsonArray("groups").size() == 0 && ext.getAsJsonArray("memos").size() == 0
                    : ext.equals(extBefore);
            if (!shape && extSame) {
                continue;
            }
            removed.sort(null);
            added.sort(ModelJson.ORDER);
            JsonObject o = new JsonObject();
            o.addProperty("fileId", fileId);
            o.addProperty("circuitId", json.ids().of(c));
            JsonArray r = new JsonArray();
            removed.forEach(r::add);
            o.add("removed", r);
            o.add("added", ModelJson.toArray(added));
            o.add("nets", json.nets(c, netlist(c)));
            o.add("junctions", ModelJson.junctions(c));
            o.add("groups", ext.get("groups"));
            o.add("memos", ext.get("memos"));
            o.addProperty("dirty", dirty);
            out.add(o);
        }
        last.clear();
        last.putAll(next);
        lastExt.clear();
        lastExt.putAll(nextExt);
        netlists.keySet().retainAll(next.keySet());
        for (Circuit c : viewedLibraryCircuits) {
            live.addAll(c.getNonWires());
            live.addAll(c.getWires());
        }
        json.ids().retain(live);
        return out;
    }

    private Map<String, JsonObject> state(Circuit c) {
        Map<String, JsonObject> m = new LinkedHashMap<>();
        for (Component x : c.getNonWires()) {
            JsonObject o = json.component(c, x);
            m.put(o.get("id").getAsString(), o);
        }
        for (Wire w : c.getWires()) {
            JsonObject o = json.wire(w);
            m.put(o.get("id").getAsString(), o);
        }
        return m;
    }
}
