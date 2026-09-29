/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.cburch.logisim.data.Value;

/**
 * Instruction 패널의 필드(N-14, D-144): 형식 규칙과 필드 자리는 MIPS32 명세, 이름은 Hallym MIPS Inspector와 같다. 필드는
 * 워드의 32비트를 빈틈없이 덮는다.
 */
class InstructionFieldsTest {
    static List<String> names(int word) {
        List<String> out = new ArrayList<>();
        for (InstructionFields.Field f : InstructionFields.fields(word)) {
            out.add(f.name);
        }
        return out;
    }

    static void covers(int word) {
        int bit = 31;
        StringBuilder bits = new StringBuilder();
        for (InstructionFields.Field f : InstructionFields.fields(word)) {
            assertEquals(bit, f.hi, Integer.toHexString(word));
            bit = f.lo - 1;
            bits.append(f.bits());
        }
        assertEquals(-1, bit);
        String want = String.format("%32s", Integer.toBinaryString(word)).replace(' ', '0');
        assertEquals(want, bits.toString(), "the fields' bits are the word");
    }

    /** 이름:높은 비트-낮은 비트, 높은 비트부터. */
    static String ranges(int word) {
        StringBuilder sb = new StringBuilder();
        for (InstructionFields.Field f : InstructionFields.fields(word)) {
            sb.append(sb.length() == 0 ? "" : " ").append(f.name).append(':').append(f.hi).append('-').append(f.lo);
        }
        return sb.toString();
    }

    /**
     * 필드 경계가 Hallym MIPS v2.6.0 Inspector와 같다(D-167, docs/field-colors.md): 그쪽
     * electron/src/core/decoder.ts 231~268행 fieldsOf와 273~288행 formatOf를 형식마다 옮겨 적었다. 색은 이름으로
     * 고르므로(electron tests/unit/field-colors.test.ts) 이름과 경계가 같으면 같은 명령이 두 프로그램에서 같은 칸에 같은
     * 색이다.
     */
    @Test
    void fieldBoundariesAreHallymMips260() {
        assertEquals("opcode:31-26 rs:25-21 rt:20-16 rd:15-11 shamt:10-6 funct:5-0", ranges(0x01095020)); // R: add
        assertEquals("opcode:31-26 rs:25-21 rt:20-16 rd:15-11 shamt:10-6 funct:5-0", ranges(0x70851002)); // R: mul (0x1c)
        assertEquals("opcode:31-26 rs:25-21 rt:20-16 immediate:15-0", ranges(0x8e090000)); // I: lw
        assertEquals("opcode:31-26 rs:25-21 rt:20-16 immediate:15-0", ranges(0x1109fffa)); // I: beq
        assertEquals("opcode:31-26 target:25-0", ranges(0x0c100016)); // J: jal
        assertEquals("opcode:31-26 target:25-0", ranges(0x08100000)); // J: j
        assertEquals("opcode:31-26 rs:25-21 rt:20-16 rd:15-11 0:10-3 sel:2-0", ranges(0x40046000)); // CP0: mfc0
        assertEquals("opcode:31-26 CO:25-25 code:24-6 funct:5-0", ranges(0x42000018)); // CP0 CO: eret
        assertEquals("opcode:31-26 fmt:25-21 ft:20-16 fs:15-11 fd:10-6 funct:5-0", ranges(0x46020800)); // FR: add.s
        assertEquals("opcode:31-26 fmt:25-21 cc:20-18 nd:17-17 tf:16-16 immediate:15-0", ranges(0x45010003)); // FI: bc1t
    }

    @Test
    void formatsAndFieldNamesAreHallymMips() {
        int add = 0x01095020; // add $10, $8, $9
        int lw = 0x8e090000; // lw $9, 0($16)
        int jal = 0x0c100016; // jal 0x00400058
        int mul = 0x70851002; // mul $2, $4, $5
        int addS = 0x46020800; // add.s $f0, $f1, $f2
        int bc1t = 0x45010003; // bc1t 12
        int mfc0 = 0x40046000; // mfc0 $4, $12
        int eret = 0x42000018; // eret
        assertEquals("R", InstructionFields.format(add));
        assertEquals("R", InstructionFields.format(mul));
        assertEquals("I", InstructionFields.format(lw));
        assertEquals("J", InstructionFields.format(jal));
        assertEquals("FR", InstructionFields.format(addS));
        assertEquals("FI", InstructionFields.format(bc1t));
        assertEquals("CP0", InstructionFields.format(mfc0));
        assertEquals(List.of("opcode", "rs", "rt", "rd", "shamt", "funct"), names(add));
        assertEquals(List.of("opcode", "rs", "rt", "immediate"), names(lw));
        assertEquals(List.of("opcode", "target"), names(jal));
        assertEquals(List.of("opcode", "fmt", "ft", "fs", "fd", "funct"), names(addS));
        assertEquals(List.of("opcode", "fmt", "cc", "nd", "tf", "immediate"), names(bc1t));
        assertEquals(List.of("opcode", "rs", "rt", "rd", "0", "sel"), names(mfc0));
        assertEquals(List.of("opcode", "CO", "code", "funct"), names(eret));
        for (int w : new int[] {add, lw, jal, mul, addS, bc1t, mfc0, eret, 0, -1, 0x1000ffff}) {
            covers(w);
        }
    }

