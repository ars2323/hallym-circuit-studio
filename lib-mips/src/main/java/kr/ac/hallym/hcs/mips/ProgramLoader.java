/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeSet;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.instance.StdAttr;

import kr.ac.hallym.hcs.mips.image.ExecutableImage;
import kr.ac.hallym.hcs.mips.image.HmxError;
import kr.ac.hallym.hcs.mips.image.HmxParser;
import kr.ac.hallym.hcs.mips.image.Msg;
import kr.ac.hallym.hcs.mips.image.SourceCheck;
import kr.ac.hallym.hcs.mips.image.StartFacts;

/**
 * 실행 이미지를 어느 메모리에 어떤 속성으로 넣을지 정한다(PLAN.md 6.3, Z-01, D-126). GUI와 떨어져 있어 테스트할 수
 * 있다. .hmx와 전환용 .s({@link AssemblyTransition})가 같은 이미지 모델로 이 한 길을 지난다.
 *
 * <ul>
 *   <li>.text는 파일의 주소 그대로 Instruction Memory에 넣는다(시작 코드 포함, 자르거나 옮기지 않는다).</li>
 *   <li>.data는 그 구간을 담는 Data Memory(Stack 제외)에 넣는다.</li>
 *   <li>어느 부품도 담지 않는 구간이 있으면 구간과 범위를 말하고 아무것도 넣지 않는다(전부 아니면 전무).</li>
 *   <li>{@code reg $sp}가 있으면 그 값을 품는 Stack의 깊이 기준이 된다(0x7FFFEFFC 규칙보다 파일 값이 먼저).</li>
 * </ul>
 *
 * <p>요약은 entry 줄과 {@code reg} 줄로 시작하고, 진입 루틴에 {@code jr $ra}가 있으면 사실 줄을 더한다(공용
 * {@link StartFacts}, Z-03, D-138). 레지스터에는 아무 값도 넣지 않는다.
 */
final class ProgramLoader {
    private ProgramLoader() {
    }

    /** 회로 안의 메모리 부품 하나. */
    static final class Target {
        final Circuit circuit;
        final Component component;

        Target(Circuit circuit, Component component) {
            this.circuit = circuit;
            this.component = component;
        }

        long[] region() {
            return MemoryFactory.region(component.getAttributeSet());
        }

        boolean contains(long addr) {
            return MemoryFactory.contains(region(), (int) addr);
        }

        /** 구간 전체가 이 부품의 영역 안인가. */
        boolean covers(ExecutableImage.Segment s) {
            long[] r = region();
            return s.count == 0 ? r[0] <= s.start && s.start <= r[1] : r[0] <= s.start && s.end() <= r[1];
        }

        /** 목록에 보일 이름. 예: {@code datapath › IMem (00400000-004fffff)}. */
        String describe() {
            String label = component.getAttributeSet().getValue(StdAttr.LABEL);
            String name = label == null || label.isEmpty() ? component.getFactory().getDisplayName() : label;
            long[] r = region();
            return circuit.getName() + " › " + name + " (" + WordImage.hex(r[0]) + "-" + WordImage.hex(r[1] - 1) + ")";
        }

        @Override
        public String toString() {
            return describe();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Target && ((Target) o).component == component;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(component);
        }
    }

    static final class Change {
        final Target target;
        final Attribute<?> attr;
        final Object value;

        Change(Target target, Attribute<?> attr, Object value) {
            this.target = target;
            this.attr = attr;
            this.value = value;
        }
    }

