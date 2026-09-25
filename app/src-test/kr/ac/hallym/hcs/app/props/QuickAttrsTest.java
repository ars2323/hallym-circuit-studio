/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.props;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.SetAttributeAction;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.LocaleManager;

import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #74: 빠른 속성 등록표, 편집 Action, 숨은 단축키. */
class QuickAttrsTest {
    @TempDir
    Path tmp;

    private static List<String> names(List<QuickAttrs.Entry> es) {
        List<String> ret = new ArrayList<>();
        for (QuickAttrs.Entry e : es) {
            ret.add(e.name());
        }
        return ret;
    }

    /** 등록표의 빠른 속성 이름은 모두 원조 부품에 실제로 있는 속성이다(오타가 조용히 빠지지 않게). */
    @Test
    void registryNamesExistOnTheOriginalComponents() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        int checked = 0;
        for (Library lib : file.getLibraries()) {
            for (Tool t : lib.getTools()) {
                if (!(t instanceof AddTool)) {
                    continue;
                }
                ComponentFactory f = ((AddTool) t).getFactory();
                Kinds.Kind k = Kinds.of(f);
                if (k.category() == Kinds.Category.OTHER) {
                    continue;
                }
                AttributeSet as = f.createAttributeSet();
                for (String name : k.quickAttrs()) {
                    assertNotNull(as.getAttribute(name), f.getName() + " has no attribute " + name);
                }
                checked++;
            }
        }
        assertTrue(checked > 40, "checked " + checked);
    }

    @Test
    void entriesFollowTheRegistryWithOriginalOptions() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component and = b.add("Gates", "AND Gate", 300, 200, "inputs", "3");
        Component reg = b.add("Memory", "Register", 500, 300, "width", "8", "label", "PC");
        b.commit();

        List<QuickAttrs.Entry> es = QuickAttrs.entries(and);
        assertEquals(Arrays.asList("inputs", "width", "size", "facing", "label"), names(es));
        assertEquals("3", es.get(0).value);
        assertFalse(es.get(0).options.isEmpty(), "inputs has a list in the original editor");
        assertEquals("2", es.get(0).options.get(0)[0]);
        assertEquals(32, es.get(1).options.size(), "widths 1..32");
        assertTrue(es.get(4).options.isEmpty(), "label is free text");

        List<QuickAttrs.Entry> rs = QuickAttrs.entries(reg);
        assertEquals(Arrays.asList("width", "trigger", "label"), names(rs));
        assertEquals("PC", rs.get(2).value);
        assertTrue(rs.size() <= QuickAttrs.MAX);

        assertEquals(Collections.singletonList(and), QuickAttrs.targets(Collections.singletonList(and)));
        assertTrue(QuickAttrs.targets(Arrays.asList(and, reg)).isEmpty(), "mixed kinds");
    }

    /** 제자리 라벨 편집과 빠른 속성은 원조 속성 표와 같은 Action(이름·부품·값)을 만든다. */
    @Test
    void editsAreTheAttributeTableAction() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component reg = b.add("Memory", "Register", 500, 300, "width", "8", "label", "PC");
        Component and = b.add("Gates", "AND Gate", 300, 200);
        Component and2 = b.add("Gates", "AND Gate", 300, 300);
        b.commit();
        Project proj = new Project(file);
        String tableName = new LocaleManager("resources/logisim", "gui").get("selectionAttributeAction");

        assertTrue(QuickAttrs.unchanged(Collections.singletonList(reg), QuickAttrs.labelAttr(reg), "PC"));
        assertFalse(QuickAttrs.unchanged(Collections.singletonList(reg), QuickAttrs.labelAttr(reg), "NextPC"));
        SetAttributeAction label = QuickAttrs.labelAction(file.getMainCircuit(), reg, "NextPC");
        assertEquals(tableName, label.getName());
        label.doIt(proj);
        assertEquals("NextPC", reg.getAttributeSet().getValue(QuickAttrs.labelAttr(reg)));
        label.undo(proj);
        assertEquals("PC", reg.getAttributeSet().getValue(QuickAttrs.labelAttr(reg)));

        // UI가 쓰는 글자 경로도 원조 속성 표처럼 글자를 그대로 넘긴다(앞뒤 공백 포함)
        SetAttributeAction typed = QuickAttrs.parse(file.getMainCircuit(), Collections.singletonList(reg),
                QuickAttrs.labelAttr(reg), " PC 2 ");
        typed.doIt(proj);
        assertEquals(" PC 2 ", reg.getAttributeSet().getValue(QuickAttrs.labelAttr(reg)));
        assertTrue(QuickAttrs.unchanged(Collections.singletonList(reg), QuickAttrs.labelAttr(reg), " PC 2 "));
        assertFalse(QuickAttrs.unchanged(Collections.singletonList(reg), QuickAttrs.labelAttr(reg), "PC 2"));
        typed.undo(proj);

        QuickAttrs.Entry inputs = QuickAttrs.entries(and).get(0);
        Object before = and2.getAttributeSet().getValue(inputs.attr);
        SetAttributeAction set = QuickAttrs.parse(file.getMainCircuit(), Arrays.asList(and, and2), inputs.attr, "4");
        assertEquals(tableName, set.getName());
        set.doIt(proj);
        assertEquals(Integer.valueOf(4), and.getAttributeSet().getValue(inputs.attr));
        assertEquals(Integer.valueOf(4), and2.getAttributeSet().getValue(inputs.attr));
        set.undo(proj);
        assertEquals(before, and2.getAttributeSet().getValue(inputs.attr));

        assertThrows(IllegalArgumentException.class,
                () -> QuickAttrs.parse(file.getMainCircuit(), Collections.singletonList(and), inputs.attr, "99"));
    }

    @Test
    void labelsOnlyWhereTheOriginalHasThem() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component adder = b.add("Arithmetic", "Adder", 300, 200);
        Component pin = b.add("Wiring", "Pin", 100, 200);
        b.commit();
        assertNull(QuickAttrs.labelAttr(adder));
        assertNotNull(QuickAttrs.labelAttr(pin));
    }

    /** 숨은 단축키는 원조 KeyConfigurator가 실제로 바꾸는 속성이다. */
    @Test
    void hiddenKeysComeFromTheOriginalConfigurator() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component and = b.add("Gates", "AND Gate", 300, 200);
        Component reg = b.add("Memory", "Register", 500, 300);
        Component adder = b.add("Arithmetic", "Adder", 300, 400);
        b.commit();

        List<QuickAttrs.Hint> h = QuickAttrs.hints(and);
        assertEquals(2, h.size());
        assertEquals("0–9", h.get(0).keys);
        assertEquals("inputs", h.get(0).attr.getName());
        assertEquals("Alt+0–9", h.get(1).keys);
        assertEquals("width", h.get(1).attr.getName());

        List<QuickAttrs.Hint> r = QuickAttrs.hints(reg);
        assertEquals(1, r.size());
        assertEquals("Alt+0–9", r.get(0).keys);
        assertEquals("width", r.get(0).attr.getName());

        assertEquals("width", QuickAttrs.hints(adder).get(0).attr.getName());
    }

    /** 우클릭(메뉴)으로는 빠른 속성 창을 숨기지 않는다: 놓기가 메뉴로 가서 다시 보일 기회가 없다. */
    @Test
    void onlyLeftDragHidesTheQuickBar() {
        javax.swing.JPanel src = new javax.swing.JPanel();
        java.awt.event.MouseEvent left = new java.awt.event.MouseEvent(src, java.awt.event.MouseEvent.MOUSE_PRESSED,
                0L, java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 5, 5, 1, false, java.awt.event.MouseEvent.BUTTON1);
        java.awt.event.MouseEvent right = new java.awt.event.MouseEvent(src, java.awt.event.MouseEvent.MOUSE_PRESSED,
                0L, java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 5, 5, 1, true, java.awt.event.MouseEvent.BUTTON3);
        assertTrue(QuickBar.hidesWhilePressed(left));
        assertFalse(QuickBar.hidesWhilePressed(right));
    }

    /** 빠른 속성 창은 라벨 칩·다른 부품·선택 테두리를 덮지 않는 자리(위→아래→오른쪽→왼쪽)에 놓인다(검토 반영 1). */
    @Test
    void quickBarAvoidsChipsAndComponents() {
        java.awt.Rectangle target = new java.awt.Rectangle(400, 300, 60, 40);
        java.awt.Dimension bar = new java.awt.Dimension(300, 40);
        java.awt.Rectangle vis = new java.awt.Rectangle(0, 0, 1200, 800);
        java.util.List<java.awt.Rectangle> none = new java.util.ArrayList<>();
        assertEquals(new java.awt.Rectangle(400, 254, 300, 40), QuickBar.placement(target, bar, none, vis, 6),
                "above when free");

        java.util.List<java.awt.Rectangle> chipAbove = new java.util.ArrayList<>();
        chipAbove.add(new java.awt.Rectangle(410, 270, 40, 16)); // 레지스터 라벨 칩
        java.awt.Rectangle below = QuickBar.placement(target, bar, chipAbove, vis, 6);
        assertEquals(346, below.y, "below when the label chip sits above");
        assertFalse(below.intersects(chipAbove.get(0)));

        java.util.List<java.awt.Rectangle> both = new java.util.ArrayList<>(chipAbove);
        both.add(new java.awt.Rectangle(380, 350, 200, 60)); // 아래 부품
        java.awt.Rectangle right = QuickBar.placement(target, bar, both, vis, 6);
        assertEquals(466, right.x, "to the right when above and below are taken");
        for (java.awt.Rectangle a : both) {
            assertFalse(right.intersects(a));
        }

        java.awt.Rectangle topEdge = new java.awt.Rectangle(400, 10, 60, 40);
        java.awt.Rectangle p = QuickBar.placement(topEdge, bar, none, vis, 6);
        assertFalse(p.intersects(topEdge), "never on top of the selection");
    }
}
