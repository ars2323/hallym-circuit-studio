/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

/**
 * Stack(PLAN.md 6.2). Data Memory와 포트·동작이 같고, 맨 위 워드 {@code top}(0x7FFFFFFC)에서 낮은 주소 쪽으로
 * 자란다. 기본 한계는 1MB라 영역은 {@code 0x7FF00000}~{@code 0x7FFFFFFF}이고, SPIM의 {@code $sp} 초기값
 * {@code 0x7FFFEFFC}가 이 안에 있다. 한계 바로 아래 주소에 접근하면 "스택 사용량이 한계를 넘었습니다"를 알린다.
 */
final class StackMemory extends DataMemory {
    StackMemory() {
        super("Stack", Text.name("Stack"), "Stack", true, 0x7FFFFFFC, 0x00100000);
    }

    @Override
    String iconText() {
        return "SP";
    }
}