    /**
     * 파일을 읽은 결과. 오류가 있으면 이미지는 없다. 보일 내용은 데이터로 둔다(이미지의 entry·레지스터 시작 값·기호,
     * 원본 대조 결과). 글 줄은 트랙 A 요약 창이 그대로 쓴다.
     */
    static final class Loaded {
        final File file;
        ExecutableImage image;
        /** 원본 .s 대조(.hmx만). .s 전환 경로는 null. */
        SourceCheck check;
        /** 전환용 .s 경로로 읽었는가(요약의 ".s 임시 지원"). */
        boolean transition;
        /** 읽지 못한 이유(설명 문장, 언어 설정을 따른다). */
        final List<String> errors = new ArrayList<String>();
        /** 요약 머리 줄(어디서 온 이미지인가). */
        final List<String> notes = new ArrayList<String>();
        /** 사실 줄(원본 .s 대조). */
        final List<String> facts = new ArrayList<String>();
        /** 노란 사실 줄(원본 .s가 내보낸 뒤 바뀜). */
        final List<String> warnings = new ArrayList<String>();

        Loaded(File file) {
            this.file = file;
        }

        boolean ok() {
            return image != null && errors.isEmpty();
        }
    }

    /** 무엇을 바꿀지와 요약. {@link #errors}가 있으면 아무것도 바꾸지 않는다. */
    static final class Plan {
        /** 넣을 이미지(entry, 레지스터 시작 값, 기호는 여기서 읽는다). */
        ExecutableImage image;
        /** 구간마다 담을 부품(데이터). 오류가 있으면 비어 있다. */
        final Map<Target, List<ExecutableImage.Segment>> text = new LinkedHashMap<Target, List<ExecutableImage.Segment>>();
        final Map<Target, List<ExecutableImage.Segment>> data = new LinkedHashMap<Target, List<ExecutableImage.Segment>>();
        /** .data가 없어 비운 Data Memory. 없으면 null. */
        Target emptiedData;
        /** reg $sp를 깊이 기준으로 기억한 Stack들. */
        final List<Target> stackBase = new ArrayList<Target>();
        /** 이미지 .text의 명령어 이름(알파벳 순). */
        final List<String> instructions = new ArrayList<String>();
        final List<Change> changes = new ArrayList<Change>();
        /** 요약 줄. 앞 {@link #startLines}개는 entry·reg 줄({@link StartFacts#summary}). */
        final List<String> notes = new ArrayList<String>();
        /** notes 앞의 entry·reg 줄 수. */
        int startLines;
        final List<String> facts = new ArrayList<String>();
        final List<String> warnings = new ArrayList<String>();
        final List<String> errors = new ArrayList<String>();
    }

    /** 후보가 여럿일 때 하나를 고른다(GUI는 묻고, 테스트는 정한다). null이면 취소. */
    interface Chooser {
        Target choose(List<Target> candidates, String what);
    }

    static boolean isText(ComponentFactory f) {
        return f instanceof InstructionMemory;
    }

    static boolean isData(ComponentFactory f) {
        return f instanceof DataMemory && !(f instanceof StackMemory);
    }

    static boolean isStack(ComponentFactory f) {
        return f instanceof StackMemory;
    }

    /** 파일의 모든 회로에서 .text(또는 .data)를 받을 부품. */
    static List<Target> find(List<Circuit> circuits, boolean text) {
        List<Target> out = new ArrayList<Target>();
        for (Circuit c : circuits) {
            for (Component comp : c.getNonWires()) {
                if (text ? isText(comp.getFactory()) : isData(comp.getFactory())) {
                    out.add(new Target(c, comp));
                }
            }
        }
        return out;
    }

    static List<Target> findStacks(List<Circuit> circuits) {
        List<Target> out = new ArrayList<Target>();
        for (Circuit c : circuits) {
            for (Component comp : c.getNonWires()) {
                if (isStack(comp.getFactory())) {
                    out.add(new Target(c, comp));
                }
            }
        }
        return out;
    }

    // ---- 파일 읽기 ----

    /** 실행 이미지(.hmx) 또는 전환용 .s를 읽는다. */
    static Loaded read(File file) {
        if (AssemblyTransition.accepts(file)) {
            return AssemblyTransition.read(file);
        }
        return readImage(file);
    }

