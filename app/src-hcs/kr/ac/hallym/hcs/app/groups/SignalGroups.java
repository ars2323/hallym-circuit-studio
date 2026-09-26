/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.groups;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.Settings;
import kr.ac.hallym.hcs.app.ext.CircExtension;
import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.probe.QuickProbe;

/**
 * 신호 그룹(E-04, PLAN.md 11.12): 넷을 제어(Control)·데이터(Data)·주소(Address)로 나눈다. 학생이 우클릭으로 정한 그룹은
 * .circ 확장 정보(PLAN.md 7.0, {@code group net=… group=…})에 회로마다 둔다. 이름 있는 넷(터널·핀·라벨)은 이름으로,
 * 이름 없는 넷은 가장 작은 자리({@code @x,y})로 가리킨다. {@code control}이라는 서브회로의 출력 넷은 정하지 않아도
 * 제어다. 선 색은 값의 뜻이므로 그룹 색은 "Colors: Groups"일 때 선 옆 얇은 테두리와 라벨 칩에만 칠한다.
 */
public final class SignalGroups {
    /** 그룹. 색은 색각 이상을 고려한 Okabe–Ito 팔레트에서 선 값 색(초록·파랑·빨강)과 먼 것. */
    public enum Group {
        CONTROL(new Color(0xE69F00)), DATA(new Color(0xCC79A7)), ADDRESS(new Color(0x56B4E9));

        public final Color color;

        Group(Color color) {
            this.color = color;
        }

        public String key() {
            return name().toLowerCase();
        }
    }

    static final String KIND = "group";
    static final String MODE = "wires.colorMode";

    /** 저장 전: 가리키는 넷이 사라진 항목을 지운다. */
    public static final CircExtensions.Pruner PRUNER = (file, ext) -> {
        for (Circuit c : file.getCircuits()) {
            Map<String, Netlist.Net> keys = keys(c);
            for (CircExtension.Item item : ext.items(c.getName())) {
                if (item.kind().equals(KIND) && !keys.containsKey(item.get("net"))) {
                    ext.remove(c.getName(), item);
                }
            }
        }
    };

    private SignalGroups() {
    }

    /** 그룹 색 보기(Colors: Groups)인가. 기본은 값(Values). */
    public static boolean showGroups() {
        return "groups".equals(Settings.get().getString(MODE, "values"));
    }

    public static void setShowGroups(boolean on) {
        Settings.get().set(MODE, on ? "groups" : "values");
        try {
            Settings.get().save();
        } catch (java.io.IOException e) {
            // 환경설정을 못 써도 이번 실행에는 바뀐다
        }
    }

    /** 넷을 가리키는 열쇠: 이름, 없으면 가장 작은 자리. */
    static String key(Circuit c, Netlist.Net net) {
        String name = QuickProbe.netName(c, net);
        if (!name.isEmpty()) {
            return name;
        }
        Location best = null;
        for (Netlist.PortRef p : net.ports()) {
            if (best == null || p.location().compareTo(best) < 0) {
                best = p.location();
            }
        }
        for (Wire w : net.wires()) {
            for (Location l : new Location[] {w.getEnd0(), w.getEnd1()}) {
                if (best == null || l.compareTo(best) < 0) {
                    best = l;
                }
            }
        }
        return best == null ? "" : "@" + best.getX() + "," + best.getY();
    }

    /** 회로의 넷 열쇠 → 넷. */
    static Map<String, Netlist.Net> keys(Circuit c) {
        Map<String, Netlist.Net> out = new LinkedHashMap<>();
        for (Netlist.Net n : Netlist.of(c).nets()) {
            if (!n.wires().isEmpty() || n.ports().size() > 1) {
                out.putIfAbsent(key(c, n), n);
            }
        }
        return out;
    }

    /** 학생이 정한 그룹(없으면 null). */
    public static Group assigned(LogisimFile file, Circuit c, String key) {
        for (CircExtension.Item item : CircExtensions.of(file).items(c.getName())) {
            if (item.kind().equals(KIND) && key.equals(item.get("net"))) {
                try {
                    return Group.valueOf(item.get("group").toUpperCase());
                } catch (RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 제어 유닛의 출력 넷인가(control이라는 서브회로 인스턴스의 출력 포트에 닿음). */
    static boolean fromControl(Netlist.Net net) {
        for (Netlist.PortRef p : net.drivers()) {
            if (p.component.getFactory() instanceof SubcircuitFactory
                    && p.component.getFactory().getName().equalsIgnoreCase("control")) {
                return true;
            }
        }
        return false;
    }

    /** 회로의 넷마다 그룹(정한 것, 없으면 제어 유닛 출력이면 제어). 그룹 없는 넷은 빠진다. */
    public static Map<Netlist.Net, Group> of(LogisimFile file, Circuit c) {
        Map<Netlist.Net, Group> out = new LinkedHashMap<>();
        for (Map.Entry<String, Netlist.Net> e : keys(c).entrySet()) {
            Group g = file == null ? null : assigned(file, c, e.getKey());
            if (g == null && fromControl(e.getValue())) {
                g = Group.CONTROL;
            }
            if (g != null) {
                out.put(e.getValue(), g);
            }
        }
        return out;
    }

    /** 선마다 그룹(그룹 없는 선은 빠진다). 선 객체는 넷 목록을 다시 만들어도 같다. */
    public static Map<Wire, Group> wireGroups(LogisimFile file, Circuit c) {
        Map<Wire, Group> out = new java.util.HashMap<>();
        for (Map.Entry<Netlist.Net, Group> e : of(file, c).entrySet()) {
            for (Wire w : e.getKey().wires()) {
                out.put(w, e.getValue());
            }
        }
        return out;
    }

    static void set(LogisimFile file, Circuit c, String key, Group g) {
        CircExtension ext = CircExtensions.of(file);
        for (CircExtension.Item item : ext.items(c.getName())) {
            if (item.kind().equals(KIND) && key.equals(item.get("net"))) {
                ext.remove(c.getName(), item);
            }
        }
        if (g != null) {
            Map<String, String> a = new LinkedHashMap<>();
            a.put("net", key);
            a.put("group", g.key());
            ext.add(c.getName(), new CircExtension.Item(KIND, a));
        }
    }

    /** 이 선의 넷에 그룹을 정하는 동작(null이면 해제, 되돌리기 한 번). */
    public static Action action(LogisimFile file, Circuit c, Wire w, Group g) {
        String key = key(c, Netlist.of(c).netOf(w));
        return new Action() {
            private Group before;

            @Override
            public String getName() {
                return Messages.get("group.action");
            }

            @Override
            public void doIt(Project proj) {
                before = assigned(file, c, key);
                set(file, c, key, g);
                proj.repaintCanvas();
            }

            @Override
            public void undo(Project proj) {
                set(file, c, key, before);
                proj.repaintCanvas();
            }
        };
    }

    /** 이 선의 그룹(표시할 것). */
    public static Group groupOf(LogisimFile file, Circuit c, Wire w) {
        return wireGroups(file, c).get(w);
    }

    static boolean isControlOutput(Component c) {
        return c.getFactory() instanceof SubcircuitFactory && c.getFactory().getName().equalsIgnoreCase("control");
    }
}
