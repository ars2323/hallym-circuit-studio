/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.ext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.regress.CircEquivalence;
import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;
import kr.ac.hallym.hcs.regress.Engine;

/**
 * #67, D-024: .circ 확장 정보(hcs:ext)의 읽기·쓰기. 확장 정보가 없으면 저장 결과가 원조와 같고(D-006),
 * 확장 정보가 든 파일을 원조 2.7.1이 오류 없이 열어 같은 결과를 낸다.
 */
class CircExtensionTest {
    static final File DIR = new File(System.getProperty("hcs.circDir"));
    static final File ORIGINAL_JAR = new File(System.getProperty("hcs.logisimJar"));

    @TempDir
    Path tmp;

    static CircExtension.Item item(String kind, String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return new CircExtension.Item(kind, m);
    }

    static CircExtension sample() {
        CircExtension ext = new CircExtension();
        ext.add("main", item("tunnel", "label", "PC", "color", "#1f77b4"));
        ext.add("main", item("area", "x", "10", "y", "20", "w", "300", "h", "200",
                "note", "IF 단계\n\"PC\" & <명령어>\t끝"));
        ext.add("데이터패스", item("group", "name", "ctrl", "wires", "RegDst,ALUSrc"));
        return ext;
    }

    File copy(String name) throws Exception {
        Path dst = tmp.resolve(name + ".circ");
        Files.copy(new File(DIR, name + ".circ").toPath(), dst, StandardCopyOption.REPLACE_EXISTING);
        return dst.toFile();
    }

    @Test
    void roundTripKeepsEverything() throws Exception {
        File f = copy("gates");
        CircExtension ext = sample();
        CircExtensionIO.writeInto(f, ext);
        assertEquals(ext, CircExtensionIO.read(f));
    }

    @Test
    void emptyExtensionLeavesFileUntouched() throws Exception {
        File f = copy("gates");
        FileTime old = FileTime.fromMillis(1_000_000_000_000L);
        Files.setLastModifiedTime(f.toPath(), old);
        byte[] before = Files.readAllBytes(f.toPath());
        CircExtensionIO.writeInto(f, new CircExtension());
        assertArrayEquals(before, Files.readAllBytes(f.toPath()));
        assertEquals(old, Files.getLastModifiedTime(f.toPath()), "not even rewritten");
        assertTrue(CircExtensionIO.read(f).isEmpty());
    }

    @Test
    void writingTwiceReplacesTheBlockAndEmptyRemovesIt() throws Exception {
        File f = copy("gates");
        byte[] plain = Files.readAllBytes(f.toPath());
        CircExtensionIO.writeInto(f, sample());
        CircExtension other = new CircExtension();
        other.add("main", item("tunnel", "label", "A", "color", "#000000"));
        CircExtensionIO.writeInto(f, other);
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertEquals(1, text.split("<hcs:ext ", -1).length - 1);
        assertEquals(other, CircExtensionIO.read(f));
        CircExtensionIO.writeInto(f, new CircExtension());
        assertArrayEquals(plain, Files.readAllBytes(f.toPath()));
    }

    @Test
    void blockGoesLastInsideProjectWithFileLineEndings() throws Exception {
        String lf = "<?xml version=\"1.0\"?>\n<project source=\"2.7.1\" version=\"1.0\">\n</project>\n";
        CircExtension ext = new CircExtension();
        ext.add("main", item("tunnel", "label", "PC", "color", "#1f77b4"));
        String out = CircExtensionIO.insert(lf, ext);
        assertTrue(out.endsWith("  </hcs:ext>\n</project>\n"), out);
        assertTrue(out.contains("<hcs:ext xmlns:hcs=\"urn:hallym-circuit-studio:ext\" version=\"1\">\n"), out);
        assertFalse(out.contains("\r"));
        String crlf = CircExtensionIO.insert(lf.replace("\n", "\r\n"), ext);
        assertEquals(out.replace("\n", "\r\n"), crlf);
        assertThrows(java.io.IOException.class, () -> CircExtensionIO.insert("<x/>", ext));
    }

    @Test
    void badNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> item("a b"));
        assertThrows(IllegalArgumentException.class, () -> item("tunnel", "x\"y", "1"));
        assertThrows(IllegalArgumentException.class, () -> item("1st"));
    }

    @Test
    void unknownKindsAndCircuitRenames() throws Exception {
        File f = copy("gates");
        CircExtension ext = new CircExtension();
        ext.add("main", item("futureThing", "k", "v"));
        CircExtensionIO.writeInto(f, ext);
        CircExtension read = CircExtensionIO.read(f);
        assertEquals(ext, read, "kinds this version does not know are kept");
        read.renameCircuit("main", "top");
        assertEquals(Collections.singletonList("top"), read.circuits());
        read.remove("top", item("futureThing", "k", "v"));
        assertTrue(read.isEmpty());
    }

    @Test
    void notAnXmlFileGivesEmptyExtension() throws Exception {
        Path f = tmp.resolve("junk.circ");
        Files.write(f, "not xml".getBytes(StandardCharsets.UTF_8));
        assertTrue(CircExtensionIO.read(f.toFile()).isEmpty());
    }

    /** 확장 정보가 든 파일: 포크 로더로 열어 원조 방식으로 저장하면 hcs:ext만 빠지고 나머지는 원조와 같다. */
    @Test
    void forkOpensAndSavesFileWithExtension() throws Exception {
        File plain = copy("gates");
        File withExt = tmp.resolve("gates-ext.circ").toFile();
        Files.copy(plain.toPath(), withExt.toPath());
        CircExtensionIO.writeInto(withExt, sample());

        LogisimFile file = new Loader(null).openLogisimFile(withExt);
        CircExtensions.afterOpen(file, withExt);
        assertEquals(sample(), CircExtensions.of(file));

        File saved = tmp.resolve("saved.circ").toFile();
        CircuitBuilder.save(file, saved);
        String engineOnly = new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8);
        assertFalse(engineOnly.contains("hcs:"), "the engine writer knows nothing about extensions");
        assertEquals(CircNormalizer.normalize(new String(Files.readAllBytes(plain.toPath()), StandardCharsets.UTF_8)),
                CircNormalizer.normalize(engineOnly));

        CircExtensions.afterSave(file, saved);
        assertEquals(sample(), CircExtensionIO.read(saved));
        assertEquals(Collections.<String>emptyList(), CircEquivalence.compare(plain, saved));
    }

    /** 규칙 2.3: 원조 2.7.1이 확장 정보가 든 파일을 오류 없이 열고, 없을 때와 같은 결과를 낸다. */
    @Test
    void originalLogisimRunsFileWithExtension() throws Exception {
        for (String name : new String[] {"gates", "memory"}) {
            File f = copy(name);
            for (String sidecar : new String[] {".args", ".ram"}) {
                File s = new File(DIR, name + sidecar);
                if (s.exists()) {
                    Files.copy(s.toPath(), tmp.resolve(name + sidecar));
                }
            }
            CircExtensionIO.writeInto(f, sample());
            String expected = new String(Files.readAllBytes(new File(DIR, name + ".expected").toPath()),
                    StandardCharsets.UTF_8);
            assertEquals(expected, Engine.current(ORIGINAL_JAR).run(tmp.toFile(), name), name);
        }
    }
}
