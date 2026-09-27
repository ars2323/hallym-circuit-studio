/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;

import kr.ac.hallym.hcs.mips.image.ExecutableImage;
import kr.ac.hallym.hcs.mips.image.HmxParser;
import kr.ac.hallym.hcs.mips.image.SourceCheck;

/**
 * 실행 이미지 대조(Z-06의 불러오기 부분, D-126).
 *
 * <ul>
 *   <li>어셈블되는 tests/asm·tests/mips의 모든 .s: {@code hcs-asm -exception} 이미지와 만든 .hmx(tests/hmx/asm,
 *       tests/hmx/mips)를 읽은 이미지가 워드 단위로 같다. 만든 파일의 글도 같다. 다시 쓰기:
 *       {@code ./gradlew :lib-mips:test -Phcs.update=true}.</li>
 *   <li>읽히는 모든 tests/hmx 이미지: 부품에 넣고(헤드리스) 모든 주소를 되읽으면 이미지와 같다.</li>
 *   <li>트랙 A는 hcs-asm 없이 .hmx만으로 불러온다.</li>
 * </ul>
 */
class HmxConsistencyTest {
    static final Path TESTS = AssemblerIntegrationTest.TESTS;

    @TempDir
    Path tmp;

    /** 어셈블 오류가 나도록 만든 시험 입력은 만들 이미지가 없다. */
    static boolean assembles(Path source) throws Exception {
        return AssemblerIntegrationTest.assemble(source, "-exception").errors.isEmpty();
    }

    @TestFactory
    Stream<DynamicTest> generatedImagesMatchHcsAsm() throws Exception {
        List<Path> sources = new ArrayList<>();
        for (Path s : HmxFiles.sources(TESTS)) {
            if (assembles(s)) {
                sources.add(s);
            }
        }
        assertTrue(sources.size() >= 12, sources.toString());
        return sources.stream().map(s -> DynamicTest.dynamicTest(TESTS.relativize(s).toString(), () -> {
            Path hmx = HmxFiles.hmxFor(TESTS, s);
            String rel = hmx.getParent().relativize(s).toString().replace(File.separatorChar, '/');
            ExecutableImage fromAsm = AssemblyTransition.toImage(
                    AssemblerIntegrationTest.assemble(s, AssemblyTransition.FLAGS.toArray(new String[0])), rel);
            String text = HmxFiles.write(fromAsm, rel, s.toFile());
            if (Boolean.getBoolean("hcs.update")) {
                Files.createDirectories(hmx.getParent());
                Files.writeString(hmx, text);
            }
            assertTrue(Files.exists(hmx), "make it with ./gradlew :lib-mips:test -Phcs.update=true: " + hmx);
            assertEquals(text, Files.readString(hmx), hmx.toString());
            assertTrue(text.split("\n")[1].equals(HmxFiles.MARK), "generated files carry the mark");

            HmxParser.Result r = HmxParser.read(hmx.toFile());
            assertEquals(List.of(), r.errors);
            ExecutableImage parsed = r.image;
            assertEquals(fromAsm.textWords(), parsed.textWords(), "text word for word");
            assertEquals(fromAsm.dataWords(), parsed.dataWords(), "data word for word");
            assertEquals(fromAsm.entry(), parsed.entry());
            assertEquals(fromAsm.symbols(), parsed.symbols());
            assertTrue(parsed.sameProgram(fromAsm));
            assertEquals(parsed.symbols().get("main"), parsed.entry(), "entry is main");
            assertEquals(0x8fa40000, (int) parsed.textWords().get(0x00400000L), "start code at 0x00400000");
            assertTrue(parsed.entry() >= 0x00400024L, "Hallym MIPS layout: main after the 9-word start code");
            assertEquals(SourceCheck.Status.SAME, SourceCheck.check(hmx.toFile(), parsed).status);
        }));
    }

    /** 만든 .hmx 가운데 명세 모양의 주요 경우가 들어 있다(앞·뒤 분기, j/jal, .data+la/lw, main이 처음 아님, 의사 명령어, .data 없음). */
    @Test
    void generatedImagesCoverTheCases() throws Exception {
        ExecutableImage branches = HmxParser.read(TESTS.resolve("hmx/asm/branches.hmx").toFile()).image;
        ExecutableImage jumps = HmxParser.read(TESTS.resolve("hmx/asm/jumps.hmx").toFile()).image;
        ExecutableImage memory = HmxParser.read(TESTS.resolve("hmx/asm/memory.hmx").toFile()).image;
        ExecutableImage notFirst = HmxParser.read(TESTS.resolve("hmx/asm/main-not-first.hmx").toFile()).image;
        ExecutableImage pseudo = HmxParser.read(TESTS.resolve("hmx/asm/pseudo.hmx").toFile()).image;
        ExecutableImage arith = HmxParser.read(TESTS.resolve("hmx/asm/arith.hmx").toFile()).image;
        List<String> ops = ProgramLoader.usedInstructions(branches);
        assertTrue(ops.contains("beq") && ops.contains("bne"), ops.toString());
        List<String> jops = ProgramLoader.usedInstructions(jumps);
        assertTrue(jops.contains("j") && jops.contains("jal") && jops.contains("jr"), jops.toString());
        assertTrue(ProgramLoader.usedInstructions(memory).contains("lw"));
        assertTrue(!memory.dataWords().isEmpty());
        assertTrue(notFirst.entry() > notFirst.textWords().firstKey() + 9 * 4, "main is not first in the user text");
        assertTrue(ProgramLoader.usedInstructions(pseudo).contains("lui"));
        assertTrue(arith.dataWords().isEmpty(), "no .data");
        assertTrue(arith.segments(ExecutableImage.Kind.DATA).isEmpty());
        // 앞·뒤 분기: 오프셋 부호가 둘 다 있다
        boolean back = false;
        boolean forward = false;
        for (int w : branches.textWords().values()) {
            int op = w >>> 26;
            if (op == 4 || op == 5) {
                back |= (short) w < 0;
                forward |= (short) w > 0;
            }
        }
        assertTrue(back && forward);
    }

