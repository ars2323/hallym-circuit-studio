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
}
