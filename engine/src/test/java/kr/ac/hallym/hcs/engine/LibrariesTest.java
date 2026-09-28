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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 라이브러리와 다른 파일(N-11, D-153): Load Library(Built-in·Logisim·JAR), Unload Library, 탭 간 라이브러리(P-03:
 * 넣을 수 있는 열린 파일, 자기 자신·순환 거절, 저장 반영, 저장 전 끊길 연결, Edit Original File), Import
 * Subcircuits(P-05: 목록·계획·가져오기, 같은 파일 거절), hcs-mips.jar 복사.
 */
class LibrariesTest {
    @TempDir
    Path tmp;

    InProcess e;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
    }

    @AfterEach
    void stop() {
        e.close();
    }

    /** lib.circ: main(빈 회로)과 adder(입력 a·b, 출력 s). */
    File libFile() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit adder = new Circuit("adder");
        f.addCircuit(adder);
        CircuitBuilder cb = new CircuitBuilder(f, adder);
        cb.add("Wiring", "Pin", 100, 100, "label", "a");
        cb.add("Wiring", "Pin", 100, 200, "label", "b");
        cb.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "label", "s");
        cb.commit();
        File out = tmp.resolve("lib.circ").toFile();
        CircuitBuilder.save(f, out);
        return out;
    }

    /** 새 파일(저장한 자리 host.circ)을 연다. */
    String openHost() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        File out = tmp.resolve("host.circ").toFile();
        CircuitBuilder.save(f, out);
        return e.client.callObject("file.open", params("path", out.getPath())).get("fileId").getAsString();
    }

    static List<String> libs(JsonObject fileChangedOrOpened) {
        List<String> out = new ArrayList<>();
        for (JsonElement l : fileChangedOrOpened.getAsJsonArray("libraries")) {
            out.add(l.getAsJsonObject().get("lib").getAsString());
        }
        return out;
    }

    JsonObject call(String method, Object... kv) {
        return e.client.callObject(method, params(kv));
    }

    JsonObject lastFileChanged(int mark) {
        e.client.callObject("engine.hello", params("client", "t", "version", "0"));
        List<JsonObject> n = e.client.notificationsAfter(mark, "file.changed");
        return n.isEmpty() ? null : n.get(n.size() - 1);
    }

    @Test
    void builtInLibrariesUnloadAndLoadAgainAtTheEnd() throws Exception {
        String host = openHost();
        JsonObject list = call("model.libraries", "fileId", host);
        assertEquals(0, list.getAsJsonArray("builtins").size(), "the template has every built-in library");
        int m = e.client.mark();
        assertTrue(call("edit.unloadLibrary", "fileId", host, "name", "I/O").get("changed").getAsBoolean());
        JsonObject fc = lastFileChanged(m);
        assertFalse(libs(fc).contains("I/O"));
        list = call("model.libraries", "fileId", host);
        assertEquals("I/O", list.getAsJsonArray("builtins").get(0).getAsJsonObject().get("name").getAsString());
        m = e.client.mark();
        JsonObject r = call("edit.loadLibrary", "fileId", host, "kind", "builtin", "name", "I/O");
        assertTrue(r.get("changed").getAsBoolean());
        assertEquals("I/O", r.get("lib").getAsString());
        List<String> after = libs(lastFileChanged(m));
        assertEquals("I/O", after.get(after.size() - 1), "a library loaded again goes to the end: " + after);
        assertEquals("already", call("edit.loadLibrary", "fileId", host, "kind", "builtin", "name", "I/O")
                .get("outcome").getAsString());
        assertEquals(-32602, e.client.fail("edit.loadLibrary", params("fileId", host, "kind", "builtin", "name",
                "Nope")).code);
        // 쓰이는 라이브러리는 뺄 수 없다: 회로 이름과 함께
        String main = mainId(host);
        call("edit.addComponent", "fileId", host, "circuitId", main, "lib", "Gates", "name", "AND Gate", "loc",
                new int[] {200, 200});
        Client.Failure used = e.client.fail("edit.unloadLibrary", params("fileId", host, "name", "Gates"));
        assertEquals(3, used.code);
        assertEquals("inUse", used.reason());
        assertEquals("main", used.error.getAsJsonObject("data").get("circuit").getAsString());
        // 원조 도구 모음이 쓰는 라이브러리(Base)도 뺄 수 없다
        Client.Failure toolbar = e.client.fail("edit.unloadLibrary", params("fileId", host, "name", "Base"));
        assertEquals("toolbar", toolbar.reason());
    }

    String mainId(String fileId) {
        JsonObject changed = CircuitsTestSupport.fileJson(e, fileId);
        return changed.get("main").getAsString();
    }

    @Test
    void aLogisimLibraryByPathRelativeToTheFileAndItsRefusals() throws Exception {
        File lib = libFile();
        String host = openHost();
        int m = e.client.mark();
        JsonObject r = call("edit.loadLibrary", "fileId", host, "kind", "circ", "path", "lib.circ");
        assertTrue(r.get("changed").getAsBoolean());
        assertEquals("lib", r.get("lib").getAsString());
        JsonObject fc = lastFileChanged(m);
        JsonObject ref = fc.getAsJsonArray("libraries").get(fc.getAsJsonArray("libraries").size() - 1)
                .getAsJsonObject();
        assertEquals("circ", ref.get("kind").getAsString());
        assertEquals("already", call("edit.loadLibrary", "fileId", host, "kind", "circ", "path", lib.getPath())
                .get("outcome").getAsString());
        // 그 라이브러리의 회로를 놓을 수 있다
        String main = mainId(host);
        call("edit.addComponent", "fileId", host, "circuitId", main, "lib", "lib", "name", "adder", "loc",
                new int[] {300, 300});
        // 자기 자신
        Client.Failure self = e.client.fail("edit.loadLibrary", params("fileId", host, "kind", "circ", "path",
                "host.circ"));
        assertEquals("self", self.reason());
        // 없는 파일
        assertEquals(2, e.client.fail("edit.loadLibrary", params("fileId", host, "kind", "circ", "path",
                "nope.circ")).code);
        // 순환: lib.circ를 열고 그 파일에 host를 넣으려 하면 host가 이미 lib를 쓴다
        String libId = e.client.callObject("file.open", params("path", lib.getPath())).get("fileId").getAsString();
        call("file.save", "fileId", host);
        Client.Failure circ = e.client.fail("edit.loadLibrary", params("fileId", libId, "kind", "circ", "path",
                tmp.resolve("host.circ").toString()));
        assertEquals("circular", circ.reason());
        // model.libraries의 열린 파일 상태
        JsonObject list = call("model.libraries", "fileId", host);
        JsonObject other = list.getAsJsonArray("openFiles").get(0).getAsJsonObject();
        assertEquals(libId, other.get("fileId").getAsString());
        assertEquals("loaded", other.get("state").getAsString());
        assertEquals("lib", other.get("lib").getAsString());
        assertTrue(other.getAsJsonArray("circuits").toString().contains("adder"));
        JsonObject fromLib = call("model.libraries", "fileId", libId);
        assertEquals("circular", fromLib.getAsJsonArray("openFiles").get(0).getAsJsonObject().get("state")
                .getAsString());
        // Edit Original File: 라이브러리 회로의 파일
        JsonObject lib0 = null;
        for (JsonElement g : e.client.call("model.library", params("fileId", host)).getAsJsonArray()) {
            if (g.getAsJsonObject().has("lib") && !g.getAsJsonObject().get("lib").isJsonNull()
                    && g.getAsJsonObject().get("lib").getAsString().equals("lib")) {
                lib0 = g.getAsJsonObject();
            }
        }
        assertNotNull(lib0);
    }

    @Test
    void savingALibraryFileUpdatesTheFilesThatUseIt() throws Exception {
        File lib = libFile();
        String host = openHost();
        call("edit.loadLibrary", "fileId", host, "kind", "circ", "path", lib.getPath());
        String main = mainId(host);
        JsonObject placed = call("edit.addComponent", "fileId", host, "circuitId", main, "lib", "lib", "name", "adder",
                "loc", new int[] {300, 300});
        String inst = placed.get("id").getAsString();
        // 인스턴스의 첫 포트에 선
        JsonObject snap = call("model.circuit", "fileId", host, "circuitId", main);
        JsonArray portAt = null;
        for (JsonElement c : snap.getAsJsonArray("components")) {
            if (c.getAsJsonObject().get("id").getAsString().equals(inst)) {
                portAt = c.getAsJsonObject().getAsJsonArray("ports").get(0).getAsJsonObject().getAsJsonArray("loc");
            }
        }
        int px = portAt.get(0).getAsInt();
        int py = portAt.get(1).getAsInt();
        call("edit.addWire", "fileId", host, "circuitId", main, "points", new Object[] {new int[] {px, py},
                new int[] {px - 30, py}});
        call("file.save", "fileId", host);
        // Edit Original File
        String libCircuit = null;
        for (JsonElement c : call("model.circuit", "fileId", host, "circuitId", main).getAsJsonArray("components")) {
            if (c.getAsJsonObject().has("subcircuit")) {
                libCircuit = c.getAsJsonObject().get("subcircuit").getAsString();
            }
        }
        JsonObject origin = call("file.originOf", "fileId", host, "circuitId", libCircuit);
        assertEquals(lib.getCanonicalPath(), new File(origin.get("path").getAsString()).getCanonicalPath());
        assertEquals("adder", origin.get("circuit").getAsString());
        assertTrue(call("file.originOf", "fileId", host, "circuitId", main).get("path").isJsonNull());

        // lib.circ를 열어 adder의 핀 b를 지운다: 저장 전 영향에 host가 나온다
        JsonObject opened = e.client.callObject("file.open", params("path", lib.getPath()));
        String libId = opened.get("fileId").getAsString();
        String adder = CircuitsTest.circuitNamed(opened, "adder");
        String pinA = null;
        for (JsonElement c : call("model.circuit", "fileId", libId, "circuitId", adder).getAsJsonArray("components")) {
            JsonObject o = c.getAsJsonObject();
            if (o.getAsJsonObject("attrs").has("label") && o.getAsJsonObject("attrs").get("label").getAsString()
                    .equals("a")) {
                pinA = o.get("id").getAsString();
            }
        }
        assertEquals(0, call("file.saveImpact", "fileId", libId).getAsJsonArray("cuts").size(), "nothing yet");
        call("edit.delete", "fileId", libId, "circuitId", adder, "ids", new Object[] {pinA});
        JsonArray cuts = call("file.saveImpact", "fileId", libId).getAsJsonArray("cuts");
        assertEquals(1, cuts.size());
        assertEquals(host, cuts.get(0).getAsJsonObject().get("fileId").getAsString());
        assertEquals("host.circ", cuts.get(0).getAsJsonObject().get("file").getAsString());
        assertEquals(1, cuts.get(0).getAsJsonObject().get("connections").getAsInt());
        // 저장하면 host가 새 버전을 받는다: 인스턴스는 새 부품, 포트 둘
        int m = e.client.mark();
        call("file.save", "fileId", libId);
        JsonObject upd = e.client.awaitNotificationAfter(m, "file.libraryUpdated",
                o -> o.get("fileId").getAsString().equals(host));
        assertEquals("lib.circ", upd.get("library").getAsString());
        int ports = -1;
        for (JsonElement c : call("model.circuit", "fileId", host, "circuitId", main).getAsJsonArray("components")) {
            if (c.getAsJsonObject().has("subcircuit")) {
                ports = c.getAsJsonObject().getAsJsonArray("ports").size();
                assertFalse(c.getAsJsonObject().get("id").getAsString().equals(inst), "a new component");
            }
        }
        assertEquals(2, ports, "the new version's ports");
    }

    @Test
    void jarLibraryFromItsManifestAndTheJarBesideCopy() throws Exception {
        String host = openHost();
        File jar = tmp.resolve("hcs-mips.jar").toFile();
        Files.copy(new File(System.getProperty("hcs.bundledMips")).toPath(), jar.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        JsonObject r = call("edit.loadLibrary", "fileId", host, "kind", "jar", "path", "hcs-mips.jar");
        assertTrue(r.get("changed").getAsBoolean());
        assertTrue(call("model.libraries", "fileId", host).get("mips").getAsBoolean());
        call("file.save", "fileId", host);
        String text = new String(Files.readAllBytes(tmp.resolve("host.circ")), StandardCharsets.UTF_8);
        assertTrue(text.contains("jar#hcs-mips.jar#kr.ac.hallym.hcs.mips.MipsLibrary"), text);
        // 저장한 .circ 옆에 jar를 둔다(v1 [Copy hcs-mips.jar Here])
        Files.delete(jar.toPath());
        JsonObject copied = call("file.copyMipsJar", "fileId", host);
        assertEquals("hcs-mips.jar", copied.get("name").getAsString());
        assertTrue(jar.isFile());
        // manifest가 없는 파일: className을 달라고 한다
        File notJar = tmp.resolve("plain.jar").toFile();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(Files.newOutputStream(notJar.toPath()))) {
            z.putNextEntry(new java.util.zip.ZipEntry("x.txt"));
            z.write(1);
            z.closeEntry();
        }
        Client.Failure f = e.client.fail("edit.loadLibrary", params("fileId", host, "kind", "jar", "path", "plain.jar"));
        assertEquals("noLibraryClass", f.reason());
    }

    @Test
    void importSubcircuitsCopiesWithTheirDependenciesAndNumbersTakenNames() throws Exception {
        // 원본: top이 inner를 쓴다
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit inner = new Circuit("inner");
        f.addCircuit(inner);
        CircuitBuilder ib = new CircuitBuilder(f, inner);
        ib.add("Wiring", "Pin", 100, 100, "label", "x");
        ib.add("Wiring", "Pin", 200, 100, "facing", "west", "output", "true", "label", "y");
        ib.wire(Location.create(100, 100), Location.create(200, 100));
        ib.commit();
        Circuit top = new Circuit("top");
        f.addCircuit(top);
        CircuitBuilder tb = new CircuitBuilder(f, top);
        Component i = tb.addSubcircuit(inner, 300, 300);
        assertNotNull(i);
        tb.commit();
        File src = tmp.resolve("src.circ").toFile();
        CircuitBuilder.save(f, src);

        String host = openHost();
        JsonObject peek = call("file.peek", "fileId", host, "path", "src.circ");
        assertEquals("src.circ", peek.get("name").getAsString());
        List<String> names = new ArrayList<>();
        for (JsonElement c : peek.getAsJsonArray("circuits")) {
            names.add(c.getAsJsonObject().get("name").getAsString());
            if (c.getAsJsonObject().get("name").getAsString().equals("top")) {
                assertEquals("[\"inner\"]", c.getAsJsonObject().getAsJsonArray("uses").toString());
            }
        }
        assertEquals(List.of("main", "inner", "top"), names);
        // 계획: inner가 먼저, 이미 있는 이름(main)은 main-2
        JsonObject plan = call("model.importPlan", "fileId", host, "path", "src.circ", "circuits",
                new Object[] {"top", "main"});
        JsonArray order = plan.getAsJsonArray("order");
        assertEquals("inner", order.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("top", order.get(1).getAsJsonObject().get("name").getAsString());
        assertEquals("main-2", order.get(2).getAsJsonObject().get("as").getAsString());
        int m = e.client.mark();
        JsonObject r = call("edit.importCircuits", "fileId", host, "path", "src.circ", "circuits",
                new Object[] {"top"});
        assertTrue(r.get("changed").getAsBoolean());
        assertEquals(2, r.getAsJsonObject("plan").getAsJsonArray("order").size());
        JsonObject fc = lastFileChanged(m);
        List<String> now = new ArrayList<>();
        for (JsonElement c : fc.getAsJsonArray("circuits")) {
            now.add(c.getAsJsonObject().get("name").getAsString());
        }
        assertEquals(List.of("main", "inner", "top"), now);
        // 한 동작: 되돌리면 둘 다 빠진다
        call("edit.undo", "fileId", host);
        assertEquals(1, CircuitsTestSupport.fileJson(e, host).getAsJsonArray("circuits").size());
        // 같은 파일, 없는 회로
        assertEquals("sameFile", e.client.fail("edit.importCircuits", params("fileId", host, "path", "host.circ",
                "circuits", new Object[] {"main"})).reason());
        assertEquals(-32602, e.client.fail("edit.importCircuits", params("fileId", host, "path", "src.circ",
                "circuits", new Object[] {"nope"})).code);
    }

    @Test
    void importLeavesTheSourcesWiringToolSettingsOnThisFileOnly() throws Exception {
        // 원본의 Pin 도구: 라벨 p, 출력(원조 new Loader가 모든 파일의 Wiring 도구에 남기던 것, 골든 09)
        File src = tmp.resolve("pins.circ").toFile();
        Files.copy(new File(System.getProperty("hcs.circDir")).toPath().getParent().resolve("parity/inputs/adders.circ"),
                src.toPath(), StandardCopyOption.REPLACE_EXISTING);
        String host = openHost();
        LogisimFile other = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        File otherFile = tmp.resolve("other.circ").toFile();
        CircuitBuilder.save(other, otherFile);
        String otherId = e.client.callObject("file.open", params("path", otherFile.getPath())).get("fileId")
                .getAsString();
        call("edit.importCircuits", "fileId", host, "path", "pins.circ", "circuits", new Object[] {"half_adder"});
        call("file.save", "fileId", host);
        call("file.save", "fileId", otherId);
        String hostText = new String(Files.readAllBytes(tmp.resolve("host.circ")), StandardCharsets.UTF_8);
        String otherText = new String(Files.readAllBytes(otherFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(hostText.contains("<a name=\"label\" val=\"p\"/>"), "the host's Pin tool took the source's");
        assertFalse(otherText.contains("<a name=\"label\" val=\"p\"/>"), "another open file keeps its own (D-149)");
    }
}
