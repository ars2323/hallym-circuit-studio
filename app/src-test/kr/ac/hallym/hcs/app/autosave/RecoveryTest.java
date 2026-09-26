/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.autosave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.regress.CircEquivalence;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * R-04 자동 저장 복구: 저장하지 않은 편집이 자동 저장된 채 프로그램이 강제로 끝난 뒤(같은 폴더의 새 저장소로 "다시
 * 실행"), 남은 자동 저장이 목록에 나오고, 그것을 열면 편집이 들어 있으며, 복구 저장한 파일은 원조 로더가 같은 회로로 읽고
 * 원본 파일은 그대로다.
 */
class RecoveryTest {
    @TempDir
    Path tmp;

    @Test
    void autosaveSurvivesAKillAndRecoversTheEdit() throws Exception {
        File dir = tmp.toFile();
        File original = new File(dir, "hw.circ");
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), dir);
        CircuitBuilder.save(f, original); // 저장된 원본: 빈 main
        byte[] originalBytes = Files.readAllBytes(original.toPath());

        // 편집(게이트 하나)만 하고 저장하지 않은 채 자동 저장이 돈다
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        b.add("Gates", "AND Gate", 300, 100);
        b.commit();
        File autosaveDir = new File(dir, "autosave");
        AutoSaveStore store = new AutoSaveStore(autosaveDir);
        File snapshot = new File(dir, "snapshot.circ");
        CircuitBuilder.save(f, snapshot);
        byte[] xml = Files.readAllBytes(snapshot.toPath());
        store.write(AutoSaveStore.key(original, "hw"), original, "hw", xml, 1_000_000L);

        // "강제 종료 → 다시 실행": 새 저장소 객체가 같은 폴더를 본다
        AutoSaveStore again = new AutoSaveStore(autosaveDir);
        List<AutoSaveStore.Entry> left = again.list();
        assertEquals(1, left.size(), "the autosave is offered");
        AutoSaveStore.Entry e = left.get(0);
        assertEquals(original.getAbsoluteFile(), e.original.getAbsoluteFile());

        Loader loader = new Loader(null);
        LogisimFile recovered = loader.openLogisimFile(e.circ);
        Circuit main = recovered.getMainCircuit();
        assertEquals(1, main.getNonWires().size(), "the unsaved gate is in the recovered file");
        assertEquals("AND Gate", main.getNonWires().iterator().next().getFactory().getName());

        File dest = new File(dir, "hw-recovered.circ");
        assertTrue(AutoSave.saveRecovered(loader, recovered, dest));
        assertEquals(Collections.<String>emptyList(), CircEquivalence.compare(e.circ, dest));
        assertTrue(Files.readAllBytes(original.toPath()).length == originalBytes.length
                && java.util.Arrays.equals(originalBytes, Files.readAllBytes(original.toPath())),
                "the original file is untouched");
        again.delete(AutoSaveStore.key(original, "hw"));
        assertTrue(again.list().isEmpty());
    }
}
