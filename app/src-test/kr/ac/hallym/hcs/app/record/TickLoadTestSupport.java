/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.record;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.cburch.logisim.circuit.SimulatorEvent;
import com.cburch.logisim.circuit.SimulatorListener;

/** 부하 테스트 도구(D-123): 엔진을 느리게 하는 청취자, 틱 세기, CPU를 바쁘게 하는 스레드. */
public final class TickLoadTestSupport {
    private TickLoadTestSupport() {
    }

    /** 틱이 끝날 때마다 ms만큼 엔진 스레드를 잡아 둔다. */
    public static SimulatorListener slow(int ms) {
        return new SimulatorListener() {
            public void propagationCompleted(SimulatorEvent e) {
            }

            public void tickCompleted(SimulatorEvent e) {
                try {
                    Thread.sleep(ms);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

            public void simulatorStateChanged(SimulatorEvent e) {
            }
        };
    }

    /** 엔진이 끝낸 틱 수(틱 완료 알림). */
    public static SimulatorListener counting(java.util.concurrent.atomic.AtomicInteger n) {
        return new SimulatorListener() {
            public void propagationCompleted(SimulatorEvent e) {
            }

            public void tickCompleted(SimulatorEvent e) {
                n.incrementAndGet();
            }

            public void simulatorStateChanged(SimulatorEvent e) {
            }
        };
    }

    /** CPU를 바쁘게 하는 스레드들. 끝나면 close. */
    public static final class Burner implements AutoCloseable {
        final AtomicBoolean on = new AtomicBoolean(true);
        final List<Thread> threads = new ArrayList<>();

        public Burner(int n) {
            for (int i = 0; i < n; i++) {
                Thread t = new Thread(() -> {
                    long x = 0;
                    while (on.get()) {
                        x += System.nanoTime() % 7;
                    }
                    if (x == 42) {
                        System.out.print("");
                    }
                }, "burner-" + i);
                t.setDaemon(true);
                t.start();
                threads.add(t);
            }
        }

        public void close() throws InterruptedException {
            on.set(false);
            for (Thread t : threads) {
                t.join();
            }
        }
    }

}
