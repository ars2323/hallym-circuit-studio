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
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
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

    /** 찾기 결과 한 줄: 종류·글자·경로가 같은 항목을 묶은 것(#135). 하나면 그 항목 줄이다. */
    public static final class Group {
        public final Entry first;
        public final List<Entry> entries;

        Group(List<Entry> entries) {
            this.first = entries.get(0);
            this.entries = Collections.unmodifiableList(entries);
        }

        public int size() {
            return entries.size();
        }
    }

    /**
     * 같은 종류·글자·경로의 결과를 한 줄로 묶는다(순서는 처음 나온 순서, 묶음 안은 위→아래, 왼쪽→오른쪽). 같은 이름
     * 터널 7개는 한 줄 "pc 터널 · main › pc (7곳)"이 되고, 펼치면 위치별 줄이 된다.
     */
    public static List<Group> group(List<Entry> found) {
        java.util.LinkedHashMap<String, List<Entry>> by = new java.util.LinkedHashMap<>();
        for (Entry e : found) {
            by.computeIfAbsent(e.kind + "\u0000" + e.text + "\u0000" + e.path, k -> new ArrayList<>()).add(e);
        }
        List<Group> ret = new ArrayList<>();
        for (List<Entry> es : by.values()) {
            es.sort((a, b) -> a.component.getLocation().getY() != b.component.getLocation().getY()
                    ? a.component.getLocation().getY() - b.component.getLocation().getY()
                    : a.component.getLocation().getX() - b.component.getLocation().getX());
            ret.add(new Group(es));
        }
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

    /** 다른 부품의 포트 하나. */
    public static final class Port {
        public final Component component;
        public final int end;

        Port(Component component, int end) {
            this.component = component;
            this.end = end;
        }
    }

    /**
     * 펼친 묶음의 위치 줄에 쓰는 자리(검토 2차 D): 좌표 대신 붙어 있는 포트. 포트 하나짜리(터널·핀·프로브 …)는 그 끝에
     * 선으로(터널을 건너지 않고) 닿는 다른 부품의 포트 중 가장 뜻있는 것이다: 일반 부품 → 스플리터 → 핀·상수 → 프로브
     * → 터널 순, 같으면 가까운 것. 포트가 여럿인 부품(라벨이 같은 레지스터 둘 …)이나 아무 데도 붙지 않은 부품은
     * {@code null}이다.
     */
    public static Port attached(Entry e) {
        Component c = e.component;
        if (c.getEnds().size() != 1) {
            return null;
        }
        Location at = c.getEnd(0).getLocation();
        // 끝에서 선을 따라 닿는 점들. 터널은 건너지 않는다(같은 이름 터널끼리의 넷이 아니라 이 자리를 알린다)
        Set<Location> reached = new java.util.HashSet<>();
        List<Wire> wires = new ArrayList<>();
        java.util.ArrayDeque<Location> todo = new java.util.ArrayDeque<>();
        reached.add(at);
        todo.add(at);
        while (!todo.isEmpty()) {
            Location q = todo.poll();
            for (Wire w : e.circuit.getWires()) {
                if (!wires.contains(w) && w.contains(q)) {
                    wires.add(w);
                    for (Location end : new Location[] {w.getEnd0(), w.getEnd1()}) {
                        if (reached.add(end)) {
                            todo.add(end);
                        }
                    }
                }
            }
        }
        Port best = null;
        Location bestAt = null;
        for (Component o : e.circuit.getNonWires()) {
            if (o == c) {
                continue;
            }
            for (int i = 0; i < o.getEnds().size(); i++) {
                Location l = o.getEnd(i).getLocation();
                boolean touches = reached.contains(l);
                for (int k = 0; !touches && k < wires.size(); k++) {
                    touches = wires.get(k).contains(l);
                }
                if (!touches) {
                    continue;
                }
                Port p = new Port(o, i);
                if (best == null || rank(p) < rank(best)
                        || rank(p) == rank(best) && distance(l, at) < distance(bestAt, at)) {
                    best = p;
                    bestAt = l;
                }
            }
        }
        return best;
    }

    /**
     * 위치 줄의 자리 이름(경로 포함): 붙은 포트 {@code main › datapath › PC.D}, 없으면 번호 이름
     * ({@code main › Reg #2}, {@code main › Tunnel #3}).
     */
    public static String place(Entry e) {
        String circuitPath = circuitPath(e.path);
        Port p = attached(e);
        return p == null ? Names.path(circuitPath, Names.numbered(e.circuit, e.component))
                : Names.path(circuitPath, Names.port(e.circuit, p.component, p.end));
    }

    /** 이웃 포트의 순위: 작을수록 자리를 잘 알려 준다. */
    static int rank(Port r) {
        String f = r.component.getFactory().getName();
        switch (f) {
            case "Tunnel":
                return 4;
            case "Probe":
            case "Radix Probe":
                return 3;
            case "Pin":
            case "Constant":
            case "Clock":
                return 2;
            case "Splitter":
                return 1;
            default:
                return 0;
        }
    }

    private static int distance(Location a, Location b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }

    /** 부품 경로에서 회로 경로: {@code main › datapath › pc} → {@code main › datapath}. */
    static String circuitPath(String componentPath) {
        int i = componentPath.lastIndexOf(Names.SEP);
        return i < 0 ? "" : componentPath.substring(0, i);
    }
}
