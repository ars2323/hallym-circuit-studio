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

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.util.LocaleManager;

import kr.ac.hallym.hcs.mips.image.AssemblySource;
import kr.ac.hallym.hcs.mips.image.ExecutableImage;

/**
 * 불러오기가 원조 엔진의 메모리 부품에 붙는 부분(PLAN.md 6.3·6.6, D-126). 받는 것은 실행 이미지(.hmx)뿐이다(D-141): .s를
 * 고르거나 옛 {@code source}가 .s를 가리키면 사실과 할 일을 돌려주고 아무것도 바꾸지 않는다. 이 시험의 이미지는
 * tests/hmx/asm의 .hmx다(vendor/spim이 있을 때 hcs-asm {@code -exception}으로 만들어 굳힌 것, tests/asm/README.md).
 */
class ProgramLoadIntegrationTest {
    static final Path TESTS = Path.of(System.getProperty("hcs.testsDir"));

    @TempDir
    Path tmp;

    /** tests/hmx 아래 실행 이미지를 불러오기와 같은 길로 읽는다. 오류가 없어야 한다. */
    static ExecutableImage image(String relative) {
        ProgramLoader.Loaded l = ProgramLoader.readImage(TESTS.resolve("hmx").resolve(relative).toFile());
        assertEquals(List.of(), l.errors, relative);
        return l.image;
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

    @Test
    void listsTheInstructionsAProgramUses() {
        List<String> used = ProgramLoader.usedInstructions(image("asm/pseudo.hmx"));
        // li 큰 값 = lui+ori, blt = slt+bne, move = addu, neg = sub, not = nor, b = bgez
        for (String m : List.of("lui", "ori", "slt", "bne", "addu", "nor", "sub", "syscall")) {
            assertTrue(used.contains(m), m + " in " + used);
        }
        assertFalse(used.contains("?"), used.toString());
    }

    /** 이미지의 .text·.data를 메모리에 넣고 원조 엔진에서 읽으면 이미지의 워드가 나온다(Hallym MIPS 배치). */
    @Test
    void loadedProgramIsWhatTheMemoriesServe() throws Exception {
        ExecutableImage img = image("asm/memory.hmx");
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
                null, "memory.hmx");
        apply(plan);
        sim.start();
        assertEquals((int) img.textWords().get(0x00400028L), sim.port(im, InstructionMemory.INSTR).toIntValue());
        assertEquals(40, sim.port(dm, DataMemory.READ_DATA).toIntValue()); // arr[3]
        assertEquals("memory.hmx", im.getAttributeSet().getValue(MemoryFactory.SOURCE));
        assertTrue(plan.notes.get(plan.notes.size() - 1).contains("lw"), plan.notes.toString());
    }

    /** 담을 부품이 없으면 구간과 범위를 말하고 아무것도 바꾸지 않는다(전부 아니면 전무). */
    @Test
    void reportsWhatHasNoMemory() {
        ExecutableImage img = image("asm/strings.hmx");
        ProgramLoader.Plan plan = ProgramLoader.plan(img, List.of(), List.of(), List.of(), null, null, "strings.hmx");
        assertTrue(plan.changes.isEmpty());
        assertEquals(2, plan.errors.size(), plan.errors.toString());
        String e = String.join("\n", plan.errors); // 설명 문장은 언어 설정을 따른다
        assertTrue(e.contains(".text 0x00400000–0x0040004c") && e.contains("Instruction Memory"), e);
        assertTrue(e.contains(".data 0x10010000–") && e.contains("Data Memory"), e);
    }

    @Test
    void sourcePathIsRelativeToTheCircuitWhenBelowIt() {
        File circ = new File(tmp.toFile(), "lab/cpu.circ");
        assertEquals("prog.hmx", ProgramLoader.relativeSource(circ, new File(tmp.toFile(), "lab/prog.hmx")));
        assertEquals("hmx/prog.hmx", ProgramLoader.relativeSource(circ, new File(tmp.toFile(), "lab/hmx/prog.hmx")));
        String outside = new File(tmp.toFile(), "other/prog.hmx").getAbsolutePath();
        assertEquals(outside, ProgramLoader.relativeSource(circ, new File(outside)));
        assertEquals(outside, ProgramLoader.relativeSource(null, new File(outside)));
        assertEquals(new File(tmp.toFile(), "lab/prog.hmx"), ProgramLoader.resolveSource(circ, "prog.hmx"));
        assertEquals(new File(outside), ProgramLoader.resolveSource(circ, outside));
    }

    /**
     * .s는 읽지 않는다(D-141): 고른 파일이나 옛 source의 다시 불러오기가 .s(.asm, 대소문자 무관)면 이미지 없이 사실과 할
     * 일 한 줄만 돌려준다. hcs-asm을 찾지 않고, 그 파일이 없어도 같다. 문장은 언어 설정을 따른다.
     */
    @Test
    void anAssemblyFileGivesTheFactAndLoadsNothing() {
        java.util.Locale old = LocaleManager.getLocale();
        try {
            LocaleManager.setLocale(java.util.Locale.ENGLISH);
            for (String name : List.of("mips/sum.s", "missing/prog.S", "lab04.asm")) {
                ProgramLoader.Loaded l = ProgramLoader.read(TESTS.resolve(name).toFile());
                assertNull(l.image, name);
                assertNull(l.check, name);
                assertEquals(List.of(AssemblySource.FACT.en), l.errors, name);
            }
            // 한국어 문장은 공용 Msg가 가진다(원조 2.7.1 언어 목록에 ko가 없어 여기서 바꿔 볼 수 없다, AssemblySourceTest)
            assertEquals(List.of(AssemblySource.FACT.get(Text.korean())),
                    ProgramLoader.read(TESTS.resolve("mips/sum.s").toFile()).errors);
        } finally {
            LocaleManager.setLocale(old);
        }
        // .hmx는 그대로 읽는다
        assertEquals(List.of(), ProgramLoader.read(TESTS.resolve("hmx/example.hmx").toFile()).errors);
    }
}
