/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.libs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.appear.AutoAppearance;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * P-05: 고른 회로와 딸린 서브회로가 딸린 것 먼저 들어오고, 이름이 겹치면 번호가 붙으며, 인스턴스는 사본을 가리키고,
 * 사용자 모양이 새 핀에 맞춰 복사되고, 되돌리면 사라지며, 저장한 파일을 원조 로더가 다시 연다.
 */
class CircuitImportTest {
    @TempDir
    Path tmp;

    /** 원본: half(입력 a,b → 출력 s)와 그것을 둘 쓰는 full, 그리고 main. */
    static LogisimFile source(File dir) throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), dir);
        Circuit half = new Circuit("half");
        f.addCircuit(half);
        CircuitBuilder b = new CircuitBuilder(f, half);
        b.input("a", 1, 100, 100);
        b.input("b", 1, 100, 200);
        Component x = b.add("Gates", "XOR Gate", 300, 150);
        b.tunnel(x, 1, "a");
        b.tunnel(x, 2, "b");
        b.tunnel(x, 0, "s");
        b.output("s", 1, 500, 150);
        b.commit();
        Circuit full = new Circuit("full");
        f.addCircuit(full);
        CircuitBuilder c = new CircuitBuilder(f, full);
        c.addSubcircuit(half, 300, 100);
        c.addSubcircuit(half, 300, 300);
        c.input("x", 1, 100, 100);
        c.commit();
        return f;
    }

    @Test
    void importsWithDependenciesRenamesAndUndoes() throws Exception {
        File dir = tmp.toFile();
        LogisimFile src = source(dir);
        LogisimFile dst = CircuitBuilder.newFile(new Loader(null), dir);
        Circuit clash = new Circuit("half"); // 같은 이름이 이미 있다
        dst.addCircuit(clash);
        Circuit full = null;
        for (Circuit c : src.getCircuits()) {
            if (c.getName().equals("full")) {
                full = c;
            }
        }
        // 사용자 모양(Auto Appearance)도 복사되는지
        new Project(src).doAction(AutoAppearance.action(full, AutoAppearance.build(full)));
        CircuitImport.Plan plan = CircuitImport.plan(dst, src, Collections.singletonList(full));
        List<String> order = new ArrayList<>();
        for (Circuit c : plan.order) {
            order.add(c.getName());
        }
        assertEquals(Arrays.asList("half", "full"), order, "dependency first");
        assertEquals("half-2", plan.names.get(plan.order.get(0)));
        assertEquals("full", plan.names.get(full));
        assertTrue(plan.skipped.isEmpty());
        String summary = ImportDialog.summary(plan);
        assertTrue(summary.contains("half  →  half-2") && summary.contains("full"), summary);

        Project proj = new Project(dst);
        proj.doAction(CircuitImport.action(dst, plan));
        Circuit newFull = null;
        Circuit newHalf = null;
        for (Circuit c : dst.getCircuits()) {
            if (c.getName().equals("full")) {
                newFull = c;
            } else if (c.getName().equals("half-2")) {
                newHalf = c;
            }
        }
        List<String> after = new ArrayList<>();
        for (Circuit c : dst.getCircuits()) {
            after.add(c.getName());
        }
        assertNotNull(newFull, "circuits: " + after);
        assertNotNull(newHalf, "circuits: " + after);
        int instances = 0;
        for (Component c : newFull.getNonWires()) {
            if (c.getFactory() instanceof SubcircuitFactory) {
                assertEquals(newHalf, ((SubcircuitFactory) c.getFactory()).getSubcircuit(),
                        "instances point at the copy, not the original file's circuit");
                instances++;
            }
        }
        assertEquals(2, instances);
        assertEquals(4, newHalf.getNonWires().size() - countTunnels(newHalf), "pins and gate copied (tunnels aside)");
        assertFalse(newFull.getAppearance().isDefaultAppearance(), "user appearance copied");
        assertEquals(full.getAppearance().getPortOffsets(com.cburch.logisim.data.Direction.EAST).size(),
                newFull.getAppearance().getPortOffsets(com.cburch.logisim.data.Direction.EAST).size());

        File saved = new File(dir, "imported.circ");
        CircuitBuilder.save(dst, saved);
        LogisimFile again = new Loader(null).openLogisimFile(saved);
        List<String> names = new ArrayList<>();
        for (Circuit c : again.getCircuits()) {
            names.add(c.getName());
        }
        assertTrue(names.containsAll(Arrays.asList("half", "half-2", "full")), names.toString());

        proj.undoAction();
        names.clear();
        for (Circuit c : dst.getCircuits()) {
            names.add(c.getName());
        }
        assertEquals(Arrays.asList("main", "half"), names, "undo removes the copies");
    }

    static int countTunnels(Circuit c) {
        int n = 0;
        for (Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals("Tunnel")) {
                n++;
            }
        }
        return n;
    }
}
