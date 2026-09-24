/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.theme;

import java.awt.Color;

/**
 * 디자인 토큰(D-008). 값은 Hallym MIPS {@code QtSpim/edu/theme/tokens.h}와 같다(docs/ref-hallym-mips.md 1절).
 * 두 제품의 색과 크기를 맞추려고 한 곳에만 둔다. 라이트 전용이다.
 */
public final class Tokens {
    private Tokens() {
    }

    // CI 4색. teal은 CI 표시에만 쓰고 글자색으로 쓰지 않는다(흰 배경 대비 2.91).
    public static final Color NAVY = new Color(0x00205B);
    public static final Color BLUE = new Color(0x0055A5);
    public static final Color TEAL = new Color(0x00A9A5);
    public static final Color GRAY = new Color(0xBCBEC0);

    // 중립
    public static final Color WHITE = new Color(0xFFFFFF);
    public static final Color WINDOW = new Color(0xF5F7FA);
    public static final Color BORDER = new Color(0xE1E5EA);
    public static final Color HOVER = new Color(0xF3F6F9);
    public static final Color TEXT = new Color(0x1F2933);
    public static final Color TEXT_2 = new Color(0x5A6472);
    public static final Color TEXT_MUTED = new Color(0x65707E);
    public static final Color SCROLL = new Color(0xC9D0D8);
    public static final Color SCROLL_HOVER = new Color(0xAEB7C2);
    public static final Color TAB_INACTIVE = new Color(0xEAEEF3);

    // 파생(틴트와 그 위 글자)
    public static final Color BLUE_TINT = new Color(0xE8F0F9);
    public static final Color BLUE_TINT_2 = new Color(0xD3E2F3);
    public static final Color TEAL_TINT = new Color(0xE6F6F5);
    public static final Color TEAL_TEXT = new Color(0x00736F);
    public static final Color AMBER_TINT = new Color(0xFDF3E1);
    public static final Color AMBER_TEXT = new Color(0x8A5A00);
    public static final Color ERROR = new Color(0xC0392B);
    public static final Color ERROR_TINT = new Color(0xFBEAE8);
    public static final Color ERROR_TEXT = new Color(0x8E2A1F);
    /** 경고 아이콘에만(대비 3.6). 글자에는 쓰지 않는다. */
    public static final Color WARNING = new Color(0xB7791F);

    // 글꼴(px)
    public static final String UI_FONT = "Pretendard";
    public static final int FONT_BADGE = 11;
    public static final int FONT_SMALL = 12;
    public static final int FONT_UI = 13;
    public static final int FONT_TITLE = 15;
    public static final int FONT_DISPLAY = 20;

    // 간격·크기(px)
    public static final int SPACE_1 = 4;
    public static final int SPACE_2 = 8;
    public static final int SPACE_3 = 12;
    public static final int SPACE_4 = 16;
    public static final int SPACE_6 = 24;
    public static final int RADIUS_SMALL = 4; // 배지·버튼·입력칸
    public static final int RADIUS = 6; // 도크·탭·메뉴
    public static final int SCROLLBAR = 12;
    public static final int TOOL_ICON = 20;

    /** WCAG 2 대비 비율. */
    public static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** FlatLaf 설정 값에 쓰는 #RRGGBB. */
    static String hex(Color c) {
        return String.format("#%06X", c.getRGB() & 0xFFFFFF);
    }
}
