/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.flow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;

import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.model.Trace;

/**
 * Active Path Only(P-07): 선택 입력이 확정된 MUX·Demux·Decoder·Priority Encoder는 실제로 고른 가지만 건넌다. 선택
 * 입력이 X·E·모름이면 모든 가지를 건너고 그 부품을 "선택 미확정"으로 적는다. 게이트의 제어값 차단(AND에 0 등)은
 * 하지 않는다(D-063). 값은 {@link ValueSource}에서 읽기만 한다.
 */
public final class ActivePath implements Trace.Pass {
    private final ValueSource values;
    /** 선택 미확정 부품(경로 → 부품). 같은 부품은 한 번. */
    private final Map<String, Component> undetermined = new LinkedHashMap<>();

    public ActivePath(ValueSource values) {
        this.values = values;
    }

    public java.util.Collection<Component> undetermined() {
        return undetermined.values();
    }

    @Override
    public boolean passes(List<Component> instances, Component c, int end, int other) {
        String f = Kinds.of(c).factory();
        int n = c.getEnds().size();
        switch (f) {
        case "Multiplexer": {
            // 포트: 데이터 0..k-1, 선택 k, (enable k+1), 출력 마지막
            int k = dataCount(c, n);
            int out = n - 1;
            int data = end == out ? other : end;
            if (data >= k) {
                return true; // 선택·enable 입력은 출력에 영향을 준다
            }
            Integer sel = select(instances, c, k);
            return sel == null || sel == data;
        }
        case "Demultiplexer": {
            // 포트: 출력 0..k-1, 선택 k, (enable k+1), 데이터 입력 마지막
            int k = dataCount(c, n);
            int in = n - 1;
            int branch = end == in ? other : (other == in ? end : -1);
            if (branch < 0 || branch >= k) {
                return true;
            }
            Integer sel = select(instances, c, k);
            return sel == null || sel == branch;
        }
        case "Decoder": {
            // 포트: 출력 0..k-1, 선택 k, (enable k+1)
            int k = dataCount(c, n);
            int branch = end < k ? end : other;
            if (branch >= k) {
                return true;
            }
            Integer sel = select(instances, c, k);
            return sel == null || sel == branch;
        }
        case "Priority Encoder": {
            // 포트: 입력 0..k-1, 출력 k, EN_IN k+1, EN_OUT k+2, GS k+3
            int k = n - 4;
            int input = end < k ? end : (other < k ? other : -1);
            if (input < 0) {
                return true;
            }
            Integer top = highest(instances, c, k);
            return top == null || top == input;
        }
        default:
            return true;
        }
    }

    /** MUX·Demux·Decoder의 가지 수(선택 폭 s → 2^s). */
    static int dataCount(Component c, int n) {
        Object sel = c.getAttributeSet().getValue(com.cburch.logisim.std.plexers.Plexers.ATTR_SELECT);
        int w = sel instanceof com.cburch.logisim.data.BitWidth ? ((com.cburch.logisim.data.BitWidth) sel).getWidth()
                : 1;
        return 1 << w;
    }

    private Integer select(List<Component> instances, Component c, int selEnd) {
        Value v = values.value(instances, c.getEnd(selEnd).getLocation());
        if (v == null || !v.isFullyDefined()) {
            mark(instances, c);
            return null;
        }
        return v.toIntValue();
    }

    /** 켜진 입력 중 가장 높은 번호(우선순위). 입력 중 하나라도 모르면 null. 아무것도 안 켜졌으면 -1. */
    private Integer highest(List<Component> instances, Component c, int k) {
        int top = -1;
        for (int i = 0; i < k; i++) {
            Value v = values.value(instances, c.getEnd(i).getLocation());
            if (v == null || !v.isFullyDefined()) {
                mark(instances, c);
                return null;
            }
            if (v == Value.TRUE) {
                top = i;
            }
        }
        return top;
    }

    private void mark(List<Component> instances, Component c) {
        StringBuilder key = new StringBuilder();
        for (Component i : instances) {
            key.append(System.identityHashCode(i)).append('/');
        }
        key.append(System.identityHashCode(c));
        undetermined.putIfAbsent(key.toString(), c);
    }
}
