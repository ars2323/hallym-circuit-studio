/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.memo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.regress.CircEquivalence;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * E-08: 고른 부품 둘레 상자(격자, 여백), 더하기·고치기·지우기와 되돌리기, 점으로 찾기(겹치면 안쪽), 확장 정보로
 * 저장해 다시 열면 돌아오고 원조가 여는 회로 부분은 바이트 그대로, 그리기가 예외 없이 된다.
 */
class AreaMemosTest {
    @TempDir
    Path tmp;

    @Test
    void boxAroundSelectionAndDefaultBox() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component a = b.add("Gates", "NOT Gate", 300, 100);
        Component d = b.add("Gates", "NOT Gate", 365, 203);
        b.commit();
        Bounds box = AreaMemos.around(Arrays.asList(a, d), Location.create(0, 0));
        Bounds parts = a.getBounds().add(d.getBounds());
        assertTrue(box.contains(parts.expand(AreaMemos.MARGIN - 1)), "margin around the parts " + box);
        assertEquals(0, box.getX() % 10);
        assertEquals(0, box.getWidth() % 10);
        Bounds dflt = AreaMemos.around(Collections.<Component>emptyList(), Location.create(123, 456));
        assertEquals(AreaMemos.DEFAULT_W, dflt.getWidth());
        assertTrue(dflt.contains(Location.create(123, 456)));
    }

    @Test
    void addEditDeleteUndoAndSave() throws Exception {
        File dir = tmp.toFile();
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), dir);
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        b.add("Gates", "NOT Gate", 300, 100);
        b.commit();
        Circuit main = f.getMainCircuit();
        File plain = new File(dir, "plain.circ");
        CircuitBuilder.save(f, plain);
        Project proj = new Project(f);
        AreaMemos.Memo big = new AreaMemos.Memo(Bounds.create(100, 100, 400, 300), 0, "IF");
        AreaMemos.Memo small = new AreaMemos.Memo(Bounds.create(200, 200, 100, 100), 3, "PC & +4");
        proj.doAction(AreaMemos.action(f, main, null, big));
        proj.doAction(AreaMemos.action(f, main, null, small));
        assertEquals(Arrays.asList(big, small), AreaMemos.of(f, main));
        assertEquals(small, AreaMemos.at(f, main, Location.create(250, 250)), "inner box wins");
        assertEquals(big, AreaMemos.at(f, main, Location.create(120, 120)));
        assertNull(AreaMemos.at(f, main, Location.create(900, 900)));
        AreaMemos.Memo edited = new AreaMemos.Memo(big.bounds, 5, "IF stage");
        proj.doAction(AreaMemos.action(f, main, big, edited));
        assertEquals(Arrays.asList(edited, small), AreaMemos.of(f, main), "edit keeps the place");
        proj.undoAction();
        assertEquals(Arrays.asList(big, small), AreaMemos.of(f, main));
        proj.doAction(AreaMemos.action(f, main, small, null));
        assertEquals(Collections.singletonList(big), AreaMemos.of(f, main));
        proj.undoAction();
        assertEquals(2, AreaMemos.of(f, main).size());

        File ext = new File(dir, "ext.circ");
        CircuitBuilder.save(f, ext);
        CircExtensions.afterSave(f, ext);
        String xml = new String(Files.readAllBytes(ext.toPath()), StandardCharsets.UTF_8);
        assertTrue(xml.contains("<hcs:memo ") && xml.contains("PC &amp; +4"), "saved in the extension namespace");
        assertEquals(Collections.<String>emptyList(), CircEquivalence.compare(plain, ext));
        String stripped = xml.replaceAll("(?s)  <hcs:ext .*?</hcs:ext>\\r?\\n", "");
        assertEquals(new String(Files.readAllBytes(plain.toPath()), StandardCharsets.UTF_8), stripped,
                "byte-identical outside the extension block");
        LogisimFile again = new Loader(null).openLogisimFile(ext);
        CircExtensions.afterOpen(again, ext);
        assertEquals(Arrays.asList(big, small), AreaMemos.of(again, again.getMainCircuit()));

        BufferedImage img = new BufferedImage(600, 500, BufferedImage.TYPE_INT_ARGB);
        MemoOverlay.paint(img.createGraphics(), AreaMemos.of(f, main));
        assertTrue((img.getRGB(101, 300) >>> 24) > 0, "box fill drawn");
        assertEquals(0, img.getRGB(550, 450) >>> 24, "outside untouched");
    }
}
