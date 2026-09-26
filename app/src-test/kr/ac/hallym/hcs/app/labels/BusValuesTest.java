/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.app.record.RecordingTestSupport;

/** C-08 버스 값 칩: 진법별 글자, 떠 있거나 좁은 값은 보이지 않음, 배치용 글자는 어떤 값보다 넓다, 이름 없는 버스도 자리. */
class BusValuesTest {
    @TempDir
    Path tmp;

    static Value v(int width, int value) {
        return Value.createKnown(BitWidth.create(width), value);
    }

    @Test
    void formatsEachRadix() {
        assertEquals("0x0000000c", BusValues.format(v(32, 12), BusValues.Mode.HEX));
        assertEquals("0xff", BusValues.format(v(8, 255), BusValues.Mode.HEX));
        assertEquals("4294967295", BusValues.format(v(32, -1), BusValues.Mode.DEC));
        assertEquals("-1", BusValues.format(v(32, -1), BusValues.Mode.SIGNED));
        assertEquals("-128", BusValues.format(v(8, 0x80), BusValues.Mode.SIGNED));
        assertEquals("127", BusValues.format(v(8, 0x7f), BusValues.Mode.SIGNED));
        assertNull(BusValues.format(v(32, 12), BusValues.Mode.OFF));
        assertNull(BusValues.format(Value.TRUE, BusValues.Mode.HEX), "one-bit wires show their color only");
        assertNull(BusValues.format(Value.createUnknown(BitWidth.create(32)), BusValues.Mode.HEX),
                "a floating bus has no chip");
        Value partial = v(8, 0x0f).set(7, Value.UNKNOWN).set(6, Value.UNKNOWN).set(5, Value.UNKNOWN)
                .set(4, Value.UNKNOWN);
        assertEquals("0xxf", BusValues.format(partial, BusValues.Mode.DEC), "partly known: original hex notation");
        assertEquals("0xEf", BusValues.format(v(8, 0x0f).set(4, Value.ERROR), BusValues.Mode.HEX), "E: conflict");
    }

    @Test
    void templateIsAtLeastAsWideAsAnyValue() {
        FontMetrics fm = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
                .getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (BusValues.Mode m : new BusValues.Mode[] {BusValues.Mode.HEX, BusValues.Mode.DEC, BusValues.Mode.SIGNED}) {
            for (int w : new int[] {2, 5, 8, 16, 32}) {
                int tw = fm.stringWidth(BusValues.template(w, m, fm));
                for (int x : new int[] {0, 1, -1, 0x80000000, 0x7fffffff, 0x12345678, 0xdeadbeef, 0x88888888}) {
                    Value val = v(w, x);
                    assertTrue(fm.stringWidth(BusValues.format(val, m)) <= tw, m + " " + w + " " + BusValues.format(val, m));
                }
            }
        }
    }

    /** 상태 표시줄 단추는 코드나 다른 창에서 진법을 바꿔도 글자가 따라간다(C-08 검토). */
    @Test
    void everyButtonFollowsTheMode() {
        BusValues.Mode before = BusValues.mode();
        try {
            javax.swing.JButton a = BusValues.button();
            javax.swing.JButton b = BusValues.button();
            BusValues.setMode(BusValues.Mode.SIGNED);
            assertEquals(kr.ac.hallym.hcs.app.Messages.get("labels.busValues.signed"), a.getText());
            assertEquals(a.getText(), b.getText());
            a.doClick();
            assertEquals(BusValues.Mode.OFF, BusValues.mode());
            assertEquals(kr.ac.hallym.hcs.app.Messages.get("labels.busValues.off"), b.getText());
        } finally {
            BusValues.setMode(before);
        }
    }

    /** 버스 칩의 지시선은 그 선 위의 가장 가까운 점에서 시작한다(C-08 검토). */
    @Test
    void leaderStartsOnTheBus() {
        Wire v = Wire.create(com.cburch.logisim.data.Location.create(540, 100),
                com.cburch.logisim.data.Location.create(540, 240));
        assertEquals(new java.awt.Point(540, 150), LabelOverlay.nearestOnWire(v, new java.awt.Point(500, 150)));
        assertEquals(new java.awt.Point(540, 240), LabelOverlay.nearestOnWire(v, new java.awt.Point(600, 300)));
    }

    @Test
    void unnamedBusesGetASpotToo() throws Exception {
        LogisimFile file = RecordingTestSupport.openCirc(tmp, "demo-datapath.circ");
        Map<Wire, LabelOverlay.Bus> buses = LabelOverlay.buses(file.getMainCircuit());
        Map<Wire, String> names = LabelOverlay.busNames(file.getMainCircuit());
        assertTrue(buses.keySet().containsAll(names.keySet()), "named buses keep their chip spot");
        assertTrue(buses.size() > names.size(), "unnamed buses also get a value chip spot: " + buses.size());
        for (LabelOverlay.Bus b : buses.values()) {
            assertTrue(b.width >= 2);
        }
    }
}
