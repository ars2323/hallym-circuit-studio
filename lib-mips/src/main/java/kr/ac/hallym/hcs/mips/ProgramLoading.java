/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;

import kr.ac.hallym.hcs.mips.image.ExecutableImage;
import kr.ac.hallym.hcs.mips.image.LoadReport;
import kr.ac.hallym.hcs.mips.image.Msg;

/**
 * v2 엔진이 부르는 불러오기 입구(N-16, D-147). 트랙 A 메뉴({@link LoadProgramMenu})와 같은 길이다: 파일 읽기와 원본
 * 대조({@link ProgramLoader#read}), 넣을 곳 정하기({@link ProgramLoader#plan}). 파싱·배치를 다시 짜지 않고, 결과를 공용
 * {@link LoadReport}로 옮길 뿐이다. 아무것도 바꾸지 않는다(바꿀 것은 {@link LoadReport#changes}, 엔진이 되돌리기 한 단계로
 * 한다).
 *
 * <p>엔진은 이 라이브러리를 원조 JAR 라이브러리 방식(따로 된 클래스 로더)으로 불러 이 클래스를 컴파일 때 모른다. 그래서
 * 이름으로 찾아 부르고, 인자와 결과는 원조 Logisim 형과 공용 형(부모 클래스 로더가 먼저 찾아 엔진과 같은 형)만 쓴다.
 * 트랙 A(원조 2.7.1)는 이 클래스를 쓰지 않는다.
 */
public final class ProgramLoading {
    /** {@code picks}의 열쇠: .text를 넣을 Instruction Memory. */
    public static final String TEXT = "text";
    /** {@code picks}의 열쇠: .data를 넣을 Data Memory. */
    public static final String DATA = "data";

    private ProgramLoading() {
    }

    /**
     * 실행 이미지 hmx를 파일의 회로들에 넣을 계획. clicked는 사람이 고른 메모리 부품(우클릭, 없으면 null: 그 종류의 구간을
     * 모두 담아야 한다). 한 구간을 담는 부품이 여럿이면 picks의 부품(종류별 {@link #TEXT}·{@link #DATA})을 쓰고, 없으면
     * {@link LoadReport#choice}에 후보를 적고 멈춘다. circ는 .circ 파일(상대 경로의 기준, 없으면 null).
     */
    public static LoadReport load(File hmx, File circ, List<Circuit> circuits, Circuit clickedCircuit, Component clicked,
            Map<String, Component> picks) {
        final LoadReport out = new LoadReport(hmx);
        ProgramLoader.Loaded loaded = ProgramLoader.read(hmx);
        if (!loaded.ok()) {
            out.problems.addAll(loaded.problems);
            return out;
        }
        out.image = loaded.image;
        out.check = loaded.check;
        out.source = ProgramLoader.relativeSource(circ, hmx);
        final Map<String, Component> chosen = picks == null ? Collections.<String, Component>emptyMap() : picks;
        ProgramLoader.Target target = clicked == null ? null : new ProgramLoader.Target(clickedCircuit, clicked);
        ProgramLoader.Plan plan = ProgramLoader.plan(loaded, circuits, target, new ProgramLoader.Chooser() {
            @Override
            public ProgramLoader.Target choose(List<ProgramLoader.Target> candidates, String what) {
                boolean text = ProgramLoader.isText(candidates.get(0).component.getFactory());
                Component pick = chosen.get(text ? TEXT : DATA);
                for (ProgramLoader.Target t : candidates) {
                    if (t.component == pick) {
                        return t;
                    }
                }
                LoadReport.Choice c = new LoadReport.Choice(
                        text ? ExecutableImage.Kind.TEXT : ExecutableImage.Kind.DATA, what);
                for (ProgramLoader.Target t : candidates) {
                    c.circuits.add(t.circuit);
                    c.components.add(t.component);
                    c.names.add(t.describe());
                }
                out.choice = c;
                return null; // 고를 때까지 아무것도 정하지 않는다
            }
        }, out.source);
        if (out.choice != null) {
            return out; // 고르기를 멈춘 것이라 "아무것도 불러오지 않았습니다"는 문제가 아니다
        }
        if (!plan.errors.isEmpty()) {
            for (Msg m : plan.problems) {
                out.problems.add(new LoadReport.Problem(0, m));
            }
            return out;
        }
        place(out, ExecutableImage.Kind.TEXT, plan.text);
        place(out, ExecutableImage.Kind.DATA, plan.data);
        if (plan.emptiedData != null) {
            out.emptiedCircuit = plan.emptiedData.circuit;
            out.emptied = plan.emptiedData.component;
            out.emptiedName = plan.emptiedData.describe();
        }
        for (ProgramLoader.Target t : plan.stackBase) {
            out.stackBase.add(t.component);
            out.stackBaseNames.add(t.describe());
        }
        out.instructions.addAll(plan.instructions);
        out.notes.addAll(plan.notes);
        for (ProgramLoader.Change ch : plan.changes) {
            out.changes.add(new LoadReport.Change(ch.target.circuit, ch.target.component, ch.attr, ch.value));
        }
        return out;
    }

