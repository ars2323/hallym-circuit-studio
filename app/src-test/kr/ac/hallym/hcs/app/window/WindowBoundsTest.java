/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import kr.ac.hallym.hcs.app.Settings;

/** X-01 (D-105): 첫 실행 크기, 저장된 자리를 모니터에 맞추기, 최소 크기, 포크 전용 설정 키. */
class WindowBoundsTest {
    static final Rectangle WORK = new Rectangle(0, 0, 1024, 728); // 1024×768에서 작업 표시줄 40px 뺀 것

    @Test
    void firstRunIsNinetyPercentCenteredButNeverBelowTheMinimum() {
        Rectangle r = WindowBounds.firstRun(WORK);
        assertEquals(new Rectangle(51, 36, 922, 655), r, "90% of the work area, centered");
        Rectangle big = WindowBounds.firstRun(new Rectangle(0, 0, 1920, 1040));
        assertEquals(1728, big.width);
        assertEquals(936, big.height);
        Rectangle small = WindowBounds.firstRun(new Rectangle(0, 0, 800, 560));
        assertEquals(new Dimension(720, 560), small.getSize(), "90% wide; full height because 600 > 560");
    }

    @Test
    void minimumIsNineSixtyBySixHundredOrTheWorkArea() {
        assertEquals(new Dimension(512, 600), WindowBounds.minimum(WORK), "half of a 1024 work area");
        assertEquals(new Dimension(960, 600), WindowBounds.minimum(new Rectangle(0, 0, 1920, 1040)));
        assertEquals(new Dimension(320, 480), WindowBounds.minimum(new Rectangle(0, 0, 640, 480)));
    }

    @Test
    void savedBoundsAreFittedIntoTheMonitorTheyMostlyLieOn() {
        List<Rectangle> works = Arrays.asList(new Rectangle(0, 0, 1920, 1040), new Rectangle(-1600, 0, 1600, 860));
        // 왼쪽(음수 좌표) 모니터에 있던 창은 그대로
        assertEquals(new Rectangle(-1500, 50, 1200, 700), WindowBounds.fit(new Rectangle(-1500, 50, 1200, 700), works));
        // 이제 없는 모니터(오른쪽 멀리)에 있던 창은 주 모니터 안으로
        Rectangle moved = WindowBounds.fit(new Rectangle(4000, 100, 1200, 700), works);
        assertEquals(new Rectangle(720, 100, 1200, 700), moved);
        // 작업 영역보다 큰 창은 작업 영역 크기로, 위치는 안으로
        assertEquals(new Rectangle(0, 0, 1920, 1040), WindowBounds.fit(new Rectangle(-200, -100, 2600, 1500), works));
        // 너무 작은 저장값은 최소 크기로 키운다(1920 작업 영역: 960×600)
        assertEquals(new Rectangle(0, 0, 960, 600), WindowBounds.fit(new Rectangle(0, 0, 640, 480), works));
        // 두 모니터에 걸친 창은 더 많이 겹치는 모니터(여기서는 주 모니터 700px 대 300px)로 들어간다
        Rectangle edge = WindowBounds.fit(new Rectangle(-300, 200, 1000, 600), works);
        assertEquals(new Rectangle(0, 200, 1000, 600), edge);
        Rectangle left = WindowBounds.fit(new Rectangle(-900, 200, 1000, 600), works);
        assertEquals(new Rectangle(-1000, 200, 1000, 600), left, "mostly on the left monitor: pulled fully inside it");
    }

    @Test
    void forkKeysOnlyAndNoValueMeansFirstRun() {
        Settings s = Settings.get();
        String[] keys = {WindowBounds.X, WindowBounds.Y, WindowBounds.W, WindowBounds.H, WindowBounds.MAX,
            WindowBounds.SPLIT};
        for (String k : keys) {
            assertTrue(k.startsWith("window."), k);
            s.set(k, "");
        }
        assertNull(WindowBounds.saved(s), "no width/height: first run");
        assertEquals(0.25, WindowBounds.mainSplit(), 1e-9);
        s.set(WindowBounds.W, 1200);
        s.set(WindowBounds.H, 700);
        s.set(WindowBounds.X, 30);
        s.set(WindowBounds.Y, 40);
        assertEquals(new Rectangle(30, 40, 1200, 700), WindowBounds.saved(s));
        WindowBounds.saveMainSplit(0.2);
        assertEquals(0.2, WindowBounds.mainSplit(), 1e-9);
        for (String k : keys) {
            s.set(k, "");
        }
    }
}
