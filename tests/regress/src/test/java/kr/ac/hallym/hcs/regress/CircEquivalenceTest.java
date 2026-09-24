/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.regress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 의미 동등성 검사(D-006 보강)가 같은 것은 같다고, 다른 것은 다르다고 말한다. */
class CircEquivalenceTest {
    static final File DIR = new File(System.getProperty("hcs.circDir"));

    @TempDir
    Path tmp;

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    Path write(String name, String text) throws Exception {
        Path p = tmp.resolve(name);
        Files.write(p, text.getBytes(StandardCharsets.UTF_8));
        return p;
    }

    /** 원조가 다시 저장한 파일(순서·공백이 달라짐)은 의미가 같다. */
    @Test
    void resavedFileIsEquivalent() throws Exception {
        for (String name : Engine.circuits(DIR)) {
            File original = new File(DIR, name + ".circ");
            com.cburch.logisim.file.Loader loader = new com.cburch.logisim.file.Loader(null);
            com.cburch.logisim.file.LogisimFile f = loader.openLogisimFile(original);
            File resaved = tmp.resolve(name + ".circ").toFile();
            CircuitBuilder.save(f, resaved);
            assertEquals(java.util.Collections.<String>emptyList(), CircEquivalence.compare(original, resaved), name);
        }
    }

    /** 정규화로는 같아 보여도 속성 값이 바뀌면 다르다고 한다. */
    @Test
    void attributeChangeIsDetected() throws Exception {
        String text = read(new File(DIR, "register.circ"));
        String changed = text.replace("<a name=\"mode\" val=\"unsigned\"/>", "<a name=\"mode\" val=\"twosComplement\"/>");
        assertFalse(text.equals(changed));
        List<String> diffs = CircEquivalence.compare(write("a.circ", text).toFile(), write("b.circ", changed).toFile());
        assertTrue(diffs.stream().anyMatch(d -> d.contains("components")), diffs.toString());
        changed = text.replace("<a name=\"gateUndefined\" val=\"ignore\"/>", "<a name=\"gateUndefined\" val=\"error\"/>");
        diffs = CircEquivalence.compare(write("a.circ", text).toFile(), write("c.circ", changed).toFile());
        assertTrue(diffs.stream().anyMatch(d -> d.startsWith("options")), diffs.toString());
    }

    static final String WIRE = "<wire from=\"(100,600)\" to=\"(600,600)\"/>";

    /** 선 끝을 핀 앞에서 멈추게 해 포트에서 떼면 넷이 다르다고 한다. */
    @Test
    void disconnectionIsDetected() throws Exception {
        String text = read(new File(DIR, "values.circ"));
        assertTrue(text.contains(WIRE));
        String cut = text.replace(WIRE, "<wire from=\"(100,600)\" to=\"(590,600)\"/>");
        List<String> diffs = CircEquivalence.compare(write("a.circ", text).toFile(), write("d.circ", cut).toFile());
        assertTrue(diffs.stream().anyMatch(d -> d.contains("nets")), diffs.toString());
    }

    /** 선이 핀을 지나 더 뻗어도 원조는 연결로 본다(부록 A.4). 검사도 같은 넷으로 본다. */
    @Test
    void portOnAWireInteriorStaysConnected() throws Exception {
        String text = read(new File(DIR, "values.circ"));
        String longer = text.replace(WIRE, "<wire from=\"(100,600)\" to=\"(700,600)\"/>");
        List<String> diffs = CircEquivalence.compare(write("a.circ", text).toFile(), write("e.circ", longer).toFile());
        assertTrue(diffs.stream().noneMatch(d -> d.contains("nets")), diffs.toString());
    }
}
