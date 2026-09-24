/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.regress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/** tests/circ/의 회로를 표준 2.7.1 jar로 돌린 결과가 기대값과 같다(PLAN.md 8.3 엔진 회귀). */
class EngineRegressionTest {
    static final File DIR = new File(System.getProperty("hcs.circDir"));
    static final File REFERENCE = new File(System.getProperty("hcs.logisimJar"));

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @TestFactory
    Stream<DynamicTest> referenceJarMatchesExpected() {
        Engine engine = Engine.current(REFERENCE);
        List<String> names = Engine.circuits(DIR);
        assertFalse(names.isEmpty());
        return names.stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            String expected = read(new File(DIR, name + ".expected"));
            assertTrue(expected.startsWith("exit=0\n"), name + ": 회로가 halt로 끝나지 않음");
            assertEquals(expected, engine.run(DIR, name));
        }));
    }

    /**
     * 커밋된 회로가 생성기(Circuits)의 현재 결과와 같다. 생성기를 고쳤으면 generate와 update를 다시 돌린다.
     * 부품 순서는 identity hash 순서라 JVM 상태에 따라 달라지므로 D-006 정규화 뒤 비교한다.
     */
    @Test
    void committedCircuitsMatchGenerator(@TempDir Path tmp) throws Exception {
        Circuits.generateAll(tmp.toFile());
        List<String> generated = new ArrayList<String>();
        for (File f : tmp.toFile().listFiles()) {
            generated.add(f.getName());
            String fresh = read(f);
            String committed = read(new File(DIR, f.getName()));
            if (f.getName().endsWith(".circ")) {
                assertEquals(CircNormalizer.normalize(committed), CircNormalizer.normalize(fresh), f.getName());
                assertEquals(java.util.Collections.<String>emptyList(),
                        CircEquivalence.compare(new File(DIR, f.getName()), f), f.getName());
            } else {
                assertEquals(committed, fresh, f.getName());
            }
        }
        assertEquals(Engine.circuits(tmp.toFile()), Engine.circuits(DIR));
        assertTrue(generated.contains("memory.ram"));
    }
}
