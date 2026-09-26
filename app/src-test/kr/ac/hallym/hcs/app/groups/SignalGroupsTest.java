/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.groups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.regress.CircEquivalence;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * E-04: control 서브회로의 출력은 저절로 Control, 우클릭으로 정한 그룹은 되돌릴 수 있고 .circ 확장 정보로 저장되어 다시
 * 열면 돌아오며(원조 2.7.1 호환), 넷이 사라지면 저장 전에 지운다. 보기(Values/Groups)는 앱 환경설정.
 */
class SignalGroupsTest {
    @TempDir
    Path tmp;

    /** control(입력 op → 출력 RegWrite)과 main: 상수 → control → 선 → 출력 핀, 그리고 데이터 선 하나. */
    static Wire[] build(LogisimFile f) {
        Circuit control = new Circuit("control");
        f.addCircuit(control);
        CircuitBuilder cb = new CircuitBuilder(f, control);
        Component in = cb.add("Wiring", "Pin", 100, 100, "tristate", "false", "label", "op");
        cb.tunnel(in, 0, "op");
        Component not = cb.add("Gates", "NOT Gate", 300, 100);
        cb.tunnel(not, 1, "op");
        cb.tunnel(not, 0, "RegWrite");
        cb.output("RegWrite", 1, 500, 100);
        cb.commit();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component inst = b.addSubcircuit(control, 400, 300);
        b.commit();
        b = new CircuitBuilder(f, f.getMainCircuit());
        Location out = inst.getEnd(1).getLocation();
        Location in0 = inst.getEnd(0).getLocation();
        b.add("Wiring", "Constant", in0.getX() - 60, in0.getY());
        b.wire(Location.create(in0.getX() - 60, in0.getY()), in0);
        Component pin = b.add("Wiring", "Pin", out.getX() + 100, out.getY(), "facing", "west", "output", "true",
                "label", "rw");
        b.wire(out, pin.getLocation());
        b.add("Wiring", "Constant", 100, 600, "width", "8", "value", "0x5");
        Component d = b.add("Wiring", "Pin", 300, 600, "facing", "west", "output", "true", "width", "8", "label",
                "data");
        b.wire(Location.create(100, 600), d.getLocation());
        b.commit();
        Wire ctl = null;
        Wire data = null;
        for (Wire w : f.getMainCircuit().getWires()) {
            if (w.contains(Location.create(200, 600))) {
                data = w;
            } else if (w.getEnd0().equals(out) || w.getEnd1().equals(out)) {
                ctl = w;
            }
        }
        return new Wire[] {ctl, data};
    }

    @Test
    void controlOutputsAreControlAndChosenGroupsAreSaved() throws Exception {
        File dir = tmp.toFile();
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), dir);
        Wire[] w = build(f);
        Circuit main = f.getMainCircuit();
        assertEquals(SignalGroups.Group.CONTROL, SignalGroups.groupOf(f, main, w[0]), "control unit output");
        assertNull(SignalGroups.groupOf(f, main, w[1]));
        Project proj = new Project(f);
        proj.doAction(SignalGroups.action(f, main, w[1], SignalGroups.Group.DATA));
        assertEquals(SignalGroups.Group.DATA, SignalGroups.groupOf(f, main, w[1]));
        proj.undoAction();
        assertNull(SignalGroups.groupOf(f, main, w[1]));
        proj.doAction(SignalGroups.action(f, main, w[1], SignalGroups.Group.DATA));

        File plain = new File(dir, "plain.circ");
        File ext = new File(dir, "ext.circ");
        LogisimFile g = CircuitBuilder.newFile(new Loader(null), dir);
        build(g);
        CircuitBuilder.save(g, plain);
        CircuitBuilder.save(f, ext);
        CircExtensions.afterSave(f, ext);
        String xml = new String(Files.readAllBytes(ext.toPath()), StandardCharsets.UTF_8);
        assertTrue(xml.contains("group") && xml.contains("data"), "saved in the extension namespace");
        assertEquals(Collections.<String>emptyList(), CircEquivalence.compare(plain, ext),
                "the circuit itself is the same for the original 2.7.1");
        LogisimFile again = new Loader(null).openLogisimFile(ext);
        CircExtensions.afterOpen(again, ext);
        Wire dataAgain = null;
        for (Wire x : again.getMainCircuit().getWires()) {
            if (x.contains(Location.create(200, 600))) {
                dataAgain = x;
            }
        }
        assertEquals(SignalGroups.Group.DATA, SignalGroups.groupOf(again, again.getMainCircuit(), dataAgain));

        // 선을 지우면(넷이 사라지면) 저장 전에 항목도 지운다
        CircuitMutation m = new CircuitMutation(main);
        m.remove(w[1]);
        m.execute();
        SignalGroups.PRUNER.prune(f, CircExtensions.of(f));
        assertFalse(CircExtensions.of(f).items(main.getName()).stream().anyMatch(i -> i.kind().equals("group")));
    }

    @Test
    void viewModeIsAnAppSetting() {
        boolean before = SignalGroups.showGroups();
        try {
            SignalGroups.setShowGroups(false);
            assertFalse(SignalGroups.showGroups(), "values by default");
            SignalGroups.setShowGroups(true);
            assertTrue(SignalGroups.showGroups());
        } finally {
            SignalGroups.setShowGroups(before);
        }
    }
}
