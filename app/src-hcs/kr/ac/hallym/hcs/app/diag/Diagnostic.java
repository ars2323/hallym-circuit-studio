/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;

import kr.ac.hallym.hcs.app.Messages;

/**
 * 진단 하나(PLAN.md 4.2·4.4). 원인 한 곳을 학생이 붙인 이름으로 말하고, 사실과 위치까지만 담는다. 문구는 설명 문장
 * 리소스(한국어·영어)이고, 그 안의 이름은 영어·학생 이름 그대로다(D-049).
 */
public final class Diagnostic {
    /** 검사 종류(4.2 표 순서). */
    public enum Kind {
        CLOCK_UNCONNECTED, SHORT, WIDTH_MISMATCH, INPUT_UNCONNECTED, INPUT_UNDRIVEN, TUNNEL_UNPAIRED,
        SUBCIRCUIT_PORT_UNCONNECTED, COMBINATIONAL_LOOP, MEMORY_OVERLAP,
        // 동적 진단(PLAN.md 4.3, D-01·D-03): 시뮬레이션 중 처음 생긴 사이클과 원인 한 곳
        /** E(충돌 값)가 생겼다. 문구는 원인 문장 하나. */
        E_APPEARED,
        /** 클럭 에지에 쓰려는 값(D, WriteData, Addr)이 정해지지 않았다. */
        X_WRITE_DATA,
        /** 클럭 에지에 쓰기 허용 입력(en, MemWrite)이 정해지지 않았다. */
        X_WRITE_CONTROL,
        /** 값이 멈추지 않고 계속 바뀐다(원조가 전파를 그만두고 시뮬레이션을 껐다, D-02). */
        OSCILLATION,
        /** MIPS 부품의 값 의존 문제: 영역 밖 주소, 워드 정렬, 스택 한계, syscall(D-04). 문구는 몸체의 빨간 글자. */
        MIPS_STATUS;

        /** 시뮬레이션 값으로 찾는 진단인가. */
        public boolean dynamic() {
            return ordinal() >= E_APPEARED.ordinal();
        }
    }

    public final Kind kind;
    /** 원인이 있는 회로(정적 검사는 회로 정의 단위). 동적 진단도 원인 자리를 가리킨다(누르면 그리로 간다). */
    public final Circuit circuit;
    /** 강조할 부품(원인 부품이 맨 앞). */
    public final List<Component> components;
    /** 강조할 선. */
    public final List<Wire> wires;
    /** 클릭하면 갈 곳. */
    public final Location location;
    /** 동적 진단: 원인을 본 스텝(기록 엔진의 스텝). 정적 진단은 -1. */
    public final int step;
    /** 동적 진단: 맨 위 회로에서 circuit 인스턴스까지의 서브회로 부품들(정적 진단은 빈 목록). */
    public final List<Component> instances;
    private final Object[] args;
    /** 동적 진단: E·X가 처음 생긴(또는 쓰려던) 자리. 원인 자리({@link #location})와 다를 수 있다(V-03). null이면 없음. */
    private Spot appeared;

    /** 회로 인스턴스 경로 안의 한 자리(포트나 선 끝). */
    public static final class Spot {
        public final List<Component> instances;
        public final Circuit circuit;
        public final Location at;

        Spot(List<Component> instances, Circuit circuit, Location at) {
            this.instances = Collections.unmodifiableList(new ArrayList<>(instances));
            this.circuit = circuit;
            this.at = at;
        }
    }

    Diagnostic appearedAt(List<Component> instances, Circuit circuit, Location at) {
        this.appeared = at == null ? null : new Spot(instances, circuit, at);
        return this;
    }

    public Spot appeared() {
        return appeared;
    }

    Diagnostic(Kind kind, Circuit circuit, List<Component> components, List<Wire> wires, Location location,
            Object... args) {
        this(kind, circuit, Collections.<Component>emptyList(), -1, components, wires, location, args);
    }

    Diagnostic(Kind kind, Circuit circuit, List<Component> instances, int step, List<Component> components,
            List<Wire> wires, Location location, Object... args) {
        this.kind = kind;
        this.step = step;
        this.instances = Collections.unmodifiableList(new ArrayList<>(instances));
        this.circuit = circuit;
        this.components = Collections.unmodifiableList(new ArrayList<>(components));
        this.wires = Collections.unmodifiableList(new ArrayList<>(wires));
        this.location = location;
        this.args = args.clone();
    }

    /** 문구 키: {@code diag.<kind>}. */
    public String key() {
        return "diag." + kind.name();
    }

    /** 지금 언어의 문구. */
    public String message() {
        return message(com.cburch.logisim.util.LocaleManager.getLocale());
    }

    /** 한 언어의 문구(v2 엔진이 영어·한국어 두 벌을 함께 보낸다, D-143). */
    public String message(java.util.Locale locale) {
        return Messages.get(locale, key(), Text.render(locale, args));
    }

    /** 문구 인자(테스트용). 뒤에 붙는 문장({@link Text})은 지금 언어의 글자다. */
    public List<Object> args() {
        return Collections.unmodifiableList(java.util.Arrays.asList(Text.render(
                com.cburch.logisim.util.LocaleManager.getLocale(), args)));
    }

    /** 문구 인자 그대로: 이름·수와 언어마다 다시 쓰는 문장({@link Text}). v2 엔진이 자기 문구로 다시 쓸 때 읽는다. */
    public List<Object> rawArgs() {
        return Collections.unmodifiableList(java.util.Arrays.asList(args));
    }

    /**
     * 문구 안에 들어가는 문장 하나(원인 문장, E 표기): 문구 키와 인자만 들고, 글자는 쓰는 언어로 그때 만든다(D-143).
     * 만들 때 언어를 정해 두면 v2 엔진이 한 진단을 영어·한국어 두 벌로 보낼 수 없다.
     */
    public static final class Text {
        public final String key;
        private final Object[] args;

        private Text(String key, Object... args) {
            this.key = key;
            this.args = args.clone();
        }

        public static Text of(String key, Object... args) {
            return new Text(key, args);
        }

        /** 인자 그대로(이름·수와 안쪽 Text). */
        public List<Object> args() {
            return Collections.unmodifiableList(java.util.Arrays.asList(args));
        }

        /** 한 언어의 글자. */
        public String render(java.util.Locale locale) {
            return Messages.get(locale, key, render(locale, args));
        }

        /** 인자 가운데 Text를 그 언어의 글자로 바꾼 사본. */
        static Object[] render(java.util.Locale locale, Object[] args) {
            Object[] out = args.clone();
            for (int i = 0; i < out.length; i++) {
                if (out[i] instanceof Text) {
                    out[i] = ((Text) out[i]).render(locale);
                }
            }
            return out;
        }

        /** 지금 언어의 글자(v1 화면과 원조 문구 틀에 그대로 들어간다). */
        @Override
        public String toString() {
            return render(com.cburch.logisim.util.LocaleManager.getLocale());
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Text && ((Text) o).key.equals(key) && java.util.Arrays.equals(((Text) o).args, args);
        }

        @Override
        public int hashCode() {
            return key.hashCode() * 31 + java.util.Arrays.hashCode(args);
        }
    }

    @Override
    public String toString() {
        return kind + " " + args();
    }
}
