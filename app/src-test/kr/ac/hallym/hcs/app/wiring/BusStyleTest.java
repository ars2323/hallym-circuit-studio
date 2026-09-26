/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.record.RecordingTestSupport;

/** E-03: 버스만 굵게(1비트 선은 그대로), 넷마다 비트 수 자리 하나, 범례 여섯 줄, 선 값의 뜻, 설정 켜고 끄기. */
class BusStyleTest {
    @TempDir
    Path tmp;

    @Test
    void busesAndWidthSpots() throws Exception {
        LogisimFile f = RecordingTestSupport.openCirc(tmp, "demo-datapath.circ");
        Circuit c = f.getMainCircuit();
        Map<Wire, Integer> buses = BusStyle.buses(c);
        assertFalse(buses.isEmpty());
        for (Map.Entry<Wire, Integer> e : buses.entrySet()) {
            assertTrue(e.getValue() >= 2);
        }
        int oneBit = 0;
        for (Wire w : c.getWires()) {
            if (!buses.containsKey(w)) {
                oneBit++;
            }
        }
        assertTrue(oneBit > 0, "1-bit wires stay as they are");
        Map<Wire, Integer> spots = BusStyle.labelSpots(c, buses);
        assertTrue(spots.containsValue(32) && spots.containsValue(5), spots.values().toString());
        assertTrue(spots.size() < buses.size(), "one spot per bus net");
        // 그리기: 굵은 버스는 1비트 선보다 넓은 획으로 그려진다
        Project proj = new Project(f);
        CircuitState s = proj.getCircuitState();
        BufferedImage img = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        WireMarks.paint(g, c, s, null, 1.0);
        g.dispose();
        // 버스에 마우스를 올리면 색의 뜻(E-03)
        Wire bus = buses.keySet().iterator().next();
        s.getPropagator().propagate();
        java.util.List<String> lines = kr.ac.hallym.hcs.app.labels.HoverInfo.lines(s, bus.getEnd0().translate(
                bus.isVertical() ? 0 : 5, bus.isVertical() ? 5 : 0));
        assertTrue(lines.get(lines.size() - 1).startsWith(kr.ac.hallym.hcs.app.Messages.get("hover.color", "").trim()),
                lines.toString());
    }

    @Test
    void legendAndMeanings() {
        assertEquals(6, WireLegend.ROWS.length);
        assertEquals(6, WireLegend.panel().getComponentCount());
        assertEquals("legend.one", WireLegend.meaningKey(Value.TRUE, false));
        assertEquals("legend.zero", WireLegend.meaningKey(Value.FALSE, false));
        assertEquals("legend.x", WireLegend.meaningKey(Value.UNKNOWN, false));
        assertEquals("legend.e", WireLegend.meaningKey(Value.ERROR, false));
        assertEquals("legend.width", WireLegend.meaningKey(Value.TRUE, true));
        assertEquals("legend.bus", WireLegend.meaningKey(Value.createKnown(BitWidth.create(8), 5), false));
        assertEquals("legend.x", WireLegend.meaningKey(Value.createUnknown(BitWidth.create(8)), false));
    }

    @Test
    void settingsToggle() {
        boolean t = BusStyle.thick();
        boolean w = BusStyle.widths();
        try {
            assertTrue(BusStyle.thick(), "thick buses by default");
            assertFalse(BusStyle.widths(), "width numbers off by default");
            BusStyle.setWidths(true);
            assertTrue(BusStyle.widths());
            BusStyle.setThick(false);
            assertFalse(BusStyle.thick());
        } finally {
            BusStyle.setThick(t);
            BusStyle.setWidths(w);
        }
    }
}
