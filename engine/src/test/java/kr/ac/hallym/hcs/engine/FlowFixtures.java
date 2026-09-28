/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * 캔버스 덧그림의 시험 자료(N-15, D-151): demo-datapath에서 엔진이 답한 trace.influence·flow.path·flow.activePath·
 * trace.net·record.fieldPaths를 CanvasFixtures와 같은 id(회로·부품·선·넷을 차례대로 다시 매긴 것)로 적는다
 * ({@code electron/tests/fixtures/flow/demo-datapath.json}). 가짜 엔진이 그대로 돌려줘 e2e·스크린숏이 진짜 엔진의
 * 경로를 같은 픽셀로 그린다. {@code ./gradlew :engine:canvasFixtures}가 함께 만들고 CI가 다시 만들어 비교한다.
 * <ul>
 * <li>influence: 주 회로의 부품마다 앞·뒤·양쪽(레지스터에서 멈춤, 끝까지), PC는 레지스터 넘어·깊이 1부터 끝까지,
 * PC와 MUX 사이. 열쇠 {@code 회로|시작 id들|mode|through|depth}.</li>
 * <li>flow: 부품마다 모든 출력에서 앞으로·모든 입력에서 뒤로, 출력이 둘 이상이면 출력마다, Active Path Only가 다른 답을
 * 내는 것, PC·Instruction Memory는 레지스터 넘어, 선마다 가운데를 누른 앞으로. 열쇠
 * {@code 회로|부품 또는 선|port|backward|through|active}.</li>
 * <li>activePath·fieldPaths: Reset에서 사이클 0부터 {@link CanvasFixtures#CYCLES}까지.</li>
 * <li>net: 주 회로의 넷마다.</li>
 * </ul>
 */
final class FlowFixtures {
    private static final Gson GSON = kr.ac.hallym.hcs.engine.rpc.Server.GSON;
    /** 이름·글자 칸은 id로 바꾸지 않는다. */
    private static final Set<String> TEXT = Set.of("label", "name", "text", "mode", "kind", "format", "group");

    private FlowFixtures() {
    }

    static void write(File file, Path target) throws Exception {
        try (InProcess e = new InProcess()) {
            JsonObject f = e.client.callObject("file.open", params("path", file.getAbsolutePath()));
            String fileId = f.get("fileId").getAsString();
            String main = f.get("main").getAsString();
            List<String> circuitIds = new ArrayList<>();
            List<JsonObject> snaps = new ArrayList<>();
            for (JsonElement c : f.getAsJsonArray("circuits")) {
                String id = c.getAsJsonObject().get("circuitId").getAsString();
                circuitIds.add(id);
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
            JsonObject mainSnap = snaps.get(circuitIds.indexOf(main));
            Map<String, String> netMap = canon.nets.get(main);
            String fx = canon.circuits.get(main);
            JsonArray comps = mainSnap.getAsJsonArray("components");
            String pc = null;
            String mux = null;
            String im = null;
            for (JsonElement x : comps) {
                JsonObject o = x.getAsJsonObject();
                String label = o.getAsJsonObject("attrs").has("label") ? o.getAsJsonObject("attrs").get("label").getAsString() : "";
                if (o.get("name").getAsString().equals("Register") && label.equals("PC")) {
                    pc = o.get("id").getAsString();
                }
                if (o.get("name").getAsString().equals("Multiplexer")) {
                    mux = o.get("id").getAsString();
                }
                if (o.get("name").getAsString().equals("Instruction Memory")) {
                    im = o.get("id").getAsString();
                }
            }
            // 값: 주 회로를 보고 Reset에서
            int mark = e.client.mark();
            e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
            e.client.awaitNotificationAfter(mark, "sim.values", v -> true);
            reset(e, fileId);

            Map<String, JsonElement> influence = new TreeMap<>();
            for (JsonElement x : comps) {
                String id = x.getAsJsonObject().get("id").getAsString();
                for (String mode : new String[] {"forward", "backward", "both"}) {
                    influence.put(key(fx, canon, List.of(id), mode, false, -1), rename(inf(e, fileId, main, List.of(id), mode, false, -1), canon, netMap));
                }
            }
            for (boolean through : new boolean[] {false, true}) {
                JsonObject all = inf(e, fileId, main, List.of(pc), "forward", through, -1);
                influence.put(key(fx, canon, List.of(pc), "forward", through, -1), rename(all, canon, netMap));
                for (int depth = 1; depth < all.get("maxDepth").getAsInt(); depth++) {
                    influence.put(key(fx, canon, List.of(pc), "forward", through, depth),
                            rename(inf(e, fileId, main, List.of(pc), "forward", through, depth), canon, netMap));
                }
                influence.put(key(fx, canon, List.of(pc), "both", through, -1), rename(inf(e, fileId, main, List.of(pc), "both", through, -1), canon, netMap));
                influence.put(key(fx, canon, List.of(pc), "backward", through, -1), rename(inf(e, fileId, main, List.of(pc), "backward", through, -1), canon, netMap));
            }
            influence.put(key(fx, canon, List.of(pc, mux), "between", false, -1), rename(inf(e, fileId, main, List.of(pc, mux), "between", false, -1), canon, netMap));

            Map<String, JsonElement> flow = new TreeMap<>();
            for (JsonElement x : comps) {
                JsonObject o = x.getAsJsonObject();
                String id = o.get("id").getAsString();
                List<Integer> outs = new ArrayList<>();
                for (JsonElement p : o.getAsJsonArray("ports")) {
                    if (!p.getAsJsonObject().get("dir").getAsString().equals("in")) {
                        outs.add(p.getAsJsonObject().get("i").getAsInt());
                    }
                }
                for (boolean back : new boolean[] {false, true}) {
                    JsonObject plain = path(e, fileId, main, id, -1, back, false, false);
                    flow.put(fkey(fx, canon.parts.get(id), -1, back, false, false), rename(plain, canon, netMap));
                    JsonObject active = path(e, fileId, main, id, -1, back, false, true);
                    if (!active.equals(plain)) {
                        flow.put(fkey(fx, canon.parts.get(id), -1, back, false, true), rename(active, canon, netMap));
                    }
                }
                if (outs.size() > 1) {
                    for (int port : outs) {
                        flow.put(fkey(fx, canon.parts.get(id), port, false, false, false), rename(path(e, fileId, main, id, port, false, false, false), canon, netMap));
                    }
                }
            }
            for (String id : new String[] {pc, im}) {
                flow.put(fkey(fx, canon.parts.get(id), -1, false, true, false), rename(path(e, fileId, main, id, -1, false, true, false), canon, netMap));
            }
            for (JsonElement w : mainSnap.getAsJsonArray("wires")) {
                JsonObject o = w.getAsJsonObject();
                String id = o.get("id").getAsString();
                JsonObject r = e.client.callObject("flow.path", params("fileId", fileId, "circuitId", main, "wire", id));
                flow.put(fkey(fx, canon.parts.get(id), -1, false, false, false), rename(r, canon, netMap));
            }

            Map<String, JsonElement> nets = new TreeMap<>();
            for (JsonElement n : mainSnap.getAsJsonArray("nets")) {
                String id = n.getAsJsonObject().get("id").getAsString();
                nets.put(netMap.get(id), rename(e.client.callObject("trace.net", params("fileId", fileId, "circuitId", main, "netId", id)), canon, netMap));
            }

            // 사이클마다(Reset에서): 활성 경로와 필드 경로. 위의 요청은 읽기만 하므로 시뮬레이션은 아직 Reset 그대로다
            // (다시 Reset하면 값이 바뀌지 않아 sim.values가 오지 않을 수 있다)
            List<String> active = new ArrayList<>();
            List<String> fields = new ArrayList<>();
            for (int k = 0; k <= CanvasFixtures.CYCLES; k++) {
                if (k > 0) {
                    int m = e.client.mark();
                    e.client.callObject("sim.cycles", params("fileId", fileId, "n", 1));
                    final long want = k;
                    e.client.awaitNotificationAfter(m, "sim.state", st -> st.get("cycle").getAsLong() >= want
                            && !st.get("ticking").getAsBoolean());
                    CanvasFixtures.settle(e, fileId);   // the tick's propagation done (and recorded), not a guess
                }
                active.add(GSON.toJson(rename(e.client.callObject("flow.activePath", params("fileId", fileId, "circuitId", main)), canon, netMap)));
                JsonObject fp = e.client.callObject("record.fieldPaths", params("fileId", fileId, "circuitId", main));
                fp.remove("fileId");
                fields.add(GSON.toJson(rename(fp, canon, netMap)));
            }
            e.client.call("file.close", params("fileId", fileId));

            StringBuilder sb = new StringBuilder();
            sb.append("{\"source\":\"./gradlew :engine:canvasFixtures (D-151): trace.influence, flow.path, flow.activePath, "
                    + "trace.net, record.fieldPaths; ids as circuits/").append(file.getName().replace(".circ", ".json"))
                    .append("\",\n");
            sb.append("\"file\":").append(GSON.toJson(file.getName())).append(",\n");
            sb.append("\"influence\":{\n").append(entries(influence)).append("\n},\n");
            sb.append("\"flow\":{\n").append(entries(flow)).append("\n},\n");
            sb.append("\"net\":{\n").append(entries(nets)).append("\n},\n");
            sb.append("\"activePath\":[\n").append(String.join(",\n", active)).append("\n],\n");
            sb.append("\"fieldPaths\":[\n").append(String.join(",\n", fields)).append("\n]}\n");
            Files.createDirectories(target.getParent());
            Files.write(target, sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Reset, and the propagation after it done ({@link CanvasFixtures#settle}: the engine's sim.state comes before the
     * simulator thread has reset). Not its sim.values: the engine sends only values that changed, and a Reset right
     * after the file opened may change none (the fixture then timed out now and then).
     */
    private static void reset(InProcess e, String fileId) throws Exception {
        int m = e.client.mark();
        e.client.callObject("sim.reset", params("fileId", fileId));
        e.client.awaitNotificationAfter(m, "sim.state", st -> st.get("cycle").getAsLong() == 0
                && !st.get("ticking").getAsBoolean());
        CanvasFixtures.settle(e, fileId);
    }

    private static JsonObject inf(InProcess e, String fileId, String circuit, List<String> from, String mode,
            boolean through, int depth) {
        return e.client.callObject("trace.influence", params("fileId", fileId, "circuitId", circuit, "from", from,
                "mode", mode, "throughRegisters", through, "depth", depth));
    }

    private static JsonObject path(InProcess e, String fileId, String circuit, String id, int port, boolean back,
            boolean through, boolean active) {
        return e.client.callObject("flow.path", params("fileId", fileId, "circuitId", circuit, "componentId", id,
                "port", port, "backward", back, "throughRegisters", through, "activePathOnly", active));
    }

    static String key(String circuit, CanvasFixtures.Canon canon, List<String> from, String mode, boolean through,
            int depth) {
        List<String> ids = new ArrayList<>();
        for (String id : from) {
            ids.add(canon.parts.get(id));
        }
        ids.sort(null);
        return circuit + "|" + String.join(",", ids) + "|" + mode + "|" + through + "|" + depth;
    }

    static String fkey(String circuit, String id, int port, boolean back, boolean through, boolean active) {
        return circuit + "|" + id + "|" + port + "|" + back + "|" + through + "|" + active;
    }

    private static String entries(Map<String, JsonElement> m) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> x : m.entrySet()) {
            out.add(GSON.toJson(x.getKey()) + ":" + GSON.toJson(x.getValue()));
        }
        return String.join(",\n", out);
    }

    /** 엔진 id(회로·부품·선, 이 회로의 넷)를 자료의 id로. 이름·글자 칸은 그대로. */
    static JsonElement rename(JsonElement el, CanvasFixtures.Canon canon, Map<String, String> netMap) {
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String s = el.getAsString();
            String r = canon.circuits.containsKey(s) ? canon.circuits.get(s)
                    : canon.parts.containsKey(s) ? canon.parts.get(s) : netMap.getOrDefault(s, s);
            return new JsonPrimitive(r);
        }
        if (el.isJsonArray()) {
            JsonArray a = new JsonArray();
            for (JsonElement x : el.getAsJsonArray()) {
                a.add(rename(x, canon, netMap));
            }
            return a;
        }
        if (el.isJsonObject()) {
            JsonObject o = new JsonObject();
            // 열쇠는 그대로(fields의 {필드 이름: [선 id]}도 값만 바꾼다)
            for (Map.Entry<String, JsonElement> x : el.getAsJsonObject().entrySet()) {
                o.add(x.getKey(), TEXT.contains(x.getKey()) ? x.getValue() : rename(x.getValue(), canon, netMap));
            }
            return o;
        }
        return el;
    }

    static Path target(Path out, File file) throws IOException {
        return out.resolve("flow").resolve(file.getName().replace(".circ", ".json"));
    }
}
