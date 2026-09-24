/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.regress;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;

/**
 * 원조 Logisim 2.7.1의 부품 객체로 회로를 만든다. 포트 좌표는 규칙으로 계산하지 않고 부품에서 받는다
 * (PLAN.md 부록 A.2). 연결은 주로 같은 라벨의 터널을 포트 위에 놓아 만든다. 선 모양 때문에 생기는 우연한
 * 합선·단선을 피하기 위해서다. 곧은 선은 {@link #wire}로 따로 긋는다.
 */
final class CircuitBuilder {
    private final LogisimFile file;
    private final Circuit circuit;
    private final List<Component> pending = new ArrayList<Component>();

    CircuitBuilder(LogisimFile file, Circuit circuit) {
        this.file = file;
        this.circuit = circuit;
    }

    Circuit circuit() {
        return circuit;
    }

    /** 기본 라이브러리 부품을 (x, y)에 놓는다. attrs는 저장 이름과 값의 쌍이다(.circ의 {@code <a name val>}). */
    Component add(String library, String name, int x, int y, String... attrs) {
        Library lib = file.getLoader().getBuiltin().getLibrary(library);
        if (lib == null) {
            throw new IllegalArgumentException("no library " + library);
        }
        AddTool tool = (AddTool) lib.getTool(name);
        if (tool == null) {
            throw new IllegalArgumentException("no component " + library + "/" + name);
        }
        return place(tool.getFactory(), x, y, attrs);
    }

    /** 이 파일의 다른 회로를 서브회로로 놓는다. */
    Component addSubcircuit(Circuit sub, int x, int y) {
        return place(sub.getSubcircuitFactory(), x, y);
    }

    private Component place(ComponentFactory factory, int x, int y, String... attrs) {
        AttributeSet as = factory.createAttributeSet();
        for (int i = 0; i < attrs.length; i += 2) {
            set(as, attrs[i], attrs[i + 1]);
        }
        Component c = factory.createComponent(Location.create(x, y), as);
        pending.add(c);
        return c;
    }

    @SuppressWarnings("unchecked")
    private static void set(AttributeSet as, String name, String value) {
        Attribute<Object> attr = (Attribute<Object>) as.getAttribute(name);
        if (attr == null) {
            throw new IllegalArgumentException("no attribute " + name);
        }
        as.setValue(attr, attr.parse(value));
    }

    static Location port(Component c, int index) {
        return c.getEnds().get(index).getLocation();
    }

    static int width(Component c, int index) {
        EndData end = c.getEnds().get(index);
        return end.getWidth().getWidth();
    }

    /** 포트 위에 라벨 터널을 놓는다. 같은 라벨의 터널끼리 연결된다. */
    void tunnel(Component c, int index, String label) {
        Location at = port(c, index);
        add("Wiring", "Tunnel", at.getX(), at.getY(),
                "width", Integer.toString(width(c, index)), "label", label);
    }

    /** 출력 핀을 만들고 라벨 터널로 연결한다. */
    Component output(String label, int width, int x, int y) {
        Component pin = add("Wiring", "Pin", x, y,
                "facing", "west", "output", "true", "width", Integer.toString(width), "label", label);
        tunnel(pin, 0, label);
        return pin;
    }

    /** 입력 핀(서브회로의 포트)을 만들고 라벨 터널로 연결한다. */
    Component input(String label, int width, int x, int y) {
        Component pin = add("Wiring", "Pin", x, y, "width", Integer.toString(width), "label", label);
        tunnel(pin, 0, label);
        return pin;
    }

    /** 상수를 만들고 라벨 터널로 연결한다. */
    Component constant(String label, int width, int value, int x, int y) {
        Component k = add("Wiring", "Constant", x, y,
                "width", Integer.toString(width), "value", "0x" + Integer.toHexString(value));
        tunnel(k, 0, label);
        return k;
    }

    /** 두 점을 가로 또는 세로 곧은 선으로 잇는다. */
    void wire(Location a, Location b) {
        if (a.getX() != b.getX() && a.getY() != b.getY()) {
            throw new IllegalArgumentException("wire must be straight: " + a + " " + b);
        }
        pending.add(Wire.create(a, b));
    }

    /** 모은 부품을 회로에 넣는다. */
    void commit() {
        CircuitMutation m = new CircuitMutation(circuit);
        m.addAll(pending);
        m.execute();
        pending.clear();
    }
}
