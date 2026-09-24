/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.cburch.logisim.util.LocaleManager;

/**
 * #23: 원조 문구의 한국어 번들(resources/logisim/ko). 영어 번들과 키·자리표시자·끝 공백이 같고, 원조처럼
 * ASCII(\\u 이스케이프)로 저장되어 있다.
 */
class KoreanBundleTest {
    static final String[] NAMES = {"analyze", "circuit", "data", "draw", "file", "gui", "hex", "log", "menu",
        "opts", "prefs", "proj", "start", "std", "tools", "util"};

    static Properties load(String lang, String name) throws Exception {
        Properties p = new Properties();
        try (InputStream in = LocaleManager.class.getResourceAsStream(
                "/resources/logisim/" + lang + "/" + name + ".properties")) {
            p.load(in); // 원조와 같은 ISO-8859-1 읽기
        }
        return p;
    }

    /** StringUtil.format의 %s·%$1과 String.format의 %d 등. */
    static List<String> placeholders(String s) {
        List<String> ret = new ArrayList<>();
        Matcher m = Pattern.compile("%(\\$\\d|\\d+\\$[a-z]|[a-z%])").matcher(s);
        while (m.find()) {
            ret.add(m.group());
        }
        Collections.sort(ret);
        return ret;
    }

    @Test
    void sameKeysPlaceholdersAndTrailingSpaces() throws Exception {
        for (String name : NAMES) {
            Properties en = load("en", name);
            Properties ko = load("ko", name);
            assertEquals(new TreeSet<>(en.stringPropertyNames()), new TreeSet<>(ko.stringPropertyNames()), name);
            for (String k : en.stringPropertyNames()) {
                String e = en.getProperty(k);
                String v = ko.getProperty(k);
                assertEquals(placeholders(e), placeholders(v), name + ":" + k);
                assertEquals(e.endsWith(" "), v.endsWith(" "), name + ":" + k + " trailing space");
                assertFalse(v.contains("%1$s") || v.contains("%2$s"), name + ":" + k + " uses %$1 in Logisim");
            }
        }
    }

    @Test
    void filesAreAsciiLikeTheOriginals() throws Exception {
        File dir = new File(LocaleManager.class.getResource("/resources/logisim/ko/menu.properties").toURI())
                .getParentFile();
        for (String name : NAMES) {
            byte[] b = Files.readAllBytes(new File(dir, name + ".properties").toPath());
            for (byte x : b) {
                assertTrue(x >= 0, name + " must use \\u escapes");
            }
            assertEquals(new String(b, StandardCharsets.US_ASCII), new String(b, StandardCharsets.ISO_8859_1));
        }
    }

    /** 저장 파일에 들어가는 문구는 영어 그대로(규칙 2.3). 새 회로 이름은 템플릿 없이 새로 만들 때 쓰인다. */
    @Test
    void stringsThatGoIntoSavedFilesStayEnglish() throws Exception {
        assertEquals("main", load("ko", "proj").getProperty("newCircuitName"));
        assertEquals("doc/doc_en.hs", load("ko", "menu").getProperty("helpsetUrl"), "no Korean help set");
    }

    @Test
    void koreanIsOfferedAndLoads() throws Exception {
        Locale old = LocaleManager.getLocale();
        try {
            LocaleManager menu = new LocaleManager("resources/logisim", "menu");
            boolean offered = false;
            for (Locale l : menu.getLocaleOptions()) {
                offered |= l.getLanguage().equals("ko");
            }
            assertTrue(offered, "settings.properties lists ko");
            LocaleManager.setLocale(new Locale("ko"));
            assertEquals("파일", menu.get("fileMenu"));
            LocaleManager.setLocale(Locale.ENGLISH);
            assertEquals("File", menu.get("fileMenu"));
        } finally {
            LocaleManager.setLocale(old);
        }
    }
}
