/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.find;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #80: 이름 색인(서브회로 경로 포함)과 찾은 곳의 시뮬레이션 상태. */
class NameIndexTest {
    @TempDir
    Path tmp;

    LogisimFile build() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit alu = new Circuit("alu");
        file.addCircuit(alu);
        CircuitBuilder ab = new CircuitBuilder(file, alu);
        ab.input("a", 8, 100, 100);
        ab.output("result", 8, 500, 100);
        ab.add("Memory", "Register", 300, 300, "width", "8", "label", "PC");
        ab.commit();
        Circuit spare = new Circuit("spare");
        file.addCircuit(spare);
        CircuitBuilder sb = new CircuitBuilder(file, spare);
        sb.add("Memory", "Register", 300, 300, "label", "pcAlone");
        sb.commit();
        CircuitBuilder mb = new CircuitBuilder(file, file.getMainCircuit());
        mb.addSubcircuit(alu, 300, 100);
        mb.addSubcircuit(alu, 300, 300);
        mb.add("Wiring", "Tunnel", 100, 400, "label", "bus");
        mb.commit();
        return file;
    }

    static List<String> paths(List<NameIndex.Entry> es) {
        List<String> ret = new ArrayList<>();
        for (NameIndex.Entry e : es) {
            ret.add(e.path);
        }
        return ret;
    }

    @Test
    void findsInEverySubcircuitWithPaths() throws Exception {
        LogisimFile file = build();
        NameIndex idx = NameIndex.of(file);
        List<NameIndex.Entry> pc = idx.find("pc");
        assertEquals(Arrays.asList("main › alu #1 › PC", "main › alu #2 › PC",
                "spare › pcAlone"), paths(pc), "exact name first, both instances, and a circuit not used by main");
        assertEquals(NameIndex.Kind.LABEL, pc.get(0).kind);
        assertEquals(2, idx.find("alu").size(), "the two subcircuit instances");
        assertEquals(NameIndex.Kind.SUBCIRCUIT, idx.find("alu").get(0).kind);
        assertEquals(NameIndex.Kind.TUNNEL, idx.find("bus").get(0).kind);
        assertTrue(idx.find("").isEmpty());
        assertTrue(idx.find("nothing").isEmpty());
        assertEquals(1, NameIndex.tunnels(file.getMainCircuit()).get("bus").size());
    }

    /** 찾은 곳으로 가면 그 인스턴스의 시뮬레이션 상태로 들어간다(두 인스턴스는 상태가 다르다). */
    @Test
    void goingThereEntersThatInstance() throws Exception {
        LogisimFile file = build();
        Project proj = new Project(file);
        List<NameIndex.Entry> pc = NameIndex.of(file).find("PC");
        CircuitState s1 = FindDialog.stateFor(proj, pc.get(0));
        CircuitState s2 = FindDialog.stateFor(proj, pc.get(1));
        assertEquals("alu", s1.getCircuit().getName());
        assertTrue(s1 != s2, "each instance has its own state");
        assertSame(proj.getCircuitState(file.getMainCircuit()), s1.getParentState());
        Component inst = pc.get(0).instances.get(0);
        assertEquals("alu", inst.getFactory().getName());
    }
}
