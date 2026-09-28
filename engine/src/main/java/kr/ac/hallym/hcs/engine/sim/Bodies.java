/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.sim;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.util.StringUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 부품 몸체에 보이는 상태 가운데 넷 값에 없는 것(N-05, D-137, docs/engine-api.md {@code sim.values.bodies}).
 * 레지스터·카운터·핀처럼 값이 출력 넷에 그대로 있는 부품은 싣지 않는다(화면이 넷 값으로 그린다). 싣는 것:
 * <ul>
 * <li>RAM·ROM: 원조가 몸체에 그리는 4줄 표(원조 {@code MemState}의 스크롤 자리, 지금 주소).</li>
 * <li>Shift Register: 단마다의 값(원조가 병렬 적재일 때 칸마다 그리는 값).</li>
 * <li>Hallym MIPS Instruction Memory·Data Memory·Stack: 몸체 줄과 빨간 상태 글(lib-mips {@code bodyLines},
 * {@code status}, 두 영역의 범위와 쓰임, D-140).</li>
 * <li>Console: 출력의 마지막 줄들과 상태 글(-- exit --, 떠 있는 Syscall).</li>
 * <li>Radix Probe: 세 진법 줄(주 진법이 첫 줄, 조작 도구로 바꾼 주 진법 포함).</li>
 * </ul>
 * lib-mips는 JAR 라이브러리로 따로 불려 엔진이 컴파일할 때 모르므로 이름으로(반사) 부른다({@link
 * kr.ac.hallym.hcs.engine.mips.MipsParts}와 같은 방식). 원조 기억 장치의 패키지 전용 상태도 반사로 읽기만 한다:
 * 회로 상태에 있는 데이터({@code CircuitState.getData})만 보고, 없으면 만들지 않는다(몸체 상태 없음). 몸체를 읽어도
 * 시뮬레이션 상태는 그대로다({@code CanvasDataTest}).
 * 그리기 문맥은 화면에 붙지 않는 작은 그림(headless에서도 된다)이고 아무것도 그리지 않는다.
 */
public final class Bodies {
    private static final String MIPS = "kr.ac.hallym.hcs.mips.";
    private static final String MEMORY = "com.cburch.logisim.std.memory.";

    private final Map<String, Method> methods = new HashMap<>();
    private final BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private java.lang.reflect.Constructor<InstancePainter> painterConstructor;