    /** 읽히는 모든 tests/hmx 이미지를 부품에 넣고(헤드리스) 모든 주소를 되읽는다. */
    @TestFactory
    Stream<DynamicTest> headlessLoadReadsBackEveryAddress() throws Exception {
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(TESTS.resolve("hmx"))) {
            walk.filter(f -> f.toString().endsWith(".hmx")).sorted().forEach(files::add);
        }
        List<Path> ok = new ArrayList<>();
        for (Path f : files) {
            if (HmxParser.read(f.toFile()).ok()) {
                ok.add(f);
            }
        }
        assertTrue(ok.size() >= 18, ok.toString());
        return ok.stream().map(f -> DynamicTest.dynamicTest(TESTS.relativize(f).toString(), () -> {
            ProgramLoader.Loaded l = ProgramLoader.readImage(f.toFile());
            assertEquals(List.of(), l.errors);
            ExecutableImage img = l.image;
            InProcessSim sim = new InProcessSim();
            Component im = sim.b.add(sim.mips, "Instruction Memory", 600, 100);
            Component dm = sim.b.add(sim.mips, "Data Memory", 600, 400);
            Component gp = sim.b.add(sim.mips, "Data Memory", 600, 700, "base", "0x10000000", "size", "0x10000");
            Component st = sim.b.add(sim.mips, "Stack", 600, 1000);
            sim.b.commit();
            List<Circuit> circuits = sim.file.getCircuits();
            ProgramLoader.Plan plan = ProgramLoader.plan(l, circuits, null, null, f.getFileName().toString());
            AssemblerIntegrationTest.apply(plan);
            sim.start();
            WordImage text = im.getAttributeSet().getValue(MemoryFactory.CONTENTS);
            assertEquals(img.textWords().size(), text.size());
            for (Map.Entry<Long, Integer> e : img.textWords().entrySet()) {
                assertEquals(e.getValue(), text.read((int) (long) e.getKey()), Long.toHexString(e.getKey()));
            }
            int dataWords = 0;
            for (Component c : new Component[] {dm, gp}) {
                DataMemory.State s = (DataMemory.State) sim.data(c);
                assertNotNull(s);
                for (Map.Entry<Long, Integer> e : img.dataWords().entrySet()) {
                    int a = (int) (long) e.getKey();
                    if (s.contains(a)) {
                        assertTrue(s.isDefined(a), Long.toHexString(e.getKey()));
                        assertEquals((int) e.getValue(), s.readWord(a), Long.toHexString(e.getKey()));
                        dataWords += 1;
                    }
                }
            }
            assertEquals(img.dataWords().size(), dataWords, "every data word is in some Data Memory");
            WordImage stack = st.getAttributeSet().getValue(MemoryFactory.CONTENTS);
            assertEquals(img.reg("$sp"), stack.initialSp(), "reg $sp is the Stack depth base");
            assertEquals(f.getFileName().toString(), im.getAttributeSet().getValue(MemoryFactory.SOURCE));
        }));
    }

    /** reg $sp가 있으면 그 값에서 Stack 깊이를 잰다(0x7FFFEFFC 규칙보다 먼저). */
    @Test
    void regSpIsTheStackDepthBase() throws Exception {
        ProgramLoader.Loaded l = ProgramLoader.readImage(TESTS.resolve("hmx/example.hmx").toFile());
        InProcessSim sim = new InProcessSim();
        Component st = sim.b.add(sim.mips, "Stack", 600, 400);
        sim.b.add(sim.mips, "Instruction Memory", 600, 100);
        sim.b.add(sim.mips, "Data Memory", 600, 700);
        sim.b.constant("addr", 32, 0x7fffeff8, 100, 400); // $sp 0x7ffff000 아래 두 번째 워드
        sim.b.tunnel(st, DataMemory.ADDR, "addr");
        sim.b.constant("one", 1, 1, 100, 500);
        sim.b.tunnel(st, DataMemory.MEM_READ, "one");
        sim.b.constant("zero", 1, 0, 100, 560);
        sim.b.tunnel(st, DataMemory.MEM_WRITE, "zero");
        Component clk = sim.b.add("Wiring", "Clock", 100, 620);
        sim.b.tunnel(clk, 0, "clk");
        sim.b.tunnel(st, DataMemory.CLK, "clk");
        sim.b.commit();
        AssemblerIntegrationTest.apply(ProgramLoader.plan(l, sim.file.getCircuits(), null, null, "example.hmx"));
        sim.start();
        sim.cycle();
        DataMemory.State s = (DataMemory.State) sim.data(st);
        assertEquals(0x7ffff000L, s.depthBase());
        assertEquals(8, s.usedBytes(), "measured from the file's $sp, not 0x7FFFEFFC");
        // 다시 불러와 reg $sp가 없는 이미지면 기준을 지운다
        ProgramLoader.Loaded none = ProgramLoader.readImage(TESTS.resolve("hmx/asm/memory.hmx").toFile());
        ProgramLoader.Plan plan = ProgramLoader.plan(none, sim.file.getCircuits(), null, null, "memory.hmx");
        AssemblerIntegrationTest.apply(plan);
        assertEquals(null, st.getAttributeSet().getValue(MemoryFactory.CONTENTS).initialSp());
        // 저장 형식: 주소만 있는 줄(옛 lib-mips도 건너뛴다)
        WordImage marked = WordImage.EMPTY.withInitialSp(0x7ffff000L);
        assertEquals("hcs-words 1\n7ffff000\n", marked.format());
        assertEquals(marked, WordImage.parse(marked.format()));
    }

    /**
     * 트랙 A는 .hmx만으로 동작한다: hcs-asm을 찾지 못하는 환경(jar 옆에 없음, 속성·환경 변수 없음)에서도 .hmx는
     * 읽히고, .s만 "hcs-asm을 찾을 수 없음"이다.
     */
    @Test
    void trackAWorksWithExecutableImagesOnly() throws Exception {
        String old = System.getProperty("hcs.asm");
        try {
            System.setProperty("hcs.asm", tmp.resolve("no-such-hcs-asm").toString());
            org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("HCS_ASM") == null, "HCS_ASM set");
            assertEquals(null, AssemblyTransition.locate(), "no hcs-asm in this setup");
            ProgramLoader.Loaded hmx = ProgramLoader.read(TESTS.resolve("hmx/example.hmx").toFile());
            assertEquals(List.of(), hmx.errors);
            assertEquals(14, hmx.image.textWords().size());
            ProgramLoader.Loaded s = ProgramLoader.read(TESTS.resolve("mips/sum.s").toFile());
            assertEquals(null, s.image);
            assertTrue(s.errors.get(0).contains("hcs-asm"), s.errors.toString());
        } finally {
            System.setProperty("hcs.asm", old);
        }
    }

    /** 담지 못하는 구간: 우클릭한 부품이 구간을 담지 못하면 오류이고 아무것도 바꾸지 않는다. */
    @Test
    void chosenMemoryMustCoverTheSegment() throws Exception {
        ProgramLoader.Loaded l = ProgramLoader.readImage(TESTS.resolve("hmx/example.hmx").toFile());
        InProcessSim sim = new InProcessSim();
        Component im = sim.b.add(sim.mips, "Instruction Memory", 600, 100, "base", "0x0");
        sim.b.add(sim.mips, "Data Memory", 600, 400);
        sim.b.commit();
        Circuit main = sim.file.getMainCircuit();
        ProgramLoader.Plan plan = ProgramLoader.plan(l, sim.file.getCircuits(), new ProgramLoader.Target(main, im),
                null, "example.hmx");
        assertTrue(plan.changes.isEmpty());
        assertEquals(List.of(".text 0x00400000–0x00400034 is outside the chosen main › Instruction Memory"
                + " (00000000-000fffff), so nothing was loaded."), plan.errors);
        // 둘 이상이 담으면 고르게 한다
        InProcessSim two = new InProcessSim();
        two.b.add(two.mips, "Instruction Memory", 600, 100);
        two.b.add(two.mips, "Instruction Memory", 600, 400, "label", "IM2");
        two.b.add(two.mips, "Data Memory", 600, 700);
        two.b.commit();
        List<String> asked = new ArrayList<>();
        ProgramLoader.Plan chosen = ProgramLoader.plan(l, two.file.getCircuits(), null, (cands, what) -> {
            asked.add(what + " " + cands.size());
            for (ProgramLoader.Target t : cands) { // 부품 순서는 정해져 있지 않다: 라벨로 고른다
                if ("IM2".equals(t.component.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.LABEL))) {
                    return t;
                }
            }
            return null;
        }, "example.hmx");
        assertEquals(List.of(".text 0x00400000–0x00400034 2"), asked);
        assertEquals(List.of(), chosen.errors);
        assertEquals("IM2", chosen.text.keySet().iterator().next().component.getAttributeSet()
                .getValue(com.cburch.logisim.instance.StdAttr.LABEL));
        ProgramLoader.Plan cancelled = ProgramLoader.plan(l, two.file.getCircuits(), null, (cands, what) -> null,
                "example.hmx");
        assertTrue(cancelled.changes.isEmpty());
        assertEquals(1, cancelled.errors.size());
    }
}
