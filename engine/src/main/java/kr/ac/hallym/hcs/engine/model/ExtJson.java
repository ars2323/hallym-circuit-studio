/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.model;

import java.awt.Color;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.labels.TunnelColorStore;
import kr.ac.hallym.hcs.app.splitter.SplitterEdits;

/**
 * 부품에 딸린 .circ 확장 정보(hcs:ext, D-024) 가운데 화면이 그리는 것(N-12, D-150). 학생이 직접 정한 것만 있다:
 * <ul>
 *   <li>터널: {@code {color:"#RRGGBB"}} — Tunnel Color로 고른 팔레트 색(v1 {@link TunnelColorStore}). 고르지 않은
 *       터널은 없다(화면이 이름으로 자동 색을 정한다).</li>
 *   <li>스플리터: {@code {arms:["op","rs",…]}} — Splitter 편집기의 팔 이름(위 팔부터, v1 {@link SplitterEdits}). 이름
 *       항목의 팔 수가 부품의 팔 수와 같을 때만(저장할 때 v1 정리 규칙과 같다).</li>
 * </ul>
 * 읽기만 한다. 이 파일의 회로가 아니면(.circ 라이브러리의 회로) 없다.
 */
final class ExtJson {
    private ExtJson() {
    }

    static JsonObject of(LogisimFile file, Circuit owner, Component c) {
        if (file == null || owner == null || !file.contains(owner)) {
            return null;
        }
        String kind = c.getFactory().getName();
        if (kind.equals("Tunnel")) {
            String label = TunnelColorStore.name(c);
            Color col = label == null ? null : TunnelColorStore.get(file, owner, label);
            if (col == null) {
                return null;
            }
            JsonObject o = new JsonObject();
            o.addProperty("color", String.format("#%06X", col.getRGB() & 0xFFFFFF));
            return o;
        }
        if (kind.equals("Splitter")) {
            List<String> names = SplitterEdits.names(file, owner, c.getLocation());
            if (names.size() != c.getEnds().size() - 1 || names.stream().allMatch(String::isEmpty)) {
                return null;
            }
            JsonArray arms = new JsonArray();
            names.forEach(arms::add);
            JsonObject o = new JsonObject();
            o.add("arms", arms);
            return o;
        }
        return null;
    }
}
