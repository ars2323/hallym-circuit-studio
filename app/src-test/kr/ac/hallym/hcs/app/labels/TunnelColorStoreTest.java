/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.ext.CircExtensionIO;
import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.splitter.SplitterEdits;
import kr.ac.hallym.hcs.app.splitter.SplitterSpec;
import kr.ac.hallym.hcs.regress.CircEquivalence;
import kr.ac.hallym.hcs.regress.CircuitBuilder;
import kr.ac.hallym.hcs.regress.Engine;

/** D-042: 파일과 함께 가는 정보(터널 색, 스플리터 팔 이름)는 .circ 확장 정보로, 원조 2.7.1은 그 파일을 그대로 연다. */
class TunnelColorStoreTest {
    static final File ORIGINAL_JAR = new File(System.getProperty("hcs.logisimJar"));

    @TempDir
    Path tmp;

    /** 상수 → 스플리터 → 출력 핀 두 개와 halt, 터널로 연결. */
    private static Component build(LogisimFile file) {
        Circuit c = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, c);
        b.constant("word", 8, 0x5A, 100, 100);
        Component sp = b.add("Wiring", "Splitter", 300, 200, "fanout", "2", "incoming", "8");
        b.tunnel(sp, 0, "word");
        b.tunnel(sp, 1, "lo");
        b.tunnel(sp, 2, "hi");
        b.output("lo", 4, 500, 100);
        b.output("hi", 4, 500, 200);
        b.constant("halt", 1, 1, 100, 300); // 입력이 없는 회로는 halt 핀이 1이 될 때까지 돈다(원조 -tty)
        b.output("halt", 1, 500, 300);
        b.commit();
        return sp;
    }

    @Test
    void colorIsUndoableAndPrunedWithItsTunnels() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        build(file);
        Circuit c = file.getMainCircuit();
        Project proj = new Project(file);
        assertNull(TunnelColorStore.get(file, c, "lo"));
        assertEquals(TunnelColors.of("lo"), TunnelColorStore.display(file, c, "lo"));

        proj.doAction(TunnelColorStore.action(file, c, "lo", TunnelColors.PALETTE[3]));
        assertEquals(TunnelColors.PALETTE[3], TunnelColorStore.get(file, c, "lo"));
        assertEquals(TunnelColors.PALETTE[3], TunnelColorStore.display(file, c, "lo"));
        assertTrue(file.isDirty() || proj.isFileDirty(), "a chosen color is a change to the file");
        proj.undoAction();
        assertNull(TunnelColorStore.get(file, c, "lo"));
        proj.doAction(TunnelColorStore.action(file, c, "lo", TunnelColors.PALETTE[3]));

        // 그 이름의 터널을 모두 지우면 저장 전에 항목도 지운다
        CircuitMutation m = new CircuitMutation(c);
        for (Component comp : c.getNonWires()) {
            if ("lo".equals(TunnelColorStore.name(comp))) {
                m.remove(comp);
            }
        }
        m.execute();
        TunnelColorStore.PRUNER.prune(file, CircExtensions.of(file));
        assertNull(TunnelColorStore.get(file, c, "lo"));
    }

    /** 앱이 실제로 쓰는 길(원조 저장 + 확장 정보)로 저장한 파일을 원조 2.7.1이 열고 확장 없는 파일과 같은 결과를 낸다. */
    @Test
    void originalLogisimOpensFilesWithColorsAndArmNames() throws Exception {
        File dir = tmp.resolve("run").toFile();
        dir.mkdirs();
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Component sp = build(file);
        Circuit c = file.getMainCircuit();
        File plain = new File(dir, "plain.circ");
        CircuitBuilder.save(file, plain);

        new Project(file).doAction(TunnelColorStore.action(file, c, "word", TunnelColors.PALETTE[1]));
        SplitterSpec spec = SplitterSpec.parse("3:0, 7:4", 8, false).withNames(Arrays.asList("low", "high"));
        SplitterEdits.setNames(file, c, sp.getLocation(), spec);
        File ext = new File(dir, "ext.circ");
        CircuitBuilder.save(file, ext);
        CircExtensions.afterSave(file, ext);

        String xml = new String(Files.readAllBytes(ext.toPath()), StandardCharsets.UTF_8);
        assertTrue(xml.contains(CircExtensionIO.NS), "extension written into the .circ");
        assertTrue(xml.contains("tunnel") && xml.contains("#56B4E9") && xml.contains("high"), xml);
        assertFalse(new String(Files.readAllBytes(plain.toPath()), StandardCharsets.UTF_8).contains("hcs:"));

        Engine original = Engine.current(ORIGINAL_JAR);
        String a = original.run(dir, "plain");
        String b = original.run(dir, "ext");
        assertTrue(a.startsWith("exit=0"), a);
        assertEquals(a, b, "the original 2.7.1 ignores the extension");
        assertEquals(Collections.<String>emptyList(), CircEquivalence.compare(plain, ext));

        // 포크로 다시 열면 색과 이름이 돌아온다
        LogisimFile again = new Loader(null).openLogisimFile(ext);
        CircExtensions.afterOpen(again, ext);
        assertEquals(TunnelColors.PALETTE[1], TunnelColorStore.get(again, again.getMainCircuit(), "word"));
        assertEquals(Arrays.asList("low", "high"),
                SplitterEdits.names(again, again.getMainCircuit(), sp.getLocation()));
    }
}