    /**
     * 자동 다시 불러오기(PLAN.md 6.8): {@code source} 속성이 source인 부품이 담던 그 이미지를 다시 넣을 계획. 한 구간을
     * 담는 부품이 여럿이면 그 {@code source}를 가진 부품을 쓴다. 그래도 고를 수 없으면 문제 하나로 돌려준다(사람에게 묻지
     * 않는다).
     */
    public static LoadReport reload(File hmx, File circ, List<Circuit> circuits, String source) {
        Map<String, Component> picks = new java.util.HashMap<String, Component>();
        for (Circuit c : circuits) {
            for (Component comp : c.getNonWires()) {
                String kind = ProgramLoader.isText(comp.getFactory()) ? TEXT
                        : ProgramLoader.isData(comp.getFactory()) ? DATA : null;
                if (kind != null && !picks.containsKey(kind)
                        && source.equals(comp.getAttributeSet().getValue(MemoryFactory.SOURCE))) {
                    picks.put(kind, comp);
                }
            }
        }
        LoadReport r = load(hmx, circ, circuits, null, null, picks);
        if (r.choice != null) {
            String kind = r.choice.kind == ExecutableImage.Kind.TEXT ? "Instruction Memory" : "Data Memory";
            r.problems.add(new LoadReport.Problem(0, Msg.of(
                    "Several " + kind + " components can hold " + r.choice.segment + ", so it was not loaded again."
                            + " Load it with the Load Program… button.",
                    r.choice.segment + " 구간을 담는 " + kind + " 부품이 여럿이라 다시 불러오지 않았습니다."
                            + " Load Program… 단추로 불러오세요.")));
            r.choice = null;
        }
        return r;
    }

    /** 메모리 부품(Instruction Memory, Data Memory, 옛 Stack)의 초기 내용(주소 → 워드). 메모리 부품이 아니면 null. */
    public static SortedMap<Long, Integer> contents(Component c) {
        if (!(c.getFactory() instanceof MemoryFactory)) {
            return null;
        }
        WordImage image = c.getAttributeSet().getValue(MemoryFactory.CONTENTS);
        return (image == null ? WordImage.EMPTY : image).words();
    }

    private static void place(LoadReport out, ExecutableImage.Kind kind,
            Map<ProgramLoader.Target, List<ExecutableImage.Segment>> to) {
        for (Map.Entry<ProgramLoader.Target, List<ExecutableImage.Segment>> e : to.entrySet()) {
            ProgramLoader.Target t = e.getKey();
            out.placements.add(new LoadReport.Placement(kind, Collections.unmodifiableList(e.getValue()), t.circuit,
                    t.component, t.describe()));
        }
    }
}
