/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** E-05: 되돌릴 동작(오래된 것부터)·지금·다시 실행할 동작, 줄을 누르면 그 상태까지 되돌리거나 다시 실행. */
class UndoHistoryTest {
    @TempDir
    Path tmp;

    static void addWire(Project proj, Circuit c, int y, String name) {
        CircuitMutation m = new CircuitMutation(c);
        m.add(Wire.create(Location.create(100, y), Location.create(200, y)));
        proj.doAction(m.toAction(() -> name));
    }

    static String kinds(List<UndoHistory.Row> rows) {
        StringBuilder sb = new StringBuilder();
        for (UndoHistory.Row r : rows) {
            sb.append(r.kind.name().charAt(0));
        }
        return sb.toString();
    }

    @Test
    void listsAndMovesThroughTheHistory() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Project proj = new Project(f);
        Circuit main = f.getMainCircuit();
        for (int i = 0; i < 3; i++) {
            addWire(proj, main, 100 + 20 * i, "wire " + i);
        }
        List<UndoHistory.Row> rows = UndoHistory.rows(proj);
        assertEquals("SUUUN", kinds(rows));
        assertEquals("wire 0", rows.get(1).text);
        assertEquals(-3, rows.get(0).moves, "start: undo everything");
        assertEquals(-2, rows.get(1).moves, "keep wire 0");
        assertEquals(0, rows.get(3).moves, "the last action is the present");
        // wire 0만 남기고 되돌린다
        UndoHistory.go(proj, rows.get(1));
        assertEquals(1, main.getWires().size());
        rows = UndoHistory.rows(proj);
        assertEquals("SUNRR", kinds(rows));
        assertEquals("wire 1", rows.get(3).text, "next redo first");
        // 끝까지 다시 실행
        UndoHistory.go(proj, rows.get(4));
        assertEquals(3, main.getWires().size());
        assertEquals("SUUUN", kinds(UndoHistory.rows(proj)));
    }
}