    /** .hmx를 읽고 원본 .s와 대조한다. hcs-asm은 쓰지 않는다. */
    static Loaded readImage(File hmx) {
        Loaded out = new Loaded(hmx);
        HmxParser.Result r;
        try {
            r = HmxParser.read(hmx);
        } catch (IOException e) {
            out.errors.add(Text.of("Cannot read the file " + hmx.getName() + ": " + e.getMessage(),
                    "파일을 읽을 수 없습니다. File: " + hmx.getName() + " (" + e.getMessage() + ")").get());
            return out;
        }
        if (!r.ok()) {
            for (HmxError e : r.errors) {
                out.errors.add(e.text(Text.korean()));
            }
            return out;
        }
        out.image = r.image;
        StringBuilder origin = new StringBuilder(Text.name("Executable image ").get()).append(hmx.getName());
        if (r.image.producedBy() != null) {
            origin.append(", ").append(r.image.producedBy());
        }
        if (r.image.assembled() != null) {
            origin.append(", ").append(r.image.assembled());
        }
        out.notes.add(origin.toString());
        SourceCheck check = SourceCheck.check(hmx, r.image);
        out.check = check;
        String line = check.message().get(Text.korean());
        (check.warns() ? out.warnings : out.facts).add(line);
        return out;
    }

    // ---- 넣을 곳 정하기 ----

    /** 파일 전체 회로에서 넣을 곳을 정한다. clicked는 우클릭한 부품(없으면 null). */
    static Plan plan(Loaded loaded, List<Circuit> circuits, Target clicked, Chooser chooser, String source) {
        Plan plan = plan(loaded.image, find(circuits, true), find(circuits, false), findStacks(circuits), clicked,
                chooser, source);
        plan.notes.addAll(plan.startLines, loaded.notes);
        plan.facts.addAll(loaded.facts);
        plan.warnings.addAll(loaded.warnings);
        return plan;
    }

    static Plan plan(ExecutableImage img, List<Target> texts, List<Target> datas, List<Target> stacks,
            Target clicked, Chooser chooser, String source) {
        Plan plan = new Plan();
        plan.image = img;
        // 요약 머리: entry 줄, reg 줄(파일 순서). 사실 줄: 진입 루틴의 jr $ra(Z-03)
        for (Msg m : StartFacts.summary(img)) {
            plan.notes.add(m.get(Text.korean()));
        }
        plan.startLines = plan.notes.size();
        for (Msg m : StartFacts.facts(img)) {
            plan.facts.add(m.get(Text.korean()));
        }
        Map<Target, List<ExecutableImage.Segment>> textTo = assign(plan, img.segments(ExecutableImage.Kind.TEXT),
                texts, clicked != null && isText(clicked.component.getFactory()) ? clicked : null, chooser,
                Text.name("Instruction Memory").get());
        Map<Target, List<ExecutableImage.Segment>> dataTo = assign(plan, img.segments(ExecutableImage.Kind.DATA),
                datas, clicked != null && isData(clicked.component.getFactory()) ? clicked : null, chooser,
                Text.name("Data Memory").get());
        if (!plan.errors.isEmpty() || textTo == null || dataTo == null) {
            if (plan.errors.isEmpty()) {
                plan.errors.add(Text.of("Nothing was loaded.", "아무것도 불러오지 않았습니다.").get()); // 고르기 취소
            }
            return plan; // 바꿀 것(changes)은 아직 하나도 없다
        }
        plan.text.putAll(textTo);
        plan.data.putAll(dataTo);

        // .text: 파일 주소 그대로
        String entry = img.entry() == null ? Text.name("no entry (no main label)").get()
                : Text.name("entry ").get() + ExecutableImage.hex(img.entry());
        if (textTo.isEmpty()) {
            plan.notes.add(Text.name(".text: none").get());
        }
        for (Map.Entry<Target, List<ExecutableImage.Segment>> e : textTo.entrySet()) {
            put(plan, e.getKey(), img.words(e.getValue()), source);
            plan.notes.add(".text: " + ExecutableImage.describe(e.getValue()) + ", " + entry + " → "
                    + e.getKey().describe());
        }

        // .data: 그 구간을 담는 Data Memory. .data가 없으면 이 프로그램의 Data Memory를 비운다.
        if (dataTo.isEmpty()) {
            Target own = clicked != null && isData(clicked.component.getFactory()) ? clicked
                    : datas.size() == 1 ? datas.get(0) : null;
            if (own != null) {
                plan.emptiedData = own;
                put(plan, own, img.words(new ArrayList<ExecutableImage.Segment>()), source);
                plan.notes.add(Text.name(".data: none (emptied ").get() + own.describe() + ")");
            } else {
                plan.notes.add(Text.name(".data: none").get());
            }
        }
        for (Map.Entry<Target, List<ExecutableImage.Segment>> e : dataTo.entrySet()) {
            put(plan, e.getKey(), img.words(e.getValue()), source);
            plan.notes.add(".data: " + ExecutableImage.describe(e.getValue()) + " → " + e.getKey().describe());
        }

        stackDepthBase(plan, img.reg("$sp"), stacks);

        plan.instructions.addAll(usedInstructions(img));
        plan.notes.add(Text.name("Instructions used: ").get() + String.join(", ", plan.instructions));
        return plan;
    }

