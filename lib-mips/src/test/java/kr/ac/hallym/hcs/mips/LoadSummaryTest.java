/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.cburch.logisim.util.LocaleManager;

import kr.ac.hallym.hcs.mips.image.ExecutableImage;

/**
 * 불러오기 요약(검토 3차, D-049, D-126): 요약 줄은 정보 표시라 한국어 설정에서도 영어이고, 단수·복수를 가리며, 넣은
 * 영역과 양을 정확히 말한다(예: {@code 14 words (0x00400000–0x00400034), entry 0x00400024}).
 */
class LoadSummaryTest {
    static ExecutableImage small() {
        return new ExecutableImage.Builder().entry(0x00400000L).text(0x00400000L, 0x20020001)
                .data(0x10010000L, 7, 0, 0, 0, 8, 0, 0, 0).build();
    }

    @Test
    void summaryIsEnglishWithSingularAndPlural() throws Exception {
        Locale old = LocaleManager.getLocale();
        try {
            LocaleManager.setLocale(new Locale("ko"));
            boolean korean = Text.korean(); // 원조 2.7.1은 ko가 선택지에 없어 영어로 떨어질 수 있다
            InProcessSim sim = new InProcessSim();
            sim.b.add(sim.mips, "Instruction Memory", 400, 200);
            sim.b.add(sim.mips, "Data Memory", 400, 500);
            sim.b.commit();
            ExecutableImage img = small();
            List<ProgramLoader.Target> texts = ProgramLoader.find(sim.file.getCircuits(), true);
            List<ProgramLoader.Target> datas = ProgramLoader.find(sim.file.getCircuits(), false);
            ProgramLoader.Plan plan = ProgramLoader.plan(img, texts, datas, List.of(), null, null, "p.hmx");
            assertEquals(List.of(), plan.errors);
            assertEquals("entry 0x00400000", plan.notes.get(0), "Z-03: the summary starts with the entry line");
            assertEquals(1, plan.startLines);
            assertEquals(".text: 1 word (0x00400000–0x00400000), entry 0x00400000 → main › Instruction Memory"
                    + " (00400000-004fffff)", plan.notes.get(1));
            assertEquals(".data: 8 bytes = 2 words (0x10010000–0x10010007) → main › Data Memory"
                    + " (10000000-100fffff)", plan.notes.get(2)); // 새 Data Memory의 데이터 영역(D-140)
            assertEquals("Instructions used: addi", plan.notes.get(plan.notes.size() - 1));
            assertEquals(List.of("addi"), plan.instructions);
            for (String n : plan.notes) {
                assertFalse(n.matches(".*[\\uAC00-\\uD7AF].*"), n);
            }
            // 담을 부품이 없으면 오류(설명 문장이라 언어 설정을 따른다)
            ProgramLoader.Plan none = ProgramLoader.plan(img, List.of(), List.of(), List.of(), null, null, "p.hmx");
            assertEquals(korean ? List.of(".text 0x00400000–0x00400000 구간을 담는 Instruction Memory 부품이 없어"
                    + " 아무것도 불러오지 않았습니다. 회로에 Instruction Memory 부품이 없습니다.",
                    ".data 0x10010000–0x10010007 구간을 담는 Data Memory 부품이 없어 아무것도 불러오지 않았습니다."
                            + " 회로에 Data Memory 부품이 없습니다.")
                    : List.of("No Instruction Memory covers .text 0x00400000–0x00400000, so nothing was loaded."
                            + " The circuit has no Instruction Memory.",
                            "No Data Memory covers .data 0x10010000–0x10010007, so nothing was loaded."
                                    + " The circuit has no Data Memory."), none.errors);
            assertTrue(none.changes.isEmpty());
        } finally {
            LocaleManager.setLocale(old);
        }
    }

