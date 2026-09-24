/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static kr.ac.hallym.hcs.mips.OriginalLogisim.hex;
import static kr.ac.hallym.hcs.mips.OriginalLogisim.value;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * Instruction Memory, Data Memory, Stack이 원조 Logisim 2.7.1 안에서 PLAN.md 6.2대로 동작한다.
 * 기대값은 명세에서 직접 계산한다.
 */
class MemoryComponentsTest {
    @TempDir
    Path tmp;

    /** 행을 "값,값,…"(16진수 8자리, 정의 안 됨은 x)으로. */
    static List<String> hexRows(List<String[]> rows) {
        List<String> out = new ArrayList<>();
        for (String[] r : rows) {
            List<String> cells = new ArrayList<>();
            for (String c : r) {
                cells.add(hex(value(c)));
            }
            out.add(String.join(",", cells));
        }
        return out;
    }

    static String row(Object... cells) {
        List<String> out = new ArrayList<>();
        for (Object c : cells) {
            out.add(c == null ? "x" : String.format("%08x", ((Number) c).longValue() & 0xffffffffL));
        }
        return String.join(",", out);
    }

    static void clock(CircuitBuilder b) {
        Component clock = b.add("Wiring", "Clock", 80, 40);
        b.tunnel(clock, 0, "clk");
        b.constant("zero", 1, 0, 80, 80);
        b.constant("one", 1, 1, 80, 120);
    }

    /** 카운터(0..max)와 그 비트를 나눈 터널. fields는 {라벨, 비트 수}를 아래 비트부터. */
    static void counter(CircuitBuilder b, int width, String... fields) {
        Component c = b.add("Memory", "Counter", 300, 60,
                "width", Integer.toString(width), "max", "0x" + Integer.toHexString((1 << width) - 1));
        b.tunnel(c, 0, "n");
        b.tunnel(c, 2, "clk");
        b.tunnel(c, 3, "zero");
        b.tunnel(c, 4, "zero");
        b.tunnel(c, 5, "one");
        b.tunnel(c, 6, "halt");
        List<String> attrs = new ArrayList<>(List.of("incoming", Integer.toString(width),
                "fanout", Integer.toString(fields.length / 2)));
        int bit = 0;
        for (int f = 0; f < fields.length; f += 2) {
            for (int i = 0; i < Integer.parseInt(fields[f + 1]); i += 1) {
                attrs.add("bit" + bit++);
                attrs.add(Integer.toString(f / 2));
            }
        }
        Component split = b.add("Wiring", "Splitter", 400, 60, attrs.toArray(new String[0]));
        b.tunnel(split, 0, "n");
        for (int f = 0; f < fields.length; f += 2) {
            b.tunnel(split, 1 + f / 2, fields[f]);
        }
    }

    /** base + index×4 를 32비트 터널 addr로 만든다. index는 bits비트 터널. */
    static void wordAddress(CircuitBuilder b, String base, String index, int bits, int y) {
        List<String> attrs = new ArrayList<>(List.of("incoming", "32", "fanout", "2"));
        for (int i = 0; i < 32; i += 1) {
            attrs.add("bit" + i);
            attrs.add(i >= 2 && i < 2 + bits ? "1" : "0");
        }
        Component split = b.add("Wiring", "Splitter", 600, y, attrs.toArray(new String[0]));
        b.tunnel(split, 0, "offset");
        b.tunnel(split, 1, "offzero");
        b.tunnel(split, 2, index);
        b.constant("offzero", 32 - bits, 0, 500, y + 60);
        Component add = b.add("Arithmetic", "Adder", 800, y, "width", "32");
        b.tunnel(add, 0, base);
        b.tunnel(add, 1, "offset");
        b.tunnel(add, 2, "addr");
    }

    @Test
    void instructionMemoryReadsContentsAndStaysOffOutsideItsRegion() throws Exception {
        OriginalLogisim o = new OriginalLogisim(tmp);
        CircuitBuilder b = o.b;
        clock(b);
        Component pc = b.add("Memory", "Register", 300, 200, "width", "32");
        b.tunnel(pc, 0, "pc");
        b.tunnel(pc, 1, "next");
        b.tunnel(pc, 2, "clk");
        b.tunnel(pc, 3, "zero");
        b.tunnel(pc, 4, "one");
        Component inc = b.add("Arithmetic", "Adder", 500, 200, "width", "32");
        b.tunnel(inc, 0, "pc");
        b.tunnel(inc, 1, "four");
        b.tunnel(inc, 2, "next");
        b.constant("four", 32, 4, 300, 320);
        Component add = b.add("Arithmetic", "Adder", 500, 400, "width", "32");
        b.tunnel(add, 0, "pc");
        b.tunnel(add, 1, "text");
        b.tunnel(add, 2, "addr");
        b.constant("text", 32, 0x00400000, 300, 440);
        Component cmp = b.add("Arithmetic", "Comparator", 500, 600, "width", "32", "mode", "unsigned");
        b.tunnel(cmp, 0, "pc");
        b.tunnel(cmp, 1, "limit");
        b.tunnel(cmp, 3, "halt");
        b.constant("limit", 32, 0x18, 300, 640);

        String program = "hcs-words 1\n00400000 11111111 22222222 33333333\n00400010 55555555\n";
        Component im = b.add(o.mips, "Instruction Memory", 1000, 300, "contents", program);
        b.tunnel(im, InstructionMemory.ADDR, "addr");
        b.tunnel(im, InstructionMemory.INSTR, "instr");
        Component small = b.add(o.mips, "Instruction Memory", 1000, 500, "contents", program, "size", "0x8");
        b.tunnel(small, InstructionMemory.ADDR, "addr");
        b.tunnel(small, InstructionMemory.INSTR, "instr2");

        b.output("pc", 32, 1400, 100);
        b.output("instr", 32, 1400, 180);
        b.output("instr2", 32, 1400, 260);
        b.output("halt", 1, 1400, 340);

        List<String> expected = List.of(
                row(0x00, 0x11111111, 0x11111111),
                row(0x04, 0x22222222, 0x22222222),
                row(0x08, 0x33333333, null), // 두 번째 메모리는 8바이트뿐
                row(0x0c, 0, null), // 쓰지 않은 워드는 0
                row(0x10, 0x55555555, null),
                row(0x14, 0, null),
                row(0x18, 0, null));
        assertEquals(expected, hexRows(o.run("imem")));
    }

