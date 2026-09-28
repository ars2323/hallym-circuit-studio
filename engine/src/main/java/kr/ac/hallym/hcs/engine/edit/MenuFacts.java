/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.edit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.tools.MenuExtender;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.edit.CircuitEdits;
import kr.ac.hallym.hcs.app.menu.MenuLayout;
import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.probe.QuickProbe;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * 우클릭 메뉴의 사실(model.menu, N-10, D-157). 메뉴의 항목은 화면의 메뉴 등록표 하나(electron
 * {@code app/menus/registry.ts})가 정하고, 엔진은 그 항목들이 기대는 사실만 v1 코드로 답한다: 요약 줄(v1
 * {@code MenuLayout.summary}, 단수·복수 S-25), 누른 부품·선, 포트(5px 안), 고른 것과 고른 차례(v1
 * {@code SelectionOrder}), 부품이 원조 선택지를 가진 속성, 게이트 바꾸기, 핀·터널·서브회로·PC 표시·프로브, 원조
 * 부품 메뉴의 항목(Splitter Distribute). 모델은 바꾸지 않는다.
 */
public final class MenuFacts {
    private MenuFacts() {
    }

    /**
     * id(화면이 누른 곳에서 찾은 부품·선, 없으면 엔진이 찾는다)와 누른 점 at의 사실.
     */
    public static JsonObject at(Doc d, Circuit c, Location at, Component hit) throws RpcError {
        if (hit == null) {
            hit = under(d, c, at);
        }
        JsonObject o = new JsonObject();
        o.addProperty("circuitId", d.ids().of(c));
        o.addProperty("editable", kr.ac.hallym.hcs.engine.model.AttrTable.editable(d, c));
        List<Component> order = chosenInOrder(d, c);
        List<Component> parts = nonWires(order);
        boolean inSelection = hit == null || order.contains(hit);
        String kind;
        if (parts.size() >= 2 && inSelection) {
            kind = "many";
        } else if (hit == null) {
            kind = "empty";
        } else if (hit instanceof Wire) {
            kind = "wire";
        } else {
            kind = "part";
        }
        o.addProperty("kind", kind);
        o.addProperty("summary", MenuLayout.summary(c, hit, at, inSelection ? parts.size() : 1));
        if (hit != null) {
            o.addProperty("id", d.ids().of(hit));
        }
        JsonObject sel = new JsonObject();
        JsonArray ids = new JsonArray();
        for (Component x : order) {
            ids.add(d.ids().of(x));
        }
        sel.add("ids", ids);
        sel.addProperty("ordered", d.selectionOrder().known());
        sel.addProperty("parts", parts.size());
        sel.addProperty("wires", order.size() - parts.size());
        o.add("selection", sel);
        if (kind.equals("many")) {
            o.add("common", common(parts));
        } else if (kind.equals("part")) {
            o.add("part", part(d, c, hit, at));
        } else if (kind.equals("wire")) {
            o.add("wire", wire(c, (Wire) hit));
        }
        if (hit == null) { // 빈 곳(여러 개를 고른 채여도): v1 ProbeMenu의 Select/Delete All Probes
            JsonArray probes = new JsonArray();
            for (Component p : QuickProbe.probes(c)) {
                probes.add(d.ids().of(p));
            }
            o.add("probes", probes);
        }
        // 고른 선이 모두 선이고 둘 이상: 하나의 버스로 합치기(v1 SplitterMenu.combine)
        if (parts.isEmpty() && order.size() >= 2 && (hit == null || order.contains(hit))) {
            JsonArray widths = new JsonArray();
            for (Component x : order) {
                widths.add(wireWidth(c, (Wire) x));
            }
            o.add("combine", widths);
        }
        return o;
    }

    /** 누른 점의 부품(v1: 그 점을 담는 것, 선보다 부품 먼저). */
    static Component under(Doc d, Circuit c, Location at) {
        Collection<Component> here = c.getAllContaining(at, d.canvas().getGraphics());
        Component wire = null;
        for (Component x : here) {
            if (!(x instanceof Wire)) {
                return x;
            }
            if (wire == null) {
                wire = x;
            }
        }
        return wire;
    }

    /** 이 회로에서 고른 것(떠 있는 것 포함), 고른 차례. */
    static List<Component> chosenInOrder(Doc d, Circuit c) {
        List<Component> ret = new ArrayList<>();
        if (d.selectionCircuit() != c) {
            return ret;
        }
        Collection<Component> now = d.selection().getComponents();
        d.selectionOrder().update(now, new Object()); // 편집 밖에서 바뀐 것이 없으면 아무것도 바뀌지 않는다
        for (Component x : d.selectionOrder().order()) {
            if (now.contains(x)) {
                ret.add(x);
            }
        }
        for (Component x : now) {
            if (!ret.contains(x)) {
                ret.add(x);
            }
        }
        return ret;
    }

    static List<Component> nonWires(List<Component> xs) {
        List<Component> ret = new ArrayList<>();
        for (Component x : xs) {
            if (!(x instanceof Wire)) {
                ret.add(x);
            }
        }
        return ret;
    }

