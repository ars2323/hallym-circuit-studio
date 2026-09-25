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
        assertEquals(Collections.singletonList("AND Gate #1 (output)"), info.get("drivers"));
        assertEquals(Collections.singletonList("R (D)"), info.get("readers"));
        assertEquals(Arrays.asList("d", "d"), sorted(info.get("others")), "a tunnel is named once");
    }

    /** 메뉴의 선택지는 원조 속성에서 읽으므로 모두 적용할 수 있다(핀 풀, NOT·AND 크기 등). */
    @Test
    void everyOfferedOptionApplies() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component pin = b.add("Wiring", "Pin", 100, 100);
        Component not = b.add("Gates", "NOT Gate", 300, 100);
        Component and = b.add("Gates", "AND Gate", 500, 100);
        Component buf = b.add("Gates", "Buffer", 700, 100);
        b.commit();
        Circuit main = file.getMainCircuit();
        assertEquals(Arrays.asList("none", "up", "down"), values(pin, "pull"));
        assertEquals(Arrays.asList("20", "30"), values(not, "size"));
        assertEquals(Arrays.asList("30", "50", "70"), values(and, "size"));
        for (Object[] c : new Object[][] {{pin, "pull"}, {not, "size"}, {and, "size"}, {buf, "size"}}) {
            for (String v : values((Component) c[0], (String) c[1])) {
                kr.ac.hallym.hcs.app.edit.CircuitEdits.setAttribute(main,
                        Collections.singletonList(current(main, (Component) c[0])), (String) c[1], v).execute();
            }
        }
    }

    static List<String> values(Component c, String attr) {
        List<String> ret = new java.util.ArrayList<>();
        for (String[] o : EditMenus.options(c, attr)) {
            ret.add(o[0]);
        }
        return ret;
    }

    /** 속성을 바꾸면 원조가 부품을 새로 만들 수 있으니 같은 위치·종류의 지금 부품. */
    static Component current(Circuit c, Component old) {
        for (Component comp : c.getNonWires()) {
            if (comp.getFactory() == old.getFactory() && comp.getLocation().equals(old.getLocation())) {
                return comp;
            }
        }
        return old;
    }

    static List<String> sorted(List<String> l) {
        Collections.sort(l);
        return l;
    }
}
