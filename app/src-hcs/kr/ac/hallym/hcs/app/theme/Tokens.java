/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.theme;

import java.awt.Color;

/**
 * 디자인 토큰(D-008, Z-12b). 값은 Hallym MIPS v2.3.0 {@code electron/src/renderer/app/app.css}와 같고, 출처 줄과 판정은
 * docs/design-parity.md 표에 있다({@code DesignParityTest}가 표와 이 값을 비교한다). 두 제품의 색과 크기를 맞추려고
 * 한 곳에만 둔다. 라이트 전용이다.
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
    public static final Color DIM = new Color(0xA9B1BB);
    public static final Color PURPLE = new Color(0x6B4C9A);
    public static final Color CP0_TINT = new Color(0xEEF0F2);
    public static final Color CP0_TEXT = new Color(0x4A5560);
    /** 방금 바뀐 값(레지스터·메모리 줄, 상태 표시줄의 "방금 바뀜"). */
    public static final Color CHANGED = new Color(0xFFF1B8);
    public static final Color CHANGED_BAR = new Color(0xE0A100);
    public static final Color CHANGED_TEXT = new Color(0x6B4A00);

    // 명령어 필드(Instruction 탭, Hallym MIPS Inspector와 같은 바탕·글자 짝)
    public static final Color FIELD_OPCODE_BG = new Color(0xDFE5EF);
    public static final Color FIELD_OPCODE_FG = new Color(0x00205B);
    public static final Color FIELD_RS_BG = new Color(0xE8F0F9);
    public static final Color FIELD_RS_FG = new Color(0x0055A5);
    public static final Color FIELD_RT_BG = new Color(0xE6F6F5);
    public static final Color FIELD_RT_FG = new Color(0x00736F);
    public static final Color FIELD_RD_BG = new Color(0xFDF3E1);
    public static final Color FIELD_RD_FG = new Color(0x8A5A00);
    public static final Color FIELD_SHAMT_BG = new Color(0xEFE9F6);
    public static final Color FIELD_SHAMT_FG = new Color(0x6B4C9A);
    public static final Color FIELD_FUNCT_BG = new Color(0xEEF0F2);
    public static final Color FIELD_FUNCT_FG = new Color(0x4A5560);
    public static final Color FIELD_IMM_BG = new Color(0xE3EEF0);
    public static final Color FIELD_IMM_FG = new Color(0x1D5C63);
    /**
     * 캔버스 데이터패스 선의 필드 색(C-07). Hallym MIPS Inspector의 필드 글자색과 같다(Z-12b: 같은 명령이면 두
     * 프로그램에서 같은 색). 순서: op, rs, rt, rd, shamt, funct, imm, addr(target).
     */
    public static final Color[] FIELD = {FIELD_OPCODE_FG, FIELD_RS_FG, FIELD_RT_FG, FIELD_RD_FG, FIELD_SHAMT_FG,
        FIELD_FUNCT_FG, FIELD_IMM_FG, FIELD_IMM_FG};
    /** 경고 아이콘에만(대비 3.6). 글자에는 쓰지 않는다. */
    public static final Color WARNING = new Color(0xB7791F);

    // 글꼴(px)
    public static final String UI_FONT = "Pretendard";
    /** 주소·기계어·레지스터 값(0과 O가 구분되는 고정폭, O-07). */
    public static final String CODE_FONT = "D2Coding";
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

    // 줄·틀(px, Hallym MIPS app.css)
    public static final int ROW = 22;
    public static final int REG_ROW = 21;
    public static final int TITLEBAR = 40;
    public static final int HEAD = 34;
    public static final int STATUS_BAR = 24;
    public static final int HEAD_PAD_LEFT = 12;
    public static final int HEAD_PAD_RIGHT = 8;
    public static final int TAB_PAD = 10;
    public static final int TAB_UNDERLINE = 2;
    public static final int HEAD_BUTTON = 24;
    public static final int RADIUS_PANEL = 8;
    public static final int RADIUS_SEG = 8;

    // 단추
    public static final int BUTTON_HEIGHT = 28;
    public static final int BUTTON_SMALL_HEIGHT = 26;
    public static final int BUTTON_ICON = 16;
    public static final int ICON_BUTTON = 30;
    public static final int ICON_BUTTON_ICON = 18;

    // 빈 상태(notice)
    public static final int NOTICE_GAP = 24;
    public static final int NOTICE_MAX_WIDTH = 640;
    public static final int NOTICE_TITLE = 16;
    public static final int NOTICE_CHARACTER = 120;
    /** 칸이 이보다 낮거나 좁으면 캐릭터를 빼고 글만 둔다. */
    public static final int NOTICE_MIN_HEIGHT = 156;
    public static final int NOTICE_MIN_WIDTH = 380;

    // 시작 카드
    public static final int START_CARD_WIDTH = 780;
    public static final int START_CARD_RADIUS = 14;
    public static final int START_CARD_PAD_V = 36;
    public static final int START_CARD_PAD_H = 40;
    public static final int START_CARD_GAP = 40;
    public static final int START_CHARACTER = 200;
    public static final int START_TITLE = 24;
    public static final int START_LEAD = 14;
    public static final int ACTION_WIDTH = 220;
    public static final int ACTION_HEIGHT = 84;
    public static final int ACTION_RADIUS = 10;
    public static final int ACTION_ICON = 20;
    public static final int ACTION_GAP = 12;

    // 대화상자
    public static final int DIALOG_WIDTH = 560;
    public static final int DIALOG_RADIUS = 12;
    public static final int DIALOG_TITLE = 18;
    public static final int ASK_WIDTH = 480;
    public static final int ASK_GAP = 18;
    public static final int ASK_TITLE = 17;
    public static final int ASK_CHARACTER = 96;
    /** 대화상자 뒤 덮개: navy의 불투명도(%). */
    public static final int BACKDROP_ALPHA = 35;

    // 튜토리얼
    /** 밝힌 패널 밖 덮개: navy의 불투명도(%). */
    public static final int TUTORIAL_DIM_ALPHA = 26;
    public static final int RING_WIDTH = 2;
    public static final int TUTORIAL_CARD_WIDTH = 310;
    public static final int TUTORIAL_CARD_RADIUS = 12;
    public static final int TUTORIAL_END_WIDTH = 420;
    public static final int TUTORIAL_CHARACTER = 76;
    public static final int TUTORIAL_BUTTON_GAP = 6;

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
