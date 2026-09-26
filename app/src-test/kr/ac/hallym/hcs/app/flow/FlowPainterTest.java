/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** P-07 그림(GUI 없이): 흐름은 경로 위에만, 부품 글자 영역은 그대로, 다시 그리는 상자 안, 대시가 움직임. */
class FlowPainterTest {
    @TempDir
    Path tmp;

    static final double Z = 1.0;
    static final int W = 700;
    static final int H = 400;

    Circuit circuit;
    Component a;
    Component not;

    /** A ─ NOT ─ Y를 선으로 잇고, 흐름과 상관없는 선 B ─ C를 아래에 둔다. */
    void build() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        a = b.add("Wiring", "Pin", 100, 100, "label", "A");
        not = b.add("Gates", "NOT Gate", 400, 100);
        b.wire(Location.create(100, 100), not.getEnd(1).getLocation());
        b.add("Wiring", "Pin", 600, 100, "facing", "west", "output", "true", "label", "Y");
        b.wire(not.getEnd(0).getLocation(), Location.create(600, 100));
        b.add("Wiring", "Pin", 100, 300, "label", "B");
        b.add("Wiring", "Pin", 600, 300, "facing", "west", "output", "true", "label", "C");
        b.wire(Location.create(100, 300), Location.create(600, 300));
        b.commit();
        circuit = f.getMainCircuit();
    }

    BufferedImage base() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setStroke(new java.awt.BasicStroke(Wire.WIDTH));
        for (Wire w : circuit.getWires()) {
            g.drawLine(w.getEnd0().getX(), w.getEnd0().getY(), w.getEnd1().getX(), w.getEnd1().getY());
        }
        // 부품 몸체(글자 영역 대신): 회색 상자
        for (Component c : circuit.getNonWires()) {
            Bounds bb = c.getBounds();
            g.setColor(new Color(200, 200, 200));
            g.fillRect(bb.getX(), bb.getY(), bb.getWidth(), bb.getHeight());
        }
        g.dispose();
        return img;
    }

    BufferedImage frame(SignalFlowPath p, double t, boolean reduce) {
        BufferedImage img = base();
        Graphics2D g = img.createGraphics();
        g.scale(Z, Z);
        FlowPainter.paint(g, p, circuit, t, Z, reduce, null);
        g.dispose();
        return img;
    }

    static int diff(BufferedImage a, BufferedImage b, Rectangle allowed, List<Rectangle> forbidden) {
        int n = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    n++;
                    assertTrue(allowed == null || allowed.contains(x, y), "changed pixel outside " + allowed + ": "
                            + x + "," + y);
                    for (Rectangle f : forbidden) {
                        assertFalse(f.contains(x, y), "changed pixel inside " + f + ": " + x + "," + y);
                    }
                }
            }
        }
        return n;
    }

    @Test
    void flowStaysOnThePathAndOffTheUnrelatedWire() throws Exception {
        build();
        SignalFlowPath p = SignalFlowPath.fromComponent(circuit, a, -1, new SignalFlowPath.Options());
        assertEquals(List.of("OUTPUT Y"), p.endpointSummary());
        // 앞단이 A–NOT 선 가운데쯤: NOT 몸체는 아직 빛나지 않는다
        double t = 150;
        BufferedImage before = base();
        BufferedImage img = frame(p, t, false);
        Rectangle lit = new Rectangle(100 - 6, 100 - 6, 150 + 12, 12);
        Bounds nb = not.getBounds();
        Rectangle notBody = new Rectangle(nb.getX() + 3, nb.getY() + 3, nb.getWidth() - 6, nb.getHeight() - 6);
        Rectangle other = new Rectangle(100, 300 - 6, 500, 12);
        int n = diff(before, img, lit, List.of(notBody, other));
        assertTrue(n > 50, "something is drawn on the lit part: " + n);
    }

    @Test
    void everythingDrawnFitsInTheRepaintBox() throws Exception {
        build();
        SignalFlowPath p = SignalFlowPath.fromComponent(circuit, a, -1, new SignalFlowPath.Options());
        Rectangle box = FlowPainter.bounds(p, circuit);
        BufferedImage before = base();
        for (double t : new double[] {10, 200, 400, p.total + 1, p.total + 500}) {
            diff(before, frame(p, t, false), box, List.of(new Rectangle(100, 300 - 6, 500, 12)));
        }
    }

    @Test
    void dashesMoveButReduceMotionStandsStill() throws Exception {
        build();
        SignalFlowPath p = SignalFlowPath.fromComponent(circuit, a, -1, new SignalFlowPath.Options());
        double t = p.total + 100; // 연속 흐름
        BufferedImage f1 = frame(p, t, false);
        BufferedImage f2 = frame(p, t + 7, false);
        assertTrue(diff(f1, f2, null, List.of()) > 0, "the dashes moved");
        BufferedImage r1 = frame(p, t, true);
        BufferedImage r2 = frame(p, t + 7, true);
        assertEquals(0, diff(r1, r2, null, List.of()), "reduce motion: no change between frames");
        assertTrue(diff(base(), r1, null, List.of()) > 0, "reduce motion still shows arrows and numbers");
    }

    @Test
    void wireValueColorShowsBetweenTheDashes() throws Exception {
        build();
        SignalFlowPath p = SignalFlowPath.fromComponent(circuit, a, -1, new SignalFlowPath.Options());
        BufferedImage img = frame(p, p.total + 100, false);
        // 선 위(y = 100) 한 줄에서 검정(선 색)과 흰 대시가 번갈아 보인다
        int black = 0;
        int white = 0;
        for (int x = 110; x < 290; x++) {
            Color c = new Color(img.getRGB(x, 100));
            if (c.getRed() < 90 && c.getGreen() < 120 && c.getBlue() < 120) {
                black++;
            } else if (c.getRed() > 200 && c.getGreen() > 200 && c.getBlue() > 200) {
                white++;
            }
        }
        assertTrue(black > 30, "wire color visible between dashes: " + black);
        assertTrue(white > 30, "dashes visible: " + white);
    }

    @Test
    void busDashesStayWithinTheWireWidth() {
        for (int bits : new int[] {1, 2, 8, 32}) {
            for (double z : new double[] {0.5, 1, 2, 4}) {
                double w = Math.min(Wire.WIDTH - 0.6, FlowPainter.px(FlowPainter.dashWidth(bits), z));
                assertTrue(w < Wire.WIDTH, "bits " + bits + " z " + z);
            }
        }
        assertTrue(FlowPainter.dashWidth(32) > FlowPainter.dashWidth(1), "a bus is a little thicker");
    }

    /** 25%: 연속 흐름의 멈춘 그림(Layer)이 그때그때 그린 것과 같다. 끝점 칩이 상자 가장자리에서 잘리지 않는다. */
    @Test
    void lowZoomLayerKeepsWholeChips() throws Exception {
        LogisimFile demo = SignalFlowPathTest.openDemo(tmp);
        Circuit c = demo.getMainCircuit();
        SignalFlowPath p = SignalFlowPath.fromComponent(c, SignalFlowPathTest.byLabel(c, "PC", "Register"), 0,
                new SignalFlowPath.Options());
        double z = 0.25;
        int w = 480;
        int h = 300;
        BufferedImage direct = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        BufferedImage layered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g1 = direct.createGraphics();
        g1.scale(z, z);
        FlowPainter.paintParts(g1, p, c, p.total + 1, z, false, null, null, true, true);
        g1.dispose();
        Graphics2D g2 = layered.createGraphics();
        g2.scale(z, z);
        FlowPainter.paint(g2, p, c, p.total + 1, z, false, null, null);
        g2.dispose();
        int missing = 0;
        int drawn = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((direct.getRGB(x, y) >>> 24) > 0) {
                    drawn++;
                    if ((layered.getRGB(x, y) >>> 24) == 0) {
                        missing++;
                    }
                }
            }
        }
        assertTrue(drawn > 500, "something drawn: " + drawn);
        assertTrue(missing < drawn / 100, "the layer lost " + missing + " of " + drawn + " pixels");
        // 상자는 25%에서 칩이 놓일 수 있는 곳까지 넓다
        Rectangle b100 = FlowPainter.bounds(p, c, 1);
        Rectangle b25 = FlowPainter.bounds(p, c, z);
        assertTrue(b25.contains(b100) && b25.width > b100.width, b100 + " " + b25);
    }

    /** 놓을 곳이 없으면(빽빽한 낮은 배율) 다른 라벨 칩을 가리지 않고 라벨을 뺀다. 놓으면 간격을 둔다. */
    @Test
    void endpointLabelsNeverCoverOtherChips() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "r").toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component in = b.add("Wiring", "Pin", 100, 200, "label", "A");
        Component reg = b.add("Memory", "Register", 400, 200, "width", "1");
        b.commit();
        Location d = null;
        for (int i = 0; i < reg.getEnds().size(); i++) {
            Location l = reg.getEnd(i).getLocation();
            if (!reg.getEnd(i).isOutput() && (d == null || l.getX() < d.getX())) {
                d = l;
            }
        }
        b.wire(Location.create(100, 200), Location.create(250, 200));
        b.wire(Location.create(250, 200), Location.create(250, d.getY()));
        b.wire(Location.create(250, d.getY()), d);
        b.commit();
        Circuit c = f.getMainCircuit();
        SignalFlowPath p = SignalFlowPath.fromComponent(c, in, -1, new SignalFlowPath.Options());
        SignalFlowPath.Endpoint e = p.endpoints.get(0);
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // 주변 전체가 다른 라벨 칩: 놓을 곳이 없다
        List<Rectangle> full = List.of(new Rectangle(e.at.getX() - 400, e.at.getY() - 400, 800, 800));
        assertTrue(FlowPainter.layout(g, p, c, 1, full).isEmpty(), "no label rather than covering a chip");
        // 오른쪽 위 한 칸만 비었다
        java.util.Map<SignalFlowPath.Endpoint, double[]> m = FlowPainter.layout(g, p, c, 1,
                List.of(new Rectangle(e.at.getX() - 400, e.at.getY() - 20, 800, 420)));
        double[] at = m.get(e);
        assertTrue(at != null, "placed in the free space");
        BufferedImage chip = FlowPainter.chipImage(g, e.label, false, 1);
        assertTrue(at[1] + chip.getHeight() <= e.at.getY() - 20 - FlowPainter.LABEL_GAP + 0.01,
                "gap to the other chip: " + at[1]);
        g.dispose();
    }

    @Test
    void ringsGrowOutOfThickWiresWhenZoomedIn() {
        assertEquals(6.0, FlowPainter.ringRadius(6, 1), 1e-9, "unchanged at 100%");
        assertEquals(24.0, FlowPainter.ringRadius(6, 0.25), 1e-9, "screen size at 25%");
        double at400 = FlowPainter.ringRadius(6, 4) * 4; // 화면 px
        assertTrue(at400 >= 2 * 4 + 4, "outside a bus wire (half width 2) and its rail at 400%: " + at400);
    }

    /** 25%: 놓인 끝점 칩은 모두 다시 그리는 상자 안이고, 지시선은 다른 칩 위에 그려지지 않는다(P-07 4차 검토). */
    @Test
    void lowZoomChipsInsideTheBoxAndLeadersOffOtherChips() throws Exception {
        Circuit c = SignalFlowPathTest.openDemo(tmp).getMainCircuit();
        SignalFlowPath p = SignalFlowPath.fromComponent(c, SignalFlowPathTest.byLabel(c, "PC", "Register"), 0,
                new SignalFlowPath.Options());
        double z = 0.25;
        BufferedImage img = new BufferedImage(600, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.scale(z, z);
        java.util.Map<SignalFlowPath.Endpoint, double[]> places = FlowPainter.layout(g, p, c, z, null);
        Rectangle box = FlowPainter.bounds(p, c, z);
        double s = FlowPainter.deviceScale(g);
        java.util.List<java.awt.geom.Rectangle2D> chips = new java.util.ArrayList<>();
        for (java.util.Map.Entry<SignalFlowPath.Endpoint, double[]> e : places.entrySet()) {
            BufferedImage ci = FlowPainter.chipImage(g, e.getKey().label, false, z);
            java.awt.geom.Rectangle2D r = new java.awt.geom.Rectangle2D.Double(e.getValue()[0], e.getValue()[1],
                    ci.getWidth() / s, ci.getHeight() / s);
            chips.add(r);
            assertTrue(box.contains(r), e.getKey().label + " " + r + " inside " + box);
            assertTrue(r.getX() >= 0 && r.getY() >= 0, e.getKey().label + " is on the canvas (not left of 0): " + r);
        }
        assertTrue(places.size() >= 2, "several chips placed: " + places.size());
        // 지시선은 다른 끝점 칩을 가로지르지 않는다(놓을 때 피한다)
        double ring = FlowPainter.ringRadius(FlowPainter.RING_PX, z);
        for (java.util.Map.Entry<SignalFlowPath.Endpoint, double[]> e : places.entrySet()) {
            BufferedImage ci = FlowPainter.chipImage(g, e.getKey().label, false, z);
            double x = e.getValue()[0];
            double y = e.getValue()[1];
            double w = ci.getWidth() / s;
            double h = ci.getHeight() / s;
            double lx = Math.max(x, Math.min(e.getKey().at.getX(), x + w));
            double ly = Math.max(y, Math.min(e.getKey().at.getY(), y + h));
            if (Math.hypot(lx - e.getKey().at.getX(), ly - e.getKey().at.getY()) <= ring + FlowPainter.px(8, z)) {
                continue;
            }
            java.awt.geom.Line2D leader = new java.awt.geom.Line2D.Double(e.getKey().at.getX(),
                    e.getKey().at.getY(), lx, ly);
            for (java.awt.geom.Rectangle2D r : chips) {
                if (r.getX() != x || r.getY() != y) {
                    assertTrue(!leader.intersects(r), e.getKey().label + "'s leader crosses the chip " + r);
                }
            }
        }
        // 지시선을 그려도 되는 곳에는 어떤 칩도 없다
        java.awt.geom.Area leaders = FlowPainter.leaderArea(g, places, z, null);
        for (java.awt.geom.Rectangle2D r : chips) {
            assertTrue(!leaders.intersects(r.getX() + 0.5, r.getY() + 0.5, r.getWidth() - 1, r.getHeight() - 1),
                    "no leader over a chip: " + r);
        }
        g.dispose();
    }

    /**
     * V-06 (18e 장면): demo-datapath의 PC에서 시작한 흐름의 터널 점프 호는 부품 몸체(양끝 터널 제외)·끝점 라벨 칩 위를
     * 지나지 않는 후보를 고른다. 원래(위로 부푼) 호는 PC 레지스터·clk 터널·"PC (D)" 칩을 가로질렀다.
     */
    @Test
    void tunnelArcsAvoidPartsAndLabelChips() throws Exception {
        Circuit c = SignalFlowPathTest.openDemo(tmp).getMainCircuit();
        SignalFlowPath p = SignalFlowPath.fromComponent(c, SignalFlowPathTest.byLabel(c, "PC", "Register"), 0,
                new SignalFlowPath.Options());
        assertFalse(p.jumps.isEmpty(), "the pc tunnels make a jump");
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        java.util.Map<SignalFlowPath.Jump, double[]> shapes = FlowPainter.arcShapes(p, c, g, 1, null);
        java.util.Map<SignalFlowPath.Endpoint, double[]> places = FlowPainter.layout(g, p, c, 1, null);
        List<java.awt.geom.Rectangle2D> chips = new java.util.ArrayList<>();
        for (java.util.Map.Entry<SignalFlowPath.Endpoint, double[]> e : places.entrySet()) {
            BufferedImage ci = FlowPainter.chipImage(g, e.getKey().label, false, 1);
            chips.add(new java.awt.geom.Rectangle2D.Double(e.getValue()[0], e.getValue()[1], ci.getWidth(),
                    ci.getHeight()));
        }
        // 18e에서 문제였던 것들: PC 레지스터 몸체, clk 터널, "PC (D)" 등 끝점 칩
        List<java.awt.geom.Rectangle2D> mustAvoid = new java.util.ArrayList<>(chips);
        for (Component x : c.getNonWires()) {
            String f = x.getFactory().getName();
            String l = kr.ac.hallym.hcs.app.model.Names.label(x);
            if (f.equals("Register") && "PC".equals(l) || f.equals("Tunnel") && "clk".equals(l)) {
                Bounds bb = x.getBounds();
                mustAvoid.add(new Rectangle(bb.getX() - 2, bb.getY() - 2, bb.getWidth() + 4, bb.getHeight() + 4));
            }
        }
        assertTrue(mustAvoid.size() >= chips.size() + 2, "PC register and a clk tunnel found");
        int crossedByDefault = 0;
        int crossedNow = 0;
        for (SignalFlowPath.Jump j : p.jumps) {
            List<java.awt.geom.Rectangle2D> blockers = FlowPainter.blockers(j, c, null, chips);
            double[] chosen = shapes.get(j);
            int best = Integer.MAX_VALUE;
            for (int k = 0; k < FlowPainter.CANDIDATES; k++) {
                best = Math.min(best, hits(j, FlowPainter.control(j, k), blockers));
            }
            assertEquals(best, hits(j, chosen, blockers), "the least covering candidate: " + hitList(j, chosen, blockers));
            assertEquals(0, hits(j, chosen, mustAvoid), "off the PC register, clk tunnel and label chips: "
                    + hitList(j, chosen, mustAvoid));
            crossedByDefault += hits(j, FlowPainter.control(j, 0), blockers);
            crossedNow += hits(j, chosen, blockers);
        }
        assertTrue(crossedByDefault > crossedNow, "the default upward arc covered more: " + crossedByDefault + " > "
                + crossedNow);
        // 남는 겹침(pc 터널 바로 아래 상수)은 피할 수 없어 옅게 그린다: 표본 40개 중 한두 점뿐
        assertTrue(crossedNow <= 2, "only the unavoidable spot next to the tunnel remains: " + crossedNow);
        g.dispose();
    }

    static String hitList(SignalFlowPath.Jump j, double[] ctrl, List<java.awt.geom.Rectangle2D> blockers) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (int i = 1; i < 40; i++) {
            double u = i / 40.0;
            double x = (1 - u) * (1 - u) * j.from.getX() + 2 * (1 - u) * u * ctrl[0] + u * u * j.to.getX();
            double y = (1 - u) * (1 - u) * j.from.getY() + 2 * (1 - u) * u * ctrl[1] + u * u * j.to.getY();
            for (java.awt.geom.Rectangle2D r : blockers) {
                if (r.contains(x, y)) {
                    out.add(r + "@(" + Math.round(x) + "," + Math.round(y) + ")");
                }
            }
        }
        return out.toString();
    }

    static int hits(SignalFlowPath.Jump j, double[] ctrl, List<java.awt.geom.Rectangle2D> blockers) {
        int n = 0;
        for (int i = 1; i < 40; i++) {
            double u = i / 40.0;
            double x = (1 - u) * (1 - u) * j.from.getX() + 2 * (1 - u) * u * ctrl[0] + u * u * j.to.getX();
            double y = (1 - u) * (1 - u) * j.from.getY() + 2 * (1 - u) * u * ctrl[1] + u * u * j.to.getY();
            for (java.awt.geom.Rectangle2D r : blockers) {
                if (r.contains(x, y)) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    /** V-06: 위가 막히면 아래로, 둘 다 막히면 덜 가리는 쪽. 같은 입력이면 늘 같은 후보(결정적). */
    @Test
    void arcCandidatesAreChosenDeterministically() {
        SignalFlowPath.Jump j = new SignalFlowPath.Jump(List.of(), null, Location.create(100, 200),
                Location.create(300, 200), 0);
        double[] up = FlowPainter.control(j, 0);
        double[] down = FlowPainter.control(j, 1);
        assertTrue(up[1] < 200 && down[1] > 200);
        List<java.awt.geom.Rectangle2D> none = List.of();
        assertEquals(up[1], FlowPainter.chooseControl(j, none)[1], 1e-9, "nothing in the way: the default");
        java.awt.geom.Rectangle2D above = new java.awt.geom.Rectangle2D.Double(150, 100, 100, 95);
        assertEquals(down[1], FlowPainter.chooseControl(j, List.of(above))[1], 1e-9, "blocked above: below");
        java.awt.geom.Rectangle2D below = new java.awt.geom.Rectangle2D.Double(150, 205, 100, 100);
        double[] pick = FlowPainter.chooseControl(j, List.of(above, below));
        double[] again = FlowPainter.chooseControl(j, List.of(above, below));
        assertEquals(pick[0], again[0], 1e-9);
        assertEquals(pick[1], again[1], 1e-9);
        assertTrue(hits(j, pick, List.of(above, below)) <= hits(j, up, List.of(above, below)));
    }

    /** V-06: 연결 없는 출력 포트와 핀이 아닌 출력 끝은 링만(라벨 없음); 출력 Pin·순차 입력은 라벨. */
    @Test
    void onlyOutputPinsAndStateInputsGetLabels() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "l").toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component in = b.add("Wiring", "Pin", 100, 200, "label", "A");
        Component cmp = b.add("Arithmetic", "Comparator", 400, 200, "width", "1");
        b.commit();
        Location a = cmp.getEnd(0).getLocation();
        b.wire(Location.create(100, 200), Location.create(a.getX() - 20, 200));
        b.wire(Location.create(a.getX() - 20, 200), Location.create(a.getX() - 20, a.getY()));
        b.wire(Location.create(a.getX() - 20, a.getY()), a);
        b.commit();
        SignalFlowPath p = SignalFlowPath.fromComponent(f.getMainCircuit(), in, -1, new SignalFlowPath.Options());
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        java.util.Map<SignalFlowPath.Endpoint, double[]> places = FlowPainter.layout(g, p, f.getMainCircuit(), 1, null);
        boolean unconnected = false;
        for (SignalFlowPath.Endpoint e : p.endpoints) {
            if (e.kind == SignalFlowPath.EndKind.UNCONNECTED) {
                unconnected = true;
                assertFalse(FlowPainter.labelled(e), "comparator lt/eq/gt: ring only");
                assertFalse(places.containsKey(e));
            }
        }
        assertTrue(unconnected, "the comparator outputs are unconnected ends: " + p.endpoints);
        g.dispose();
    }
}
