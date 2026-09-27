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
import java.util.List;
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
    static final String PROPERTIES_PACKAGE = "kr.ac.hallym.hcs.app.theme";
    /** D2Coding(주소·기계어·레지스터 값). OFL 원본 TTF 그대로. */
    static final String D2CODING = "D2Coding-Ver1.3.2-20180524.ttf";
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
        registerCodeFont();
        // FlatLaf 설정은 theme/FlatLightLaf.properties 한 곳(Z-12b, docs/design-parity.md)
        FlatLaf.registerCustomDefaultsSource(PROPERTIES_PACKAGE);
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

    /** D2Coding을 등록한다. 되면 true(없으면 {@link #codeFont(int)}가 Monospaced로 넘어간다). */
    static boolean registerCodeFont() {
        try (InputStream in = Theme.class.getResourceAsStream(FONT_DIR + D2CODING)) {
            if (in == null) {
                return false;
            }
            return GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(Font.createFont(Font.TRUETYPE_FONT, in))
                    || Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                            .contains(Tokens.CODE_FONT);
        } catch (IOException | FontFormatException e) {
            return false;
        }
    }

    /** 주소·기계어·레지스터 값 글꼴(D2Coding, 0과 O가 구분된다, O-07). 크기는 px. */
    public static Font codeFont(int size) {
        registerCodeFontOnce();
        Font f = new Font(Tokens.CODE_FONT, Font.PLAIN, size);
        return Tokens.CODE_FONT.equals(f.getFamily()) ? f : new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    private static boolean codeFontRegistered;

    private static synchronized void registerCodeFontOnce() {
        if (!codeFontRegistered) {
            codeFontRegistered = true;
            registerCodeFont();
        }
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
}
