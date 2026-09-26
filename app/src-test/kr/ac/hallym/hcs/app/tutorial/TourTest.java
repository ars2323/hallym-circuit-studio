/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tutorial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import kr.ac.hallym.hcs.app.Messages;

/** E-10: 단계마다 두 언어 문구가 있고 제목은 영어 이름이며, 말풍선은 대상을 가리지 않고 창 안에 놓인다. */
class TourTest {
    @Test
    void everyStepHasTitleAndBodyInBothLanguages() {
        assertTrue(Tour.steps().size() >= 10);
        for (Tour.Step s : Tour.steps()) {
            for (Locale l : new Locale[] {Locale.KOREAN, Locale.ENGLISH}) {
                String title = Messages.get(l, s.key + ".title");
                String body = Messages.get(l, s.key + ".body");
                assertFalse(title.isEmpty() || title.startsWith("!"), s.key);
                assertFalse(body.isEmpty() || body.startsWith("!") || body.equals(s.key + ".body"), s.key + " " + l);
                assertTrue(title.chars().allMatch(ch -> ch < 0x3131 || ch > 0xD7A3), "title is an English name: " + title);
            }
        }
    }

    @Test
    void bubbleStaysInsideAndOffTheTarget() {
        Dimension pane = new Dimension(1400, 900);
        Dimension bubble = new Dimension(380, 200);
        Rectangle left = new Rectangle(0, 100, 300, 500); // 왼쪽 패널 → 오른쪽에
        Rectangle r = Tour.place(bubble, left, pane);
        assertFalse(r.intersects(left));
        assertEquals(300 + Tour.GAP, r.x);
        Rectangle right = new Rectangle(1100, 100, 300, 600); // 오른쪽 패널 → 왼쪽에
        r = Tour.place(bubble, right, pane);
        assertFalse(r.intersects(right));
        assertTrue(r.x + r.width <= 1100);
        Rectangle bottom = new Rectangle(0, 860, 1400, 40); // 상태 표시줄 → 위에
        r = Tour.place(bubble, bottom, pane);
        assertFalse(r.intersects(bottom));
        assertTrue(r.y + r.height <= 860 && r.y >= Tour.PAD);
        Rectangle huge = new Rectangle(0, 0, 1400, 900); // 캔버스 전체 → 안쪽에 겹쳐
        r = Tour.place(bubble, huge, pane);
        assertTrue(new Rectangle(0, 0, 1400, 900).contains(r));
        r = Tour.place(bubble, null, pane); // 대상 없음 → 가운데
        assertEquals((1400 - 380) / 2, r.x);
    }
}
