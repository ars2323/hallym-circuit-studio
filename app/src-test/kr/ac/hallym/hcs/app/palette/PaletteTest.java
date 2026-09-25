/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #76: 질의 해석(별칭·약어·속성 문법)과 순위, 배치 결과. */
class PaletteTest {
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));

    @TempDir
    Path tmp;

    LogisimFile file;
    List<Library> libs;

    void start() throws Exception {
        Loader loader = new Loader(null);
        file = CircuitBuilder.newFile(loader, tmp.toFile());
        Path jar = tmp.resolve("hcs-mips.jar");
        Files.copy(MIPS_JAR.toPath(), jar, StandardCopyOption.REPLACE_EXISTING);
        libs = new ArrayList<>(file.getLibraries());
        libs.add(loader.loadJarLibrary(jar.toFile(), "kr.ac.hallym.hcs.mips.MipsLibrary"));
    }

    Palette.Item first(String q) {
        List<Palette.Item> r = Palette.search(q, libs, Collections.<Circuit>emptyList(),
                Collections.<String>emptyList(), Collections.<String>emptyList());
        assertTrue(!r.isEmpty(), "nothing for " + q);
        return r.get(0);
    }

    /** 표: 질의 → 첫 결과의 저장 이름과 속성. */
    @Test
    void queriesFollowTheTable() throws Exception {
        start();
        Object[][] table = {
            {"앤드", "AND Gate", Collections.emptyMap()},
            {"and 3", "AND Gate", Collections.singletonMap("inputs", "3")},
            {"or", "OR Gate", Collections.emptyMap()},
            {"mux 32", "Multiplexer", Collections.singletonMap("width", "32")},
            {"먹스", "Multiplexer", Collections.emptyMap()},
            {"reg 32", "Register", Collections.singletonMap("width", "32")},
            {"레지스터", "Register", Collections.emptyMap()},
            {"가산기", "Adder", Collections.emptyMap()},
            {"add 16", "Adder", Collections.singletonMap("width", "16")},
            {"split 32", "Splitter", Collections.singletonMap("incoming", "32")},
            {"터널", "Tunnel", Collections.emptyMap()},
            {"const 0x10", "Constant", Collections.singletonMap("value", "0x10")},
            {"pin 8", "Pin", Collections.singletonMap("width", "8")},
            {"imem", "Instruction Memory", Collections.emptyMap()},
            {"스택", "Stack", Collections.emptyMap()},
        };
        for (Object[] row : table) {
            Palette.Item it = first((String) row[0]);
            assertEquals(row[1], it.name, (String) row[0]);
            assertEquals(row[2], it.attrs, (String) row[0]);
        }
    }

    /** 원조 편집기에서 고를 수 없는 숫자는 그 부품을 내지 않고 예외도 없다. */
    @Test
    void outOfRangeNumbersOfferNothingWrong() throws Exception {
        start();
        for (String q : new String[] {"reg 64", "reg 0", "mux 40", "split 64", "and 1", "and 33",
                "reg 0x11111111111111111", "add 99999999999"}) {
            for (Palette.Item it : Palette.search(q, libs, Collections.<Circuit>emptyList(),
                    Collections.<String>emptyList(), Collections.<String>emptyList())) {
                String w = it.attrs.getOrDefault("width", it.attrs.getOrDefault("incoming", "1"));
                assertTrue(Integer.parseInt(w) >= 1 && Integer.parseInt(w) <= 32, q + " → " + it);
                String inputs = it.attrs.get("inputs");
                assertTrue(inputs == null || (Integer.parseInt(inputs) >= 2 && Integer.parseInt(inputs) <= 32),
                        q + " → " + it);
            }
        }
        assertEquals(Collections.singletonMap("width", "32"), first("reg 32").attrs, "the edge values still work");
        assertEquals(Collections.singletonMap("inputs", "32"), first("and 32").attrs);
        assertEquals(Collections.singletonMap("width", "1"), first("reg 1").attrs);
    }

    @Test
    void recentFavoritesSubcircuitsAndCommands() throws Exception {
        start();
        List<Palette.Item> plain = Palette.search("a", libs, Collections.<Circuit>emptyList(),
                Collections.<String>emptyList(), Collections.<String>emptyList());
        List<Palette.Item> recent = Palette.search("a", libs, Collections.<Circuit>emptyList(),
                Collections.singletonList("Adder"), Collections.<String>emptyList());
        assertEquals("Adder", recent.get(0).name, "recently used first");
        List<Palette.Item> fav = Palette.search("a", libs, Collections.<Circuit>emptyList(),
                Collections.singletonList("Adder"), Collections.singletonList("AND Gate"));
        assertEquals("AND Gate", fav.get(0).name, "a favourite beats a recent one");

        Circuit alu = new Circuit("alu");
        List<Palette.Item> sub = Palette.search("alu", libs, Collections.singletonList(alu),
                Collections.<String>emptyList(), Collections.<String>emptyList());
        assertEquals(Palette.Kind.SUBCIRCUIT, sub.get(0).kind);

        assertEquals(Palette.Kind.COMMAND, first("리셋").kind);
        assertEquals("reset", first("리셋").command);
        assertEquals("tick", first("클럭 한 번").command);
        assertEquals("loadS", first(".s").command);
        assertEquals(Arrays.asList("Adder", "AND Gate"), Palette.touch(Arrays.asList("AND Gate", "Adder"), "Adder"));
    }

    /** Enter로 놓은 부품의 속성이 입력과 같다. */
    @Test
    void placedComponentHasTheTypedAttributes() throws Exception {
        start();
        Circuit main = file.getMainCircuit();
        PaletteActions.place(main, first("and 3"), Location.create(203, 198)).execute();
        PaletteActions.place(main, first("reg 32"), Location.create(400, 300)).execute();
        Map<String, Component> byName = new java.util.HashMap<>();
        for (Component c : main.getNonWires()) {
            byName.put(c.getFactory().getName(), c);
        }
        Component and = byName.get("AND Gate");
        assertEquals(Location.create(200, 200), and.getLocation(), "snapped to the grid");
        assertEquals(1 + 3, and.getEnds().size(), "three inputs and an output");
        assertEquals(32, byName.get("Register").getEnds().get(0).getWidth().getWidth());
    }

    /**
     * 목록에는 원조 표시 이름과 속성 표시 이름이 보인다. 이름은 한국어 UI에서도 영어다(D-049). 저장용 속성 이름
     * (width=)은 보이지 않는다.
     */
    @Test
    void listShowsEnglishDisplayNamesEvenInKorean() throws Exception {
        start();
        java.util.Locale before = com.cburch.logisim.util.LocaleManager.getLocale();
        com.cburch.logisim.util.LocaleManager.setLocale(java.util.Locale.KOREAN);
        try {
            Palette.Item mux = first("mux 32");
            assertEquals("Multiplexer", mux.name, "storage name for placing");
            assertEquals("Multiplexer", Palette.displayName(mux));
            assertEquals("Data Bits 32", Palette.attrText(mux));
            String label = PaletteWindow.label(mux);
            assertTrue(label.contains("Multiplexer") && label.contains("Data Bits 32") && !label.contains("width="),
                    label);
            assertEquals("Multiplexer", first("먹스").name, "Korean aliases still find it");
        } finally {
            com.cburch.logisim.util.LocaleManager.setLocale(before);
        }
    }
}