    /** 여러 부품: 모두가 가진 것(v1 EditMenus.multi: 방향·폭 바꾸기, 라벨 한꺼번에). */
    static JsonObject common(List<Component> parts) {
        Set<String> common = null;
        for (Component x : parts) {
            Set<String> names = new LinkedHashSet<>();
            for (Attribute<?> a : x.getAttributeSet().getAttributes()) {
                names.add(a.getName());
            }
            if (common == null) {
                common = names;
            } else {
                common.retainAll(names);
            }
        }
        JsonObject o = new JsonObject();
        o.addProperty("facing", common != null && common.contains("facing"));
        o.addProperty("width", common != null && common.contains("width"));
        o.addProperty("label", common != null && common.contains("label"));
        JsonArray labels = new JsonArray();
        for (Component x : parts) {
            Object v = value(x, "label");
            labels.add(v == null ? "" : v.toString());
        }
        o.add("labels", labels);
        return o;
    }

    /** 부품 하나(v1 EditMenus.contribute의 갈래). */
    static JsonObject part(Doc d, Circuit c, Component x, Location at) {
        JsonObject o = new JsonObject();
        String f = x.getFactory().getName();
        o.addProperty("name", f);
        o.addProperty("display", x.getFactory().getDisplayName());
        String label = Names.label(x);
        if (label != null) {
            o.addProperty("label", label);
        }
        o.addProperty("labelAttr", x.getAttributeSet().getAttribute("label") != null);
        o.addProperty("facing", x.getAttributeSet().getAttribute("facing") != null);
        o.addProperty("width", x.getAttributeSet().getAttribute("width") != null);
        o.addProperty("inputs", x.getAttributeSet().getAttribute("inputs") != null);
        int port = portAt(x, at);
        if (port >= 0) {
            EndData e = x.getEnds().get(port);
            JsonObject p = new JsonObject();
            p.addProperty("i", port);
            p.addProperty("name", Kinds.portName(x, port));
            p.addProperty("dir", e.isInput() && e.isOutput() ? "inout" : e.isInput() ? "in" : "out");
            p.addProperty("width", e.getWidth().getWidth());
            Attribute<?> negate = x.getAttributeSet().getAttribute("negate" + (port - 1));
            if (negate != null && port >= 1) {
                p.addProperty("negate", negate.getName());
                p.addProperty("negated", Boolean.TRUE.equals(x.getAttributeSet().getValue(negate)));
            }
            o.add("port", p);
        }
        boolean gate = CircuitEdits.isSwappableGate(x) || f.equals("NOT Gate") || f.equals("Buffer");
        o.addProperty("gate", gate);
        if (CircuitEdits.isSwappableGate(x)) {
            JsonArray swaps = new JsonArray();
            for (String g : CircuitEdits.swappableGates()) {
                if (!g.equals(f)) {
                    swaps.add(g);
                }
            }
            o.add("swaps", swaps);
        }
        JsonObject options = new JsonObject();
        for (String name : new String[] {"size", "pull"}) {
            JsonArray opts = options(x, name);
            if (opts.size() > 0) {
                options.add(name, opts);
            }
        }
        o.add("options", options);
        if (f.equals("Pin")) {
            JsonObject pin = new JsonObject();
            pin.addProperty("output", "true".equals(standard(x, "output")));
            pin.addProperty("tristate", "true".equals(standard(x, "tristate")));
            o.add("pin", pin);
        }
        if (f.equals("Tunnel") && label != null) {
            JsonObject t = new JsonObject();
            JsonArray same = new JsonArray();
            List<Component> tunnels = sameTunnels(c, label);
            for (Component y : tunnels) {
                same.add(d.ids().of(y));
            }
            t.add("same", same);
            t.addProperty("index", tunnels.indexOf(x));
            o.add("tunnel", t);
        }
        if (x.getFactory() instanceof SubcircuitFactory) {
            Circuit sub = ((SubcircuitFactory) x.getFactory()).getSubcircuit();
            JsonObject s = new JsonObject();
            s.addProperty("circuitId", d.ids().of(sub));
            s.addProperty("name", sub.getName());
            java.io.File origin = null;
            try {
                origin = kr.ac.hallym.hcs.app.libs.LibrarySync.originFile(d.project(), sub);
            } catch (RuntimeException e) {
                origin = null;
            }
            if (!d.file().getCircuits().contains(sub)) {
                s.addProperty("library", origin != null ? origin.getName() : "");
            }
            s.addProperty("defaultAppearance", sub.getAppearance().isDefaultAppearance());
            s.addProperty("registerFile", kr.ac.hallym.hcs.app.cycle.RegisterFile.marked(d.file()) == sub);
            o.add("subcircuit", s);
        }
        if (kr.ac.hallym.hcs.app.sim.PcMark.markable(x)) {
            o.addProperty("pcMarked", kr.ac.hallym.hcs.app.sim.PcMark.marked(d.file(), c) == x);
        }
        // RAM·ROM(원조 MemMenu), Hallym MIPS 메모리(Load Program…, lib-mips LoadProgramMenu)
        if (kr.ac.hallym.hcs.engine.sim.Memories.isRam(x)) {
            o.addProperty("memory", "ram");
        } else if (kr.ac.hallym.hcs.engine.sim.Memories.isRom(x)) {
            o.addProperty("memory", "rom");
        } else if (x.getFactory().getClass().getName().startsWith("kr.ac.hallym.hcs.mips.")
                && (f.equals("Instruction Memory") || f.equals("Data Memory"))) {
            o.addProperty("memory", "program");
            String source = standard(x, "source");
            if (source != null && !source.isEmpty()) {
                o.addProperty("source", source);
            }
        }
        JsonArray original = originalItems(d, c, x);
        if (original.size() > 0) {
            o.add("original", original);
        }
        return o;
    }

