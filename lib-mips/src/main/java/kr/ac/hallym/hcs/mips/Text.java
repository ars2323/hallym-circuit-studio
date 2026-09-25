/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.StringGetter;

/**
 * 한국어·영어 문구. 설명 문장은 Logisim의 언어 설정(Window › Preferences)을 따르고, 이름은 늘 영어다
 * (PLAN.md 3장 UI 언어, D-049).
 */
final class Text implements StringGetter {
    private final String en;
    private final String ko;

    private Text(String en, String ko) {
        this.en = en;
        this.ko = ko;
    }

    /** 설명 문장(안내·오류·마우스 오버 설명): 언어 설정을 따른다(D-049). */
    static Text of(String en, String ko) {
        return new Text(en, ko);
    }

    /** 이름(부품·속성·메뉴·제목·부품 몸체 글자): 모든 언어에서 영어다(D-049). */
    static Text name(String en) {
        return new Text(en, en);
    }

    /** 개수와 단위(영어 이름): {@code 1 word}, {@code 27 words}(검토 3차: 단수·복수). */
    static String count(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s");
    }

    static boolean korean() {
        return "ko".equals(LocaleManager.getLocale().getLanguage());
    }

    @Override
    public String get() {
        return korean() ? ko : en;
    }

    @Override
    public String toString() {
        return get();
    }
}
