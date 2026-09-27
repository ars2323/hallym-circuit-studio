/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 실행 이미지의 시작에 관한 사실(Z-03, D-138): 불러오기 요약 머리 줄(entry, 레지스터 시작 값), 예외 처리기 없이 어셈블한
 * 이미지라는 사실 줄, 진입 루틴의 {@code jr $ra} 사실 줄, 상태 표시줄의 PC ≠ entry 사실 줄. v2 화면(Electron)이 엔진 API로 받아 보이고, 트랙 A 요약 창도
 * 같은 줄을 쓴다. 글은 영어·한국어 두 벌({@link Msg})이고 이름(레지스터, 키, 기호)은 영어 그대로, 그 바로 뒤에 조사를 붙이지
 * 않는다(D-126).
 *
 * <p>사실만 말한다. 도구는 학생의 레지스터에 값을 넣지 않고(CLAUDE.md 2.6), 회로가 맞는지 판단하지 않는다. 그래서 이 줄들은
 * Messages 진단이 아니다.
 */
public final class StartFacts {
    private StartFacts() {
    }

    /** {@code jr $ra}(jr $31) 워드. */
    public static final int JR_RA = 0x03e00008;
    /** {@code jal}의 opcode. */
    private static final int JAL = 3;
    /** SPIM이 실행을 시작하는 라벨. 예외 처리기 파일의 시작 코드, 또는 처리기 없이 어셈블한 프로그램 자신의 라벨. */
    public static final String START_LABEL = "__start";

    // ---- 요약 머리 줄: 이름·숫자뿐이라 두 언어 모두 영어(D-049) ----

    /**
     * 요약 머리 줄: 첫 줄은 entry({@link #entryLine}), 이어서 {@code reg}마다 한 줄(파일 순서, {@link #regLines}).
     */
    public static List<Msg> summary(ExecutableImage image) {
        List<Msg> out = new ArrayList<Msg>();
        out.add(entryLine(image));
        out.addAll(regLines(image));
        return out;
    }

    /**
     * 예: {@code entry 0x00400024 (main)}. entry 주소의 기호가 여럿이면 파일 순서로 쉼표로 잇는다({@code (main, start)}),
     * 없으면 괄호를 붙이지 않는다. entry가 없으면(전환용 .s에 main이 없음) {@code no entry}.
     */
    public static Msg entryLine(ExecutableImage image) {
        Long entry = image.entry();
        if (entry == null) {
            return same("no entry");
        }
        StringBuilder sb = new StringBuilder("entry ").append(ExecutableImage.hex(entry));
        List<String> names = image.symbolsAt(entry);
        if (!names.isEmpty()) {
            sb.append(" (");
            for (int i = 0; i < names.size(); i++) {
                sb.append(i == 0 ? "" : ", ").append(names.get(i));
            }
            sb.append(')');
        }
        return same(sb.toString());
    }

    /** {@code reg}마다 한 줄, 파일 순서. 예: {@code reg $sp 0x7ffff000}. 이름은 표준 이름({@code $29} → {@code $sp}). */
    public static List<Msg> regLines(ExecutableImage image) {
        List<Msg> out = new ArrayList<Msg>();
        for (Map.Entry<String, Long> r : image.regs().entrySet()) {
            out.add(same("reg " + r.getKey() + " " + ExecutableImage.hex(r.getValue())));
        }
        return out;
    }

    // ---- 진입 루틴과 jr $ra ----

    /**
     * 진입 루틴의 범위 {@code [시작, 끝)}: entry부터, entry를 담는 {@code .text} 구간 안에서 entry보다 뒤에 있는 첫
     * {@code jal} 목적지(다른 루틴의 시작) 앞까지, 그런 곳이 없으면 구간 끝까지. entry가 없거나 어느 {@code .text} 구간에도
     * 없으면 null.
     *
     * <p>"다음 기호"가 아니라 "다음 {@code jal} 목적지"로 자른다: 기호에는 루틴 안의 라벨({@code loop}, {@code done})도
     * 들어 있어, 다음 기호에서 자르면 반복문 뒤의 {@code jr $ra}를 놓친다. {@code jal}로 부르는 곳은 따로 복귀하는 루틴이다.
     * 워드만 보고 정하므로 기호가 없는 이미지에서도 같다.
     */
    public static long[] entryRoutine(ExecutableImage image) {
        Long entry = image.entry();
        if (entry == null) {
            return null;
        }
        for (ExecutableImage.Segment s : image.segments(ExecutableImage.Kind.TEXT)) {
            if (s.start <= entry && entry < s.end()) {
                for (long target : callTargets(image)) { // 오름차순: entry 뒤의 첫 목적지
                    if (target > entry) {
                        return new long[] {entry, Math.min(target, s.end())};
                    }
                }
                return new long[] {entry, s.end()};
            }
        }
        return null;
    }

    /** 이미지 {@code .text}의 모든 {@code jal} 목적지(오름차순). 목적지 = (jal 주소 + 4)의 위 4비트 | index × 4. */
    public static List<Long> callTargets(ExecutableImage image) {
        TreeSet<Long> out = new TreeSet<Long>();
        for (Map.Entry<Long, Integer> e : image.textWords().entrySet()) {
            int w = e.getValue();
            if (w >>> 26 == JAL) {
                out.add(((e.getKey() + 4) & 0xf0000000L) | ((w & 0x03ffffffL) << 2));
            }
        }
        return new ArrayList<Long>(out);
    }

