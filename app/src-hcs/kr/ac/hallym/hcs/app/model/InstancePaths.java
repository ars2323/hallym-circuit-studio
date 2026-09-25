/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.Instance;

/**
 * 서브회로 인스턴스 경로와 핀 변경 영향(P-02, PLAN.md 11.10). GUI 없이 회로 모델만 본다.
 * <ul>
 * <li>{@link #paths}: main에서 어떤 서브회로까지 가는 인스턴스 경로(위치 순, 결정적).</li>
 * <li>{@link #stateFor}: 그 경로의 실행 중 상태(원조 {@code SubcircuitFactory.getSubstate}를 따라 내려간다).</li>
 * <li>{@link #snapshot}·{@link #broken}: 서브회로의 핀을 바꾸기 전후, 부모 회로의 인스턴스 포트 중 이어져 있다가
 * 끊긴 것.</li>
 * </ul>
 */
public final class InstancePaths {
    /** 경로가 너무 많으면(깊은 계층) 이만큼만. */
    static final int MAX_PATHS = 64;

    private InstancePaths() {
    }

    /** root에서 target까지의 인스턴스 경로들(각 경로는 root부터 차례로 거치는 서브회로 부품). */
    public static List<List<Component>> paths(Circuit root, Circuit target) {
        List<List<Component>> out = new ArrayList<>();
        if (root == null || target == null || root == target) {
            return out;
        }
        walk(root, target, new ArrayList<>(), out, Collections.newSetFromMap(new IdentityHashMap<>()));
        return out;
    }

    private static void walk(Circuit at, Circuit target, List<Component> path, List<List<Component>> out,
            java.util.Set<Circuit> onPath) {
        if (out.size() >= MAX_PATHS || !onPath.add(at)) {
            return;
        }
        for (Component c : sorted(at.getNonWires())) {
            if (!(c.getFactory() instanceof SubcircuitFactory)) {
                continue;
            }
            Circuit sub = ((SubcircuitFactory) c.getFactory()).getSubcircuit();
            path.add(c);
            if (sub == target) {
                out.add(new ArrayList<>(path));
            } else {
                walk(sub, target, path, out, onPath);
            }
            path.remove(path.size() - 1);
        }
        onPath.remove(at);
    }

    static List<Component> sorted(Collection<Component> cs) {
        List<Component> ret = new ArrayList<>(cs);
        ret.sort(Comparator.<Component>comparingInt(c -> c.getLocation().getY())
                .thenComparingInt(c -> c.getLocation().getX()).thenComparing(c -> c.getFactory().getName()));
        return ret;
    }

    /** "main › datapath › alu": 인스턴스마다 학생 라벨, 없으면 서브회로 이름. */
    public static String describe(Circuit root, List<Component> path) {
        List<String> parts = new ArrayList<>();
        parts.add(root.getName());
        for (Component c : path) {
            String l = Names.label(c);
            parts.add(l != null ? l : c.getFactory().getName());
        }
        return Names.path(parts);
    }

    /** rootState(맨 위 회로의 실행 상태)에서 경로를 따라 내려간 인스턴스의 상태. */
    public static CircuitState stateFor(CircuitState rootState, List<Component> path) {
        CircuitState s = rootState;
        for (Component inst : path) {
            if (s == null) {
                return null;
            }
            s = ((SubcircuitFactory) inst.getFactory()).getSubstate(s, inst);
        }
        return s;
    }

    // ---- 핀 변경 영향 ----

    /** 서브회로 sub의 인스턴스 포트 하나: 어느 회로의 어느 인스턴스, 어떤 핀, 어디, 이어져 있었는가. */
    public static final class PortUse {
        public final Circuit parent;
        public final Component instance;
        public final Component pin;
        public final Location at;
        public final boolean connected;

        PortUse(Circuit parent, Component instance, Component pin, Location at, boolean connected) {
            this.parent = parent;
            this.instance = instance;
            this.pin = pin;
            this.at = at;
            this.connected = connected;
        }
    }

