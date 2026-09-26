/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.sim;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Value;

import kr.ac.hallym.hcs.app.model.Names;

/**
 * 상태 표시줄과 "한 사이클"의 모델(#77, PLAN.md 11.7). 학생에게는 반 주기 틱보다 한 사이클이 기본 단위다: 한 사이클은
 * 클럭 상승과 하강 한 번, 곧 원조 엔진 틱 두 번이다. 값 표시(PC, 불러온 .s)는 사실만 보인다. GUI 없이 테스트한다.
 */
public final class StatusModel {
    private StatusModel() {
    }

    /** n 사이클을 한 틱씩 나눠 실행한다. 도구 모음 버튼(타이머)과 테스트가 같은 규칙을 쓴다. */
    public static final class Run {
        private int left;

        public Run(int cycles) {
            this.left = 2 * cycles;
        }

        /** 남은 틱이 있으면 tick을 한 번 부르고 true, 끝났으면 false. */
        public boolean step(Runnable tick) {
            if (left <= 0) {
                return false;
            }
            left--;
            tick.run();
            return true;
        }
    }

    /** 한 사이클: 틱 두 번과 그 뒤 전파(원조 Simulator가 틱마다 하는 일과 같다). */
    public static void oneCycle(Propagator p) {
        Run r = new Run(1);
        while (r.step(() -> {
            p.tick();
            p.propagate();
        })) {
            // 틱마다 전파
        }
    }

    /** 사이클 수: 원조 엔진 틱 수의 절반(내림). */
    public static long cycles(long ticks) {
        return ticks / 2;
    }

    /** 회로에서 라벨이 PC인 부품의 출력 값(16진). 없거나 정해지지 않았으면 null. */
    public static String pc(CircuitState state) {
        if (state == null) {
            return null;
        }
        Circuit c = state.getCircuit();
        Component pc = pcComponent(state.getProject() == null ? null : state.getProject().getLogisimFile(), c);
        if (pc == null) {
            return null;
        }
        Value v = state.getValue(pc.getEnds().get(0).getLocation());
        return v != null && v.isFullyDefined() ? String.format("0x%08x", v.toIntValue()) : null;
    }

    /**
     * PC로 읽을 부품(V-08, D-103): (1) 학생이 Mark as PC로 표시한 레지스터, (2) 라벨이 PC(대소문자 무관)인 부품 — 터널보다
     * 다른 부품 먼저, 위→아래·왼쪽→오른쪽, (3) Instruction Memory(Addr 입력이 곧 PC 값), (4) 없으면 null. 값은 첫
     * 포트(레지스터 Q, 터널·핀의 자리, Instruction Memory의 Addr)에서 읽는다.
     */
    public static Component pcComponent(com.cburch.logisim.file.LogisimFile file, Circuit c) {
        if (c == null) {
            return null;
        }
        Component marked = PcMark.marked(file, c);
        if (marked != null) {
            return marked;
        }
        java.util.List<Component> parts = new java.util.ArrayList<>(c.getNonWires());
        parts.sort(java.util.Comparator.<Component>comparingInt(x -> x.getLocation().getY())
                .thenComparingInt(x -> x.getLocation().getX()));
        Component tunnel = null;
        Component imem = null;
        for (Component comp : parts) {
            if (comp.getEnds().isEmpty()) {
                continue;
            }
            String label = Names.label(comp);
            if (label != null && label.equalsIgnoreCase("PC")) {
                if (!comp.getFactory().getName().equals("Tunnel")) {
                    return comp;
                }
                if (tunnel == null) {
                    tunnel = comp;
                }
            }
            if (imem == null && comp.getFactory().getName().equals("Instruction Memory")) {
                imem = comp;
            }
        }
        return tunnel != null ? tunnel : imem;
    }

    /** 이 회로의 Instruction Memory가 불러온 .s 파일 이름. 없으면 null. */
    public static String program(Circuit c) {
        for (Component comp : c.getNonWires()) {
            if (comp.getFactory().getName().equals("Instruction Memory")) {
                @SuppressWarnings("unchecked")
                Attribute<Object> a = (Attribute<Object>) comp.getAttributeSet().getAttribute("source");
                Object v = a == null ? null : comp.getAttributeSet().getValue(a);
                if (v != null && !v.toString().isEmpty()) {
                    return new java.io.File(v.toString()).getName();
                }
            }
        }
        return null;
    }
}
