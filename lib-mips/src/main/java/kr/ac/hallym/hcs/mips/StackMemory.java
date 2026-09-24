/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

/**
 * Stack(PLAN.md 6.2). Data Memory와 포트·동작이 같고 기본 영역만 {@code 0x7FF00000}~{@code 0x7FFFFFFF}다.
 * SPIM의 {@code $sp} 초기값 {@code 0x7FFFEFFC}가 이 안에 있다. 당분간 별도 부품으로 두고 나중에 합친다.
 */
final class StackMemory extends DataMemory {
    StackMemory() {
        super("Stack", Text.of("Stack", "스택"), "Stack", 0x7FF00000, 0x00100000);
    }

    @Override
    String iconText() {
        return "SP";
    }
}
