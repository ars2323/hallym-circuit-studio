/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
 * N-21(D-162): Undo History(model.history, edit.history: v1 UndoHistory의 목록과 옮기기), Project › Analyze Circuit
 * (model.analyze: 원조 Analyze·AnalyzerModel), Get Circuit Statistics(model.statistics: 원조 FileStatistics), Create
 * Submission(file.submission: v1 Submission 규칙).
 */
class ProjectToolsTest {
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

    JsonObject call(String method, Object... kv) {
        return e.client.callObject(method, params(kv));
    }

    String open(File f) {
        return call("file.open", "path", f.getPath()).get("fileId").getAsString();
    }

    static List<String> kinds(JsonObject history) {
        List<String> out = new ArrayList<>();
        for (JsonElement r : history.getAsJsonArray("rows")) {
            out.add(r.getAsJsonObject().get("kind").getAsString());
        }
        return out;
    }

    static List<Integer> moves(JsonObject history) {
        List<Integer> out = new ArrayList<>();
        for (JsonElement r : history.getAsJsonArray("rows")) {
            out.add(r.getAsJsonObject().get("moves").getAsInt());
        }
        return out;
    }

    int parts(String fileId, String circuitId) {
        return call("model.circuit", "fileId", fileId, "circuitId", circuitId).getAsJsonArray("components").size();
    }

    // ---- Undo History ----

    @Test
    void undoHistoryListsTheOriginalLogAndJumpsAsV1Did() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        File host = tmp.resolve("history.circ").toFile();
        CircuitBuilder.save(f, host);
        byte[] original = Files.readAllBytes(host.toPath());
        JsonObject opened = call("file.open", "path", host.getPath());
        String fileId = opened.get("fileId").getAsString();
        String main = opened.get("main").getAsString();
        JsonObject h = call("model.history", "fileId", fileId);
        assertEquals(List.of("start", "now"), kinds(h), "nothing done yet");
        assertEquals(List.of(0, 0), moves(h));
        for (int i = 0; i < 3; i++) {
            call("edit.addComponent", "fileId", fileId, "circuitId", main, "lib", "Gates", "name", "AND Gate",
                    "loc", new int[] {200 + 100 * i, 200});
        }
        h = call("model.history", "fileId", fileId);
        assertEquals(List.of("start", "undo", "undo", "undo", "now"), kinds(h));
        assertEquals(List.of(-3, -2, -1, 0, 0), moves(h), "a row takes the log back to just after its action");
        assertEquals("Add AND Gate", h.getAsJsonArray("rows").get(1).getAsJsonObject().get("name").getAsString());
        assertEquals(3, parts(fileId, main));

