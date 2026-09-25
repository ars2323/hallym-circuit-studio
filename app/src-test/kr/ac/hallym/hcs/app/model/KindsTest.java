/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.LocaleManager;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #24: 부품 종류 등록표와 공용 식별자. */
class KindsTest {
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));

    @TempDir
    Path tmp;

    static List<ComponentFactory> factories(List<Library> libs) {
        List<ComponentFactory> ret = new ArrayList<>();
        for (Library lib : libs) {
            for (Tool t : lib.getTools()) {
                if (t instanceof AddTool) {
                    ret.add(((AddTool) t).getFactory());
                }
            }
        }
        return ret;
    }

    Library mips(Loader loader) throws Exception {
        Path jar = tmp.resolve("hcs-mips.jar");
        Files.copy(MIPS_JAR.toPath(), jar, StandardCopyOption.REPLACE_EXISTING);
        return loader.loadJarLibrary(jar.toFile(), "kr.ac.hallym.hcs.mips.MipsLibrary");
    }

    /** 기본 라이브러리와 MIPS 라이브러리의 모든 부품이 등록표에 있고, 기본 속성에서 포트 이름이 서로 다르다. */
    @Test
    void everyComponentIsRegisteredWithDistinctPortNames() throws Exception {
        Loader loader = new Loader(null);
        List<Library> libs = new ArrayList<>(loader.getBuiltin().getLibraries());
        libs.add(mips(loader));
        List<ComponentFactory> all = factories(libs);
        assertTrue(all.size() >= 60, "builtin + MIPS: " + all.size());
        for (ComponentFactory f : all) {
            Kinds.Kind k = Kinds.of(f);
            assertNotEquals(Kinds.Category.OTHER, k.category(), f.getName() + " is not in the registry");
            Component c = f.createComponent(Location.create(200, 200), f.createAttributeSet());
            Set<String> names = new HashSet<>();
            for (int i = 0; i < c.getEnds().size(); i++) {
                String n = Kinds.portName(c, i);
                assertFalse(n.isEmpty());
                assertTrue(names.add(n), f.getName() + " port " + i + " name " + n + " repeats " + names);
            }
        }
        assertEquals(all.size() - 0, new HashSet<>(all).size());
    }

    @Test
    void statefulKindsAreTheOnesWithAClock() {
        for (String s : new String[] {"Register", "Counter", "RAM", "ROM", "D Flip-Flop", "Instruction Memory",
                "Data Memory", "Stack", "Console"}) {
            assertTrue(find(s).stateful(), s);
        }
        for (String s : new String[] {"AND Gate", "Multiplexer", "Adder", "Splitter", "Tunnel", "Pin"}) {
            assertFalse(find(s).stateful(), s);
        }
    }

    static Kinds.Kind find(String factory) {
        for (Kinds.Kind k : Kinds.all()) {
            if (k.factory().equals(factory)) {
                return k;
            }
        }
        throw new AssertionError(factory);
    }

    /** 속성에 따라 포트 배치가 바뀌는 선택기류: 원조의 포트 순서와 같은 이름. */
    @Test
    void plexerPortsFollowTheirAttributes() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component mux = b.add("Plexers", "Multiplexer", 200, 200, "select", "2", "width", "8", "enable", "true");
        List<String> names = new ArrayList<>();
        for (int i = 0; i < mux.getEnds().size(); i++) {
            names.add(Kinds.portName(mux, i));
        }
        assertEquals(Arrays.asList("in0", "in1", "in2", "in3", "sel", "en", "out"), names);
        assertEquals(2, CircuitBuilder.width(mux, 4), "sel is 2 bits");
        assertTrue(mux.getEnds().get(6).isOutput());
        Component noEn = b.add("Plexers", "Multiplexer", 400, 200, "select", "1", "enable", "false");
        assertEquals("out", Kinds.portName(noEn, 3));
        Component dec = b.add("Plexers", "Decoder", 200, 400, "select", "2", "enable", "true");
        assertEquals("sel", Kinds.portName(dec, 4));
        assertEquals("en", Kinds.portName(dec, 5));
        Component reg = b.add("Memory", "Register", 400, 400, "width", "32");
        assertEquals("Q", Kinds.portName(reg, 0));
        assertEquals(32, CircuitBuilder.width(reg, 0));
        assertEquals("D", Kinds.portName(reg, 1));
    }

    /** 서브회로 포트 이름은 안쪽 핀 라벨이고, 부품 이름·경로는 UI 언어와 무관하다. */
    @Test
    void subcircuitPortsAndNamesAreLanguageIndependent() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit alu = new Circuit("alu");
        file.addCircuit(alu);
        CircuitBuilder inner = new CircuitBuilder(file, alu);
        inner.input("a", 32, 100, 100);
        inner.input("b", 32, 100, 200);
        inner.output("result", 32, 400, 150);
        inner.commit();
        CircuitBuilder top = new CircuitBuilder(file, file.getMainCircuit());
        Component inst = top.addSubcircuit(alu, 300, 300);
        Component and1 = top.add("Gates", "AND Gate", 500, 100);
        Component and2 = top.add("Gates", "AND Gate", 200, 100);
        Component pc = top.add("Memory", "Register", 600, 400, "label", "PC");
        top.commit();
        Circuit main = file.getMainCircuit();

        Set<String> ports = new HashSet<>();
        for (int i = 0; i < inst.getEnds().size(); i++) {
            ports.add(Kinds.portName(inst, i));
        }
        assertEquals(new HashSet<>(Arrays.asList("a", "b", "result")), ports);

        Locale old = LocaleManager.getLocale();
        try {
            for (Locale l : new Locale[] {Locale.ENGLISH, new Locale("ko")}) {
                LocaleManager.setLocale(l);
                assertEquals("AND #1", Names.name(main, and2), "left one first on the same row");
                assertEquals("AND #2", Names.name(main, and1));
                assertEquals("PC", Names.name(main, pc));
                assertEquals("alu #1", Names.name(main, inst));
                assertEquals("PC.Q", Names.port(main, pc, 0));
            }
        } finally {
            LocaleManager.setLocale(old);
        }
    }

    @Test
    void pathNotation() {
        assertEquals("main › datapath › PC", Names.path("main", "datapath", "PC"));
        assertEquals("main › PC", Names.path("main", "", null, "PC"));
        assertEquals("", Names.path());
    }

    /** S-09: 화면 글자용 포트 이름은 읽을 수 있다(식별자는 그대로). */
    @Test
    void readablePortTitles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        com.cburch.logisim.file.LogisimFile f = kr.ac.hallym.hcs.regress.CircuitBuilder.newFile(
                new com.cburch.logisim.file.Loader(null), dir.toFile());
        kr.ac.hallym.hcs.regress.CircuitBuilder b = new kr.ac.hallym.hcs.regress.CircuitBuilder(f, f.getMainCircuit());
        com.cburch.logisim.comp.Component sp = b.add("Wiring", "Splitter", 200, 200, "fanout", "3", "incoming",
                "32", "bit0", "2", "bit1", "2", "bit2", "2", "bit3", "2", "bit4", "2", "bit5", "2");
        com.cburch.logisim.comp.Component and = b.add("Gates", "AND Gate", 400, 200, "inputs", "3");
        com.cburch.logisim.comp.Component mux = b.add("Plexers", "Multiplexer", 600, 200, "select", "1");
        com.cburch.logisim.comp.Component pin = b.add("Wiring", "Pin", 100, 400, "label", "PC");
        com.cburch.logisim.comp.Component reg = b.add("Memory", "Register", 300, 400, "label", "R");
        b.commit();
        com.cburch.logisim.circuit.Circuit c = f.getMainCircuit();
        assertEquals("Split #1.combined", Names.port(c, sp, 0), "the identifier stays");
        assertEquals("Splitter #1 (combined end)", Names.portTitle(c, sp, 0));
        assertEquals("[31:22,5:0] end", Kinds.readablePort(sp, 3), "bits 0-5 and the default upper bits");
        assertEquals("AND Gate #1 (input 1)", Names.portTitle(c, and, 1), "gate inputs count from 1");
        assertEquals("AND Gate #1 (output)", Names.portTitle(c, and, 0));
        assertEquals("Multiplexer #1 (input 0)", Names.portTitle(c, mux, 0), "a MUX input is its select value");
        assertEquals("PC", Names.portTitle(c, pin, 0), "a labelled pin is named once");
        assertEquals("R (D)", Names.portTitle(c, reg, 1));
        assertEquals("Register #1", Names.numberedTitle(c, reg));
        com.cburch.logisim.comp.Component bare = null;
        for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
            if (x != pin && x.getFactory().getName().equals("Pin")) {
                bare = x;
            }
        }
        assertEquals(null, bare, "only the labelled pin");
        kr.ac.hallym.hcs.regress.CircuitBuilder pb = new kr.ac.hallym.hcs.regress.CircuitBuilder(f, c);
        com.cburch.logisim.comp.Component probe = pb.add("Wiring", "Probe", 700, 400);
        pb.commit();
        assertEquals("Probe #1", Names.portTitle(c, probe, 0), "one-port parts: the part name only");
    }
}
