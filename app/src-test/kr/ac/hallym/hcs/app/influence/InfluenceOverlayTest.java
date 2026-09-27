/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.influence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.model.Influence;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** P-01 표시: 닿은 선에는 앞(파랑) 띠, 나머지는 흐리게, 회로를 고치면 지움, "n places" 단수·복수. */
class InfluenceOverlayTest {
    @TempDir
    Path tmp;

    @Test
    void placesUseSingularForOne() {
        assertEquals("alu: 1 place", kr.ac.hallym.hcs.app.Messages.get("influence.places", "alu", 1));
        assertEquals("alu: 3 places", kr.ac.hallym.hcs.app.Messages.get("influence.places", "alu", 3));
    }

    @Test
    void reachedWiresGetABandAndTheRestIsDimmed() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component a = b.add("Wiring", "Pin", 100, 100, "label", "A");
        Component not = b.add("Gates", "NOT Gate", 300, 100);
        b.wire(Location.create(100, 100), not.getEnd(1).getLocation());
        b.add("Wiring", "Pin", 500, 100, "facing", "west", "output", "true", "label", "Y");
        b.wire(not.getEnd(0).getLocation(), Location.create(500, 100));
        // 닿지 않는 넷
        b.add("Wiring", "Pin", 100, 300, "label", "B");
        b.add("Wiring", "Pin", 500, 300, "facing", "west", "output", "true", "label", "C");
        b.wire(Location.create(100, 300), Location.create(500, 300));
        b.commit();
        Circuit c = f.getMainCircuit();
        Influence inf = Influence.of(c, List.of(a), Influence.Mode.FORWARD, false, -1);
        Influence.View v = inf.view(c);
        assertEquals(2, v.forwardWires.size(), v.forwardWires.toString());

