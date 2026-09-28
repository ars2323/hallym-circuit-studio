/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 속성 표·빠른 속성 창·우클릭 메뉴의 화면 시험 자료(N-10, D-157, {@code electron/tests/fixtures/attributes.json}).
 * {@code ./gradlew :engine:canvasFixtures}가 캔버스 자료와 함께 만들고 CI가 다시 만들어 비교한다. 가짜 엔진이 이것으로
 * model.attributes·model.menu에 진짜 엔진의 줄(원조 표시 이름, 편집기 종류, 선택지, 빠른 속성, 숨은 키)과 사실을 준다.
 * <ul>
 * <li>{@code kinds}: 부품 목록의 모든 도구(Hallym MIPS 포함)를 새 파일에 하나씩 놓고 고른 뒤의 표(값은 그 도구의
 * 기본값), 열쇠 {@code lib/name}. demo-datapath의 서브회로 인스턴스는 {@code /회로 이름}.</li>
 * <li>{@code tools}: 든 도구의 표(원조 AttrTableToolModel): 모든 도구와 Base의 Text Tool.</li>
 * <li>{@code circuit}: 새 파일 main의 회로 속성 표.</li>
 * <li>{@code menus}: demo-datapath 주 회로의 부품마다 가운데, 선마다 가운데, 빈 곳 한 점의 model.menu 답(아무것도
 * 고르지 않은 채). id는 캔버스 자료와 같은 id(k·w·c)다.</li>
 * </ul>
 */
final class AttrFixtures {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    private AttrFixtures() {
    }

