/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 한국어 UI 용어집(docs/GLOSSARY.md)의 "쓰지 않는 말"이 앱 문구에 없는지 본다. 범위: 원조 번역
 * (app/resources/logisim/ko), 앱 문구(messages_ko.properties), lib-mips의 한국어 문구(Text.of의 둘째 인자).
 */
class GlossaryTest {
    static final File ROOT = new File(System.getProperty("hcs.circDir")).getParentFile().getParentFile();

    /** 용어집 표에서 쓰지 않는 말 → 권하는 말. */
    static Map<String, String> forbidden() throws IOException {
        Map<String, String> ret = new LinkedHashMap<>();
        for (String line : Files.readAllLines(new File(ROOT, "docs/GLOSSARY.md").toPath(), StandardCharsets.UTF_8)) {
            String[] cells = line.split("\\|", -1);
            if (cells.length < 5 || line.contains("---") || cells[1].trim().startsWith("영어")) {
                continue;
            }
            String use = cells[2].trim();
            for (String bad : cells[3].split(",")) {
                if (!bad.trim().isEmpty()) {
                    ret.put(bad.trim(), use);
                }
            }
        }
        return ret;
    }

    private static Properties load(File f, boolean utf8) throws IOException {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            if (utf8) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } else {
                p.load(in); // ASCII 이스케이프
            }
        }
        return p;
    }

    /** 검사할 한국어 문구: "출처: 문구". */
    static List<String> koreanTexts() throws IOException {
        List<String> ret = new ArrayList<>();
        File[] ko = new File(ROOT, "app/resources/logisim/ko").listFiles((d, n) -> n.endsWith(".properties"));
        assertTrue(ko != null && ko.length > 10);
        for (File f : ko) {
            Properties p = load(f, false);
            for (String k : p.stringPropertyNames()) {
                ret.add(f.getName() + "#" + k + ": " + p.getProperty(k));
            }
        }
        Properties m = load(new File(ROOT, "app/src-hcs/kr/ac/hallym/hcs/app/messages_ko.properties"), true);
        for (String k : m.stringPropertyNames()) {
            ret.add("messages_ko#" + k + ": " + m.getProperty(k));
        }
        Pattern textOf = Pattern.compile("Text\\.of\\(\\s*\"(?:[^\"\\\\]|\\\\.)*\"\\s*,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        List<File> java = new ArrayList<>();
        collect(new File(ROOT, "lib-mips/src/main/java"), java);
        int n = 0;
        for (File f : java) {
            Matcher mt = textOf.matcher(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            while (mt.find()) {
                ret.add(f.getName() + ": " + mt.group(1));
                n++;
            }
        }
        assertTrue(n > 20, "lib-mips Korean texts found: " + n);
        return ret;
    }

    private static void collect(File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) {
            return;
        }
        for (File f : fs) {
            if (f.isDirectory()) {
                collect(f, out);
            } else if (f.getName().endsWith(".java")) {
                out.add(f);
            }
        }
    }

    @Test
    void glossaryHasTheAgreedTerms() throws Exception {
        Map<String, String> bad = forbidden();
        assertEquals("조작 도구", bad.get("찌르기"));
        assertEquals("서브회로", bad.get("하위 회로"));
        assertEquals("도구 모음", bad.get("툴바"));
        assertTrue(bad.size() >= 20, bad.toString());
    }

    @Test
    void appTextsFollowTheGlossary() throws Exception {
        Map<String, String> bad = forbidden();
        List<String> found = new ArrayList<>();
        for (String t : koreanTexts()) {
            for (Map.Entry<String, String> e : bad.entrySet()) {
                if (t.contains(e.getKey())) {
                    found.add(t + "  →  \"" + e.getKey() + "\" 대신 \"" + e.getValue() + "\"");
                }
            }
        }
        assertEquals(new ArrayList<String>(), found);
    }
}
