/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** 포크 문구 번들: 한국어와 영어의 키와 자리표시자가 같다(PLAN.md 11.0). */
class MessagesTest {
    static Properties load(String name) throws Exception {
        Properties p = new Properties();
        try (InputStream in = Messages.class.getResourceAsStream(name)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    static List<String> placeholders(String s) {
        List<String> ret = new ArrayList<>();
        Matcher m = Pattern.compile("\\{\\d+[^}]*\\}").matcher(s);
        while (m.find()) {
            ret.add(m.group());
        }
        Collections.sort(ret);
        return ret;
    }

    @Test
    void koreanAndEnglishMatch() throws Exception {
        Properties en = load("messages.properties");
        Properties ko = load("messages_ko.properties");
        assertEquals(new TreeSet<>(en.stringPropertyNames()), new TreeSet<>(ko.stringPropertyNames()));
        for (String k : en.stringPropertyNames()) {
            assertEquals(placeholders(en.getProperty(k)), placeholders(ko.getProperty(k)), k);
        }
    }
}