    /** Z-01의 예: 명세 예시 이미지의 요약 줄. */
    @Test
    void summaryStatesTheExactRegionAndAmount() throws Exception {
        ProgramLoader.Loaded l = ProgramLoader.read(AssemblerIntegrationTest.TESTS.resolve("hmx/example.hmx").toFile());
        assertEquals(List.of(), l.errors);
        assertEquals("Executable image example.hmx, Hallym MIPS 2.2.0, 2026-09-27T13:15+09:00", l.notes.get(0));
        InProcessSim sim = new InProcessSim();
        sim.b.add(sim.mips, "Instruction Memory", 400, 200);
        sim.b.add(sim.mips, "Data Memory", 400, 500);
        sim.b.add(sim.mips, "Stack", 400, 800);
        sim.b.commit();
        ProgramLoader.Plan plan = ProgramLoader.plan(l, sim.file.getCircuits(), null, null, "example.hmx");
        assertEquals(List.of(), plan.errors);
        // Z-03: entry 줄, reg 줄(파일 순서), 그다음 어디서 온 이미지인가
        assertEquals(List.of("entry 0x00400024 (main)", "reg $sp 0x7ffff000", "reg $gp 0x10008000",
                "Executable image example.hmx, Hallym MIPS 2.2.0, 2026-09-27T13:15+09:00"), plan.notes.subList(0, 4));
        String all = String.join("\n", plan.notes);
        assertTrue(all.contains(".text: 14 words (0x00400000–0x00400034), entry 0x00400024 →"), all);
        assertTrue(all.contains(".data: 12 bytes = 3 words (0x10010000–0x1001000b) →"), all);
        assertTrue(all.contains("$sp 0x7ffff000: Stack depth base of main › Stack"), all);
        assertEquals(1, plan.facts.size(), "source check line");
        assertTrue(plan.warnings.isEmpty());
        // 요약 창 글: 노란 줄은 바탕색 div
        ProgramLoader.Loaded changed = ProgramLoader.read(
                AssemblerIntegrationTest.TESTS.resolve("hmx/source-changed.hmx").toFile());
        ProgramLoader.Plan warn = ProgramLoader.plan(changed, sim.file.getCircuits(), null, null, "x.hmx");
        assertEquals(1, warn.warnings.size());
        String html = LoadProgramMenu.summaryHtml(warn);
        assertTrue(html.contains("<div style='background-color:" + LoadProgramMenu.WARN_BACKGROUND), html);
        assertTrue(html.contains("example.s"), html);
        // 실행 이미지의 데이터(entry, 레지스터 시작 값, 기호)는 글이 아니라 값으로도 있다
        assertEquals(0x00400024L, (long) plan.image.entry());
        assertEquals(0x10008000L, (long) plan.image.reg("$gp"));
        assertEquals(1, plan.stackBase.size());
    }

    /**
     * Z-03(D-138): 진입 루틴의 jr $ra, 예외 처리기 없이 어셈블한 이미지는 사실 줄(설명 문장이라 언어 설정을 따른다)이다.
     * 요약 머리 줄은 두 언어 모두 영어다. 레지스터에는 아무것도 넣지 않는다(바뀌는 것은 메모리 부품 속성뿐).
     */
    @Test
    void startFactsAreFactLines() throws Exception {
        Locale old = LocaleManager.getLocale();
        try {
            for (Locale loc : new Locale[] {Locale.ENGLISH, new Locale("ko")}) {
                LocaleManager.setLocale(loc);
                boolean korean = Text.korean();
                InProcessSim sim = new InProcessSim();
                sim.b.add(sim.mips, "Instruction Memory", 400, 200);
                sim.b.commit();
                int jal = 0x0c100009; // 0x00400014: jal main
                ExecutableImage jr = new ExecutableImage.Builder().entry(0x00400024L).reg("$sp", 0x7fffeffcL)
                        .symbol("main", 0x00400024L).text(0x00400000L, 0, 0, 0, 0, 0, jal, 0, 0, 0x0000000c,
                                0x20080001, 0x03e00008).build();
                ProgramLoader.Plan plan = ProgramLoader.plan(jr, ProgramLoader.find(sim.file.getCircuits(), true),
                        List.of(), List.of(), null, null, "jr.hmx");
                assertEquals(List.of(), plan.errors);
                assertEquals(List.of("entry 0x00400024 (main)", "reg $sp 0x7fffeffc"), plan.notes.subList(0, 2));
                assertEquals(1, plan.facts.size(), plan.facts.toString());
                assertEquals(korean ? "진입 루틴의 jr $ra: 0x00400028. 회로에서는 시작 코드가 돌지 않으므로 $ra 값은 회로가 주는"
                        + " 값입니다." : "jr $ra in the entry routine: 0x00400028. The startup code does not run in the"
                        + " circuit, so $ra is whatever the circuit gives.", plan.facts.get(0));
                for (ProgramLoader.Change ch : plan.changes) {
                    assertTrue(ch.target.component.getFactory() instanceof MemoryFactory, "only memory parts change");
                }
                // 예외 처리기 없이 어셈블한 이미지(Hallym MIPS no-handler.hmx): 처리기 사실 줄, jr $ra 줄은 없다
                ProgramLoader.Loaded nh = ProgramLoader.read(AssemblerIntegrationTest.TESTS.resolve(
                        "hmx/hallym-mips-v2.4.0/no-handler.hmx").toFile());
                ProgramLoader.Plan noHandler = ProgramLoader.plan(nh, sim.file.getCircuits(), null, null, "nh.hmx");
                assertEquals(List.of(), noHandler.errors);
                assertEquals("entry 0x00400000 (__start)", noHandler.notes.get(0));
                assertEquals(korean ? "예외 처리기 없이 어셈블한 이미지: 시작 코드 없음, 진입점 = 프로그램의 __start."
                        : "Assembled without the exception handler: no start-up code, entry = the program's own __start.",
                        noHandler.facts.get(0));
                assertEquals(2, noHandler.facts.size(), "then the source check line");
                // 요약 창 글: 머리 줄, 요약 줄, 사실 줄 순
                String html = LoadProgramMenu.summaryHtml(noHandler);
                assertTrue(html.startsWith("<html>entry 0x00400000 (__start)<br>reg $sp 0x7fffffe4<br>"), html);
            }
        } finally {
            LocaleManager.setLocale(old);
        }
    }

