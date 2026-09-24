/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.StringGetter;

/** 한국어·영어 문구. Logisim의 언어 설정(Window › Preferences)을 따른다. */
final class Text implements StringGetter {
    private final String en;
    private final String ko;

    private Text(String en, String ko) {
        this.en = en;
        this.ko = ko;
    }

    static Text of(String en, String ko) {
        return new Text(en, ko);
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
