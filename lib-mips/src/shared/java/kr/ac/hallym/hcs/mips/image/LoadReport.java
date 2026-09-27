/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 실행 이미지 불러오기 한 번의 결과(N-16, D-147): 트랙 A 메뉴와 같은 길(lib-mips {@code ProgramLoader}의 read·plan)이
 * 정한 것을 v2 엔진에 넘기는 데이터다. 공용 코드라 Logisim 클래스를 쓰지 않는다(D-125): 회로·부품·속성·값은
 * {@link Object}로 들고, 엔진이 제 형으로 되돌린다(원조 JAR 라이브러리 클래스 로더는 부모를 먼저 찾으므로 엔진 안에서는
 * 이 클래스도 하나다).
 *
 * <p>아무것도 바꾸지 않는다. 바꿀 것은 {@link #changes}이고, 문제가 하나라도 있거나({@link #problems}) 고를 것이
 * 남았으면({@link #choice}) 비어 있다(전부 아니면 전무, D-126).
 */
public final class LoadReport {
    /** 읽지 못했거나 넣지 못한 이유 하나: 줄 번호(0: 특정 줄이 아님)와 두 언어 문장("줄 → 무엇이 → 무엇을 할지"). */
    public static final class Problem {
        public final int line;
        public final Msg text;

        public Problem(int line, Msg text) {
            this.line = line;
            this.text = text;
        }
    }

    /** 구간들을 넣을 메모리 부품 하나. */
    public static final class Placement {
        public final ExecutableImage.Kind kind;
        public final List<ExecutableImage.Segment> segments;
        public final Object circuit;
        public final Object component;
        /** 트랙 A 목록과 같은 이름. 예: {@code main › Instruction Memory (00400000-004fffff)}. */
        public final String name;

        public Placement(ExecutableImage.Kind kind, List<ExecutableImage.Segment> segments, Object circuit,
                Object component, String name) {
            this.kind = kind;
            this.segments = segments;
            this.circuit = circuit;
            this.component = component;
            this.name = name;
        }
    }

    /** 부품 속성 하나를 바꾼다(엔진이 원조 SetAttributeAction으로 한다). */
    public static final class Change {
        public final Object circuit;
        public final Object component;
        public final Object attribute;
        public final Object value;

        public Change(Object circuit, Object component, Object attribute, Object value) {
            this.circuit = circuit;
            this.component = component;
            this.attribute = attribute;
            this.value = value;
        }
    }

    /** 한 구간을 담는 부품이 여럿이라 골라야 한다. 고른 부품을 주고 다시 부르면 이어서 정한다. */
    public static final class Choice {
        public final ExecutableImage.Kind kind;
        /** 구간 글(예: {@code .data 0x10010000-0x1001001b (28 bytes)}). */
        public final String segment;
        public final List<Object> circuits = new ArrayList<Object>();
        public final List<Object> components = new ArrayList<Object>();
        public final List<String> names = new ArrayList<String>();

        public Choice(ExecutableImage.Kind kind, String segment) {
            this.kind = kind;
            this.segment = segment;
        }
    }

    /** 고른 파일. */
    public final File file;
    /** 읽은 이미지. 읽지 못했으면 null. */
    public ExecutableImage image;
    /** 원본 .s 대조. 읽지 못했으면 null. */
    public SourceCheck check;
    public final List<Problem> problems = new ArrayList<Problem>();
    /** 고를 것이 남았으면 그것(문제가 아니다). */
    public Choice choice;
    public final List<Placement> placements = new ArrayList<Placement>();
    /** {@code .data}가 없어 비운 Data Memory(없으면 null). */
    public Object emptiedCircuit;
    public Object emptied;
    public String emptiedName;
    /** {@code reg $sp}를 깊이 기준으로 기억한 부품들과 그 이름. */
    public final List<Object> stackBase = new ArrayList<Object>();
    public final List<String> stackBaseNames = new ArrayList<String>();
    /** 이미지 {@code .text}가 쓰는 명령어 이름(알파벳 순). */
    public final List<String> instructions = new ArrayList<String>();
    /** 트랙 A 요약 창과 같은 요약 줄(이름·숫자뿐이라 두 언어가 같다, D-049). */
    public final List<String> notes = new ArrayList<String>();
    public final List<Change> changes = new ArrayList<Change>();
    /** {@code source} 속성에 적을 글(.circ 기준 상대 경로). */
    public String source;

    public LoadReport(File file) {
        this.file = file;
    }

    /** 넣을 수 있다: 이미지가 있고 문제도 고를 것도 없다. */
    public boolean ok() {
        return image != null && problems.isEmpty() && choice == null;
    }
}
