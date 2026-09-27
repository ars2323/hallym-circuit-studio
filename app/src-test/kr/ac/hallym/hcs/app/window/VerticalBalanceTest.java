/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Y-01 계산 규칙: 캔버스 절반, 비율로 줄이기, 접기, 왼쪽 칸 트리 절반. */
class VerticalBalanceTest {
    @Test
    void aTallWindowKeepsTheUserHeight() {
        // 1920×1040: 내부 1000, 나눔 칸 900, 회로 탭 줄 25 → 아래 칸 300 그대로
        assertEquals(300, VerticalBalance.bottom(900, 1000, 25, 300, 5, 30));
    }

    @Test
    void aShortWindowShrinksTheBottomToKeepHalfForTheCanvas() {
        // 1093×582: 내부 ≈ 558, 나눔 칸 ≈ 470 → 캔버스 279+2 + 회로 탭 25 + 나눔선 5 → 아래 159
        int b = VerticalBalance.bottom(470, 558, 25, 300, 5, 30);
        assertEquals(159, b);
        assertFalse(VerticalBalance.collapsed(b, 30));
        assertTrue(470 - b - 5 - 25 >= 558 / 2, "canvas keeps half of the window");
    }

    @Test
    void aVeryShortWindowCollapsesInsteadOfShowingAnEmptyStrip() {
        // 683×512(1024×768 150%): 내부 ≈ 488, 나눔 칸 ≈ 400 → 남는 124 < 130 → 탭 줄만(줄 하나도 못 보이는 칸은 접는다)
        int b = VerticalBalance.bottom(400, 488, 25, 300, 5, 30);
        assertEquals(30, b);
        assertTrue(VerticalBalance.collapsed(b, 30));
    }

    @Test
    void whenEvenTheMinimumDoesNotFitTheBottomCollapsesToTheTabStrip() {
        // 아래에 남는 높이가 80 미만이면 탭 줄(30)만
        int b = VerticalBalance.bottom(300, 488, 25, 300, 5, 30);
        assertEquals(30, b);
        assertTrue(VerticalBalance.collapsed(b, 30));
    }

    @Test
    void aSmallUserHeightIsNeverEnlarged() {
        assertEquals(100, VerticalBalance.bottom(900, 1000, 25, 100, 5, 30));
    }

    @Test
    void sideTabsShrinkThenCollapseSoTheTreeKeepsHalf() {
        assertEquals(200, VerticalBalance.sideTabs(600, 200, 5, 30), "plenty of room: user height");
        assertEquals(143, VerticalBalance.sideTabs(300, 200, 5, 30), "half for the tree: 300 - 152 - 5");
        assertEquals(30, VerticalBalance.sideTabs(140, 200, 5, 30), "too short: only the tab strip");
    }
}
