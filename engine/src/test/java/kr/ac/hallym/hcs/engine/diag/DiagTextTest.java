/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import kr.ac.hallym.hcs.app.diag.Diagnostic;

/**
 * v2 Messages 문구(D-143): 영어·한국어 틀이 같은 키를 갖고, 한국어 틀에서 이름 인자 바로 뒤에 조사가 붙지 않으며(v2 지시
 * 7절: 이름 뒤에는 낱말·빈칸·문장 부호), 안쪽 문장은 같은 언어로, lib-mips 몸체 글자는 두 언어로 다시 쓴다.
 */
class DiagTextTest {
    static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    static ResourceBundle bundle(Locale l) {
        return ResourceBundle.getBundle(DiagText.BUNDLE, l, NO_FALLBACK);
    }

    @Test
    void bothLanguagesHaveTheSameKeys() {
        Set<String> en = new TreeSet<>(bundle(Locale.ROOT).keySet());
        Set<String> ko = new TreeSet<>();
        ResourceBundle k = bundle(Locale.KOREAN);
        for (String key : k.keySet()) {
            if (k.getString(key) != null) {
                ko.add(key);
            }
        }
        assertEquals(en, ko);
        for (Diagnostic.Kind kind : Diagnostic.Kind.values()) {
            assertTrue(en.contains("diag." + kind.name()), kind.name());
        }
    }

    /** 수가 들어가는 인자(사이클, 비트 수, 주소·번호): 뒤에 "에", "비트"가 와도 된다. 나머지는 이름이다. */
    static final Map<String, Set<Integer>> NUMBERS = Map.of(
            "diag.WIDTH_MISMATCH", Set.of(2, 4),
            "diag.E_APPEARED", Set.of(0),
            "diag.X_WRITE_DATA", Set.of(0),
            "diag.X_WRITE_CONTROL", Set.of(0),
            "diag.OSCILLATION", Set.of(0),
            "diag.MIPS_STATUS", Set.of(0),
            "mips.syscall", Set.of(0),
            "mips.stringTooLong", Set.of(0));
    /** 뒤에 오는 문장(원인, E 표기)이라 이름이 아닌 인자. */
    static final Map<String, Set<Integer>> SENTENCES = Map.of(
            "diag.E_APPEARED", Set.of(2, 3),
            "diag.X_WRITE_DATA", Set.of(3),
            "diag.X_WRITE_CONTROL", Set.of(3),
            "diag.MIPS_STATUS", Set.of(2, 3),
            "diag.causePrefix", Set.of(0));

    @Test
    void noKoreanParticleRightAfterAName() {
        ResourceBundle ko = bundle(Locale.KOREAN);
        List<String> bad = new ArrayList<>();
        for (String key : new TreeSet<>(ko.keySet())) {
            String p = ko.getString(key);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{(\\d+)[^}]*}").matcher(p);
            while (m.find()) {
                int arg = Integer.parseInt(m.group(1));
                if (NUMBERS.getOrDefault(key, Set.of()).contains(arg)
                        || SENTENCES.getOrDefault(key, Set.of()).contains(arg)) {
                    continue;
                }
                if (m.end() < p.length()) {
                    char next = p.charAt(m.end());
                    if (next >= '가' && next <= '힣') {
                        bad.add(key + ": {" + arg + "}" + next + " in " + p);
                    }
                }
            }
        }
        assertEquals(new ArrayList<String>(), bad);
    }

    @Test
    void nestedSentencesFollowTheLanguage() {
        Diagnostic.Text cause = Diagnostic.Text.of("diag.cause.INPUT_PIN", "main › RegWrite");
        Diagnostic.Text prefix = Diagnostic.Text.of("diag.causePrefix", cause);
        assertEquals("원인: main › RegWrite 입력 핀의 값이 정해지지 않았습니다.",
                DiagText.render(DiagText.KO, prefix.key, prefix.args().toArray()));
        assertEquals("Cause: input pin main › RegWrite has no defined value.",
                DiagText.render(DiagText.EN, prefix.key, prefix.args().toArray()));
    }

    @Test
    void mipsBodyTextIsWrittenInBothLanguages() {
        String[][] cases = {
            {"Addr not word-aligned", "Addr not word-aligned", "Addr 포트의 주소가 워드 정렬이 아닙니다"},
            {"Addr가 워드 정렬 안 됨", "Addr not word-aligned", "Addr 포트의 주소가 워드 정렬이 아닙니다"},
            {"20000000 is in no memory region", "20000000 is in no memory region",
                "20000000 주소는 어느 메모리 영역에도 없습니다"},
            {"Stack 사용량이 한계(256KB)를 넘었습니다", "Stack use exceeds its limit (256KB)",
                "Stack 사용량이 한계(256KB)를 넘었습니다"},
            {"syscall 99 not supported", "syscall 99 not supported", "지원하지 않는 syscall 번호 99입니다"},
            {"V0가 정의되지 않음", "V0 undefined", "V0 포트 값이 정해지지 않았습니다"},
            {"undefined byte at 0x10010000", "undefined byte at 0x10010000",
                "0x10010000 주소의 바이트가 정해지지 않았습니다"},
            {"something new", "something new", "something new"},
        };
        for (String[] c : cases) {
            DiagText.Both b = DiagText.MipsText.both(c[0]);
            assertEquals(Arrays.asList(c[1], c[2]), Arrays.asList(b.get(Locale.ENGLISH), b.get(Locale.KOREAN)),
                    c[0]);
        }
        Set<String> seen = new HashSet<>();
        for (String k : bundle(Locale.ROOT).keySet()) {
            if (k.startsWith("mips.")) {
                seen.add(k);
            }
        }
        assertEquals(10, seen.size(), "every lib-mips body text form has a v2 wording");
    }
}
