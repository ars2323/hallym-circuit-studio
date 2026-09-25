/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.appear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.draw.model.CanvasObject;
import com.cburch.draw.shapes.Rectangle;
import com.cburch.draw.shapes.Text;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.appear.AppearanceAnchor;
import com.cburch.logisim.circuit.appear.AppearancePort;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.Instance;

import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.regress.CircuitBuilder;
import kr.ac.hallym.hcs.regress.Engine;

/** 검토 2차 C: Auto Appearance(원조 2.7.1 표준 사용자 모양). */
class AutoAppearanceTest {
    static final File ORIGINAL_JAR = new File(System.getProperty("hcs.logisimJar"));

    @TempDir
    Path tmp;

    /** 8비트 덧셈 서브회로: 입력 A, Bvalue, Cin(서쪽), 출력 Sum(동쪽), 입력 clk(아래에서 위를 봄 → 남쪽 변). */
    Circuit adder(LogisimFile file) {
        Circuit c = new Circuit("adder8");
        file.addCircuit(c);
        CircuitBuilder b = new CircuitBuilder(file, c);
        b.input("A", 8, 100, 100);
        b.input("Bvalue", 8, 100, 200);
        b.input("Cin", 1, 100, 300);
        Component add = b.add("Arithmetic", "Adder", 400, 200, "width", "8");
        b.tunnel(add, 0, "A");
        b.tunnel(add, 1, "Bvalue");
        b.tunnel(add, 3, "Cin");
        b.tunnel(add, 2, "Sum");
        b.output("Sum", 8, 600, 200);
        b.commit();
        CircuitBuilder b2 = new CircuitBuilder(file, c);
        for (Component p : c.getNonWires()) {
            String l = kr.ac.hallym.hcs.app.model.Names.label(p);
            if (p.getFactory().getName().equals("Pin") && l != null && !l.equals("Sum")) {
                b2.tunnel(p, 0, l);
            } else if (p.getFactory().getName().equals("Pin") && "Sum".equals(l)) {
                b2.tunnel(p, 0, "Sum");
            }
        }
        b2.add("Wiring", "Pin", 300, 500, "facing", "north", "label", "clk");
        b2.commit();
        return c;
    }

    static List<AppearancePort> ports(List<CanvasObject> shapes) {
        List<AppearancePort> ret = new ArrayList<>();
        for (CanvasObject o : shapes) {
            if (o instanceof AppearancePort) {
                ret.add((AppearancePort) o);
            }
        }
        return ret;
    }

    static String pinName(AppearancePort p) {
        return AutoAppearance.portName(p.getPin());
    }

    @Test
    void boxFitsNamesAndKeepsTheDefaultSidesAndOrder() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit c = adder(file);
        Map<Location, Instance> before = c.getAppearance().getPortOffsets(Direction.EAST);
        List<CanvasObject> shapes = AutoAppearance.build(c);

        Rectangle rect = null;
        AppearanceAnchor anchor = null;
        List<String> texts = new ArrayList<>();
        for (CanvasObject o : shapes) {
            if (o instanceof Rectangle) {
                rect = (Rectangle) o;
            } else if (o instanceof AppearanceAnchor) {
                anchor = (AppearanceAnchor) o;
            } else if (o instanceof Text) {
                texts.add(((Text) o).getText());
                Bounds tb = o.getBounds();
                assertTrue(tb.getX() >= rect.getX() && tb.getX() + tb.getWidth() <= rect.getX() + rect.getWidth(),
                        "text inside the box: " + ((Text) o).getText());
            }
        }
        assertTrue(texts.containsAll(List.of("adder8", "A", "Bvalue", "Cin", "Sum", "clk")), texts.toString());

