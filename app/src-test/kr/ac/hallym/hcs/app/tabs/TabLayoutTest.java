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
}
