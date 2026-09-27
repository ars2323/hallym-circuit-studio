/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;

import kr.ac.hallym.hcs.mips.image.AssemblySource;
import kr.ac.hallym.hcs.mips.image.ExecutableImage;
import kr.ac.hallym.hcs.mips.image.HmxParser;
import kr.ac.hallym.hcs.mips.image.LoadReport;

/**
 * v2 엔진의 불러오기 입구(N-16, D-147): 트랙 A와 같은 읽기·계획의 결과를 공용 {@link LoadReport}로 옮긴다. 고를 것이
 * 남으면 후보만 적고 멈추고, 고른 부품을 주면 이어서 정하고, 문제는 두 언어와 줄 번호로 준다. 다시 불러오기는 그
 * {@code source}를 가진 부품을 고르고, 고를 수 없으면 문제 하나로 바꾼다.
 */
class ProgramLoadingTest {
    static final File DATA = ProgramLoadIntegrationTest.TESTS.resolve("hmx/hallym-mips-v2.4.0/data.hmx").toFile();

    @TempDir
    Path tmp;

    @Test
    void aReportCarriesThePlanOfTheTrackAMenu() throws Exception {
        InProcessSim sim = new InProcessSim();
        Component im = sim.b.add(sim.mips, "Instruction Memory", 400, 200);
        Component dm = sim.b.add(sim.mips, "Data Memory", 400, 500);
        sim.b.commit();
        File circ = ProgramLoadIntegrationTest.TESTS.resolve("lab.circ").toFile();
        LoadReport r = ProgramLoading.load(DATA, circ, sim.file.getCircuits(), null, null, null);
        assertTrue(r.ok(), r.problems.toString());
        assertEquals("hmx/hallym-mips-v2.4.0/data.hmx", r.source, "the track A attribute, relative to the .circ");
        assertEquals(ExecutableImage.Kind.TEXT, r.placements.get(0).kind);
        assertSame(im, r.placements.get(0).component);
        assertSame(sim.file.getMainCircuit(), r.placements.get(0).circuit);
        assertEquals("main › Instruction Memory (00400000-004fffff)", r.placements.get(0).name);
        assertEquals(ExecutableImage.Kind.DATA, r.placements.get(1).kind);
        assertSame(dm, r.placements.get(1).component);
        assertEquals(List.of(dm), r.stackBase, "reg $sp: the merged Data Memory's depth base");
        assertNull(r.emptied);
        assertNotNull(r.check);
        assertTrue(r.instructions.contains("syscall"));
        assertEquals("entry 0x00400024 (main)", r.notes.get(0));
        // 바꿀 것: 두 부품의 contents와 source(트랙 A와 같은 속성)
        Set<Object> attrs = new HashSet<>();
        for (LoadReport.Change ch : r.changes) {
            attrs.add(ch.attribute);
            assertTrue(ch.component == im || ch.component == dm);
        }
        assertEquals(Set.of(MemoryFactory.CONTENTS, MemoryFactory.SOURCE), attrs);
        // 아무것도 바꾸지 않았다
        assertTrue(ProgramLoading.contents(im).isEmpty());
        ExecutableImage img = HmxParser.read(DATA).image;
        for (LoadReport.Change ch : r.changes) {
            if (ch.component == im && ch.attribute == MemoryFactory.CONTENTS) {
                assertEquals(img.textWords(), ((WordImage) ch.value).words());
            }
        }
        assertNull(ProgramLoading.contents(sim.b.add(sim.mips, "Console", 900, 300)), "not a memory");
    }

