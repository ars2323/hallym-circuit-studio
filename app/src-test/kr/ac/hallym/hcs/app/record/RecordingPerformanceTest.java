/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.record;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

/**
 * C-01 성능(docs/PERFORMANCE.md, CI 로그에 수치를 남기고 넘으면 실패): ref-mips.circ가 끝나지 않는 반복
 * (tests/record/busy-loop.s)을 도는 동안 기록한다. 스텝마다 캡처 시간, 스텝당 메모리, 지난 스텝 복원 시간을 잰다.
 * 기본 보관 상한({@link Recording#DEFAULT_MAX_STEPS})은 여기서 잰 스텝당 메모리로 정했다.
 */
class RecordingPerformanceTest {
    /** 캡처 한 번(스텝 하나) 중앙값 상한. 원조 엔진의 틱+전파보다 작아야 시뮬레이션 속도가 크게 줄지 않는다. */
    static final double CAPTURE_MS = 1.0;
    /** 체크포인트에서 가장 먼 스텝을 복원하는 시간 상한(열 클릭 한 번). */
    static final double RECONSTRUCT_MS = 150;
    /** 성긴 체크포인트 사이 가장 먼 스텝 복원 시간 상한(오래된 사이클로 한 번에 갈 때). */
    static final double OLD_RECONSTRUCT_MS = 600;
    /** 기본 보관 상한까지 기록했을 때 쓸 메모리 상한. */
    static final double BUDGET_MB = 256;

    @TempDir
    Path tmp;

    static long used() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return rt.totalMemory() - rt.freeMemory();
    }

    static double median(List<Double> t) {
        List<Double> s = new ArrayList<>(t);
        Collections.sort(s);
        return s.get(s.size() / 2);
    }

    static double reconstructMs(Recording r, int step) {
        List<Double> t = new ArrayList<>();
        CircuitState again = null;
        for (int i = 0; i < 5; i++) {
            long t0 = System.nanoTime();
            again = r.reconstruct(step);
            t.add((System.nanoTime() - t0) / 1e6);
        }
        assertNull(RecordingTest.diff(r, again, step), "replay equals the recording on ref-mips at " + step);
        return median(t);
    }

    @Test
    void recordingRefMipsStaysWithinLimits() throws Exception {
        LogisimFile file = RecordingTestSupport.openRefMips(tmp);
        RecordingTestSupport.load(file, RecordingTestSupport.program("record/busy-loop.s"));
        Project proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
        int steps = 4000;

        // 기록 없이: 원조 엔진만의 틱+전파 시간(비교용)
        CircuitState plain = new CircuitState(proj, file.getMainCircuit());
        plain.getPropagator().propagate();
        List<Double> tick = new ArrayList<>();
        for (int s = 0; s < steps; s++) {
            long t0 = System.nanoTime();
            Propagator p = plain.getPropagator();
            p.tick();
            p.propagate();
            tick.add((System.nanoTime() - t0) / 1e6);
        }

        // 넷 변화만의 메모리: 체크포인트를 맨 앞 하나만 둔 기록
        CircuitState bare = new CircuitState(proj, file.getMainCircuit());
        bare.getPropagator().propagate();
        long before = used();
        Recording changesOnly = new Recording(file.getMainCircuit(), Recording.DEFAULT_MAX_STEPS,
                Integer.MAX_VALUE);
        changesOnly.restart(bare, 0);
        for (int s = 1; s <= steps; s++) {
            Propagator p = bare.getPropagator();
            p.tick();
            p.propagate();
            changesOnly.capture(bare, s, false);
        }
        double bytesPerStep = Math.max(0, used() - before) / (double) steps;
        assertTrue(changesOnly.changeCount() > steps, "kept alive until measured");
        // 체크포인트 하나의 메모리
        before = used();
        List<CircuitState> clones = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            clones.add(bare.cloneState());
        }
        double bytesPerCheckpoint = Math.max(0, used() - before) / (double) clones.size();
        clones.clear();

        CircuitState root = new CircuitState(proj, file.getMainCircuit());
        root.getPropagator().propagate();
        Recording r = new Recording(file.getMainCircuit());
        r.restart(root, 0);
        List<Double> capture = new ArrayList<>();
        for (int s = 1; s <= steps; s++) {
            Propagator p = root.getPropagator();
            p.tick();
            p.propagate();
            long t0 = System.nanoTime();
            r.capture(root, s, false);
            capture.add((System.nanoTime() - t0) / 1e6);
        }
        double capMs = median(capture.subList(steps / 4, steps));
        double tickMs = median(tick.subList(steps / 4, steps));

        // 최근 창: 체크포인트에서 가장 먼 스텝(간격 - 1)을 복원
        int far = steps - steps % Recording.CHECKPOINT_EVERY - 1;
        double recMs = reconstructMs(r, far);
        // 오래된 곳: 성긴 체크포인트에서 가장 먼 스텝
        int old = 2 * Recording.SPARSE_EVERY - 1;
        double oldMs = reconstructMs(r, old);
        int cps = Recording.DENSE_WINDOW / Recording.CHECKPOINT_EVERY
                + Recording.DEFAULT_MAX_STEPS / Recording.SPARSE_EVERY;
        double atCapMb = (bytesPerStep * Recording.DEFAULT_MAX_STEPS + bytesPerCheckpoint * cps) / 1e6;

        String log = String.format("[perf] recording ref-mips busy-loop: nets %d, changes %d over %d steps (%.1f/step); "
                + "tick+propagate %.3f ms, capture %.3f ms (limit %.1f); changes %.0f bytes/step, checkpoint %.0f KB; "
                + "%d steps with %d checkpoints = %.0f MB (limit %.0f); reconstruct %d steps %.1f ms (limit %.0f), "
                + "%d steps %.1f ms (limit %.0f)",
                r.netCount(), r.changeCount(), steps, r.changeCount() / (double) steps, tickMs, capMs, CAPTURE_MS,
                bytesPerStep, bytesPerCheckpoint / 1e3, Recording.DEFAULT_MAX_STEPS, cps, atCapMb, BUDGET_MB,
                Recording.CHECKPOINT_EVERY - 1, recMs, RECONSTRUCT_MS, Recording.SPARSE_EVERY - 1, oldMs,
                OLD_RECONSTRUCT_MS);
        System.out.println(log);
        assertTrue(r.changeCount() > steps, "the program really runs: " + log);
        assertTrue(capMs <= CAPTURE_MS, log);
        assertTrue(recMs <= RECONSTRUCT_MS, log);
        assertTrue(oldMs <= OLD_RECONSTRUCT_MS, log);
        assertTrue(atCapMb <= BUDGET_MB, log);
    }
}
