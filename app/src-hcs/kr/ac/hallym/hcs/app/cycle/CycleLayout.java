/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

/**
 * 사이클 표와 Registers 칸의 폭 배분(Y-02, D-113). 표가 최소 3열(좁은 아래 칸에서는 2열)을 온전히 보이도록 Registers
 * 칸을 학생이 정한 폭에서 줄이고(2진수 열부터 숨김), 그래도 모자라면 Registers 칸을 접어 탭으로 바꾼다. GUI 없이 계산만 한다.
 */
public final class CycleLayout {
    /** Registers 칸의 표시 단계: 0 전체, 1 2진수 숨김, 2 10진수도 숨김, 3 접음(탭으로). */
    public static final int FULL = 0;
    public static final int NO_BIN = 1;
    public static final int NO_DEC = 2;
    public static final int COLLAPSED = 3;

    /** 각 단계에서 Registers 칸에 필요한 폭. */
    public static final int[] SIDE_MIN = {560, 300, 210};
    /** 아래 칸(표+Registers) 폭이 이보다 좁으면 표는 2열만 보장한다(683 폭 창의 아래 칸은 약 500). */
    public static final int NARROW = 560;

    /** 계산 결과. */
    public static final class Plan {
        public final int side;
        public final int level;

        Plan(int side, int level) {
            this.side = side;
            this.level = level;
        }

        @Override
        public String toString() {
            return "side " + side + " level " + level;
        }
    }

    private CycleLayout() {
    }

    /** 표가 보장해야 하는 열 수. */
    public static int columnsWanted(int width) {
        return width < NARROW ? 2 : 3;
    }

    /** 표에 필요한 최소 폭: 이름 열 + 열 수 × 열 폭 + 세로 스크롤바. */
    public static int tableMin(int width, int nameWidth, int colWidth) {
        return nameWidth + columnsWanted(width) * colWidth + 20;
    }

    /**
     * width는 아래 칸(표+Registers) 전체 폭, userSide는 학생이 정한 Registers 폭, nameWidth는 이름 열 폭, colWidth는
     * 사이클 열 폭, divider는 나눔선 폭.
     */
    public static Plan plan(int width, int userSide, int nameWidth, int colWidth, int divider) {
        int avail = width - divider - tableMin(width, nameWidth, colWidth);
        int side = Math.min(userSide, avail);
        if (side >= SIDE_MIN[FULL]) {
            return new Plan(side, FULL);
        }
        if (side >= SIDE_MIN[NO_BIN]) {
            return new Plan(side, NO_BIN);
        }
        if (side >= SIDE_MIN[NO_DEC]) {
            return new Plan(side, NO_DEC);
        }
        return new Plan(0, COLLAPSED);
    }

    /** 이름 열 폭: 가장 긴 이름에 맞추되 최소 80, 최대 180. 넘치면 말줄임과 툴팁으로. */
    public static int nameWidth(int longestText) {
        return Math.max(80, Math.min(180, longestText + 30));
    }
}
