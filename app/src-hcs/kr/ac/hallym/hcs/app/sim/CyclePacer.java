/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.sim;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.SimulatorEvent;
import com.cburch.logisim.circuit.SimulatorListener;
import com.cburch.logisim.proj.Project;

/**
 * N 사이클 실행기(D-123). 원조 엔진({@code Simulator.PropagationManager.requestTick})은 아직 처리하지 못한 틱을
 * 16개까지만 쌓고 그 뒤 요청은 버린다. 그래서 틱을 시간 간격으로만 요청하면(v1.0.3까지: 5ms마다 하나, 창이 없으면
 * 한꺼번에) 회로가 느리거나 PC에 부하가 있을 때 사이클이 빠졌다. 여기서는 처리 중인 틱이 {@link #MAX_PENDING}개
 * 미만일 때만 다음 틱을 요청하고, 엔진의 틱 완료 알림으로 센다. 요청 간격(5ms)은 그대로 두어 화면 갱신 부담은 전과 같다.
 * 엔진은 건드리지 않는다. 시뮬레이션이 꺼지면(발진, Run 끔) 멈춘다.
 */
public final class CyclePacer {
    /** 엔진의 한도(16)의 절반: 여기까지만 앞서 요청한다. */
    public static final int MAX_PENDING = 8;
    /** 요청 간격(ms). 한 사이클이면 기다리지 않는다. */
    static final int INTERVAL = 5;

    private final Project proj;
    private final Simulator sim;
    private final Runnable done;
    private final StatusModel.Run run;
    private final Timer timer;
    private int pending; // GUI 스레드에서만
    private int requested;
    private int completed;
    private boolean finished;
    private final SimulatorListener listener = new SimulatorListener() {
        public void propagationCompleted(SimulatorEvent e) {
        }

        public void tickCompleted(SimulatorEvent e) {
            SwingUtilities.invokeLater(CyclePacer.this::onTickCompleted);
        }

        public void simulatorStateChanged(SimulatorEvent e) {
            SwingUtilities.invokeLater(CyclePacer.this::pump);
        }
    };

    private CyclePacer(Project proj, int cycles, Runnable done) {
        this.proj = proj;
        this.sim = proj.getSimulator();
        this.done = done;
        this.run = new StatusModel.Run(cycles);
        this.timer = new Timer(cycles == 1 ? 0 : INTERVAL, e -> pump());
    }

    /**
     * n 사이클(원조 틱 2n번)을 시작한다. GUI 스레드에서 부른다. done은 끝나거나 멈췄을 때 GUI 스레드에서 한 번 불린다
     * (null이면 부르지 않는다).
     */
    public static CyclePacer start(Project proj, int cycles, Runnable done) {
        CyclePacer p = new CyclePacer(proj, cycles, done);
        if (p.sim == null) {
            p.finish();
            return p;
        }
        p.sim.addSimulatorListener(p.listener);
        p.timer.start();
        p.pump();
        return p;
    }

    /** 요청한 틱 수(테스트용). */
    public int requested() {
        return requested;
    }

    /** 엔진이 끝낸 것으로 센 틱 수(테스트용). */
    public int completed() {
        return completed;
    }

    public boolean isFinished() {
        return finished;
    }

    /** 멈춘다(남은 틱을 요청하지 않는다). */
    public void stop() {
        finish();
    }

    private void onTickCompleted() {
        if (finished) {
            return;
        }
        completed++;
        if (pending > 0) {
            pending--;
        }
        if (run.isDone() && pending == 0) {
            finish();
        }
    }

    /** 타이머마다: 처리 중인 틱이 한도 아래면 하나 더 요청한다(완료 알림은 세기만 한다: 속도는 전과 같이 5ms에 하나). */
    private void pump() {
        if (finished) {
            return;
        }
        if (!TickGuard.canTick(sim)) {
            // 꺼졌다: 엔진은 꺼질 때 쌓인 틱을 버린다. 남은 틱이 있으면 알리고(D-091) 멈춘다
            if (!run.isDone()) {
                TickGuard.tick(proj);
            }
            finish();
            return;
        }
        if (run.isDone()) {
            if (pending == 0) {
                finish();
            }
            return;
        }
        if (pending < MAX_PENDING) {
            boolean[] ok = {true};
            run.step(() -> ok[0] = TickGuard.tick(proj));
            if (!ok[0]) {
                finish();
                return;
            }
            pending++;
            requested++;
        }
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        timer.stop();
        if (sim != null) {
            sim.removeSimulatorListener(listener);
        }
        if (done != null) {
            done.run();
        }
    }
}
