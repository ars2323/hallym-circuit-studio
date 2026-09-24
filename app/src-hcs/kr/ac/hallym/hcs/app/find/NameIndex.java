/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.find;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.app.model.Names;

/**
 * 이름 색인(#80, PLAN.md 11.11): 라벨, 터널 이름, 서브회로 이름을 모든 서브회로에서 경로와 함께 모은다.
 * 주 회로에서 서브회로 부품을 따라 내려가므로 같은 서브회로를 두 번 쓰면 경로가 둘이다. 주 회로에서 닿지 않는
 * 회로도 제 이름을 경로로 넣는다. GUI 없이 테스트한다.
 */
public final class NameIndex {
    public enum Kind { LABEL, TUNNEL, SUBCIRCUIT }

    /** 찾은 것 하나. instances는 맨 위 회로부터 거쳐 온 서브회로 부품들. */
    public static final class Entry {
        public final Kind kind;
        public final String text;
        public final Circuit top;
        public final List<Component> instances;
        public final Circuit circuit;
        public final Component component;
        public final String path;

        Entry(Kind kind, String text, Circuit top, List<Component> instances, Circuit circuit, Component component,
                String path) {
            this.kind = kind;
            this.text = text;
            this.top = top;
            this.instances = Collections.unmodifiableList(new ArrayList<>(instances));
            this.circuit = circuit;
            this.component = component;
            this.path = path;
        }

        @Override
        public String toString() {
            return kind + " " + path;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public static NameIndex of(LogisimFile file) {
        NameIndex idx = new NameIndex();
        Circuit main = file.getMainCircuit();
        Set<Circuit> reached = new LinkedHashSet<>();
        if (main != null) {
            idx.walk(main, main, new ArrayList<>(), new ArrayList<>(Collections.singletonList(main.getName())),
                    reached, 0);
        }
        for (Circuit c : file.getCircuits()) {
            if (!reached.contains(c)) {
                idx.walk(c, c, new ArrayList<>(), new ArrayList<>(Collections.singletonList(c.getName())), reached,
                        0);
            }
        }
        return idx;
    }

    private void walk(Circuit top, Circuit c, List<Component> instances, List<String> path, Set<Circuit> reached,
            int depth) {
        reached.add(c);
        if (depth > 32) {
            return; // 순환 참조 방어(원조가 막지만 안전하게)
        }
        // 위→아래, 왼쪽→오른쪽 순으로 보면 결과 순서가 늘 같다
        List<Component> comps = new ArrayList<>(c.getNonWires());
        comps.sort((a, b) -> a.getLocation().getY() != b.getLocation().getY()
                ? a.getLocation().getY() - b.getLocation().getY() : a.getLocation().getX() - b.getLocation().getX());
        for (Component comp : comps) {
            String name = Names.name(c, comp);
            List<String> here = new ArrayList<>(path);
            here.add(name);
            String p = Names.path(here);
            if (comp.getFactory() instanceof SubcircuitFactory) {
                Circuit sub = ((SubcircuitFactory) comp.getFactory()).getSubcircuit();
                entries.add(new Entry(Kind.SUBCIRCUIT, sub.getName(), top, instances, c, comp, p));
                List<Component> deeper = new ArrayList<>(instances);
                deeper.add(comp);
                walk(top, sub, deeper, here, reached, depth + 1);
                continue;
            }
            String label = Names.label(comp);
            if (label == null) {
                continue;
            }
            Kind k = comp.getFactory().getName().equals("Tunnel") ? Kind.TUNNEL : Kind.LABEL;
            entries.add(new Entry(k, label, top, instances, c, comp, p));
        }
    }

    /** 대소문자 없이 text를 포함하는 것. */
    public List<Entry> find(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Entry> ret = new ArrayList<>();
        if (q.isEmpty()) {
            return ret;
        }
        for (Entry e : entries) {
            if (e.text.toLowerCase(Locale.ROOT).contains(q)) {
                ret.add(e);
            }
        }
        // 이름이 정확히 같은 것 먼저
        ret.sort((a, b) -> Boolean.compare(!a.text.equalsIgnoreCase(q), !b.text.equalsIgnoreCase(q)));
        return ret;
    }

    /** 한 회로 안의 터널 이름과 개수(이름 순). */
    public static TreeMap<String, List<Component>> tunnels(Circuit c) {
        TreeMap<String, List<Component>> m = new TreeMap<>();
        for (Component comp : c.getNonWires()) {
            if (comp.getFactory().getName().equals("Tunnel")) {
                String label = Names.label(comp);
                if (label != null) {
                    m.computeIfAbsent(label, k -> new ArrayList<>()).add(comp);
                }
            }
        }
        return m;
    }
}
