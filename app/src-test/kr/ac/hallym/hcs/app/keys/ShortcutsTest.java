/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #78: 단축키 표, 회전, 값 해석, 포트 툴팁. */
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

    @Test
    void portTipNamesThePortAndWidth() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component reg = b.add("Memory", "Register", 400, 300, "width", "8", "label", "PC");
        b.commit();
        Circuit main = file.getMainCircuit();
        Location q = reg.getEnds().get(0).getLocation();
        String tip = Shortcuts.portTip(main, q.translate(2, 1));
        assertTrue(tip.startsWith("PC.Q · 8"), tip);
        assertNull(Shortcuts.portTip(main, Location.create(10, 10)));
    }

    @Test
    void everyShortcutHasKoreanAndEnglishText() throws Exception {
        Method get = kr.ac.hallym.hcs.app.Messages.class.getDeclaredMethod("get", Locale.class, String.class,
                Object[].class);
        get.setAccessible(true);
        for (String key : Shortcuts.messageKeys()) {
            String en = (String) get.invoke(null, Locale.ENGLISH, key, new Object[0]);
            String ko = (String) get.invoke(null, Locale.KOREAN, key, new Object[0]);
            assertNotEquals(key, en);
            assertNotEquals(key, ko);
        }
        assertTrue(Shortcuts.TABLE.containsKey("R / Shift+R"));
    }
}