    /** 몸체 상태 JSON. 이 종류는 넷 값만으로 그리거나 상태가 아직 없으면 null. */
    public JsonObject of(Component c, Circuit circuit, CircuitState state) {
        String k = c.getFactory().getClass().getName();
        Instance ic = Instance.getInstanceFor(c);
        if (ic == null) {
            return null;
        }
        try {
            switch (k) {
                case MEMORY + "Ram":
                case MEMORY + "Rom":
                    return memoryGrid(ic, state);
                case MEMORY + "ShiftRegister":
                    return stages(ic, state);
                case MIPS + "InstructionMemory":
                case MIPS + "DataMemory":
                case MIPS + "StackMemory":
                    return mipsLines(ic, circuit, state);
                case MIPS + "Console":
                    return console(ic, state);
                case MIPS + "RadixProbe":
                    return radix(ic, circuit, state);
                default:
                    return null;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null; // 다른 판의 라이브러리 등: 몸체 상태 없이 그린다
        }
    }

    /**
     * 원조 RAM·ROM 몸체의 표: 스크롤 자리부터 4줄, 줄마다 columns 워드. 회로 상태에 이미 있는 MemState만 읽는다. 원조
     * {@code Mem.getState}는 없으면 만들어 넣으므로(RAM은 setRam까지) 부르지 않는다: 아직 없으면(시뮬레이션이 한 번도
     * 이 부품을 돌리지 않음) 몸체 상태도 없다.
     */
    private JsonObject memoryGrid(Instance ic, CircuitState state) throws ReflectiveOperationException {
        Class<?> k = Class.forName(MEMORY + "MemState");
        Object ms = state.getData(Instance.getComponentFor(ic));
        if (!k.isInstance(ms)) {
            return null;
        }
        int addrBits = (Integer) method(k, "getAddrBits").invoke(ms);
        int columns = (Integer) method(k, "getColumns").invoke(ms);
        int rows = (Integer) method(k, "getRows").invoke(ms);
        long scroll = (Long) method(k, "getScroll").invoke(ms);
        long current = (Long) method(k, "getCurrent").invoke(ms);
        Object contents = method(k, "getContents").invoke(ms);
        Method get = method(contents.getClass(), "get", long.class);
        int dataBits = (Integer) method(contents.getClass(), "getWidth").invoke(contents);
        long last = (1L << addrBits) - 1;
        JsonArray out = new JsonArray();
        for (int row = 0; row < rows; row++) {
            long addr = scroll / columns * columns + (long) columns * row;
            if (addr < 0 || addr > last) {
                break;
            }
            JsonObject r = new JsonObject();
            r.addProperty("addr", StringUtil.toHexString(addrBits, (int) addr));
            JsonArray words = new JsonArray();
            for (int col = 0; col < columns && addr + col <= last; col++) {
                words.add(StringUtil.toHexString(dataBits, (Integer) get.invoke(contents, addr + col)));
            }
            r.add("words", words);
            out.add(r);
        }
        JsonObject o = new JsonObject();
        o.addProperty("columns", columns);
        o.add("rows", out);
        if (current >= 0 && current <= last) {
            o.addProperty("current", StringUtil.toHexString(addrBits, (int) current));
        }
        return o;
    }

    /** 원조 Shift Register의 단마다의 값(0번이 가장 최근에 들어온 값). */
    private JsonObject stages(Instance ic, CircuitState state) throws ReflectiveOperationException {
        Object data = state.getData(Instance.getComponentFor(ic));
        if (data == null) {
            return null;
        }
        Class<?> k = Class.forName(MEMORY + "ShiftRegisterData");
        int n = (Integer) method(k, "getLength").invoke(data);
        Method get = method(k, "get", int.class);
        JsonArray a = new JsonArray();
        for (int i = 0; i < n; i++) {
            a.add(((Value) get.invoke(data, i)).toHexString());
        }
        JsonObject o = new JsonObject();
        o.add("stages", a);
        return o;
    }

    /** lib-mips 메모리 부품의 몸체 줄과 상태 글(원조 캔버스의 Swing 몸체와 같은 글). */
    private JsonObject mipsLines(Instance ic, Circuit circuit, CircuitState state)
            throws ReflectiveOperationException {
        Object factory = ic.getFactory();
        Class<?> base = Class.forName(MIPS + "MemoryFactory", true, factory.getClass().getClassLoader());
        InstancePainter painter = painter(ic, circuit, state);
        String[] lines = (String[]) method(base, "bodyLines", InstancePainter.class).invoke(factory, painter);
        String status = (String) method(base, "status", InstancePainter.class).invoke(factory, painter);
        JsonObject o = new JsonObject();
        o.add("lines", strings(java.util.Arrays.asList(lines)));
        if (status != null) {
            o.addProperty("status", status);
        }
        return o;
    }

    /** Console: 출력의 마지막 줄들(원조 몸체와 같은 접기)과 상태 글. */
    @SuppressWarnings("unchecked")
    private JsonObject console(Instance ic, CircuitState state) throws ReflectiveOperationException {
        Object st = state.getData(Instance.getComponentFor(ic));
        Class<?> k = ic.getFactory().getClass();
        JsonObject o = new JsonObject();
        if (st == null) {
            o.add("lines", new JsonArray());
            return o;
        }
        String text = (String) method(st.getClass(), "text").invoke(st);
        int rows = (Integer) field(k, "ROWS").get(null);
        int columns = (Integer) field(k, "COLUMNS").get(null);
        List<String> lines = (List<String>) method(k, "lastLines", String.class, int.class, int.class)
                .invoke(null, text, rows, columns);
        o.add("lines", strings(lines));
        boolean exited = (Boolean) field(st.getClass(), "exited").get(st);
        boolean floating = (Boolean) field(st.getClass(), "syscallFloating").get(st);
        String status = (String) field(st.getClass(), "status").get(st);
        o.addProperty("exited", exited);
        if (exited) {
            o.addProperty("status", "-- exit --");
        } else if (floating) {
            o.addProperty("status", "Syscall floating");
            o.addProperty("error", true);
        } else if (status != null) {
            o.addProperty("status", status);
            o.addProperty("error", true);
        }
        return o;
    }

    /** Radix Probe: 주 진법이 첫 줄인 세 줄(16·10·2진수). */
    private JsonObject radix(Instance ic, Circuit circuit, CircuitState state)
            throws ReflectiveOperationException {
        Class<?> k = ic.getFactory().getClass();
        InstancePainter painter = painter(ic, circuit, state);
        Object st = state.getData(Instance.getComponentFor(ic));
        Class<?> stateClass = Class.forName(k.getName() + "$State", true, k.getClassLoader());
        int primary = (Integer) method(k, "primary", InstanceState.class, stateClass).invoke(null, painter, st);
        boolean signed = (Boolean) painter.getAttributeValue(attribute(k, "SIGNED"));
        Value v = painter.getPort(0);
        String[] lines = (String[]) method(k, "lines", Value.class, int.class, boolean.class)
                .invoke(null, v, primary, signed);
        JsonObject o = new JsonObject();
        o.add("lines", strings(java.util.Arrays.asList(lines)));
        o.addProperty("primary", primary);
        return o;
    }

    /** 원조 그리기와 같은 창(InstancePainter). 그 생성자가 패키지 전용 부품 클래스를 받아 반사로 만든다. */
    private InstancePainter painter(Instance ic, Circuit circuit, CircuitState state)
            throws ReflectiveOperationException {
        Graphics g = scratch.createGraphics();
        ComponentDrawContext ctx = new ComponentDrawContext(null, circuit, state, g, g);
        if (painterConstructor == null) {
            Class<?> k = Class.forName("com.cburch.logisim.instance.InstanceComponent");
            painterConstructor = InstancePainter.class.getDeclaredConstructor(ComponentDrawContext.class, k);
            painterConstructor.setAccessible(true);
        }
        return painterConstructor.newInstance(ctx, Instance.getComponentFor(ic));
    }

    @SuppressWarnings("unchecked")
    private static com.cburch.logisim.data.Attribute<Object> attribute(Class<?> k, String name)
            throws ReflectiveOperationException {
        return (com.cburch.logisim.data.Attribute<Object>) field(k, name).get(null);
    }

    private static java.lang.reflect.Field field(Class<?> k, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field f = k.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private Method method(Class<?> k, String name, Class<?>... args) throws ReflectiveOperationException {
        String key = k.getName() + "#" + name + "/" + args.length;
        Method m = methods.get(key);
        if (m == null) {
            m = find(k, name, args);
            m.setAccessible(true);
            methods.put(key, m);
        }
        return m;
    }

    private static Method find(Class<?> k, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> c = k; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, args);
            } catch (NoSuchMethodException e) {
                // 위 클래스에서 찾는다
            }
        }
        throw new NoSuchMethodException(k.getName() + "." + name);
    }

    private static JsonArray strings(List<String> items) {
        JsonArray a = new JsonArray();
        for (String s : items) {
            a.add(s);
        }
        return a;
    }
}
