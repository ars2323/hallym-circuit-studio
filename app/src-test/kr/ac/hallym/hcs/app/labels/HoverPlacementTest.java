/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/** Q-03 검토: 마우스 오버 도움말은 라벨·값 칩을 가리지 않는 자리에 놓인다(오른쪽 위 → 오른쪽 아래 → 왼쪽 → 위·아래). */
class HoverPlacementTest {
    static Rectangle box(Point p) {
        return new Rectangle(p.x, p.y, HoverInfo.TIP_W, HoverInfo.TIP_H);
    }

    @Test
    void avoidsChipsBesideThePart() {
        Rectangle vis = new Rectangle(0, 0, 1200, 800);
        Rectangle part = new Rectangle(400, 300, 100, 80);
        Point plain = HoverInfo.choose(vis, part, Collections.<Rectangle>emptyList());
        assertEquals(new Point(516, 292), plain, "right-top by default");
        Rectangle chipRightTop = new Rectangle(520, 280, 90, 20); // RD1 값 칩이 오른쪽 위에
        Point moved = HoverInfo.choose(vis, part, Collections.singletonList(chipRightTop));
        assertFalse(box(moved).intersects(chipRightTop));
        assertEquals(new Point(516, 396), moved, "right-below when the chip sits right-top");
        Rectangle chipRightBelow = new Rectangle(520, 400, 90, 20);
        Point left = HoverInfo.choose(vis, part, Arrays.asList(chipRightTop, chipRightBelow));
        assertFalse(box(left).intersects(chipRightTop) || box(left).intersects(chipRightBelow));
        assertTrue(left.x < part.x, "then the left side");
    }

    @Test
    void avoidsWiresToo() {
        Rectangle vis = new Rectangle(0, 0, 1200, 800);
        Rectangle part = new Rectangle(400, 300, 100, 80);
        Rectangle chipRightTop = new Rectangle(520, 280, 90, 20);
        Rectangle busBelow = new Rectangle(540, 380, 5, 300); // 부품 오른쪽 아래로 내려가는 세로 버스(RD2)
        Point p = HoverInfo.choose(vis, part, Arrays.asList(chipRightTop, busBelow));
        assertFalse(box(p).intersects(chipRightTop) || box(p).intersects(busBelow), p.toString());
        assertTrue(p.x + HoverInfo.TIP_W <= part.x, "goes to the left when the right is blocked above and below");
    }

    @Test
    void staysInsideTheViewAndFallsBackWhenEverythingIsCovered() {
        Rectangle vis = new Rectangle(0, 0, 700, 400);
        Rectangle part = new Rectangle(480, 100, 100, 80); // 오른쪽에 200px 자리가 없다
        Point p = HoverInfo.choose(vis, part, Collections.<Rectangle>emptyList());
        assertTrue(p.x + HoverInfo.TIP_W <= 700 && p.x >= 0, p.toString());
        Rectangle everywhere = new Rectangle(0, 0, 700, 400);
        Point f = HoverInfo.choose(vis, part, Collections.singletonList(everywhere));
        assertTrue(f.x >= 0 && f.y >= 0, "some in-view place even if all covered");
    }
}
