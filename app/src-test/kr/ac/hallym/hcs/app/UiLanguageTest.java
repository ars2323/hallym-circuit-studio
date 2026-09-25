/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.LocaleManager;

/**
 * UI 언어 방침(PLAN.md 3장, D-049): 이름·명령은 영어, 설명 문장만 한국어다.
 * <ul>
 * <li>이름 리소스(앱 {@code names.properties}, lib-mips {@code Text.name}, 한국어로 둔 원조 번들 밖의 키)에는 한글이
 * 없다.</li>
 * <li>원조 한국어 번들(resources/logisim/ko)에는 설명 문장 키만 있다. 나머지 키는 영어 번들로 넘어간다.</li>
 * <li>한국어 설명 문장 안에서 도구·패널·MIPS 부품 이름은 영어 그대로 쓴다(docs/GLOSSARY.md "문장 안에서 쓰지 않는
 * 번역").</li>
 * </ul>
 */
class UiLanguageTest {
    static final File ROOT = new File(System.getProperty("hcs.circDir")).getParentFile().getParentFile();
    static final Pattern HANGUL = Pattern.compile("[\\u1100-\\u11FF\\u3130-\\u318F\\uAC00-\\uD7AF]");
    static final String[] BUNDLES = KoreanBundleTest.NAMES;

    /** 원조 키의 끝말: 이름으로 쓰이는 것(제목, 메뉴 항목, 버튼, 속성, 부품 …). */
    static final Set<String> NAME_SUFFIXES = new HashSet<>(Arrays.asList("Attr", "Title", "Item", "Option",
            "Button", "Label", "Tab", "Menu", "Component", "Library", "Tool", "Column", "Header", "Name", "Action"));
    /** 원조 키의 끝말: 설명 문장으로 쓰이는 것(오류, 안내, 도움말 …). */
    static final Set<String> DESCRIPTION_SUFFIXES = new HashSet<>(Arrays.asList("Error", "Message", "Msg", "Help",
            "Desc", "Question", "Usage", "Warning"));
    /** 끝말 규칙과 다른 것. */
    static final Set<String> FORCE_DESCRIPTION = new HashSet<>(Arrays.asList("badVariableName",
            "fileAppearanceNotFound", "toolNameMissing", "toolNotFound", "zoomShowGrid", "accelRestartLabel",
            "ttyHaltReasonPin", "ttyHaltReasonOscillation"));
    static final Set<String> FORCE_NAME = new HashSet<>(Arrays.asList("keybDesc", "ttyDesc", "layoutRadix1",
            "layoutRadix2"));

    /**
     * 원조 영어 문구가 설명 문장인가. 설명 문장: 오류·안내 키, 세 낱말 이상의 문장(마침표·물음표로 끝남), 네 낱말 이상의
     * 마우스 오버 설명, 명령줄 옵션 설명. 나머지(메뉴 항목, 제목, 속성, 짧은 버튼 설명)는 이름이다.
     */
    static boolean isDescription(String key, String english) {
        if (FORCE_NAME.contains(key)) {
            return false;
        }
        if (FORCE_DESCRIPTION.contains(key) || key.startsWith("arg") && key.endsWith("Option")) {
            return true;
        }
        Matcher m = Pattern.compile("[A-Z][a-z]+$").matcher(key);
        String suffix = m.find() ? m.group() : "";
        String t = english.trim();
        int words = t.isEmpty() ? 0 : t.split("\\s+").length;
        if (NAME_SUFFIXES.contains(suffix) || t.endsWith("...")) {
            return false;
        }
        if (DESCRIPTION_SUFFIXES.contains(suffix) && words >= 2) {
            return true;
        }
        if (t.endsWith(":")) {
            return words >= 5;
        }
        if (suffix.equals("Tip")) {
            return words >= 4 || t.endsWith(".");
        }
        return t.matches(".*[.?!]$") && words >= 3;
    }

