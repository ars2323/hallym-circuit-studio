/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * S-06: 원조 부품의 포트 이름은 편집 캔버스에서 마우스를 올렸을 때나 200% 이상에서만, 부품 바깥에 그린다. 원조
 * 문맥(인쇄·내보내기)은 원조 그대로 안쪽에 그린다.
 */
class PortLabelsTest {
    @TempDir
    Path tmp;

    /** drawString을 적어 두는 Graphics(자식도 같은 목록에 적는다). */
    static final class Recorder extends DelegatingGraphics {
        final List<Object[]> texts;

        Recorder(Graphics2D g, List<Object[]> texts) {
            super(g);
            this.texts = texts;
        }

        @Override
        public void drawString(String s, int x, int y) {
            texts.add(new Object[] {s, x, y, g.getFont().getSize2D()});
            g.drawString(s, x, y);
        }

        @Override
        public Graphics create() {
            return new Recorder((Graphics2D) g.create(), texts);
        }
    }

    static Circuit circuit;
    static com.cburch.logisim.circuit.CircuitState state;

    Component[] parts() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit c = f.getMainCircuit();
        circuit = c;
        CircuitBuilder b = new CircuitBuilder(f, c);
        Component adder = b.add("Arithmetic", "Adder", 300, 200, "width", "32");
        Component reg = b.add("Memory", "Register", 300, 400, "width", "32");
        b.commit();
        com.cburch.logisim.proj.Project p = new com.cburch.logisim.proj.Project(f);
        p.getSimulator().setIsRunning(false);
        state = p.getCircuitState();
        return new Component[] {adder, reg};
    }

    static List<Object[]> draw(Component c, ComponentDrawContext ctx, List<Object[]> out) {
        c.draw(ctx);
        return out;
    }

    static List<Object[]> drawWith(Component c, double zoom, Component hovered) {
        List<Object[]> out = new ArrayList<>();
        Recorder g = new Recorder(new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB).createGraphics(), out);
        return draw(c, PortLabels.forTest(circuit, state, g, zoom, hovered), out);
    }

    static List<Object[]> drawOriginal(Component c) {
        List<Object[]> out = new ArrayList<>();
        Recorder g = new Recorder(new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB).createGraphics(), out);
        return draw(c, new ComponentDrawContext(null, circuit, state, g, g), out);
    }

    static Object[] find(List<Object[]> texts, String s) {
        for (Object[] t : texts) {
            if (s.equals(t[0])) {
                return t;
            }
        }
        return null;
    }

    @Test
    void hiddenAtNormalZoomShownOutsideAtTwoHundred() throws Exception {
        Component adder = parts()[0];
        assertTrue(find(drawOriginal(adder), "c in") != null, "the original draws c in");
        assertTrue(find(drawWith(adder, 1.0, null), "c in") == null, "hidden at 100%");
        assertTrue(find(drawWith(adder, 1.0, null), "c out") == null);
        for (List<Object[]> shown : List.of(drawWith(adder, 2.0, null), drawWith(adder, 1.0, adder))) {
            for (String name : new String[] {"c in", "c out"}) {
                Object[] t = find(shown, name);
                assertTrue(t != null, name + " shown at 200% or when hovered");
                Bounds b = adder.getBounds();
                int x = (Integer) t[1];
                int y = (Integer) t[2];
                assertFalse(x > b.getX() && x < b.getX() + b.getWidth() && y > b.getY() && y < b.getY()
                        + b.getHeight(), name + " is drawn outside the part: " + x + "," + y + " " + b);
            }
        }
    }

    @Test
    void registerValueStaysAndPortNamesMoveOut() throws Exception {
        Component reg = parts()[1];
        List<Object[]> orig = drawOriginal(reg);
        List<Object[]> ours = drawWith(reg, 4.0, null);
        Bounds b = reg.getBounds();
        int moved = 0;
        // 원조의 글자마다: 같은 자리 그대로(값, "reg" 등)이거나, 포트 이름이라 부품 바깥으로 옮겨졌다
        for (Object[] t : orig) {
            boolean same = false;
            boolean outside = false;
            for (Object[] o : ours) {
                if (!o[0].equals(t[0])) {
                    continue;
                }
                if (o[1].equals(t[1]) && o[2].equals(t[2])) {
                    same = true;
                } else {
                    int x = (Integer) o[1];
                    int y = (Integer) o[2];
                    outside |= x < b.getX() || x > b.getX() + b.getWidth() || y < b.getY()
                            || y > b.getY() + b.getHeight();
                }
            }
            assertTrue(same || outside, "kept or moved outside: " + t[0]);
            if (!same) {
                moved++;
            }
        }
        assertTrue(moved >= 2, "en and the clear 0 moved outside: " + moved);
        assertEquals(orig.size(), ours.size(), "nothing else added or removed");
    }

    @Test
    void onlyBuiltInParts() throws Exception {
        Component[] p = parts();
        assertTrue(PortLabels.original(p[0]) && PortLabels.original(p[1]));
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.resolve("s").toFile().getParentFile());
        Circuit sub = new Circuit("sub");
        f.addCircuit(sub);
        Component inst = new CircuitBuilder(f, f.getMainCircuit()).addSubcircuit(sub, 100, 100);
        assertFalse(PortLabels.original(inst), "subcircuits draw as the original");
        assertEquals(2.0, PortLabels.SHOW_ZOOM);
    }

    /** 캔버스 글꼴은 원조(Metal)와 같은 Dialog 12: 원조 부품의 값 글자 폭이 원조와 같다(S-06 검토). */
    @Test
    void canvasUsesTheOriginalFont() throws Exception {
        parts();
        com.cburch.logisim.proj.Project p = new com.cburch.logisim.proj.Project(circuit == null ? null
                : CircuitBuilder.newFile(new Loader(null), tmp.resolve("c").toFile().getParentFile()));
        p.getSimulator().setIsRunning(false);
        java.awt.Font f = new com.cburch.logisim.gui.main.Canvas(p).getFont();
        assertEquals(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12), f);
    }

    /** 아래쪽 절반의 옆 포트 이름은 포트 아래에(가운데에서 먼 쪽), 25%에서 마우스를 올리면 화면 10px 이상. */
    @Test
    void sideLabelsPointAwayAndStayReadableWhenHovered() throws Exception {
        Component reg = parts()[1];
        String en = null;
        for (Object[] t : drawOriginal(reg)) {
            if (!t[0].equals("0") && ((String) t[0]).length() <= 3 && !((String) t[0]).matches("[0-9a-f]+")) {
                en = (String) t[0];
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(en, "the enable label of the original");
        int enEnd = 4; // Register: 0 = Q, 1 = D, 2 = clk, 3 = clr, 4 = en
        com.cburch.logisim.data.Location port = reg.getEnd(enEnd).getLocation();
        Bounds b = reg.getBounds();
        assertTrue(port.getY() > b.getY() + b.getHeight() / 2, "en is in the lower half");
        Object[] t = find(drawWith(reg, 2.0, null), en);
        assertTrue((Integer) t[2] > port.getY(), "below its port (away from the D input above)");
        Object[] h = find(drawWith(reg, 0.25, reg), en);
        assertTrue((Float) h[3] * 0.25f >= PortLabels.MIN_SCREEN_PX - 0.01f, "readable at 25%: " + h[3]);
    }
}
