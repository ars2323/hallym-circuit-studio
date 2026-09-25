/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.side;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** S-11(GUI 없이): Tunnels 탭의 목록(이름 순, 개수, 위치 순)과 Minimap의 좌표 변환. */
class SidePanelTest {
    @TempDir
    Path tmp;

    @Test
    void tunnelEntriesAreSortedAndCounted() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        b.add("Wiring", "Tunnel", 300, 300, "label", "pc");
        b.add("Wiring", "Tunnel", 100, 100, "label", "pc");
        b.add("Wiring", "Tunnel", 200, 200, "label", "ALUOp");
        b.add("Wiring", "Pin", 50, 50, "label", "pc"); // 핀은 터널이 아니다
        b.commit();
        List<TunnelList.Entry> e = TunnelList.entries(f.getMainCircuit());
        assertEquals(2, e.size());
        assertEquals("ALUOp (1)", e.get(0).toString(), "names in order, ignoring case");
        assertEquals("pc (2)", e.get(1).toString());
        assertEquals(Location.create(100, 100), e.get(1).tunnels.get(0).getLocation(), "top first");
        assertTrue(TunnelList.entries(null).isEmpty());
    }

    @Test
    void minimapFitsTheCircuitAndMapsBack() {
        Minimap.Fit f = new Minimap.Fit(Bounds.create(100, 50, 400, 200), 216, 216);
        // 가로가 길다: 폭이 배율을 정하고 세로는 가운데
        assertEquals((216 - 2.0 * Minimap.MARGIN) / 400, f.scale, 1e-9);
        assertEquals(Minimap.MARGIN, f.x(100), 1e-6);
        assertEquals(216 - Minimap.MARGIN, f.x(500), 1e-6);
        double top = f.y(50);
        double bottom = f.y(250);
        assertEquals(216 - bottom, top, 1e-6, "vertically centered");
        Location back = f.toCircuit((int) Math.round(f.x(300)), (int) Math.round(f.y(150)));
        assertEquals(300, back.getX(), 2);
        assertEquals(150, back.getY(), 2);
    }
}