        BufferedImage img = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 600, 400);
        g.setColor(Color.BLACK);
        g.setStroke(new java.awt.BasicStroke(Wire.WIDTH));
        for (Wire w : c.getWires()) {
            g.drawLine(w.getEnd0().getX(), w.getEnd0().getY(), w.getEnd1().getX(), w.getEnd1().getY());
        }
        g.setClip(0, 0, 600, 400);
        InfluenceOverlay.paint(g, null, c, v, 1.0);
        g.dispose();
        // 닿은 선 바로 위(선 굵기 밖, 띠 안): 파랑 기운
        Color band = new Color(img.getRGB(150, 100 - 3));
        assertTrue(band.getBlue() > band.getRed() + 40, "forward band on the reached wire: " + band);
        // 닿지 않은 선은 흐리게(검정이 회색으로)
        Color dim = new Color(img.getRGB(300, 300));
        assertTrue(dim.getRed() > 120, "unreached wire is dimmed: " + dim);
    }

    @Test
    void editingTheCircuitClearsIt() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component a = b.add("Wiring", "Pin", 100, 100, "label", "A");
        b.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "label", "Y");
        b.wire(Location.create(100, 100), Location.create(300, 100));
        b.commit();
        Project proj = new Project(f);
        proj.getSimulator().setIsRunning(false);
        Circuit c = f.getMainCircuit();
        InfluenceOverlay o = InfluenceOverlay.of(proj);
        o.show(c, List.of(a), Influence.Mode.FORWARD);
        assertNotNull(o.viewFor(c));
        CircuitMutation m = new CircuitMutation(c);
        m.add(Wire.create(Location.create(100, 200), Location.create(200, 200)));
        m.execute();
        assertNull(o.viewFor(c), "an edit clears the influence");
        assertTrue(!o.active());
    }

    @Test
    void widenAndNarrowStepThroughTheDepths() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component a = b.input("A", 1, 100, 100);
        Component n1 = b.add("Gates", "NOT Gate", 300, 100);
        b.tunnel(n1, 1, "A");
        b.tunnel(n1, 0, "x");
        Component n2 = b.add("Gates", "NOT Gate", 500, 100);
        b.tunnel(n2, 1, "x");
        b.tunnel(n2, 0, "y");
        Component n3 = b.add("Gates", "NOT Gate", 700, 100);
        b.tunnel(n3, 1, "y");
        b.tunnel(n3, 0, "Z");
        b.output("Z", 1, 900, 100);
        b.commit();
        Project proj = new Project(f);
        proj.getSimulator().setIsRunning(false);
        InfluenceOverlay o = InfluenceOverlay.of(proj);
        o.show(f.getMainCircuit(), List.of(a), Influence.Mode.FORWARD);
        assertEquals(-1, o.depth(), "all steps first");
        o.widen(-1);
        assertEquals(2, o.depth());
        o.widen(-1);
        assertEquals(1, o.depth());
        o.widen(-1);
        assertEquals(1, o.depth(), "never below one step");
        o.widen(1);
        o.widen(1);
        assertEquals(-1, o.depth(), "back to all steps");
    }

    /** ui-reviewer #246: 터널 사이 점선은 사이에 놓인 부품 몸체(글자) 위를 지나지 않는다. */
    @Test
    void tunnelLinkSkipsPartBodies() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component a = b.add("Wiring", "Pin", 100, 200, "label", "A");
        b.add("Wiring", "Tunnel", 100, 200, "label", "t", "facing", "west");
        Component t2 = b.add("Wiring", "Tunnel", 600, 200, "label", "t", "facing", "east");
        // 두 터널을 잇는 직선 위에 관계없는 NOT 게이트
        Component mid = b.add("Gates", "NOT Gate", 360, 200);
        Component not = b.add("Gates", "NOT Gate", 700, 200);
        b.wire(t2.getLocation(), not.getEnd(1).getLocation());
        b.commit();
        Circuit c = f.getMainCircuit();
        Influence.View v = Influence.of(c, List.of(a), Influence.Mode.FORWARD, false, -1).view(c);
        assertEquals(1, v.tunnelLinks.size(), v.tunnelLinks.toString());
        BufferedImage img = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 800, 400);
        g.setClip(0, 0, 800, 400);
        InfluenceOverlay.paint(g, null, c, v, 1.0);
        g.dispose();
        com.cburch.logisim.data.Bounds mb = mid.getBounds();
        int onLine = 0;
        for (int x = 130; x < 570; x++) {
            Color px = new Color(img.getRGB(x, 200));
            boolean blue = px.getBlue() > px.getRed() + 60;
            boolean inside = x > mb.getX() - 2 && x < mb.getX() + mb.getWidth() + 2;
            if (inside) {
                assertTrue(!blue, "no link over the NOT gate body at x=" + x + ": " + px);
            } else if (blue) {
                onLine++;
            }
        }
        assertTrue(onLine > 20, "the link is drawn elsewhere: " + onLine);
    }

    /** ui-reviewer #246: 닿은 부품을 다시 그릴 때 원조 라벨 글자("Zero")는 그리지 않는다(라벨 칩이 대신한다). */
    @Test
    void redrawnPartsDoNotBringBackTheOriginalLabel() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component pin = b.add("Wiring", "Pin", 200, 100, "label", "Zero", "output", "true", "facing", "west");
        b.commit();
        Circuit c = f.getMainCircuit();
        Project proj = new Project(f);
        proj.getSimulator().setIsRunning(false);
        com.cburch.logisim.circuit.CircuitState st = proj.getCircuitState();
        javax.swing.JPanel dest = new javax.swing.JPanel();
        int[] dark = new int[2];
        for (int k = 0; k < 2; k++) {
            BufferedImage img = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 400, 200);
            java.awt.Graphics base = k == 0 ? g
                    : kr.ac.hallym.hcs.app.labels.LabelOverlay.filterOne(null, g, c, pin);
            java.awt.Graphics cg = base.create();
            pin.draw(new com.cburch.logisim.comp.ComponentDrawContext(dest, c, st, g, cg));
            cg.dispose();
            g.dispose();
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 400; x++) {
                    Color px = new Color(img.getRGB(x, y));
                    if (px.getRed() + px.getGreen() + px.getBlue() < 300) {
                        dark[k]++;
                    }
                }
            }
        }
        assertTrue(dark[1] < dark[0], "the label text is left out: " + dark[0] + " vs " + dark[1]);
    }

    /** v1.0.3 최종 세트: "N places" 칩은 라벨·값 칩을 피해 자리를 옮긴다. */
    @Test
    void placesChipAvoidsLabelChips() {
        java.awt.Rectangle body = new java.awt.Rectangle(400, 200, 100, 120);
        // 칩이 없으면 오른쪽 위
        java.awt.geom.Point2D.Float p = InfluenceOverlay.placesAt(body, 90, 16, 6, java.util.Collections.emptyList());
        org.junit.jupiter.api.Assertions.assertEquals(410f, p.x, 0.01);
        org.junit.jupiter.api.Assertions.assertEquals(178f, p.y, 0.01);
        // 오른쪽 위 자리의 왼쪽 끝에 값 칩이 있으면(17c의 "0x00") 다른 자리로
        java.awt.Rectangle chip = new java.awt.Rectangle(398, 180, 30, 14);
        java.awt.geom.Point2D.Float q = InfluenceOverlay.placesAt(body, 90, 16, 6, java.util.List.of(chip));
        java.awt.Rectangle placed = new java.awt.Rectangle(Math.round(q.x), Math.round(q.y), 90, 16);
        org.junit.jupiter.api.Assertions.assertFalse(placed.intersects(chip), "moved off the chip: " + placed);
    }

    /** 옮긴 자리도 터널 점선(무게 10)을 덮지 않고, 모든 자리가 막히면 가장 적게 가리는 자리를 고른다. */
    @Test
    void placesChipAvoidsTunnelLinksAndPicksTheLeastCoveredSpot() {
        java.awt.Rectangle body = new java.awt.Rectangle(400, 200, 100, 120);
        java.util.List<InfluenceOverlay.Obstacle> obs = new java.util.ArrayList<>();
        obs.add(new InfluenceOverlay.Obstacle(new java.awt.Rectangle(398, 180, 30, 14), 10)); // 오른쪽 위 자리의 값 칩
        obs.add(new InfluenceOverlay.Obstacle(new java.awt.Rectangle(400, 170, 20, 20), 10)); // 왼쪽 위 자리
        obs.add(new InfluenceOverlay.Obstacle(new java.awt.Rectangle(420, 326, 6, 6), 10)); // 아래 자리를 지나는 점선
        java.awt.geom.Point2D.Float q = InfluenceOverlay.placesAtWeighted(body, 90, 16, 6, obs);
        java.awt.Rectangle placed = new java.awt.Rectangle(Math.round(q.x), Math.round(q.y), 90, 16);
        for (InfluenceOverlay.Obstacle o : obs) {
            org.junit.jupiter.api.Assertions.assertFalse(placed.intersects(o.rect), placed + " covers " + o.rect);
        }
        org.junit.jupiter.api.Assertions.assertFalse(InfluenceOverlay.coversText(q, 90, 16, obs));
        // 둘레가 모두 칩으로 막히면 가장 적게 가리는 자리라도 글자를 가린다: 작은 배율에서는 그리지 않는 조건
        java.util.List<InfluenceOverlay.Obstacle> full = java.util.List.of(
                new InfluenceOverlay.Obstacle(new java.awt.Rectangle(250, 50, 400, 450), 10));
        java.awt.geom.Point2D.Float r = InfluenceOverlay.placesAtWeighted(body, 90, 16, 6, full);
        org.junit.jupiter.api.Assertions.assertTrue(InfluenceOverlay.coversText(r, 90, 16, full));
    }

    /**
     * D-129: 부품을 같은 수의 새 부품으로 바꿔도(원조는 옮기거나 속성을 바꾸면 새 객체를 만든다) 회로가 바뀐 것이다.
     * identity hash 합 서명은 -XX:hashCode=2 JVM에서 이것을 못 알아채 옛 영향 경로를 그렸다.
     */
    @Test
    void replacingAPartClearsItToo() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component a = b.add("Wiring", "Pin", 100, 100, "label", "A");
        Component y = b.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "label", "Y");
        b.wire(Location.create(100, 100), Location.create(300, 100));
        b.commit();
        Project proj = new Project(f);
        proj.getSimulator().setIsRunning(false);
        Circuit c = f.getMainCircuit();
        Object before = InfluenceOverlay.sig(c);
        assertEquals(before, InfluenceOverlay.sig(c), "unchanged circuit, same signature");
        InfluenceOverlay o = InfluenceOverlay.of(proj);
        o.show(c, List.of(a), Influence.Mode.FORWARD);
        assertNotNull(o.viewFor(c));
        CircuitMutation m = new CircuitMutation(c);
        m.replace(y, y.getFactory().createComponent(Location.create(300, 100),
                (com.cburch.logisim.data.AttributeSet) y.getAttributeSet().clone()));
        m.execute();
        assertTrue(!before.equals(InfluenceOverlay.sig(c)), "a new part object: a different circuit");
        assertNull(o.viewFor(c), "the replacement clears the influence");
    }
}
