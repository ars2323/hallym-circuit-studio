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

import kr.ac.hallym.hcs.mips.image.ExecutableImage;

/** hcs-asm과 붙는 부분(전환용 .s 불러오기 {@link AssemblyTransition}, PLAN.md 6.3·6.6, D-126). */
class AssemblerIntegrationTest {
    static final Path TESTS = Path.of(System.getProperty("hcs.testsDir"));
    static final File HCS_ASM = new File(System.getProperty("hcs.asm"));
    static final File ORACLE = new File(System.getProperty("hcs.spimOracle"));
    static final Path SPIM_DIR = Path.of(System.getProperty("hcs.spimDir"));

    @TempDir
    Path tmp;

    static AssemblyTransition.Program assemble(Path source, String... flags) throws Exception {
        assertTrue(HCS_ASM.canExecute(), "먼저 make -C native/hcs-asm 을 돌린다: " + HCS_ASM);
        AssemblyTransition.Run run = AssemblyTransition.run(HCS_ASM, source.toFile(), List.of(flags));
        assertTrue(run.exit == 0 || run.exit == 1, run.stderr);
        return AssemblyTransition.Program.fromJson(run.stdout);
    }

    /** 전환용 .s 경로와 같게: hcs-asm -exception(Hallym MIPS 배치)으로 어셈블한 실행 이미지. 오류가 없어야 한다. */
    static ExecutableImage image(Path source) throws Exception {
        AssemblyTransition.Program p = assemble(source, AssemblyTransition.FLAGS.toArray(new String[0]));
        assertEquals(List.of(), p.errors.stream().map(Object::toString).toList(), source.toString());
        return AssemblyTransition.toImage(p, source.getFileName().toString());
    }

    /** 이미지를 plan대로 부품 속성에 넣는다(메뉴의 되돌리기 동작 대신). */
    static void apply(ProgramLoader.Plan plan) {
        assertEquals(List.of(), plan.errors);
        for (ProgramLoader.Change ch : plan.changes) {
            @SuppressWarnings("unchecked")
            com.cburch.logisim.data.Attribute<Object> a = (com.cburch.logisim.data.Attribute<Object>) ch.attr;
            ch.target.component.getAttributeSet().setValue(a, ch.value);
        }
    }

    /** tests/asm의 골든 JSON을 모두 읽는다. */
    @Test
    void readsEveryGoldenOutput() throws Exception {
        int files = 0;
        try (var list = Files.list(TESTS.resolve("asm"))) {
            for (Path p : (Iterable<Path>) list.filter(f -> f.toString().endsWith(".json"))::iterator) {
                AssemblyTransition.Program prog = AssemblyTransition.Program.fromJson(Files.readString(p));
                assertEquals(false, prog.settings.get("delayed_branches"), p.toString());
                files += 1;
            }
        }
        assertTrue(files >= 10);
        AssemblyTransition.Program mem = AssemblyTransition.Program.fromJson(
                Files.readString(TESTS.resolve("asm/memory.json")));
        assertEquals(0x00400000L, (long) mem.entry);
        assertEquals(0x10010000L, (long) mem.labels.get("arr"));
        assertEquals(10, (int) mem.data.get(0x10010000L));
        assertEquals(14, mem.text.size());
        AssemblyTransition.Program bad = AssemblyTransition.Program.fromJson(
                Files.readString(TESTS.resolve("asm/syntax-error.json")));
        assertEquals(4, bad.errors.get(0).line);
        assertEquals("addi  $t0, $t0,", bad.errors.get(0).context);
    }

