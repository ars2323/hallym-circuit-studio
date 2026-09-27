/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.splitter;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.proj.Project;

/**
 * 편집 동등성 골든(N-01)이 선 우클릭 "Split Bits Here…"를 메뉴 항목과 똑같이 부르게 하는 다리. 메뉴 항목은
 * {@link SplitterMenu}의 패키지 안 도우미(누른 점을 선 위 격자점으로, 선의 폭)로 인자를 정한 뒤
 * {@link SplitterEditor#createNew}를 부른다. 같은 도우미를 그대로 쓴다.
 */
public final class ParitySplitterBridge {
    private ParitySplitterBridge() {
    }

    /** 선 w를 점 p에서 우클릭하고 "Split Bits Here…"를 누른 것과 같다(편집 창이 뜬다). */
    public static void splitBitsHere(Project proj, Circuit circuit, Wire w, Location p) {
        int width = SplitterMenu.width(circuit, w);
        if (width <= 1) {
            throw new IllegalStateException("the menu shows Split Bits Here only on a multi-bit wire: " + w);
        }
        SplitterEditor.createNew(proj, circuit, SplitterMenu.onWire(w, p), width);
    }
}
