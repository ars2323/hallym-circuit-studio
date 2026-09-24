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

    /** 한 사이클: 틱 두 번과 그 뒤 전파(원조 Simulator가 틱마다 하는 일과 같다). */
    public static void oneCycle(Propagator p) {
        for (int i = 0; i < 2; i++) {
            p.tick();
            p.propagate();
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
        for (Component comp : c.getNonWires()) {
            if ("PC".equals(Names.label(comp)) && !comp.getEnds().isEmpty()) {
                Value v = state.getValue(comp.getEnds().get(0).getLocation());
                if (v != null && v.isFullyDefined()) {
                    return String.format("0x%08x", v.toIntValue());
                }
                return null;
            }
        }
        return null;
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