    /** 트랙 A 우클릭 메뉴: "Load Program...", 불러온 뒤 "Reload 파일 이름", 파일 고르기 거르개(.hmx, 전환용 .s). */
    @Test
    void menuIsLoadProgramWithImageAndTransitionFilters() throws Exception {
        InProcessSim sim = new InProcessSim();
        com.cburch.logisim.comp.Component im = sim.b.add(sim.mips, "Instruction Memory", 400, 200);
        sim.b.commit();
        Object ext = im.getFeature(com.cburch.logisim.tools.MenuExtender.class);
        assertTrue(ext instanceof LoadProgramMenu);
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        ((LoadProgramMenu) ext).configureMenu(menu, null);
        List<String> items = new java.util.ArrayList<>();
        for (java.awt.Component c : menu.getComponents()) {
            if (c instanceof javax.swing.JMenuItem) {
                items.add(((javax.swing.JMenuItem) c).getText());
            }
        }
        assertEquals(List.of("Load Program..."), items);
        im.getAttributeSet().setValue(MemoryFactory.SOURCE, "asm/lab04.hmx");
        javax.swing.JPopupMenu again = new javax.swing.JPopupMenu();
        ((LoadProgramMenu) im.getFeature(com.cburch.logisim.tools.MenuExtender.class)).configureMenu(again, null);
        assertEquals("Reload lab04.hmx", ((javax.swing.JMenuItem) again.getComponent(2)).getText());
        assertEquals("Program", MemoryFactory.SOURCE.getDisplayName());

        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        javax.swing.filechooser.FileNameExtensionFilter hmx = new javax.swing.filechooser.FileNameExtensionFilter(
                "Executable image (*.hmx)", "hmx");
        chooser.setFileFilter(hmx);
        AssemblyTransition.addFilters(chooser, hmx);
        java.io.File dir = AssemblerIntegrationTest.TESTS.toFile();
        javax.swing.filechooser.FileFilter both = chooser.getFileFilter();
        assertTrue(both.accept(new java.io.File(dir, "hmx/example.hmx")) && both.accept(new java.io.File(dir, "mips/sum.s")),
                "the default filter shows .hmx and .s during the transition");
        assertTrue(hmx.accept(new java.io.File(dir, "hmx/example.hmx")));
        assertFalse(hmx.accept(new java.io.File(dir, "mips/sum.s")));
        List<String> names = new java.util.ArrayList<>();
        for (javax.swing.filechooser.FileFilter f : chooser.getChoosableFileFilters()) {
            names.add(f.getDescription());
        }
        assertTrue(names.contains("Executable image (*.hmx)") && names.contains("MIPS assembly, transition (*.s, *.asm)"),
                names.toString());
    }

    @Test
    void countsUseSingularForOne() {
        assertEquals("1 word", Text.count(1, "word"));
        assertEquals("0 words", Text.count(0, "word"));
        assertEquals("27 words", Text.count(27, "word"));
        assertEquals("1 byte", ExecutableImage.count(1, "byte"));
    }

    @Test
    void errorListIsCut() {
        List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            many.add("e" + i);
        }
        String t = LoadProgramMenu.errorText(many);
        assertTrue(t.startsWith("e0\ne1"), t);
        assertFalse(t.contains("e12"), t);
        assertTrue(t.endsWith("3 more") || t.endsWith("3개"), t);
    }
}
