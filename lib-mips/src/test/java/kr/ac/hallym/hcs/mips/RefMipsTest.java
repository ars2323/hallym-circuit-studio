/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;

/**
 * 참조 single-cycle MIPS 회로(#16)로 예제 .s를 돌린 결과(레지스터, 메모리, Console)가 원본 spim 실행과 같다.
 * spim은 예외 처리기 없이 {@code run 0x00400000}으로 main부터 돌려 회로와 조건을 맞춘다.
 */
class RefMipsTest {
    static final Path PROGRAMS = Path.of(System.getProperty("hcs.testsDir"), "mips");
    static final File ORACLE = new File(System.getProperty("hcs.spimOracle"));
    static final int MAX_CYCLES = 5000;
    /** spim이 실행 전에 채우는 레지스터: $a1, $a2, $gp. 프로그램이 쓰지 않으면 비교하지 않는다. */
    static final int[] PRESET = {5, 6, 28};

    @TempDir
    Path tmp;

    static final class Result {
        String console;
        int[] regs = new int[32];
        Map<Long, Integer> data = new TreeMap<>();
        int cycles;
    }

    static void set(AttributeSet as, Attribute<?> attr, Object value) {
        @SuppressWarnings("unchecked")
        Attribute<Object> a = (Attribute<Object>) attr;
        as.setValue(a, value);
    }

    /** 회로에서 exit까지 돌린다. tweak은 시작 전 속성 변경(예: Stack 한계). */
    static Result runCircuit(AssembledProgram prog, java.util.function.Consumer<RefMips> tweak) throws Exception {
        return runCircuit(prog, tweak, true);
    }

    /** 상승 에지마다 본 Stack 문제들. */
    static final Set<DataMemory.Problem> stackProblems = new HashSet<>();

    static Result runCircuit(AssembledProgram prog, java.util.function.Consumer<RefMips> tweak,
            boolean requireExit) throws Exception {
        stackProblems.clear();
        InProcessSim sim = new InProcessSim();
        RefMips cpu = RefMips.build(sim.b, sim.mips);
        set(cpu.imem.getAttributeSet(), MemoryFactory.CONTENTS, prog.textImage());
        set(cpu.dmem.getAttributeSet(), MemoryFactory.CONTENTS, prog.dataImage());
        tweak.accept(cpu);
        sim.start();
        Result r = new Result();
        Console.State console = null;
        for (int n = 0; n < MAX_CYCLES; n += 1) {
            sim.cycle();
            console = (Console.State) sim.data(cpu.console);
            DataMemory.State st = (DataMemory.State) sim.data(cpu.stack);
            if (st != null && st.problem != null) {
                stackProblems.add(st.problem);
            }
            if (console != null && console.exited) {
                r.cycles = n + 1;
                break;
            }
        }
        assertNotNull(console);
        if (requireExit) {
            assertTrue(console.exited, "no exit within " + MAX_CYCLES + " cycles");
        }
        r.console = console.text();
        for (int i = 1; i < 32; i += 1) {
            r.regs[i] = sim.port(cpu.regs[i], 0).toIntValue();
        }
        DataMemory.State data = (DataMemory.State) sim.data(cpu.dmem);
        for (Long addr : prog.data.keySet()) {
            r.data.put(addr, data.memory.read((int) (long) addr));
        }
        lastSim = sim;
        lastCpu = cpu;
        return r;
    }

    static InProcessSim lastSim;
    static RefMips lastCpu;

    /** 원본 spim(예외 처리기 없음)으로 main부터 돌린다. */
    static Result runSpim(Path source, AssembledProgram prog, Path work) throws Exception {
        StringBuilder cmd = new StringBuilder("load \"" + source + "\"\nrun 0x00400000\nprint_all_regs hex\n");
        for (Long addr : prog.data.keySet()) {
            cmd.append("print 0x").append(Long.toHexString(addr)).append('\n');
        }
        Path in = work.resolve("cmd.txt");
        Files.writeString(in, cmd.toString());
        Process p = new ProcessBuilder(ORACLE.getPath(), "-noexception").redirectInput(in.toFile())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        String[] parts = out.split("\\(spim\\) ", -1);
        Result r = new Result();
        r.console = parts[2]; // load, run 다음
        Matcher m = Pattern.compile("R(\\d+)\\s+\\(\\w+\\) = ([0-9a-f]{8})").matcher(out);
        while (m.find()) {
            r.regs[Integer.parseInt(m.group(1))] = (int) Long.parseLong(m.group(2), 16);
        }
        Matcher d = Pattern.compile("seg @ 0x([0-9a-f]{8}) \\(\\d+\\) = 0x([0-9a-f]{8})").matcher(out);
        while (d.find()) {
            r.data.put(Long.parseLong(d.group(1), 16), (int) Long.parseLong(d.group(2), 16));
        }
        return r;
    }

