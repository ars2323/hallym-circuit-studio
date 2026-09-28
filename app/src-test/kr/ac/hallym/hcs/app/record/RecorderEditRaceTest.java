/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.record;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.diag.DiagnosticSet;
import kr.ac.hallym.hcs.regress.CircuitBuilder;
import kr.ac.hallym.hcs.regress.LogisimRace;

/**
 * 모델 스레드가 부품을 잇달아 넣는 동안 원조 시뮬레이터가 빠르게 틱해도 시뮬레이터 스레드가 죽지 않고 기록이 이어진다
 * (D-143). 기록기는 편집 뒤 첫 틱에 기록을 새로 시작하며 넷 목록을 다시 만들고 상태를 복제하고(Recording.Probe,
 * CircuitState.cloneState), 동적 진단은 스텝마다 부품 집합을 훑는다. 원조 회로 읽기 잠금 없이 하던 때는 편집과 겹쳐
 * ConcurrentModificationException으로 시뮬레이터 스레드가 끝났다(몇십 번의 편집 안에).
 * <p>
 * 편집마다 잠깐 쉰다: 원조 잠금(ReentrantReadWriteLock, 비공정)은 쓰기 쪽이 새치기하므로 쉼 없이 편집하면 기록기가
 * 읽기 잠금을 기다리기만 해서 편집 3초 동안 틱이 몇 번밖에 돌지 않는다. 쉬면 편집 수백 번이 틱·캡처 수백 번과
 * 번갈아 겹친다.
 * <p>
 * 원조 자체의 경합(고치지 않음, D-143, {@link LogisimRace}): 편집 스레드의 원조 CircuitState 청취자가 넣은 부품을 dirtyComponents에 더하는
 * 동안 원조 전파(시뮬레이터 스레드)가 같은 집합을 배열로 옮기거나 비우면 예외가 난다. 원조 Simulator는 그 예외를 잡아
 * 찍고 시뮬레이션을 끈다(스레드는 살아 있고, 학생은 Simulation Enabled를 다시 켠다). 그러면 틱이 멈춰 기록도 멈추므로,
 * 이 테스트는 그때 찍힌 예외가 원조 전파 안의 것이고 우리 코드를 지나지 않았는지 보고 시뮬레이션을 다시 켠다. 부품을
 * 빼는 편집은 이 원조 경합을 거의 늘 드러내므로 넣기만 한다.
 */
class RecorderEditRaceTest {
    /** 편집 사이에 쉬는 시간: 기다리던 기록기가 읽기 잠금을 얻고 틱이 돈다. */
    static final long PAUSE_NANOS = 100_000;
    /** 실패 문구의 스레드 덤프: 스레드마다 스택 프레임 수, 글 전체 글자 수. */
    static final int DUMP_FRAMES = 40;
    static final int DUMP_CHARS = 400_000;

    @TempDir
    Path tmp;

