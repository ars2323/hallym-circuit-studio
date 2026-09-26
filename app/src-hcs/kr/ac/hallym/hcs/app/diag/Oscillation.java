/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;

import kr.ac.hallym.hcs.app.model.InstancePaths;
import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.record.Recorder;

/**
 * 진동(D-02, PLAN.md 4.2 조합 루프의 실행 중 모습): 원조 엔진이 값이 멈추지 않아 전파를 그만두면(Propagator
 * isOscillating, 원조는 시뮬레이션을 끈다) 그때 계속 바뀌던 점들로 고리를 찾아 한 줄로 말한다. 점은 원조가 캔버스에
 * 동그라미로 그리는 것과 같은 것이다(Propagator의 비공개 oscPoints를 읽기만 한다). 고리의 부품과 선을 강조하고,
 * Messages의 Reset 단추로 시뮬레이션을 처음으로 돌린다.
 */
public final class Oscillation {
    private Oscillation() {
    }

    /** 진동 점 하나: 어느 인스턴스 상태의 어느 자리. */
    static final class Point {
        final CircuitState state;
        final Location at;

        Point(CircuitState state, Location at) {
            this.state = state;
            this.at = at;
        }
    }

    /** 원조 전파기가 기록한 진동 점들. 읽지 못하면(원조가 바뀌었으면) 빈 목록. */
    static List<Point> points(CircuitState root) {
        List<Point> out = new ArrayList<>();
        try {
            Propagator p = root.getPropagator();
            Field osc = Propagator.class.getDeclaredField("oscPoints");
            osc.setAccessible(true);
            Object pts = osc.get(p);
            Field data = pts.getClass().getDeclaredField("data");
            data.setAccessible(true);
            Collection<?> set = new ArrayList<>((Collection<?>) data.get(pts));
            for (Object e : set) {
                Field st = e.getClass().getDeclaredField("state");
                Field loc = e.getClass().getDeclaredField("loc");
                st.setAccessible(true);
                loc.setAccessible(true);
                out.add(new Point((CircuitState) st.get(e), (Location) loc.get(e)));
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return new ArrayList<>();
        }
        return out;
    }

    /**
     * 진동 진단 하나. 점이 가장 많은 인스턴스(같으면 위쪽)를 고리로 보고, 그 점에 포트가 닿는 부품(터널·스플리터
     * 제외)과 그 점의 넷 선을 강조한다. 점이 없으면 null.
     */
    static Diagnostic diagnose(Circuit top, List<Point> points, int step) {
        Map<CircuitState, Set<Location>> byState = new LinkedHashMap<>();
        for (Point p : points) {
            byState.computeIfAbsent(p.state, k -> new LinkedHashSet<>()).add(p.at);
        }
        CircuitState best = null;
        int bestDepth = Integer.MAX_VALUE;
        for (Map.Entry<CircuitState, Set<Location>> e : byState.entrySet()) {
            int depth = Recorder.pathOf(e.getKey()).size();
            if (best == null || e.getValue().size() > byState.get(best).size()
                    || e.getValue().size() == byState.get(best).size() && depth < bestDepth) {
                best = e.getKey();
                bestDepth = depth;
            }
        }
        if (best == null) {
            return null;
        }
        Circuit c = best.getCircuit();
        Set<Location> at = byState.get(best);
        List<Component> comps = new ArrayList<>();
        for (Component x : c.getNonWires()) {
            String f = Kinds.of(x).factory();
            if (f.equals("Tunnel") || f.equals("Splitter")) {
                continue;
            }
            for (int i = 0; i < x.getEnds().size(); i++) {
                if (at.contains(x.getEnd(i).getLocation())) {
                    comps.add(x);
                    break;
                }
            }
        }
        comps.sort(java.util.Comparator.comparingInt((Component x) -> x.getLocation().getY())
                .thenComparingInt(x -> x.getLocation().getX()));
        Netlist nl = Netlist.of(c);
        Set<Wire> wires = new LinkedHashSet<>();
        for (Location l : at) {
            Netlist.Net n = nl.netAt(l);
            if (n != null) {
                wires.addAll(n.wires());
            }
        }
        List<Component> path = Recorder.pathOf(best);
        List<String> names = new ArrayList<>();
        for (Component x : comps) {
            if (names.size() == 6) {
                names.add("…");
                break;
            }
            names.add(Names.name(c, x));
        }
        Location loc = comps.isEmpty() ? at.iterator().next() : comps.get(0).getLocation();
        return new Diagnostic(Diagnostic.Kind.OSCILLATION, c, path, step, comps, new ArrayList<>(wires), loc,
                DynamicCheck.cycleOf(step), InstancePaths.describe(top, path), String.join(", ", names));
    }
}
