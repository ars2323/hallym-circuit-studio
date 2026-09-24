/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.edit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.StringUtil;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** 다시 실행: 원조 되돌리기 기록 위에서 되돌린 동작을 다시 적용하고, 새 편집이면 비운다. */
class RedoStackTest {
    @TempDir
    Path tmp;

    private static Action add(LogisimFile file, Circuit c, String gate, int x, int y) {
        ComponentFactory f = CircuitEdits.builtin(file, "Gates", gate);
        CircuitMutation m = new CircuitMutation(c);
        m.add(f.createComponent(Location.create(x, y), f.createAttributeSet()));
        return m.toAction(StringUtil.constantGetter("add " + gate));
    }

    private static int count(Circuit c, String name) {
        int n = 0;
        for (Component comp : c.getNonWires()) {
            if (comp.getFactory().getName().equals(name)) {
                n++;
            }
        }
        return n;
    }

    @Test
    void redoReappliesInOrderAndNewEditsClearIt() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit c = file.getMainCircuit();
        Project proj = new Project(file);
        RedoStack redo = RedoStack.of(proj);
        int[] changes = {0};
        redo.addListener(() -> changes[0]++);
        assertFalse(redo.canRedo());

        proj.doAction(add(file, c, "AND Gate", 200, 200));
        proj.doAction(add(file, c, "OR Gate", 300, 300));
        proj.undoAction();
        proj.undoAction();
        assertEquals(0, count(c, "AND Gate") + count(c, "OR Gate"));
        assertTrue(redo.canRedo());
        assertEquals("add AND Gate", redo.nextName());

        redo.redo();
        assertEquals(1, count(c, "AND Gate"));
        assertEquals(0, count(c, "OR Gate"));
        assertEquals("add OR Gate", redo.nextName());
        redo.redo();
        assertEquals(1, count(c, "OR Gate"));
        assertFalse(redo.canRedo());
        assertTrue(changes[0] > 0);

        // 다시 실행한 것도 원조 되돌리기로 되돌리고 다시 다시 실행할 수 있다
        assertEquals("add OR Gate", proj.getLastAction().getName());
        proj.undoAction();
        assertEquals(0, count(c, "OR Gate"));
        redo.redo();
        assertEquals(1, count(c, "OR Gate"));

        // 되돌린 뒤 새로 고치면 다시 실행 기록을 비운다
        proj.undoAction();
        assertTrue(redo.canRedo());
        proj.doAction(add(file, c, "XOR Gate", 400, 400));
        assertFalse(redo.canRedo());
        assertNull(redo.nextName());
        redo.redo(); // 아무 일도 없다
        assertEquals(0, count(c, "OR Gate"));
    }

    @Test
    void selectionOnlyActionsKeepTheRedoHistory() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit c = file.getMainCircuit();
        Project proj = new Project(file);
        RedoStack redo = RedoStack.of(proj);
        proj.doAction(add(file, c, "AND Gate", 200, 200));
        proj.undoAction();
        proj.doAction(new Action() {
            @Override
            public boolean isModification() {
                return false; // 원조 복사(Copy)처럼 회로를 바꾸지 않는 동작
            }

            @Override
            public String getName() {
                return "copy";
            }

            @Override
            public void doIt(Project p) {
            }

            @Override
            public void undo(Project p) {
            }
        });
        assertTrue(redo.canRedo());
    }

    /** 속성 바꾸기를 되돌렸다 다시 실행한 파일은 한 번 바꾼 파일과 바이트까지 같다. */
    @Test
    void redoneAttributeEditSavesLikeTheOriginalEdit() throws Exception {
        File once = tmp.resolve("once.circ").toFile();
        File redone = tmp.resolve("redone.circ").toFile();
        for (File dest : new File[] {once, redone}) {
            File dir = tmp.resolve(dest.getName() + "-d").toFile();
            dir.mkdirs();
            LogisimFile file = CircuitBuilder.newFile(new Loader(null), dir);
            Circuit c = file.getMainCircuit();
            CircuitBuilder b = new CircuitBuilder(file, c);
            Component reg = b.add("Memory", "Register", 300, 200, "label", "PC");
            b.commit();
            Project proj = new Project(file);
            RedoStack redo = RedoStack.of(proj); // 창이 열릴 때처럼 편집 전에 만든다
            Action set = CircuitEdits.setAttribute(c, Collections.singletonList(reg), "width", "16")
                    .toAction(StringUtil.constantGetter("width"));
            proj.doAction(set);
            if (dest == redone) {
                proj.undoAction();
                redo.redo();
            }
            CircuitBuilder.save(file, dest);
        }
        assertArrayEquals(Files.readAllBytes(once.toPath()), Files.readAllBytes(redone.toPath()));
    }
}
