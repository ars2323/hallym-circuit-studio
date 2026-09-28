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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 회로·모양의 시험 자료(N-11, D-153): demo-datapath의 회로마다 엔진이 답한 model.appearance·model.ports·
 * model.instances를 CanvasFixtures와 같은 id(회로·부품을 차례대로 다시 매긴 것)로 적는다
 * ({@code electron/tests/fixtures/appearance/demo-datapath.json}). 가짜 엔진이 그대로 돌려줘 e2e·스크린숏의 모양 편집
 * 화면·Port Order 창·인스턴스 안내 띠가 진짜 엔진의 모양과 포트를 보인다. {@code ./gradlew :engine:canvasFixtures}가
 * 함께 만들고 CI가 다시 만들어 비교한다.
 */
final class AppearanceFixtures {
    private static final Gson GSON = kr.ac.hallym.hcs.engine.rpc.Server.GSON;

    private AppearanceFixtures() {
    }

    static void write(File file, Path target) throws Exception {
        try (InProcess e = new InProcess()) {
            JsonObject f = e.client.callObject("file.open", params("path", file.getAbsolutePath()));
            String fileId = f.get("fileId").getAsString();
            List<String> circuitIds = new ArrayList<>();
            List<String> names = new ArrayList<>();
            List<JsonObject> snaps = new ArrayList<>();
            for (JsonElement c : f.getAsJsonArray("circuits")) {
                String id = c.getAsJsonObject().get("circuitId").getAsString();
                circuitIds.add(id);
                names.add(c.getAsJsonObject().get("name").getAsString());
                snaps.add(e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", id)));
            }
            // CanvasFixtures.circuit와 같은 차례로 다시 매긴다(가짜 엔진이 그 자료의 id를 쓴다)
            CanvasFixtures.Canon canon = new CanvasFixtures.Canon();
            for (int i = 0; i < circuitIds.size(); i++) {
                canon.circuits.put(circuitIds.get(i), "c" + (i + 1));
            }
            for (JsonObject s : snaps) {
                canon.snapshot(s);
            }
            JsonObject circuits = new JsonObject();
            for (int i = 0; i < circuitIds.size(); i++) {
                String id = circuitIds.get(i);
                JsonObject o = new JsonObject();
                JsonObject app = e.client.callObject("model.appearance", params("fileId", fileId, "circuitId", id));
                app.remove("fileId");
                o.add("appearance", FlowFixtures.rename(app, canon, java.util.Map.of()));
                o.add("ports", FlowFixtures.rename(e.client.callObject("model.ports", params("fileId", fileId, "circuitId", id)),
                        canon, java.util.Map.of()));
                o.add("instances", FlowFixtures.rename(e.client.callObject("model.instances", params("fileId", fileId,
                        "circuitId", id)), canon, java.util.Map.of()));
                circuits.add(names.get(i), o);
            }
            JsonObject root = new JsonObject();
            root.addProperty("source", "./gradlew :engine:canvasFixtures (N-11, D-153): file.open, model.appearance, "
                    + "model.ports, model.instances of " + file.getName() + ", ids as in circuits/"
                    + file.getName().replace(".circ", ".json"));
            root.add("circuits", circuits);
            Files.createDirectories(target.getParent());
            Files.write(target, (GSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    static Path target(Path out, File file) {
        return out.resolve("appearance").resolve(file.getName().replace(".circ", ".json"));
    }
}
