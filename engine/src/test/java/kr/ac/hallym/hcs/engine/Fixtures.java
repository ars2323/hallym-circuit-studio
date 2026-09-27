/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** 테스트 회로와 스냅숏 도우미. */
final class Fixtures {
    static final File CIRC_DIR = new File(System.getProperty("hcs.circDir"));
    static final File REF_MIPS = new File(System.getProperty("hcs.refMips"));

    private Fixtures() {
    }

    /** tests/circ의 .circ 전부(이름 순). */
    static List<File> circFiles() {
        List<File> ret = new ArrayList<>();
        File[] fs = CIRC_DIR.listFiles((d, n) -> n.endsWith(".circ"));
        if (fs != null) {
            java.util.Arrays.sort(fs);
            ret.addAll(java.util.Arrays.asList(fs));
        }
        return ret;
    }

    /** MIPS 부품 라이브러리(JAR)를 쓰는 파일인가. */
    static boolean usesMips(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8)
                .contains("kr.ac.hallym.hcs.mips.MipsLibrary");
    }

    /** 클럭 → 8비트 카운터 → 출력 핀 q(터널로 잇는다). */
    static File counter(Path dir) throws IOException {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), dir.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component clk = b.add("Wiring", "Clock", 100, 100);
        Component ctr = b.add("Memory", "Counter", 300, 200, "width", "8");
        b.tunnel(clk, 0, "clk");
        b.tunnel(ctr, 2, "clk");
        b.output("q", 8, 500, 300);
        b.tunnel(ctr, 0, "q");
        b.commit();
        File out = dir.resolve("counter.circ").toFile();
        CircuitBuilder.save(f, out);
        return out;
    }

    /** NOT 게이트 둘이 고리를 이룬 발진 회로. */
    static File oscillator(Path dir) throws IOException {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), dir.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component n1 = b.add("Gates", "NOT Gate", 200, 100);
        b.tunnel(n1, 0, "a");
        b.tunnel(n1, 1, "a");
        b.commit();
        File out = dir.resolve("osc.circ").toFile();
        CircuitBuilder.save(f, out);
        return out;
    }

    // ---- 스냅숏 읽기 ----

    static JsonObject byId(JsonArray items, String id) {
        for (JsonElement e : items) {
            if (e.getAsJsonObject().get("id").getAsString().equals(id)) {
                return e.getAsJsonObject();
            }
        }
        return null;
    }

    static List<JsonObject> byName(JsonArray items, String name) {
        List<JsonObject> ret = new ArrayList<>();
        for (JsonElement e : items) {
            if (e.getAsJsonObject().get("name").getAsString().equals(name)) {
                ret.add(e.getAsJsonObject());
            }
        }
        return ret;
    }

    static int[] portLoc(JsonObject comp, int i) {
        JsonArray loc = comp.getAsJsonArray("ports").get(i).getAsJsonObject().getAsJsonArray("loc");
        return new int[] {loc.get(0).getAsInt(), loc.get(1).getAsInt()};
    }

    /** 부품 id·포트 번호가 든 넷의 id. */
    static String netOf(JsonArray nets, String compId, int port) {
        for (JsonElement e : nets) {
            for (JsonElement p : e.getAsJsonObject().getAsJsonArray("ports")) {
                JsonArray pa = p.getAsJsonArray();
                if (pa.get(0).getAsString().equals(compId) && pa.get(1).getAsInt() == port) {
                    return e.getAsJsonObject().get("id").getAsString();
                }
            }
        }
        return null;
    }
}
