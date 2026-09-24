/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.splitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.regress.CircuitBuilder;
import kr.ac.hallym.hcs.regress.Engine;

/** #105: 범위 표기 파서, 프리셋의 bitN, MSB/LSB 방향, 합치기 순서, 원조 엔진에서의 동작. */
class SplitterSpecTest {
    static final File ORIGINAL_JAR = new File(System.getProperty("hcs.logisimJar"));

    @TempDir
    Path tmp;

    static List<String> labels(SplitterSpec s) {
        List<String> ret = new ArrayList<>();
        for (SplitterSpec.Arm a : s.arms()) {
            ret.add(a.label());
        }
        return ret;
    }

    @Test
    void rangesMsbOnTopAndLsbOnTop() throws Exception {
        SplitterSpec msb = SplitterSpec.parse("31:26, 25:21, 20:16, 15:0", 32, true);
        assertEquals(Arrays.asList("[31:26]", "[25:21]", "[20:16]", "[15:0]"), labels(msb));
        Map<String, String> a = msb.toStandardAttrs();
        assertEquals("4", a.get("fanout"));
        assertEquals("32", a.get("incoming"));
        assertEquals("0", a.get("bit31"));
        assertEquals("0", a.get("bit26"));
        assertEquals("1", a.get("bit25"));
        assertEquals("3", a.get("bit0"));
        List<String> keys = new ArrayList<>(a.keySet());
        assertEquals(Arrays.asList("fanout", "incoming", "bit0"), keys.subList(0, 3), "fanout and width first");

        SplitterSpec lsb = SplitterSpec.parse("15:0, 31:26 , 20:16,25:21", 32, false);
        assertEquals(Arrays.asList("[15:0]", "[20:16]", "[25:21]", "[31:26]"), labels(lsb));
        assertEquals("0", lsb.toStandardAttrs().get("bit0"));
        assertEquals("3", lsb.toStandardAttrs().get("bit31"));
    }

    @Test
    void repeatsAndWidths() throws Exception {
        SplitterSpec bytes = SplitterSpec.parse("4x8", 0, true);
        assertEquals(32, bytes.width());
        assertEquals(Arrays.asList("[31:24]", "[23:16]", "[15:8]", "[7:0]"), labels(bytes));
        SplitterSpec bits = SplitterSpec.parse("32x1", 0, false);
        assertEquals(32, bits.arms().size());
        assertEquals("[0]", bits.arms().get(0).range());
        assertEquals("31", bits.toStandardAttrs().get("bit31"));
        assertEquals(8, SplitterSpec.parse("7:4, 3:0", 0, true).width(), "width from the highest bit");
        assertEquals(Arrays.asList(15, 14, 13, 12, 11, 10, 9, 8), SplitterSpec.parse("7:0", 16, true).unassigned());
    }

    @Test
    void namesAndLabels() throws Exception {
        SplitterSpec s = SplitterSpec.parse("31:26 op, 25:0 addr", 32, true);
        assertEquals(Arrays.asList("[31:26] op", "[25:0] addr"), labels(s));
        assertEquals("31:26 op, 25:0 addr", s.toText());
        SplitterSpec back = SplitterSpec.fromStandardAttrs(s.toStandardAttrs(), s.names());
        assertEquals(labels(s), labels(back), "round trip through the standard attributes");
    }

    @Test
    void presetsGiveTheMipsFields() {
        assertEquals(Arrays.asList("[31:26] op", "[25:21] rs", "[20:16] rt", "[15:11] rd", "[10:6] shamt",
                "[5:0] funct"), labels(SplitterSpec.Preset.MIPS_R.spec(true)));
        assertEquals(Arrays.asList("[31:26] op", "[25:21] rs", "[20:16] rt", "[15:0] imm"),
                labels(SplitterSpec.Preset.MIPS_I.spec(true)));
        assertEquals(Arrays.asList("[31:26] op", "[25:0] addr"), labels(SplitterSpec.Preset.MIPS_J.spec(true)));
        assertEquals(Arrays.asList("[7:0] b0", "[15:8] b1", "[23:16] b2", "[31:24] b3"),
                labels(SplitterSpec.Preset.BYTES.spec(false)));
        assertEquals(Arrays.asList("[31:16] hi", "[15:0] lo"), labels(SplitterSpec.Preset.HALVES.spec(true)));
        assertEquals(Arrays.asList("[31] sign", "[30:0] rest"), labels(SplitterSpec.Preset.SIGN.spec(true)));
        for (SplitterSpec.Preset p : SplitterSpec.Preset.values()) {
            assertTrue(p.spec(true).unassigned().isEmpty(), p + " covers every bit");
        }
    }

