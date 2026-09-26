/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.submit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

/**
 * E-06: .circ와 .s와 MIPS jar를 .circ 폴더 기준 경로 그대로 묶는다. 점검(저장, Messages, Probe, 빠진 파일). jar가
 * .circ 옆에 없으면 번들 jar를 그 이름으로 넣는다.
 */
class SubmissionTest {
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));
    static final File CIRC = new File(System.getProperty("hcs.circDir"), "demo-datapath.circ");

    @TempDir
    Path tmp;

    LogisimFile open(Path dir, boolean jarNextToIt, String source) throws Exception {
        Files.createDirectories(dir);
        if (jarNextToIt) {
            Files.copy(MIPS_JAR.toPath(), dir.resolve("hcs-mips.jar"), StandardCopyOption.REPLACE_EXISTING);
        }
        Path to = dir.resolve("demo-datapath.circ");
        Files.copy(CIRC.toPath(), to, StandardCopyOption.REPLACE_EXISTING);
        LogisimFile f = new Loader(null).openLogisimFile(to.toFile());
        if (source != null) {
            for (Component x : f.getMainCircuit().getNonWires()) {
                if (x.getFactory().getName().equals("Instruction Memory")) {
                    @SuppressWarnings("unchecked")
                    Attribute<Object> a = (Attribute<Object>) x.getAttributeSet().getAttribute("source");
                    CircuitMutation m = new CircuitMutation(f.getMainCircuit());
                    m.set(x, a, source);
                    m.execute();
                }
            }
        }
        return f;
    }

    static List<String> entries(File zip) throws Exception {
        List<String> out = new ArrayList<>();
        try (ZipInputStream z = new ZipInputStream(Files.newInputStream(zip.toPath()))) {
            for (ZipEntry e = z.getNextEntry(); e != null; e = z.getNextEntry()) {
                out.add(e.getName());
            }
        }
        return out;
    }

    @Test
    void bundlesTheCircuitProgramAndJar() throws Exception {
        Path dir = tmp.resolve("a");
        LogisimFile f = open(dir, true, "prog/sum.s");
        Files.createDirectories(dir.resolve("prog"));
        Files.write(dir.resolve("prog/sum.s"), "main: jr $ra\n".getBytes(StandardCharsets.UTF_8));
        Submission s = Submission.plan(f, 0, false);
        assertEquals(Arrays.asList("demo-datapath.circ", "hcs-mips.jar", "prog/sum.s"), new ArrayList<>(
                s.files.keySet()));
        for (Submission.Check c : s.checks) {
            assertTrue(c.ok, c.toString());
        }
        File zip = Submission.suggestedZip(f);
        assertEquals("demo-datapath-submission.zip", zip.getName());
        s.write(zip);
        assertEquals(Arrays.asList("demo-datapath.circ", "hcs-mips.jar", "prog/sum.s"), entries(zip));
        assertTrue(SubmissionDialog.summary(s).contains("prog/sum.s"));
    }

    @Test
    void checksWarnButDoNotBlock() throws Exception {
        Path dir = tmp.resolve("b");
        LogisimFile f = open(dir, true, "../elsewhere/sum.s");
        Submission s = Submission.plan(f, 2, true);
        assertFalse(s.checks.get(0).ok, "unsaved changes");
        assertFalse(s.checks.get(1).ok, "two messages");
        assertFalse(s.checks.get(3).ok, "a file outside the folder");
        assertEquals(Arrays.asList("../elsewhere/sum.s"), s.missing);
        assertEquals(2, s.files.size(), "the rest is still bundled");
    }

    @Test
    void theBundledJarStandsInWhenItIsNotNextToTheCircuit() throws Exception {
        String before = System.getProperty("hcs.bundledMips");
        System.setProperty("hcs.bundledMips", MIPS_JAR.getAbsolutePath());
        try {
            Path dir = tmp.resolve("c");
            LogisimFile f = open(dir, false, null);
            Submission s = Submission.plan(f, 0, false);
            assertEquals(MIPS_JAR.getAbsoluteFile(), s.files.get("hcs-mips.jar").getAbsoluteFile());
            assertTrue(s.missing.isEmpty());
        } finally {
            if (before == null) {
                System.clearProperty("hcs.bundledMips");
            } else {
                System.setProperty("hcs.bundledMips", before);
            }
        }
    }
}
