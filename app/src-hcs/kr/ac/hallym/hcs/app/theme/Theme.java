/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.theme;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * FlatLaf에 디자인 토큰({@link Tokens})과 Pretendard를 입힌다(#21). 창 틀·메뉴·표·대화상자 같은 Swing 부분만
 * 바뀐다. 회로 캔버스 그림은 엔진이 그리므로 그대로다.
 */
public final class Theme {
    static final String FONT_DIR = "/kr/ac/hallym/hcs/app/fonts/";
    static final List<String> PRETENDARD = Arrays.asList(
            "Pretendard-Regular.otf", "Pretendard-Medium.otf", "Pretendard-SemiBold.otf", "Pretendard-Bold.otf");
    /** Pretendard를 못 쓸 때 한글이 나오는 글꼴 순서(Hallym MIPS와 같음). */
    static final List<String> FALLBACK = Arrays.asList("Malgun Gothic", "Noto Sans CJK KR", "Segoe UI");

    private static boolean installed;

    private Theme() {
    }

    /** 앱 시작 때 한 번. 실패하면 Swing 기본 모양으로 둔다. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        swingNamesInEnglish();
        String family = registerFonts();
        FlatLaf.setGlobalExtraDefaults(defaults());
        if (!FlatLightLaf.setup()) {
            return;
        }
        UIManager.put("defaultFont", new FontUIResource(family, Font.PLAIN, Tokens.FONT_UI));
        FlatLaf.updateUI();
    }

    /**
     * Swing이 스스로 그리는 이름(대화 상자 버튼 OK·Cancel, 파일 고르기 창의 버튼·칸 이름)도 영어로 한다(D-049: 이름은
     * 영어). Swing은 OS 언어를 따라 "확인"·"취소"를 내므로 컴포넌트 기본 로캘을 영어로 둔다. 앱 문구는 이와 상관없이
     * Logisim 언어 설정(LocaleManager)을 따른다.
     */
    static void swingNamesInEnglish() {
        // ROOT: Swing의 기본(영어) 번들을 바로 고른다. ENGLISH는 영어 번들이 따로 없어 OS 언어(한국어)로 넘어간다
        javax.swing.JComponent.setDefaultLocale(java.util.Locale.ROOT);
    }

    /** Pretendard를 등록하고 UI 글꼴 이름을 돌려준다. */
    static String registerFonts() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        boolean ok = true;
        for (String name : PRETENDARD) {
            try (InputStream in = Theme.class.getResourceAsStream(FONT_DIR + name)) {
                if (in == null) {
                    ok = false;
                    continue;
                }
                ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, in));
            } catch (IOException | FontFormatException e) {
                ok = false;
            }
        }
        Set<String> families = new HashSet<>(Arrays.asList(ge.getAvailableFontFamilyNames()));
        if (ok && families.contains(Tokens.UI_FONT)) {
            return Tokens.UI_FONT;
        }
        for (String f : FALLBACK) {
            if (families.contains(f)) {
                return f;
            }
        }
        return Font.SANS_SERIF;
    }

    /** FlatLaf 키 → 값. 색 배치는 Hallym MIPS light.qss와 같다(docs/ref-hallym-mips.md 1.3). */
    static Map<String, String> defaults() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("@accentColor", Tokens.hex(Tokens.BLUE));
        m.put("@background", Tokens.hex(Tokens.WINDOW));
        m.put("@foreground", Tokens.hex(Tokens.TEXT));
        m.put("@disabledForeground", Tokens.hex(Tokens.TEXT_MUTED));
        // 선택: 진파랑 채움 + 흰 글자 대신 옅은 파랑 + navy 글자
        m.put("@selectionBackground", Tokens.hex(Tokens.BLUE_TINT_2));
        m.put("@selectionForeground", Tokens.hex(Tokens.NAVY));
        m.put("@selectionInactiveBackground", Tokens.hex(Tokens.BLUE_TINT));
        m.put("@selectionInactiveForeground", Tokens.hex(Tokens.NAVY));
        m.put("@textSelectionBackground", Tokens.hex(Tokens.BLUE_TINT_2));
        m.put("@textSelectionForeground", Tokens.hex(Tokens.NAVY));
        m.put("Component.borderColor", Tokens.hex(Tokens.BORDER));
        m.put("Component.disabledBorderColor", Tokens.hex(Tokens.BORDER));
        m.put("Component.focusedBorderColor", Tokens.hex(Tokens.BLUE));
        m.put("Component.focusColor", Tokens.hex(Tokens.BLUE_TINT_2));
        m.put("Component.arc", Integer.toString(2 * Tokens.RADIUS_SMALL));
        m.put("TextComponent.arc", Integer.toString(2 * Tokens.RADIUS_SMALL));
        m.put("TextField.background", Tokens.hex(Tokens.WHITE));
        m.put("FormattedTextField.background", Tokens.hex(Tokens.WHITE));
        m.put("TextArea.background", Tokens.hex(Tokens.WHITE));
        m.put("ComboBox.background", Tokens.hex(Tokens.WHITE));
        m.put("Spinner.background", Tokens.hex(Tokens.WHITE));
        // 버튼: 일반은 흰 바탕 navy 글자, 기본 버튼은 blue 채움 흰 글자(hover·눌림 navy)
        m.put("Button.arc", Integer.toString(2 * Tokens.RADIUS_SMALL));
        m.put("Button.background", Tokens.hex(Tokens.WHITE));
        m.put("Button.foreground", Tokens.hex(Tokens.NAVY));
        m.put("Button.borderColor", Tokens.hex(Tokens.BORDER));
        m.put("Button.hoverBackground", Tokens.hex(Tokens.HOVER));
        m.put("Button.pressedBackground", Tokens.hex(Tokens.BLUE_TINT_2));
        m.put("Button.default.background", Tokens.hex(Tokens.BLUE));
        m.put("Button.default.foreground", Tokens.hex(Tokens.WHITE));
        m.put("Button.default.borderColor", Tokens.hex(Tokens.BLUE));
        m.put("Button.default.hoverBackground", Tokens.hex(Tokens.NAVY));
        m.put("Button.default.pressedBackground", Tokens.hex(Tokens.NAVY));
        m.put("Button.default.focusedBackground", Tokens.hex(Tokens.BLUE));
        m.put("ToggleButton.selectedBackground", Tokens.hex(Tokens.BLUE_TINT_2));
        m.put("ToggleButton.selectedForeground", Tokens.hex(Tokens.NAVY));
        // 메뉴
        m.put("MenuBar.background", Tokens.hex(Tokens.WHITE));
        m.put("MenuBar.borderColor", Tokens.hex(Tokens.BORDER));
        m.put("MenuBar.hoverBackground", Tokens.hex(Tokens.BLUE_TINT));
        m.put("PopupMenu.background", Tokens.hex(Tokens.WHITE));
        m.put("PopupMenu.borderColor", Tokens.hex(Tokens.BORDER));
        m.put("PopupMenu.borderCornerRadius", Integer.toString(Tokens.RADIUS));
        m.put("Menu.background", Tokens.hex(Tokens.WHITE));
        m.put("MenuItem.background", Tokens.hex(Tokens.WHITE));
        m.put("MenuItem.selectionBackground", Tokens.hex(Tokens.BLUE_TINT_2));
        m.put("MenuItem.selectionForeground", Tokens.hex(Tokens.NAVY));
        m.put("Menu.selectionBackground", Tokens.hex(Tokens.BLUE_TINT_2));
        m.put("Menu.selectionForeground", Tokens.hex(Tokens.NAVY));
        m.put("MenuItem.acceleratorForeground", Tokens.hex(Tokens.TEXT_2));
        m.put("MenuItem.acceleratorSelectionForeground", Tokens.hex(Tokens.NAVY));
        // 표·트리·목록
        m.put("Table.background", Tokens.hex(Tokens.WHITE));
        m.put("Table.gridColor", Tokens.hex(Tokens.BORDER));
        m.put("TableHeader.background", Tokens.hex(Tokens.WHITE));
        m.put("TableHeader.foreground", Tokens.hex(Tokens.NAVY));
        m.put("TableHeader.separatorColor", Tokens.hex(Tokens.WHITE));
        m.put("TableHeader.bottomSeparatorColor", Tokens.hex(Tokens.BORDER));
        m.put("Tree.background", Tokens.hex(Tokens.WHITE));
        m.put("List.background", Tokens.hex(Tokens.WHITE));
        // 탭: 활성 탭 흰 바탕 blue 글자와 밑줄
        m.put("TabbedPane.underlineColor", Tokens.hex(Tokens.BLUE));
        m.put("TabbedPane.inactiveUnderlineColor", Tokens.hex(Tokens.GRAY));
        m.put("TabbedPane.selectedBackground", Tokens.hex(Tokens.WHITE));
        m.put("TabbedPane.selectedForeground", Tokens.hex(Tokens.BLUE));
        m.put("TabbedPane.hoverColor", Tokens.hex(Tokens.HOVER));
        m.put("TabbedPane.foreground", Tokens.hex(Tokens.TEXT_2));
        m.put("TabbedPane.tabSeparatorColor", Tokens.hex(Tokens.BORDER));
        // 스크롤바: 폭 12, 화살표 없음, 둥근 손잡이
        m.put("ScrollBar.width", Integer.toString(Tokens.SCROLLBAR));
        m.put("ScrollBar.showButtons", "false");
        m.put("ScrollBar.thumbArc", "999");
        m.put("ScrollBar.thumbInsets", "2,2,2,2");
        m.put("ScrollBar.track", Tokens.hex(Tokens.WHITE));
        m.put("ScrollBar.thumb", Tokens.hex(Tokens.SCROLL));
        m.put("ScrollBar.hoverThumbColor", Tokens.hex(Tokens.SCROLL_HOVER));
        m.put("ScrollBar.pressedThumbColor", Tokens.hex(Tokens.SCROLL_HOVER));
        // 툴팁: navy 바탕 흰 글자
        m.put("ToolTip.background", Tokens.hex(Tokens.NAVY));
        m.put("ToolTip.foreground", Tokens.hex(Tokens.WHITE));
        m.put("ToolTip.border", "5,8,5,8");
        // 그 밖
        m.put("Separator.foreground", Tokens.hex(Tokens.BORDER));
        m.put("SplitPane.background", Tokens.hex(Tokens.WINDOW));
        m.put("SplitPaneDivider.draggingColor", Tokens.hex(Tokens.BLUE));
        m.put("TitlePane.background", Tokens.hex(Tokens.WHITE));
        m.put("TitlePane.foreground", Tokens.hex(Tokens.NAVY));
        m.put("ProgressBar.foreground", Tokens.hex(Tokens.BLUE));
        m.put("CheckBox.icon.checkmarkColor", Tokens.hex(Tokens.WHITE));
        m.put("CheckBox.icon.selectedBackground", Tokens.hex(Tokens.BLUE));
        m.put("CheckBox.icon.selectedBorderColor", Tokens.hex(Tokens.BLUE));
        return m;
    }
}
