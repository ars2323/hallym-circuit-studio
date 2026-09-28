/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

/** 불러오기 보고서(N-16, D-147): 이미지가 있고 문제도 고를 것도 없을 때만 넣을 수 있다. */
class LoadReportTest {
    @Test
    void okOnlyWithAnImageAndNoProblemOrChoice() {
        LoadReport r = new LoadReport(new File("p.hmx"));
        assertFalse(r.ok(), "no image");
        r.image = new ExecutableImage.Builder().entry(0x00400000L).text(0x00400000L, 0).build();
        assertTrue(r.ok());
        r.choice = new LoadReport.Choice(ExecutableImage.Kind.TEXT, ".text 0x00400000");
        assertFalse(r.ok(), "a choice is left");
        r.choice = null;
        r.problems.add(new LoadReport.Problem(3, Msg.of("Line 3: x", "3번째 줄: x")));
        assertFalse(r.ok(), "a problem");
    }
}
