/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.sim;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.SimulatorEvent;
import com.cburch.logisim.circuit.SimulatorListener;

/**
 * 엔진 스레드의 모델 편집·Poke를 원조 시뮬레이터의 전파와 겹치지 않게 한다(D-143). 원조 {@code CircuitState}의 회로
 * 청취자와 Poke는 부르는 스레드(엔진 스레드)에서 더러운 부품·점 집합(원조 {@code SmallSet}, 스레드 안전하지 않음)을
 * 고치고, 원조 전파(시뮬레이터 스레드)는 잠금 없이 같은 집합을 옮기고 비운다. 둘이 겹치면 편집이 그 청취자 안의 예외로
 * 끝나거나(ConcurrentModificationException·NullPointerException) 원조가 전파 중의 예외를 잡아 시뮬레이션을 끈다.
 * 원조는 고치지 않는다(CLAUDE.md 2절). 대신 편집하기 전에 전파를 한 번 요청하고, 시뮬레이터 스레드가 그 뒤의 전파 완료
 * 알림(전파 밖)에 오면 거기 세워 둔 채 편집하고 풀어 준다. 원조의 공개 API(요청, 시뮬레이터 청취자)만 쓴다. 세워 둔
 * 동안 밀린 틱은 원조가 쌓아 두었다가(16개 한도) 풀린 뒤 처리한다.
 */
public final class SimGate implements SimulatorListener {
    /** 시뮬레이터 스레드가 알림에 오기를 기다리는 한도(ms). 넘으면(멈춘 시뮬레이터 등) 세우지 않고 편집한다. */
    static final long ARRIVE_MS = 5_000;
    /** 세운 시뮬레이터 스레드가 풀리기를 기다리는 한도(ms): 엔진 스레드에 무슨 일이 있어도 영영 서 있지 않게. */
    static final long HOLD_MS = 60_000;

    private final Simulator sim;
    private final long arriveMs;
    private volatile Stop pending;
    /** 지금 세우고 있는 스레드(다시 부르면 그냥 돈다). */
    private volatile Thread holder;

    /** 한 번의 세우기: 시뮬레이터 스레드가 왔다, 엔진 스레드가 풀었다. */
    private static final class Stop {
        final CountDownLatch arrived = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);
    }

    SimGate(Simulator sim) {
        this(sim, ARRIVE_MS);
    }

    SimGate(Simulator sim, long arriveMs) {
        this.sim = sim;
        this.arriveMs = arriveMs;
        sim.addSimulatorListener(this);
    }

    void close() {
        sim.removeSimulatorListener(this);
    }

    /**
     * 시뮬레이터 스레드를 전파 밖에 세워 둔 채 body를 부르는 스레드에서 돌고 그 값을 돌려준다. 시뮬레이터 스레드가
     * 한도({@link #ARRIVE_MS}) 안에 오지 않으면 세우지 않고 돈다. 이미 세운 스레드가 다시 부르면(body 안의 편집) 그냥
     * 돈다. 시뮬레이터 스레드에서 부르면 안 된다(자기를 기다린다).
     */
    <T, X extends Exception> T hold(Body<T, X> body) throws X {
        if (holder == Thread.currentThread()) {
            return body.run();
        }
        Stop s = new Stop();
        pending = s;
        holder = Thread.currentThread();
        try {
            sim.requestPropagate();
            try {
                s.arrived.await(arriveMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
            }
            return body.run();
        } finally {
            holder = null;
            pending = null;
            s.released.countDown();
        }
    }

    /** 세운 동안 할 일. */
    public interface Body<T, X extends Exception> {
        T run() throws X;
    }

    // 전파 완료·틱 완료 알림은 원조 시뮬레이터 스레드가 전파 밖에서 부른다(상태 알림은 아무 스레드나 부르므로 세우지 않는다)
    @Override
    public void propagationCompleted(SimulatorEvent e) {
        park();
    }

    @Override
    public void tickCompleted(SimulatorEvent e) {
        park();
    }

    @Override
    public void simulatorStateChanged(SimulatorEvent e) {
    }

    private void park() {
        Stop s = pending;
        if (s == null || s.arrived.getCount() == 0) {
            return;
        }
        s.arrived.countDown();
        try {
            s.released.await(HOLD_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
        }
    }
}
