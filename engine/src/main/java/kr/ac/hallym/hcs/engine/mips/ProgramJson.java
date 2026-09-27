/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.mips;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.engine.model.Ids;
import kr.ac.hallym.hcs.mips.image.ExecutableImage;
import kr.ac.hallym.hcs.mips.image.LoadReport;
import kr.ac.hallym.hcs.mips.image.Msg;
import kr.ac.hallym.hcs.mips.image.SourceCheck;
import kr.ac.hallym.hcs.mips.image.StartFacts;

/**
 * 불러오기 결과({@link LoadReport})를 규약 글(docs/engine-api.md "mips")로 옮긴다. 값은 공용 코드가 정한 그대로다: 요약
 * 머리 줄과 사실 줄은 {@link StartFacts}, 원본 대조 문장은 {@link SourceCheck}, 구간 글은 {@link ExecutableImage}.
 * 문장은 영어·한국어 두 벌({@code {en, ko}})이고 화면이 고른다. 이름·숫자는 두 언어 모두 영어다(D-049).
 */
final class ProgramJson {
    private ProgramJson() {
    }

    static JsonObject text(Msg m) {
        JsonObject o = new JsonObject();
        o.addProperty("en", m.en);
        o.addProperty("ko", m.ko);
        return o;
    }

    static JsonArray problems(List<LoadReport.Problem> list) {
        JsonArray out = new JsonArray();
        for (LoadReport.Problem p : list) {
            JsonObject o = new JsonObject();
            o.addProperty("line", p.line);
            o.add("text", text(p.text));
            out.add(o);
        }
        return out;
    }

    static String kind(ExecutableImage.Kind k) {
        return k == ExecutableImage.Kind.TEXT ? "text" : "data";
    }

    /** 원본 대조 결과의 이름: same, changed, notFound, noHash. */
    static String status(SourceCheck.Status s) {
        switch (s) {
            case SAME:
                return "same";
            case CHANGED:
                return "changed";
            case NOT_FOUND:
                return "notFound";
            default:
                return "noHash";
        }
    }

    /** 고를 것: {@code {kind, segment, candidates:[{componentId, circuitId, name}]}}. */
    static JsonObject choice(LoadReport.Choice c, Ids ids) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind(c.kind));
        o.addProperty("segment", c.segment);
        JsonArray cands = new JsonArray();
        for (int i = 0; i < c.components.size(); i++) {
            JsonObject x = new JsonObject();
            x.addProperty("componentId", ids.of((Component) c.components.get(i)));
            x.addProperty("circuitId", ids.of((Circuit) c.circuits.get(i)));
            x.addProperty("name", c.names.get(i));
            cands.add(x);
        }
        o.add("candidates", cands);
        return o;
    }

    /** 구간 하나의 워드 칸 수(바이트 구간은 걸친 워드 수, {@link ExecutableImage#describe}와 같은 셈). */
    static long wordSlots(ExecutableImage.Segment s) {
        if (s.kind == ExecutableImage.Kind.TEXT) {
            return s.count;
        }
        long n = 0;
        for (long a = s.start & ~3L; a < s.end(); a += 4) {
            n++;
        }
        return n;
    }

    /**
     * 요약: entry, reg 시작 값, 구간마다 넣은 곳과 양, 비운 Data Memory, 스택 깊이 기준, 원본 대조, 사실 줄(처리기 없음,
     * 진입 루틴의 jr $ra), 쓰인 명령어, 트랙 A와 같은 요약 줄.
     */
    static JsonObject summary(LoadReport r, Ids ids) {
        ExecutableImage img = r.image;
        JsonObject o = new JsonObject();
        o.addProperty("entry", img.entry() == null ? null : ExecutableImage.hex(img.entry()));
        o.addProperty("entryLine", StartFacts.entryLine(img).en);
        JsonArray regs = new JsonArray();
        for (Map.Entry<String, Long> e : img.regs().entrySet()) {
            JsonObject x = new JsonObject();
            x.addProperty("name", e.getKey());
            x.addProperty("value", ExecutableImage.hex(e.getValue()));
            regs.add(x);
        }
        o.add("regs", regs);
        JsonArray segs = new JsonArray();
        for (LoadReport.Placement p : r.placements) {
            for (ExecutableImage.Segment s : p.segments) {
                JsonObject x = new JsonObject();
                x.addProperty("kind", kind(s.kind));
                x.addProperty("start", ExecutableImage.hex(s.start));
                x.addProperty("last", ExecutableImage.hex(s.last()));
                x.addProperty("range", s.range());
                x.addProperty("units", s.count);
                x.addProperty("unit", s.kind.unit);
                x.addProperty("bytes", s.bytes());
                x.addProperty("words", wordSlots(s));
                x.addProperty("text", ExecutableImage.describe(Collections.singletonList(s)));
                x.addProperty("componentId", ids.of((Component) p.component));
                x.addProperty("circuitId", ids.of((Circuit) p.circuit));
                x.addProperty("target", p.name);
                segs.add(x);
            }
        }
        o.add("segments", segs);
        if (r.emptied != null) {
            JsonObject x = new JsonObject();
            x.addProperty("componentId", ids.of((Component) r.emptied));
            x.addProperty("circuitId", ids.of((Circuit) r.emptiedCircuit));
            x.addProperty("target", r.emptiedName);
            o.add("emptied", x);
        } else {
            o.add("emptied", JsonNull.INSTANCE);
        }
        JsonArray stack = new JsonArray();
        Long sp = img.reg("$sp");
        for (int i = 0; i < r.stackBase.size(); i++) {
            JsonObject x = new JsonObject();
            x.addProperty("componentId", ids.of((Component) r.stackBase.get(i)));
            x.addProperty("target", r.stackBaseNames.get(i));
            x.addProperty("sp", sp == null ? null : ExecutableImage.hex(sp));
            stack.add(x);
        }
        o.add("stackBase", stack);
        JsonArray instr = new JsonArray();
        r.instructions.forEach(instr::add);
        o.add("instructions", instr);
        if (r.check != null) {
            JsonObject x = new JsonObject();
            x.addProperty("status", status(r.check.status));
            x.addProperty("name", r.check.name);
            x.add("text", text(r.check.message()));
            x.addProperty("warn", r.check.warns());
            o.add("source", x);
        }
        JsonArray facts = new JsonArray();
        Msg noHandler = StartFacts.noHandlerFact(img);
        if (noHandler != null) {
            facts.add(fact("noHandler", noHandler));
        }
        Msg jr = StartFacts.jrRaFact(img);
        if (jr != null) {
            facts.add(fact("jrRa", jr));
        }
        o.add("facts", facts);
        o.addProperty("producedBy", img.producedBy());
        o.addProperty("assembled", img.assembled());
        JsonArray notes = new JsonArray();
        r.notes.forEach(notes::add);
        o.add("notes", notes);
        return o;
    }

    static JsonObject fact(String id, Msg m) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.add("text", text(m));
        return o;
    }

    /**
     * 메모리 부품 몸체의 양(첫 워드와 마지막 워드의 주소, {@link ExecutableImage.Segment#range}의 .text와 같은 꼴):
     * {@code 27 words (0x00400000–0x00400068)}. 비었으면 {@code no program}(트랙 A 몸체와 같은 이름).
     */
    static String amount(java.util.SortedMap<Long, Integer> words) {
        if (words.isEmpty()) {
            return "no program";
        }
        return ExecutableImage.count(words.size(), "word") + " (" + ExecutableImage.hex(words.firstKey()) + "–"
                + ExecutableImage.hex(words.lastKey()) + ")";
    }
}
