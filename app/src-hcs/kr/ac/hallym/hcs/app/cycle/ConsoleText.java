/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;

import kr.ac.hallym.hcs.app.model.InstancePaths;
import kr.ac.hallym.hcs.app.model.Names;

/**
 * Console 탭의 모델(C-09): 회로 상태 나무의 모든 lib-mips Console 출력 전체와 exit 여부. 부품 몸통은 마지막 몇 줄만
 * 보이므로 탭이 전체를 모아 보인다. lib-mips는 JAR 라이브러리로 따로 불려서 상태의 text()와 exited를 이름으로 읽는다.
 */
public final class ConsoleText {
    /** Console 하나. */
    public static final class Entry {
        public final String name;
        public final String text;
        public final boolean exited;

        Entry(String name, String text, boolean exited) {
            this.name = name;
            this.text = text;
            this.exited = exited;
        }
    }

    private ConsoleText() {
    }

    /** rootState(보고 있는 사이클의 최상위 상태) 안의 모든 Console. */
    public static List<Entry> collect(CircuitState rootState) {
        List<Entry> out = new ArrayList<>();
        if (rootState != null) {
            Circuit root = rootState.getCircuit();
            collect(root, root, rootState, new ArrayList<Component>(), out);
        }
        return out;
    }

    private static void collect(Circuit root, Circuit c, CircuitState s, List<Component> path, List<Entry> out) {
        for (Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals("Console")) {
                Object st = s.getData(x);
                String label = Names.label(x);
                String name = label != null ? label : "Console";
                if (!path.isEmpty()) {
                    name = InstancePaths.describe(root, path).substring(root.getName().length() + Names.SEP.length())
                            + Names.SEP + name;
                }
                out.add(new Entry(name, text(st), exited(st)));
            } else if (x.getFactory() instanceof SubcircuitFactory) {
                Object d = s.getData(x);
                if (d instanceof CircuitState) {
                    path.add(x);
                    collect(root, ((SubcircuitFactory) x.getFactory()).getSubcircuit(), (CircuitState) d, path, out);
                    path.remove(path.size() - 1);
                }
            }
        }
    }

    static String text(Object st) {
        if (st == null) {
            return "";
        }
        try {
            Method m = st.getClass().getDeclaredMethod("text");
            m.setAccessible(true);
            return String.valueOf(m.invoke(st));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    static boolean exited(Object st) {
        if (st == null) {
            return false;
        }
        try {
            Field f = st.getClass().getDeclaredField("exited");
            f.setAccessible(true);
            return f.getBoolean(st);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
