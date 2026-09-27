/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.window;

/**
 * 세로 공간 배분(Y-01, D-112). 캔버스는 창 내부 높이의 절반 이상을 가진다. 캔버스 아래 칸(Messages·Cycle View·Console)은
 * 학생이 정한 높이에서 비율로 줄이고, 최소 높이 아래로 내려가야 하면 탭 줄만 남기고 접는다. 왼쪽 칸의 트리/Tunnels
 * 분할도 같다: 트리가 왼쪽 칸 높이의 절반 이상을 가지고, 아래 탭은 줄이다가 접는다. GUI 없이 계산만 한다.
 */
public final class VerticalBalance {
    /** 아래 칸이 이보다 낮아져야 하면 접는다: 탭 줄 + Cycle View 도구 줄 + 머리 세 줄이 들어가는 높이(검토 반영). */
    public static final int BOTTOM_MIN = 130;
    /** 왼쪽 칸 아래 탭(Tunnels·Minimap)이 이보다 낮아져야 하면 접는다. */
    public static final int SIDE_TABS_MIN = 70;

    private VerticalBalance() {
    }

    /**
     * 캔버스 아래 칸 높이. splitHeight는 나눔 칸 전체, contentHeight는 창 내부(메뉴 아래) 높이, canvasChrome은 나눔
     * 칸 위쪽에서 캔버스가 아닌 부분(회로 탭 줄 등), userBottom은 학생이 정한 아래 칸 높이, strip은 접었을 때 남는 탭 줄
     * 높이. 결과가 strip이면 접힌 것이다.
     */
    public static int bottom(int splitHeight, int contentHeight, int canvasChrome, int userBottom, int divider,
            int strip) {
        int canvasMin = (contentHeight + 1) / 2 + 2; // 절반에 여유 2px(경계 픽셀 반올림)
        int allowed = splitHeight - divider - canvasMin - canvasChrome;
        int bottom = Math.min(userBottom, allowed);
        if (bottom < BOTTOM_MIN) {
            return Math.min(strip, Math.max(0, splitHeight - divider));
        }
        return bottom;
    }

    /** 접힌 상태인가(bottom의 결과가 탭 줄 높이 이하). */
    public static boolean collapsed(int bottom, int strip) {
        return bottom <= strip;
    }

    /**
     * 왼쪽 칸 아래 탭(Tunnels·Minimap)의 높이. sideHeight는 왼쪽 칸 나눔 전체, userTabs는 학생이 정한 아래 탭 높이,
     * strip은 접었을 때 남는 탭 줄 높이. 트리는 절반 이상을 가진다.
     */
    public static int sideTabs(int sideHeight, int userTabs, int divider, int strip) {
        int treeMin = (sideHeight + 1) / 2 + 2;
        int allowed = sideHeight - divider - treeMin;
        int tabs = Math.min(userTabs, allowed);
        if (tabs < SIDE_TABS_MIN) {
            return Math.min(strip, Math.max(0, sideHeight - divider));
        }
        return tabs;
    }
}