    /**
     * Data Memory와 Stack의 ReadData를 한 선에 잇는다. 첫 16사이클에 두 영역에 쓰고(MemRead 0이라 아무도
     * 구동하지 않음), 다음 16사이클에 읽는다. 주소가 속한 쪽만 값을 낸다.
     */
    @Test
    void dataMemoryAndStackShareReadDataAndOnlyTheOwnerDrives() throws Exception {
        OriginalLogisim o = new OriginalLogisim(tmp);
        CircuitBuilder b = o.b;
        clock(b);
        counter(b, 5, "k", "3", "r", "1", "p", "1");
        Component mux = b.add("Plexers", "Multiplexer", 700, 300, "width", "32", "enable", "false");
        b.tunnel(mux, 0, "dbase");
        b.tunnel(mux, 1, "sbase");
        b.tunnel(mux, 2, "r");
        b.tunnel(mux, 3, "base");
        b.constant("dbase", 32, 0x10010000, 400, 280);
        b.constant("sbase", 32, 0x7FFFFFE0, 400, 320);
        wordAddress(b, "base", "k", 3, 500);
        Component xor = b.add("Gates", "XOR Gate", 1000, 700, "width", "32", "inputs", "2");
        b.tunnel(xor, 0, "wdata");
        b.tunnel(xor, 1, "addr");
        b.tunnel(xor, 2, "pattern");
        b.constant("pattern", 32, 0xA5A5A5A5, 800, 760);
        Component not = b.add("Gates", "NOT Gate", 1000, 800);
        b.tunnel(not, 0, "we");
        b.tunnel(not, 1, "p");

        String[] kinds = {"Data Memory", "Stack"};
        for (int i = 0; i < 2; i += 1) {
            Component m = b.add(o.mips, kinds[i], 1400, 300 + 200 * i);
            b.tunnel(m, DataMemory.ADDR, "addr");
            b.tunnel(m, DataMemory.WRITE_DATA, "wdata");
            b.tunnel(m, DataMemory.MEM_WRITE, "we");
            b.tunnel(m, DataMemory.MEM_READ, "p");
            b.tunnel(m, DataMemory.CLK, "clk");
            b.tunnel(m, DataMemory.READ_DATA, "rdata");
        }
        b.output("n", 5, 1800, 100);
        b.output("addr", 32, 1800, 180);
        b.output("rdata", 32, 1800, 260);
        b.output("halt", 1, 1800, 340);

        List<String> expected = new ArrayList<>();
        for (int n = 0; n < 32; n += 1) {
            long addr = ((n & 8) != 0 ? 0x7FFFFFE0L : 0x10010000L) + 4 * (n & 7);
            expected.add(row(n, addr, (n & 16) != 0 ? addr ^ 0xA5A5A5A5L : null));
        }
        assertEquals(expected, hexRows(o.run("dmem")));
    }

    /**
     * 떠 있는 MemWrite는 쓰기로 치지 않는다(원조 RAM과 다름). 떠 있는 WriteData를 쓰면 그 칸은 정의되지
     * 않은 값(x)이 된다. contents는 초기값이다.
     */
    @Test
    void floatingMemWriteDoesNotStoreAndUndefinedDataReadsBackUndefined() throws Exception {
        OriginalLogisim o = new OriginalLogisim(tmp);
        CircuitBuilder b = o.b;
        clock(b);
        counter(b, 3, "k", "2", "pass", "1");
        b.constant("base", 32, 0x10010000, 400, 280);
        wordAddress(b, "base", "k", 2, 500);
        b.constant("ones", 32, -1, 800, 700);

        Component a = b.add(o.mips, "Data Memory", 1400, 300,
                "contents", "hcs-words 1\n10010000 cafebabe 12345678\n");
        b.tunnel(a, DataMemory.ADDR, "addr");
        b.tunnel(a, DataMemory.WRITE_DATA, "ones");
        b.tunnel(a, DataMemory.MEM_READ, "one");
        b.tunnel(a, DataMemory.CLK, "clk");
        b.tunnel(a, DataMemory.READ_DATA, "ra");
        Component x = b.add(o.mips, "Data Memory", 1400, 600);
        b.tunnel(x, DataMemory.ADDR, "addr");
        b.tunnel(x, DataMemory.MEM_WRITE, "one");
        b.tunnel(x, DataMemory.MEM_READ, "one");
        b.tunnel(x, DataMemory.CLK, "clk");
        b.tunnel(x, DataMemory.READ_DATA, "rb");
        b.output("n", 3, 1800, 100);
        b.output("ra", 32, 1800, 180);
        b.output("rb", 32, 1800, 260);
        b.output("halt", 1, 1800, 340);

        long[] contents = {0xCAFEBABEL, 0x12345678L, 0, 0};
        List<String> expected = new ArrayList<>();
        for (int n = 0; n < 8; n += 1) {
            expected.add(row(n, contents[n & 3], n < 4 ? 0L : null));
        }
        assertEquals(expected, hexRows(o.run("floating")));
    }
}
