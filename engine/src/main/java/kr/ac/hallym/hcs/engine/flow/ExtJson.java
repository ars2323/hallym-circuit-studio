/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.ext.CircExtension;
import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.groups.SignalGroups;
import kr.ac.hallym.hcs.app.memo.AreaMemos;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.engine.model.ModelJson;

/**
 * 학생이 회로에 둔 표시 정보를 규약 JSON으로(N-15, D-151): 신호 그룹(v1 E-04 {@link SignalGroups})과 영역 메모(v1
 * E-08 {@link AreaMemos}). 둘 다 .circ 확장 정보(hcs:ext)에 있고 원조 회로 부분과 무관하다. 스냅숏({@code model.circuit})과
 * {@code model.changed}에 실린다.
 * <ul>
 * <li>{@code groups: [{net, group, assigned}]}: 그룹이 있는 넷(모델 알림과 같은 넷 번호). {@code assigned}는 학생이
 * 정한 것, 아니면 {@code control}이라는 서브회로 인스턴스의 출력이라 기본 Control인 것이다.</li>
 * <li>{@code memos: [{x, y, w, h, color, text}]}: 파일 차례. {@code color}는 터널 색 팔레트 번호(0~11).</li>
 * </ul>
 */
public final class ExtJson {
    private ExtJson() {
    }

    /** 회로 c의 그룹과 메모. nl은 모델 알림의 넷리스트(넷 번호가 같게, 그룹이 있을 때만 부른다). */
    public static JsonObject of(LogisimFile file, Circuit c, Supplier<Netlist> nl) {
        JsonObject o = new JsonObject();
        o.add("groups", groups(file, c, nl));
        o.add("memos", memos(file, c));
        return o;
    }

    /** 스냅숏에는 있는 것만 싣는다(없으면 빈 목록과 같다, 화면 자료가 늘지 않게). */
    public static void addTo(JsonObject snapshot, LogisimFile file, Circuit c, Netlist nl) {
        JsonObject e = of(file, c, () -> nl);
        if (e.getAsJsonArray("groups").size() > 0) {
            snapshot.add("groups", e.get("groups"));
        }
        if (e.getAsJsonArray("memos").size() > 0) {
            snapshot.add("memos", e.get("memos"));
        }
    }

    public static JsonArray groups(LogisimFile file, Circuit c, Supplier<Netlist> netlist) {
        JsonArray out = new JsonArray();
        if (file == null || !mayHaveGroups(file, c)) {
            return out; // 넷리스트를 새로 만들지 않는다(편집마다 모든 회로를 본다)
        }
        Map<Netlist.Net, SignalGroups.Group> groups = SignalGroups.of(file, c);
        if (groups.isEmpty()) {
            return out;
        }
        // SignalGroups는 제 넷리스트를 쓴다: 선·포트로 알림의 넷을 찾는다
        Netlist nl = netlist.get();
        Map<Wire, Netlist.Net> byWire = new HashMap<>();
        for (Netlist.Net n : nl.nets()) {
            for (Wire w : n.wires()) {
                byWire.put(w, n);
            }
        }
        List<Object[]> rows = new ArrayList<>();
        for (Map.Entry<Netlist.Net, SignalGroups.Group> e : groups.entrySet()) {
            Netlist.Net theirs = e.getKey();
            Netlist.Net ours = null;
            if (!theirs.wires().isEmpty()) {
                ours = byWire.get(theirs.wires().get(0));
            } else if (!theirs.ports().isEmpty()) {
                Netlist.PortRef p = theirs.ports().get(0);
                ours = nl.netOf(p.component, p.end);
            }
            if (ours == null) {
                continue;
            }
            rows.add(new Object[] {ours, e.getValue(), SignalGroups.isAssigned(file, c, theirs)});
        }
        rows.sort((a, b) -> Integer.compare(((Netlist.Net) a[0]).id(), ((Netlist.Net) b[0]).id()));
        for (Object[] r : rows) {
            JsonObject o = new JsonObject();
            o.addProperty("net", ModelJson.netId((Netlist.Net) r[0]));
            o.addProperty("group", ((SignalGroups.Group) r[1]).key());
            o.addProperty("assigned", (Boolean) r[2]);
            out.add(o);
        }
        return out;
    }

    /** 그룹이 있을 수 있는 회로: 확장 정보에 그룹 항목이 있거나 control 서브회로 인스턴스가 있다. */
    static boolean mayHaveGroups(LogisimFile file, Circuit c) {
        for (CircExtension.Item item : CircExtensions.of(file).items(c.getName())) {
            if (item.kind().equals("group")) {
                return true;
            }
        }
        for (Component x : c.getNonWires()) {
            if (x.getFactory() instanceof SubcircuitFactory && x.getFactory().getName().equalsIgnoreCase("control")) {
                return true;
            }
        }
        return false;
    }

    public static JsonArray memos(LogisimFile file, Circuit c) {
        JsonArray out = new JsonArray();
        for (AreaMemos.Memo m : AreaMemos.of(file, c)) {
            JsonObject o = new JsonObject();
            o.addProperty("x", m.bounds.getX());
            o.addProperty("y", m.bounds.getY());
            o.addProperty("w", m.bounds.getWidth());
            o.addProperty("h", m.bounds.getHeight());
            o.addProperty("color", m.color);
            o.addProperty("text", m.text);
            out.add(o);
        }
        return out;
    }
}
