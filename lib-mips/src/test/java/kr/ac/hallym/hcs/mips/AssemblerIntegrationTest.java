/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.util.ZipClassLoader;

/** hcs-asm과 붙는 부분(.s 불러오기, PLAN.md 6.3·6.6). */
class AssemblerIntegrationTest {
    static final Path TESTS = Path.of(System.getProperty("hcs.testsDir"));
    static final File HCS_ASM = new File(System.getProperty("hcs.asm"));
    static final File ORACLE = new File(System.getProperty("hcs.spimOracle"));
    static final Path SPIM_DIR = Path.of(System.getProperty("hcs.spimDir"));

    @TempDir
    Path tmp;

    static AssembledProgram assemble(Path source, String... flags) throws Exception {
        assertTrue(HCS_ASM.canExecute(), "먼저 make -C native/hcs-asm 을 돌린다: " + HCS_ASM);
        HcsAsm.Run run = HcsAsm.run(HCS_ASM, source.toFile(), List.of(flags));
        assertTrue(run.exit == 0 || run.exit == 1, run.stderr);
        return AssembledProgram.fromJson(run.stdout);
    }

    /** tests/asm의 골든 JSON을 모두 읽는다. */
    @Test
    void readsEveryGoldenOutput() throws Exception {
        int files = 0;
        try (var list = Files.list(TESTS.resolve("asm"))) {
            for (Path p : (Iterable<Path>) list.filter(f -> f.toString().endsWith(".json"))::iterator) {
                AssembledProgram prog = AssembledProgram.fromJson(Files.readString(p));
                assertEquals(false, prog.settings.get("delayed_branches"), p.toString());
                files += 1;
            }
        }
        assertTrue(files >= 10);
        AssembledProgram mem = AssembledProgram.fromJson(Files.readString(TESTS.resolve("asm/memory.json")));
        assertEquals(0x00400000L, (long) mem.entry);
        assertEquals(0x10010000L, (long) mem.labels.get("arr"));
        assertEquals(10, (int) mem.data.get(0x10010000L));
        assertEquals(14, mem.textImage().size());
        AssembledProgram bad = AssembledProgram.fromJson(Files.readString(TESTS.resolve("asm/syntax-error.json")));
        assertEquals(4, bad.errors.get(0).line);
        assertEquals("addi  $t0, $t0,", bad.errors.get(0).context);
    }

    @Test
    void listsTheInstructionsAProgramUses() throws Exception {
        AssembledProgram p = AssembledProgram.fromJson(Files.readString(TESTS.resolve("asm/pseudo.json")));
        List<String> used = p.usedInstructions();
        // li 큰 값 = lui+ori, blt = slt+bne, move = addu, neg = sub, not = nor, b = bgez
        for (String m : List.of("lui", "ori", "slt", "bne", "addu", "nor", "sub", "syscall")) {
            assertTrue(used.contains(m), m + " in " + used);
        }
        assertFalse(used.contains("?"), used.toString());
    }

    /** 디스어셈블러의 이름이 원본 spim의 디스어셈블과 같다. */
    @Test
    void mnemonicsMatchTheOriginalSpim() throws Exception {
        assertTrue(ORACLE.canExecute(), "먼저 make -C native/hcs-asm oracle 을 돌린다: " + ORACLE);
        List<Path> programs = new ArrayList<>();
        try (var list = Files.list(TESTS.resolve("asm"))) {
            list.filter(f -> f.toString().endsWith(".s")).forEach(programs::add);
        }
        programs.add(SPIM_DIR.resolve("Tests/tt.core.s"));
        programs.add(SPIM_DIR.resolve("Tests/tt.alu.bare.s"));
        Pattern line = Pattern.compile("^\\[0x([0-9a-f]{8})\\]\\s+0x([0-9a-f]{8})\\s+(\\S+)");
        int compared = 0;
        int unknown = 0;
        List<String> missing = new ArrayList<>();
        for (Path program : programs) {
            Path work = Files.createTempDirectory(tmp, "spim");
            new ProcessBuilder(ORACLE.getPath(), "-noexception", "-dump", "-file", program.toString())
                    .directory(work.toFile()).redirectErrorStream(true)
                    .redirectOutput(work.resolve("log").toFile()).start().waitFor();
            for (String l : Files.readAllLines(work.resolve("text.asm"))) {
                Matcher m = line.matcher(l);
                if (!m.find()) {
                    continue;
                }
                int word = (int) Long.parseLong(m.group(2), 16);
                String ours = Disassembler.mnemonic(word);
                if (ours == null) {
                    // 부동소수점(COP1, COP1X)은 수업 범위 밖이라 이름을 모른다. 그 밖은 모두 알아야 한다.
                    int op = word >>> 26;
                    if (op != 0x11 && op != 0x13) {
                        missing.add(program.getFileName() + ": " + l);
                    }
                    unknown += 1;
                    continue;
                }
                assertEquals(m.group(3), ours, program.getFileName() + " " + l);
                compared += 1;
            }
        }
        assertEquals(List.of(), missing);
        assertTrue(compared > 2000, "compared " + compared);
        assertTrue(unknown > 0); // tt.core.s에는 부동소수점 명령이 있다
    }

