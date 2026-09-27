/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.sim;

import com.cburch.logisim.data.Value;

/** 테스트가 패키지 안의 값 글자 함수를 부른다. */
public final class SimSessionTestAccess {
    private SimSessionTestAccess() {
    }

    public static String text(Value v, int width) {
        return SimSession.text(v, width);
    }
}
