/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** W-04: 이어진 점은 큰 점, 이어지지 않은 교차는 반원 점프. 크기는 화면 기준이라 25~400%에서 보인다. */
class WireMarksTest {
    @TempDir
    Path tmp;

    LogisimFile fresh() throws Exception {
        return CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
    }

    /** 가로 A→Y 넷을 세로 B→C 넷이 끝점 없이 가로지르고, 가로 넷에서 T자로 D가 갈라진다. */
    Circuit crossAndTee(LogisimFile f) {
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        b.add("Wiring", "Pin", 100, 200, "label", "A");
        b.add("Wiring", "Pin", 400, 200, "facing", "west", "output", "true", "label", "Y");
        b.wire(Location.create(100, 200), Location.create(400, 200));
        b.add("Wiring", "Pin", 200, 100, "facing", "south", "label", "B");
        b.add("Wiring", "Pin", 200, 300, "facing", "north", "output", "true", "label", "C");
        b.wire(Location.create(200, 100), Location.create(200, 300));
        b.add("Wiring", "Pin", 300, 300, "facing", "north", "output", "true", "label", "D");
        b.wire(Location.create(300, 200), Location.create(300, 300));
        b.commit();
        return f.getMainCircuit();
    }

    @Test
    void crossingIsAJumpAndTeeIsADot() throws Exception {
        Circuit c = crossAndTee(fresh());
        assertEquals(List.of(Location.create(200, 200)), WireMarks.crossings(c));
        assertEquals(List.of(Location.create(300, 200)), WireMarks.junctions(c));
    }

