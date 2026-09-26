/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.app.model.Trace;
import kr.ac.hallym.hcs.app.splitter.SplitterEdits;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 명령어 필드 경로(C-07, PLAN.md 5.2 "명령어 클릭"). 학생이 스플리터 팔에 op, rs, rt, rd, shamt, funct, imm, addr라는
 * 이름을 붙였으면(스플리터 편집기, D-041) 그 팔의 선(터널로 이어진 곳 포함, 첫 부품 입력까지)을 필드 색으로 칠한다. 예:
 * rs 필드 색이 Read register 1로 가는 선에 칠해진다. 이름 붙인 팔만 따라가고, 경로가 맞는지는 판단하지 않는다(규칙 2.6).
 * 보이는 회로 하나 안에서 계산한다. GUI 없이 테스트한다.
 */
public final class FieldPaths {
    public static final List<String> FIELDS = Collections.unmodifiableList(
            Arrays.asList("op", "rs", "rt", "rd", "shamt", "funct", "imm", "addr"));

    private FieldPaths() {
    }

    public static Color color(String field) {
        int i = FIELDS.indexOf(field);
        return i < 0 ? Tokens.TEXT_2 : Tokens.FIELD[i];
    }

    /** 형식에 쓰는 필드 이름(R: op rs rt rd shamt funct, I: op rs rt imm, J: op addr). */
    public static List<String> fieldsOf(int word) {
        List<String> out = new ArrayList<>();
        for (MipsText.Field f : MipsText.fields(word)) {
            out.add(f.name);
        }
        return out;
    }

    /** 팔 이름을 필드 이름으로(대소문자 무시, "immediate"·"imm16"·"target"도 받는다). 아니면 null. */
    static String fieldName(String arm) {
        if (arm == null) {
            return null;
        }
        String a = arm.trim().toLowerCase(Locale.ROOT);
        if (FIELDS.contains(a)) {
            return a;
        }
        if (a.equals("immediate") || a.equals("imm16") || a.equals("offset")) {
            return "imm";
        }
        if (a.equals("target") || a.equals("address")) {
            return "addr";
        }
        if (a.equals("opcode")) {
            return "op";
        }
        return null;
    }

    /**
     * shown 회로 안에서 이름 붙은 스플리터 팔마다(fields에 든 것만) 그 팔의 넷 선들(첫 부품 입력까지). 결과는 필드
     * 순서.
     */
    public static Map<String, Set<Wire>> of(LogisimFile file, Circuit shown, List<String> fields) {
        Map<String, Set<Wire>> out = new LinkedHashMap<>();
        Trace t = new Trace();
        for (Component c : shown.getNonWires()) {
            if (!c.getFactory().getName().equals("Splitter")) {
                continue;
            }
            List<String> names = SplitterEdits.names(file, shown, c.getLocation());
            for (int i = 0; i < names.size(); i++) {
                String field = fieldName(names.get(i));
                if (field == null || !fields.contains(field) || i + 1 >= c.getEnds().size()) {
                    continue;
                }
                // 팔의 넷(터널로 이어진 곳 포함)만: 첫 부품 입력에서 멈춘다. 레지스터 파일·ALU를 지나면 필드가 아니라
                // 레지스터 값이므로 칠하지 않는다
                Trace.Node arm = t.node(shown, c, i + 1);
                Set<Wire> wires = out.computeIfAbsent(field, k -> new LinkedHashSet<>());
                if (!touches(arm, c, 0)) {
                    wires.addAll(arm.net.wires());
                }
            }
        }
        Map<String, Set<Wire>> ordered = new LinkedHashMap<>();
        for (String f : FIELDS) {
            if (out.containsKey(f)) {
                ordered.put(f, out.get(f));
            }
        }
        return ordered;
    }

    /** 넷이 스플리터의 합친 끝(end)에 닿는가: 팔에서 스플리터를 거슬러 합친 버스로 퍼지지 않게. */
    private static boolean touches(Trace.Node n, Component splitter, int end) {
        for (kr.ac.hallym.hcs.app.model.Netlist.PortRef p : n.net.ports()) {
            if (p.component == splitter && p.end == end) {
                return true;
            }
        }
        return false;
    }
}