    static void write(Path out, Path repo) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("source", "./gradlew :engine:canvasFixtures (engine AttrFixtures, N-10)");
        try (InProcess e = new InProcess()) {
            JsonObject f = e.client.callObject("file.new", params());
            String fileId = f.get("fileId").getAsString();
            String main = f.get("main").getAsString();
            root.add("circuit", e.client.callObject("model.attributes", params("fileId", fileId, "circuitId", main,
                    "circuit", true)));
            Map<String, JsonElement> kinds = new TreeMap<>();
            Map<String, JsonElement> tools = new TreeMap<>();
            for (JsonElement g : e.client.call("model.library", params("fileId", fileId)).getAsJsonArray()) {
                JsonObject go = g.getAsJsonObject();
                if (go.get("lib").isJsonNull()) {
                    continue; // this file's circuits: see demo-datapath below
                }
                String lib = go.get("lib").getAsString();
                for (JsonElement t : go.getAsJsonArray("tools")) {
                    String name = t.getAsJsonObject().get("name").getAsString();
                    String key = lib + "/" + name;
                    tools.put(key, e.client.callObject("model.attributes", params("fileId", fileId, "lib", lib, "name", name)));
                    JsonObject r = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main,
                            "lib", lib, "name", name, "loc", new int[] {400, 400}));
                    String id = r.get("id").getAsString();
                    JsonArray one = new JsonArray();
                    one.add(id);
                    e.client.callObject("edit.select", params("fileId", fileId, "circuitId", main, "ids", one));
                    JsonObject table = e.client.callObject("model.attributes", params("fileId", fileId, "circuitId", main));
                    table.remove("circuitId");
                    kinds.put(key, table);
                    e.client.callObject("edit.delete", params("fileId", fileId, "circuitId", main, "ids", one));
                }
            }
            tools.put("Base/Text Tool", e.client.callObject("model.attributes", params("fileId", fileId, "lib", "Base",
                    "name", "Text Tool")));
            e.client.call("file.close", params("fileId", fileId));

            // demo-datapath: its subcircuits' tables and the main circuit's menu facts (the canvas fixture's ids)
            File demo = repo.resolve("tests/circ/demo-datapath.circ").toFile();
            JsonObject d = e.client.callObject("file.open", params("path", demo.getAbsolutePath()));
            String dId = d.get("fileId").getAsString();
            String dMain = d.get("main").getAsString();
            List<String> circuitIds = new ArrayList<>();
            List<JsonObject> snaps = new ArrayList<>();
            for (JsonElement c : d.getAsJsonArray("circuits")) {
                String id = c.getAsJsonObject().get("circuitId").getAsString();
                circuitIds.add(id);
                snaps.add(e.client.callObject("model.circuit", params("fileId", dId, "circuitId", id)));
            }
            CanvasFixtures.Canon canon = new CanvasFixtures.Canon();
            for (int i = 0; i < circuitIds.size(); i++) {
                canon.circuits.put(circuitIds.get(i), "c" + (i + 1));
            }
            for (JsonObject s : snaps) {
                canon.snapshot(s);
            }
            JsonObject mainSnap = snaps.get(circuitIds.indexOf(dMain));
            Map<String, String> netMap = canon.nets.get(dMain);
            Map<String, JsonElement> menus = new TreeMap<>();
            String fx = canon.circuits.get(dMain);
            for (JsonElement x : mainSnap.getAsJsonArray("components")) {
                JsonObject o = x.getAsJsonObject();
                String id = o.get("id").getAsString();
                if (o.has("subcircuit") && o.get("lib").isJsonNull()) {
                    JsonArray one = new JsonArray();
                    one.add(id);
                    JsonObject table = e.client.callObject("model.attributes", params("fileId", dId, "circuitId", dMain, "ids", one));
                    table.remove("circuitId");
                    String sub = o.get("name").getAsString();
                    kinds.put("/" + sub, FlowFixtures.rename(table, canon, netMap));
                }
                JsonArray b = o.getAsJsonArray("bounds");
                int[] mid = {b.get(0).getAsInt() + b.get(2).getAsInt() / 2, b.get(1).getAsInt() + b.get(3).getAsInt() / 2};
                JsonObject m = e.client.callObject("model.menu", params("fileId", dId, "circuitId", dMain, "at", mid, "id", id));
                menus.put(fx + "|" + canon.parts.get(id), FlowFixtures.rename(m, canon, netMap));
            }
            for (JsonElement x : mainSnap.getAsJsonArray("wires")) {
                JsonObject o = x.getAsJsonObject();
                String id = o.get("id").getAsString();
                JsonArray a = o.getAsJsonArray("a");
                JsonArray b = o.getAsJsonArray("b");
                int[] mid = {(a.get(0).getAsInt() + b.get(0).getAsInt()) / 2, (a.get(1).getAsInt() + b.get(1).getAsInt()) / 2};
                JsonObject m = e.client.callObject("model.menu", params("fileId", dId, "circuitId", dMain, "at", mid, "id", id));
                menus.put(fx + "|" + canon.parts.get(id), FlowFixtures.rename(m, canon, netMap));
            }
            JsonObject empty = e.client.callObject("model.menu", params("fileId", dId, "circuitId", dMain, "at",
                    new int[] {-500, -500}));
            menus.put(fx + "|empty", FlowFixtures.rename(empty, canon, netMap));
            root.add("kinds", PRETTY.toJsonTree(kinds));
            JsonObject textTool = new JsonObject();
            textTool.add("Base/Text Tool", tools.get("Base/Text Tool"));
            root.add("tools", textTool);
            JsonObject m = new JsonObject();
            m.add("demo-datapath", PRETTY.toJsonTree(menus));
            root.add("menus", m);
        }
        Files.writeString(out.resolve("attributes.json"), text(root), StandardCharsets.UTF_8);
    }

    /** 한 항목(부품 종류, 메뉴의 사실)이 한 줄: 자료가 작고 바뀐 곳만 diff에 나온다. */
    static String text(JsonObject root) {
        Gson line = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
        StringBuilder b = new StringBuilder("{\n");
        b.append("  \"source\": ").append(line.toJson(root.get("source"))).append(",\n");
        b.append("  \"circuit\": ").append(line.toJson(root.get("circuit"))).append(",\n");
        for (String group : new String[] {"kinds", "tools"}) {
            b.append("  \"").append(group).append("\": {\n");
            List<String> rows = new ArrayList<>();
            for (Map.Entry<String, JsonElement> x : root.getAsJsonObject(group).entrySet()) {
                rows.add("    " + line.toJson(x.getKey()) + ": " + line.toJson(x.getValue()));
            }
            b.append(String.join(",\n", rows)).append("\n  },\n");
        }
        b.append("  \"menus\": {\n");
        List<String> files = new ArrayList<>();
        for (Map.Entry<String, JsonElement> f : root.getAsJsonObject("menus").entrySet()) {
            List<String> rows = new ArrayList<>();
            for (Map.Entry<String, JsonElement> x : f.getValue().getAsJsonObject().entrySet()) {
                rows.add("      " + line.toJson(x.getKey()) + ": " + line.toJson(x.getValue()));
            }
            files.add("    " + line.toJson(f.getKey()) + ": {\n" + String.join(",\n", rows) + "\n    }");
        }
        b.append(String.join(",\n", files)).append("\n  }\n}\n");
        return b.toString();
    }
}