    /**
     * 원조 부품 메뉴(MenuExtender)의 항목 가운데 창 없이 원조 코드로 할 수 있는 것: Splitter의 Distribute
     * Ascending·Descending(원조 SplitterDistributeItem, 켜짐 여부 그대로). RAM·ROM의 메뉴와 서브회로의 View는 화면이
     * 따로 만든다(16진 편집기·파일 고르기·들어가기).
     */
    static JsonArray originalItems(Doc d, Circuit c, Component x) {
        JsonArray out = new JsonArray();
        if (!x.getFactory().getName().equals("Splitter")) {
            return out;
        }
        Object ext = x.getFeature(MenuExtender.class);
        if (!(ext instanceof MenuExtender)) {
            return out;
        }
        JPopupMenu menu = new JPopupMenu();
        ((MenuExtender) ext).configureMenu(menu, d.project());
        int i = 0;
        for (java.awt.Component item : menu.getComponents()) {
            if (item instanceof JMenuItem) {
                JsonObject it = new JsonObject();
                it.addProperty("i", i);
                it.addProperty("text", ((JMenuItem) item).getText());
                it.addProperty("enabled", item.isEnabled());
                out.add(it);
                i++;
            }
        }
        return out;
    }

    static JsonObject wire(Circuit c, Wire w) {
        JsonObject o = new JsonObject();
        o.addProperty("width", wireWidth(c, w));
        o.addProperty("net", QuickProbe.netName(c, Netlist.of(c).netOf(w)));
        return o;
    }

    /** 선의 폭(원조가 계산한 그 점의 폭, 모르면 넷의 포트 폭, v1 SplitterMenu.width). */
    static int wireWidth(Circuit c, Wire w) {
        BitWidth bw = c.getWidth(w.getEnd0());
        if (bw != null && bw.getWidth() > 0) {
            return bw.getWidth();
        }
        Netlist.Net net = Netlist.of(c).netOf(w);
        return net == null ? 0 : net.width();
    }

    /** 누른 점 가까이(5px 안)의 포트(v1 EditMenus.portAt). 없으면 -1. */
    static int portAt(Component c, Location p) {
        for (int i = 0; i < c.getEnds().size(); i++) {
            Location e = c.getEnds().get(i).getLocation();
            if (Math.abs(e.getX() - p.getX()) <= 5 && Math.abs(e.getY() - p.getY()) <= 5) {
                return i;
            }
        }
        return -1;
    }

    /** 같은 이름 터널(위→아래, 왼쪽→오른쪽, v1 EditMenus.sameTunnels). */
    static List<Component> sameTunnels(Circuit c, String label) {
        List<Component> ret = new ArrayList<>();
        for (Component o : c.getNonWires()) {
            if (o.getFactory().getName().equals("Tunnel") && label.equals(Names.label(o))) {
                ret.add(o);
            }
        }
        ret.sort((a, b) -> a.getLocation().getY() != b.getLocation().getY()
                ? a.getLocation().getY() - b.getLocation().getY() : a.getLocation().getX() - b.getLocation().getX());
        return ret;
    }

    /** 원조 편집기의 목록(v1 EditMenus.options: 손으로 적지 않고 원조 속성에서 읽는다). */
    static JsonArray options(Component x, String attr) {
        JsonArray out = new JsonArray();
        @SuppressWarnings("unchecked")
        Attribute<Object> a = (Attribute<Object>) x.getAttributeSet().getAttribute(attr);
        if (a == null) {
            return out;
        }
        Object now = x.getAttributeSet().getValue(a);
        JsonArray opts = kr.ac.hallym.hcs.engine.model.AttrTable.optionsJson(a, now);
        if (opts == null) {
            return out;
        }
        String cur = now == null ? null : a.toStandardString(now);
        for (JsonElement e : opts) {
            JsonObject o = e.getAsJsonObject();
            o.addProperty("checked", o.get("value").getAsString().equals(cur));
            out.add(o);
        }
        return out;
    }

    static Object value(Component x, String attr) {
        Attribute<?> a = x.getAttributeSet().getAttribute(attr);
        return a == null ? null : x.getAttributeSet().getValue(a);
    }

    static String standard(Component x, String attr) {
        @SuppressWarnings("unchecked")
        Attribute<Object> a = (Attribute<Object>) x.getAttributeSet().getAttribute(attr);
        if (a == null) {
            return null;
        }
        Object v = x.getAttributeSet().getValue(a);
        return v == null ? null : a.toStandardString(v);
    }
}
