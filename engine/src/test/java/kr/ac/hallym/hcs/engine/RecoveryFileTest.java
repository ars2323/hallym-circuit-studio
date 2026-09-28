/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static kr.ac.hallym.hcs.engine.Client.xy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.engine.doc.RecoveryFiles;
import kr.ac.hallym.hcs.regress.CircEquivalence;
import kr.ac.hallym.hcs.regress.CircNormalizer;

/**
 * 비정상 종료 복구 파일(N-19, D-152, docs/engine-api.md file.recoverWrite·file.open recovery): 학생이 저장한 파일
 * 옆의 {@code <이름>.circ.hcs-recover}는 지금 저장하면 쓰일 .circ 그대로이고, 그 폴더 말고는 아무 데도 쓰지 않는다.
 * 새 파일(한 번도 저장하지 않음)은 복구 파일이 없다. 다시 켠 엔진이 그 파일 자리에서 복구 내용을 열면 죽기 전의 모델이고
 * 저장하지 않은 편집이다. 앱이 맡으면(engine.hello recoveryFiles) 저장·닫기·정상 종료에 지우고, 화면이 사라져
 * 끝나면(stdin 닫힘) 저장하지 않은 파일의 것을 써 둔다.
 */
class RecoveryFileTest {
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

    static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    static File recoveryOf(File circ) {
        return new File(circ.getAbsolutePath() + ".hcs-recover");
    }

