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

/** 검토 3차: .s 불러오기 요약은 정보 표시라 한국어 설정에서도 영어이고, 단수·복수를 가린다(D-049). */
class LoadSummaryTest {
    static final String JSON = "{\"settings\":{},"
            + "\"text\":[{\"addr\":\"0x00400000\",\"word\":\"0x20020001\",\"line\":1,\"source\":\"addi $v0, $0, 1\"}],"
            + "\"data\":[{\"addr\":\"0x10010000\",\"word\":\"0x7\"},{\"addr\":\"0x10010004\",\"word\":\"0x8\"}],"
            + "\"labels\":{},\"errors\":[],\"warnings\":[]}";

    @Test
    void summaryIsEnglishWithSingularAndPlural() throws Exception {
        Locale old = LocaleManager.getLocale();
        try {
            LocaleManager.setLocale(new Locale("ko"));
            InProcessSim sim = new InProcessSim();
            sim.b.add(sim.mips, "Instruction Memory", 400, 200);
            sim.b.add(sim.mips, "Data Memory", 400, 500);
            sim.b.commit();
            AssembledProgram prog = AssembledProgram.fromJson(JSON);
            List<ProgramLoader.Target> texts = ProgramLoader.find(sim.file.getCircuits(), true);
            List<ProgramLoader.Target> datas = ProgramLoader.find(sim.file.getCircuits(), false);
            ProgramLoader.Plan plan = ProgramLoader.plan(prog, texts.get(0), datas.get(0), "p.s");
            assertTrue(plan.notes.get(0).startsWith("1 word .text → main › Instruction Memory"), plan.notes.toString());
            assertTrue(plan.notes.get(1).startsWith("2 words .data → main › Data Memory"), plan.notes.toString());
            assertEquals("Instructions used: addi", plan.notes.get(plan.notes.size() - 1));
            ProgramLoader.Plan none = ProgramLoader.plan(prog, null, null, "p.s");
            assertTrue(none.notes.contains("No Instruction Memory for .text"), none.notes.toString());
            assertTrue(none.notes.contains("No Data Memory for .data"), none.notes.toString());
            for (String n : plan.notes) {
                assertFalse(n.matches(".*[\\uAC00-\\uD7AF].*"), n);
            }
            for (String n : none.notes) {
                assertFalse(n.matches(".*[\\uAC00-\\uD7AF].*"), n);
            }
        } finally {
            LocaleManager.setLocale(old);
        }
    }

    @Test
    void countsUseSingularForOne() {
        assertEquals("1 word", Text.count(1, "word"));
        assertEquals("0 words", Text.count(0, "word"));
        assertEquals("27 words", Text.count(27, "word"));
    }
}
