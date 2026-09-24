/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #72, #73: 메뉴가 쓰는 대상 판정과 넷 정보. */
class EditMenusTest {
    @TempDir
    Path tmp;

    @Test
    void portUnderThePointerAndNetInfo() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component and = b.add("Gates", "AND Gate", 300, 200, "inputs", "2");
        Component reg = b.add("Memory", "Register", 500, 300, "width", "1", "label", "R");
        b.tunnel(and, 0, "d");
        b.tunnel(reg, 1, "d");
        b.add("Wiring", "Probe", 400, 100);
        b.commit();
        Circuit main = file.getMainCircuit();
        Location in1 = and.getEnds().get(1).getLocation();
        assertEquals(1, EditMenus.portAt(and, in1.translate(3, -2)));
        assertEquals(-1, EditMenus.portAt(and, in1.translate(20, 0)));

        Netlist.Net net = Netlist.of(main).netOf(and, 0);
        Map<String, List<String>> info = EditMenus.netInfo(main, net);
        assertEquals(Collections.singletonList("AND #1.out"), info.get("drivers"));
        assertEquals(Collections.singletonList("R.D"), info.get("readers"));
        assertEquals(Arrays.asList("d", "d"), sorted(info.get("others")), "a tunnel is named once");
    }

    static List<String> sorted(List<String> l) {
        Collections.sort(l);
        return l;
    }
}
