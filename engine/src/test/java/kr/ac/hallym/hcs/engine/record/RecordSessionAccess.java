/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.record;

import com.cburch.logisim.data.Value;

/** 테스트용 창: 패키지 안의 값 글자 규칙. */
public final class RecordSessionAccess {
    private RecordSessionAccess() {
    }

    public static String text(Value v) {
        return ValueText.of(v);
    }
}
