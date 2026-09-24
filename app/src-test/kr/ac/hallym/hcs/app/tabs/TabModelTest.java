/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** #68: 파일 탭 모델(열기·닫기·중복·더티·순서·복원 목록). */
class TabModelTest {
    @TempDir
    Path tmp;

    static List<String> keys(TabModel<String> m) {
        List<String> ret = new ArrayList<>();
        for (TabModel.Tab<String> t : m.tabs()) {
            ret.add(t.key());
        }
        return ret;
    }

    @Test
    void newTabGoesRightOfActiveAndBecomesActive() {
        TabModel<String> m = new TabModel<>();
        m.add("a", null, "a");
        m.add("b", null, "b");
        m.activate("a");
        m.add("c", null, "c");
        assertEquals(Arrays.asList("a", "c", "b"), keys(m));
        assertEquals("c", m.active());
    }

    @Test
    void closingPicksTheRightNeighbourThenLeft() {
        TabModel<String> m = new TabModel<>();
        for (String k : new String[] {"a", "b", "c"}) {
            m.add(k, null, k);
        }
        m.activate("b");
        m.remove("b");
        assertEquals("c", m.active());
        m.remove("c");
        assertEquals("a", m.active());
        m.remove("a");
        assertNull(m.active());
        assertEquals(0, m.size());
    }

    @Test
    void sameFileIsFoundThroughDifferentPaths() throws Exception {
        TabModel<String> m = new TabModel<>();
        Path dir = Files.createDirectories(tmp.resolve("d"));
        File f = Files.createFile(dir.resolve("cpu.circ")).toFile();
        m.add("p", f, "cpu");
        assertEquals("p", m.find(new File(dir.toFile(), "../d/cpu.circ")));
        assertNull(m.find(new File(dir.toFile(), "other.circ")));
        assertNull(m.find(null));
    }

    @Test
    void dirtyAndRenameNotifyOnlyOnChange() {
        TabModel<String> m = new TabModel<>();
        int[] events = {0};
        m.addListener(() -> events[0]++);
        m.add("a", null, "Untitled");
        int after = events[0];
        m.update("a", null, "Untitled", false);
        assertEquals(after, events[0], "no change, no event");
        m.update("a", null, "Untitled", true);
        assertTrue(m.tabs().get(0).dirty());
        File f = new File(tmp.toFile(), "x.circ");
        m.update("a", f, "x", false);
        assertEquals(after + 2, events[0]);
        assertEquals("x", m.tabs().get(0).title());
        assertFalse(m.tabs().get(0).dirty());
    }

    @Test
    void restoreListSkipsUnsavedAndKeepsOrderAndActive() {
        TabModel<String> m = new TabModel<>();
        File a = new File(tmp.toFile(), "a.circ");
        File c = new File(tmp.toFile(), "c.circ");
        m.add("a", a, "a");
        m.add("new", null, "Untitled");
        m.add("c", c, "c");
        assertEquals(Arrays.asList(a.getAbsolutePath(), c.getAbsolutePath()), m.restoreList());
        assertEquals(1, m.restoreActive());
        m.activate("new");
        assertEquals(-1, m.restoreActive());
        m.move(2, 0);
        assertEquals(Arrays.asList("c", "a", "new"), keys(m));
        m.move(0, 9);
        assertEquals(Arrays.asList("c", "a", "new"), keys(m), "out of range is ignored");
        assertEquals(Collections.emptyList(), new TabModel<String>().restoreList());
    }
}
