/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.CircuitState;

/** 다른 패키지(테스트·스크린샷)가 Console 글을 읽는 창: {이름, 글, exit 여부}. */
public final class ConsoleTextAccess {
    private ConsoleTextAccess() {
    }

    public static List<String[]> collect(CircuitState root) {
        List<String[]> out = new ArrayList<>();
        for (ConsoleText.Entry e : ConsoleText.collect(root)) {
            out.add(new String[] {e.name, e.text, Boolean.toString(e.exited)});
        }
        return out;
    }
}
