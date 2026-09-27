/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.mips;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.engine.model.Ids;

/**
 * 파일의 MIPS 사실(D-140). 진단(Messages)이 아니다: 회로는 동작하고, 도구가 바뀐 사실과 할 수 있는 일만 상태 표시줄에
 * 한 줄로 알린다(화면은 N-16/N-17). 지금 사실은 하나다.
 *
 * <ul>
 *   <li>{@link #SEPARATE_STACK}: 파일에 옛 Stack 부품이 있다. 새 Data Memory는 스택 영역을 함께 맡는다(사용자 결정).
 *       옛 부품은 전과 똑같이 동작하므로 고치라고 하지 않는다.</li>
 * </ul>
 */
public final class CircuitFacts {
    /** 사실 id: 따로 된 Stack 부품을 쓴다. */
    public static final String SEPARATE_STACK = "separateStack";

    private CircuitFacts() {
    }

    /** 사실 하나: id, 영어·한국어 문장, 관련 부품(파일 안 순서). */
    public static final class Fact {
        public final String id;
        public final String en;
        public final String ko;
        public final List<Component> components;

        Fact(String id, String en, String ko, List<Component> components) {
            this.id = id;
            this.en = en;
            this.ko = ko;
            this.components = Collections.unmodifiableList(components);
        }
    }

    /** 파일의 모든 회로를 보고 사실들을 낸다. 없으면 빈 목록. */
    public static List<Fact> of(LogisimFile file) {
        List<Component> stacks = new ArrayList<>();
        for (Circuit c : file.getCircuits()) {
            for (Component x : c.getNonWires()) {
                Kinds.Kind k = Kinds.of(x);
                if (k != null && k.category() == Kinds.Category.MIPS && k.factory().equals("Stack")) {
                    stacks.add(x);
                }
            }
        }
        List<Fact> out = new ArrayList<>();
        if (!stacks.isEmpty()) {
            String key = "fact." + SEPARATE_STACK;
            out.add(new Fact(SEPARATE_STACK, Messages.get(Locale.ENGLISH, key), Messages.get(Locale.KOREAN, key),
                    stacks));
        }
        return out;
    }

    /** {@code mips.facts}의 결과: {@code {facts:[{id, en, ko, components:[id]}]}}. */
    public static JsonObject json(LogisimFile file, Ids ids) {
        JsonArray facts = new JsonArray();
        for (Fact f : of(file)) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id);
            o.addProperty("en", f.en);
            o.addProperty("ko", f.ko);
            JsonArray comps = new JsonArray();
            for (Component c : f.components) {
                comps.add(ids.of(c));
            }
            o.add("components", comps);
            facts.add(o);
        }
        JsonObject out = new JsonObject();
        out.add("facts", facts);
        return out;
    }
}
