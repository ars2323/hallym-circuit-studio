/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** 검토 반영 1: 우클릭 메뉴는 요약 → 대상별 → 공통 → 삭제 순서이고, 맨 위 한 줄이 대상을 요약한다. */
class MenuLayoutTest {
    @TempDir
    Path tmp;

    private static List<String> texts(JPopupMenu m) {
        List<String> ret = new ArrayList<>();
        for (java.awt.Component c : m.getComponents()) {
            if (c instanceof JPopupMenu.Separator) {
                ret.add("--");
            } else if (c instanceof JLabel) {
                ret.add("[" + ((JLabel) c).getText() + "]");
            } else if (c instanceof JMenuItem) {
                ret.add(((JMenuItem) c).getText());
            }
        }
        return ret;
    }

    @Test
    void orderIsSummarySpecificCommonDelete() {
        List<java.awt.Component> original = new ArrayList<>();
        original.add(MenuLayout.group(new JMenuItem("삭제"), MenuLayout.DELETE));
        original.add(new JMenuItem(".s 불러오기")); // 부품 자체 항목(MenuExtender)
        List<java.awt.Component> ours = new ArrayList<>();
        ours.add(MenuLayout.group(new JMenuItem("복제"), MenuLayout.COMMON));
        ours.add(new JMenuItem("in1에 붙이기"));
        ours.add(new JPopupMenu.Separator());
        ours.add(MenuLayout.group(new JMenuItem("속성 패널에서 보기"), MenuLayout.COMMON));
        ours.add(new JMenuItem("입력 수"));
        JPopupMenu m = MenuLayout.arrange("AND #1 · 입력 in1 · 1비트", Arrays.asList(original, ours));
        assertEquals(Arrays.asList("[AND #1 · 입력 in1 · 1비트]", "--", ".s 불러오기", "--", "in1에 붙이기", "입력 수",
                "--", "복제", "속성 패널에서 보기", "--", "삭제"), texts(m));
    }

    @Test
    void summariesNameTheTarget() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit c = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, c);
        Component and = b.add("Gates", "AND Gate", 300, 200, "inputs", "2");
        Component pin = b.add("Wiring", "Pin", 100, 400, "width", "32", "label", "aluResult");
        b.wire(Location.create(100, 400), Location.create(200, 400));
        Component reg = b.add("Memory", "Register", 500, 300, "width", "8", "label", "PC");
        b.commit();
        Wire w = null;
        for (Wire x : c.getWires()) {
            w = x;
        }
        String bits1 = Messages.get("menu.sum.bits", 1);
        Location in1 = and.getEnds().get(2).getLocation();
        assertEquals("AND #1 · " + Messages.get("menu.sum.in", "in1") + " · " + bits1,
                MenuLayout.summary(c, and, in1, 1));
        assertEquals("AND #1 · " + Messages.get("menu.sum.inputs", 2) + " · " + bits1,
                MenuLayout.summary(c, and, Location.create(280, 200), 1));
        assertEquals(Messages.get("menu.sum.net", "aluResult") + " · " + Messages.get("menu.sum.bits", 32),
                MenuLayout.summary(c, w, Location.create(150, 400), 1));
        String r = MenuLayout.summary(c, reg, Location.create(480, 300), 1);
        assertTrue(r.startsWith("PC(") && r.endsWith(Messages.get("menu.sum.bits", 8)), r);
        assertEquals(Messages.get("menu.sum.empty", "main"), MenuLayout.summary(c, null, Location.create(0, 0), 0));
        assertEquals(Messages.get("menu.sum.many", 3), MenuLayout.summary(c, and, Location.create(280, 200), 3));
        assertTrue(pin != null);
    }
}
