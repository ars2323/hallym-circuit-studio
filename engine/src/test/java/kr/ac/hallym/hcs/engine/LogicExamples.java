/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static kr.ac.hallym.hcs.engine.Client.xy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 논리설계 및 실험의 Help › Examples(A-08, D-168): 1비트 전가산기, 4비트 리플 캐리 가산기(전가산기 서브회로 넷),
 * 4비트 카운터. 엔진의 편집 의도(원조 Logisim의 AddTool·WiringTool 코드)로 짓고 원조 저장 코드로 저장하므로 새 부품이
 * 없는 원조 2.7.1 파일 그대로다. tests/circ/의 세 파일은 이것이 지은 것이다(LogicExamplesTest가 대조, 다시 쓰기는
 * {@code ./gradlew :engine:test --tests '*LogicExamplesTest*' -Phcs.update=true}).
 */
final class LogicExamples {
    static final String[] NAMES = {"adder-1bit.circ", "ripple-carry-4bit.circ", "counter-4bit.circ"};

    final InProcess e;
    String fileId;
    String circuit;

    LogicExamples(InProcess e) {
        this.e = e;
    }

    /** 이름의 예제를 지어 out에 저장한다. */
    static void build(InProcess e, String name, File out) {
        LogicExamples b = new LogicExamples(e);
        switch (name) {
            case "adder-1bit.circ" -> {
                b.fresh();
                b.fullAdder();
            }
            case "ripple-carry-4bit.circ" -> b.rippleCarry();
            case "counter-4bit.circ" -> {
                b.fresh();
                b.counter();
            }
            default -> throw new IllegalArgumentException(name);
        }
        b.e.client.callObject("file.save", params("fileId", b.fileId, "path", out.getPath()));
        b.e.client.callObject("file.close", params("fileId", b.fileId));
    }

    void fresh() {
        JsonObject r = e.client.callObject("file.new", params());
        fileId = r.get("fileId").getAsString();
        circuit = r.get("main").getAsString();
    }

    JsonObject edit(String method, Object... kv) {
        Object[] all = new Object[kv.length + 4];
        all[0] = "fileId";
        all[1] = fileId;
        all[2] = "circuitId";
        all[3] = circuit;
        System.arraycopy(kv, 0, all, 4, kv.length);
        return e.client.callObject(method, params(all));
    }

    String add(String lib, String name, int x, int y, Object... attrs) {
        return edit("edit.addComponent", "lib", lib, "name", name, "loc", xy(x, y), "attrs", params(attrs))
                .get("id").getAsString();
    }

    /** 부품 id의 i번째 포트 자리(엔진의 스냅숏). */
    int[] port(String id, int i) {
        JsonObject snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", circuit));
        for (JsonElement c : snap.getAsJsonArray("components")) {
            JsonObject o = c.getAsJsonObject();
            if (!o.get("id").getAsString().equals(id)) {
                continue;
            }
            for (JsonElement p : o.getAsJsonArray("ports")) {
                JsonObject q = p.getAsJsonObject();
                if (q.get("i").getAsInt() == i) {
                    return new int[] {q.getAsJsonArray("loc").get(0).getAsInt(), q.getAsJsonArray("loc").get(1).getAsInt()};
                }
            }
        }
        throw new IllegalStateException("no port " + i + " of " + id);
    }

    /** 점들을 차례로 잇는 선(한 토막씩 Wiring 도구로). 같은 점이 이어지면 건너뛴다. */
    void wire(int[]... pts) {
        for (int k = 1; k < pts.length; k++) {
            int[] a = pts[k - 1];
            int[] b = pts[k];
            if (a[0] == b[0] && a[1] == b[1]) {
                continue;
            }
            if (a[0] != b[0] && a[1] != b[1]) {
                throw new IllegalArgumentException("not straight: " + a[0] + "," + a[1] + " → " + b[0] + "," + b[1]);
            }
            edit("edit.addWire", "points", new int[][] {a, b});
        }
    }

    /** from에서 가로로 x까지, 세로로 to의 높이까지, 가로로 to까지. */
    void route(int[] from, int[] to, int x) {
        wire(from, new int[] {x, from[1]}, new int[] {x, to[1]}, to);
    }

    /**
     * One output to several inputs: from → (x, from.y), a trunk at x over every height (drawn outward from the
     * junction, never over a wire already there: the Wiring tool would shorten it), then (x, t.y) → t for each target
     * (its end on the trunk: the original splits the trunk there and joins them).
     */
    void fan(int[] from, int x, int[]... targets) {
        wire(from, at(x, from[1]));
        int lo = from[1];
        int hi = from[1];
        for (int[] t : targets) {
            lo = Math.min(lo, t[1]);
            hi = Math.max(hi, t[1]);
        }
        wire(at(x, from[1]), at(x, lo));
        wire(at(x, from[1]), at(x, hi));
        for (int[] t : targets) {
            wire(at(x, t[1]), t);
        }
    }

    static int[] at(int x, int y) {
        return new int[] {x, y};
    }

    // ---- 1비트 전가산기: S = A ⊕ B ⊕ Cin, Cout = A·B + Cin·(A ⊕ B) ----------------------------------------