    /** 폴더의 파일 이름들(정렬). */
    static List<String> names(Path dir) throws IOException {
        List<String> ret = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.forEach(p -> ret.add(p.getFileName().toString()));
        }
        Collections.sort(ret);
        return ret;
    }

    /** 파일의 모든 회로의 모델: 부품(라이브러리·이름·자리·속성)과 선(두 끝), id 없이(다른 엔진과 견준다). */
    static Map<String, List<String>> model(Client c, String fileId) {
        Map<String, List<String>> out = new TreeMap<>();
        JsonArray groups = c.call("model.library", params("fileId", fileId)).getAsJsonArray();
        for (JsonElement t : groups.get(0).getAsJsonObject().getAsJsonArray("tools")) {
            JsonObject tool = t.getAsJsonObject();
            JsonObject snap = c.callObject("model.circuit", params("fileId", fileId, "circuitId",
                    tool.get("circuitId").getAsString()));
            List<String> parts = new ArrayList<>();
            for (JsonElement k : snap.getAsJsonArray("components")) {
                JsonObject o = k.getAsJsonObject();
                Map<String, String> attrs = new TreeMap<>();
                for (Map.Entry<String, JsonElement> a : o.getAsJsonObject("attrs").entrySet()) {
                    attrs.put(a.getKey(), a.getValue().getAsString());
                }
                parts.add("comp " + o.get("lib") + " " + o.get("name").getAsString() + " " + o.get("loc") + " "
                        + attrs);
            }
            for (JsonElement w : snap.getAsJsonArray("wires")) {
                JsonObject o = w.getAsJsonObject();
                String a = o.get("a").toString();
                String b = o.get("b").toString();
                parts.add("wire " + (a.compareTo(b) < 0 ? a + b : b + a));
            }
            Collections.sort(parts);
            out.put(tool.get("name").getAsString(), parts);
        }
        return out;
    }

    /** 모델과 넷(넷마다 그 선과 포트를 id 없이), 회로 이름별. */
    static Map<String, List<String>> netModel(Client c, String fileId) {
        Map<String, List<String>> out = model(c, fileId);
        JsonArray groups = c.call("model.library", params("fileId", fileId)).getAsJsonArray();
        for (JsonElement t : groups.get(0).getAsJsonObject().getAsJsonArray("tools")) {
            JsonObject tool = t.getAsJsonObject();
            JsonObject snap = c.callObject("model.circuit", params("fileId", fileId, "circuitId",
                    tool.get("circuitId").getAsString()));
            Map<String, String> key = new java.util.HashMap<>();
            for (JsonElement k : snap.getAsJsonArray("components")) {
                JsonObject o = k.getAsJsonObject();
                key.put(o.get("id").getAsString(), o.get("name").getAsString() + o.get("loc"));
            }
            for (JsonElement w : snap.getAsJsonArray("wires")) {
                JsonObject o = w.getAsJsonObject();
                String a = o.get("a").toString();
                String b = o.get("b").toString();
                key.put(o.get("id").getAsString(), a.compareTo(b) < 0 ? a + b : b + a);
            }
            List<String> nets = new ArrayList<>();
            for (JsonElement n : snap.getAsJsonArray("nets")) {
                List<String> members = new ArrayList<>();
                for (JsonElement w : n.getAsJsonObject().getAsJsonArray("wires")) {
                    members.add(key.get(w.getAsString()));
                }
                for (JsonElement pt : n.getAsJsonObject().getAsJsonArray("ports")) {
                    members.add(key.get(pt.getAsJsonArray().get(0).getAsString()) + "#" + pt.getAsJsonArray().get(1));
                }
                Collections.sort(members);
                nets.add("net " + n.getAsJsonObject().get("width") + " " + members);
            }
            Collections.sort(nets);
            List<String> all = new ArrayList<>(out.get(tool.get("name").getAsString()));
            all.addAll(nets);
            out.put(tool.get("name").getAsString(), all);
        }
        return out;
    }

    /** 학생이 하는 편집 몇 가지: 놓기, 속성, 옮기기, 선, 지우기. 마지막으로 놓은 부품 id. */
    static String edits(Client c, String fileId, String circuitId) {
        String and = c.callObject("edit.addComponent", params("fileId", fileId, "circuitId", circuitId, "lib", "Gates",
                "name", "AND Gate", "loc", xy(3000, 3000))).get("id").getAsString();
        String or = c.callObject("edit.addComponent", params("fileId", fileId, "circuitId", circuitId, "lib", "Gates",
                "name", "OR Gate", "loc", xy(3000, 3200))).get("id").getAsString();
        c.call("edit.setAttr", params("fileId", fileId, "circuitId", circuitId, "ids", List.of(and), "attr", "size",
                "value", "30"));
        c.call("edit.addWire", params("fileId", fileId, "circuitId", circuitId, "points",
                List.of(xy(3100, 3400), xy(3300, 3400))));
        c.call("edit.delete", params("fileId", fileId, "circuitId", circuitId, "ids", List.of(or)));
        String pin = c.callObject("edit.addComponent", params("fileId", fileId, "circuitId", circuitId, "lib",
                "Wiring", "name", "Pin", "loc", xy(3000, 3600))).get("id").getAsString();
        c.call("edit.move", params("fileId", fileId, "circuitId", circuitId, "ids", List.of(pin), "dx", 40, "dy", 0));
        return pin;
    }

    JsonObject open(File f) {
        return e.client.callObject("file.open", params("path", f.getPath()));
    }

    // ---- 쓰기: 지금 저장하면 쓰일 .circ 그대로, 그 파일 옆에만 ----

    /** tests/circ 아래 모든 파일과 ref-mips: 편집한 뒤의 복구 파일 = 같은 때의 저장(D-006 정규화, 새 부품 없으면 의미 동등). */
    @TestFactory
    Stream<DynamicTest> theRecoveryFileIsWhatASaveWouldWrite() throws Exception {
        List<File> files = new ArrayList<>(Fixtures.circFiles());
        files.add(Fixtures.REF_MIPS);
        return files.stream().map(f -> DynamicTest.dynamicTest(Fixtures.name(f), () -> {
            Path dir = Files.createTempDirectory(tmp, "w");
            File copy = Fixtures.copyWithSiblings(f, dir);
            List<String> before = names(dir);
            JsonObject opened = open(copy);
            String fileId = opened.get("fileId").getAsString();
            // 열기만 했으면(저장하지 않은 편집 없음) 쓰지 않는다
            JsonObject clean = e.client.callObject("file.recoverWrite", params("fileId", fileId));
            assertFalse(clean.get("written").getAsBoolean());
            assertEquals(recoveryOf(copy).getPath(), clean.get("path").getAsString());
            assertEquals(before, names(dir), "nothing written for a file with no unsaved edits");
            String circuit = opened.getAsJsonArray("circuits").get(0).getAsJsonObject().get("circuitId").getAsString();
            edits(e.client, fileId, circuit);
            JsonObject w = e.client.callObject("file.recoverWrite", params("fileId", fileId));
            assertTrue(w.get("written").getAsBoolean(), w.toString());
            File rf = recoveryOf(copy);
            assertEquals(rf.length(), w.get("bytes").getAsLong());
            List<String> want = new ArrayList<>(before);
            want.add(rf.getName());
            Collections.sort(want);
            assertEquals(want, names(dir), "the recovery file beside the student's file, and nothing else");
            assertEquals(read(f), read(copy), "the student's file is not touched");
            assertTrue(e.client.callObject("file.dirty", params("fileId", fileId)).get("dirty").getAsBoolean(),
                    "writing the recovery file does not save");
            // the same moment's save
            File saved = new File(dir.toFile(), "saved-" + f.getName());
            e.client.call("file.save", params("fileId", fileId, "path", saved.getPath()));
            assertEquals(CircNormalizer.normalize(read(saved)), CircNormalizer.normalize(read(rf)));
            if (!read(f).contains("jar#")) {
                assertEquals(Collections.<String>emptyList(), CircEquivalence.compare(saved, rf),
                        "the original 2.7.1 loader reads the recovery file as the save");
            }
            e.client.call("file.close", params("fileId", fileId));
        }));
    }

    @Test
    void aNewFileNeverSavedHasNoRecoveryFileAndNothingIsWritten() throws Exception {
        List<String> before = names(tmp);
        JsonObject f = e.client.callObject("file.new", params());
        String fileId = f.get("fileId").getAsString();
        edits(e.client, fileId, f.get("main").getAsString());
        JsonObject w = e.client.callObject("file.recoverWrite", params("fileId", fileId));
        assertTrue(w.get("path").isJsonNull(), w.toString());
        assertFalse(w.get("written").getAsBoolean());
        assertEquals(before, names(tmp));
        // Saved once: from then on it has one, beside it
        File mine = tmp.resolve("mine.circ").toFile();
        e.client.call("file.save", params("fileId", fileId, "path", mine.getPath()));
        e.client.call("edit.addComponent", params("fileId", fileId, "circuitId", f.get("main").getAsString(), "lib",
                "Gates", "name", "NOT Gate", "loc", xy(500, 500)));
        assertTrue(e.client.callObject("file.recoverWrite", params("fileId", fileId)).get("written").getAsBoolean());
        assertEquals(List.of("mine.circ", "mine.circ.hcs-recover"), names(tmp));
    }

    @Test
    void aReadOnlyFileHasNone() throws Exception {
        File copy = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "gates.circ"), tmp);
        String fileId = e.client.callObject("file.open", params("path", copy.getPath(), "readOnly", true))
                .get("fileId").getAsString();
        JsonObject w = e.client.callObject("file.recoverWrite", params("fileId", fileId));
        assertTrue(w.get("path").isJsonNull());
        assertFalse(recoveryOf(copy).exists());
    }

    /** 쓸 수 없는 폴더(읽기 전용 매체, 권한 없음)에는 복구 파일을 만들지 않고 조용히 건너뛴다(오류 아님). */
    @Test
    void anUnwritableFolderGetsNoneSilently() throws Exception {
        Path dir = Files.createDirectory(tmp.resolve("ro"));
        File copy = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "gates.circ"), dir);
        JsonObject opened = open(copy);
        String fileId = opened.get("fileId").getAsString();
        e.client.call("edit.addComponent", params("fileId", fileId, "circuitId", opened.get("main").getAsString(),
                "lib", "Gates", "name", "NOT Gate", "loc", xy(900, 900)));
        List<String> before = names(dir);
        assertTrue(dir.toFile().setWritable(false, false));
        try {
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.isWritable(dir), "root can write anywhere");
            JsonObject w = e.client.callObject("file.recoverWrite", params("fileId", fileId));
            assertFalse(w.get("written").getAsBoolean(), w.toString());
            assertEquals(before, names(dir));
        } finally {
            dir.toFile().setWritable(true, false);
        }
    }

    @Test
    void undoneBackToTheSavedFileTheRecoveryFileGoes() throws Exception {
        File copy = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "gates.circ"), tmp);
        JsonObject opened = open(copy);
        String fileId = opened.get("fileId").getAsString();
        e.client.call("edit.addComponent", params("fileId", fileId, "circuitId", opened.get("main").getAsString(),
                "lib", "Gates", "name", "NOT Gate", "loc", xy(900, 900)));
        assertTrue(e.client.callObject("file.recoverWrite", params("fileId", fileId)).get("written").getAsBoolean());
        assertTrue(recoveryOf(copy).isFile());
        e.client.call("edit.undo", params("fileId", fileId));
        JsonObject w = e.client.callObject("file.recoverWrite", params("fileId", fileId));
        assertFalse(w.get("written").getAsBoolean());
        assertFalse(recoveryOf(copy).exists(), "no unsaved edits, no recovery file");
    }

    @Test
    void aLeftoverPieceIsReplacedAndTheWriteIsWhole() throws Exception {
        File copy = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "gates.circ"), tmp);
        File part = new File(copy.getPath() + ".hcs-recover.tmp");
        Files.writeString(part.toPath(), "<half");
        JsonObject opened = open(copy);
        String fileId = opened.get("fileId").getAsString();
        e.client.call("edit.addComponent", params("fileId", fileId, "circuitId", opened.get("main").getAsString(),
                "lib", "Gates", "name", "NOT Gate", "loc", xy(900, 900)));
        e.client.call("file.recoverWrite", params("fileId", fileId));
        assertFalse(part.exists(), "the piece is written over and moved into place");
        assertTrue(read(recoveryOf(copy)).trim().endsWith("</project>"));
    }

    // ---- 열기: 복구 내용을 그 파일 자리에서 ----

    @Test
    void recoverOpensTheRecoveryContentInTheFilesPlaceAsUnsavedEdits() throws Exception {
        File copy = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "demo-datapath.circ"), tmp);
        String original = read(copy);
        JsonObject opened = open(copy);
        String fileId = opened.get("fileId").getAsString();
        edits(e.client, fileId, opened.get("main").getAsString());
        Map<String, List<String>> crashed = model(e.client, fileId);
        e.client.call("file.recoverWrite", params("fileId", fileId));

        // a new engine (the app started again)
        try (InProcess next = new InProcess()) {
            Client c = next.client;
            JsonObject r = c.callObject("file.open", params("path", copy.getPath(), "recovery", "recover"));
            String id = r.get("fileId").getAsString();
            assertEquals("demo-datapath", r.get("name").getAsString(), "the student's file's name");
            assertEquals(new JsonArray(), r.getAsJsonArray("messages"));
            assertEquals(crashed, model(c, id), "the model before the crash");
            assertTrue(c.callObject("file.dirty", params("fileId", id)).get("dirty").getAsBoolean(),
                    "recovered edits are unsaved");
            // an edit and its undo: still unsaved (the file on disk has none of it)
            c.call("edit.addComponent", params("fileId", id, "circuitId", r.get("main").getAsString(), "lib", "Gates",
                    "name", "NOT Gate", "loc", xy(3000, 4000)));
            c.call("edit.undo", params("fileId", id));
            assertTrue(c.callObject("file.dirty", params("fileId", id)).get("dirty").getAsBoolean());
            assertEquals(original, read(copy), "opening does not write the student's file");
            // Ctrl+S: into the student's file (not the recovery file)
            JsonObject saved = c.callObject("file.save", params("fileId", id));
            assertEquals(copy.getAbsolutePath(), saved.get("path").getAsString());
            assertEquals(CircNormalizer.normalize(read(recoveryOf(copy))), CircNormalizer.normalize(read(copy)));
            assertFalse(c.callObject("file.dirty", params("fileId", id)).get("dirty").getAsBoolean());
        }
    }

    /** demo-datapath에서 터널을 옮기는 편집(따라오는 선): 복구 내용, 그것을 저장한 파일을 다시 연 것, 죽기 전의 모델이 넷까지 같다. */
    @Test
    void theRecoveredModelSavedAndOpenedAgainIsTheSameToTheNets() throws Exception {
        File copy = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "demo-datapath.circ"), tmp);
        JsonObject opened = open(copy);
        String fileId = opened.get("fileId").getAsString();
        String main = opened.get("main").getAsString();
        JsonObject snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
        String tunnel = null;
        for (JsonElement k : snap.getAsJsonArray("components")) {
            if (k.getAsJsonObject().get("name").getAsString().equals("Tunnel")) {
                tunnel = k.getAsJsonObject().get("id").getAsString();
                break;
            }
        }
        e.client.call("edit.move", params("fileId", fileId, "circuitId", main, "ids", List.of(tunnel), "dx", 0, "dy", 10));
        e.client.call("edit.addWire", params("fileId", fileId, "circuitId", main, "points",
                List.of(xy(1000, 1000), xy(1100, 1000))));
        e.client.call("edit.undo", params("fileId", fileId));
        e.client.call("edit.redo", params("fileId", fileId));
        Map<String, List<String>> crashed = netModel(e.client, fileId);
        e.client.call("file.recoverWrite", params("fileId", fileId));
        try (InProcess next = new InProcess()) {
            String id = next.client.callObject("file.open", params("path", copy.getPath(), "recovery", "recover"))
                    .get("fileId").getAsString();
            assertEquals(crashed, netModel(next.client, id), "recovered");
            next.client.call("file.save", params("fileId", id));
            next.client.call("file.close", params("fileId", id));
            String again = next.client.callObject("file.open", params("path", copy.getPath())).get("fileId").getAsString();
            assertEquals(crashed, netModel(next.client, again), "saved and opened again");
        }
    }

    @Test
    void recoverWithoutARecoveryFileIsAFileError() throws Exception {
        File copy = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "gates.circ"), tmp);
        Client.Failure f = assertThrows(Client.Failure.class,
                () -> e.client.call("file.open", params("path", copy.getPath(), "recovery", "recover")));
        assertEquals(2, f.code);
        assertEquals("notFound", f.reason());
        Client.Failure bad = assertThrows(Client.Failure.class,
                () -> e.client.call("file.open", params("path", copy.getPath(), "recovery", "maybe")));
        assertEquals(-32602, bad.code);
    }

    @Test
    void discardOpensTheSavedFileAndThenRemovesTheRecoveryFile() throws Exception {
        File copy = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "gates.circ"), tmp);
        Files.writeString(recoveryOf(copy).toPath(), read(copy).replace("</project>", "<!-- other --></project>"));
        Map<String, List<String>> plain;
        String plainId = open(copy).get("fileId").getAsString();
        plain = model(e.client, plainId);
        e.client.call("file.close", params("fileId", plainId));
        JsonObject r = e.client.callObject("file.open", params("path", copy.getPath(), "recovery", "discard"));
        assertEquals(plain, model(e.client, r.get("fileId").getAsString()));
        assertFalse(e.client.callObject("file.dirty", params("fileId", r.get("fileId").getAsString())).get("dirty")
                .getAsBoolean());
        assertFalse(recoveryOf(copy).exists());
    }

    @Test
    void aFileThatCannotBeOpenedKeepsItsRecoveryFileOnDiscard() throws Exception {
        File broken = tmp.resolve("broken.circ").toFile();
        Files.writeString(broken.toPath(), "not a circuit");
        Files.writeString(recoveryOf(broken).toPath(), "<project/>");
        Client.Failure f = assertThrows(Client.Failure.class,
                () -> e.client.call("file.open", params("path", broken.getPath(), "recovery", "discard")));
        assertEquals(2, f.code);
        assertTrue(recoveryOf(broken).isFile(), "kept: it may be the only good copy");
    }

    // ---- 앱이 맡을 때(engine.hello recoveryFiles): 저장·닫기·정상 종료에 지우고, 화면이 사라지면 써 둔다 ----

    void managed() {
        e.client.call("engine.hello", params("client", "test", "version", "0", "recoveryFiles", true));
    }

    /** 편집하고 복구 파일을 쓴 열린 파일. */
    JsonObject editedAndWritten(File copy) {
        JsonObject opened = open(copy);
        String fileId = opened.get("fileId").getAsString();
        e.client.call("edit.addComponent", params("fileId", fileId, "circuitId", opened.get("main").getAsString(),
                "lib", "Gates", "name", "NOT Gate", "loc", xy(900, 900)));
        assertTrue(e.client.callObject("file.recoverWrite", params("fileId", fileId)).get("written").getAsBoolean());
        assertTrue(recoveryOf(copy).isFile());
        return opened;
    }

    @Test
    void savingRemovesTheRecoveryFileBesideTheOldAndTheNewPath() throws Exception {
        managed();
        File copy = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "gates.circ"), tmp);
        String fileId = editedAndWritten(copy).get("fileId").getAsString();
        e.client.call("file.save", params("fileId", fileId));
        assertFalse(recoveryOf(copy).exists());
        // Save As: the old path's and the new path's
        e.client.call("edit.addComponent", params("fileId", fileId, "circuitId",
                open(copy).get("main").getAsString(), "lib", "Gates", "name", "NOT Gate", "loc", xy(1100, 900)));
        e.client.call("file.recoverWrite", params("fileId", fileId));
        File other = tmp.resolve("other.circ").toFile();
        Files.writeString(recoveryOf(other).toPath(), "<project/>");
        e.client.call("file.save", params("fileId", fileId, "path", other.getPath()));
        assertFalse(recoveryOf(copy).exists());
        assertFalse(recoveryOf(other).exists());
    }

    @Test
    void closingRemovesItUnlessAskedToKeepIt() throws Exception {
        managed();
        File a = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "gates.circ"), tmp);
        e.client.call("file.close", params("fileId", editedAndWritten(a).get("fileId").getAsString()));
        assertFalse(recoveryOf(a).exists(), "closed without saving: the student chose so");
        e.client.call("file.close", params("fileId", editedAndWritten(a).get("fileId").getAsString(),
                "keepRecovery", true));
        assertTrue(recoveryOf(a).isFile(), "the main process's own close during a recovery keeps it");
    }

    @Test
    void aNormalShutdownRemovesThem() throws Exception {
        managed();
        File a = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "gates.circ"), tmp);
        editedAndWritten(a);
        e.client.call("engine.shutdown", params());
        assertEquals("shutdown", e.awaitExit(10_000));
        assertFalse(recoveryOf(a).exists());
    }

    @Test
    void whenTheAppIsGoneTheUnsavedFilesGetTheirRecoveryFiles() throws Exception {
        managed();
        Path d1 = Files.createDirectory(tmp.resolve("one"));
        Path d2 = Files.createDirectory(tmp.resolve("two"));
        File dirty = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "demo-datapath.circ"), d1);
        File clean = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "gates.circ"), d2);
        JsonObject opened = open(dirty);
        String fileId = opened.get("fileId").getAsString();
        open(clean);
        JsonObject created = e.client.callObject("file.new", params());
        // edits after the last recovery write: the engine writes them as it ends
        edits(e.client, fileId, opened.get("main").getAsString());
        edits(e.client, created.get("fileId").getAsString(), created.get("main").getAsString());
        Map<String, List<String>> before = model(e.client, fileId);
        List<String> d2Before = names(d2);
        e.client.closeInput(); // the main process is gone: its end of the pipe closes
        assertEquals("stdin closed", e.awaitExit(10_000));
        assertTrue(recoveryOf(dirty).isFile());
        assertEquals(d2Before, names(d2), "a file with no unsaved edits gets none");
        try (InProcess next = new InProcess()) {
            JsonObject r = next.client.callObject("file.open", params("path", dirty.getPath(), "recovery", "recover"));
            assertEquals(before, model(next.client, r.get("fileId").getAsString()), "every edit up to the end");
        }
    }

    @Test
    void anEngineTheAppDidNotAskLeavesThemAlone() throws Exception {
        File a = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "gates.circ"), tmp);
        JsonObject opened = editedAndWritten(a);
        e.client.call("file.close", params("fileId", opened.get("fileId").getAsString()));
        assertTrue(recoveryOf(a).isFile(), "not managed: closing does not remove it");
        JsonObject reopened = open(a);
        String again = reopened.get("fileId").getAsString();
        e.client.call("edit.addComponent", params("fileId", again, "circuitId", reopened.get("main").getAsString(),
                "lib", "Gates", "name", "NOT Gate", "loc", xy(1300, 900)));
        Files.delete(recoveryOf(a).toPath());
        e.client.closeInput();
        e.awaitExit(10_000);
        assertFalse(recoveryOf(a).exists(), "not managed: nothing written as it ends");
    }

    @Test
    void theRecoveryFileNameIsTheFilesNameAndTheSuffix() {
        File f = new File(tmp.toFile(), "lab 3 (최종).circ");
        assertEquals(new File(tmp.toFile(), "lab 3 (최종).circ.hcs-recover").getAbsolutePath(),
                RecoveryFiles.of(f).getPath());
        assertNotEquals(RecoveryFiles.of(f), f);
    }
}
