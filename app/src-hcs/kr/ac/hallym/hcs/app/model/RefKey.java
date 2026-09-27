/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 객체의 정체로 가르는 열쇠이자 바뀜 서명(D-129). {@link System#identityHashCode}는 고유한 값이 아니다: 서로 다른 두
 * 객체가 같은 값을 가질 수 있고, {@code -XX:hashCode=2} JVM에서는 모두 같다. 그래서 그 값을 문자열·수 열쇠, 방문 표시,
 * "바뀌었나" 서명으로 쓰지 않고 이 열쇠를 쓴다. 부분마다 객체는 {@code ==}로, 값은 {@code equals}로 비교한다.
 * {@link #hashCode}는 해시 표의 자리 고르기에만 쓰이므로 identity hash를 섞어도 된다(겹쳐도 equals가 가른다).
 * <ul>
 * <li>{@link Builder#ref}: 객체(부품, 회로, 넷, 선 …)를 {@code ==}로.</li>
 * <li>{@link Builder#refs}: 차례가 있는 목록(서브회로 인스턴스 경로 등)을 길이와 원소마다 {@code ==}로.</li>
 * <li>{@link Builder#refSet}: 차례 없는 모음(회로의 부품·선)을 원소마다 {@code ==}로.</li>
 * <li>{@link Builder#value}: 값(수, 문자열, 열거값, 위치, 사각형 …)을 {@code equals}로. 나중에 바뀔 수 있는 값은
 * 복사해서 넘긴다.</li>
 * </ul>
 */
public final class RefKey {
    private static final byte REF = 0;
    private static final byte VALUE = 1;

    private final Object[] parts;
    private final byte[] modes;
    private final int hash;

    private RefKey(List<Object> parts, List<Byte> modes) {
        this.parts = parts.toArray();
        this.modes = new byte[modes.size()];
        int h = 1;
        for (int i = 0; i < this.parts.length; i++) {
            this.modes[i] = modes.get(i);
            Object p = this.parts[i];
            h = 31 * h + (this.modes[i] == REF ? System.identityHashCode(p) : Objects.hashCode(p));
        }
        this.hash = h;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 회로 모양의 서명: 부품과 선의 정체(==, 차례 없이). 원조는 부품·선을 옮기거나 지우고 다시 놓으면 새 객체를
     * 만들므로, 같은 서명이면 같은 부품·선들이다.
     */
    public static RefKey shape(com.cburch.logisim.circuit.Circuit c) {
        return builder().refSet(c.getNonWires()).refSet(c.getWires()).build();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RefKey)) {
            return false;
        }
        RefKey k = (RefKey) o;
        if (k.hash != hash || k.parts.length != parts.length) {
            return false;
        }
        for (int i = 0; i < parts.length; i++) {
            if (k.modes[i] != modes[i]) {
                return false;
            }
            if (modes[i] == REF ? k.parts[i] != parts[i] : !Objects.equals(k.parts[i], parts[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    /** 디버깅용. 열쇠로 쓰지 않는다. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("RefKey[");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Object p = parts[i];
            sb.append(modes[i] == REF && p != null ? p.getClass().getSimpleName() + "@ref" : String.valueOf(p));
        }
        return sb.append(']').toString();
    }

    /** 부분을 차례로 더한다. */
    public static final class Builder {
        private final List<Object> parts = new ArrayList<>();
        private final List<Byte> modes = new ArrayList<>();

        private Builder() {
        }

        /** 객체 하나(==). null도 된다. */
        public Builder ref(Object o) {
            parts.add(o);
            modes.add(REF);
            return this;
        }

        /** 차례가 있는 목록: 길이와 원소마다 ==. */
        public Builder refs(List<?> os) {
            value(os.size());
            for (Object o : os) {
                ref(o);
            }
            return this;
        }

        /** 차례 없는 모음: 같은 객체들인가(==). 모음은 지금 모습을 복사해 둔다. */
        public Builder refSet(Collection<?> os) {
            Set<Object> s = Collections.newSetFromMap(new IdentityHashMap<>());
            s.addAll(os);
            parts.add(s); // 두 identity 집합의 equals는 원소를 ==로 가른다
            modes.add(VALUE);
            return this;
        }

        /** 값 하나(equals). */
        public Builder value(Object v) {
            parts.add(v);
            modes.add(VALUE);
            return this;
        }

        public Builder value(int v) {
            return value(Integer.valueOf(v));
        }

        public RefKey build() {
            return new RefKey(parts, modes);
        }
    }
}
