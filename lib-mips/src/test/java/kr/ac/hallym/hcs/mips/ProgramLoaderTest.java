/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.util.LocaleManager;

import kr.ac.hallym.hcs.mips.image.ExecutableImage;

/**
 * 불러오기 계획(ProgramLoader, D-126, D-138)의 규칙 하나하나: 담는 부품 고르기, 영역 경계, 담지 못할 때의 문구, 빈 Data
 * Memory, Stack 깊이 기준, 경로. Z-24의 돌연변이 테스트 대상이다. 문구는 영어 설정으로 본다.
 */
class ProgramLoaderTest {
    @TempDir
    Path tmp;

    private Locale old;

    @BeforeEach
    void english() {
        old = LocaleManager.getLocale();
        LocaleManager.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void restore() {
        LocaleManager.setLocale(old);
    }

    static ExecutableImage.Builder image() {
        return new ExecutableImage.Builder().entry(0x00400000L).text(0x00400000L, 0x20080001, 0x0000000c);
    }

    static ProgramLoader.Plan plan(ExecutableImage img, InProcessSim sim, ProgramLoader.Target clicked,
            ProgramLoader.Chooser chooser) {
        List<Circuit> cs = sim.file.getCircuits();
        return ProgramLoader.plan(img, ProgramLoader.find(cs, true), ProgramLoader.find(cs, false),
                ProgramLoader.findStacks(cs), clicked, chooser, "p.hmx");
    }

    static Object change(ProgramLoader.Plan plan, Component c, Object attr) {
        Object out = null;
        for (ProgramLoader.Change ch : plan.changes) {
            if (ch.target.component == c && ch.attr == attr) {
                out = ch.value;
            }
        }
        return out;
    }

    // ---- 영역 ----

    @Test
    void targetRegionBoundaries() throws Exception {
        InProcessSim sim = new InProcessSim();
        Component im = sim.b.add(sim.mips, "Instruction Memory", 400, 200, "base", "0x400000", "size", "0x10");
        Component dm = sim.b.add(sim.mips, "Data Memory", 400, 500, "label", "DM");
        sim.b.commit();
        Circuit main = sim.file.getMainCircuit();
        ProgramLoader.Target t = new ProgramLoader.Target(main, im);
        assertTrue(t.contains(0x00400000L));
        assertTrue(t.contains(0x0040000cL));
        assertFalse(t.contains(0x00400010L));
        assertFalse(t.contains(0x003ffffcL));
        // 구간 전체가 영역 안: 끝이 영역 끝과 같아도 된다
        assertTrue(t.covers(seg(0x00400000L, 4)));
        assertFalse(t.covers(seg(0x00400000L, 5)));
        assertFalse(t.covers(seg(0x003ffffcL, 1)));
        assertTrue(t.covers(seg(0x0040000cL, 1)));
        // 빈 구간: 시작 주소가 영역 안(끝 포함)
        assertTrue(t.covers(seg(0x00400010L, 0)));
        assertTrue(t.covers(seg(0x00400000L, 0)));
        assertFalse(t.covers(seg(0x00400014L, 0)));
        assertFalse(t.covers(seg(0x003ffffcL, 0)));
        // 이름: 라벨이 있으면 라벨
        assertEquals("main › Instruction Memory (00400000-0040000f)", t.describe());
        assertEquals(t.describe(), t.toString());
        assertEquals("main › DM (10010000-1010ffff)", new ProgramLoader.Target(main, dm).describe());
        // 같은 부품이면 같은 대상
        ProgramLoader.Target again = new ProgramLoader.Target(main, im);
        assertEquals(t, again);
        assertEquals(t.hashCode(), again.hashCode());
        assertNotEquals(t, new ProgramLoader.Target(main, dm));
        assertNotEquals(t, "not a target");
    }

    static ExecutableImage.Segment seg(long start, int words) {
        return new ExecutableImage.Builder().text(start, new int[words]).build().segments().get(0);
    }

    // ---- 고르기 ----

    @Test
    void choosingAmongSeveralMemories() throws Exception {
        InProcessSim sim = new InProcessSim();
        Component a = sim.b.add(sim.mips, "Instruction Memory", 400, 200, "label", "A");
        Component b = sim.b.add(sim.mips, "Instruction Memory", 400, 500, "label", "B");
        sim.b.commit();
        Circuit main = sim.file.getMainCircuit();
        // 구간이 둘이면 두 번째에는 앞에서 고른 부품을 다시 쓴다(묻지 않는다)
        ExecutableImage two = image().text(0x00400100L, 1).build();
        List<String> asked = new ArrayList<>();
        ProgramLoader.Plan plan = plan(two, sim, null, (cands, what) -> {
            asked.add(what);
            return cands.get(cands.get(0).component == b ? 0 : 1); // B
        });
        assertEquals(List.of(), plan.errors);
        assertEquals(List.of(".text 0x00400000–0x00400004"), asked);
        assertEquals(1, plan.text.size());
        assertSame(b, plan.text.keySet().iterator().next().component);
        assertEquals(2, plan.text.values().iterator().next().size());
        // 고를 사람이 없으면 첫 후보
        ProgramLoader.Plan first = plan(image().build(), sim, null, null);
        assertEquals(List.of(), first.errors);
        assertEquals(1, first.text.size());
        // 우클릭한 부품이 담으면 그 부품(묻지 않는다)
        ProgramLoader.Plan clicked = plan(image().build(), sim, new ProgramLoader.Target(main, a), (c, w) -> {
            throw new AssertionError("asked");
        });
        assertSame(a, clicked.text.keySet().iterator().next().component);
        assertNotNull(change(clicked, a, MemoryFactory.CONTENTS));
        assertNull(change(clicked, b, MemoryFactory.CONTENTS), "the other memory is not touched");
        // 우클릭한 부품이 Data Memory면 .text는 담는 부품을 찾는다
        InProcessSim both = new InProcessSim();
        Component im = both.b.add(both.mips, "Instruction Memory", 400, 200);
        Component dm = both.b.add(both.mips, "Data Memory", 400, 500);
        both.b.commit();
        ProgramLoader.Plan fromData = plan(image().data(0x10010000L, 1).build(), both,
                new ProgramLoader.Target(both.file.getMainCircuit(), dm), null);
        assertEquals(List.of(), fromData.errors);
        assertSame(im, fromData.text.keySet().iterator().next().component);
        assertSame(dm, fromData.data.keySet().iterator().next().component);
        WordImage put = (WordImage) change(fromData, dm, MemoryFactory.CONTENTS);
        assertEquals(1, put.size());
        assertEquals(1, put.read(0x10010000));
        assertEquals("p.hmx", change(fromData, im, MemoryFactory.SOURCE));
    }

    @Test
    void noMemoryCoversTheSegment() throws Exception {
        InProcessSim sim = new InProcessSim();
        sim.b.add(sim.mips, "Instruction Memory", 400, 200, "base", "0x0");
        sim.b.add(sim.mips, "Instruction Memory", 400, 500, "base", "0x0", "label", "IM2");
        sim.b.commit();
        ProgramLoader.Plan plan = plan(image().build(), sim, null, null);
        // 후보는 회로에서 찾은 순서로 잇는다(부품 순서는 정해져 있지 않다)
        List<String> names = new ArrayList<>();
        for (ProgramLoader.Target t : ProgramLoader.find(sim.file.getCircuits(), true)) {
            names.add(t.describe());
        }
        assertEquals(List.of("main › IM2 (00000000-000fffff)", "main › Instruction Memory (00000000-000fffff)"),
                names.stream().sorted().toList());
        String have = String.join(", ", names);
        assertEquals(List.of("No Instruction Memory covers .text 0x00400000–0x00400004, so nothing was loaded."
                + " Instruction Memory: " + have + "."), plan.errors);
        assertTrue(plan.changes.isEmpty());
        assertTrue(plan.text.isEmpty());
        // 한국어 문장(이름은 영어). 원조 2.7.1의 언어 설정에는 ko가 없을 수 있어 두 벌을 직접 본다
        List<ProgramLoader.Target> ims = ProgramLoader.find(sim.file.getCircuits(), true);
        ExecutableImage.Segment text = image().build().segments().get(0);
        assertEquals(".text 0x00400000–0x00400004 구간을 담는 Instruction Memory 부품이 없어 아무것도 불러오지 않았습니다."
                + " 이 파일의 Instruction Memory 부품: " + have + ".", ProgramLoader.noMemory(text, ims,
                "Instruction Memory").ko);
        assertEquals(".text 0x00400000–0x00400004 구간을 담는 Instruction Memory 부품이 없어 아무것도 불러오지 않았습니다."
                + " 회로에 Instruction Memory 부품이 없습니다.", ProgramLoader.noMemory(text, List.of(), "Instruction Memory").ko);
        assertEquals(plan.errors.get(0), ProgramLoader.noMemory(text, ims, "Instruction Memory").en);
        // 부품이 아예 없음
        ProgramLoader.Plan none = ProgramLoader.plan(image().data(0x10010000L, 1).build(), List.of(), List.of(),
                List.of(), null, null, "p.hmx");
        assertEquals(List.of("No Instruction Memory covers .text 0x00400000–0x00400004, so nothing was loaded."
                + " The circuit has no Instruction Memory.", "No Data Memory covers .data 0x10010000–0x10010000, so"
                + " nothing was loaded. The circuit has no Data Memory."), none.errors);
        // .text는 담지만 .data를 담지 못하면 아무것도 바꾸지 않는다(전부 아니면 전무)
        InProcessSim half = new InProcessSim();
        half.b.add(half.mips, "Instruction Memory", 400, 200);
        half.b.add(half.mips, "Data Memory", 400, 500, "base", "0x0");
        half.b.commit();
        ProgramLoader.Plan halfPlan = plan(image().data(0x10010000L, 1).build(), half, null, null);
        assertEquals(1, halfPlan.errors.size());
        assertTrue(halfPlan.changes.isEmpty());
        assertTrue(halfPlan.text.isEmpty() && halfPlan.data.isEmpty());
        // 고르기를 취소하면 "아무것도 불러오지 않았습니다"
        InProcessSim twoIm = new InProcessSim();
        twoIm.b.add(twoIm.mips, "Instruction Memory", 400, 200);
        twoIm.b.add(twoIm.mips, "Instruction Memory", 400, 500);
        twoIm.b.commit();
        ProgramLoader.Plan cancelled = plan(image().build(), twoIm, null, (c, w) -> null);
        assertEquals(List.of("Nothing was loaded."), cancelled.errors);
        assertTrue(cancelled.changes.isEmpty());
    }

    // ---- .data 없음, 저장된 깊이 기준 ----

    @Test
    void programWithoutDataEmptiesItsDataMemory() throws Exception {
        InProcessSim sim = new InProcessSim();
        sim.b.add(sim.mips, "Instruction Memory", 400, 200);
        Component dm = sim.b.add(sim.mips, "Data Memory", 400, 500);
        sim.b.commit();
        dm.getAttributeSet().setValue(MemoryFactory.CONTENTS, WordImage.parse("hcs-words 1\n10010000 00000007\n"));
        ProgramLoader.Plan plan = plan(image().build(), sim, null, null);
        assertSame(dm, plan.emptiedData.component);
        assertTrue(((WordImage) change(plan, dm, MemoryFactory.CONTENTS)).isEmpty());
        assertTrue(plan.notes.contains(".data: none (emptied main › Data Memory (10010000-1010ffff))"), plan.notes.toString());
        // 우클릭한 Data Memory가 둘 중 하나면 그것을 비운다
        Component dm2 = sim.b.add(sim.mips, "Data Memory", 400, 800, "label", "D2");
        sim.b.commit();
        ProgramLoader.Plan two = plan(image().build(), sim, new ProgramLoader.Target(sim.file.getMainCircuit(), dm2), null);
        assertSame(dm2, two.emptiedData.component);
        ProgramLoader.Plan unclear = plan(image().build(), sim, null, null);
        assertNull(unclear.emptiedData, "two Data Memories and none clicked: nothing is emptied");
        assertTrue(unclear.notes.contains(".data: none"), unclear.notes.toString());
        // 내용을 새로 넣어도 부품에 적힌 Stack 깊이 기준(주소만 있는 줄)은 남긴다
        Component im = ProgramLoader.find(sim.file.getCircuits(), true).get(0).component;
        im.getAttributeSet().setValue(MemoryFactory.CONTENTS, WordImage.EMPTY.withInitialSp(0x7fffeffcL));
        ProgramLoader.Plan keep = plan(image().build(), sim, null, null);
        assertEquals(0x7fffeffcL, (long) ((WordImage) change(keep, im, MemoryFactory.CONTENTS)).initialSp());
    }

    @Test
    void regSpIsTheDepthBaseOfTheStackThatHoldsTheWordBelowIt() throws Exception {
        InProcessSim sim = new InProcessSim();
        sim.b.add(sim.mips, "Instruction Memory", 400, 200);
        Component st = sim.b.add(sim.mips, "Stack", 400, 500); // 0x7ff00000..0x7fffffff
        sim.b.commit();
        // $sp가 영역 끝(0x80000000)이면 바로 아래 워드 0x7ffffffc가 영역 안이다
        ProgramLoader.Plan top = plan(image().reg("$sp", 0x80000000L).build(), sim, null, null);
        assertEquals(List.of(), top.errors);
        assertEquals(1, top.stackBase.size());
        assertEquals(0x80000000L, (long) ((WordImage) change(top, st, MemoryFactory.CONTENTS)).initialSp());
        assertTrue(top.notes.contains("$sp 0x80000000: Stack depth base of main › Stack (7ff00000-7fffffff)"),
                top.notes.toString());
        // 바로 아래 워드가 영역 밖이면 기준이 아니다
        ProgramLoader.Plan below = plan(image().reg("$sp", 0x7ff00000L).build(), sim, null, null);
        assertTrue(below.stackBase.isEmpty());
        assertNull(change(below, st, MemoryFactory.CONTENTS), "nothing to change");
        // 기준이 있던 Stack에 reg $sp 없는 이미지를 넣으면 기준을 지운다
        st.getAttributeSet().setValue(MemoryFactory.CONTENTS, WordImage.EMPTY.withInitialSp(0x7fffeffcL));
        ProgramLoader.Plan cleared = plan(image().build(), sim, null, null);
        assertNull(((WordImage) change(cleared, st, MemoryFactory.CONTENTS)).initialSp());
        assertTrue(cleared.stackBase.isEmpty());
        // 레지스터에는 아무것도 넣지 않는다: 바뀌는 것은 메모리 부품의 속성뿐이다
        for (ProgramLoader.Change ch : top.changes) {
            assertTrue(ch.target.component.getFactory() instanceof MemoryFactory);
        }
    }

    // ---- 파일 ----

    @Test
    void readingFailures() throws Exception {
        ProgramLoader.Loaded missing = ProgramLoader.read(tmp.resolve("none.hmx").toFile());
        assertFalse(missing.ok());
        assertNull(missing.image);
        assertTrue(missing.errors.get(0).startsWith("Cannot read the file none.hmx: "), missing.errors.toString());
        Path bad = Files.writeString(tmp.resolve("bad.hmx"), "HALLYM-EXEC 1\nendian little\n");
        ProgramLoader.Loaded broken = ProgramLoader.read(bad.toFile());
        assertFalse(broken.ok());
        assertNull(broken.image);
        assertEquals(2, broken.errors.size(), broken.errors.toString());
        ProgramLoader.Loaded good = ProgramLoader.read(AssemblerIntegrationTest.TESTS.resolve("hmx/example.hmx").toFile());
        assertTrue(good.ok());
        good.errors.add("later");
        assertFalse(good.ok(), "an error makes it not ok even with an image");
        ProgramLoader.Loaded plain = ProgramLoader.read(AssemblerIntegrationTest.TESTS.resolve("hmx/comments.hmx").toFile());
        assertEquals(List.of("Executable image comments.hmx"), plain.notes, "no produced-by, no assembled");
    }

    @Test
    void sourcePathsRelativeToTheCircuit() throws Exception {
        File circ = tmp.resolve("lab/cpu.circ").toFile();
        File s = tmp.resolve("lab/asm/lab04.hmx").toFile();
        assertEquals("asm/lab04.hmx", ProgramLoader.relativeSource(circ, s));
        assertEquals(s.getAbsolutePath(), ProgramLoader.relativeSource(null, s));
        assertEquals(s.getAbsolutePath(), ProgramLoader.relativeSource(new File("/"), s), "no folder to be relative to");
        File outside = tmp.resolve("other/lab05.hmx").toFile();
        assertEquals(outside.getAbsolutePath(), ProgramLoader.relativeSource(circ, outside), "not under the folder");
        assertEquals(s.getAbsoluteFile(), ProgramLoader.resolveSource(circ, "asm/lab04.hmx").getAbsoluteFile());
        assertEquals(s.getAbsolutePath(), ProgramLoader.resolveSource(circ, s.getAbsolutePath()).getPath());
        assertEquals("asm/lab04.hmx", ProgramLoader.resolveSource(null, "asm/lab04.hmx").getPath().replace('\\', '/'));
        assertEquals("x.hmx", ProgramLoader.resolveSource(new File("/"), "x.hmx").getPath(), "no folder");
    }
}
