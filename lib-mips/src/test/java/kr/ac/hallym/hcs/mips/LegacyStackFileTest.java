/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;

import kr.ac.hallym.hcs.mips.image.ExecutableImage;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 옛 구조의 파일(D-140 전 lib-mips로 만든 tests/mips/ref-mips-v1-stack.circ: 속성 없는 Data Memory + Stack)이 이 jar에서
 * 전과 똑같이 열리고 동작하며, 원조 2.7.1로 열고 저장하면 바이트가 같다. 그 파일은 D-140 직전의 tests/mips/ref-mips.circ를
 * 그대로 복사한 것이다(고치지 않는다).
 */
class LegacyStackFileTest {
    static final Path V1 = Path.of(System.getProperty("hcs.testsDir"), "mips", "ref-mips-v1-stack.circ");

    @TempDir
    Path tmp;

    /** 원조 2.7.1 + 이 jar로 옛 파일을 연다(jar를 파일 옆에 둔다). */
    static LogisimFile open(Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.copy(OriginalLogisim.MIPS_JAR, dir.resolve("hcs-mips.jar"), StandardCopyOption.REPLACE_EXISTING);
        Path circ = dir.resolve("ref-mips-v1-stack.circ");
        Files.copy(V1, circ, StandardCopyOption.REPLACE_EXISTING);
        return new Loader(null).openLogisimFile(circ.toFile());
    }

    static Component byFactory(Circuit c, String name) {
        for (Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals(name)) {
                return x;
            }
        }
        return null;
    }

    static Component byLabel(Circuit c, String label) {
        for (Component x : c.getNonWires()) {
            if (label.equals(x.getAttributeSet().getValue(StdAttr.LABEL))) {
                return x;
            }
        }
        return null;
    }

    @Test
    void opensWithTheV1RegionsAndSavesTheSameBytes() throws Exception {
        LogisimFile f = open(tmp.resolve("a"));
        Circuit main = f.getMainCircuit();
        Component dm = byFactory(main, "Data Memory");
        Component stack = byFactory(main, "Stack");
        assertNotNull(dm);
        assertNotNull(stack);
        assertArrayEquals(new long[] {0x10010000L, 0x10110000L}, MemoryFactory.dataRegion(dm.getAttributeSet()));
        assertNull(MemoryFactory.stackRegion(dm.getAttributeSet()), "an old Data Memory has no stack region");
        assertArrayEquals(new long[] {0x7FF00000L, 0x80000000L}, MemoryFactory.stackRegion(stack.getAttributeSet()));
        Path resaved = tmp.resolve("a/resaved.circ");
        CircuitBuilder.save(f, resaved.toFile());
        // D-006 기준: 원조 저장은 부품 순서가 해시 순서라 줄 앞 공백과 wire/comp 순서만 정규화하고, 나머지는 바이트가 같다
        String before = Files.readString(V1);
        String after = Files.readString(resaved);
        assertEquals(kr.ac.hallym.hcs.regress.CircNormalizer.normalize(before),
                kr.ac.hallym.hcs.regress.CircNormalizer.normalize(after), "saved by the original 2.7.1 + this jar");
        assertEquals(before.length(), after.length());
        for (String line : new String[] {
            "  <lib desc=\"jar#hcs-mips.jar#kr.ac.hallym.hcs.mips.MipsLibrary\" name=\"7\"/>\n",
            "    <comp lib=\"7\" loc=\"(4000,7600)\" name=\"Data Memory\"/>\n",
            "    <comp lib=\"7\" loc=\"(4600,7600)\" name=\"Stack\"/>\n"}) {
            assertTrue(before.contains(line) && after.contains(line), line);
        }
    }

    /** 옛 파일의 참조 CPU로 factorial을 돌리면 SPIM과 같고, 스택은 전처럼 옛 Stack 부품이 맡는다(깊이 56 B). */
    @Test
    void factorialRunsOnTheOldStackAsBefore() throws Exception {
        LogisimFile f = open(tmp.resolve("b"));
        Circuit main = f.getMainCircuit();
        Component imem = byFactory(main, "Instruction Memory");
        Component dm = byFactory(main, "Data Memory");
        Component stack = byFactory(main, "Stack");
        Component console = byFactory(main, "Console");
        ExecutableImage prog = RefMipsTest.image("factorial.s");
        ProgramLoader.Plan plan = ProgramLoader.plan(prog, List.of(new ProgramLoader.Target(main, imem)),
                List.of(new ProgramLoader.Target(main, dm)), List.of(new ProgramLoader.Target(main, stack)), null, null,
                "factorial.hmx");
        assertEquals(List.of(), plan.errors);
        AssemblerIntegrationTest.apply(plan);
        InProcessSim sim = new InProcessSim(f);
        sim.start();
        Console.State out = null;
        for (int n = 0; n < RefMipsTest.MAX_CYCLES && (out == null || !out.exited); n += 1) {
            sim.cycle();
            out = (Console.State) sim.data(console);
            DataMemory.State d = (DataMemory.State) sim.data(dm);
            DataMemory.State s = (DataMemory.State) sim.data(stack);
            assertTrue(d == null || d.problem == null, "Data Memory problem " + (d == null ? null : d.problem));
            assertTrue(s == null || s.problem == null, "Stack problem " + (s == null ? null : s.problem));
        }
        assertNotNull(out);
        assertTrue(out.exited);
        assertEquals("6! = 720", out.text());
        assertEquals(0x7fffeffc, sim.port(byLabel(main, "$29"), 0).toIntValue());
        DataMemory.State s = (DataMemory.State) sim.data(stack);
        assertEquals(56, s.usedBytes(), "the old Stack keeps the depth as before");
        assertTrue(s.growsDown());
        DataMemory.State d = (DataMemory.State) sim.data(dm);
        assertNull(d.stackRegion());
        assertEquals(-1, d.lowestAccess(), "the old Data Memory has no stack accesses");
        Path work = Files.createDirectories(tmp.resolve("spim"));
        RefMipsTest.Result spim = RefMipsTest.runSpim(RefMipsTest.PROGRAMS.resolve("factorial.s"), prog, work);
        assertEquals(spim.console, out.text());
    }

    /** 원조 jar -tty로 옛 파일을 돌려도 sum이 exit에서 멈춘다(첫 줄 PC = entry). */
    @Test
    void originalLogisimRunsTheOldFileToExit() throws Exception {
        LogisimFile f = open(tmp.resolve("c"));
        Circuit main = f.getMainCircuit();
        ExecutableImage prog = RefMipsTest.image("sum.s");
        ProgramLoader.Plan plan = ProgramLoader.plan(prog,
                List.of(new ProgramLoader.Target(main, byFactory(main, "Instruction Memory"))),
                List.of(new ProgramLoader.Target(main, byFactory(main, "Data Memory"))),
                List.of(new ProgramLoader.Target(main, byFactory(main, "Stack"))), null, null, "sum.hmx");
        AssemblerIntegrationTest.apply(plan);
        Path circ = tmp.resolve("c/sum.circ");
        CircuitBuilder.save(f, circ.toFile());
        List<String[]> rows = OriginalLogisim.runFile(circ);
        assertEquals(RefMipsTest.ENTRY, (long) OriginalLogisim.value(rows.get(0)[0]));
        assertEquals(prog.textWords().lastKey() + 4, (long) OriginalLogisim.value(rows.get(rows.size() - 1)[0]));
    }
}