    @Test
    void listsTheInstructionsAProgramUses() throws Exception {
        AssemblyTransition.Program p = AssemblyTransition.Program.fromJson(
                Files.readString(TESTS.resolve("asm/pseudo.json")));
        List<String> used = ProgramLoader.usedInstructions(AssemblyTransition.toImage(p, "pseudo.s"));
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

    /** 어셈블한 .text·.data를 메모리에 넣고 원조 엔진에서 읽으면 SPIM이 둔 워드가 나온다(Hallym MIPS 배치). */
    @Test
    void loadedProgramIsWhatTheMemoriesServe() throws Exception {
        ExecutableImage img = image(TESTS.resolve("asm/memory.s"));
        assertEquals(0x00400024L, (long) img.entry(), "main after the 9-word start code");
        assertEquals(0x8fa40000, (int) img.textWords().get(0x00400000L), "start code kept at its address");
        InProcessSim sim = new InProcessSim();
        Component im = sim.b.add(sim.mips, "Instruction Memory", 600, 100);
        Component dm = sim.b.add(sim.mips, "Data Memory", 600, 400);
        sim.b.add(sim.mips, "Stack", 600, 700);
        sim.b.constant("pc", 32, img.entry().intValue() + 4, 100, 100);
        sim.b.tunnel(im, InstructionMemory.ADDR, "pc");
        sim.b.constant("addr", 32, img.symbols().get("arr").intValue() + 12, 100, 400);
        sim.b.tunnel(dm, DataMemory.ADDR, "addr");
        sim.b.constant("one", 1, 1, 100, 500);
        sim.b.tunnel(dm, DataMemory.MEM_READ, "one");
        sim.b.commit();

        List<Circuit> circuits = sim.file.getCircuits();
        List<ProgramLoader.Target> texts = ProgramLoader.find(circuits, true);
        List<ProgramLoader.Target> datas = ProgramLoader.find(circuits, false);
        assertEquals(1, texts.size());
        assertEquals(1, datas.size()); // Stack은 .data 후보가 아니다
        ProgramLoader.Plan plan = ProgramLoader.plan(img, texts, datas, ProgramLoader.findStacks(circuits), null,
                null, "memory.s");
        apply(plan);
        sim.start();
        assertEquals((int) img.textWords().get(0x00400028L), sim.port(im, InstructionMemory.INSTR).toIntValue());
        assertEquals(40, sim.port(dm, DataMemory.READ_DATA).toIntValue()); // arr[3]
        assertEquals("memory.s", im.getAttributeSet().getValue(MemoryFactory.SOURCE));
        assertTrue(plan.notes.get(plan.notes.size() - 1).contains("lw"), plan.notes.toString());
    }

    /** 담을 부품이 없으면 구간과 범위를 말하고 아무것도 바꾸지 않는다(전부 아니면 전무). */
    @Test
    void reportsWhatHasNoMemory() throws Exception {
        ExecutableImage img = image(TESTS.resolve("asm/strings.s"));
        ProgramLoader.Plan plan = ProgramLoader.plan(img, List.of(), List.of(), List.of(), null, null, "strings.s");
        assertTrue(plan.changes.isEmpty());
        assertEquals(2, plan.errors.size(), plan.errors.toString());
        String e = String.join("\n", plan.errors); // 설명 문장은 언어 설정을 따른다
        assertTrue(e.contains(".text 0x00400000\u20130x0040004c") && e.contains("Instruction Memory"), e);
        assertTrue(e.contains(".data 0x10010000\u2013") && e.contains("Data Memory"), e);
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
        java.net.URL url = loader.findResource("kr/ac/hallym/hcs/mips/AssemblyTransition.class");
        assertNotNull(url);
        assertEquals("jar", url.getProtocol());
        assertEquals(dir.toFile().getCanonicalFile(), AssemblyTransition.jarDirectory(url).getCanonicalFile());
        assertEquals(null, AssemblyTransition.jarDirectory(new File(tmp.toFile(), "x.class").toURI().toURL()));
    }

    /** hcs-asm 명령줄의 기본값(예외 처리기 없음)은 그대로이고, 불러오기는 -exception(Hallym MIPS 배치)을 켠다(D-126). */
    @Test
    void settingsMapKeepsHcsAsmNames() throws Exception {
        AssemblyTransition.Program prog = assemble(TESTS.resolve("asm/branches.s"));
        Map<String, Object> s = prog.settings;
        assertEquals(false, s.get("delayed_branches")); // QtSpim 기본 설정 그대로(D-010)
        assertEquals(false, s.get("bare_machine"));
        assertEquals(false, s.get("exception_handler"), "hcs-asm CLI default unchanged");
        assertEquals(0x00400000L, (long) prog.entry);
        AssemblyTransition.Program loaded = assemble(TESTS.resolve("asm/branches.s"),
                AssemblyTransition.FLAGS.toArray(new String[0]));
        assertEquals(true, loaded.settings.get("exception_handler"), "the loader uses the Hallym MIPS layout");
        assertEquals(false, loaded.settings.get("delayed_branches"));
        assertEquals(0x00400024L, (long) loaded.entry);
        assertEquals(Value.TRUE, Value.TRUE); // 형식상
    }

    /** 전환용 .s 경로: 요약에 ".s 임시 지원"이 보이고, 시작 코드 9워드가 파일 주소 그대로 들어간다. */
    @Test
    void readingAnAssemblyFileGoesThroughTheTransitionClass() throws Exception {
        ProgramLoader.Loaded l = ProgramLoader.read(TESTS.resolve("mips/sum.s").toFile());
        assertEquals(List.of(), l.errors);
        assertTrue(l.transition);
        assertTrue(l.notes.get(0).startsWith(".s temporary support (hcs-asm -exception, Hallym MIPS layout)")
                || l.notes.get(0).startsWith(".s 임시 지원"), l.notes.toString());
        assertEquals(0x00400024L, (long) l.image.entry());
        assertEquals(9, l.image.textWords().headMap(0x00400024L).size(), "start code words");
        assertEquals(0x00400000L, (long) l.image.textWords().firstKey());
        ProgramLoader.Loaded bad = ProgramLoader.read(TESTS.resolve("asm/syntax-error.s").toFile());
        assertEquals(null, bad.image);
        assertTrue(bad.errors.size() >= 2 && bad.errors.get(1).startsWith("4: syntax error"), bad.errors.toString());
    }
}