    @Test
    void meaningsAreFactsFromTheWord() {
        Map<Integer, String> names = new TreeMap<>();
        names.put(0x00400038, "next");
        names.put(0x00400058, "fact");
        int lw = 0x8e090000;
        List<InstructionFields.Field> f = InstructionFields.fields(lw);
        assertEquals("lw", InstructionFields.meaning(f.get(0), lw, 0x00400038, names));
        assertEquals("$s0", InstructionFields.meaning(f.get(1), lw, 0x00400038, names));
        assertEquals("$t1", InstructionFields.meaning(f.get(2), lw, 0x00400038, names));
        assertEquals("0x0000", InstructionFields.meaning(f.get(3), lw, 0x00400038, names));
        // 분기: 목적지 = 분기 주소 + imm×4(D-010·D-127), 라벨
        int bne = 0x1620fffa; // bne $17, $0, -24
        f = InstructionFields.fields(bne);
        assertEquals("-6", InstructionFields.value(f.get(3)));
        assertEquals("0x00400038 [next]", InstructionFields.meaning(f.get(3), bne, 0x00400050, names));
        // 점프: (PC의 위 4비트) | target×4
        int jal = 0x0c100016;
        f = InstructionFields.fields(jal);
        assertEquals("0x00400058 [fact]", InstructionFields.meaning(f.get(1), jal, 0x00400030, names));
        assertEquals("jal", InstructionFields.meaning(f.get(0), jal, 0x00400030, names));
        // R형: opcode는 R-type, funct는 명령어 이름, shamt는 자리 수
        int sll = 0x00084080; // sll $8, $8, 2
        f = InstructionFields.fields(sll);
        assertEquals("R-type", InstructionFields.meaning(f.get(0), sll, 0, null));
        assertEquals("sll", InstructionFields.meaning(f.get(5), sll, 0, null));
        assertEquals("2", InstructionFields.meaning(f.get(4), sll, 0, null));
        // 부동소수점 레지스터
        int addS = 0x46020800;
        f = InstructionFields.fields(addS);
        assertEquals("single", InstructionFields.meaning(f.get(1), addS, 0, null));
        assertEquals("$f2", InstructionFields.meaning(f.get(2), addS, 0, null));
        assertTrue(InstructionFields.isBranch("beq"));
        assertTrue(InstructionFields.isBranch("bc1t"));
        assertFalse(InstructionFields.isBranch("jal"));
        assertFalse(InstructionFields.isBranch(null));
    }

    @Test
    void pcTextIsHexOrASymbol() {
        assertEquals(0x00400034L, (long) RecordSession.parsePc("0x00400034", null));
        assertEquals(0x00400034L, (long) RecordSession.parsePc(" 400034 ", null));
        assertNull(RecordSession.parsePc("fact", null));
        assertNull(RecordSession.parsePc("", null));
        assertNull(RecordSession.parsePc("0x", null));
        assertNull(RecordSession.parsePc("0x1234567890", null));
        assertNull(RecordSession.parsePc(null, null));
    }

    @Test
    void valueTextIsTheProtocolsLetters() {
        assertEquals("0101", ValueText.of(Value.createKnown(com.cburch.logisim.data.BitWidth.create(4), 5), 4));
        assertEquals("x", ValueText.of(Value.UNKNOWN, 1));
        assertEquals("E", ValueText.of(Value.ERROR, 1));
        assertNull(ValueText.of(null, 8));
        assertEquals("0x0000002a", ValueText.hex(Value.createKnown(com.cburch.logisim.data.BitWidth.create(32), 42)));
        assertNull(ValueText.hex(Value.UNKNOWN));
    }
}