    /** 어셈블한 .text·.data를 메모리에 넣고 원조 엔진에서 읽으면 SPIM이 둔 워드가 나온다. */
    @Test
    void loadedProgramIsWhatTheMemoriesServe() throws Exception {
        AssembledProgram prog = assemble(TESTS.resolve("asm/memory.s"));
        InProcessSim sim = new InProcessSim();
        Component im = sim.b.add(sim.mips, "Instruction Memory", 600, 100);
        Component dm = sim.b.add(sim.mips, "Data Memory", 600, 400);
        sim.b.add(sim.mips, "Stack", 600, 700);
        sim.b.constant("pc", 32, prog.entry.intValue() + 4, 100, 100);
        sim.b.tunnel(im, InstructionMemory.ADDR, "pc");
        sim.b.constant("addr", 32, prog.labels.get("arr").intValue() + 12, 100, 400);
        sim.b.tunnel(dm, DataMemory.ADDR, "addr");
        sim.b.constant("one", 1, 1, 100, 500);
        sim.b.tunnel(dm, DataMemory.MEM_READ, "one");
        sim.b.commit();

        List<Circuit> circuits = sim.file.getCircuits();
        List<ProgramLoader.Target> texts = ProgramLoader.find(circuits, true);
        List<ProgramLoader.Target> datas = ProgramLoader.find(circuits, false);
        assertEquals(1, texts.size());
        assertEquals(1, datas.size()); // Stack은 .data 후보가 아니다
        ProgramLoader.Plan plan = ProgramLoader.plan(prog, texts.get(0), datas.get(0), "memory.s");
        for (ProgramLoader.Change ch : plan.changes) {
            @SuppressWarnings("unchecked")
            com.cburch.logisim.data.Attribute<Object> a = (com.cburch.logisim.data.Attribute<Object>) ch.attr;
            ch.target.component.getAttributeSet().setValue(a, ch.value);
        }
        sim.start();
        assertEquals(prog.text.get(1).word, sim.port(im, InstructionMemory.INSTR).toIntValue());
        assertEquals(40, sim.port(dm, DataMemory.READ_DATA).toIntValue()); // arr[3]
        assertEquals("memory.s", im.getAttributeSet().getValue(MemoryFactory.SOURCE));
        assertTrue(plan.notes.get(plan.notes.size() - 1).contains("lw"), plan.notes.toString());
    }

    @Test
    void reportsWhatHasNoMemory() throws Exception {
        AssembledProgram prog = assemble(TESTS.resolve("asm/strings.s"));
        ProgramLoader.Plan plan = ProgramLoader.plan(prog, null, null, "strings.s");
        assertTrue(plan.changes.isEmpty());
        assertEquals(2, plan.notes.stream().filter(n -> n.contains("Memory")).count(), plan.notes.toString());
    }

    @Test
    void sourcePathIsRelativeToTheCircuitWhenBelowIt() {
        File circ = new File(tmp.toFile(), "lab/cpu.circ");
        assertEquals("prog.s", ProgramLoader.relativeSource(circ, new File(tmp.toFile(), "lab/prog.s")));
        assertEquals("asm/prog.s", ProgramLoader.relativeSource(circ, new File(tmp.toFile(), "lab/asm/prog.s")));
        String outside = new File(tmp.toFile(), "other/prog.s").getAbsolutePath();
        assertEquals(outside, ProgramLoader.relativeSource(circ, new File(outside)));
        assertEquals(outside, ProgramLoader.relativeSource(null, new File(outside)));
    }

    /**
     * 원조 2.7.1은 JAR 라이브러리를 ZipClassLoader로 읽는다. 그 로더가 주는 리소스 URL에서 jar 폴더를 찾는다.
     * (이 테스트 JVM은 같은 클래스를 클래스패스에도 가지고 있어 loadClass는 부모가 가져가므로, 로더의
     * findResource를 직접 부른다.)
     */
    @Test
    void findsHcsAsmNextToTheJarUnderTheOriginalClassLoader() throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("lib dir"));
        Path jar = dir.resolve("hcs-mips.jar");
        Files.copy(OriginalLogisim.MIPS_JAR, jar, StandardCopyOption.REPLACE_EXISTING);
        ZipClassLoader loader = new ZipClassLoader(jar.toFile());
        java.net.URL url = loader.findResource("kr/ac/hallym/hcs/mips/HcsAsm.class");
        assertNotNull(url);
        assertEquals("jar", url.getProtocol());
        assertEquals(dir.toFile().getCanonicalFile(), HcsAsm.jarDirectory(url).getCanonicalFile());
        assertEquals(null, HcsAsm.jarDirectory(new File(tmp.toFile(), "x.class").toURI().toURL()));
    }

    @Test
    void settingsMapKeepsHcsAsmNames() throws Exception {
        AssembledProgram prog = assemble(TESTS.resolve("asm/branches.s"));
        Map<String, Object> s = prog.settings;
        assertEquals(false, s.get("delayed_branches")); // QtSpim 기본 설정 그대로(D-010)
        assertEquals(false, s.get("bare_machine"));
        assertEquals(false, s.get("exception_handler"));
        assertEquals(Value.TRUE, Value.TRUE); // 형식상
    }
}
