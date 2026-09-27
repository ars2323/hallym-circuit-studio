/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.mips;

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
 * 회로 상태 안의 MIPS 메모리 부품(Data Memory, 옛 Stack)을 {@link MemoryTable.Part}로 읽는다(D-140). lib-mips는 JAR
 * 라이브러리로 따로 불려서 엔진이 그 클래스를 컴파일 때 모른다. 그래서 부품 상태 객체의 공개 메서드(readWord, isDefined,
 * pageAddresses, dataRegion, stackRegion, lowestAccess, depthBase)를 이름으로 부른다. 읽기만 한다.
 */
public final class MipsParts {
    private MipsParts() {
    }

    /** root 회로 상태에서 서브회로 안까지 MIPS 메모리 부품들(회로 안 부품 순서, 서브회로는 그 자리에서). */
    public static List<MemoryTable.Part> collect(Circuit root, CircuitState rootState) {
        List<MemoryTable.Part> out = new ArrayList<>();
        collect(root, root, rootState, new ArrayList<Component>(), out);
        return out;
    }

    private static void collect(Circuit root, Circuit c, CircuitState s, List<Component> path,
            List<MemoryTable.Part> out) {
        if (s == null) {
            return;
        }
        for (Component x : c.getNonWires()) {
            String f = x.getFactory().getName();
            if (f.equals("Data Memory") || f.equals("Stack")) {
                Object data = s.getData(x);
                if (data != null) {
                    String label = Names.label(x);
                    String name = label != null ? label : f;
                    if (!path.isEmpty()) { // 메모리 패널(C-06)과 같은 이름: 맨 위 회로 이름은 뺀다
                        name = InstancePaths.describe(root, path).substring(root.getName().length()
                                + Names.SEP.length()) + Names.SEP + name;
                    }
                    View v = View.of(name, data);
                    if (v != null) {
                        out.add(v);
                    }
                }
            } else if (x.getFactory() instanceof SubcircuitFactory) {
                Object d = s.getData(x);
                path.add(x);
                collect(root, ((SubcircuitFactory) x.getFactory()).getSubcircuit(), d instanceof CircuitState
                        ? (CircuitState) d : null, path, out);
                path.remove(path.size() - 1);
            }
        }
    }

    /** lib-mips DataMemory.State를 이름으로 부르는 창. */
    static final class View implements MemoryTable.Part {
        private final String name;
        private final Object state;
        private final Method readWord;
        private final Method isDefined;
        private final Method pages;
        private final Method dataRegion;
        private final Method stackRegion;
        private final Method lowest;
        private final Method base;

        private View(String name, Object state) throws ReflectiveOperationException {
            this.name = name;
            this.state = state;
            Class<?> k = state.getClass();
            readWord = open(k.getMethod("readWord", int.class));
            isDefined = open(k.getMethod("isDefined", int.class));
            pages = open(k.getMethod("pageAddresses"));
            dataRegion = open(k.getMethod("dataRegion"));
            stackRegion = open(k.getMethod("stackRegion"));
            lowest = open(k.getMethod("lowestAccess"));
            base = open(k.getMethod("depthBase"));
        }

        static View of(String name, Object state) {
            try {
                return new View(name, state);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null; // D-140 전 lib-mips 등: 표에 싣지 않는다
            }
        }

        private static Method open(Method m) {
            m.setAccessible(true); // 공개 메서드지만 클래스가 패키지 전용
            return m;
        }

        private Object call(Method m, Object... args) {
            try {
                return m.invoke(state, args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public long[] dataRegion() {
            return (long[]) call(dataRegion);
        }

        @Override
        public long[] stackRegion() {
            return (long[]) call(stackRegion);
        }

        @Override
        public int word(long addr) {
            return (Integer) call(readWord, (int) addr);
        }

        @Override
        public boolean defined(long addr) {
            return (Boolean) call(isDefined, (int) addr);
        }

        @Override
        public long[] pages() {
            return (long[]) call(pages);
        }

        @Override
        public long lowestAccess() {
            return (Long) call(lowest);
        }

        @Override
        public long depthBase() {
            return (Long) call(base);
        }
    }
}