        // the second undo row: back to just after the first action
        JsonObject r = call("edit.history", "fileId", fileId, "moves", -2, "circuitId", main);
        assertTrue(r.get("changed").getAsBoolean());
        assertEquals(1, parts(fileId, main));
        h = call("model.history", "fileId", fileId);
        assertEquals(List.of("start", "undo", "now", "redo", "redo"), kinds(h));
        assertEquals(List.of(-1, 0, 0, 1, 2), moves(h), "redo rows: the next one first");
        // the last redo row: everything done again
        call("edit.history", "fileId", fileId, "moves", 2, "circuitId", main);
        assertEquals(3, parts(fileId, main));
        // Start of History: all undone; the file is as it was, and saves as the original did
        call("edit.history", "fileId", fileId, "moves", -3);
        assertEquals(0, parts(fileId, main));
        assertFalse(call("file.dirty", "fileId", fileId).get("dirty").getAsBoolean());
        File saved = tmp.resolve("saved.circ").toFile();
        call("file.save", "fileId", fileId, "path", saved.getPath());
        assertArrayEquals(original, Files.readAllBytes(saved.toPath()), "all undone saves the original bytes");
        // more than the log holds: as many as there are; nothing to do: unchanged
        assertTrue(call("edit.history", "fileId", fileId, "moves", 50).get("changed").getAsBoolean());
        assertEquals(3, parts(fileId, main));
        JsonObject none = call("edit.history", "fileId", fileId, "moves", 1);
        assertFalse(none.get("changed").getAsBoolean());
        assertEquals("nothing", none.get("outcome").getAsString());
        assertFalse(call("edit.history", "fileId", fileId, "moves", 0).get("changed").getAsBoolean());
        assertEquals(-32602, e.client.fail("edit.history", params("fileId", fileId, "moves", 1_000_000)).code);
        // a new action after an undo drops the redo rows (the original's log, the fork's RedoStack)
        call("edit.history", "fileId", fileId, "moves", -1);
        call("edit.addComponent", "fileId", fileId, "circuitId", main, "lib", "Gates", "name", "OR Gate",
                "loc", new int[] {200, 400});
        h = call("model.history", "fileId", fileId);
        assertEquals(List.of("start", "undo", "undo", "undo", "now"), kinds(h));
        assertEquals("Add OR Gate", h.getAsJsonArray("rows").get(3).getAsJsonObject().get("name").getAsString());
    }

    // ---- Analyze Circuit ----

    /** a XOR b = y, wired (no tunnels): pins on the gate's ports' rows. */
    File xorFile() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit c = f.getCircuit("main");
        CircuitBuilder cb = new CircuitBuilder(f, c);
        Component g = cb.add("Gates", "XOR Gate", 300, 200);
        Location out = CircuitBuilder.port(g, 0), in1 = CircuitBuilder.port(g, 1), in2 = CircuitBuilder.port(g, 2);
        cb.add("Wiring", "Pin", in1.getX() - 60, in1.getY(), "label", "a");
        cb.add("Wiring", "Pin", in2.getX() - 60, in2.getY(), "label", "b");
        cb.add("Wiring", "Pin", out.getX() + 60, out.getY(), "facing", "west", "output", "true", "label", "y");
        cb.wire(Location.create(in1.getX() - 60, in1.getY()), in1);
        cb.wire(Location.create(in2.getX() - 60, in2.getY()), in2);
        cb.wire(out, Location.create(out.getX() + 60, out.getY()));
        cb.commit();
        // a multiplexer: no expression (the original computes the table by simulation)
        Circuit mux = new Circuit("mux");
        f.addCircuit(mux);
        cb = new CircuitBuilder(f, mux);
        Component m = cb.add("Plexers", "Multiplexer", 300, 200, "enable", "false");
        Location mo = CircuitBuilder.port(m, 3); // inputs 0 and 1, select 2, output last
        cb.add("Wiring", "Pin", CircuitBuilder.port(m, 0).getX() - 60, CircuitBuilder.port(m, 0).getY(), "label", "a");
        cb.add("Wiring", "Pin", CircuitBuilder.port(m, 1).getX() - 60, CircuitBuilder.port(m, 1).getY(), "label", "b");
        cb.wire(Location.create(CircuitBuilder.port(m, 0).getX() - 60, CircuitBuilder.port(m, 0).getY()),
                CircuitBuilder.port(m, 0));
        cb.wire(Location.create(CircuitBuilder.port(m, 1).getX() - 60, CircuitBuilder.port(m, 1).getY()),
                CircuitBuilder.port(m, 1));
        Location sel = CircuitBuilder.port(m, 2);
        cb.add("Wiring", "Pin", sel.getX(), sel.getY() + 40, "facing", "north", "label", "s");
        cb.wire(sel, Location.create(sel.getX(), sel.getY() + 40));
        cb.add("Wiring", "Pin", mo.getX() + 60, mo.getY(), "facing", "west", "output", "true", "label", "y");
        cb.wire(mo, Location.create(mo.getX() + 60, mo.getY()));
        cb.commit();
        // a 4-bit input: the original refuses (analyzeMultibitInputError)
        Circuit wide = new Circuit("wide");
        f.addCircuit(wide);
        cb = new CircuitBuilder(f, wide);
        cb.add("Wiring", "Pin", 100, 100, "width", "4", "label", "d");
        cb.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "label", "q");
        cb.commit();
        // no pins at all
        f.addCircuit(new Circuit("empty"));
        File out1 = tmp.resolve("analyze.circ").toFile();
        CircuitBuilder.save(f, out1);
        return out1;
    }

    Map<String, String> circuits(JsonObject opened) {
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonElement c : opened.getAsJsonArray("circuits")) {
            out.put(c.getAsJsonObject().get("name").getAsString(), c.getAsJsonObject().get("circuitId").getAsString());
        }
        return out;
    }

    static List<String> row(JsonObject a, int i) {
        List<String> out = new ArrayList<>();
        for (JsonElement x : a.getAsJsonObject("table").getAsJsonArray("rows").get(i).getAsJsonArray()) {
            out.add(x.getAsString());
        }
        return out;
    }

    @Test
    void analyzeCircuitIsTheOriginalsTableAndExpressions() throws Exception {
        File f = xorFile();
        JsonObject opened = call("file.open", "path", f.getPath());
        String fileId = opened.get("fileId").getAsString();
        Map<String, String> ids = circuits(opened);
        File first = tmp.resolve("first.circ").toFile();
        call("file.save", "fileId", fileId, "path", first.getPath());
        byte[] before = Files.readAllBytes(first.toPath());

        JsonObject xor = call("model.analyze", "fileId", fileId, "circuitId", ids.get("main"));
        assertEquals("main", xor.get("circuit").getAsString());
        assertEquals("[\"a\",\"b\"]", xor.getAsJsonArray("inputs").toString(), "top-down order (Analyze.getPinLabels)");
        assertEquals("[\"y\"]", xor.getAsJsonArray("outputs").toString());
        assertEquals("expression", xor.get("source").getAsString());
        assertFalse(xor.has("problem"));
        assertEquals(4, xor.getAsJsonObject("table").getAsJsonArray("rows").size());
        assertEquals(List.of("00", "0"), row(xor, 0));
        assertEquals(List.of("01", "1"), row(xor, 1));
        assertEquals(List.of("10", "1"), row(xor, 2));
        assertEquals(List.of("11", "0"), row(xor, 3));
        JsonObject y = xor.getAsJsonArray("expressions").get(0).getAsJsonObject();
        assertEquals("y", y.get("output").getAsString());
        assertTrue(y.get("expression").getAsString().contains("a") && y.get("expression").getAsString().contains("b"),
                y.toString());
        assertEquals("~a b + a ~b", y.get("sop").getAsString(), "the original's minimal sum of products");
        assertEquals("(a + b) (~a + ~b)", y.get("pos").getAsString(), "and product of sums");

        JsonObject mux = call("model.analyze", "fileId", fileId, "circuitId", ids.get("mux"));
        assertEquals("table", mux.get("source").getAsString(), "no expression for a multiplexer: simulated");
        assertTrue(mux.has("expressionFailure"), mux.toString());
        assertEquals("cannotHandle", mux.get("expressionReason").getAsString());
        assertEquals("Multiplexer", mux.get("expressionPart").getAsString());
        assertEquals(8, mux.getAsJsonObject("table").getAsJsonArray("rows").size());
        List<String> ins = new ArrayList<>();
        mux.getAsJsonArray("inputs").forEach(x -> ins.add(x.getAsString()));
        // every row: y is the input the select picks
        for (int i = 0; i < 8; i++) {
            List<String> r = row(mux, i);
            char s = r.get(0).charAt(ins.indexOf("s")), a = r.get(0).charAt(ins.indexOf("a")),
                    b = r.get(0).charAt(ins.indexOf("b"));
            assertEquals(String.valueOf(s == '0' ? a : b), r.get(1), "row " + i + " " + ins + " " + r);
        }

        JsonObject wide = call("model.analyze", "fileId", fileId, "circuitId", ids.get("wide"));
        assertEquals("multibitInput", wide.get("problem").getAsString());
        assertEquals("d", wide.get("pin").getAsString());
        assertTrue(wide.get("source").isJsonNull());
        assertEquals("noInputs", call("model.analyze", "fileId", fileId, "circuitId", ids.get("empty"))
                .get("problem").getAsString());

        // read only: the file is not dirty and saves as it was
        assertFalse(call("file.dirty", "fileId", fileId).get("dirty").getAsBoolean());
        File saved = tmp.resolve("analyzed.circ").toFile();
        call("file.save", "fileId", fileId, "path", saved.getPath());
        assertArrayEquals(before, Files.readAllBytes(saved.toPath()));
    }

    // ---- Get Circuit Statistics ----

    @Test
    void statisticsAreTheOriginalsCounts() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit half = new Circuit("half");
        f.addCircuit(half);
        CircuitBuilder cb = new CircuitBuilder(f, half);
        cb.add("Gates", "AND Gate", 200, 100);
        cb.add("Gates", "XOR Gate", 200, 200);
        cb.commit();
        cb = new CircuitBuilder(f, f.getCircuit("main"));
        cb.addSubcircuit(half, 200, 100);
        cb.addSubcircuit(half, 200, 300);
        cb.add("Gates", "AND Gate", 400, 400);
        cb.commit();
        File file = tmp.resolve("stats.circ").toFile();
        CircuitBuilder.save(f, file);
        JsonObject opened = call("file.open", "path", file.getPath());
        String fileId = opened.get("fileId").getAsString();
        JsonObject s = call("model.statistics", "fileId", fileId, "circuitId", circuits(opened).get("main"));
        Map<String, int[]> rows = new LinkedHashMap<>();
        Map<String, String> libs = new LinkedHashMap<>();
        for (JsonElement r : s.getAsJsonArray("rows")) {
            JsonObject o = r.getAsJsonObject();
            libs.put(o.get("component").getAsString(), o.get("library").getAsString());
            rows.put(o.get("component").getAsString(), new int[] {o.get("simple").getAsInt(),
                    o.get("unique").getAsInt(), o.get("recursive").getAsInt()});
        }
        assertArrayEquals(new int[] {2, 2, 2}, rows.get("half"), rows.keySet().toString());
        assertArrayEquals(new int[] {1, 2, 3}, rows.get("AND Gate"), "simple 1, unique 1 + 1 in half, 1 + 2 × 1");
        assertArrayEquals(new int[] {0, 1, 2}, rows.get("XOR Gate"));
        JsonObject with = s.getAsJsonObject("with");
        assertEquals(3, with.get("simple").getAsInt());
        JsonObject without = s.getAsJsonObject("without");
        assertEquals(1, without.get("simple").getAsInt(), "without the project's subcircuits");
        assertEquals("Gates", libs.get("AND Gate"), "the library's display name");
        assertEquals("stats", libs.get("half"), "the project's own circuits: the file's name");
    }

    /**
     * 원조 FileStatistics와 같은 셈·같은 줄 차례(tests/circ 전부의 회로마다): 엔진은 도구를 새로 불러오지 않을 뿐이다
     * (따로 읽은 파일에 원조 compute를 부르고 견준다).
     */
    @Test
    void statisticsAreTheOriginalFileStatisticsForEveryTestCircuit() throws Exception {
        int circuits = 0;
        for (File f : Fixtures.circFiles()) {
            LogisimFile orig = new kr.ac.hallym.hcs.engine.doc.EngineLoader().openLogisimFile(f);
            JsonObject opened = call("file.open", "path", f.getPath(), "readOnly", true);
            String fileId = opened.get("fileId").getAsString();
            for (JsonElement ce : opened.getAsJsonArray("circuits")) {
                JsonObject c = ce.getAsJsonObject();
                JsonObject got = call("model.statistics", "fileId", fileId, "circuitId", c.get("circuitId").getAsString());
                com.cburch.logisim.file.FileStatistics want = com.cburch.logisim.file.FileStatistics.compute(orig,
                        orig.getCircuit(c.get("name").getAsString()));
                List<String> w = new ArrayList<>();
                for (com.cburch.logisim.file.FileStatistics.Count k : want.getCounts()) {
                    w.add(k.getFactory().getDisplayName() + " " + k.getLibrary().getDisplayName() + " " + k.getSimpleCount()
                            + " " + k.getUniqueCount() + " " + k.getRecursiveCount());
                }
                List<String> g = new ArrayList<>();
                for (JsonElement r : got.getAsJsonArray("rows")) {
                    JsonObject o = r.getAsJsonObject();
                    g.add(o.get("component").getAsString() + " " + o.get("library").getAsString() + " " + o.get("simple")
                            + " " + o.get("unique") + " " + o.get("recursive"));
                }
                assertEquals(w, g, Fixtures.name(f) + " " + c.get("name"));
                assertEquals(want.getTotalWithSubcircuits().getRecursiveCount(), got.getAsJsonObject("with").get("recursive")
                        .getAsInt());
                assertEquals(want.getTotalWithoutSubcircuits().getSimpleCount(), got.getAsJsonObject("without")
                        .get("simple").getAsInt());
                circuits++;
            }
            e.client.call("file.close", params("fileId", fileId));
        }
        assertTrue(circuits >= 30, "circuits: " + circuits);
    }

    // ---- Create Submission ----

    /** ref-mips as lab.circ, its Instruction Memory's program at prog/example.hmx (beside it). */
    File labFile(Path dir, String programPath) throws Exception {
        String t = new String(Files.readAllBytes(Fixtures.REF_MIPS.toPath()), StandardCharsets.UTF_8);
        String im = "<comp lib=\"7\" loc=\"(6400,400)\" name=\"Instruction Memory\"/>";
        assertTrue(t.contains(im));
        t = t.replace(im, "<comp lib=\"7\" loc=\"(6400,400)\" name=\"Instruction Memory\">\n      <a name=\"source\" val=\""
                + programPath + "\"/>\n    </comp>");
        File lab = dir.resolve("lab.circ").toFile();
        Files.write(lab.toPath(), t.getBytes(StandardCharsets.UTF_8));
        return lab;
    }

    /** mips.reloaded after the file opened (the program the memory's source names, loaded once). */
    void awaitProgramLoaded(String fileId) {
        e.client.awaitNotification("mips.reloaded", n -> n.get("fileId").getAsString().equals(fileId));
    }

    static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        a.forEach(x -> out.add(x.getAsString()));
        return out;
    }

    @Test
    void createSubmissionZipsTheFileAndWhatItPointsAtAsV1Did() throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("lab"));
        File lab = labFile(dir, "prog/example.hmx");
        Path prog = Files.createDirectories(dir.resolve("prog"));
        File hmx = new File(Fixtures.REF_MIPS.getParentFile().getParentFile(), "hmx/example.hmx");
        Files.copy(hmx.toPath(), prog.resolve("example.hmx"));
        long mtime = lab.lastModified();
        byte[] labBytes = Files.readAllBytes(lab.toPath());
        String fileId = open(lab);
        // the program the Instruction Memory points at is loaded when the file opens (D-147): not the student's edit
        awaitProgramLoaded(fileId);
        assertFalse(call("file.dirty", "fileId", fileId).get("dirty").getAsBoolean(), "opening leaves the file clean");
        JsonObject plan = call("file.submission", "fileId", fileId);
        assertTrue(plan.get("saved").getAsBoolean());
        assertFalse(plan.get("dirty").getAsBoolean());
        assertEquals(List.of("lab.circ", "hcs-mips.jar", "prog/example.hmx"), strings(plan.getAsJsonArray("files")),
                "the .circ, its libraries, its program, by their paths from the .circ's folder");
        assertTrue(plan.get("bundledJar").getAsBoolean(), "no hcs-mips.jar beside it: the bundled one under that name");
        assertEquals(List.of(), strings(plan.getAsJsonArray("missing")));
        assertEquals(0, plan.get("probes").getAsInt());
        assertEquals(0, plan.get("messages").getAsInt(), "ref-mips works");
        assertEquals("lab-submission.zip", plan.get("suggested").getAsString());
        assertFalse(plan.has("written"), "without a path nothing is written");
        assertFalse(dir.resolve("lab-submission.zip").toFile().exists());

        JsonObject w = call("file.submission", "fileId", fileId, "path", dir.resolve("hand-in").toString());
        JsonObject written = w.getAsJsonObject("written");
        assertEquals("hand-in.zip", written.get("name").getAsString(), ".zip is added");
        assertEquals(3, written.get("count").getAsInt());
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream z = new ZipInputStream(Files.newInputStream(dir.resolve("hand-in.zip")))) {
            for (ZipEntry en; (en = z.getNextEntry()) != null;) {
                entries.put(en.getName(), z.readAllBytes());
            }
        }
        assertEquals(List.of("lab.circ", "hcs-mips.jar", "prog/example.hmx"), new ArrayList<>(entries.keySet()));
        assertArrayEquals(Files.readAllBytes(lab.toPath()), entries.get("lab.circ"), "the saved file as on disk");
        assertArrayEquals(Files.readAllBytes(hmx.toPath()), entries.get("prog/example.hmx"));
        assertTrue(entries.get("hcs-mips.jar").length > 1000);
        // nothing else left in the folder (the lab-PC rule: the zip and the student's files only)
        List<String> left = new ArrayList<>();
        try (var s = Files.list(dir)) {
            s.forEach(p -> left.add(p.getFileName().toString()));
        }
        left.sort(null);
        assertEquals(List.of("hand-in.zip", "lab.circ", "prog"), left);
        assertArrayEquals(labBytes, Files.readAllBytes(lab.toPath()), "the student's .circ is not written");
        assertEquals(mtime, lab.lastModified(), "nor touched");

        // an edit not saved yet: said so (the zip holds the saved file); a Probe left: counted
        String main = call("file.info", "fileId", fileId).get("main").getAsString();
        call("edit.addComponent", "fileId", fileId, "circuitId", main, "lib", "Wiring", "name", "Probe",
                "loc", new int[] {100, 9000});
        JsonObject dirty = call("file.submission", "fileId", fileId);
        assertTrue(dirty.get("dirty").getAsBoolean());
        assertEquals(1, dirty.get("probes").getAsInt());
    }

    @Test
    void aSubmissionSaysWhatItCouldNotTake() throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("lab2"));
        File lab = labFile(dir, "../elsewhere/prog.hmx");
        Files.copy(Fixtures.REF_MIPS.toPath().resolveSibling("ref-mips.circ"), dir.resolve("unused.circ"));
        Files.copy(new File(Fixtures.REF_MIPS.getParentFile().getParentFile(), "hmx/example.hmx").toPath(),
                dir.resolve("hcs-mips.jar.not"));
        String fileId = open(lab);
        JsonObject plan = call("file.submission", "fileId", fileId);
        assertEquals(List.of("../elsewhere/prog.hmx"), strings(plan.getAsJsonArray("missing")),
                "outside the .circ's folder: it would not open from the unzipped folder");
        assertEquals(List.of("lab.circ", "hcs-mips.jar"), strings(plan.getAsJsonArray("files")));
        // a new file never saved: nothing to zip
        String fresh = call("file.new").get("fileId").getAsString();
        JsonObject never = call("file.submission", "fileId", fresh);
        assertFalse(never.get("saved").getAsBoolean());
        assertEquals(List.of(), strings(never.getAsJsonArray("files")));
        assertEquals(-32602, e.client.fail("file.submission", params("fileId", fresh, "path",
                dir.resolve("x.zip").toString())).code);
    }
}