    void fullAdder() {
        String a = add("Wiring", "Pin", 80, 100, "label", "A", "tristate", "false");
        String b = add("Wiring", "Pin", 80, 160, "label", "B", "tristate", "false");
        String cin = add("Wiring", "Pin", 80, 300, "label", "Cin", "tristate", "false");
        String x1 = add("Gates", "XOR Gate", 280, 130, "inputs", "2");
        String a1 = add("Gates", "AND Gate", 280, 220, "inputs", "2");
        String x2 = add("Gates", "XOR Gate", 450, 150, "inputs", "2");
        String a2 = add("Gates", "AND Gate", 450, 270, "inputs", "2");
        String o1 = add("Gates", "OR Gate", 590, 240, "inputs", "2");
        String s = add("Wiring", "Pin", 680, 150, "label", "S", "output", "true", "facing", "west", "labelloc", "east");
        String cout = add("Wiring", "Pin", 680, 240, "label", "Cout", "output", "true", "facing", "west", "labelloc", "east");
        fan(port(a, 0), 150, port(x1, 1), port(a1, 1));
        fan(port(b, 0), 180, port(x1, 2), port(a1, 2));
        fan(port(x1, 0), 330, port(x2, 1), port(a2, 1));
        fan(port(cin, 0), 360, port(x2, 2), port(a2, 2));
        route(port(a1, 0), port(o1, 1), 510);
        route(port(a2, 0), port(o1, 2), 510);
        wire(port(x2, 0), port(s, 0));
        wire(port(o1, 0), port(cout, 0));
    }

    // ---- 4비트 리플 캐리: full_adder 서브회로 넷, 자리올림이 아래로 --------------------------------------------

    void rippleCarry() {
        fresh();
        String main = circuit;
        circuit = edit("edit.createCircuit", "name", "full_adder").get("circuitId").getAsString();
        fullAdder();
        e.client.callObject("edit.setMainCircuit", params("fileId", fileId, "circuitId", main));
        circuit = main;
        final int pitch = 120;
        String carry = add("Wiring", "Pin", 120, 60, "label", "Cin", "tristate", "false");
        int[] carryOut = port(carry, 0);
        List<String> sums = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int y = 120 + i * pitch;
            String fa = add(null, "full_adder", 420, y, "label", "FA" + i);
            int[] pa = port(fa, 0);
            int[] pb = port(fa, 1);
            int[] pc = port(fa, 2);
            String ai = add("Wiring", "Pin", 120, pa[1] - 20, "label", "A" + i, "tristate", "false");
            String bi = add("Wiring", "Pin", 120, pb[1] + 20, "label", "B" + i, "tristate", "false");
            route(port(ai, 0), pa, 300);
            route(port(bi, 0), pb, 300 + 20);
            // the carry in: from the pin (FA0) or the stage above's Cout, down the left, into Cin
            int x = 360;
            if (i == 0) {
                route(carryOut, pc, x);
            } else {
                wire(carryOut, at(carryOut[0] + 30, carryOut[1]), at(carryOut[0] + 30, y - 50), at(x, y - 50), at(x, pc[1]), pc);
            }
            int[] ps = port(fa, 3);
            carryOut = port(fa, 4);
            String si = add("Wiring", "Pin", 640, ps[1], "label", "S" + i, "output", "true", "facing", "west", "labelloc", "east");
            wire(ps, port(si, 0));
            sums.add(si);
        }
        String cout = add("Wiring", "Pin", 640, carryOut[1] + 40, "label", "Cout", "output", "true", "facing", "west", "labelloc", "east");
        route(carryOut, port(cout, 0), carryOut[0] + 30);
    }

    // ---- 4비트 카운터: Register의 Q + 1을 다음 클럭에 D로 -----------------------------------------------------

    void counter() {
        String clk = add("Wiring", "Clock", 180, 300);
        String reg = add("Memory", "Register", 300, 200, "width", "4", "label", "count");
        String adder = add("Arithmetic", "Adder", 480, 230, "width", "4");
        String one = add("Wiring", "Constant", port(adder, 1)[0] - 20, port(adder, 1)[1], "width", "4", "value", "0x1");
        String q = add("Wiring", "Pin", 640, 140, "label", "Q", "width", "4", "output", "true", "facing", "west", "labelloc", "east");
        // Register ports (2.7.1): 0 Q (east), 1 D (west), 2 clock, 3 clear, 4 enable
        int[] regQ = port(reg, 0);
        int[] regD = port(reg, 1);
        int[] regClk = port(reg, 2);
        int[] addA = port(adder, 0);
        int[] addB = port(adder, 1);
        int[] addOut = port(adder, 2);
        fan(regQ, 400, addA, port(q, 0));
        wire(port(one, 0), addB);
        wire(port(clk, 0), at(regClk[0], port(clk, 0)[1]), regClk);
        // the sum back to D: right of the adder, under everything, left of the register, up into D
        int below = 360;
        wire(addOut, at(addOut[0] + 30, addOut[1]), at(addOut[0] + 30, below), at(regD[0] - 30, below), at(regD[0] - 30, regD[1]), regD);
    }
}