    /** 합치기: 고른 순서대로, 먼저 고른 선이 위 팔이고 가장 큰 비트. 도구는 순서를 바꾸지 않는다. */
    @Test
    void combineKeepsTheChosenOrder() {
        SplitterSpec s = SplitterSpec.combine(Arrays.asList(4, 26, 2), Arrays.asList("PC", "addr", "00"));
        assertEquals(32, s.width());
        assertEquals(Arrays.asList("[31:28] PC", "[27:2] addr", "[1:0] 00"), labels(s));
        SplitterSpec other = SplitterSpec.combine(Arrays.asList(2, 4), Collections.<String>emptyList());
        assertEquals(Arrays.asList("[5:4]", "[3:0]"), labels(other));
    }

    @Test
    void extractOneBit() {
        SplitterSpec s = SplitterSpec.extract(32, 5);
        Map<String, String> a = s.toStandardAttrs();
        assertEquals("1", a.get("fanout"));
        assertEquals("0", a.get("bit5"));
        assertEquals("none", a.get("bit4"));
        assertEquals(31, s.unassigned().size());
    }

    @Test
    void badInputIsRejected() {
        assertThrows(SplitterSpec.ParseException.class, () -> SplitterSpec.parse("", 32, true));
        assertThrows(SplitterSpec.ParseException.class, () -> SplitterSpec.parse("31:20, 25:0", 32, true));
        assertThrows(SplitterSpec.ParseException.class, () -> SplitterSpec.parse("40:32", 32, true));
        assertThrows(SplitterSpec.ParseException.class, () -> SplitterSpec.parse("31-26", 32, true));
        assertThrows(SplitterSpec.ParseException.class, () -> SplitterSpec.parse("5x8", 0, true));
    }

    /** R형 프리셋 스플리터를 원조 2.7.1 엔진에서 돌리면 필드가 나뉜다(add $t0, $t1, $t2 = 0x012A4020). */
    @Test
    void originalEngineSplitsTheFields() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        SplitterSpec spec = SplitterSpec.Preset.MIPS_R.spec(true);
        List<String> attrs = new ArrayList<>();
        for (Map.Entry<String, String> e : spec.toStandardAttrs().entrySet()) {
            attrs.add(e.getKey());
            attrs.add(e.getValue());
        }
        Component sp = b.add("Wiring", "Splitter", 300, 300, attrs.toArray(new String[0]));
        b.constant("instr", 32, 0x012A4020, 100, 300);
        b.tunnel(sp, 0, "instr");
        String[] names = {"op", "rs", "rt", "rd", "shamt", "funct"};
        for (int i = 0; i < names.length; i++) {
            b.tunnel(sp, i + 1, names[i]);
            b.output(names[i], spec.arms().get(i).width(), 600, 100 + 60 * i);
        }
        b.constant("halt", 1, 1, 100, 800);
        b.output("halt", 1, 600, 800);
        b.commit();
        CircuitBuilder.save(file, tmp.resolve("rtype.circ").toFile());
        String tty = Engine.current(ORIGINAL_JAR).run(tmp.toFile(), "rtype");
        String[] lines = tty.split("\n");
        assertEquals("exit=0", lines[0], tty);
        String[] v = lines[1].trim().split("\t");
        long[] expected = {0, 9, 10, 8, 0, 0x20};
        for (int i = 0; i < names.length; i++) {
            assertEquals(expected[i], Long.parseLong(v[i].replace(" ", ""), 2), names[i] + " in " + tty);
        }
    }
}