    @Test
    void editsWhileTheSimulatorTicksDoNotKillItsThread() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit main = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, main);
        Component clk = b.add("Wiring", "Clock", 100, 200);
        b.tunnel(clk, 0, "clk");
        Component ctr = b.add("Memory", "Counter", 400, 200, "width", "8", "label", "PC");
        b.tunnel(ctr, 2, "clk");
        Component reg = b.add("Memory", "Register", 400, 400, "width", "8", "label", "R");
        b.tunnel(reg, 2, "clk");
        b.commit();

        List<String> uncaught = new CopyOnWriteArrayList<>();
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, x) -> {
            StringWriter w = new StringWriter();
            x.printStackTrace(new PrintWriter(w));
            uncaught.add(t.getName() + ": " + w);
        });
        // 원조 Simulator는 전파 중의 예외를 잡아 System.err에 찍기만 한다: 그 사본을 모은다
        LogisimRace race = LogisimRace.watch();
        Project proj = new Project(file);
        Simulator sim = proj.getSimulator();
        try {
            Recorder rec = Recorder.of(proj);
            // 동적 진단도 시뮬레이터 스레드에서 돈다(기록기는 이것을 약하게 잡으므로 끝까지 붙든다)
            DiagnosticSet diags = new DiagnosticSet(proj, () -> { });
            Recorder.requestReset(proj);
            RecorderTest.waitFor(() -> rec.current() != null && !rec.current().isEmpty(), "the first step");
            sim.setTickFrequency(4096);
            sim.setIsTicking(true);

            // 원조 전파가 제 경합으로 시뮬레이션을 껐으면(우리 코드를 지나지 않았는지 확인하고) 학생이 Simulation
            // Enabled를 다시 켜듯 켠다
            int[] stops = {0};
            Runnable resume = () -> {
                if (race.stoppedByLogisim(sim)) {
                    stops[0]++;
                    sim.setIsRunning(true);
                }
            };
            long end = System.currentTimeMillis() + 3000;
            int edits = 0;
            while (edits < 1000 && System.currentTimeMillis() < end && uncaught.isEmpty()) {
                CircuitBuilder add = new CircuitBuilder(file, main);
                add.add("Gates", "NOT Gate", 1000 + 60 * (edits % 25), 1000 + 60 * (edits / 25));
                add.commit();
                edits++;
                resume.run();
                LockSupport.parkNanos(PAUSE_NANOS);
            }
            if (!uncaught.isEmpty()) {
                fail(uncaught.size() + " threads died after " + edits + " edits, the first:\n" + uncaught.get(0));
            }
            // 스레드가 살아 있다: 편집을 멈춘 뒤에도 기록이 늘어난다
            int last = rec.current().last();
            waitOrDump(() -> rec.current().last() > last + 4, resume,
                    "steps after the edits (" + edits + " edits, Logisim stopped the simulation " + stops[0]
                            + " times)",
                    sim, rec);
            assertTrue(edits >= 50, "only " + edits + " edits");
            assertTrue(uncaught.isEmpty(), () -> uncaught.size() + " threads died, the first:\n" + uncaught.get(0));
            System.out.println(edits + " edits; Logisim's propagator stopped the simulation " + stops[0] + " times");
            diags.detach();
        } finally {
            sim.setIsTicking(false);
            sim.shutDown();
            race.close();
            Thread.setDefaultUncaughtExceptionHandler(before);
        }
    }

    /**
     * ok가 참이 될 때까지(10초) 기다린다. 기다리는 동안 between을 부른다. 못 기다리면 시뮬레이터·기록 상태와 모든 스레드의
     * 덤프를 실패 문구에 담는다(CI에서 멈춘 까닭을 보려고).
     */
    static void waitOrDump(BooleanSupplier ok, Runnable between, String what, Simulator sim, Recorder rec)
            throws Exception {
        long end = System.currentTimeMillis() + 10_000;
        while (!ok.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                Recording r = rec.current();
                throw new AssertionError("timed out: " + what + "\nsimulator running=" + sim.isRunning()
                        + " ticking=" + sim.isTicking() + " exceptionEncountered=" + sim.isExceptionEncountered()
                        + " hz=" + sim.getTickFrequency() + "; recording "
                        + (r == null ? "null" : "first=" + r.first() + " last=" + r.last() + " cursor=" + r.cursor())
                        + "\n" + threadDump());
            }
            between.run();
            Thread.sleep(10);
        }
    }

    /**
     * 모든 스레드: 상태, 기다리는 잠금과 그 임자, 스택({@link #DUMP_FRAMES}개까지), 쥔 모니터·잠금. 교착이면 그 수도.
     * 앞 테스트들이 남긴 시뮬레이터 스레드가 수백 개라 스레드마다 스택을 줄이고, 글 전체도 {@link #DUMP_CHARS}자까지만.
     */
    static String threadDump() {
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        StringBuilder b = new StringBuilder("--- thread dump ---\n");
        for (ThreadInfo t : mx.dumpAllThreads(true, true)) {
            if (b.length() > DUMP_CHARS) {
                b.append("… (more threads)\n");
                break;
            }
            b.append('"').append(t.getThreadName()).append("\" ").append(t.getThreadState());
            if (t.getLockName() != null) {
                b.append(" on ").append(t.getLockName());
            }
            if (t.getLockOwnerName() != null) {
                b.append(" owned by \"").append(t.getLockOwnerName()).append('"');
            }
            b.append('\n');
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(st.length, DUMP_FRAMES); i++) {
                b.append("\tat ").append(st[i]).append('\n');
                for (MonitorInfo m : t.getLockedMonitors()) {
                    if (m.getLockedStackDepth() == i) {
                        b.append("\t- locked ").append(m).append('\n');
                    }
                }
            }
            for (LockInfo l : t.getLockedSynchronizers()) {
                b.append("\t- holds ").append(l).append('\n');
            }
            b.append('\n');
        }
        long[] dead = mx.findDeadlockedThreads();
        b.append("deadlocked threads: ").append(dead == null ? 0 : dead.length).append('\n');
        return b.toString();
    }
}
