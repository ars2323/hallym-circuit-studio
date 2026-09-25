/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import com.cburch.logisim.util.LocaleManager;

/**
 * 포크가 더한 화면 문구(PLAN.md 3장 UI 언어, D-049). 원조 문구는 원조 번들(resources/logisim)을 그대로 쓴다.
 * <ul>
 * <li>이름(메뉴·버튼·탭·제목·속성·부품): {@code names.properties} 하나. 모든 언어에서 영어다.</li>
 * <li>설명 문장(안내·오류·도움말·마우스 오버 설명): {@code messages.properties}(영어)와
 * {@code messages_ko.properties}(한국어).</li>
 * </ul>
 * 모두 UTF-8. 한 키는 둘 중 한 곳에만 있다(UiLanguageTest).
 */
public final class Messages {
    static final String BUNDLE = "kr.ac.hallym.hcs.app.messages";
    static final String NAMES = "kr.ac.hallym.hcs.app.names";
    /** 영어를 고른 사람이 한국어 OS에서 한국어 번들을 받지 않도록 시스템 기본 로캘로 넘어가지 않는다. */
    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private Messages() {
    }

    public static String get(String key, Object... args) {
        return get(LocaleManager.getLocale(), key, args);
    }

    static String get(Locale locale, String key, Object... args) {
        String pattern;
        ResourceBundle names = ResourceBundle.getBundle(NAMES, Locale.ROOT, NO_FALLBACK);
        if (names.containsKey(key)) {
            pattern = names.getString(key);
        } else {
            try {
                pattern = ResourceBundle.getBundle(BUNDLE, locale, NO_FALLBACK).getString(key);
            } catch (MissingResourceException e) {
                return key;
            }
        }
        return args.length == 0 ? pattern : new MessageFormat(pattern, locale).format(args);
    }
}
