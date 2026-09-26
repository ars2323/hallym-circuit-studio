/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.appear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.draw.model.CanvasObject;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.appear.AppearancePort;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** P-04: 변마다 준 순서대로 포트가 놓이고, 적용은 되돌릴 수 있으며, 순서 바꾸기 도우미가 경계를 지킨다. */
class PortOrderTest {
    @TempDir
    Path tmp;

    static List<String> westTopToBottom(Circuit c) {
        List<AppearancePort> ports = new ArrayList<>();
        int left = Integer.MAX_VALUE;
        for (CanvasObject o : c.getAppearance().getObjectsFromBottom()) {
            if (o instanceof AppearancePort) {
                ports.add((AppearancePort) o);
                left = Math.min(left, ((AppearancePort) o).getLocation().getX());
            }
        }
        final int l = left;
        List<AppearancePort> west = new ArrayList<>();
        for (AppearancePort p : ports) {
            if (p.getLocation().getX() == l) {
                west.add(p);
            }
        }
        west.sort((a, b) -> a.getLocation().getY() - b.getLocation().getY());
        List<String> names = new ArrayList<>();
        for (AppearancePort p : west) {
            names.add(AutoAppearance.portName(p.getPin()));
        }
        return names;
    }

    @Test
    void chosenOrderBecomesTheShapeAndUndoRestores() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit c = new AutoAppearanceTest().adder(file);
        Map<Direction, List<Instance>> sides = AutoAppearance.sides(c);
        List<Instance> west = sides.get(Direction.WEST);
        assertEquals(3, west.size());
        List<String> before = new ArrayList<>();
        for (Instance p : west) {
            before.add(AutoAppearance.portName(p));
        }
        assertEquals(Arrays.asList("A", "Bvalue", "Cin"), before);
        sides.put(Direction.WEST, PortOrderDialog.moved(west, 2, 0)); // Cin을 맨 위로
        Project proj = new Project(file);
        assertTrue(PortOrderDialog.apply(proj, c, sides, null));
        assertEquals(Arrays.asList("Cin", "A", "Bvalue"), westTopToBottom(c));
        // 포트가 격자 위, 다른 변은 그대로(동쪽 Sum이 기준점)
        Map<Location, Instance> offs = c.getAppearance().getPortOffsets(Direction.EAST);
        for (Location l : offs.keySet()) {
            assertEquals(0, l.getX() % 10);
            assertEquals(0, l.getY() % 10);
        }
        proj.undoAction();
        assertTrue(c.getAppearance().isDefaultAppearance(), "undo goes back to the default appearance");
    }

    @Test
    void movedKeepsBounds() {
        List<String> l = Arrays.asList("a", "b", "c", "d");
        assertEquals(Arrays.asList("b", "c", "a", "d"), PortOrderDialog.moved(l, 0, 2));
        assertEquals(Arrays.asList("d", "a", "b", "c"), PortOrderDialog.moved(l, 3, 0));
        assertEquals(l, PortOrderDialog.moved(l, 1, 1));
        assertEquals(l, PortOrderDialog.moved(l, -1, 2));
        assertEquals(l, PortOrderDialog.moved(l, 1, 9));
    }
}