    static Properties load(File f, boolean utf8) throws IOException {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            if (utf8) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } else {
                p.load(in); // 원조처럼 \\u 이스케이프
            }
        }
        return p;
    }

    static File app(String name) {
        return new File(ROOT, "app/src-hcs/kr/ac/hallym/hcs/app/" + name);
    }

    static boolean hangul(String s) {
        return HANGUL.matcher(s).find();
    }

    @Test
    void appNameResourcesHaveNoHangul() throws Exception {
        Properties names = load(app("names.properties"), true);
        assertTrue(names.size() > 150, "names: " + names.size());
        List<String> bad = new ArrayList<>();
        for (String k : names.stringPropertyNames()) {
            if (hangul(names.getProperty(k))) {
                bad.add(k + " = " + names.getProperty(k));
            }
        }
        assertEquals(new ArrayList<String>(), bad);
        assertFalse(app("names_ko.properties").exists(), "names are English in every language");
        Properties descriptions = load(app("messages.properties"), true);
        for (String k : names.stringPropertyNames()) {
            assertFalse(descriptions.containsKey(k), k + " is both a name and a description");
        }
    }

    @Test
    void namesAreEnglishEvenInKorean() throws Exception {
        assertEquals("Fit to Window", Messages.get(Locale.KOREAN, "menu.fit"));
        assertEquals("Show in Attribute Panel", Messages.get(Locale.KOREAN, "menu.showAttrs"));
        assertEquals("Labels: All", Messages.get(Locale.KOREAN, "labels.density.all"));
        assertEquals("1 Cycle", Messages.get(Locale.KOREAN, "bar.cycle"));
        assertTrue(hangul(Messages.get(Locale.KOREAN, "probe.noRoom")), "descriptions stay Korean");
        assertFalse(hangul(Messages.get(Locale.ENGLISH, "probe.noRoom")));
    }

    @Test
    void koreanOriginalBundlesHoldDescriptionsOnly() throws Exception {
        List<String> bad = new ArrayList<>();
        int kept = 0;
        for (String name : BUNDLES) {
            Properties en = KoreanBundleTest.load("en", name);
            Properties ko = KoreanBundleTest.load("ko", name);
            for (String k : ko.stringPropertyNames()) {
                String e = en.getProperty(k);
                if (e == null || !isDescription(k, e)) {
                    bad.add(name + ":" + k + " = " + e);
                }
                kept++;
            }
        }
        assertEquals(new ArrayList<String>(), bad, "name keys must fall back to the English bundle");
        assertTrue(kept > 250, "Korean descriptions kept: " + kept);
    }

    @Test
    void originalNamesShowInEnglishUnderKorean() {
        Locale old = LocaleManager.getLocale();
        try {
            LocaleManager.setLocale(new Locale("ko"));
            LocaleManager menu = new LocaleManager("resources/logisim", "menu");
            LocaleManager tools = new LocaleManager("resources/logisim", "tools");
            LocaleManager std = new LocaleManager("resources/logisim", "std");
            LocaleManager circuit = new LocaleManager("resources/logisim", "circuit");
            assertEquals("File", menu.get("fileMenu"));
            assertEquals("Poke Tool", tools.get("pokeTool"));
            assertEquals("Edit Tool", tools.get("editTool"));
            assertEquals("Wiring", std.get("wiringLibrary"));
            assertEquals("Plexers", std.get("plexerLibrary"));
            assertEquals("Splitter", circuit.get("splitterComponent"));
            assertEquals("Data Bits", std.get("stdDataWidthAttr"));
            assertTrue(hangul(tools.get("pokeToolDesc")), "tool descriptions stay Korean");
        } finally {
            LocaleManager.setLocale(old);
        }
    }

    /** lib-mips의 이름: 라이브러리, 부품, 속성, 선택지. 한국어 설정에서도 영어다. */
    @Test
    void libMipsNamesAreEnglishUnderKorean() throws Exception {
        Locale old = LocaleManager.getLocale();
        URL jar = new File(System.getProperty("hcs.mipsJar")).toURI().toURL();
        try (URLClassLoader cl = new URLClassLoader(new URL[] {jar}, getClass().getClassLoader())) {
            LocaleManager.setLocale(new Locale("ko"));
            Library lib = (Library) cl.loadClass(BundledLibraries.MIPS_CLASS).getDeclaredConstructor().newInstance();
            List<String> seen = new ArrayList<>();
            seen.add(lib.getDisplayName());
            for (Tool t : lib.getTools()) {
                seen.add(t.getDisplayName());
                AttributeSet as = t.getAttributeSet();
                for (Attribute<?> a : as.getAttributes()) {
                    seen.add(a.getDisplayName());
                    seen.add(display(a, as));
                }
            }
            List<String> bad = new ArrayList<>();
            for (String s : seen) {
                if (s != null && hangul(s)) {
                    bad.add(s);
                }
            }
            assertEquals(new ArrayList<String>(), bad);
            assertTrue(seen.containsAll(Arrays.asList("Instruction Memory", "Data Memory", "Stack", "Console",
                    "Radix Probe", "Start Address")), seen.toString());
        } finally {
            LocaleManager.setLocale(old);
        }
    }

    @SuppressWarnings("unchecked")
    private static String display(Attribute<?> a, AttributeSet as) {
        Object v = as.getValue(a);
        return v == null ? null : ((Attribute<Object>) a).toDisplayString(v);
    }

    /** lib-mips 소스의 Text.name(…)에는 한글이 없다. */
    @Test
    void libMipsNameCallsHaveNoHangul() throws Exception {
        Pattern call = Pattern.compile("Text\\.name\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        int n = 0;
        for (File f : javaFiles(new File(ROOT, "lib-mips/src/main/java"))) {
            Matcher m = call.matcher(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            while (m.find()) {
                assertFalse(hangul(m.group(1)), f.getName() + ": " + m.group(1));
                n++;
            }
        }
        assertTrue(n > 15, "Text.name calls: " + n);
    }

    /** GLOSSARY.md "문장 안에서 쓰지 않는 번역" 칸의 말. */
    static List<String[]> forbiddenTranslations() throws IOException {
        List<String[]> ret = new ArrayList<>();
        boolean inTable = false; // 머리가 "| 영어 이름 | 문장 안에서 쓰지 않는 번역 |"인 표만 읽는다
        for (String line : Files.readAllLines(new File(ROOT, "docs/GLOSSARY.md").toPath(), StandardCharsets.UTF_8)) {
            String[] cells = line.split("\\|", -1);
            if (!line.startsWith("|")) {
                inTable = false;
                continue;
            }
            if (cells.length == 4 && cells[2].contains("쓰지 않는 번역")) {
                inTable = true;
                continue;
            }
            if (!inTable || line.contains("---")) {
                continue;
            }
            for (String bad : cells[2].split(",")) {
                if (!bad.trim().isEmpty()) {
                    ret.add(new String[] {bad.trim(), cells[1].trim()});
                }
            }
        }
        return ret;
    }

    /** 한국어 설명 문장: 원조 번역, 앱 messages_ko, lib-mips Text.of의 둘째 인자. "출처: 문구". */
    static List<String> koreanSentences() throws IOException {
        List<String> ret = new ArrayList<>();
        for (String name : BUNDLES) {
            Properties p = load(new File(ROOT, "app/resources/logisim/ko/" + name + ".properties"), false);
            for (String k : p.stringPropertyNames()) {
                ret.add(name + "#" + k + ": " + p.getProperty(k));
            }
        }
        Properties m = load(app("messages_ko.properties"), true);
        for (String k : m.stringPropertyNames()) {
            ret.add("messages_ko#" + k + ": " + m.getProperty(k));
        }
        Pattern textOf = Pattern.compile("Text\\.of\\(\\s*(?:\"(?:[^\"\\\\]|\\\\.)*\"[^,]*),\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        int n = 0;
        for (File f : javaFiles(new File(ROOT, "lib-mips/src/main/java"))) {
            Matcher mt = textOf.matcher(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            while (mt.find()) {
                ret.add(f.getName() + ": " + mt.group(1));
                n++;
            }
        }
        assertTrue(n > 15, "lib-mips Korean texts found: " + n);
        return ret;
    }

    @Test
    void glossaryListsTheEnglishNames() throws Exception {
        List<String[]> bad = forbiddenTranslations();
        assertTrue(bad.size() >= 10, "forbidden translations: " + bad.size());
        String all = new String(Files.readAllBytes(new File(ROOT, "docs/GLOSSARY.md").toPath()),
                StandardCharsets.UTF_8);
        for (String name : new String[] {"Poke Tool", "Edit Tool", "Wiring", "Plexers", "Splitter", "Load .s",
            "1 Cycle", "N Cycles", "Quick Attributes", "Show in Attribute Panel", "Fit to Window", "Labels: All"}) {
            assertTrue(all.contains(name), name);
        }
    }

    @Test
    void koreanSentencesUseEnglishNames() throws Exception {
        List<String> found = new ArrayList<>();
        List<String[]> bad = forbiddenTranslations();
        for (String t : koreanSentences()) {
            for (String[] b : bad) {
                if (t.contains(b[0])) {
                    found.add(t + "  →  \"" + b[0] + "\" 대신 \"" + b[1] + "\"");
                }
            }
        }
        assertEquals(new ArrayList<String>(), found);
    }

    @Test
    void noOldTranslationLeftForNames() throws Exception {
        assertNull(KoreanBundleTest.load("ko", "circuit").getProperty("splitterComponent"));
        assertNull(KoreanBundleTest.load("ko", "menu").getProperty("fileMenu"));
    }

    /**
     * 검토 3차: 이름인데 한국어로 남았던 네 곳(팔레트 아래 안내, 부품 검색 칸, 빠른 속성 창 안내, .s 불러오기 요약)이
     * 한국어 설정에서도 영어다. 불러오기 요약의 실행 검사는 lib-mips LoadSummaryTest가 한다.
     */
    @Test
    void formerlyKoreanNamesStayEnglish() throws Exception {
        Properties names = load(app("names.properties"), true);
        String[][] expected = {
            {"palette.hint", "\u2191\u2193 Select \u00B7 Enter Place/Run \u00B7 Alt+Enter Favorite \u00B7 Esc Close"},
            {"toolbox.search", "Search components (e.g. mux 32, register)"},
            {"quick.hintLabel", "F2: Label"},
            {"quick.hintRotate", "R: Rotate"},
        };
        for (String[] e : expected) {
            assertTrue(names.containsKey(e[0]), e[0] + " is a name");
            assertEquals(e[1], Messages.get(Locale.KOREAN, e[0]));
        }
        String loader = new String(Files.readAllBytes(new File(ROOT,
                "lib-mips/src/main/java/kr/ac/hallym/hcs/mips/ProgramLoader.java").toPath()), StandardCharsets.UTF_8);
        assertFalse(loader.contains("Text.of("), "the load summary is English (Text.name)");
    }

    /** 검토 3차: 폭 표시는 1이면 단수다(1 bit, 32 bits). */
    @Test
    void bitWidthsUseSingularForOne() {
        for (Locale l : new Locale[] {Locale.KOREAN, Locale.ENGLISH}) {
            assertEquals("1 bit", Messages.get(l, "menu.sum.bits", 1));
            assertEquals("32 bits", Messages.get(l, "menu.sum.bits", 32));
            assertEquals("1 bit", Messages.get(l, "hover.width", 1));
            assertEquals("1 bit", Messages.get(l, "menu.bits", 1));
            assertEquals("8 bits", Messages.get(l, "splitter.bits", 8));
            assertEquals("Width: 1 bit", Messages.get(l, "net.width", 1));
            assertEquals("clk \u00B7 1 bit", Messages.get(l, "keys.portTip", "clk", 1));
        }
        assertEquals("Value for A (1 bit):", Messages.get(Locale.ENGLISH, "keys.valuePrompt", "A", 1));
        assertEquals("Value for A (32 bits):", Messages.get(Locale.ENGLISH, "keys.valuePrompt", "A", 32));
        List<String> plural = new ArrayList<>();
        for (Properties p : new Properties[] {loadQuiet("names.properties"), loadQuiet("messages.properties")}) {
            for (String k : p.stringPropertyNames()) {
                if (p.getProperty(k).matches(".*\\{\\d+\\} bits.*") && !p.getProperty(k).contains("choice")) {
                    plural.add(k);
                }
            }
        }
        assertEquals(new ArrayList<String>(), plural, "\"{n} bits\" without a choice for 1");
    }

    private static Properties loadQuiet(String name) {
        try {
            return load(app(name), true);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    static List<File> javaFiles(File dir) {
        List<File> out = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory()) {
                    out.addAll(javaFiles(f));
                } else if (f.getName().endsWith(".java")) {
                    out.add(f);
                }
            }
        }
        return out;
    }
}
