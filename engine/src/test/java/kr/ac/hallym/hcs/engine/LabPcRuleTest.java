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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 실습실 PC 규칙의 엔진 쪽(N-19, D-152, D-134 10항): 실제 하위 프로세스가 모든 메서드 묶음을 한 번씩 돌아도
 * java.util.prefs를 디스크에서 읽지도 쓰지도 않는다. 임시 HOME에 원조 Logisim의 디스크 설정(틱 64 Hz, 사용자
 * 템플릿으로 File › New)을 JDK 기본 방식으로 써 두어도 엔진은 기본값(1 Hz, 원조 기본 틀)이고, 끝난 뒤 HOME(과 그 아래
 * XDG 폴더)은 JDK 글꼴 목록 캐시 말고 그대로다. {@code java.util.prefs.userRoot}·{@code systemRoot}로 준 폴더도 생기지
 * 않는다. Windows(레지스트리 {@code HKCU\Software\JavaSoft\Prefs})는 CI setup-e2e의 변화 0 검사가 본다(D-148).
 *
 * <p>엔진에 새 메서드 묶음이 생기면 {@link #groupsCalledOnPurpose}가 실패한다: 그 묶음을 여기서 뜻 있게 한 번 부르게
 * 더한다. 새 메서드는 묶음이 같아도 아래 훑기가 한 번씩 부른다(인자가 틀려 오류로 답해도 그 메서드의 코드는 돈다).
 */
class LabPcRuleTest {
    @TempDir
    Path tmp;

    static String group(String method) {
        return method.substring(0, method.indexOf('.'));
    }

    static SortedSet<String> groups(Set<String> methods) {
        SortedSet<String> ret = new TreeSet<>();
        methods.forEach(m -> ret.add(group(m)));
        return ret;
    }

    /** 이 엔진이 답하는 메서드 전부(같은 JVM의 엔진에 물어본다). */
    static SortedSet<String> engineMethods() throws Exception {
        try (InProcess e = new InProcess()) {
            return e.server.methods();
        }
    }

    /** 부른 메서드를 적고, 오류 응답은 받아들인다(메서드는 돌았다). */
    static final class Caller {
        final Client client;
        final SortedSet<String> called = new TreeSet<>();
        final List<String> failed = new ArrayList<>();

        Caller(Client client) {
            this.client = client;
        }

        JsonElement must(String method, JsonObject params) {
            called.add(method);
            return client.call(method, params);
        }

        JsonElement any(String method, JsonObject params) {
            called.add(method);
            try {
                return client.call(method, params);
            } catch (Client.Failure f) {
                failed.add(method + " " + f.getMessage());
                return null;
            }
        }
    }

    /** 뜻 있게 부르는 것: 학생이 하는 한 차례(열기·편집·시뮬레이션·진단·MIPS·Cycle View·복구 파일·저장·닫기). */
    static void studentSession(Caller c, File work, File hmx) {
        JsonObject hello = c.must("engine.hello", params("client", "lab-pc-rule", "version", "0", "recoveryFiles", true))
                .getAsJsonObject();
        assertEquals("hcs-engine", hello.get("engine").getAsString());
        JsonObject created = c.must("file.new", params()).getAsJsonObject();
        JsonArray circuits = created.getAsJsonArray("circuits");
        assertEquals(1, circuits.size(), "the original's default template, not the student's custom one: " + circuits);
        assertEquals("main", circuits.get(0).getAsJsonObject().get("name").getAsString());
        String newId = created.get("fileId").getAsString();
        JsonObject st = c.must("sim.state", params("fileId", newId)).getAsJsonObject();
        assertEquals(1.0, st.get("hz").getAsDouble(), "the original's tick frequency on disk (64 Hz) is not read");

        File mips = new File(work, "ref-mips.circ");
        JsonObject opened = c.must("file.open", params("path", mips.getPath())).getAsJsonObject();
        String fileId = opened.get("fileId").getAsString();
        String main = opened.get("main").getAsString();
        JsonObject snap = c.must("model.circuit", params("fileId", fileId, "circuitId", main)).getAsJsonObject();
        c.must("model.library", params("fileId", fileId));
        String and = c.must("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib", "Gates", "name",
                "AND Gate", "loc", xy(4000, 4000))).getAsJsonObject().get("id").getAsString();
        c.must("edit.setAttr", params("fileId", fileId, "circuitId", main, "ids", List.of(and), "attr", "size",
                "value", "30"));
        c.must("edit.addWire", params("fileId", fileId, "circuitId", main, "points",
                List.of(xy(4100, 4200), xy(4300, 4200))));
        c.must("edit.move", params("fileId", fileId, "circuitId", main, "ids", List.of(and), "dx", 20, "dy", 0));
        c.must("edit.undo", params("fileId", fileId));
        c.must("edit.redo", params("fileId", fileId));
        c.any("edit.delete", params("fileId", fileId, "circuitId", main, "ids", List.of(and)));
        c.must("file.dirty", params("fileId", fileId));
        c.must("sim.watch", params("fileId", fileId, "circuitId", main));
        c.must("sim.run", params("fileId", fileId, "on", true, "hz", 64));
        c.must("sim.run", params("fileId", fileId, "on", false));
        c.must("sim.enable", params("fileId", fileId, "on", true));
        c.must("sim.cycles", params("fileId", fileId, "n", 2));
        c.must("sim.reset", params("fileId", fileId));
        c.must("diag.list", params("fileId", fileId));
        c.must("find.query", params("fileId", fileId, "text", "PC"));
        JsonArray nets = snap.getAsJsonArray("nets");
        c.any("trace.origin", params("fileId", fileId, "circuitId", main, "netId",
                nets.size() > 0 ? nets.get(0).getAsJsonObject().get("id").getAsString() : "n0"));
        c.must("mips.facts", params("fileId", fileId));
        c.any("mips.load", params("fileId", fileId, "path", hmx.getPath()));
        c.must("mips.console", params("fileId", fileId));
        c.any("mips.disasm", params("fileId", fileId));
        c.any("mips.reload", params("fileId", fileId));
        c.must("record.state", params("fileId", fileId));
        c.must("record.table", params("fileId", fileId));
        c.must("record.registers", params("fileId", fileId));
        c.must("record.memory", params("fileId", fileId));
        c.must("record.instruction", params("fileId", fileId));
        c.must("record.fieldPaths", params("fileId", fileId, "circuitId", main));
        c.must("file.recoverWrite", params("fileId", fileId));
        c.must("file.save", params("fileId", newId, "path", new File(work, "mine.circ").getPath()));
        c.must("file.close", params("fileId", newId));
    }

    /** 엔진의 메서드 묶음마다 위에서 뜻 있게 부른 것이 있다(새 묶음이 생기면 여기서 실패: 위에 더한다). */
    @Test
    void groupsCalledOnPurpose() throws Exception {
        SortedSet<String> missing = groups(engineMethods());
        missing.removeAll(groups(new TreeSet<>(List.of(PURPOSEFUL))));
        assertEquals(new TreeSet<>(), missing, "a method group the lab-PC test does not call on purpose");
    }

    /** {@link #studentSession}이 부르는 메서드(묶음 검사용, 같은 차례). */
    static final String[] PURPOSEFUL = {"engine.hello", "file.new", "sim.state", "file.open", "model.circuit",
        "model.library", "edit.addComponent", "edit.setAttr", "edit.addWire", "edit.move", "edit.undo", "edit.redo",
        "edit.delete", "file.dirty", "sim.watch", "sim.run", "sim.enable", "sim.cycles", "sim.reset", "diag.list", "find.query",
        "trace.origin", "mips.facts", "mips.load", "mips.console", "mips.disasm", "mips.reload", "record.state",
        "record.table", "record.registers", "record.memory", "record.instruction", "record.fieldPaths",
        "file.recoverWrite", "file.save", "file.close"};

    @Test
    void everyMethodGroupRunsWithoutDiskPreferencesAndLeavesHomeAsItWas() throws Exception {
        File home = tmp.resolve("home").toFile();
        assertTrue(home.mkdirs());
        for (String xdg : new String[] {".config", ".cache", ".local/share", ".local/state"}) {
            assertTrue(new File(home, xdg).mkdirs());
        }
        // The original Logisim's settings on disk, the JDK's own way: 64 Hz, and File > New from a custom template.
        File template = tmp.resolve("custom.templ").toFile();
        Files.writeString(template.toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                + "<project source=\"2.7.1\" version=\"1.0\">\n  <main name=\"from-disk\"/>\n"
                + "  <circuit name=\"from-disk\"/>\n  <circuit name=\"second\"/>\n</project>\n");
        String cp = new File(PrefsTool.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        SubprocessTest.run(home, SubprocessTest.JAVA, "-Duser.home=" + home, "-cp", cp, PrefsTool.class.getName(),
                "write", "64", template.getPath());
        assertEquals("64.0 2", SubprocessTest.run(home, SubprocessTest.JAVA, "-Duser.home=" + home, "-cp", cp,
                PrefsTool.class.getName(), "read").trim(), "a plain JVM reads the original Logisim preferences");
        Map<String, String> before = SubprocessTest.tree(home);

        File work = tmp.resolve("work").toFile();
        assertTrue(work.mkdirs());
        Files.copy(Fixtures.REF_MIPS.toPath(), work.toPath().resolve("ref-mips.circ"));
        File hmx = new File(Fixtures.CIRC_DIR.getParentFile(), "hmx/example.hmx");
        assertTrue(hmx.isFile(), hmx.getPath());
        File userRoot = tmp.resolve("prefs-user").toFile();
        File systemRoot = tmp.resolve("prefs-system").toFile();
        SortedSet<String> all = engineMethods();
        Caller c;
        try (SubprocessTest.Proc p = new SubprocessTest.Proc(home, List.of("-Djava.util.prefs.userRoot=" + userRoot,
                "-Djava.util.prefs.systemRoot=" + systemRoot))) {
            c = new Caller(p.client);
            studentSession(c, work, hmx);
            assertEquals(new TreeSet<>(List.of(PURPOSEFUL)), c.called, "PURPOSEFUL lists what studentSession calls");
            // Every other method, once, on a file (an error answer is fine: the method ran)
            String fileId = c.must("file.open", params("path", new File(work, "ref-mips.circ").getPath()))
                    .getAsJsonObject().get("fileId").getAsString();
            for (String m : all) {
                if (!c.called.contains(m) && !m.equals("engine.shutdown")) {
                    c.any(m, params("fileId", fileId, "circuitId", "c1"));
                }
            }
            c.must("engine.shutdown", params());
            assertEquals(0, p.awaitExit());
        }
        assertEquals(all, c.called, "every method the engine answers was called");
        Map<String, String> after = SubprocessTest.tree(home);
        after.keySet().removeIf(k -> k.startsWith(".java/fonts/") || k.equals(".java/fonts"));
        assertEquals(before, after, "nothing but the JDK font cache is written under HOME (or its XDG folders)");
        assertFalse(userRoot.exists(), "no preferences written where java.util.prefs.userRoot points");
        assertFalse(systemRoot.exists(), "nor systemRoot");
        List<String> left = new ArrayList<>();
        try (java.util.stream.Stream<Path> s = Files.list(work.toPath())) {
            s.forEach(f -> left.add(f.getFileName().toString()));
        }
        left.sort(null);
        assertEquals(List.of("mine.circ", "ref-mips.circ"), left,
                "the student's own files only (the recovery file goes at a normal end)");
    }
}
