/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #78의 GUI 없는 부분: 회전, 값 해석, 핀 값 넣기(엔진의 edit.rotate·sim.pinValue). */
class ShortcutsTest {
    @TempDir
    Path tmp;

    @Test
    void rotationGoesClockwiseAndBack() {
        Direction d = Direction.EAST;
        for (Direction want : new Direction[] {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST}) {
            d = Shortcuts.next(d, true);
            assertEquals(want, d);
        }
        assertEquals(Direction.NORTH, Shortcuts.next(Direction.EAST, false));
    }

    @Test
    void rotatingChangesOnlyFacing() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component and = b.add("Gates", "AND Gate", 300, 300, "inputs", "3");
        Component pin = b.add("Wiring", "Pin", 100, 100, "facing", "north");
        b.commit();
        Circuit main = file.getMainCircuit();
        Shortcuts.rotation(main, Arrays.asList(and, pin), true).execute();
        for (Component c : main.getNonWires()) {
            Direction f = c.getAttributeSet().getValue(StdAttr.FACING);
            if (c.getFactory().getName().equals("AND Gate")) {
                assertEquals(Direction.SOUTH, f);
                assertEquals("3", c.getAttributeSet().getValue(c.getAttributeSet().getAttribute("inputs")).toString());
            } else {
                assertEquals(Direction.EAST, f);
            }
        }
    }

    @Test
    void valuesAreReadInEveryUsualForm() {
        assertEquals(31L, (long) Shortcuts.parseValue("0x1F", 8));
        assertEquals(11L, (long) Shortcuts.parseValue("0b1011", 4));
        assertEquals(31L, (long) Shortcuts.parseValue(" 31 ", 8));
        assertEquals(13L, (long) Shortcuts.parseValue("-3", 4), "negative is two's complement");
        assertEquals(8L, (long) Shortcuts.parseValue("-8", 4));
        assertEquals(0xFFFF_FFFFL, (long) Shortcuts.parseValue("0xFFFF_FFFF", 32));
        assertNull(Shortcuts.parseValue("0x100", 8), "does not fit");
        assertNull(Shortcuts.parseValue("-9", 4));
        assertNull(Shortcuts.parseValue("abc", 8));
        assertNull(Shortcuts.parseValue("", 8));
    }

    /** 값 넣기: 시뮬레이션 상태의 핀 값만 바뀌고 저장 파일은 그대로다. */
    @Test
    void typedValueGoesToTheSimulationOnly() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component pin = b.add("Wiring", "Pin", 100, 100, "width", "8", "label", "in");
        b.commit();
        java.io.File before = tmp.resolve("before.circ").toFile();
        CircuitBuilder.save(file, before);
        com.cburch.logisim.proj.Project proj = new com.cburch.logisim.proj.Project(file);
        com.cburch.logisim.circuit.CircuitState state = proj.getCircuitState();
        Shortcuts.setPinValue(state, pin, Shortcuts.parseValue("0x2A", 8));
        assertEquals(0x2A, Shortcuts.pinValue(state, pin).toIntValue());
        Shortcuts.setPinValue(state, pin, Shortcuts.parseValue("-1", 8));
        assertEquals(0xFF, Shortcuts.pinValue(state, pin).toIntValue());
        java.io.File after = tmp.resolve("after.circ").toFile();
        CircuitBuilder.save(file, after);
        assertEquals(new String(java.nio.file.Files.readAllBytes(before.toPath()), "UTF-8"),
                new String(java.nio.file.Files.readAllBytes(after.toPath()), "UTF-8"), "nothing about the value is saved");
    }
}
