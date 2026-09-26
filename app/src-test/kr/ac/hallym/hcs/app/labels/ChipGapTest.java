/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.util.List;

import org.junit.jupiter.api.Test;

import kr.ac.hallym.hcs.app.cycle.FieldOverlay;

/** X-04: 라벨 칩은 선에서 강조 띠(Active Path·필드 색)의 반 폭보다 멀리 놓인다. */
class ChipGapTest {
    @Test
    void wireGapCoversTheHighlightBand() {
        assertEquals(6, LabelOverlay.WIRE_GAP, "BAND 10 → half 5 + 1");
        assertTrue(LabelOverlay.WIRE_GAP > FieldOverlay.BAND / 2);
    }

    @Test
    void aChipAnchoredOnAWireIsPushedBeyondTheBand() {
        // 가로 선 y=100(x 0~200)을 LabelOverlay와 같은 여백으로 피한다
        Rectangle wire = new Rectangle(0, 100, 200, 0);
        int g = LabelOverlay.WIRE_GAP;
        List<Rectangle> obstacles = List.of(new Rectangle(wire.x - g, wire.y - g, wire.width + 2 * g, 2 * g));
        // 칩의 기본 자리는 선 바로 위(선에서 3 위): LabelOverlay.chips와 같다
        Rectangle anchor = new Rectangle(80, 100 - 14 - 3, 40, 14);
        List<LabelLayout.Placed> placed = LabelLayout.layout(
                List.of(new LabelLayout.Req("bus", anchor, 40, 14, 1,
                        new java.awt.geom.Line2D.Double(0, 100, 200, 100))),
                obstacles, 3, 14);
        assertEquals(1, placed.size());
        Rectangle r = placed.get(0).rect;
        int bottom = r.y + r.height;
        assertTrue(bottom <= 100 - g || r.y >= 100 + g, "chip " + r + " keeps " + g + " from the wire");
    }
}
