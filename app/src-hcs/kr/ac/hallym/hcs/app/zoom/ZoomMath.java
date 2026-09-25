/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.zoom;

import java.awt.Point;
import java.awt.Rectangle;

/**
 * 확대·축소 계산(#69, PLAN.md 11.2). GUI 없이 테스트한다. 배율은 1.0 = 100%이고 25%~400% 안에 둔다.
 */
public final class ZoomMath {
    /** 한 단계씩 오갈 때 멈추는 배율. */
    static final double[] STEPS = {0.25, 0.33, 0.5, 0.67, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0, 4.0};
    public static final double MIN = STEPS[0];
    public static final double MAX = STEPS[STEPS.length - 1];
    private static final double EPS = 1e-6;

    private ZoomMath() {
    }

    public static double clamp(double z) {
        return Math.max(MIN, Math.min(MAX, z));
    }

    /** z보다 큰 다음 단계. 이미 최대면 그대로. */
    public static double stepIn(double z) {
        for (double s : STEPS) {
            if (s > z + EPS) {
                return s;
            }
        }
        return MAX;
    }

    /** z보다 작은 이전 단계. 이미 최소면 그대로. */
    public static double stepOut(double z) {
        for (int i = STEPS.length - 1; i >= 0; i--) {
            if (STEPS[i] < z - EPS) {
                return STEPS[i];
            }
        }
        return MIN;
    }

    /**
     * 커서 아래 점을 고정한 채 배율을 바꿀 때의 새 스크롤 위치. view는 지금 스크롤 위치(보이는 영역 왼쪽 위),
     * cursor는 보이는 영역 안의 커서 위치(픽셀)다.
     */
    public static Point anchor(Point view, Point cursor, double oldZoom, double newZoom) {
        double mx = (view.x + cursor.x) / oldZoom;
        double my = (view.y + cursor.y) / oldZoom;
        return new Point((int) Math.round(mx * newZoom - cursor.x), (int) Math.round(my * newZoom - cursor.y));
    }

    /** 회로 영역(모델 좌표)이 보이는 영역에 여백을 두고 들어가는 배율. 빈 회로면 100%. */
    public static double fit(Rectangle bounds, int viewWidth, int viewHeight, int margin) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return 1.0;
        }
        double zx = (viewWidth - 2.0 * margin) / bounds.width;
        double zy = (viewHeight - 2.0 * margin) / bounds.height;
        return clamp(Math.min(zx, zy));
    }

    /** 배율 z에서 영역(모델 좌표)의 가운데가 보이는 영역 가운데에 오는 스크롤 위치. */
    public static Point center(Rectangle bounds, int viewWidth, int viewHeight, double z) {
        double cx = (bounds.x + bounds.width / 2.0) * z;
        double cy = (bounds.y + bounds.height / 2.0) * z;
        return new Point((int) Math.round(Math.max(0, cx - viewWidth / 2.0)),
                (int) Math.round(Math.max(0, cy - viewHeight / 2.0)));
    }

    /**
     * 화면 맞춤의 자리(S-10): 배율 z에서 회로 영역이 보이는 영역보다 작은 축은 원점을 옮겨 가운데 두고(스크롤 0),
     * 큰 축은 원점 0에서 가운데로 스크롤한다. 돌려주는 값은 {원점 x, 원점 y, 스크롤 x, 스크롤 y}(화면 px).
     */
    public static int[] fitPlacement(Rectangle bounds, int viewWidth, int viewHeight, double z) {
        int ox = (int) Math.round((viewWidth - bounds.width * z) / 2.0 - bounds.x * z);
        int oy = (int) Math.round((viewHeight - bounds.height * z) / 2.0 - bounds.y * z);
        Point c = center(bounds, viewWidth, viewHeight, z);
        return new int[] {Math.max(0, ox), Math.max(0, oy), ox > 0 ? 0 : c.x, oy > 0 ? 0 : c.y};
    }

    /**
     * 원점 이동의 한도(S-10 후속): 회로 영역을 가운데 두는 만큼까지만 옮긴다. 회로가 보이는 영역보다 넓거나 가운데
     * 자리가 0보다 작으면 0이다. 커서 배율·원조 배율 조절이 이보다 많이 옮기면 회로 옆에 빈 띠가 남는다.
     */
    public static int originCap(int start, int length, int viewLength, double z) {
        return Math.max(0, (int) Math.floor((viewLength - length * z) / 2.0 - start * z));
    }

    /** 상태 표시줄에 직접 넣은 비율. "150", "150%", " 75 % " 모두 받는다. 읽을 수 없으면 NaN. */
    public static double parsePercent(String text) {
        if (text == null) {
            return Double.NaN;
        }
        String t = text.trim();
        if (t.endsWith("%")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        try {
            double v = Double.parseDouble(t);
            return v > 0 ? clamp(v / 100.0) : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static String percent(double z) {
        return Math.round(z * 100) + "%";
    }
}