    /**
     * {@code reg $sp}를 Stack 깊이 기준으로 기억한다(D-126 4번, D-138): 파일의 {@code $sp} 바로 아래 워드를 담는 Stack의
     * {@code contents}에 주소만 있는 줄로 적고, {@code reg $sp}가 없는 이미지면 기준을 지운다. 파일 값이 0x7FFFEFFC 규칙보다
     * 먼저다. 레지스터에는 아무것도 넣지 않는다. $sp를 다루는 곳은 여기 하나다(Data Memory와 Stack을 한 부품으로 합치면 이
     * 메서드만 그 부품의 스택 영역을 보도록 바꾼다).
     */
    static void stackDepthBase(Plan plan, Long sp, List<Target> stacks) {
        for (Target st : stacks) {
            WordImage now = st.component.getAttributeSet().getValue(MemoryFactory.CONTENTS);
            now = now == null ? WordImage.EMPTY : now;
            Long want = sp != null && st.contains(sp - 4) ? sp : null;
            WordImage next = now.withInitialSp(want);
            if (!next.equals(now)) {
                plan.changes.add(new Change(st, MemoryFactory.CONTENTS, next));
            }
            if (want != null) {
                plan.stackBase.add(st);
                plan.notes.add(Text.name("$sp ").get() + ExecutableImage.hex(sp) + Text.name(": Stack depth base of ")
                        .get() + st.describe());
            }
        }
    }

    /**
     * 구간마다 담을 부품을 정한다. 우클릭한 부품(preferred)이 그 종류면 그 부품이 모두 담아야 한다. 아니면 담는 부품이
     * 하나면 그것, 여럿이면 묻는다. 담는 부품이 없으면 오류를 남긴다. 고르기를 취소하면 null.
     */
    private static Map<Target, List<ExecutableImage.Segment>> assign(Plan plan, List<ExecutableImage.Segment> segs,
            List<Target> candidates, Target preferred, Chooser chooser, String kind) {
        Map<Target, List<ExecutableImage.Segment>> out = new LinkedHashMap<Target, List<ExecutableImage.Segment>>();
        Target lastChoice = null;
        for (ExecutableImage.Segment s : segs) {
            Target to = null;
            if (preferred != null) {
                if (preferred.covers(s)) {
                    to = preferred;
                } else {
                    plan.errors.add(Text.of(s + " is outside the chosen " + preferred.describe()
                            + ", so nothing was loaded.",
                            s + " 구간이 고른 부품 " + preferred.describe() + " 영역 밖에 있어 아무것도 불러오지 않았습니다.")
                            .get());
                    continue;
                }
            } else {
                List<Target> covering = new ArrayList<Target>();
                for (Target t : candidates) {
                    if (t.covers(s)) {
                        covering.add(t);
                    }
                }
                if (covering.isEmpty()) {
                    plan.errors.add(noMemory(s, candidates, kind));
                    continue;
                } else if (covering.size() == 1) {
                    to = covering.get(0);
                } else if (lastChoice != null && covering.contains(lastChoice)) {
                    to = lastChoice;
                } else {
                    to = chooser == null ? covering.get(0) : chooser.choose(covering, s.toString());
                    if (to == null) {
                        return null;
                    }
                    lastChoice = to;
                }
            }
            List<ExecutableImage.Segment> list = out.get(to);
            if (list == null) {
                list = new ArrayList<ExecutableImage.Segment>();
                out.put(to, list);
            }
            list.add(s);
        }
        return out;
    }

