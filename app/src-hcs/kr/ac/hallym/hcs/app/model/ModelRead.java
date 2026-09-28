/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutator;
import com.cburch.logisim.circuit.CircuitTransaction;

/**
 * 모델 스레드(v2 엔진 스레드, Swing 이벤트 스레드)가 아닌 곳, 곧 원조 시뮬레이터 스레드의 청취자에서 원조 회로 모델을
 * 읽을 때(D-143). 편집은 모델 스레드가 하고, 기록 엔진의 캡처와 동적 진단·진동 검사는 시뮬레이터 스레드에서 회로의
 * 부품 집합({@code Circuit.getNonWires()}의 HashSet)·선·속성을 훑는다. 그대로 두면 편집과 겹쳐
 * ConcurrentModificationException이 나고 시뮬레이터 스레드가 죽는다.
 * <p>
 * 원조는 부품·선·속성을 바꿀 때 늘 {@link CircuitTransaction}으로 바꾸는 회로의 쓰기 잠금을 쥔다(원조
 * {@code CircuitLocker}: 회로마다 ReentrantReadWriteLock, 잠금 없이 바꾸면 {@code checkForWritePermission}이
 * 막는다, 여러 회로는 일련번호 차례로 잡는다). 여기서는 같은 잠금의 읽기 쪽을 쥐는 읽기 전용 트랜잭션 안에서 body를
 * 돈다: 도는 동안 그 회로들은 바뀌지 않고, 편집은 body가 끝날 때까지 기다린다. 새 잠금을 만들지 않으므로 원조 코드와
 * 엔진의 편집 경로는 그대로다. body 안에서 모델을 바꾸면 안 된다(읽기 잠금을 쥔 채 쓰기 잠금을 기다리게 된다).
 */
public final class ModelRead {
    private ModelRead() {
    }

    /** circuits가 바뀌지 않는 동안 body를 돈다(부르는 스레드에서). */
    public static void run(Collection<Circuit> circuits, Runnable body) {
        call(circuits, () -> {
            body.run();
            return null;
        });
    }

    /** circuits가 바뀌지 않는 동안 body를 돌고 그 값을 돌려준다. body의 예외는 그대로 던진다(잠금은 푼다). */
    public static <T> T call(Collection<Circuit> circuits, Supplier<T> body) {
        if (circuits.isEmpty()) {
            return body.get();
        }
        ReadOnly<T> xn = new ReadOnly<>(circuits, body);
        xn.execute();
        return xn.result;
    }

    private static final class ReadOnly<T> extends CircuitTransaction {
        private final Collection<Circuit> circuits;
        private final Supplier<T> body;
        T result;

        ReadOnly(Collection<Circuit> circuits, Supplier<T> body) {
            this.circuits = circuits;
            this.body = body;
        }

        @Override
        protected Map<Circuit, Integer> getAccessedCircuits() {
            Map<Circuit, Integer> m = new IdentityHashMap<>();
            for (Circuit c : circuits) {
                m.put(c, READ_ONLY); // 원조는 이 상수를 ==로 가른다
            }
            return m;
        }

        @Override
        protected void run(CircuitMutator mutator) {
            result = body.get();
        }
    }
}
