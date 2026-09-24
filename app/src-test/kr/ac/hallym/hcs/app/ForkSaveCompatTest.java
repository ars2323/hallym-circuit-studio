/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.regress.CircEquivalence;
import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;
import kr.ac.hallym.hcs.regress.Engine;

/**
 * 규칙 2.3(#20): 새 부품을 안 쓴 .circ를 포크로 열고 저장한 결과가 원조 2.7.1(JDK 8)이 저장한 결과와 같다.
 * 기준은 D-006: 줄 앞 공백·wire/comp 순서만 정규화한 뒤 바이트 비교 + 의미 동등성(원조 로더로 속성·넷리스트 비교).
 * 이 테스트의 Loader는 포크 빌드의 클래스다(app 클래스가 클래스패스 앞에 온다).
 */
class ForkSaveCompatTest {
    static final File DIR = new File(System.getProperty("hcs.circDir"));

    @TempDir
    Path tmp;

    @TestFactory
    Stream<DynamicTest> forkSavesLikeTheOriginal() {
        List<String> names = Engine.circuits(DIR);
        assertFalse(names.isEmpty());
        return names.stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            assertEquals("com.cburch.logisim.file.Loader", Loader.class.getName());
            assertFalse(Loader.class.getProtectionDomain().getCodeSource().getLocation().toString()
                    .contains("logisim-generic-2.7.1.jar"), "fork classes must come first");
            File original = new File(DIR, name + ".circ");
            LogisimFile file = new Loader(null).openLogisimFile(original);
            File saved = tmp.resolve(name + ".circ").toFile();
            CircuitBuilder.save(file, saved);
            String a = new String(Files.readAllBytes(original.toPath()), StandardCharsets.UTF_8);
            String b = new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8);
            assertEquals(CircNormalizer.normalize(a), CircNormalizer.normalize(b));
            assertEquals(Collections.<String>emptyList(), CircEquivalence.compare(original, saved));
        }));
    }
}