    private static String noMemory(ExecutableImage.Segment s, List<Target> candidates, String kind) {
        StringBuilder have = new StringBuilder();
        for (Target t : candidates) {
            have.append(have.length() == 0 ? "" : ", ").append(t.describe());
        }
        String list = candidates.isEmpty() ? "" : " " + kind + ": " + have + ".";
        String listKo = candidates.isEmpty() ? " 회로에 " + kind + " 부품이 없습니다."
                : " 이 파일의 " + kind + " 부품: " + have + ".";
        return Text.of("No " + kind + " covers " + s + ", so nothing was loaded."
                + (candidates.isEmpty() ? " The circuit has no " + kind + "." : list),
                s + " 구간을 담는 " + kind + " 부품이 없어 아무것도 불러오지 않았습니다." + listKo).get();
    }

    private static void put(Plan plan, Target t, SortedMap<Long, Integer> words, String source) {
        WordImage old = t.component.getAttributeSet().getValue(MemoryFactory.CONTENTS);
        plan.changes.add(new Change(t, MemoryFactory.CONTENTS, WordImage.of(words).withInitialSp(
                old == null ? null : old.initialSp())));
        plan.changes.add(new Change(t, MemoryFactory.SOURCE, source));
    }

    /**
     * 이미지 .text의 모든 워드가 쓰는 명령어 이름(알파벳 순, PLAN.md 6.6). 시작 코드도 이미지의 일부라 함께 센다:
     * 실행 이미지는 어느 워드가 시작 코드인지 적지 않고, 도구는 entry나 기호로 추측하지 않는다(D-126).
     */
    static List<String> usedInstructions(ExecutableImage img) {
        TreeSet<String> names = new TreeSet<String>();
        for (Integer w : img.textWords().values()) {
            String m = kr.ac.hallym.hcs.mips.disasm.Disassembler.mnemonic(w); // D-127: 공용 디스어셈블러
            names.add(m == null ? "?" : m);
        }
        return new ArrayList<String>(names);
    }

    /** .circ 폴더 기준 상대 경로(PLAN.md 6.8). 구분자는 '/'. 기준이 없거나 다른 드라이브면 절대 경로. */
    static String relativeSource(File circFile, File source) {
        File abs = source.getAbsoluteFile();
        if (circFile == null || circFile.getAbsoluteFile().getParentFile() == null) {
            return abs.getPath();
        }
        try {
            java.net.URI base = circFile.getAbsoluteFile().getParentFile().toURI();
            java.net.URI rel = base.relativize(abs.toURI());
            if (!rel.isAbsolute()) {
                return new File(rel.getPath()).getPath().replace(File.separatorChar, '/');
            }
        } catch (IllegalArgumentException e) {
            // 다른 루트
        }
        return abs.getPath();
    }

    /** source 속성(상대 경로면 .circ 폴더 기준)을 파일로. */
    static File resolveSource(File circFile, String source) {
        File f = new File(source);
        if (!f.isAbsolute() && circFile != null && circFile.getAbsoluteFile().getParentFile() != null) {
            f = new File(circFile.getAbsoluteFile().getParentFile(), source);
        }
        return f;
    }
}
