/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #23, D-026: 한국어 OS에서도 {@code -tty} 출력(통계 머리, 멈춘 이유)은 원조 2.7.1처럼 영어다. 채점 스크립트가
 * 원조의 출력을 읽기 때문이다. {@code -locale ko}를 주면 한국어로 나온다.
 */
class TtyLocaleTest {
    static final File FORK = new File(System.getProperty("hcs.forkJar"));
    static final File ORIGINAL = new File(System.getProperty("hcs.logisimJar"));
    static final File DIR = new File(System.getProperty("hcs.circDir"));

    @TempDir
    static Path prefs;

    /** 한국어 OS처럼 실행한다. Logisim은 -locale을 Java 환경설정에 저장하므로 테스트용 환경설정 폴더를 쓴다. */
    static String run(File jar, String... extra) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList(
                new File(new File(System.getProperty("java.home"), "bin"), "java").getPath(),
                "-Djava.util.prefs.userRoot=" + prefs, "-Djava.awt.headless=true",
                "-Duser.language=ko", "-Duser.country=KR",
                "-jar", jar.getPath(), "gates.circ", "-tty", "table,stats"));
        cmd.addAll(Arrays.asList(extra));
        Process p = new ProcessBuilder(cmd).directory(DIR).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(out);
        }
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        return "exit=" + p.exitValue() + "\n" + out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void ttyIsEnglishOnKoreanSystemsLikeTheOriginal() throws Exception {
        String original = run(ORIGINAL);
        String fork = run(FORK);
        assertTrue(original.contains("TOTAL"), original);
        assertEquals(original, fork);
    }

    @Test
    void explicitKoreanLocaleIsHonouredAndDoesNotStick() throws Exception {
        String ko = run(FORK, "-locale", "ko");
        // 표 머리(TOTAL 등)는 이름이라 한국어 설정에서도 영어다(D-049)
        assertTrue(ko.startsWith("exit=0") && ko.contains("TOTAL"), ko);
        // -locale ko가 환경설정에 남아도 다음 -tty는 영어다(원조와 같다)
        assertEquals(run(ORIGINAL), run(FORK));
    }
}