    /** 진입 루틴({@link #entryRoutine}) 안의 {@code jr $ra} 주소들(오름차순). 없으면 빈 목록. */
    public static List<Long> jrRaInEntryRoutine(ExecutableImage image) {
        List<Long> out = new ArrayList<Long>();
        long[] r = entryRoutine(image);
        if (r == null) {
            return out;
        }
        for (Map.Entry<Long, Integer> e : image.textWords().subMap(r[0], r[1]).entrySet()) {
            if (e.getValue() == JR_RA) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    // ---- 예외 처리기 없이 어셈블한 이미지 ----

    /**
     * 예외 처리기 없이 어셈블한 이미지인가: 기호에 {@code __start}가 있다. 명세(hmx-format.md)는 처리기 파일의 라벨
     * ({@code __start} 등)을 기호에 넣지 않고, 처리기를 불러온 채로는 프로그램이 {@code __start}를 또 정의할 수 없다(SPIM이
     * 같은 라벨 두 번을 오류로 알림). 그래서 {@code __start} 기호는 처리기 없이 어셈블한 프로그램 자신의 라벨뿐이다. 워드
     * 모양(시작 코드 9워드)으로 가리지 않는다: 처리기 파일은 설정에서 바꿀 수 있다.
     */
    public static boolean withoutExceptionHandler(ExecutableImage image) {
        return image.symbols().containsKey(START_LABEL);
    }

    /**
     * 예외 처리기 없이 어셈블한 이미지면 사실 줄 하나, 아니면 null. 시작 코드가 없고 프로그램 자신의 {@code __start}가
     * 있다. entry가 {@code __start}면 그렇게, 아니면(main도 있음) {@code __start} 주소를 말한다.
     */
    public static Msg noHandlerFact(ExecutableImage image) {
        if (!withoutExceptionHandler(image)) {
            return null;
        }
        long start = image.symbols().get(START_LABEL);
        Long entry = image.entry();
        if (entry != null && entry == start) {
            return Msg.of("Assembled without the exception handler: no start-up code, entry = the program's own"
                    + " __start.", "예외 처리기 없이 어셈블한 이미지: 시작 코드 없음, 진입점 = 프로그램의 __start.");
        }
        String at = ExecutableImage.hex(start);
        return Msg.of("Assembled without the exception handler: no start-up code, the program's own __start = " + at
                + ".", "예외 처리기 없이 어셈블한 이미지: 시작 코드 없음, 프로그램의 __start = " + at + ".");
    }

    /**
     * 진입 루틴에 {@code jr $ra}가 있으면 사실 줄 하나, 없으면 null. 예외 처리기 없이 어셈블한 이미지는 시작 코드가 없어
     * 말하지 않는다({@link #withoutExceptionHandler}). 회로는 entry에서 시작해 시작 코드({@code jal main})가
     * 돌지 않으므로 {@code $ra}는 학생 회로가 주는 값이다. 파일에 {@code reg $ra}가 있으면 그 값도 보인다(도구는 넣지 않는다).
     *
     * <p>예: {@code jr $ra in the entry routine: 0x00400034. The startup code does not run in the circuit, so $ra is
     * whatever the circuit gives.} / {@code 진입 루틴의 jr $ra: 0x00400034. 회로에서는 시작 코드가 돌지 않으므로 $ra 값은
     * 회로가 주는 값입니다.}
     */
    public static Msg jrRaFact(ExecutableImage image) {
        List<Long> at = jrRaInEntryRoutine(image);
        if (at.isEmpty() || withoutExceptionHandler(image)) {
            return null;
        }
        StringBuilder where = new StringBuilder();
        for (Long a : at) {
            where.append(where.length() == 0 ? "" : ", ").append(ExecutableImage.hex(a));
        }
        String en = "jr $ra in the entry routine: " + where
                + ". The startup code does not run in the circuit, so $ra is whatever the circuit gives.";
        String ko = "진입 루틴의 jr $ra: " + where + ". 회로에서는 시작 코드가 돌지 않으므로 $ra 값은 회로가 주는 값입니다.";
        Long ra = image.reg("$ra");
        if (ra != null) {
            String v = ExecutableImage.hex(ra);
            en += " The file gives reg $ra " + v + " (the tool does not set registers).";
            ko += " 파일의 시작 값: reg $ra " + v + "(도구는 레지스터 값을 넣지 않습니다).";
        }
        return Msg.of(en, ko);
    }

    /** 불러오기 요약의 사실 줄: {@link #noHandlerFact}, {@link #jrRaFact}(있는 것만, 이 순서). 없으면 빈 목록. */
    public static List<Msg> facts(ExecutableImage image) {
        List<Msg> out = new ArrayList<Msg>();
        for (Msg m : new Msg[] {noHandlerFact(image), jrRaFact(image)}) {
            if (m != null) {
                out.add(m);
            }
        }
        return out;
    }

    // ---- 상태 표시줄 ----

    /**
     * 회로의 PC가 entry와 다를 때 상태 표시줄에 보일 사실 줄. 예: {@code PC 0x00400000 · executable image entry
     * 0x00400024} / {@code PC 0x00400000 · 실행 이미지 진입점 0x00400024}. PC가 entry와 같거나 entry가 없으면 null.
     * Messages 진단이 아니다(동작하는 회로를 판단하지 않는다).
     */
    public static Msg pcFact(ExecutableImage image, long pc) {
        Long entry = image.entry();
        long p = pc & 0xffffffffL;
        if (entry == null || p == entry) {
            return null;
        }
        String e = ExecutableImage.hex(entry);
        return Msg.of("PC " + ExecutableImage.hex(p) + " · executable image entry " + e,
                "PC " + ExecutableImage.hex(p) + " · 실행 이미지 진입점 " + e);
    }

    private static Msg same(String text) {
        return Msg.of(text, text);
    }
}
