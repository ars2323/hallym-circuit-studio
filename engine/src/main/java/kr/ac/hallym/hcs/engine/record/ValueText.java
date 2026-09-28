/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.record;

import com.cburch.logisim.data.Value;

/** 값 글자(docs/engine-api.md 4절): 폭만큼, 높은 비트부터 '0' '1' 'x' 'E'. 기록에 없으면 null. */
final class ValueText {
    private ValueText() {
    }

    static String of(Value v, int width) {
        if (v == null) {
            return null;
        }
        int w = Math.max(1, width);
        StringBuilder sb = new StringBuilder(w);
        for (int i = w - 1; i >= 0; i--) {
            if (i >= v.getWidth()) {
                sb.append('x');
            } else {
                Value b = v.get(i);
                sb.append(b == Value.TRUE ? '1' : b == Value.FALSE ? '0' : b == Value.ERROR ? 'E' : 'x');
            }
        }
        return sb.toString();
    }

    /** 값의 폭 그대로. */
    static String of(Value v) {
        return v == null ? null : of(v, v.getWidth());
    }

    /** 정해진 값의 16진 글자(0x%08x). 정해지지 않았으면 null. */
    static String hex(Value v) {
        return v == null || !v.isFullyDefined() ? null : String.format("0x%08x", v.toIntValue());
    }
}
