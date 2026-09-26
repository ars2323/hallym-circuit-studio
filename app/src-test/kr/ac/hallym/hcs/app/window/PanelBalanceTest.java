/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.window;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** X-03 (D-107): 캔버스 ≥ 창 폭의 절반(최소 480). 모자라면 양쪽 칸을 비율로 줄이고, 그래도 모자라면 Attributes를 접는다. */
class PanelBalanceTest {
    static int canvas(int width, int[] p, int strip, int divider) {
        return width - p[0] - (p[1] == 0 ? strip : p[1] + divider);
    }

    @Test
    void wideWindowKeepsTheUserWidths() {
        int[] p = PanelBalance.plan(1920, 0.25, 240, false, 24, 6);
        assertArrayEquals(new int[] {480, 240}, p);
        assertTrue(canvas(1920, p, 24, 6) >= 960);
    }

    @Test
    void narrowWindowShrinksBothPanelsProportionally() {
        assertArrayEquals(new int[] {320, 300}, PanelBalance.plan(1280, 0.25, 300, false, 24, 6),
                "654px of canvas is already enough: nothing changes");
        int[] p = PanelBalance.plan(1280, 0.3, 360, false, 24, 6);
        assertArrayEquals(new int[] {328, 306}, p, "both shrink in proportion (384/366 → 328/312)");
        assertEquals(640, canvas(1280, p, 24, 6));
    }

    @Test
    void whenTheDockWouldGoBelowItsMinimumItCollapses() {
        int[] p = PanelBalance.plan(960, 0.4, 170, false, 24, 6);
        assertEquals(0, p[1], "collapsed: the dock would drop below 160");
        int c = canvas(960, p, 24, 6);
        assertTrue(c >= 480, "canvas ≥ half at 960: " + c);
        assertTrue(p[0] >= PanelBalance.LEFT_MIN);
        int[] q = PanelBalance.plan(960, 0.3, 240, false, 24, 6);
        assertArrayEquals(new int[] {259, 215}, q, "dock still ≥ 160: shrink, do not collapse");
        assertEquals(480, canvas(960, q, 24, 6));
    }

    @Test
    void userCollapsedDockStaysCollapsedAndOnlyTheLeftShrinks() {
        int[] p = PanelBalance.plan(700, 0.4, 240, true, 24, 6);
        assertEquals(0, p[1]);
        assertEquals(480, canvas(700, p, 24, 6));
        assertEquals(196, p[0], "the left panel gives up exactly what the canvas needs");
        int[] tiny = PanelBalance.plan(600, 0.5, 240, true, 24, 6);
        assertEquals(PanelBalance.LEFT_MIN, tiny[0], "but never below its minimum");
    }
}
