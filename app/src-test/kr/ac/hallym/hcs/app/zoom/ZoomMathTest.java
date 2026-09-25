/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.zoom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;

import org.junit.jupiter.api.Test;

/** #69: 확대 변환(커서 고정점, 단계 목록, 맞춤 계산, 직접 입력). */
class ZoomMathTest {
    @Test
    void stepsGoThroughTheListAndStopAtTheEnds() {
        assertEquals(1.25, ZoomMath.stepIn(1.0));
        assertEquals(0.75, ZoomMath.stepOut(1.0));
        assertEquals(1.0, ZoomMath.stepIn(0.9), "off-list values snap to the next step");
        assertEquals(0.75, ZoomMath.stepOut(0.9));
        assertEquals(4.0, ZoomMath.stepIn(4.0));
        assertEquals(0.25, ZoomMath.stepOut(0.25));
        double z = 0.25;
        int n = 0;
        while (z < 4.0) {
            z = ZoomMath.stepIn(z);
            n++;
        }
        assertEquals(ZoomMath.STEPS.length - 1, n);
    }

    @Test
    void pointUnderCursorStaysPut() {
        Point view = new Point(300, 120);
        Point cursor = new Point(250, 180);
        for (double[] zz : new double[][] {{1.0, 2.0}, {2.0, 0.5}, {0.75, 1.25}, {1.0, 4.0}}) {
            Point nv = ZoomMath.anchor(view, cursor, zz[0], zz[1]);
            double before = (view.x + cursor.x) / zz[0];
            double after = (nv.x + cursor.x) / zz[1];
            assertEquals(before, after, 1.0 / zz[1], "x at " + zz[0] + "->" + zz[1]);
            assertEquals((view.y + cursor.y) / zz[0], (nv.y + cursor.y) / zz[1], 1.0 / zz[1]);
        }
    }

    /** 원점 이동 한도: 회로를 가운데 두는 만큼. 넓은 회로나 가운데 자리가 0보다 작으면 0(S-10 후속). */
    @Test
    void originCapIsTheCenteringAmount() {
        assertEquals(400, ZoomMath.originCap(0, 200, 1000, 1.0));
        assertEquals(300, ZoomMath.originCap(100, 200, 1000, 1.0));
        assertEquals(100, ZoomMath.originCap(100, 200, 1000, 2.0));
        assertEquals(0, ZoomMath.originCap(0, 1200, 1000, 1.0), "wider than the view");
        assertEquals(0, ZoomMath.originCap(900, 200, 1000, 1.0), "the centered spot is already scrolled to");
    }

    @Test
    void fitAndCenter() {
        Rectangle circuit = new Rectangle(100, 50, 800, 400);
        double z = ZoomMath.fit(circuit, 1000, 600, 20);
        assertEquals(Math.min(960.0 / 800, 560.0 / 400), z, 1e-9);
        assertEquals(1.0, ZoomMath.fit(new Rectangle(), 1000, 600, 20), "empty circuit");
        assertEquals(4.0, ZoomMath.fit(new Rectangle(0, 0, 10, 10), 1000, 600, 20), "clamped to 400%");
        assertEquals(0.25, ZoomMath.fit(new Rectangle(0, 0, 100000, 100000), 1000, 600, 20), "clamped to 25%");
        Point c = ZoomMath.center(circuit, 1000, 600, 1.0);
        assertEquals(new Point(0, 0), c, "a circuit smaller than the view needs no scrolling");
        Point c2 = ZoomMath.center(circuit, 400, 300, 2.0);
        assertEquals(new Point((int) Math.round(500 * 2.0 - 200), (int) Math.round(250 * 2.0 - 150)), c2);
    }

    /** S-10: 작은 축은 원점을 옮겨 가운데(스크롤 0), 큰 축은 스크롤로 가운데. */
    @Test
    void fitPlacementCentersBothAxes() {
        // 가로로 긴 회로(1000×300, (40, 50)에서 시작)를 1000×600에 맞춘다: 폭이 배율을 정한다
        java.awt.Rectangle r = new java.awt.Rectangle(40, 50, 1000, 300);
        double z = ZoomMath.fit(r, 1000, 600, 20);
        int[] at = ZoomMath.fitPlacement(r, 1000, 600, z);
        assertEquals(0, at[0], "the width fills the view: no x offset");
        assertTrue(at[1] > 0, "the height does not: a y offset");
        assertEquals(0, at[3], "no vertical scroll when there is an offset");
        double left = at[0] + r.x * z - at[2];
        double right = 1000 - (at[0] + (r.x + r.width) * z - at[2]);
        assertEquals(left, right, 1.0, "horizontally centered by scrolling");
        double top = at[1] + r.y * z - at[3];
        double bottom = 600 - (at[1] + (r.y + r.height) * z - at[3]);
        assertEquals(top, bottom, 1.0, "vertically centered by the offset");
        // 작은 회로: 두 축 모두 원점 이동
        int[] small = ZoomMath.fitPlacement(new java.awt.Rectangle(0, 0, 100, 100), 1000, 600, 1.0);
        assertEquals(450, small[0]);
        assertEquals(250, small[1]);
        assertEquals(0, small[2] + small[3]);
    }

    /**
     * 끌어 이동은 화면 좌표로 잰다. 원조 Canvas는 마우스 x·y를 배율로 나눠 넘기지만(zoomEvent의 translatePoint)
     * 화면 좌표는 그대로라, 200%에서 100px 끌면 보이는 영역도 100px 움직여야 한다.
     */
    @Test
    void panUsesScreenCoordinates() {
        java.awt.Component src = new java.awt.Canvas();
        java.awt.event.MouseEvent e = new java.awt.event.MouseEvent(src, java.awt.event.MouseEvent.MOUSE_DRAGGED, 0,
                0, 300, 200, 900, 700, 0, false, java.awt.event.MouseEvent.BUTTON2);
        // 원조 Canvas.zoomEvent(배율 2.0)와 같은 변환
        e.translatePoint(-150, -100);
        assertEquals(new Point(150, 100), e.getPoint(), "canvas coordinates are divided by the zoom");
        assertEquals(new Point(900, 700), e.getLocationOnScreen(), "screen coordinates are not");
        Point view = ZoomController.panTarget(new Point(400, 300), new Point(1000, 750), e.getLocationOnScreen());
        assertEquals(new Point(500, 350), view, "dragging 100px left/50px up moves the view by the same pixels");
    }

    @Test
    void typedPercent() {
        assertEquals(1.5, ZoomMath.parsePercent("150"));
        assertEquals(0.75, ZoomMath.parsePercent(" 75 % "));
        assertEquals(4.0, ZoomMath.parsePercent("1000%"));
        assertEquals(0.25, ZoomMath.parsePercent("1"));
        assertTrue(Double.isNaN(ZoomMath.parsePercent("abc")));
        assertTrue(Double.isNaN(ZoomMath.parsePercent("-5")));
        assertEquals("133%", ZoomMath.percent(1.33));
    }
}
