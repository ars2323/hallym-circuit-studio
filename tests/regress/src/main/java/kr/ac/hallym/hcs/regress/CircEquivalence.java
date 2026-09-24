/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.regress;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.circuit.WireSet;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

/**
 * 두 .circ의 의미 동등성(D-006 보강). 원조 2.7.1로 두 파일을 불러와 옵션, 회로 속성, 부품과 그 속성, 넷리스트를
 * 비교한다. 줄 앞 공백과 wire/comp 순서를 지우는 {@link CircNormalizer}가 속성 값 변화까지 가리지 않는지 확인하는
 * 용도다.
 *
 * <p>넷은 원조의 연결 계산({@link Circuit#getWireSet})으로 묶은 선들과, 그 선의 끝점이나 선 위(부록 A.4 "포트 위를
 * 지나가면 연결")에 있는 부품 포트의 집합이다. 선 없이 같은 점에 닿은 포트끼리도 한 넷이다.
 */
public final class CircEquivalence {
    private CircEquivalence() {
    }

    /** 차이 목록. 비어 있으면 같다. */
    public static List<String> compare(File a, File b) throws LoadFailedException {
        Map<String, String> x = describe(load(a));
        Map<String, String> y = describe(load(b));
        List<String> diffs = new ArrayList<String>();
        Set<String> keys = new TreeSet<String>(x.keySet());
        keys.addAll(y.keySet());
        for (String k : keys) {
            String vx = x.get(k);
            String vy = y.get(k);
            if (vx == null ? vy != null : !vx.equals(vy)) {
                diffs.add(k + ": " + vx + " | " + vy);
            }
        }
        return diffs;
    }

    private static LogisimFile load(File f) throws LoadFailedException {
        return new Loader(null).openLogisimFile(f);
    }

    /** 비교할 사실들을 키 → 값으로. 같은 키가 둘이면 값에 개수를 센다. */
    static Map<String, String> describe(LogisimFile file) {
        Map<String, String> out = new TreeMap<String, String>();
        out.put("options", attrs(file.getOptions().getAttributeSet()));
        out.put("main", file.getMainCircuit() == null ? "" : file.getMainCircuit().getName());
        for (Circuit c : file.getCircuits()) {
            String prefix = "circuit " + c.getName() + " ";
            out.put(prefix + "attrs", attrs(c.getStaticAttributes()));
            Map<String, Integer> comps = new TreeMap<String, Integer>();
            for (Component comp : c.getNonWires()) {
                String key = id(comp) + " " + attrs(comp.getAttributeSet());
                Integer n = comps.get(key);
                comps.put(key, n == null ? 1 : n + 1);
            }
            out.put(prefix + "components", comps.toString());
            out.put(prefix + "nets", nets(c).toString());
        }
        return out;
    }

    private static String id(Component comp) {
        return comp.getFactory().getName() + "@" + comp.getLocation();
    }

    private static String attrs(AttributeSet as) {
        StringBuilder sb = new StringBuilder("{");
        for (Attribute<?> a : as.getAttributes()) {
            @SuppressWarnings("unchecked")
            Attribute<Object> attr = (Attribute<Object>) a;
            Object v = as.getValue(attr);
            sb.append(attr.getName()).append('=').append(v == null ? "null" : attr.toStandardString(v)).append(';');
        }
        return sb.append('}').toString();
    }

    /** 넷마다 연결된 포트("부품@위치#포트")의 정렬된 집합. 포트가 없는 넷은 뺀다. */
    static Set<Set<String>> nets(Circuit c) {
        List<Component> comps = new ArrayList<Component>(c.getNonWires());
        Set<Set<String>> nets = new HashSet<Set<String>>();
        Set<Wire> seen = new HashSet<Wire>();
        Set<String> wired = new HashSet<String>();
        for (Wire w : c.getWires()) {
            if (seen.contains(w)) {
                continue;
            }
            WireSet ws = c.getWireSet(w);
            List<Wire> members = new ArrayList<Wire>();
            for (Wire other : c.getWires()) {
                if (ws.containsWire(other)) {
                    members.add(other);
                    seen.add(other);
                }
            }
            Set<String> ports = new TreeSet<String>();
            for (Component comp : comps) {
                for (int i = 0; i < comp.getEnds().size(); i += 1) {
                    Location at = comp.getEnds().get(i).getLocation();
                    if (onAny(members, ws, at)) {
                        ports.add(id(comp) + "#" + i);
                    }
                }
            }
            wired.addAll(ports);
            if (!ports.isEmpty()) {
                nets.add(ports);
            }
        }
        // 선 없이 같은 점에 닿은 포트
        Map<Location, Set<String>> byPoint = new TreeMap<Location, Set<String>>();
        for (Component comp : comps) {
            for (int i = 0; i < comp.getEnds().size(); i += 1) {
                String port = id(comp) + "#" + i;
                if (!wired.contains(port)) {
                    Location at = comp.getEnds().get(i).getLocation();
                    Set<String> s = byPoint.get(at);
                    if (s == null) {
                        s = new TreeSet<String>();
                        byPoint.put(at, s);
                    }
                    s.add(port);
                }
            }
        }
        for (Set<String> s : byPoint.values()) {
            if (s.size() > 1) {
                nets.add(s);
            }
        }
        return nets;
    }

    private static boolean onAny(List<Wire> wires, WireSet ws, Location at) {
        if (ws.containsLocation(at)) {
            return true;
        }
        for (Wire w : wires) {
            if (w.contains(at)) {
                return true;
            }
        }
        return false;
    }
}
