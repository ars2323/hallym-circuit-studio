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
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.engine.model.Ids;
import kr.ac.hallym.hcs.mips.image.AssemblySource;

/**
 * 파일의 MIPS 사실(D-140, D-141). 진단(Messages)이 아니다: 회로는 동작하고, 도구가 바뀐 사실과 할 수 있는 일만 상태
 * 표시줄에 한 줄로 알린다(화면은 N-16/N-17).
 *
 * <ul>
 *   <li>{@link #SEPARATE_STACK}: 파일에 옛 Stack 부품이 있다. 새 Data Memory는 스택 영역을 함께 맡는다(사용자 결정).
 *       옛 부품은 전과 똑같이 동작하므로 고치라고 하지 않는다.</li>
 *   <li>{@link #ASSEMBLY_SOURCE}: 메모리 부품의 {@code source} 속성이 .s(.asm)를 가리킨다. .s 불러오기는 없어졌으므로
 *       Hallym MIPS의 Export executable image (.hmx)로 내보낸 파일을 불러오라고 한다(사용자 결정, D-141). 속성은 읽기만
 *       하므로 파일은 전과 같이 열리고 저장된다. 불러오기(N-16)가 .hmx 경로로 바꾼다.</li>
 * </ul>
 */
public final class CircuitFacts {
    /** 사실 id: 따로 된 Stack 부품을 쓴다. */
    public static final String SEPARATE_STACK = "separateStack";
    /** 사실 id: 메모리 부품이 .s 경로를 가리킨다(D-141). */
    public static final String ASSEMBLY_SOURCE = "assemblySource";

    private CircuitFacts() {
    }

    /** 사실 하나: id, 영어·한국어 문장, 관련 부품(파일 안 순서), 부품마다의 값(없으면 빈 목록). */
    public static final class Fact {
        public final String id;
        public final String en;
        public final String ko;
        public final List<Component> components;
        /** {@link #ASSEMBLY_SOURCE}: 부품마다 {@code source} 속성 글(components와 같은 순서). 다른 사실은 비어 있다. */
        public final List<String> sources;

        Fact(String id, String en, String ko, List<Component> components, List<String> sources) {
            this.id = id;
            this.en = en;
            this.ko = ko;
            this.components = Collections.unmodifiableList(components);
            this.sources = Collections.unmodifiableList(sources);
        }
    }

    /** 파일의 모든 회로를 보고 사실들을 낸다. 없으면 빈 목록. */
    public static List<Fact> of(LogisimFile file) {
        List<Component> stacks = new ArrayList<>();
        List<Component> assembly = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        for (Circuit c : file.getCircuits()) {
            for (Component x : c.getNonWires()) {
                Kinds.Kind k = Kinds.of(x);
                if (k == null || k.category() != Kinds.Category.MIPS) {
                    continue;
                }
                if (k.factory().equals("Stack")) {
                    stacks.add(x);
                }
                String source = source(x);
                if (AssemblySource.isAssembly(source)) {
                    assembly.add(x);
                    sources.add(source);
                }
            }
        }
        List<Fact> out = new ArrayList<>();
        if (!stacks.isEmpty()) {
            String key = "fact." + SEPARATE_STACK;
            out.add(new Fact(SEPARATE_STACK, Messages.get(Locale.ENGLISH, key), Messages.get(Locale.KOREAN, key),
                    stacks, Collections.<String>emptyList()));
        }
        if (!assembly.isEmpty()) {
            out.add(new Fact(ASSEMBLY_SOURCE, AssemblySource.FACT.en, AssemblySource.FACT.ko, assembly, sources));
        }
        return out;
    }

    /** MIPS 메모리 부품의 {@code source} 속성 글(번들 jar의 클래스를 모르므로 이름으로 찾는다). 없으면 null. */
    static String source(Component x) {
        Attribute<?> a = x.getAttributeSet().getAttribute("source");
        Object v = a == null ? null : x.getAttributeSet().getValue(a);
        return v == null ? null : v.toString();
    }

    /** {@code mips.facts}의 결과: {@code {facts:[{id, en, ko, components:[id], sources?:[글]}]}}. */
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
            if (!f.sources.isEmpty()) {
                JsonArray src = new JsonArray();
                for (String v : f.sources) {
                    src.add(v);
                }
                o.add("sources", src);
            }
            facts.add(o);
        }
        JsonObject out = new JsonObject();
        out.add("facts", facts);
        return out;
    }
}
