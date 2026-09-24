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
 * 포크가 더한 화면 문구(한국어·영어, PLAN.md 11.0). 원조 문구는 원조 번들(resources/logisim)을 그대로 쓴다.
 * 번들은 {@code messages.properties}(영어)와 {@code messages_ko.properties}(한국어), UTF-8.
 */
public final class Messages {
    static final String BUNDLE = "kr.ac.hallym.hcs.app.messages";
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
        try {
            pattern = ResourceBundle.getBundle(BUNDLE, locale, NO_FALLBACK).getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
        return args.length == 0 ? pattern : new MessageFormat(pattern, locale).format(args);
    }
}