    /** 프로그램이 값을 쓰는 레지스터(기계어의 목적지 필드). */
    static Set<Integer> written(AssembledProgram prog) {
        Set<Integer> out = new HashSet<>();
        for (AssembledProgram.Word w : prog.text) {
            int op = w.word >>> 26;
            int rt = (w.word >>> 16) & 31;
            int rd = (w.word >>> 11) & 31;
            int funct = w.word & 63;
            if (op == 0 && funct != 8 && funct != 12) {
                out.add(rd);
            } else if (op == 0x1c || op == 0 && funct == 9) {
                out.add(rd);
            } else if (op == 3) {
                out.add(31);
            } else if (op >= 8 && op <= 15 || op == 0x23) {
                out.add(rt);
            }
        }
        return out;
    }

    @TestFactory
    Stream<DynamicTest> programsMatchSpim() throws Exception {
        List<Path> programs = new ArrayList<>();
        try (var list = Files.list(PROGRAMS)) {
            list.filter(f -> f.toString().endsWith(".s")).sorted().forEach(programs::add);
        }
        assertTrue(programs.size() >= 5);
        return programs.stream().map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> {
            AssembledProgram prog = AssemblerIntegrationTest.assemble(p);
            assertEquals(List.of(), prog.errors);
            Result circuit = runCircuit(prog, cpu -> { });
            Result spim = runSpim(p, prog, Files.createTempDirectory(tmp, "spim"));
            assertEquals(spim.console, circuit.console, "Console");
            Set<Integer> writes = written(prog);
            for (int i = 1; i < 32; i += 1) {
                final int reg = i;
                boolean preset = java.util.Arrays.stream(PRESET).anyMatch(x -> x == reg);
                if (preset && !writes.contains(i)) {
                    continue;
                }
                assertEquals(String.format("%08x", spim.regs[i]), String.format("%08x", circuit.regs[i]), "$" + i);
            }
            assertEquals(spim.data.keySet(), circuit.data.keySet());
            for (Long addr : spim.data.keySet()) {
                assertEquals(String.format("%08x", spim.data.get(addr)), String.format("%08x", circuit.data.get(addr)),
                        Long.toHexString(addr));
            }
        }));
    }

    /** 재귀 팩토리얼: $sp가 내려갔다가 제자리로 돌아오고, Stack 최대 깊이는 호출 7번 × 8바이트다. */
    @Test
    void recursionMovesTheStackAndReturns() throws Exception {
        AssembledProgram prog = AssemblerIntegrationTest.assemble(PROGRAMS.resolve("factorial.s"));
        Result r = runCircuit(prog, cpu -> { });
        assertEquals("6! = 720", r.console);
        assertEquals(0x7fffeffc, r.regs[29]); // 복귀 후 $sp
        DataMemory.State st = (DataMemory.State) lastSim.data(lastCpu.stack);
        long lowest = 0x7fffeffcL - 7 * 8;
        assertEquals(0x80000000L - lowest, st.maxDepth());
        assertNull(st.problem);
    }

    /** Stack 맨 위를 프로그램의 $sp(0x7FFFEFFC)에 맞추고 한계를 256바이트로 줄인다. */
    static void smallStack(RefMips cpu) {
        set(cpu.stack.getAttributeSet(), MemoryFactory.TOP, 0x7fffeffc);
        set(cpu.stack.getAttributeSet(), MemoryFactory.SIZE, 0x100);
    }

    /** 한계를 256바이트로 줄이면 fact(6)의 깊이(56바이트)는 괜찮고, fact(40)은 한계를 넘는다. */
    @Test
    void deepRecursionReportsTheStackLimit() throws Exception {
        String deep = Files.readString(PROGRAMS.resolve("factorial.s"))
                .replace("li    $a0, 6", "li    $a0, 40");
        Path source = Files.writeString(tmp.resolve("deep.s"), deep);
        AssembledProgram prog = AssemblerIntegrationTest.assemble(source);
        // 한계 밖에는 쓰지 못해 복귀 주소가 깨지므로 exit까지 가지 않는다. 한계 초과가 알려지는지만 본다.
        runCircuit(prog, RefMipsTest::smallStack, false);
        assertTrue(stackProblems.contains(DataMemory.Problem.STACK_LIMIT), stackProblems.toString());
        DataMemory.State st = (DataMemory.State) lastSim.data(lastCpu.stack);
        assertEquals(0x100, st.maxDepth()); // 영역 안에서는 맨 아래까지 썼다
        AssembledProgram ok = AssemblerIntegrationTest.assemble(PROGRAMS.resolve("factorial.s"));
        Result r = runCircuit(ok, RefMipsTest::smallStack);
        assertEquals("6! = 720", r.console);
        assertTrue(stackProblems.isEmpty(), stackProblems.toString());
    }

    static final Path COMMITTED = PROGRAMS.resolve("ref-mips.circ");

    /** 원조 API로 만든 참조 회로(프로그램 없음)를 원조 저장 코드로 dir/ref-mips.circ에 쓴다. */
    static Path writeReference(Path dir) throws Exception {
        OriginalLogisim o = new OriginalLogisim(dir);
        RefMips.build(o.b, o.mips);
        Path out = dir.resolve("ref-mips.circ");
        kr.ac.hallym.hcs.regress.CircuitBuilder.save(o.file, out.toFile());
        return out;
    }

    /**
     * 커밋된 tests/mips/ref-mips.circ가 생성기 결과와 같다(D-006 정규화 + 의미 동등성). 생성기를 고쳤으면
     * {@code ./gradlew :lib-mips:test -Phcs.update=true}로 다시 쓴다.
     */
    @Test
    void committedReferenceCircuitMatchesGenerator() throws Exception {
        Path fresh = writeReference(Files.createDirectories(tmp.resolve("fresh")));
        if (Boolean.getBoolean("hcs.update")) {
            Files.copy(fresh, COMMITTED, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Path committed = tmp.resolve("fresh/committed.circ"); // jar 옆에 두어야 원조가 연다
        Files.copy(COMMITTED, committed);
        assertEquals(kr.ac.hallym.hcs.regress.CircNormalizer.normalize(Files.readString(committed)),
                kr.ac.hallym.hcs.regress.CircNormalizer.normalize(Files.readString(fresh)));
        assertEquals(List.of(), kr.ac.hallym.hcs.regress.CircEquivalence.compare(committed.toFile(), fresh.toFile()));
    }

    /** 원조 2.7.1 jar -tty로 sum.s를 끝까지 돌리면 Console Exit에 이은 halt로 exit 직후 멈춘다. */
    @Test
    void originalLogisimRunsTheReferenceCpuToExit() throws Exception {
        AssembledProgram prog = AssemblerIntegrationTest.assemble(PROGRAMS.resolve("sum.s"));
        OriginalLogisim o = new OriginalLogisim(tmp);
        RefMips cpu = RefMips.build(o.b, o.mips);
        set(cpu.imem.getAttributeSet(), MemoryFactory.CONTENTS, prog.textImage());
        set(cpu.dmem.getAttributeSet(), MemoryFactory.CONTENTS, prog.dataImage());
        List<String[]> rows = o.run("ref-sum");
        long exitSyscall = prog.text.get(prog.text.size() - 1).addr;
        assertEquals(exitSyscall + 4, (long) OriginalLogisim.value(rows.get(rows.size() - 1)[0]));
        assertEquals(runCircuit(prog, c -> { }).cycles + 1, rows.size()); // PC 0x00400000 줄 + 사이클마다 한 줄
    }
}
