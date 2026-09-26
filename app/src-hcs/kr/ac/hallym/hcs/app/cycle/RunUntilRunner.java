/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.util.function.Consumer;

import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.record.Recorder;
import kr.ac.hallym.hcs.app.record.Recording;

/**
 * Run Until 실행기(C-04): 한 사이클(틱 두 번)씩 돌리고, 기록이 그 사이클을 적을 때마다(시뮬레이터 스레드) 조건을
 * 본다. 조건을 만나거나 최대 사이클 수에 닿거나 멈추라고 하면 더 틱하지 않는다. 지난 사이클을 보고 있었으면 거기서
 * 진행한다(뒤 기록은 버려진다, C-03).
 */
public final class RunUntilRunner {
    /** 끝난 결과: 멈춘 열과 이유(MET, LIMIT, 또는 멈춤이면 null). */
    public static final class Outcome {
        public final int cycle;
        public final RunUntil.Result result;

        Outcome(int cycle, RunUntil.Result result) {
            this.cycle = cycle;
            this.result = result;
        }
    }

    private final Project proj;
    private final Recorder recorder;
    private final CycleModel model;
    private final RunUntil until;
    private final int startCycle;
    private final Consumer<Outcome> done;
    private volatile boolean stopped;
    private int requestedTo;
    private final Recorder.Listener listener = this::changed;

    private RunUntilRunner(Project proj, CycleModel model, RunUntil until, Consumer<Outcome> done) {
        this.proj = proj;
        this.recorder = Recorder.of(proj);
        this.model = model;
        this.until = until;
        this.done = done;
        this.startCycle = model.cursorCycle();
    }

    /** 돌리기 시작한다. done은 시뮬레이터 스레드나 부른 스레드에서 한 번 불린다. */
    public static RunUntilRunner start(Project proj, CycleModel model, RunUntil until, Consumer<Outcome> done) {
        RunUntilRunner r = new RunUntilRunner(proj, model, until, done);
        r.recorder.addListener(r.listener);
        r.proj.getSimulator().setIsTicking(false);
        r.next();
        return r;
    }

    /** 멈춘다(다음 사이클을 요청하지 않는다). */
    public void stop() {
        if (!stopped) {
            stopped = true;
            finish(new Outcome(model.cursorCycle(), null));
        }
    }

    public boolean isRunning() {
        return !stopped;
    }

    private synchronized void next() {
        // 지금 보는 스텝에서 한 사이클 더(홀수 스텝에 있으면 사이클 끝까지)
        int step = model.recording().cursor();
        int target = CycleModel.stepOf(CycleModel.cycleOf(step) + 1);
        requestedTo = target;
        Simulator sim = proj.getSimulator();
        if (!kr.ac.hallym.hcs.app.sim.TickGuard.canTick(sim)) {
            // D-091: 꺼진 시뮬레이터에 틱을 요청하면 전파 스레드가 돈다. 멈추고 알린다
            kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, kr.ac.hallym.hcs.app.Messages.get("runUntil.simOff"));
            stop();
            return;
        }
        for (int i = step; i < target; i++) {
            sim.tick();
        }
    }

    private void changed(Recording r) {
        if (stopped || r != model.recording()) {
            return;
        }
        int step = r.cursor();
        if (step < requestedTo || step % 2 != 0) {
            return;
        }
        int cycle = CycleModel.cycleOf(step);
        RunUntil.Result res = until.check(model, startCycle, cycle);
        if (res == RunUntil.Result.RUNNING) {
            next();
        } else {
            stopped = true;
            finish(new Outcome(cycle, res));
        }
    }

    private void finish(Outcome o) {
        recorder.removeListener(listener);
        done.accept(o);
    }
}
