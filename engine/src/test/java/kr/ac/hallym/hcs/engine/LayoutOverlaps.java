/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 예제 회로에서 부품끼리 겹친 곳 찾기(D-156). 엔진이 화면에 보내는 모델({@code model.circuit}의 부품 경계
 * {@code bounds}와 포트)만 본다: 화면이 그리는 몸체가 곧 이 경계다.
 *
 * <p>부품 둘(선과 Base의 Text는 뺀다)의 경계가 넓이 있게 겹치면 겹침이다. 다만 둘은 정상이다.
 * <ol>
 * <li><b>맞닿음</b>: 겹친 폭이나 높이가 {@value #TOUCH} 이하. 포트는 경계 가장자리에 있어, 포트에 붙은 터널·핀과
 * 이웃 부품의 경계가 1~2 단위 겹친다.</li>
 * <li><b>부채꼴 이웃</b>: 둘이 같은 제3 부품의 서로 다른 포트에 붙어 있고(자기 포트가 그 포트 자리에 있다), 두 붙은
 * 점이 한 줄(가로 또는 세로)에 {@value #PITCH} 단위 이하로 떨어져 있으며, 둘이 같은 방향을 보고 그 방향이 두 점을
 * 잇는 줄과 직각이다. 스플리터 팔(10 단위 간격)이나 멀티플렉서 입력에 나란히 붙은 터널은 몸체 높이(18)가 간격보다
 * 커 옆 터널과 조금 겹치지만 모두 부모에서 같은 쪽으로 뻗는다.</li>
 * </ol>
 *
 * <p><b>엄격하게</b>({@code strict}): 부채꼴 이웃도 겹침이다. 튜토리얼 예제(N-18)는 확대해서 보고 박스로 짚는
 * 회로라 터널·라벨 칩이 서로 조금도 덮지 않아야 한다(UI 검토: 스플리터 팔 터널 `rd`가 `shamt`를 덮음).
 */
public final class LayoutOverlaps {
    /** 맞닿음으로 보는 겹친 폭·높이의 상한(회로 단위). */
    static final int TOUCH = 2;
    /** 부채꼴 이웃의 붙은 점 사이 거리 상한(Logisim 격자 한 칸). */
    static final int PITCH = 10;

    private LayoutOverlaps() {
    }

    /** 겹침 하나: 회로 이름, 두 부품의 설명, 겹친 폭·높이. */
    record Overlap(String circuit, String a, String b, int width, int height) {
        @Override
        public String toString() {
            return circuit + ": " + a + " × " + b + " (" + width + "×" + height + ")";
        }
    }

    /** 스냅숏({@code model.circuit}의 답) 하나의 겹침. */
    static List<Overlap> find(JsonObject snapshot) {
        return find(snapshot, false);
    }

    /** 같은 것, {@code strict}면 부채꼴 이웃도 겹침으로. */
    static List<Overlap> find(JsonObject snapshot, boolean strict) {
        String circuit = snapshot.get("name").getAsString();
        List<JsonObject> cs = new ArrayList<>();
        for (JsonElement x : snapshot.getAsJsonArray("components")) {
            JsonObject c = x.getAsJsonObject();
            if (!isText(c)) {
                cs.add(c);
            }
        }
        List<Overlap> ret = new ArrayList<>();
        for (int i = 0; i < cs.size(); i++) {
            for (int j = i + 1; j < cs.size(); j++) {
                JsonObject p = cs.get(i);
                JsonObject q = cs.get(j);
                int[] a = bounds(p);
                int[] b = bounds(q);
                int ox = Math.min(a[0] + a[2], b[0] + b[2]) - Math.max(a[0], b[0]);
                int oy = Math.min(a[1] + a[3], b[1] + b[3]) - Math.max(a[1], b[1]);
                if (ox <= TOUCH || oy <= TOUCH) {
                    continue;
                }
                if (outward(p, q) || outward(q, p) || (!strict && fanNeighbours(p, q, cs))) {
                    continue;
                }
                ret.add(new Overlap(circuit, describe(p), describe(q), ox, oy));
            }
        }
        return ret;
    }

    static boolean isText(JsonObject c) {
        return "Text".equals(c.get("name").getAsString()) && "Base".equals(lib(c));
    }

    private static String lib(JsonObject c) {
        return c.get("lib").isJsonNull() ? null : c.get("lib").getAsString();
    }

    /** 규칙 2: a가 c의 포트에 붙어 c 바깥으로 뻗는가. */
    static boolean outward(JsonObject a, JsonObject c) {
        int[] ab = bounds(a);
        int[] cb = bounds(c);
        for (int[] at : attachments(a, c)) {
            // a가 뻗는 방향: 붙은 점에서 a 경계의 가운데로(2배 좌표로 반올림 없이)
            int dx = 2 * ab[0] + ab[2] - 2 * at[0];
            int dy = 2 * ab[1] + ab[3] - 2 * at[1];
            Side dir = Math.abs(dx) >= Math.abs(dy) ? (dx >= 0 ? Side.EAST : Side.WEST)
                    : (dy >= 0 ? Side.SOUTH : Side.NORTH);
            // c 경계에서 붙은 점과 가장 가까운 변(1 단위 차이는 같게 본다: 원조 경계는 오른쪽·아래로 1 넓다)
            int[] dist = {at[1] - cb[1], cb[0] + cb[2] - at[0], cb[1] + cb[3] - at[1], at[0] - cb[0]};
            int min = Math.min(Math.min(dist[0], dist[1]), Math.min(dist[2], dist[3]));
            if (dist[dir.ordinal()] <= min + 1) {
                return true;
            }
        }
        return false;
    }

    /** 경계의 변(바깥 방향). 순서는 {@link #outward}의 거리 배열과 같다. */
    enum Side {
        NORTH, EAST, SOUTH, WEST
    }

    /** 규칙 3: p와 q가 같은 부품 c의 서로 다른 포트에 붙은, 같은 쪽을 보는 나란한 이웃인가. */
    static boolean fanNeighbours(JsonObject p, JsonObject q, List<JsonObject> all) {
        String facing = facing(p);
        if (facing == null || !facing.equals(facing(q))) {
            return false;
        }
        for (JsonObject c : all) {
            if (c == p || c == q) {
                continue;
            }
            for (int[] at : attachments(p, c)) {
                for (int[] bt : attachments(q, c)) {
                    int dx = Math.abs(at[0] - bt[0]);
                    int dy = Math.abs(at[1] - bt[1]);
                    boolean vertical = dx == 0 && dy > 0 && dy <= PITCH; // 위아래로 나란함: 몸체는 가로로 뻗어야 한다
                    boolean horizontal = dy == 0 && dx > 0 && dx <= PITCH;
                    boolean across = facing.equals("east") || facing.equals("west");
                    if ((vertical && across) || (horizontal && !across)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** x의 포트 가운데 c의 포트와 같은 자리에 있는 것들의 자리. */
    private static List<int[]> attachments(JsonObject x, JsonObject c) {
        List<int[]> ret = new ArrayList<>();
        for (JsonElement a : x.getAsJsonArray("ports")) {
            JsonArray la = a.getAsJsonObject().getAsJsonArray("loc");
            for (JsonElement b : c.getAsJsonArray("ports")) {
                if (la.equals(b.getAsJsonObject().getAsJsonArray("loc"))) {
                    ret.add(new int[] {la.get(0).getAsInt(), la.get(1).getAsInt()});
                }
            }
        }
        return ret;
    }

    private static String facing(JsonObject c) {
        JsonElement f = c.get("facing");
        return f == null || f.isJsonNull() ? null : f.getAsString();
    }

    static int[] bounds(JsonObject c) {
        JsonArray a = c.getAsJsonArray("bounds");
        return new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt()};
    }

    /** 사람이 찾을 수 있는 설명: 이름, 라벨, 자리, 방향. */
    static String describe(JsonObject c) {
        JsonObject at = c.getAsJsonObject("attrs");
        String label = at != null && at.has("label") ? at.get("label").getAsString() : "";
        JsonArray loc = c.getAsJsonArray("loc");
        String f = facing(c);
        return c.get("name").getAsString() + (label.isEmpty() ? "" : " \"" + label + "\"") + " at ("
                + loc.get(0).getAsInt() + "," + loc.get(1).getAsInt() + ")" + (f == null ? "" : " " + f);
    }

    /** 파일 하나의 모든 회로를 엔진으로 열어 겹침을 모은다(파일은 tmp로 복사해 연다). */
    static List<Overlap> scan(InProcess e, File circ, Path tmp) throws Exception {
        return scan(e, circ, tmp, name -> false);
    }

    /** 같은 것, 이름이 strict를 만족하는 회로는 엄격하게. */
    static List<Overlap> scan(InProcess e, File circ, Path tmp, java.util.function.Predicate<String> strict)
            throws Exception {
        File copy = tmp.resolve(circ.getName()).toFile();
        Files.copy(circ.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
        JsonObject r = e.client.callObject("file.open", params("path", copy.getPath()));
        String fileId = r.get("fileId").getAsString();
        List<Overlap> ret = new ArrayList<>();
        for (JsonElement ce : r.getAsJsonArray("circuits")) {
            String cid = ce.getAsJsonObject().get("circuitId").getAsString();
            JsonObject snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", cid));
            ret.addAll(find(snap, strict.test(snap.get("name").getAsString())));
        }
        e.client.callObject("file.close", params("fileId", fileId));
        return ret;
    }

    /** 보고: 인자로 받은 .circ마다 겹침 목록을 찍는다. */
    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("hcs-overlaps");
        int total = 0;
        try (InProcess e = new InProcess()) {
            boolean strict = System.getProperty("strict") != null;
            for (String a : args) {
                List<Overlap> found = scan(e, new File(a), tmp, name -> strict);
                System.out.println(a + ": " + found.size());
                for (Overlap o : found) {
                    System.out.println("  " + o);
                }
                total += found.size();
            }
        }
        System.out.println("total: " + total);
        System.exit(0);
    }
}