        List<AppearancePort> ports = ports(shapes);
        assertEquals(before.size(), ports.size(), "every pin keeps a port");
        List<String> west = new ArrayList<>();
        for (AppearancePort p : ports) {
            Location l = p.getLocation();
            // 모든 포트가 기준점에서 10px 격자 위(원조 인스턴스 포트가 격자에 놓인다)
            assertEquals(0, (l.getX() - anchor.getLocation().getX()) % 10);
            assertEquals(0, (l.getY() - anchor.getLocation().getY()) % 10);
            if (l.getX() == rect.getX()) {
                west.add(pinName(p));
            } else if (l.getX() == rect.getX() + rect.getWidth()) {
                assertEquals("Sum", pinName(p));
                assertEquals(l, anchor.getLocation(), "anchor on the first east port, like the default");
            } else {
                assertEquals(rect.getY() + rect.getHeight(), l.getY(), "clk on the south edge");
                assertEquals("clk", pinName(p));
            }
        }
        assertEquals(List.of("A", "Bvalue", "Cin"), west, "same order as the default appearance");
    }

    /** 되돌리기로 원래(기본) 모양, 다시 하기로 새 모양. 저장하면 원조 표준 요소만 쓴다. */
    @Test
    void undoRestoresAndSavedFileUsesStandardElements() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit c = adder(file);
        assertTrue(c.getAppearance().isDefaultAppearance());
        List<CanvasObject> shapes = AutoAppearance.build(c);
        com.cburch.logisim.proj.Action a = AutoAppearance.action(c, shapes);
        a.doIt(null);
        assertFalse(c.getAppearance().isDefaultAppearance());
        assertEquals(shapes.size(), c.getAppearance().getObjectsFromBottom().size());
        a.undo(null);
        assertTrue(c.getAppearance().isDefaultAppearance());
        a.doIt(null);

        File out = tmp.resolve("auto.circ").toFile();
        CircuitBuilder.save(file, out);
        String xml = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        String appear = xml.substring(xml.indexOf("<appear>"), xml.indexOf("</appear>"));
        for (String tag : appear.split("<")) {
            String name = tag.split("[ >/]")[0];
            assertTrue(name.isEmpty() || List.of("appear>", "appear", "rect", "text", "circ-port", "circ-anchor")
                    .contains(name), "standard 2.7.1 element: " + name);
        }
        assertTrue(appear.contains(">Bvalue</text>"), appear);
        assertFalse(xml.contains("hcs:"), "no extension data");
    }

    /** 포트 자리가 바뀌면 인스턴스 연결이 끊어진다: 미리 센다. */
    @Test
    void impactCountsConnectionsThatWouldBreak() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit c = adder(file);
        CircuitBuilder mb = new CircuitBuilder(file, file.getMainCircuit());
        Component inst = mb.addSubcircuit(c, 400, 300);
        mb.commit();
        mb = new CircuitBuilder(file, file.getMainCircuit());
        mb.tunnel(inst, index(inst, "A"), "a");
        mb.tunnel(inst, index(inst, "Sum"), "s");
        mb.commit();
        AutoAppearance.Impact none = AutoAppearance.impact(file, c, new ArrayList<>(
                c.getAppearance().getObjectsFromBottom()));
        assertEquals(0, none.connections, "same shapes: nothing breaks");
        AutoAppearance.Impact im = AutoAppearance.impact(file, c, AutoAppearance.build(c));
        assertEquals(1, im.instances);
        assertTrue(im.connections >= 1 && im.connections <= 2, "A moves; Sum is the anchor: " + im.connections);
        assertEquals(List.of("main › adder8 #1"), im.where);
    }

    static int index(Component inst, String port) {
        for (int i = 0; i < inst.getEnds().size(); i++) {
            if (Kinds.portName(inst, i).equals(port)) {
                return i;
            }
        }
        throw new IllegalArgumentException(port);
    }

    /** 원조 2.7.1이 같은 포트 자리로 읽는다: 새 모양의 포트에 이은 회로가 원조 엔진에서 맞게 돈다. */
    @Test
    void originalLogisimReadsTheSamePorts() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit c = adder(file);
        AutoAppearance.action(c, AutoAppearance.build(c)).doIt(null);
        CircuitBuilder mb = new CircuitBuilder(file, file.getMainCircuit());
        Component inst = mb.addSubcircuit(c, 400, 300);
        mb.commit();
        Bounds box = inst.getBounds();
        assertTrue(box.getWidth() >= 60, "wide enough for the names: " + box);
        mb = new CircuitBuilder(file, file.getMainCircuit());
        mb.constant("a", 8, 0x21, 100, 100);
        mb.constant("b", 8, 0x13, 100, 150);
        mb.constant("cin", 1, 1, 100, 200);
        mb.constant("clk0", 1, 0, 100, 250);
        mb.tunnel(inst, index(inst, "A"), "a");
        mb.tunnel(inst, index(inst, "Bvalue"), "b");
        mb.tunnel(inst, index(inst, "Cin"), "cin");
        mb.tunnel(inst, index(inst, "clk"), "clk0");
        mb.tunnel(inst, index(inst, "Sum"), "sum");
        mb.output("sum", 8, 800, 100);
        mb.constant("halt", 1, 1, 100, 800);
        mb.output("halt", 1, 800, 800);
        mb.commit();
        CircuitBuilder.save(file, tmp.resolve("auto.circ").toFile());
        String tty = Engine.current(ORIGINAL_JAR).run(tmp.toFile(), "auto");
        String[] lines = tty.split("\n");
        assertEquals("exit=0", lines[0], tty);
        String sum = lines[1].trim().split("\t")[0]; // halt 핀은 표에 나오지 않는다
        assertEquals(0x21 + 0x13 + 1, Integer.parseInt(sum.replace(" ", ""), 2), tty);
    }
}
