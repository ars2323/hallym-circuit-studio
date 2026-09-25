/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.flow;

import kr.ac.hallym.hcs.app.Settings;

/**
 * Signal Flow 설정(P-07). 앱 환경설정에만 두고 .circ에는 저장하지 않는다(PLAN.md 11.0).
 */
public final class FlowSettings {
    public enum Speed {
        SLOW(120), NORMAL(240), FAST(480);

        /** 화면 px/초(배율과 무관). */
        public final int pxPerSecond;

        Speed(int px) {
            this.pxPerSecond = px;
        }
    }

    static final String ON_CLICK = "flow.onClick";
    static final String SPEED = "flow.speed";
    static final String THROUGH = "flow.throughRegisters";
    static final String ACTIVE = "flow.activePathOnly";
    static final String REDUCE = "flow.reduceMotion";
    static final String SMOOTH = "flow.smooth";

    private FlowSettings() {
    }

    public static boolean onClick() {
        return Settings.get().getBoolean(ON_CLICK, true);
    }

    public static void setOnClick(boolean b) {
        set(ON_CLICK, b);
    }

    public static Speed speed() {
        try {
            return Speed.valueOf(Settings.get().getString(SPEED, Speed.NORMAL.name()));
        } catch (IllegalArgumentException e) {
            return Speed.NORMAL;
        }
    }

    public static void setSpeed(Speed s) {
        Settings.get().set(SPEED, s.name());
        save();
    }

    public static boolean throughRegisters() {
        return Settings.get().getBoolean(THROUGH, false);
    }

    public static void setThroughRegisters(boolean b) {
        set(THROUGH, b);
    }

    public static boolean activePathOnly() {
        return Settings.get().getBoolean(ACTIVE, false);
    }

    public static void setActivePathOnly(boolean b) {
        set(ACTIVE, b);
    }

    /** 기본값은 OS의 "애니메이션 줄이기"를 읽을 수 있으면 그것, 아니면 꺼짐. */
    public static boolean reduceMotion() {
        return Settings.get().getBoolean(REDUCE, osReducesMotion());
    }

    public static void setReduceMotion(boolean b) {
        set(REDUCE, b);
    }

    /** 60fps(부드럽게). 기본은 30fps. */
    public static boolean smooth() {
        return Settings.get().getBoolean(SMOOTH, false);
    }

    public static void setSmooth(boolean b) {
        set(SMOOTH, b);
    }

    public static int fps() {
        return smooth() ? 60 : 30;
    }

    /**
     * OS의 애니메이션 줄이기. Windows는 AWT 데스크톱 속성 "win.ui.animations"(Windows가 "창 안의 컨트롤과 요소에
     * 애니메이션 효과"를 끄면 false)를 읽는다. 다른 OS는 AWT로 읽을 방법이 없어 꺼짐으로 본다.
     */
    static boolean osReducesMotion() {
        try {
            Object v = java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("win.ui.animations");
            return Boolean.FALSE.equals(v);
        } catch (RuntimeException | Error e) {
            return false;
        }
    }

    private static void set(String key, boolean b) {
        Settings.get().set(key, b);
        save();
    }

    private static void save() {
        try {
            Settings.get().save();
        } catch (java.io.IOException e) {
            // 설정 저장 실패는 이번 실행만 기억한다
        }
    }
}
