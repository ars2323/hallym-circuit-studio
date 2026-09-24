/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.regress.CircEquivalence;
import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * #22, D-007: .circ가 가리키는 hcs-mips.jar를 읽을 수 없으면 번들 jar로 연결하고, 저장할 때는 .circ에 있던
 * 설명자를 그대로 쓴다. 경로의 jar를 읽을 수 있거나 번들에 없는 라이브러리면 원조 동작 그대로다
 * (LibraryManager의 격리 패치, docs/engine-patches.txt).
 */
class BundledLibraryTest {
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));
    static final File REF_MIPS = new File(System.getProperty("hcs.refMips"));
    static final String DESC = "jar#hcs-mips.jar#kr.ac.hallym.hcs.mips.MipsLibrary";

    @TempDir
    Path tmp;

    @AfterEach
    void clearBundle() {
        System.clearProperty("hcs.bundledMips");
    }

    File refMipsIn(Path dir, String text) throws Exception {
        Files.createDirectories(dir);
        File f = dir.resolve("ref-mips.circ").toFile();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    static String refMipsText() throws Exception {
        String text = new String(Files.readAllBytes(REF_MIPS.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("desc=\"" + DESC + "\""), "ref-mips.circ names the jar relatively");
        return text;
    }

    /** MIPS 라이브러리. 실제로 불러와졌는지 부품(Instruction Memory)으로 확인한다. */
    static Library mipsLibrary(LogisimFile file) {
        for (Library lib : file.getLibraries()) {
            if (lib instanceof LoadedLibrary
                    && file.getLoader().getDescriptor(lib).endsWith("#" + BundledLibraries.MIPS_CLASS)) {
                assertNotNull(lib.getTool("Instruction Memory"), "library really loaded");
                return lib;
            }
        }
        return null;
    }

    @Test
    void missingJarLinksToBundleAndKeepsDescriptor() throws Exception {
        System.setProperty("hcs.bundledMips", MIPS_JAR.getPath());
        File circ = refMipsIn(tmp.resolve("student"), refMipsText()); // jar 없이 .circ만
        LogisimFile file = new Loader(null).openLogisimFile(circ); // 창이 뜨면 헤드리스라 예외
        Library lib = mipsLibrary(file);
        assertNotNull(lib);
        assertEquals(DESC, file.getLoader().getDescriptor(lib));

        File saved = tmp.resolve("student/saved.circ").toFile();
        CircuitBuilder.save(file, saved);
        String out = new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8);
        assertTrue(out.contains("desc=\"" + DESC + "\""), "the saved descriptor is the one in the file");
        assertEquals(CircNormalizer.normalize(refMipsText()), CircNormalizer.normalize(out));
        assertEquals(Collections.<String>emptyList(), CircEquivalence.compare(circ, saved));
    }

    @Test
    void absolutePathFromAnotherPcIsKept() throws Exception {
        System.setProperty("hcs.bundledMips", MIPS_JAR.getPath());
        String other = "/nonexistent/other-pc/hcs-mips.jar";
        String text = refMipsText().replace(DESC, "jar#" + other + "#" + BundledLibraries.MIPS_CLASS);
        File circ = refMipsIn(tmp.resolve("abs"), text);
        LogisimFile file = new Loader(null).openLogisimFile(circ);
        assertEquals("jar#" + other + "#" + BundledLibraries.MIPS_CLASS,
                file.getLoader().getDescriptor(mipsLibrary(file)));
    }

    @Test
    void readableJarIsUsedAsInTheOriginal() throws Exception {
        File garbage = tmp.resolve("broken-bundle.jar").toFile();
        Files.write(garbage.toPath(), "not a jar".getBytes(StandardCharsets.UTF_8));
        System.setProperty("hcs.bundledMips", garbage.getPath()); // 번들을 쓰면 실패한다
        Path dir = tmp.resolve("withjar");
        File circ = refMipsIn(dir, refMipsText());
        Files.copy(MIPS_JAR.toPath(), dir.resolve("hcs-mips.jar"));
        LogisimFile file = new Loader(null).openLogisimFile(circ);
        assertEquals(DESC, file.getLoader().getDescriptor(mipsLibrary(file)));
    }

    @Test
    void otherLibrariesStillAsk() throws Exception {
        System.setProperty("hcs.bundledMips", MIPS_JAR.getPath());
        String text = refMipsText().replace(DESC, "jar#missing.jar#com.example.OtherLibrary");
        File circ = refMipsIn(tmp.resolve("other"), text);
        // 원조처럼 파일 선택 창을 띄우려 한다(헤드리스라 예외).
        assertThrows(java.awt.HeadlessException.class, () -> new Loader(null).openLogisimFile(circ));
    }

    @Test
    void noBundleMeansOriginalBehaviour() throws Exception {
        System.setProperty("hcs.bundledMips", tmp.resolve("absent.jar").toString());
        File circ = refMipsIn(tmp.resolve("nobundle"), refMipsText());
        assertThrows(java.awt.HeadlessException.class, () -> new Loader(null).openLogisimFile(circ));
        assertEquals(null, BundledLibraries.substitute(circ, "x.Y"));
    }

    @Test
    void bundleSitsInLibNextToTheForkJar() {
        File home = BundledLibraries.appHome();
        File jar = BundledLibraries.mipsJar();
        if (home == null) {
            assertEquals(null, jar); // 클래스 폴더에서 도는 경우
        } else {
            assertEquals(new File(new File(home, "lib"), "hcs-mips.jar"), jar);
        }
    }
}