    /** 파일의 모든 회로에서 sub 인스턴스의 포트 사용(핀 → 부모 회로의 포트 자리, 이어짐). */
    public static List<PortUse> snapshot(LogisimFile file, Circuit sub) {
        List<PortUse> out = new ArrayList<>();
        for (Circuit parent : file.getCircuits()) {
            Netlist nl = null;
            for (Component inst : sorted(parent.getNonWires())) {
                if (!(inst.getFactory() instanceof SubcircuitFactory)
                        || ((SubcircuitFactory) inst.getFactory()).getSubcircuit() != sub) {
                    continue;
                }
                if (nl == null) {
                    nl = Netlist.of(parent);
                }
                for (int end = 0; end < inst.getEnds().size(); end++) {
                    Instance pin = Trace.pinFor(inst, end);
                    if (pin == null) {
                        continue;
                    }
                    Location at = inst.getEnd(end).getLocation();
                    out.add(new PortUse(parent, inst, Instance.getComponentFor(pin), at, connected(parent, nl, inst,
                            end)));
                }
            }
        }
        return out;
    }

    /** 포트가 무엇인가에 이어져 있는가: 넷에 다른 포트나 선이 있다. */
    static boolean connected(Circuit parent, Netlist nl, Component inst, int end) {
        Netlist.Net n = nl.netOf(inst, end);
        if (n == null) {
            return false;
        }
        if (!n.wires().isEmpty()) {
            return true;
        }
        for (Netlist.PortRef p : n.ports()) {
            if (p.component != inst) {
                return true;
            }
        }
        return false;
    }

    /** 핀 pins를 지우거나 옮기면 영향을 받는 이어진 포트(미리 보기): 그 핀들의 이어진 인스턴스 포트. */
    public static List<PortUse> affected(LogisimFile file, Circuit sub, Collection<Component> pins) {
        List<PortUse> out = new ArrayList<>();
        for (PortUse u : snapshot(file, sub)) {
            if (u.connected && containsIdentity(pins, u.pin)) {
                out.add(u);
            }
        }
        return out;
    }

    private static boolean containsIdentity(Collection<Component> cs, Component c) {
        for (Component x : cs) {
            if (x == c) {
                return true;
            }
        }
        return false;
    }

    /** 이어진 포트가 있는 인스턴스 수. */
    public static int instances(List<PortUse> uses) {
        Map<Component, Boolean> m = new IdentityHashMap<>();
        for (PortUse u : uses) {
            m.put(u.instance, true);
        }
        return m.size();
    }

    /** 끊긴 연결: 바꾸기 전 이어져 있던 포트가, 바꾼 뒤 사라졌거나(핀 삭제) 자리가 바뀌어 이어지지 않는다. */
    public static final class Broken {
        public final PortUse before;
        /** 바뀐 뒤 같은 핀의 포트 자리. 핀이 없어졌으면 null. */
        public final Location now;

        Broken(PortUse before, Location now) {
            this.before = before;
            this.now = now;
        }
    }

    public static List<Broken> broken(List<PortUse> before, List<PortUse> after) {
        Map<String, PortUse> now = new LinkedHashMap<>();
        for (PortUse u : after) {
            now.put(key(u.instance, u.pin), u);
        }
        List<Broken> out = new ArrayList<>();
        for (PortUse b : before) {
            if (!b.connected) {
                continue;
            }
            PortUse a = now.get(key(b.instance, b.pin));
            if (a == null) {
                out.add(new Broken(b, null));
            } else if (!a.connected) {
                out.add(new Broken(b, a.at));
            }
        }
        return out;
    }

    /**
     * 인스턴스와 핀을 잇는 열쇠. 핀을 옮기면 원조가 핀 부품을 새로 만들므로 핀은 라벨로 알아본다(라벨이 없으면
     * 부품 그대로). 인스턴스 부품은 서브회로 모양이 바뀌어도 그대로다.
     */
    private static String key(Component inst, Component pin) {
        String l = Names.label(pin);
        return System.identityHashCode(inst) + "/" + (l != null ? "label:" + l : "id:" + System.identityHashCode(pin));
    }

    /** 끊긴 자리 옆에 옛 선 끝이 그대로 있는가(다시 이을 수 있는 경우). */
    public static boolean wireEndsAt(Circuit parent, Location at) {
        for (Wire w : parent.getWires()) {
            if (w.endsAt(at)) {
                return true;
            }
        }
        return false;
    }
}
