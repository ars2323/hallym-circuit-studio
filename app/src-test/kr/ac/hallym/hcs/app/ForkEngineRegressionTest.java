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
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import kr.ac.hallym.hcs.regress.Engine;

/**
 * 엔진 회귀(#19, PLAN.md 8.3): 포크 jar를 tests/circ 회로에 -tty table로 돌린 출력이 표준 2.7.1 jar(JDK 8)의
 * 기대 출력(.expected)과 글자 단위로 같다.
 */
class ForkEngineRegressionTest {
    static final File DIR = new File(System.getProperty("hcs.circDir"));
    static final File FORK = new File(System.getProperty("hcs.forkJar"));

    @TestFactory
    Stream<DynamicTest> forkMatchesTheStandardJar() {
        List<String> names = Engine.circuits(DIR);
        assertFalse(names.isEmpty());
        Engine fork = Engine.current(FORK);
        return names.stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            String expected = new String(Files.readAllBytes(new File(DIR, name + ".expected").toPath()),
                    StandardCharsets.UTF_8);
            assertEquals(expected, fork.run(DIR, name));
        }));
    }
}
