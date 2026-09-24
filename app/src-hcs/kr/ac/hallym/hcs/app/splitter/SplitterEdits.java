/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.splitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.app.ext.CircExtension;
import kr.ac.hallym.hcs.app.ext.CircExtensions;

/**
 * 스플리터를 만들고 바꾸는 회로 변경(#105). 결과는 원조 표준 속성뿐이고, 되돌리기 한 번으로 취소되는 원조
 * {@link CircuitMutation}으로 만든다. 팔 이름은 .circ 확장 정보(D-024)에 스플리터 위치로 둔다.
 */
public final class SplitterEdits {
    static final String KIND = "splitter";

    private SplitterEdits() {
    }

    /** 원조 기본 라이브러리의 스플리터 팩토리. */
    public static ComponentFactory factory(LogisimFile file) {
        Library wiring = file.getLoader().getBuiltin().getLibrary("Wiring");
        return ((AddTool) wiring.getTool("Splitter")).getFactory();
    }

    /** 스플리터 한 개의 표준 속성(저장 문자열). */
    public static Map<String, String> standardAttrs(Component splitter) {
        Map<String, String> m = new LinkedHashMap<>();
        AttributeSet as = splitter.getAttributeSet();
        for (Attribute<?> a : as.getAttributes()) {
            m.put(a.getName(), standard(as, a));
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static <V> String standard(AttributeSet as, Attribute<V> a) {
        V v = as.getValue(a);
        return v == null ? "" : a.toStandardString(v);
    }

    /** 부품의 지금 모양을 명세로(팔 이름 포함). */
    public static SplitterSpec specOf(LogisimFile file, Circuit circuit, Component splitter) {
        return SplitterSpec.fromStandardAttrs(standardAttrs(splitter), names(file, circuit, splitter.getLocation()));
    }

    /**
     * 표준 속성을 새 속성 집합에 순서대로 넣는다. fanout·incoming을 바꾸면 원조가 bitN을 기본 배정으로 되돌리므로
     * 먼저 넣고, bitN은 그 뒤에 넣는다.
     */
    static AttributeSet attrs(ComponentFactory f, AttributeSet base, SplitterSpec spec, Direction facing) {
        AttributeSet as = base == null ? f.createAttributeSet() : (AttributeSet) base.clone();
        if (facing != null) {
            as.setValue(StdAttr.FACING, facing);
        }
        for (Map.Entry<String, String> e : spec.toStandardAttrs().entrySet()) {
            set(as, e.getKey(), e.getValue());
        }
        return as;
    }

    @SuppressWarnings("unchecked")
    private static void set(AttributeSet as, String name, String value) {
        Attribute<Object> a = (Attribute<Object>) as.getAttribute(name);
        if (a == null) {
            throw new IllegalStateException("splitter has no attribute " + name);
        }
        as.setValue(a, a.parse(value));
    }

    /**
     * 있는 스플리터를 명세대로 바꾸는 변경. 원조는 속성을 바꿀 때 bitN을 다시 계산하므로, 새 속성 집합을 가진
     * 스플리터로 바꿔 끼운다(위치·방향·모양은 그대로).
     */
    public static CircuitMutation change(Circuit circuit, Component splitter, SplitterSpec spec) {
        ComponentFactory f = splitter.getFactory();
        AttributeSet as = attrs(f, splitter.getAttributeSet(), spec, null);
        Component replaced = f.createComponent(splitter.getLocation(), as);
        CircuitMutation m = new CircuitMutation(circuit);
        m.replace(splitter, replaced);
        return m;
    }

    /** 새 스플리터를 at(묶인 쪽 끝점)에 facing 방향으로 놓는 변경. 선 위에 두면 원조 규칙대로 연결된다. */
    public static CircuitMutation create(LogisimFile file, Circuit circuit, Location at, Direction facing,
            SplitterSpec spec) {
        ComponentFactory f = factory(file);
        AttributeSet as = attrs(f, null, spec, facing);
        CircuitMutation m = new CircuitMutation(circuit);
        m.add(f.createComponent(at, as));
        return m;
    }

    /** 스플리터 위치의 팔 이름(없으면 빈 목록). */
    public static List<String> names(LogisimFile file, Circuit circuit, Location at) {
        for (CircExtension.Item item : CircExtensions.of(file).items(circuit.getName())) {
            if (item.kind().equals(KIND) && at(item).equals(at)) {
                List<String> ret = new ArrayList<>();
                for (int i = 0; item.get("arm" + i) != null; i++) {
                    ret.add(item.get("arm" + i));
                }
                return ret;
            }
        }
        return new ArrayList<>();
    }

    /** 팔 이름을 둔다. 이름이 하나도 없으면 항목을 지워 .circ에 아무것도 남기지 않는다. 바뀌었으면 true. */
    public static boolean setNames(LogisimFile file, Circuit circuit, Location at, SplitterSpec spec) {
        CircExtension ext = CircExtensions.of(file);
        List<String> old = names(file, circuit, at);
        for (CircExtension.Item item : ext.items(circuit.getName())) {
            if (item.kind().equals(KIND) && at(item).equals(at)) {
                ext.remove(circuit.getName(), item);
            }
        }
        if (spec.hasNames()) {
            Map<String, String> a = new LinkedHashMap<>();
            a.put("x", Integer.toString(at.getX()));
            a.put("y", Integer.toString(at.getY()));
            List<String> names = spec.names();
            for (int i = 0; i < names.size(); i++) {
                a.put("arm" + i, names.get(i));
            }
            ext.add(circuit.getName(), new CircExtension.Item(KIND, a));
        }
        return !old.equals(spec.hasNames() ? spec.names() : new ArrayList<String>());
    }

    private static Location at(CircExtension.Item item) {
        try {
            return Location.create(Integer.parseInt(item.get("x")), Integer.parseInt(item.get("y")));
        } catch (RuntimeException e) {
            return Location.create(Integer.MIN_VALUE, Integer.MIN_VALUE);
        }
    }
}
