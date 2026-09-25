/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * C-05 레지스터 파일 대응(C-10 "레지스터 대응 추정"): 라벨 숫자 → 라벨 이름 → 위치 순으로 $0~$31에 대응하고, 수동
 * 대응이 그 위에 얹힌다. 표시와 수동 대응은 .circ 확장 정보로 저장되고 다시 열어도 남는다. 표시가 없으면 모든 레지스터를
 * 인스턴스 경로별로 나열한다.
 */
class RegisterFileTest {
    @TempDir
    Path tmp;

    @Test
    void labelsBecomeNumbers() {
        assertEquals(5, RegisterFile.numberOf("$5"));
        assertEquals(5, RegisterFile.numberOf("R5"));
        assertEquals(17, RegisterFile.numberOf("r17"));
        assertEquals(31, RegisterFile.numberOf("31"));
        assertEquals(8, RegisterFile.numberOf("$t0"));
        assertEquals(8, RegisterFile.numberOf("t0"));
        assertEquals(29, RegisterFile.numberOf("sp"));
        assertEquals(0, RegisterFile.numberOf("$zero"));
        assertEquals(30, RegisterFile.numberOf("$s8"));
        assertEquals(-1, RegisterFile.numberOf("$32"));
        assertEquals(-1, RegisterFile.numberOf("PC"));
        assertEquals(-1, RegisterFile.numberOf(null));
    }

    /** 라벨 숫자와 이름은 그 번호로, 라벨 없는 것은 위에서 아래로 남은 번호를 작은 것부터. */
    @Test
    void estimateUsesNumbersNamesThenPosition() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit rf = new Circuit("regs");
        file.addCircuit(rf);
        CircuitBuilder b = new CircuitBuilder(file, rf);
        Component r5 = b.add("Memory", "Register", 300, 100, "width", "32", "label", "$5");
        Component sp = b.add("Memory", "Register", 300, 200, "width", "32", "label", "sp");
        Component a = b.add("Memory", "Register", 300, 300, "width", "32");
        Component c = b.add("Memory", "Register", 300, 400, "width", "32");
        Component d = b.add("Memory", "Register", 500, 150, "width", "32", "label", "tmp");
        b.commit();
        Map<Integer, Component> m = RegisterFile.estimate(rf);
        assertSame(r5, m.get(5));
        assertSame(sp, m.get(29));
        // 위치 순(y, x): d(150) → a(300) → c(400)가 0, 1, 2
        assertSame(d, m.get(0));
        assertSame(a, m.get(1));
        assertSame(c, m.get(2));
        assertEquals(5, m.size());

        // 수동 대응: 3번을 c로, 0번은 없음. 저장하고 다시 열어도 같다
        assertNull(RegisterFile.marked(file));
        Project proj = new Project(file);
        proj.doAction(RegisterFile.markAction(file, rf, true));
        assertSame(rf, RegisterFile.marked(file));
        Map<Integer, Component> chosen = new HashMap<>(m);
        chosen.put(3, c);
        chosen.remove(2);
        chosen.put(0, null);
        proj.doAction(RegisterFile.mapAction(file, rf, chosen));
        Map<Integer, Component> after = RegisterFile.mapping(file, rf);
        assertSame(c, after.get(3));
        assertFalse(after.containsKey(2), "c moved from 2 to 3");
        assertFalse(after.containsKey(0), "0 set to none");
        assertSame(r5, after.get(5));

        File saved = new File(tmp.toFile(), "regs.circ");
        CircuitBuilder.save(file, saved);
        CircExtensions.afterSave(file, saved);
        LogisimFile again = new Loader(null).openLogisimFile(saved);
        CircExtensions.afterOpen(again, saved);
        Circuit rf2 = again.getCircuit("regs");
        assertSame(rf2, RegisterFile.marked(again));
        Map<Integer, Component> reopened = RegisterFile.mapping(again, rf2);
        assertEquals(c.getLocation(), reopened.get(3).getLocation());
        assertFalse(reopened.containsKey(0));

        // 되돌리기: 대응, 표시 순으로
        proj.undoAction();
        assertEquals(m, RegisterFile.mapping(file, rf), "back to the estimate");
        proj.undoAction();
        assertNull(RegisterFile.marked(file));
    }

    /** 표시가 없으면 모든 레지스터를 인스턴스 경로별로(데모: PC와 regfile 안 $1~$3). */
    @Test
    void withoutAMarkEveryRegisterIsListedByPath() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit sub = new Circuit("regfile");
        file.addCircuit(sub);
        CircuitBuilder sb = new CircuitBuilder(file, sub);
        sb.add("Memory", "Register", 300, 100, "width", "32", "label", "$1");
        sb.add("Memory", "Register", 300, 200, "width", "32", "label", "$2");
        sb.input("x", 1, 100, 100);
        sb.commit();
        Circuit main = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, main);
        b.add("Memory", "Register", 300, 100, "width", "32", "label", "PC");
        b.addSubcircuit(sub, 600, 400);
        b.commit();
        List<RegisterFile.Found> all = RegisterFile.all(main);
        assertEquals(3, all.size());
        assertEquals("PC", all.get(0).name);
        assertTrue(all.get(0).path.isEmpty());
        assertEquals("regfile" + Names.SEP + "$1", all.get(1).name);
        assertEquals(1, all.get(1).path.size());
        assertEquals(List.of(all.get(1).path.get(0)), RegisterFile.pathTo(main, sub));
    }
}
