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

import java.awt.Rectangle;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** P-06 모델: 분리·되돌리기, 닫으면 분리 표시도 사라짐, 복원용 목록은 저장된 파일만, 창 자리 글 왕복. */
class TabLayoutTest {
    @Test
    void detachAndAttachFireAndSurviveOnlyWhileOpen() {
        TabModel<String> m = new TabModel<>();
        m.add("a", new File("/tmp/a.circ"), "a");
        m.add("b", null, "b");
        m.add("c", new File("/tmp/c.circ"), "c");
        AtomicInteger fired = new AtomicInteger();
        m.addListener(fired::incrementAndGet);
        m.detach("b");
        m.detach("c");
        assertTrue(m.isDetached("b") && m.isDetached("c") && !m.isDetached("a"));
        assertEquals(2, fired.get());
        m.detach("c"); // 이미 분리: 알리지 않는다
        assertEquals(2, fired.get());
        assertEquals(Collections.singletonList(new File("/tmp/c.circ").getAbsolutePath()), m.detachedFiles(),
                "unsaved tabs are not restored");
        m.attach("c");
        assertFalse(m.isDetached("c"));
        assertEquals(3, fired.get());
        m.detach("zzz"); // 없는 탭
        assertEquals(3, fired.get());
        m.remove("b");
        assertFalse(m.isDetached("b"));
        assertEquals(Arrays.asList("a", "c"), Arrays.asList(m.tabs().get(0).key(), m.tabs().get(1).key()));
    }

    @Test
    void boundsRoundTrip() {
        assertEquals(new Rectangle(10, 20, 800, 600), FileTabs.parseBounds("10,20,800,600"));
        assertNull(FileTabs.parseBounds("garbage"));
        assertNull(FileTabs.parseBounds(""));
    }

    /** V-05: 같은 제목의 탭만, 서로 다른 가장 가까운 상위 폴더 이름으로 가른다. */
    @Test
    void sameTitlesGetTheShortestDistinguishingFolder() {
        TabModel<String> m = new TabModel<>();
        m.add("a", new File("/home/s/circ/demo-datapath.circ"), "demo-datapath");
        m.add("b", new File("/home/s/hw3/demo-datapath.circ"), "demo-datapath");
        m.add("c", new File("/home/s/circ/gates.circ"), "gates");
        m.add("u", null, "demo-datapath"); // 저장한 적 없는 탭은 빠진다
        java.util.Map<String, String> d = TabModel.distinguishers(m.tabs());
        assertEquals("circ", d.get("a"));
        assertEquals("hw3", d.get("b"));
        assertNull(d.get("c"), "a unique title gets nothing");
        assertNull(d.get("u"));
        // 바로 위 폴더 이름이 같으면 한 단계 더 올라간다
        TabModel<String> n = new TabModel<>();
        n.add("x", new File("/home/kim/circ/demo.circ"), "demo");
        n.add("y", new File("/home/lee/circ/demo.circ"), "demo");
        java.util.Map<String, String> e = TabModel.distinguishers(n.tabs());
        assertEquals("kim/circ", e.get("x"));
        assertEquals("lee/circ", e.get("y"));
        // 셋: 둘은 바로 위 폴더가 같고 하나는 다르다 → 다른 것만 짧다
        TabModel<String> o = new TabModel<>();
        o.add("p", new File("/w/tests/circ/demo.circ"), "demo");
        o.add("q", new File("/t/x/circ/demo.circ"), "demo");
        o.add("r", new File("/t/x/hw3/demo.circ"), "demo");
        java.util.Map<String, String> f = TabModel.distinguishers(o.tabs());
        assertEquals("tests/circ", f.get("p"));
        assertEquals("x/circ", f.get("q"));
        assertEquals("hw3", f.get("r"));
    }
}
