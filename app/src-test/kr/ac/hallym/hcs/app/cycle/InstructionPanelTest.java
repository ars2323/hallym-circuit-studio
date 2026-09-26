/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** C-07 Instruction 탭의 칸: 필드마다 비트 범위, 비트, 값(레지스터 필드는 이름도), 필드 색. */
class InstructionPanelTest {
    @Test
    void rTypeCells() {
        int word = 0x00221824; // and $v1, $at, $v0
        List<InstructionPanel.Cell> cells = InstructionPanel.cells(word);
        assertEquals("R-type", InstructionPanel.formatName(word));
        assertEquals(6, cells.size());
        assertEquals("op", cells.get(0).name);
        assertEquals("31–26", cells.get(0).range);
        assertEquals("000000", cells.get(0).bits);
        assertEquals("1 $at", cells.get(1).value);
        assertEquals("2 $v0", cells.get(2).value);
        assertEquals("3 $v1", cells.get(3).value);
        assertEquals("00011", cells.get(3).bits);
        assertEquals("36", cells.get(5).value);
        assertEquals(FieldPaths.color("rs"), cells.get(1).color);
    }

    @Test
    void iAndJTypeCells() {
        List<InstructionPanel.Cell> lw = InstructionPanel.cells(0x8e0afffc); // lw $t2, -4($s0)
        assertEquals("I-type", InstructionPanel.formatName(0x8e0afffc));
        assertEquals("imm", lw.get(3).name);
        assertEquals("-4 (0xfffc)", lw.get(3).value);
        List<InstructionPanel.Cell> j = InstructionPanel.cells(0x08100004);
        assertEquals("J-type", InstructionPanel.formatName(0x08100004));
        assertEquals("addr", j.get(1).name);
        assertEquals("0x100004", j.get(1).value);
        assertEquals("25–0", j.get(1).range);
    }
}