    @Test
    void aPortOnTheMiddleOfAWireGetsADot() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        b.add("Wiring", "Pin", 100, 200, "label", "A");
        b.add("Wiring", "Pin", 400, 200, "facing", "west", "output", "true", "label", "Y");
        b.wire(Location.create(100, 200), Location.create(400, 200));
        // 프로브의 포트가 선 한가운데
        b.add("Wiring", "Probe", 250, 200, "facing", "south");
        b.commit();
        List<Location> j = WireMarks.junctions(f.getMainCircuit());
        assertTrue(j.contains(Location.create(250, 200)), j.toString());
        assertEquals(List.of(), WireMarks.crossings(f.getMainCircuit()));
    }

    @Test
    void demoCircuitMarksAreFixed() throws Exception {
        Path dir = Files.createTempDirectory(tmp, "demo");
        Files.copy(new File(System.getProperty("hcs.mipsJar")).toPath(), dir.resolve("hcs-mips.jar"),
                StandardCopyOption.REPLACE_EXISTING);
        Path circ = dir.resolve("demo-datapath.circ");
        Files.copy(new File(System.getProperty("hcs.circDir"), "demo-datapath.circ").toPath(), circ);
        LogisimFile f = new Loader(null).openLogisimFile(circ.toFile());
        Circuit c = f.getMainCircuit();
        List<Location> j = WireMarks.junctions(c);
        List<Location> x = WireMarks.crossings(c);
        assertEquals(DEMO_JUNCTIONS, j.size(), j.toString());
        assertEquals(DEMO_CROSSINGS, x.size(), x.toString());
        // 두 번 셈해도 같은 순서(결정성)
        assertEquals(j, WireMarks.junctions(c));
        assertEquals(x, WireMarks.crossings(c));
        // 점프 자리는 연결점이 아니다
        for (Location p : x) {
            assertTrue(!j.contains(p), p.toString());
        }
    }

    static final int DEMO_JUNCTIONS = 4;
    static final int DEMO_CROSSINGS = 1;

    // ---- 그리기 ----

    static final int PAD = 20;

    /** 배율 z로 회로와 표시를 그린 그림(인쇄 보기 색: 검정). */
    static BufferedImage render(Circuit c, double z, boolean marks) {
        int w = (int) Math.ceil((420 + PAD) * z);
        int h = (int) Math.ceil((320 + PAD) * z);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.scale(z, z);
        g.setColor(Color.BLACK);
        g.setStroke(new java.awt.BasicStroke(Wire.WIDTH));
        for (Wire wire : c.getWires()) {
            g.drawLine(wire.getEnd0().getX(), wire.getEnd0().getY(), wire.getEnd1().getX(), wire.getEnd1().getY());
        }
        if (marks) {
            WireMarks.paint(g, c, null, null, z);
        }
        g.dispose();
        return img;
    }

    static boolean dark(BufferedImage img, double x, double y, double z) {
        int px = (int) Math.round(x * z);
        int py = (int) Math.round(y * z);
        Color col = new Color(img.getRGB(px, py));
        return col.getRed() + col.getGreen() + col.getBlue() < 3 * 128;
    }

    @Test
    void jumpBreaksTheHorizontalWireAndArcsOverWhereItCanBeSeen() throws Exception {
        Circuit c = crossAndTee(fresh());
        for (double z : new double[] {0.5, 1.0, 2.0, 4.0}) {
            BufferedImage before = render(c, z, false);
            BufferedImage img = render(c, z, true);
            float r = WireMarks.jumpRadius(z);
            assertTrue(r > 0 && r <= WireMarks.JUMP_MAX, "z=" + z + " r=" + r);
            // 원조: 교차점 양옆 가로선이 이어져 있다
            assertTrue(dark(before, 200 - r / 2, 200, z), "z=" + z);
            // 점프: 가로선이 끊기고(세로선 옆 빈자리), 반원 꼭대기가 칠해진다
            assertTrue(!dark(img, 200 - r / 2 - 1, 200, z), "gap left of the vertical wire, z=" + z);
            assertTrue(dark(img, 200, 200 - r, z), "arc top, z=" + z);
            assertTrue(dark(img, 200, 200, z), "vertical wire stays, z=" + z);
        }
    }

    /**
     * ui-reviewer(#245): 25%에서는 반원이 옆 연결점(격자 두 칸 위)에 닿아 이어진 것처럼 보였다. 반지름은 격자 반 칸
     * 미만이고, 작아서 안 보이는 배율에서는 원조처럼 평범한 십자로 둔다(연결은 큰 점으로만 구분).
     */
    @Test
    void jumpNeverReachesTheNextGridPointAndIsLeftOutWhenTooSmall() throws Exception {
        for (double z = 0.1; z <= 8; z += 0.05) {
            float r = WireMarks.jumpRadius(z);
            assertTrue(r < 10, "z=" + z);
            if (r > 0) {
                assertTrue(r * z >= WireMarks.JUMP_VISIBLE_PX, "visible when drawn, z=" + z);
            }
        }
        assertEquals(0f, WireMarks.jumpRadius(0.25));
        Circuit c = crossAndTee(fresh());
        BufferedImage before = render(c, 0.25, false);
        BufferedImage img = render(c, 0.25, true);
        for (int y = (int) (180 * 0.25); y < (int) (220 * 0.25); y++) {
            for (int x = (int) (180 * 0.25); x < (int) (220 * 0.25); x++) {
                assertEquals(before.getRGB(x, y), img.getRGB(x, y), "plain cross at 25%: " + x + "," + y);
            }
        }
    }

    /** 값 색: 반원은 가로선의 색(상수 1 = 밝은 초록), 끊긴 자리를 지나는 세로선은 세로선의 색(떠 있음 = 파랑). */
    @Test
    void jumpKeepsEachWiresValueColor() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        b.add("Wiring", "Constant", 100, 200, "value", "0x1");
        b.add("Wiring", "Pin", 400, 200, "facing", "west", "output", "true", "label", "Y");
        b.wire(Location.create(100, 200), Location.create(400, 200));
        b.add("Wiring", "Pin", 200, 100, "facing", "south", "output", "true", "label", "B");
        b.add("Wiring", "Pin", 200, 300, "facing", "north", "output", "true", "label", "C");
        b.wire(Location.create(200, 100), Location.create(200, 300));
        b.commit();
        Circuit c = f.getMainCircuit();
        com.cburch.logisim.proj.Project proj = new com.cburch.logisim.proj.Project(f);
        proj.getSimulator().setIsRunning(false);
        com.cburch.logisim.circuit.CircuitState st = proj.getCircuitState();
        st.getPropagator().propagate();
        double z = 4.0;
        BufferedImage img = new BufferedImage(1800, 1400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.scale(z, z);
        WireMarks.paint(g, c, st, null, z);
        g.dispose();
        float r = WireMarks.jumpRadius(z);
        Color arc = new Color(img.getRGB((int) Math.round(200 * z), (int) Math.round((200 - r) * z)));
        Color stub = new Color(img.getRGB((int) Math.round(200 * z), (int) Math.round((200 - r / 2) * z)));
        assertEquals(com.cburch.logisim.data.Value.TRUE_COLOR, arc, "arc takes the horizontal wire's value color");
        assertEquals(com.cburch.logisim.data.Value.UNKNOWN_COLOR, stub, "the vertical wire keeps its own color");
    }

    @Test
    void junctionDotIsAtLeastSevenScreenPixels() throws Exception {
        Circuit c = crossAndTee(fresh());
        for (double z : new double[] {0.25, 0.5, 1.0, 2.0, 4.0}) {
            BufferedImage img = render(c, z, true);
            float d = Math.max(WireMarks.DOT_MIN, WireMarks.px(WireMarks.DOT_PX, z));
            assertTrue(d * z >= WireMarks.DOT_PX - 0.01, "z=" + z);
            // T 위쪽(가로선보다 위)의 잉크 높이를 잰다: 선 반 굵기(1.5 회로 px)가 아니라 점 반지름만큼 올라와야 한다
            int cx = (int) Math.round(300 * z);
            int cy = (int) Math.round(200 * z);
            int top = cy;
            while (top > 0 && isInk(img, cx, top - 1)) {
                top--;
            }
            double extent = cy - top;
            assertTrue(extent >= d * z / 2 - 1.5, "dot radius on screen at z=" + z + ": " + extent);
            assertTrue(extent >= 2, "dot visible above the wire at z=" + z);
        }
    }

    static boolean isInk(BufferedImage img, int x, int y) {
        Color col = new Color(img.getRGB(x, y));
        return col.getRed() + col.getGreen() + col.getBlue() < 3 * 200;
    }

    // ---- 넷 강조 ----

    @Test
    void highlightFollowsTheNetAndClearsWhenTheNetChanges() throws Exception {
        Circuit c = crossAndTee(fresh());
        Wire any = null;
        for (Wire w : c.getWires()) {
            if (w.getEnd0().getY() == 300 || w.getEnd1().getY() == 300) {
                if (w.getEnd0().getX() == 300) {
                    any = w;
                }
            }
        }
        Netlist.Net net = Netlist.of(c).netOf(any);
        WireMarks.highlight(c, net);
        assertEquals(net.wires().size(), WireMarks.highlighted(c).size());
        CircuitMutation m = new CircuitMutation(c);
        m.remove(any);
        m.execute();
        assertEquals(0, WireMarks.highlighted(c).size(), "editing the net clears the highlight");
    }

    /** "Delete Net Wires": 넷의 선만 지우고(터널·핀은 남김) 되돌리기 한 번에 돌아온다. */
    @Test
    void deleteNetWiresRemovesOnlyThatNetsWires() throws Exception {
        LogisimFile f = fresh();
        Circuit c = crossAndTee(f);
        com.cburch.logisim.proj.Project proj = new com.cburch.logisim.proj.Project(f);
        proj.getSimulator().setIsRunning(false);
        Wire vertical = null;
        for (Wire w : c.getWires()) {
            if (w.isVertical() && w.getEnd0().getX() == 200) {
                vertical = w;
            }
        }
        java.util.Set<String> geo = SafeMoveTest.geometry(c);
        int parts = c.getNonWires().size();
        Netlist.Net net = Netlist.of(c).netOf(vertical);
        proj.doAction(kr.ac.hallym.hcs.app.edit.CircuitEdits.deleteNetWires(c, net).toAction(() -> "t"));
        for (Wire w : c.getWires()) {
            assertTrue(!(w.isVertical() && w.getEnd0().getX() == 200), "the B–C wire is gone: " + w);
        }
        assertTrue(c.getWires().stream().anyMatch(w -> !w.isVertical()), "the A–Y wire stays");
        assertEquals(parts, c.getNonWires().size(), "pins stay");
        proj.undoAction();
        assertEquals(geo, SafeMoveTest.geometry(c), "one undo restores it");
    }
}
