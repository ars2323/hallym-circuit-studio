/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.window;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;

import kr.ac.hallym.hcs.app.Settings;

/**
 * 창 크기·위치(X-01, D-105). 원조 AppPreferences의 창 값(windowWidth 640, windowHeight 480, windowLocation 0,0)은 읽지도
 * 쓰지도 않고, 포크 전용 설정({@code window.*})에 둔다. 첫 실행(저장된 값 없음)은 주 모니터 작업 영역(작업 표시줄
 * 제외)에 맞춰 최대화한다. 저장된 값이 지금 모니터 밖이거나 작업 영역보다 크면 작업 영역 안으로 맞춘다. 최소 크기는
 * 960×600(작업 영역이 더 작으면 작업 영역).
 */
public final class WindowBounds {
    static final String X = "window.x";
    static final String Y = "window.y";
    static final String W = "window.width";
    static final String H = "window.height";
    static final String MAX = "window.maximized";
    static final String SPLIT = "window.mainSplit";
    public static final int MIN_W = 960;
    public static final int MIN_H = 600;
    /** 첫 실행에서 최대화를 못 하는 창 시스템(최대화 미지원)일 때 작업 영역의 이 비율 크기로 가운데에. */
    static final double FIRST_RUN_FRACTION = 0.9;

    /** 첫 실행 뒤 창에 적용한 결과(테스트·로그). */
    public static final class Placement {
        public final Rectangle bounds;
        public final boolean maximized;
        public final boolean firstRun;

        Placement(Rectangle bounds, boolean maximized, boolean firstRun) {
            this.bounds = bounds;
            this.maximized = maximized;
            this.firstRun = firstRun;
        }

        @Override
        public String toString() {
            return (firstRun ? "first-run " : "saved ") + bounds + (maximized ? " maximized" : "");
        }
    }

    private WindowBounds() {
    }

    /** 모니터 하나의 작업 영역(작업 표시줄·독 제외). */
    public static Rectangle workArea(GraphicsConfiguration gc) {
        Rectangle b = gc.getBounds();
        Insets in;
        try {
            in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        } catch (RuntimeException e) {
            in = new Insets(0, 0, 0, 0);
        }
        return new Rectangle(b.x + in.left, b.y + in.top, Math.max(1, b.width - in.left - in.right),
                Math.max(1, b.height - in.top - in.bottom));
    }

    /** 모든 모니터의 작업 영역(주 모니터 먼저). */
    public static List<Rectangle> workAreas() {
        List<Rectangle> out = new ArrayList<>();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice main = ge.getDefaultScreenDevice();
        out.add(workArea(main.getDefaultConfiguration()));
        for (GraphicsDevice d : ge.getScreenDevices()) {
            if (d != main) {
                out.add(workArea(d.getDefaultConfiguration()));
            }
        }
        return out;
    }

    /**
     * 최소 창 크기: 960×600. 작업 영역이 작으면 가로는 작업 영역의 절반(나란히 보기의 반 폭 창이 늘 가능하게), 세로는
     * 작업 영역 높이까지 내려간다.
     */
    public static Dimension minimum(Rectangle work) {
        return new Dimension(Math.min(MIN_W, Math.max(1, work.width / 2)), Math.min(MIN_H, work.height));
    }

    /** 첫 실행의 보통 크기: 작업 영역의 90%를 가운데에(최대화가 안 될 때 쓰는 값이자 최대화를 풀었을 때 크기). */
    public static Rectangle firstRun(Rectangle work) {
        int w = Math.max(minimum(work).width, (int) Math.round(work.width * FIRST_RUN_FRACTION));
        int h = Math.max(minimum(work).height, (int) Math.round(work.height * FIRST_RUN_FRACTION));
        return new Rectangle(work.x + (work.width - w) / 2, work.y + (work.height - h) / 2, w, h);
    }

    /**
     * 저장된 창 자리를 지금 모니터들에 맞춘다: 가장 많이 겹치는 작업 영역(없으면 주 모니터)을 고르고, 크기는 최소~작업
     * 영역 사이로, 위치는 그 안으로 옮긴다. 여러 모니터·음수 좌표도 이 규칙 하나로 처리한다.
     */
    public static Rectangle fit(Rectangle saved, List<Rectangle> works) {
        Rectangle work = works.get(0);
        int best = 0;
        for (Rectangle w : works) {
            Rectangle i = w.intersection(saved);
            int area = i.isEmpty() ? 0 : i.width * i.height;
            if (area > best) {
                best = area;
                work = w;
            }
        }
        Dimension min = minimum(work);
        int w = Math.max(min.width, Math.min(saved.width, work.width));
        int h = Math.max(min.height, Math.min(saved.height, work.height));
        int x = Math.max(work.x, Math.min(saved.x, work.x + work.width - w));
        int y = Math.max(work.y, Math.min(saved.y, work.y + work.height - h));
        return new Rectangle(x, y, w, h);
    }

    /** 저장된 값(포크 설정)에서 읽은 창 자리. 없으면 null. */
    public static Rectangle saved(Settings s) {
        int w = s.getInt(W, -1);
        int h = s.getInt(H, -1);
        if (w <= 0 || h <= 0) {
            return null;
        }
        return new Rectangle(s.getInt(X, 0), s.getInt(Y, 0), w, h);
    }

    /** 창을 놓는다(생성 직후, 보이기 전). */
    public static Placement apply(Frame f) {
        Settings s = Settings.get();
        List<Rectangle> works = workAreas();
        Rectangle work = works.get(0);
        Rectangle savedRect = saved(s);
        boolean maximize;
        Rectangle bounds;
        boolean first = savedRect == null;
        if (first) {
            bounds = firstRun(work);
            maximize = true;
        } else {
            bounds = fit(savedRect, works);
            maximize = s.getBoolean(MAX, false);
        }
        f.setMinimumSize(minimum(work));
        f.setBounds(bounds);
        boolean supported = Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH);
        if (maximize && supported) {
            // 최대화 상태를 벗어나면 위 bounds(작업 영역의 90%, 가운데)로 돌아온다
            f.setExtendedState(Frame.MAXIMIZED_BOTH);
        } else if (maximize) {
            // 창 관리자가 최대화를 모르면(Xvfb 등) 작업 영역을 그대로 창 크기로 쓴다
            bounds = new Rectangle(work);
            f.setBounds(bounds);
        }
        return new Placement(bounds, maximize, first);
    }

    /** 창을 닫을 때 저장한다(최대화 상태면 크기는 보통 상태의 것). */
    public static void save(Frame f) {
        Settings s = Settings.get();
        int state = f.getExtendedState();
        boolean max = (state & Frame.MAXIMIZED_BOTH) != 0;
        Rectangle b = f.getBounds();
        if (!max) {
            s.set(X, b.x);
            s.set(Y, b.y);
            s.set(W, b.width);
            s.set(H, b.height);
        } else if (saved(s) == null) {
            Rectangle normal = firstRun(workAreas().get(0));
            s.set(X, normal.x);
            s.set(Y, normal.y);
            s.set(W, normal.width);
            s.set(H, normal.height);
        }
        s.set(MAX, max);
    }

    /** 왼쪽 칸 비율(포크 설정, 기본 0.25). */
    public static double mainSplit() {
        try {
            return Double.parseDouble(Settings.get().getString(SPLIT, "0.25"));
        } catch (NumberFormatException e) {
            return 0.25;
        }
    }

    public static void saveMainSplit(double fraction) {
        Settings.get().set(SPLIT, Double.toString(fraction));
    }
}
