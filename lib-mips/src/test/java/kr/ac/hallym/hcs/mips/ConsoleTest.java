/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** Console(PLAN.md 6.9)이 syscall을 처리하고, print_string이 Data Memory에서 바이트를 읽는다. */
class ConsoleTest {
    /** 단계마다 {V0, A0, Syscall}. 카운터가 단계를 넘기고 Console은 같은 클럭 상승 에지에 처리한다. */
    static final int[][] STEPS = {
        {4, 0x10010000, 1},   // print_string "Hello\n"
        {1, -42, 1},          // print_int
        {11, 'A', 1},         // print_char
        {1, 5, 0},            // Syscall 0: 아무것도 안 함
        {5, 0, 1},            // 지원하지 않는 번호
        {4, 0x10010008, 1},   // print_string "한글" (UTF-8)
        {10, 0, 1},           // exit
        {1, 99, 1},           // exit 뒤: 처리하지 않음
    };

    @TempDir
    Path tmp;

    static String rom(int column, int width) {
        StringBuilder sb = new StringBuilder("addr/data: 3 " + width + "\n");
        for (int[] step : STEPS) {
            long v = step[column] & (width == 32 ? 0xffffffffL : 1);
            sb.append(Long.toHexString(v)).append(' ');
        }
        return sb.toString().trim() + "\n";
    }

    /** 문자열들을 0으로 끝나는 바이트열로 이어 붙인 .data 워드(리틀 엔디언). */
    static String data(long base, String... strings) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        for (String s : strings) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            bytes.write(b, 0, b.length);
            bytes.write(0);
            while (bytes.size() % 4 != 0) {
                bytes.write(0);
            }
        }
        byte[] b = bytes.toByteArray();
        Map<Long, Integer> words = new TreeMap<>();
        for (int i = 0; i < b.length; i += 4) {
            int w = (b[i] & 0xff) | (b[i + 1] & 0xff) << 8 | (b[i + 2] & 0xff) << 16 | (b[i + 3] & 0xff) << 24;
            words.put(base + i, w);
        }
        return WordImage.of(words).format();
    }

    /** 카운터, ROM 세 개, Data Memory, Console. 반환값은 Console. */
    static Component build(CircuitBuilder b, Library mips) {
        Component clock = b.add("Wiring", "Clock", 80, 40);
        b.tunnel(clock, 0, "clk");
        b.constant("zero", 1, 0, 80, 80);
        b.constant("one", 1, 1, 80, 120);
        Component c = b.add("Memory", "Counter", 300, 60, "width", "3", "max", "0x7");
        b.tunnel(c, 0, "n");
        b.tunnel(c, 2, "clk");
        b.tunnel(c, 3, "zero");
        b.tunnel(c, 4, "zero");
        b.tunnel(c, 5, "one");
        String[][] roms = {{"v0", rom(0, 32)}, {"a0", rom(1, 32)}, {"sys", rom(2, 1)}};
        for (int i = 0; i < roms.length; i += 1) {
            Component r = b.add("Memory", "ROM", 600, 200 + 150 * i, "addrWidth", "3",
                    "dataWidth", i == 2 ? "1" : "32", "contents", roms[i][1]);
            b.tunnel(r, 0, roms[i][0]);
            b.tunnel(r, 1, "n");
            b.tunnel(r, 2, "one");
        }
        Component dm = b.add(mips, "Data Memory", 1000, 300,
                "contents", data(0x10010000L, "Hello\n", "한글"));
        b.tunnel(dm, DataMemory.CLK, "clk");
        Component console = b.add(mips, "Console", 1000, 600);
        b.tunnel(console, Console.SYSCALL, "sys");
        b.tunnel(console, Console.V0, "v0");
        b.tunnel(console, Console.A0, "a0");
        b.tunnel(console, Console.CLK, "clk");
        b.tunnel(console, Console.EXIT, "halt");
        b.output("n", 3, 1400, 100);
        b.output("halt", 1, 1400, 180);
        return console;
    }

    @Test
    void servicesPrintIntStringCharAndExit() throws Exception {
        InProcessSim sim = new InProcessSim();
        Component console = build(sim.b, sim.mips);
        sim.start();
        Console.State st = null;
        for (int n = 0; n < STEPS.length; n += 1) {
            sim.cycle();
            st = (Console.State) sim.data(console);
            if (n == 4) {
                assertTrue(st.status.contains("5"), st.status); // 지원하지 않는 번호는 표시만
                assertEquals(st.status, st.statusText(), "the fork's diagnostics read the same text (D-04)");
                assertEquals("Hello\n-42A", st.text());
            }
            assertEquals(n >= 6, st.exited, "exit after step " + n);
            assertEquals(n >= 6 ? Value.TRUE : Value.FALSE, sim.port(console, Console.EXIT));
        }
        assertEquals("Hello\n-42A한글", st.text());
        assertNull(st.status);
        assertFalse(st.syscallFloating);
    }

    @Test
    void floatingSyscallIsShownAndIgnored() throws Exception {
        InProcessSim sim = new InProcessSim();
        Component console = sim.b.add(sim.mips, "Console", 600, 300);
        Component clock = sim.b.add("Wiring", "Clock", 80, 40);
        sim.b.tunnel(clock, 0, "clk");
        sim.b.tunnel(console, Console.CLK, "clk");
        sim.b.constant("v0", 32, 1, 80, 200);
        sim.b.tunnel(console, Console.V0, "v0");
        sim.start();
        sim.cycle();
        Console.State st = (Console.State) sim.data(console);
        assertTrue(st.syscallFloating);
        assertEquals("", st.text());
    }

    @Test
    void lastLinesWrapAndKeepTheTail() {
        assertEquals(List.of("abc", "de", "", "x"), Console.lastLines("abcde\n\nx", 4, 3));
        assertEquals(List.of("x"), Console.lastLines("abcde\n\nx", 1, 3));
    }

    /** 원조 2.7.1 -tty에서 Exit를 halt 핀에 이으면 exit를 처리한 사이클에서 멈춘다. */
    @Test
    void exitHaltsTheOriginalTtyRun() throws Exception {
        OriginalLogisim o = new OriginalLogisim(tmp);
        build(o.b, o.mips);
        List<String[]> rows = o.run("console");
        assertEquals(8, rows.size()); // n = 0..7, exit는 n=6의 상승 에지에 처리되고 n은 7이 된다
        assertEquals(7L, OriginalLogisim.value(rows.get(7)[0]));
    }
}
