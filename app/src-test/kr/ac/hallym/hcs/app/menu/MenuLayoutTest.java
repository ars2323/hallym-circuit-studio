/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** 검토 반영 1: 우클릭 메뉴 맨 위 한 줄이 대상을 요약한다(엔진의 menu.facts). */
class MenuLayoutTest {
    @TempDir
    Path tmp;

    @Test
    void summariesNameTheTarget() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit c = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, c);
        Component and = b.add("Gates", "AND Gate", 300, 200, "inputs", "2");
        Component pin = b.add("Wiring", "Pin", 100, 400, "width", "32", "label", "aluResult");
        b.wire(Location.create(100, 400), Location.create(200, 400));
        Component reg = b.add("Memory", "Register", 500, 300, "width", "8", "label", "PC");
        b.commit();
        Wire w = null;
        for (Wire x : c.getWires()) {
            w = x;
        }
        String bits1 = Messages.get("menu.sum.bits", 1);
        Location in1 = and.getEnds().get(2).getLocation();
        assertEquals("AND #1 · " + Messages.get("menu.sum.in", "in1") + " · " + bits1,
                MenuLayout.summary(c, and, in1, 1));
        assertEquals("AND #1 · " + Messages.get("menu.sum.inputs", 2) + " · " + bits1,
                MenuLayout.summary(c, and, Location.create(280, 200), 1));
        assertEquals(Messages.get("menu.sum.net", "aluResult") + " · " + Messages.get("menu.sum.bits", 32),
                MenuLayout.summary(c, w, Location.create(150, 400), 1));
        String r = MenuLayout.summary(c, reg, Location.create(480, 300), 1);
        assertTrue(r.startsWith("PC(") && r.endsWith(Messages.get("menu.sum.bits", 8)), r);
        assertEquals(Messages.get("menu.sum.empty", "main"), MenuLayout.summary(c, null, Location.create(0, 0), 0));
        assertEquals(Messages.get("menu.sum.many", 3), MenuLayout.summary(c, and, Location.create(280, 200), 3));
        assertTrue(pin != null);
    }
}
