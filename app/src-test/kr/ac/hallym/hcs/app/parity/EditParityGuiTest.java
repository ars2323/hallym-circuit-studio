/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import kr.ac.hallym.hcs.app.gui.GuiTestSupport;
import kr.ac.hallym.hcs.regress.CircNormalizer;

/**
 * 편집 동등성 골든(N-01, D-136, {@code xvfb-run -a ./gradlew :app:guiTest}). tests/parity의 장면마다 의도를 지금의
 * Swing 앱으로 실행하고 앱의 저장 코드로 저장한 .circ가 골든과 같은지 본다. 비교 기준은 D-006(줄 앞 공백·빈 줄과
 * 회로 안 wire·comp 순서만 정규화, 나머지는 바이트 그대로). 골든 다시 쓰기: {@code -Dparity.update=true}(또는
 * {@code -Phcs.update=true}). 일부만: {@code -Dparity.only=장면,장면}. 실행 결과와 되돌리기 기록은
 * app/build/parity/에 남는다.
 */
@Tag("gui")
class EditParityGuiTest {
    static Path parityDir() {
        return Paths.get(System.getProperty("hcs.testsDir", "../tests"), "parity");
    }

    static boolean update() {
        return Boolean.getBoolean("parity.update") || Boolean.getBoolean("hcs.update")
                || "true".equals(System.getenv("PARITY_UPDATE"));
    }

    static List<Path> scenarios() throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(parityDir())) {
            s.filter(p -> p.getFileName().toString().endsWith(".intents")).sorted().forEach(out::add);
        }
        return out;
    }

    @TestFactory
    List<DynamicTest> goldensMatchTheSwingApp() throws IOException {
        String only = System.getProperty("parity.only", "");
        List<String> wanted = only.isEmpty() ? null : Arrays.asList(only.split(","));
        List<DynamicTest> tests = new ArrayList<>();
        for (Path p : scenarios()) {
            String name = p.getFileName().toString().replace(".intents", "");
            if (wanted == null || wanted.contains(name)) {
                tests.add(DynamicTest.dynamicTest(name, () -> check(p)));
            }
        }
        return tests;
    }

    private static void check(Path intents) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        IntentScript script = IntentScript.read(intents);
        Path work = Files.createTempDirectory("hcs-parity-" + script.name);
        byte[] saved;
        List<String> trace;
        try (SwingReplayer r = new SwingReplayer(script, parityDir(), work)) {
            saved = r.run();
            trace = r.trace();
        }
        Path out = Paths.get("build", "parity");
        Files.createDirectories(out);
        Files.write(out.resolve(script.name + ".circ"), saved);
        Files.write(out.resolve(script.name + ".trace"), trace, StandardCharsets.UTF_8);
        Path golden = parityDir().resolve(script.name + ".circ");
        String got = CircNormalizer.normalize(new String(saved, StandardCharsets.UTF_8));
        if (update()) {
            // 같은 내용이면 다시 쓰지 않는다: 부품 순서(identity hash)만 다른 저장으로 골든이 흔들리지 않게
            if (!Files.exists(golden) || !CircNormalizer.normalize(read(golden)).equals(got)) {
                Files.write(golden, saved);
            }
            return;
        }
        assertTrue(Files.exists(golden), golden + " is missing; run with -Dparity.update=true");
        assertEquals(CircNormalizer.normalize(read(golden)), got, script.name
                + ": the Swing app now saves something else; see app/build/parity/" + script.name + ".circ");
    }

    static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
}
