/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.engine.rpc.RpcError;
import kr.ac.hallym.hcs.regress.CircEquivalence;
import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** file.*와 model.*: 열기·스냅숏·저장(D-006 바이트 호환)·닫기. */
class FileModelTest {
    static final String MIPS = "kr.ac.hallym.hcs.mips.MipsLibrary";

    @TempDir
    Path tmp;

    InProcess e;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
    }

    @AfterEach
    void stop() throws Exception {
        e.close();
    }

    JsonObject open(File f) {
        return e.client.callObject("file.open", params("path", f.getPath()));
    }

    JsonObject snapshot(String fileId, String circuitId) {
        return e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", circuitId));
    }

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** .circ 글자에서 회로마다 comp·wire 수. */
    static int[] counts(String xml, String circuit) {
        int start = xml.indexOf("<circuit name=\"" + circuit + "\">");
        int end = xml.indexOf("</circuit>", start);
        String body = xml.substring(start, end);
        return new int[] {count(body, "<comp "), count(body, "<wire ")};
    }

    static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) {
            n++;
        }
        return n;
    }

    static List<File> allCircuits() {
        List<File> ret = new ArrayList<>(Fixtures.circFiles());
        ret.add(Fixtures.REF_MIPS);
        return ret;
    }

    @TestFactory
    Stream<DynamicTest> snapshotsMatchTheFile() {
        return allCircuits().stream().map(f -> DynamicTest.dynamicTest(f.getName(), () -> {
            JsonObject opened = open(f);
            String xml = read(f);
            assertEquals(new JsonArray(), opened.getAsJsonArray("messages"), "no load errors");
            JsonArray circuits = opened.getAsJsonArray("circuits");
            assertEquals(count(xml, "<circuit "), circuits.size());
            Set<String> seen = new HashSet<>();
            for (JsonElement ce : circuits) {
                JsonObject ref = ce.getAsJsonObject();
                JsonObject s = snapshot(opened.get("fileId").getAsString(), ref.get("circuitId").getAsString());
                assertEquals(ref.get("name").getAsString(), s.get("name").getAsString());
                int[] n = counts(xml, s.get("name").getAsString());
                JsonArray comps = s.getAsJsonArray("components");
                JsonArray wires = s.getAsJsonArray("wires");
                assertEquals(n[0], comps.size(), "components");
                assertEquals(n[1], wires.size(), "wires");
                Set<String> ports = new HashSet<>();
                for (JsonElement x : comps) {
                    JsonObject c = x.getAsJsonObject();
                    assertTrue(seen.add(c.get("id").getAsString()), "unique ids");
                    assertTrue(c.get("id").getAsString().startsWith("k"));
                    JsonArray b = c.getAsJsonArray("bounds");
                    for (JsonElement pe : c.getAsJsonArray("ports")) {
                        JsonObject p = pe.getAsJsonObject();
                        int px = p.getAsJsonArray("loc").get(0).getAsInt();
                        int py = p.getAsJsonArray("loc").get(1).getAsInt();
                        int bx = b.get(0).getAsInt();
                        int by = b.get(1).getAsInt();
                        assertTrue(px >= bx && px <= bx + b.get(2).getAsInt() && py >= by
                                && py <= by + b.get(3).getAsInt(),
                                c.get("name") + " port " + p + " on its bounds " + b);
                        assertTrue(p.get("width").getAsInt() >= 1);
                        ports.add(c.get("id").getAsString() + "#" + p.get("i").getAsInt());
                    }
                    if (!c.get("lib").isJsonNull()) {
                        assertFalse(c.has("subcircuit"));
                    } else {
                        assertTrue(c.has("subcircuit"), "a component without a library is a subcircuit");
                    }
                }
                Set<String> wireIds = new HashSet<>();
                for (JsonElement x : wires) {
                    String id = x.getAsJsonObject().get("id").getAsString();
                    assertTrue(id.startsWith("w") && seen.add(id), "unique wire ids");
                    wireIds.add(id);
                }
                // 넷은 모든 포트와 선을 한 번씩 덮는다
                Set<String> netPorts = new HashSet<>();
                Set<String> netWires = new HashSet<>();
                for (JsonElement ne : s.getAsJsonArray("nets")) {
                    JsonObject net = ne.getAsJsonObject();
                    assertTrue(net.get("id").getAsString().matches("n\\d+"));
                    assertTrue(net.get("width").getAsInt() >= 1);
                    for (JsonElement pe : net.getAsJsonArray("ports")) {
                        assertTrue(netPorts.add(pe.getAsJsonArray().get(0).getAsString() + "#"
                                + pe.getAsJsonArray().get(1).getAsInt()));
                    }
                    for (JsonElement we : net.getAsJsonArray("wires")) {
                        assertTrue(netWires.add(we.getAsString()));
                    }
                }
                assertEquals(ports, netPorts);
                assertEquals(wireIds, netWires);
            }
        }));
    }

    @Test
    void attributesAreTheStringsTheCircStores() throws Exception {
        JsonObject opened = open(new File(Fixtures.CIRC_DIR, "gates.circ"));
        JsonObject s = snapshot(opened.get("fileId").getAsString(), opened.get("main").getAsString());
        String xml = read(new File(Fixtures.CIRC_DIR, "gates.circ"));
        // .circ에 적힌 <comp …> 블록의 속성 값이 스냅숏의 attrs에 그대로 있어야 한다
        Matcher m = Pattern.compile("<comp lib=\"(\\d+)\" loc=\"\\((\\d+),(\\d+)\\)\" name=\"([^\"]+)\">(.*?)</comp>",
                Pattern.DOTALL).matcher(xml);
        int checked = 0;
        while (m.find()) {
            int x = Integer.parseInt(m.group(2));
            int y = Integer.parseInt(m.group(3));
            JsonObject comp = null;
            for (JsonElement ce : s.getAsJsonArray("components")) {
                JsonObject c = ce.getAsJsonObject();
                if (c.get("name").getAsString().equals(m.group(4)) && c.getAsJsonArray("loc").get(0).getAsInt() == x
                        && c.getAsJsonArray("loc").get(1).getAsInt() == y) {
                    comp = c;
                }
            }
            assertNotNull(comp, m.group(4) + " at " + x + "," + y);
            Matcher a = Pattern.compile("<a name=\"([^\"]+)\" val=\"([^\"]*)\"/>").matcher(m.group(5));
            while (a.find()) {
                assertEquals(a.group(2).replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                        .replace("&quot;", "\""), comp.getAsJsonObject("attrs").get(a.group(1)).getAsString(),
                        m.group(4) + "." + a.group(1));
                checked++;
            }
        }
        assertTrue(checked > 10, "checked " + checked + " attributes");
    }

    @Test
    void refMipsLoadsTheBundledLibraryAndListsTheTools() {
        JsonObject opened = open(Fixtures.REF_MIPS);
        String fileId = opened.get("fileId").getAsString();
        JsonObject mips = null;
        for (JsonElement l : opened.getAsJsonArray("libraries")) {
            if (l.getAsJsonObject().get("kind").getAsString().equals("jar")) {
                mips = l.getAsJsonObject();
            }
        }
        assertNotNull(mips);
        assertEquals("hcs-mips.jar", mips.get("path").getAsString(), "the saved descriptor path stays as written");
        assertEquals("Hallym MIPS", mips.get("display").getAsString());
        JsonObject s = snapshot(fileId, opened.get("main").getAsString());
        assertFalse(Fixtures.byName(s.getAsJsonArray("components"), "Instruction Memory").isEmpty());
        JsonArray lib = e.client.call("model.library", params("fileId", fileId)).getAsJsonArray();
        assertTrue(lib.get(0).getAsJsonObject().get("lib").isJsonNull(), "the file's own circuits come first");
        Set<String> names = new HashSet<>();
        for (JsonElement le : lib) {
            JsonObject l = le.getAsJsonObject();
            assertFalse(l.has("pending"), "the MIPS library is in this file");
            for (JsonElement t : l.getAsJsonArray("tools")) {
                names.add((l.get("lib").isJsonNull() ? "" : l.get("lib").getAsString()) + "/"
                        + t.getAsJsonObject().get("name").getAsString());
            }
        }
        assertTrue(names.contains("Gates/AND Gate"));
        assertTrue(names.contains(MIPS + "/Data Memory"), "the MIPS library's name is its class name: " + names);
        assertTrue(names.contains("/main"));
        assertFalse(names.contains("Base/Poke Tool"), "only component tools");
    }

    @Test
    void newFileListsThePendingMipsLibraryAndSavesLikeTheOriginal() throws Exception {
        JsonObject created = e.client.callObject("file.new", params());
        String fileId = created.get("fileId").getAsString();
        assertEquals(1, created.getAsJsonArray("circuits").size());
        assertEquals("main", created.getAsJsonArray("circuits").get(0).getAsJsonObject().get("name").getAsString());
        JsonArray lib = e.client.call("model.library", params("fileId", fileId)).getAsJsonArray();
        boolean pending = false;
        for (JsonElement le : lib) {
            pending |= le.getAsJsonObject().has("pending") && le.getAsJsonObject().get("lib").getAsString()
                    .equals(MIPS);
        }
        assertTrue(pending, "Hallym MIPS is shown before it is used (V-01)");
        assertEquals(RpcError.INVALID_PARAMS, e.client.fail("file.save", params("fileId", fileId)).code,
                "a new file needs a path");
        File saved = tmp.resolve("new.circ").toFile();
        e.client.call("file.save", params("fileId", fileId, "path", saved.getPath()));
        // 원조 2.7.1의 File › New → Save와 같은 글자
        LogisimFile ref = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        File refFile = tmp.resolve("ref.circ").toFile();
        CircuitBuilder.save(ref, refFile);
        assertEquals(CircNormalizer.normalize(read(refFile)), CircNormalizer.normalize(read(saved)));
        assertFalse(read(saved).contains("MipsLibrary"), "an unused MIPS library is not written");
    }

    @TestFactory
    Stream<DynamicTest> savingWithoutNewComponentsIsByteIdenticalToTheOriginal() throws Exception {
        List<File> plain = new ArrayList<>();
        for (File f : Fixtures.circFiles()) {
            if (!Fixtures.usesMips(f)) {
                plain.add(f);
            }
        }
        assertFalse(plain.isEmpty());
        return plain.stream().map(f -> DynamicTest.dynamicTest(f.getName(), () -> {
            JsonObject opened = open(f);
            File saved = tmp.resolve(f.getName()).toFile();
            JsonObject r = e.client.callObject("file.save", params("fileId", opened.get("fileId").getAsString(),
                    "path", saved.getPath()));
            assertEquals(saved.getPath(), r.get("path").getAsString());
            assertEquals(saved.length(), r.get("bytes").getAsLong());
            assertFalse(r.get("needsMipsJar").getAsBoolean());
            assertEquals(CircNormalizer.normalize(read(f)), CircNormalizer.normalize(read(saved)));
            assertEquals(Collections.<String>emptyList(), CircEquivalence.compare(f, saved));
        }));
    }

    @TestFactory
    Stream<DynamicTest> filesWithMipsPartsSaveStably() throws Exception {
        List<File> mips = new ArrayList<>();
        for (File f : allCircuits()) {
            if (Fixtures.usesMips(f)) {
                mips.add(f);
            }
        }
        assertFalse(mips.isEmpty());
        return mips.stream().map(f -> DynamicTest.dynamicTest(f.getName(), () -> {
            Path dir = Files.createTempDirectory(tmp, "m");
            File once = dir.resolve("once.circ").toFile();
            File twice = dir.resolve("twice.circ").toFile();
            JsonObject a = open(f);
            JsonObject r = e.client.callObject("file.save", params("fileId", a.get("fileId").getAsString(),
                    "path", once.getPath()));
            assertTrue(r.get("needsMipsJar").getAsBoolean(), "no hcs-mips.jar beside the saved file");
            // 저장한 파일은 그 문서의 파일이 된다(Save As): 닫고 다시 연다
            assertTrue(open(once).get("alreadyOpen").getAsBoolean());
            e.client.call("file.close", params("fileId", a.get("fileId").getAsString()));
            JsonObject b = open(once);
            e.client.call("file.save", params("fileId", b.get("fileId").getAsString(), "path", twice.getPath()));
            assertEquals(CircNormalizer.normalize(read(once)), CircNormalizer.normalize(read(twice)));
            assertTrue(read(once).contains("jar#hcs-mips.jar#kr.ac.hallym.hcs.mips.MipsLibrary"));
        }));
    }

    @Test
    void dirtyFollowsEditsAndSaves() throws Exception {
        File copy = tmp.resolve("gates.circ").toFile();
        Files.copy(new File(Fixtures.CIRC_DIR, "gates.circ").toPath(), copy.toPath());
        JsonObject opened = open(copy);
        String fileId = opened.get("fileId").getAsString();
        assertFalse(e.client.callObject("file.dirty", params("fileId", fileId)).get("dirty").getAsBoolean());
        e.client.call("edit.addComponent", params("fileId", fileId, "circuitId", opened.get("main").getAsString(),
                "lib", "Gates", "name", "AND Gate", "loc", Client.xy(600, 600)));
        assertTrue(e.client.callObject("file.dirty", params("fileId", fileId)).get("dirty").getAsBoolean());
        JsonObject saved = e.client.callObject("file.save", params("fileId", fileId));
        assertEquals(copy.getPath(), saved.get("path").getAsString(), "save without a path writes the opened file");
        assertFalse(e.client.callObject("file.dirty", params("fileId", fileId)).get("dirty").getAsBoolean());
        assertTrue(read(copy).contains("loc=\"(600,600)\" name=\"AND Gate\""), "the edit is saved");
        assertFalse(new File(copy.getPath() + ".bak").exists(), "the original saver removes its backup");
    }

    @Test
    void readOnlyFilesRefuseEditsAndSaveOnlyToANewPath() throws Exception {
        JsonObject opened = e.client.callObject("file.open", params("path",
                new File(Fixtures.CIRC_DIR, "gates.circ").getPath(), "readOnly", true));
        String fileId = opened.get("fileId").getAsString();
        Client.Failure f = e.client.fail("edit.addComponent", params("fileId", fileId, "circuitId",
                opened.get("main").getAsString(), "lib", "Gates", "name", "AND Gate", "loc", Client.xy(600, 600)));
        assertEquals(3, f.code);
        assertEquals("readOnly", f.reason());
        Client.Failure s = e.client.fail("file.save", params("fileId", fileId));
        assertEquals(3, s.code);
        File out = tmp.resolve("copy.circ").toFile();
        e.client.call("file.save", params("fileId", fileId, "path", out.getPath()));
        assertTrue(out.isFile());
        // 새 이름으로 저장한 뒤에는 편집할 수 있다
        e.client.call("edit.addComponent", params("fileId", fileId, "circuitId", opened.get("main").getAsString(),
                "lib", "Gates", "name", "AND Gate", "loc", Client.xy(600, 600)));
    }

    @Test
    void openErrors() throws Exception {
        Client.Failure missing = e.client.fail("file.open", params("path", tmp.resolve("none.circ").toString()));
        assertEquals(2, missing.code);
        assertEquals("notFound", missing.reason());
        assertNotNull(missing.error.getAsJsonObject("data").get("path"));

        File badLib = tmp.resolve("badlib.circ").toFile();
        String xml = read(new File(Fixtures.CIRC_DIR, "gates.circ")).replace("<lib desc=\"#Base\" name=\"6\">",
                "<lib desc=\"jar#nowhere/missing.jar#x.Y\" name=\"9\"/>\n  <lib desc=\"#Base\" name=\"6\">");
        Files.write(badLib.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        Client.Failure lib = e.client.fail("file.open", params("path", badLib.getPath()));
        assertEquals(2, lib.code);
        assertEquals("libraryMissing", lib.reason());
        assertEquals("nowhere/missing.jar",
                lib.error.getAsJsonObject("data").getAsJsonArray("missing").get(0).getAsString());

        File garbage = tmp.resolve("garbage.circ").toFile();
        Files.write(garbage.toPath(), "<project><circuit name=".getBytes(StandardCharsets.UTF_8));
        Client.Failure bad = e.client.fail("file.open", params("path", garbage.getPath()));
        assertEquals(2, bad.code);
        assertEquals("loadFailed", bad.reason());
    }

    @Test
    void openingTheSameFileAgainReturnsTheOpenOne() {
        JsonObject a = open(new File(Fixtures.CIRC_DIR, "gates.circ"));
        JsonObject b = open(new File(Fixtures.CIRC_DIR, "gates.circ"));
        assertEquals(a.get("fileId"), b.get("fileId"));
        assertTrue(b.get("alreadyOpen").getAsBoolean());
        assertNull(a.get("alreadyOpen"));
    }

    @Test
    void closedFilesAreGone() {
        JsonObject a = open(new File(Fixtures.CIRC_DIR, "gates.circ"));
        String fileId = a.get("fileId").getAsString();
        e.client.call("file.close", params("fileId", fileId));
        assertEquals(1, e.client.fail("model.circuit", params("fileId", fileId, "circuitId",
                a.get("main").getAsString())).code);
        assertEquals(1, e.client.fail("file.close", params("fileId", fileId)).code);
        JsonObject again = open(new File(Fixtures.CIRC_DIR, "gates.circ"));
        assertFalse(again.get("fileId").getAsString().equals(fileId), "file ids are not reused");
    }

    @Test
    void unknownCircuitIsError1() {
        JsonObject a = open(new File(Fixtures.CIRC_DIR, "gates.circ"));
        Client.Failure f = e.client.fail("model.circuit", params("fileId", a.get("fileId").getAsString(),
                "circuitId", "c999999"));
        assertEquals(1, f.code);
    }

    @Test
    void subcircuitInstancesPointAtTheirCircuit() {
        JsonObject a = open(new File(Fixtures.CIRC_DIR, "subcircuit.circ"));
        String fileId = a.get("fileId").getAsString();
        Set<String> circuitIds = new HashSet<>();
        for (JsonElement c : a.getAsJsonArray("circuits")) {
            circuitIds.add(c.getAsJsonObject().get("circuitId").getAsString());
        }
        JsonObject s = snapshot(fileId, a.get("main").getAsString());
        int subs = 0;
        for (JsonElement ce : s.getAsJsonArray("components")) {
            JsonObject c = ce.getAsJsonObject();
            if (c.has("subcircuit")) {
                subs++;
                assertTrue(circuitIds.contains(c.get("subcircuit").getAsString()));
                assertTrue(c.get("lib").isJsonNull());
            }
        }
        assertTrue(subs > 0);
    }
}