    @Test
    void severalMemoriesStopWithTheCandidatesAndAPickGoesOn() throws Exception {
        InProcessSim sim = new InProcessSim();
        Component a = sim.b.add(sim.mips, "Instruction Memory", 400, 200, "label", "A");
        Component b = sim.b.add(sim.mips, "Instruction Memory", 400, 500, "label", "B");
        sim.b.add(sim.mips, "Data Memory", 900, 300);
        sim.b.commit();
        LoadReport asked = ProgramLoading.load(DATA, null, sim.file.getCircuits(), null, null, Map.of());
        assertFalse(asked.ok());
        assertTrue(asked.problems.isEmpty(), "a choice is not a problem");
        assertEquals(ExecutableImage.Kind.TEXT, asked.choice.kind);
        assertEquals(".text 0x00400000–0x00400068", asked.choice.segment);
        assertEquals(Set.of(a, b), new HashSet<>(asked.choice.components));
        assertEquals(2, asked.choice.circuits.size());
        assertTrue(asked.choice.names.contains("main › A (00400000-004fffff)"), asked.choice.names.toString());
        assertTrue(asked.changes.isEmpty());

        LoadReport picked = ProgramLoading.load(DATA, null, sim.file.getCircuits(), null, null, Map.of("text", b));
        assertTrue(picked.ok());
        assertSame(b, picked.placements.get(0).component);
        assertEquals(DATA.getAbsolutePath(), picked.source, "no .circ yet: an absolute path");
        // 우클릭한 부품이 있으면 묻지 않는다
        LoadReport clicked = ProgramLoading.load(DATA, null, sim.file.getCircuits(), sim.file.getMainCircuit(), a, null);
        assertTrue(clicked.ok());
        assertSame(a, clicked.placements.get(0).component);

        // 다시 불러오기: 그 source를 가진 부품. 없으면 문제 하나(묻지 않는다)
        a.getAttributeSet().setValue(MemoryFactory.SOURCE, "p/data.hmx");
        LoadReport again = ProgramLoading.reload(DATA, null, sim.file.getCircuits(), "p/data.hmx");
        assertTrue(again.ok());
        assertSame(a, again.placements.get(0).component);
        LoadReport lost = ProgramLoading.reload(DATA, null, sim.file.getCircuits(), "other.hmx");
        assertFalse(lost.ok());
        assertNull(lost.choice);
        assertEquals(1, lost.problems.size());
        assertEquals(".text 0x00400000–0x00400068 구간을 담는 Instruction Memory 부품이 여럿이라 다시 불러오지 않았습니다."
                + " Load Program… 단추로 불러오세요.", lost.problems.get(0).text.ko);
    }

    @Test
    void problemsComeInTwoLanguagesWithTheirLines() throws Exception {
        InProcessSim sim = new InProcessSim();
        sim.b.add(sim.mips, "Instruction Memory", 400, 200, "base", "0x0");
        sim.b.commit();
        LoadReport truncated = ProgramLoading.load(ProgramLoadIntegrationTest.TESTS.resolve("hmx/truncated.hmx")
                .toFile(), null, sim.file.getCircuits(), null, null, null);
        assertFalse(truncated.ok());
        assertNull(truncated.image);
        LoadReport.Problem p = truncated.problems.get(0);
        assertTrue(p.line > 0);
        assertTrue(p.text.ko.startsWith(p.line + "번째 줄: "), p.text.ko);
        assertTrue(p.text.en.startsWith("Line " + p.line + ": "), p.text.en);
        // 담는 부품이 없음: 줄 번호 없는 문장, 아무것도 바꾸지 않음
        LoadReport none = ProgramLoading.load(DATA, null, sim.file.getCircuits(), null, null, null);
        assertFalse(none.ok());
        assertNotNull(none.image, "the file was read");
        assertEquals(0, none.problems.get(0).line);
        assertTrue(none.problems.get(0).text.ko.contains("구간을 담는 Instruction Memory 부품이 없어"), none.problems.toString());
        assertTrue(none.changes.isEmpty() && none.placements.isEmpty());
        // .s는 이미지 없이 사실과 할 일(D-141)
        LoadReport s = ProgramLoading.load(ProgramLoadIntegrationTest.TESTS.resolve("mips/sum.s").toFile(), null,
                sim.file.getCircuits(), null, null, null);
        assertEquals(AssemblySource.FACT, s.problems.get(0).text);
    }
}
