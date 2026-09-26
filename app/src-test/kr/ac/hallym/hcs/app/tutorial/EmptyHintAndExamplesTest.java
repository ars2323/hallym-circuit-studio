/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tutorial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** V-07 (D-102): 빈 캔버스 안내는 회로가 빌 때만, 예제는 읽기 전용으로 열리고 다른 자리에 저장하면 보통 파일. */
class EmptyHintAndExamplesTest {
    @TempDir
    Path tmp;

    @Test
    void hintShowsOnlyWhileTheCircuitIsEmptyAndNamesTheThreeWays() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit main = file.getMainCircuit();
        assertTrue(EmptyHint.shouldShow(main), "a new file's circuit is empty");
        List<String> lines = EmptyHint.lines();
        assertEquals(4, lines.size());
        assertTrue(lines.get(1).contains("Ctrl+K"), lines.get(1));
        assertTrue(lines.get(3).contains("Help › Examples"), lines.get(3));
        assertTrue(lines.get(3).contains("demo-datapath"), lines.get(3));
        CircuitBuilder b = new CircuitBuilder(file, main);
        b.add("Gates", "AND Gate", 200, 200);
        b.commit();
        assertFalse(EmptyHint.shouldShow(main), "gone with the first part");
        // 파일에는 아무것도 남지 않는다
        File saved = tmp.resolve("a.circ").toFile();
        CircuitBuilder.save(file, saved);
        String xml = new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8);
        assertFalse(xml.contains("hint") || xml.contains("hcs:"), "nothing saved for the hint");
    }

    @Test
    void examplesAreBundledOpenReadOnlyAndBecomeOrdinaryAfterSaveAs() throws Exception {
        Examples.clear();
        assertEquals(List.of("demo-datapath", "console-demo", "stack-demo"), Examples.NAMES);
        for (String name : Examples.NAMES) {
            File f = Examples.extract(name);
            assertTrue(f.exists() && f.getName().equals(name + ".circ"), f.toString());
            assertFalse(f.canWrite(), "the extracted example is read-only on disk");
            String bundled = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            String repo = new String(Files.readAllBytes(new File(System.getProperty("hcs.circDir"), name + ".circ")
                    .toPath()), StandardCharsets.UTF_8);
            assertEquals(CircNormalizer.normalize(repo), CircNormalizer.normalize(bundled), name + " is the repo file");
        }
        File f = Examples.extract("demo-datapath");
        System.setProperty("hcs.bundledMips", System.getProperty("hcs.mipsJar"));
        try {
            LogisimFile file = new Loader(null).openLogisimFile(f); // jar 없이도 번들로 연다(D-007)
            assertNotNull(file.getMainCircuit());
            Project p = new Project(file);
            assertFalse(Examples.isExample(p));
            Examples.mark(p, f);
            assertTrue(Examples.isExample(p) && Examples.interceptsSave(p), "Save asks for a new name");
            Examples.saved(p, f);
            assertTrue(Examples.isExample(p), "saving onto the example file itself does not change that");
            File elsewhere = tmp.resolve("mine.circ").toFile();
            Examples.saved(p, elsewhere);
            assertFalse(Examples.isExample(p) || Examples.interceptsSave(p), "an ordinary file after Save As");
        } finally {
            System.clearProperty("hcs.bundledMips");
            Examples.clear();
        }
    }
}
