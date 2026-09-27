/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Y-02 계산 규칙: 표 3열(좁으면 2열) 보장, Registers 칸 단계, 이름 열 상한. */
class CycleLayoutTest {
    @Test
    void wideBottomKeepsTheUserSideWidth() {
        CycleLayout.Plan p = CycleLayout.plan(1600, 640, 180, 132, 5);
        assertEquals(640, p.side);
        assertEquals(CycleLayout.FULL, p.level);
    }

    @Test
    void mediumBottomHidesBinaryThenDecimal() {
        // 1024 폭: 표 최소 180 + 3×132 + 20 = 596 → Registers 423 → 2진수 숨김
        CycleLayout.Plan p = CycleLayout.plan(1024, 640, 180, 132, 5);
        assertEquals(423, p.side);
        assertEquals(CycleLayout.NO_BIN, p.level);
        // 850 폭: Registers 249 → 10진수도 숨김
        p = CycleLayout.plan(850, 640, 180, 132, 5);
        assertEquals(CycleLayout.NO_DEC, p.level);
        assertTrue(p.side >= 210);
    }

    @Test
    void narrowBottomCollapsesTheSideIntoTabs() {
        // 683 폭(3열 보장): 표 최소 596 → Registers 82 < 210 → 접음(표가 전체 폭)
        CycleLayout.Plan p = CycleLayout.plan(683, 640, 180, 132, 5);
        assertEquals(CycleLayout.COLLAPSED, p.level);
        assertEquals(0, p.side);
        // 683 창의 아래 칸(약 500)은 2열만 보장: 표 최소 464 → 31 → 접음
        assertEquals(2, CycleLayout.columnsWanted(500));
        assertEquals(CycleLayout.COLLAPSED, CycleLayout.plan(500, 640, 180, 132, 5).level);
        // 이름 열이 짧으면(80) 720 폭에서 표 최소 496 → Registers 219 → 10진수 숨김 단계로 남는다
        assertEquals(CycleLayout.NO_DEC, CycleLayout.plan(720, 640, 80, 132, 5).level);
    }

    @Test
    void tableAlwaysKeepsItsColumns() {
        for (int w : new int[] {1339, 891, 760, 712, 632, 498}) { // 6가지 창의 아래 칸 폭
            CycleLayout.Plan p = CycleLayout.plan(w, 640, 180, 132, 5);
            int table = p.level == CycleLayout.COLLAPSED ? w : w - p.side - 5;
            assertTrue((table - 180 - 20) / 132 >= CycleLayout.columnsWanted(w), w + ": " + p);
        }
    }

    @Test
    void nameColumnFollowsTheLongestNameWithinBounds() {
        assertEquals(80, CycleLayout.nameWidth(20));
        assertEquals(130, CycleLayout.nameWidth(100));
        assertEquals(180, CycleLayout.nameWidth(400));
    }
}
