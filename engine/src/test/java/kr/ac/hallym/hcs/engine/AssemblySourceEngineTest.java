/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.regress.CircNormalizer;

/**
 * .s 경로가 남은 옛 .circ(D-141): 엔진이 전처럼 열고 속성 글을 그대로 보이며, {@code mips.facts}가 사실
 * {@code assemblySource}(두 언어 문장, 부품, 속성 글)를 주고, 엔진이 저장해도 원래 파일과 같다(D-006 기준). 화면은 N-16.
 */
class AssemblySourceEngineTest {
    static final String KO = "이 파일은 .s 파일을 가리킵니다. Hallym MIPS에서 Export executable image (.hmx) 단추로 내보낸 파일을"
            + " 불러오세요.";

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

    /** ref-mips.circ의 Instruction Memory·Data Memory에 원조 저장 모양 그대로 source 속성을 넣은 옛 파일. */
    File oldFile(String name, String imSource, String dmSource) throws Exception {
        String text = new String(Files.readAllBytes(Fixtures.REF_MIPS.toPath()), StandardCharsets.UTF_8);
        String im = "<comp lib=\"7\" loc=\"(6400,400)\" name=\"Instruction Memory\"/>";
        assertEquals(1, text.split(java.util.regex.Pattern.quote(im), -1).length - 1, "one Instruction Memory");
        text = text.replace(im, "<comp lib=\"7\" loc=\"(6400,400)\" name=\"Instruction Memory\">\n      <a name=\"source\" val=\""
                + imSource + "\"/>\n    </comp>");
        java.util.regex.Matcher dm = java.util.regex.Pattern.compile(
                "(<comp lib=\"7\" loc=\"\\([0-9,]+\\)\" name=\"Data Memory\">\n)((?:      <a [^\n]*\n)*)(    </comp>)")
                .matcher(text);
        assertTrue(dm.find(), "the Data Memory with its two region attributes");
        text = text.substring(0, dm.start()) + dm.group(1) + dm.group(2) + "      <a name=\"source\" val=\"" + dmSource
                + "\"/>\n" + dm.group(3) + text.substring(dm.end());
        File out = tmp.resolve(name).toFile();
        Files.write(out.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return out;
    }

    JsonArray facts(String fileId) {
        return e.client.callObject("mips.facts", params("fileId", fileId)).getAsJsonArray("facts");
    }

    @Test
    void anOldAssemblySourceOpensGivesTheFactAndSavesUnchanged() throws Exception {
        File old = oldFile("old-lab.circ", "prog/sum.s", "C:\\lab\\SUM.ASM");
        JsonObject opened = e.client.callObject("file.open", params("path", old.getPath()));
        String fileId = opened.get("fileId").getAsString();
        assertEquals(0, opened.getAsJsonArray("messages").size(), opened.toString());
        JsonObject main = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId",
                opened.get("main").getAsString()));
        JsonObject im = Fixtures.byName(main.getAsJsonArray("components"), "Instruction Memory").get(0);
        JsonObject dm = Fixtures.byName(main.getAsJsonArray("components"), "Data Memory").get(0);
        assertEquals("prog/sum.s", im.getAsJsonObject("attrs").get("source").getAsString(), "the attribute is read");

        JsonArray facts = facts(fileId);
        assertEquals(1, facts.size(), facts.toString());
        JsonObject f = facts.get(0).getAsJsonObject();
        assertEquals("assemblySource", f.get("id").getAsString());
        assertEquals(KO, f.get("ko").getAsString());
        assertEquals("This file points to a .s file. Load the file exported with Export executable image (.hmx) in"
                + " Hallym MIPS.", f.get("en").getAsString());
        List<String> comps = new ArrayList<>();
        for (JsonElement c : f.getAsJsonArray("components")) {
            comps.add(c.getAsString());
        }
        List<String> sources = new ArrayList<>();
        for (JsonElement c : f.getAsJsonArray("sources")) {
            sources.add(c.getAsString());
        }
        assertEquals(2, comps.size());
        assertEquals(comps.indexOf(im.get("id").getAsString()), sources.indexOf("prog/sum.s"));
        assertEquals(comps.indexOf(dm.get("id").getAsString()), sources.indexOf("C:\\lab\\SUM.ASM"));

        File out = tmp.resolve("resaved.circ").toFile();
        e.client.call("file.save", params("fileId", fileId, "path", out.getPath()));
        String before = new String(Files.readAllBytes(old.toPath()), StandardCharsets.UTF_8);
        String after = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertEquals(CircNormalizer.normalize(before), CircNormalizer.normalize(after), "saved as before");
    }

    /** .hmx 경로(또는 source 없음)는 사실이 아니다. 속성 글이 .hmx로 바뀌면 사실이 없어진다. */
    @Test
    void anImageSourceIsNotAFact() throws Exception {
        File file = oldFile("new-lab.circ", "prog/sum.hmx", "prog/sum.hmx");
        JsonObject opened = e.client.callObject("file.open", params("path", file.getPath()));
        assertEquals(0, facts(opened.get("fileId").getAsString()).size());
        JsonObject ref = e.client.callObject("file.open", params("path", Fixtures.REF_MIPS.getPath()));
        assertEquals(0, facts(ref.get("fileId").getAsString()).size());

        File old = oldFile("half-lab.circ", "prog/sum.s", "prog/sum.hmx");
        JsonObject o = e.client.callObject("file.open", params("path", old.getPath()));
        String fileId = o.get("fileId").getAsString();
        assertEquals(1, facts(fileId).get(0).getAsJsonObject().getAsJsonArray("components").size());
        JsonObject main = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId",
                o.get("main").getAsString()));
        String im = Fixtures.byName(main.getAsJsonArray("components"), "Instruction Memory").get(0).get("id")
                .getAsString();
        e.client.call("edit.setAttr", params("fileId", fileId, "circuitId", o.get("main").getAsString(), "ids",
                List.of(im), "attr", "source", "value", "prog/sum.hmx"));
        assertEquals(0, facts(fileId).size(), "the fact goes away when the source is an executable image");
    }
}
