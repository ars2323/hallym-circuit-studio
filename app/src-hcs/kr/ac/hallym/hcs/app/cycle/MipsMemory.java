/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.lang.reflect.Method;

/**
 * lib-mips Data Memory·Stack의 상태를 읽는 창(C-06). lib-mips는 JAR 라이브러리로 따로 불려서 포크가 그 클래스를
 * 컴파일 때 알 수 없다. 그래서 상태 객체의 공개 메서드(readWord, pageAddresses, region, growsDown, isDefined,
 * lowestAccess, depthBase)를 이름으로 부른다. 읽기만 한다.
 */
final class MipsMemory {
    private final Object state;
    private final Method readWord;
    private final Method isDefined;
    private final Method pages;
    private final Method region;
    private final Method growsDown;
    private final Method lowest;
    private final Method base;

    private MipsMemory(Object state) throws ReflectiveOperationException {
        this.state = state;
        Class<?> c = state.getClass();
        readWord = open(c.getMethod("readWord", int.class));
        isDefined = open(c.getMethod("isDefined", int.class));
        pages = open(c.getMethod("pageAddresses"));
        region = open(c.getMethod("region"));
        growsDown = open(c.getMethod("growsDown"));
        lowest = open(c.getMethod("lowestAccess"));
        base = open(c.getMethod("depthBase"));
    }

    private static Method open(Method m) {
        m.setAccessible(true); // 공개 메서드지만 클래스가 패키지 전용
        return m;
    }

    /** 부품 상태가 MIPS 메모리 상태이면 그 창, 아니면 null. */
    static MipsMemory of(Object data) {
        if (data == null) {
            return null;
        }
        try {
            return new MipsMemory(data);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private Object call(Method m, Object... args) {
        try {
            return m.invoke(state, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    int readWord(int addr) {
        return (Integer) call(readWord, addr);
    }

    boolean isDefined(int addr) {
        return (Boolean) call(isDefined, addr);
    }

    long[] pageAddresses() {
        return (long[]) call(pages);
    }

    /** {낮은 주소, 높은 주소(제외)}. */
    long[] region() {
        return (long[]) call(region);
    }

    boolean growsDown() {
        return (Boolean) call(growsDown);
    }

    long lowestAccess() {
        return (Long) call(lowest);
    }

    long depthBase() {
        return (Long) call(base);
    }
}
