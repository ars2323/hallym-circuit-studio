/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;

/**
 * 공용 식별자(PLAN.md 7.0 "이름이 ID다", #24). 부품 라벨, 서브회로 이름, 터널 이름, 포트 이름을 식별자로 다루고
 * 경로를 {@code main › datapath › PC}로 적는다. 진단 문구, 마우스 오버, 찾기, 향후 Verilog 인스턴스·넷 이름이 모두
 * 이 규칙을 쓴다. 이름은 UI 언어와 무관하다(라벨이 없으면 등록표의 짧은 이름 + 번호).
 */
public final class Names {
    public static final String SEP = " › ";

    private Names() {
    }

    /** 경로 표기: {@code main › datapath › PC}. 빈 조각은 뺀다. */
    public static String path(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(SEP);
            }
            sb.append(p);
        }
        return sb.toString();
    }

    public static String path(String... parts) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, parts);
        return path(list);
    }

    /** 부품 라벨(앞뒤 공백 제거). 라벨 속성이 없거나 비었으면 null. */
    public static String label(Component c) {
        if (c.getAttributeSet().getAttribute(StdAttr.LABEL.getName()) == null) {
            return null;
        }
        String s = c.getAttributeSet().getValue(StdAttr.LABEL);
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 회로 안에서 부품의 이름: 라벨이 있으면 라벨, 서브회로는 회로 이름, 그 밖은 등록표의 짧은 이름 + 번호
     * ({@code AND #3}). 번호는 같은 종류끼리 위→아래, 왼쪽→오른쪽 순서로 1부터 매긴다(파일의 부품 순서와 무관).
     */
    public static String name(Circuit circuit, Component c) {
        String label = label(c);
        if (label != null) {
            return label;
        }
        Kinds.Kind kind = Kinds.of(c);
        String base = kind == Kinds.SUBCIRCUIT ? c.getFactory().getName() : kind.shortName();
        return base + " #" + ordinal(circuit, c);
    }

    /** 라벨과 상관없는 번호 이름: {@code Reg #2}, {@code alu #1}. 라벨이 같은 부품끼리 가를 때. */
    public static String numbered(Circuit circuit, Component c) {
        Kinds.Kind kind = Kinds.of(c);
        String base = kind == Kinds.SUBCIRCUIT ? c.getFactory().getName() : kind.shortName();
        return base + " #" + ordinal(circuit, c);
    }

    /** 같은 종류(서브회로는 같은 회로) 부품 중 위치 순서 번호(1부터). */
    static int ordinal(Circuit circuit, Component c) {
        List<Component> same = new ArrayList<>();
        for (Component o : circuit.getNonWires()) {
            if (o.getFactory() == c.getFactory()) {
                same.add(o);
            }
        }
        same.sort(Comparator.comparing((Component o) -> o.getLocation().getY())
                .thenComparing(o -> o.getLocation().getX()));
        return same.indexOf(c) + 1;
    }

    /**
     * 포트의 전체 이름: {@code 부품 이름.포트 이름}. 예: {@code PC.Q}, {@code AND #3.in1}. 라벨로 이름이 붙는
     * 포트 하나짜리 부품(핀·터널)은 이름이 같으므로 한 번만 쓴다: {@code PC}.
     */
    public static String port(Circuit circuit, Component c, int end) {
        String name = name(circuit, c);
        String port = Kinds.portName(c, end);
        return port.equals(name) ? name : name + "." + port;
    }

    /**
     * 사람이 읽는 부품 이름(S-09): 라벨이 있으면 라벨, 서브회로는 회로 이름 + 번호, 그 밖은 원조 부품 이름 + 번호
     * ({@code Splitter #10}, {@code AND Gate #3}). 식별자({@link #name})의 짧은 이름({@code Split #10})은 경로·열쇠에
     * 쓰고, 화면 글자에는 이것을 쓴다.
     */
    public static String title(Circuit circuit, Component c) {
        String label = label(c);
        return label != null ? label : numberedTitle(circuit, c);
    }

    /** 라벨과 상관없는 번호 이름의 읽는 꼴: {@code Register #2}. */
    public static String numberedTitle(Circuit circuit, Component c) {
        return c.getFactory().getName() + " #" + ordinal(circuit, c);
    }

    /**
     * 사람이 읽는 포트 이름: {@code Splitter #10 (combined end)}, {@code AND Gate #3 (input 2)}, {@code PC (D)}. 라벨로
     * 이름이 붙는 포트 하나짜리 부품(핀·터널)은 {@code PC}처럼 한 번만 쓴다.
     */
    public static String portTitle(Circuit circuit, Component c, int end) {
        String name = title(circuit, c);
        String port = Kinds.readablePort(c, end);
        return port.equals(name) || port.equals(Kinds.portName(c, end)) && port.equals(label(c)) ? name
                : name + " (" + port + ")";
    }

    /** 시뮬레이션 상태의 회로 경로: 맨 위 회로부터 지금 회로까지. */
    public static List<String> circuitPath(CircuitState state) {
        List<String> ret = new ArrayList<>();
        for (CircuitState s = state; s != null; s = s.getParentState()) {
            ret.add(0, s.getCircuit().getName());
        }
        return ret;
    }

    /** 부품의 전체 경로: {@code main › datapath › alu › AND #3}. */
    public static String componentPath(CircuitState state, Component c) {
        List<String> parts = circuitPath(state);
        parts.add(name(state.getCircuit(), c));
        return path(parts);
    }

    /** 위치 표기(진단의 보조 정보). */
    public static String at(Location loc) {
        return "(" + loc.getX() + ", " + loc.getY() + ")";
    }
}
