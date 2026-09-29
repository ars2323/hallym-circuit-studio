/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.sim;

/**
 * N 사이클 실행의 한도(D-123). 원조 엔진({@code Simulator.PropagationManager.requestTick})은 아직 처리하지 못한 틱을
 * 16개까지만 쌓고 그 뒤 요청은 버린다. 그래서 처리 중인 틱이 {@link #MAX_PENDING}개 미만일 때만 다음 틱을 요청하고
 * 엔진의 틱 완료 알림으로 센다. v1 Swing판의 실행기(Swing 타이머)는 화면 코드와 함께 지웠고(N-27, D-163), 엔진의
 * {@code SimSession}이 같은 한도로 돈다(D-145).
 */
public final class CyclePacer {
    /** 엔진의 한도(16)의 절반: 여기까지만 앞서 요청한다. */
    public static final int MAX_PENDING = 8;

    private CyclePacer() {
    }
}
