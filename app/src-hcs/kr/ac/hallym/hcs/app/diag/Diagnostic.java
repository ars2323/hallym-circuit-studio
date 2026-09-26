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
        X_WRITE_CONTROL;

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
        return Messages.get(key(), args);
    }

    /** 문구 인자(테스트용). */
    public List<Object> args() {
        return Collections.unmodifiableList(java.util.Arrays.asList(args));
    }

    @Override
    public String toString() {
        return kind + " " + args();
    }
}
