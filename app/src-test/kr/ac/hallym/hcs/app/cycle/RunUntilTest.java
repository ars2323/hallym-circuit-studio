/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.cburch.logisim.circuit.Circuit;

/**
 * C-04 Run Until의 PC 글 해석. 조건별로 실제로 돌려 멈추는 것은 엔진의 Run Until(RecordTest.runUntil*)이 본다(v1 Swing
 * 실행기는 N-27에서 지웠다, D-163).
 */
class RunUntilTest {
    @Test
    void pcTextIsHexOrALabel() throws Exception {
        java.util.Map<Integer, Integer> lines = new java.util.HashMap<>();
        lines.put(0x00400000, 1);
        java.util.Map<Integer, String> labels = new java.util.HashMap<>();
        labels.put(0x00400034, "fact");
        ProgramSource src = new ProgramSource(null, lines, labels, java.util.Collections.singletonList("main:"));
        assertEquals(Integer.valueOf(0x00400034), RunUntil.parsePc("0x00400034", src));
        assertEquals(Integer.valueOf(0x00400034), RunUntil.parsePc(" 400034 ", src));
        assertEquals(Integer.valueOf(0x00400034), RunUntil.parsePc("fact", src));
        assertEquals(null, RunUntil.parsePc("nowhere", src));
        assertEquals(null, RunUntil.parsePc("", src));
    }
}
