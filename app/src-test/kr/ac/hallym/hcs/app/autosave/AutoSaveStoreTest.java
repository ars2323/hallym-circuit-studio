/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.autosave;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.regress.CircNormalizer;

/** #70: 자동 저장 파일 이름·위치·정리, 복구 목록, 원본 불변. */
class AutoSaveStoreTest {
    static final File CIRC_DIR = new File(System.getProperty("hcs.circDir"));
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));
    static final File REF_MIPS = new File(System.getProperty("hcs.refMips"));

    @TempDir
    Path tmp;

    static String sha(File f) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f.toPath()));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    void keysAreStablePerFileAndSafeAsNames() {
        File a = new File("/home/s/과제 1/cpu.circ");
        assertEquals(AutoSaveStore.key(a, "x"), AutoSaveStore.key(a, "y"));
        assertNotEquals(AutoSaveStore.key(a, "x"), AutoSaveStore.key(new File("/home/s/과제 2/cpu.circ"), "x"));
        assertTrue(AutoSaveStore.key(a, "x").matches("cpu-[0-9a-f]{16}"), AutoSaveStore.key(a, "x"));
        assertTrue(AutoSaveStore.key(null, "w1").startsWith("untitled-"));
        assertNotEquals(AutoSaveStore.key(null, "w1"), AutoSaveStore.key(null, "w2"));
    }

    @Test
    void writeListDeleteNewestFirst() throws Exception {
        AutoSaveStore s = new AutoSaveStore(tmp.resolve("autosave").toFile());
        assertTrue(s.list().isEmpty(), "missing folder is fine");
        File orig = tmp.resolve("work/cpu.circ").toFile();
        byte[] xml = "<project>\n</project>\n".getBytes(StandardCharsets.UTF_8);
        s.write("a", orig, "cpu", xml, 1000);
        s.write("b", null, "Untitled", xml, 2000);
        List<AutoSaveStore.Entry> list = s.list();
        assertEquals(2, list.size());
        assertEquals("Untitled", list.get(0).title);
        assertNull(list.get(0).original);
        assertEquals(orig.getAbsoluteFile(), list.get(1).original);
        assertArrayEquals(xml, Files.readAllBytes(list.get(1).circ.toPath()));
        assertTrue(s.contains(list.get(0).circ));
        assertFalse(s.contains(orig));
        s.delete("a");
        assertEquals(1, s.list().size());
        Files.delete(tmp.resolve("autosave/b.circ"));
        assertTrue(s.list().isEmpty(), "a record without its circuit is skipped");
        try (java.util.stream.Stream<Path> files = Files.list(tmp.resolve("autosave"))) {
            assertTrue(files.noneMatch(p -> p.toString().endsWith(".tmp")), "no temp files left");
        }
    }

    @Test
    void relativeLibrariesBecomeAbsolute() {
        File base = new File("/home/s/work");
        String xml = "<lib desc=\"#Wiring\" name=\"0\"/>\n"
                + "<lib desc=\"jar#hcs-mips.jar#kr.ac.hallym.hcs.mips.MipsLibrary\" name=\"7\"/>\n"
                + "<lib desc=\"file#../lib/alu.circ\" name=\"8\"/>\n"
                + "<lib desc=\"jar#/opt/x.jar#a.B\" name=\"9\"/>\n";
        String out = AutoSaveStore.absoluteLibraries(xml, base);
        assertTrue(out.contains("desc=\"#Wiring\""));
        assertTrue(out.contains("desc=\"jar#" + new File(base, "hcs-mips.jar").getAbsolutePath()
                + "#kr.ac.hallym.hcs.mips.MipsLibrary\""), out);
        assertTrue(out.contains("desc=\"file#" + new File(base, "../lib/alu.circ").getAbsolutePath() + "\""), out);
        assertTrue(out.contains("desc=\"jar#/opt/x.jar#a.B\""));
    }

    /** 원조 writer로 쓴 자동 저장은 원본과 같은 회로이고, 원본 파일 바이트는 그대로다. 라이브러리도 찾는다. */
    @Test
    void autosaveOfAnOpenFileLeavesTheOriginalAlone() throws Exception {
        Path work = Files.createDirectories(tmp.resolve("work"));
        File circ = work.resolve("ref-mips.circ").toFile();
        Files.copy(REF_MIPS.toPath(), circ.toPath());
        Files.copy(MIPS_JAR.toPath(), work.resolve("hcs-mips.jar"), StandardCopyOption.REPLACE_EXISTING);
        String before = sha(circ);
        LogisimFile file = new Loader(null).openLogisimFile(circ);

        byte[] xml = AutoSave.serialize(file);
        assertEquals(before, sha(circ));
        assertEquals(CircNormalizer.normalize(new String(Files.readAllBytes(circ.toPath()), StandardCharsets.UTF_8)),
                CircNormalizer.normalize(new String(xml, StandardCharsets.UTF_8)));
        assertEquals(circ, file.getLoader().getMainFile(), "writing did not move the open file");
        assertEquals("ref-mips", file.getName());

        AutoSaveStore s = new AutoSaveStore(tmp.resolve("autosave").toFile());
        AutoSaveStore.Entry e = s.write(AutoSaveStore.key(circ, "x"), circ, "ref-mips", xml, 5);
        assertEquals(before, sha(circ));
        // 자동 저장 폴더에서 열어도 jar를 찾는다(창 없이)
        Loader recoveredLoader = new Loader(null);
        LogisimFile back = recoveredLoader.openLogisimFile(e.circ);
        assertEquals(file.getCircuits().size(), back.getCircuits().size());

        // 복구한 창을 원래 자리에 저장하면 정상 저장과 같다(라이브러리가 원래 폴더 기준 상대 경로)
        File restored = work.resolve("restored.circ").toFile();
        assertTrue(AutoSave.saveRecovered(recoveredLoader, back, restored));
        String saved = new String(Files.readAllBytes(restored.toPath()), StandardCharsets.UTF_8);
        assertTrue(saved.contains("desc=\"jar#hcs-mips.jar#kr.ac.hallym.hcs.mips.MipsLibrary\""), saved);
        assertEquals(CircNormalizer.normalize(new String(Files.readAllBytes(circ.toPath()), StandardCharsets.UTF_8)),
                CircNormalizer.normalize(saved));
        assertEquals(before, sha(circ), "the original was never written");
    }

    /** 한 번만 저장하면(원조 Loader.save) 자동 저장 폴더 기준 경로가 남는다: 두 번 저장하는 이유. */
    @Test
    void oneSaveFromTheAutosaveFolderLeavesAnAbsoluteLibraryPath() throws Exception {
        Path work = Files.createDirectories(tmp.resolve("w2"));
        File circ = work.resolve("ref-mips.circ").toFile();
        Files.copy(REF_MIPS.toPath(), circ.toPath());
        Files.copy(MIPS_JAR.toPath(), work.resolve("hcs-mips.jar"), StandardCopyOption.REPLACE_EXISTING);
        LogisimFile file = new Loader(null).openLogisimFile(circ);
        AutoSaveStore s = new AutoSaveStore(tmp.resolve("autosave2").toFile());
        AutoSaveStore.Entry e = s.write("k-ref", circ, "ref-mips", AutoSave.serialize(file), 5);
        Loader l = new Loader(null);
        LogisimFile back = l.openLogisimFile(e.circ);
        File once = work.resolve("once.circ").toFile();
        assertTrue(l.save(back, once));
        String text = new String(Files.readAllBytes(once.toPath()), StandardCharsets.UTF_8);
        assertFalse(text.contains("desc=\"jar#hcs-mips.jar#"), "documents why saveRecovered saves twice");
    }
}
