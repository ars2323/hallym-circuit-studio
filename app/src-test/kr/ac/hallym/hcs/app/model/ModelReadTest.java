/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.record.Recorder;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 모델 스레드가 아닌 곳에서 회로를 읽는 구간(D-143): 원조 회로 읽기 잠금을 쥐는 동안 원조 편집(CircuitMutation)은
 * 기다리고, 구간이 끝나면(예외로 끝나도) 곧 들어온다. 기록기는 파일의 회로 목록을 모델 스레드에서 적어 두고 그 모두를
 * 잠근다(회로를 더하면 목록에 든다).
 */
class ModelReadTest {
    @TempDir
    Path tmp;

    /** 다른 스레드에서 편집을 시작하고, 끝나면 done을 내린다. */
    static Thread edit(CircuitBuilder b, CountDownLatch done, AtomicReference<Throwable> failed) {
        Thread t = new Thread(() -> {
            try {
                b.commit();
            } catch (Throwable x) {
                failed.set(x);
            }
            done.countDown();
        }, "test-editor");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** 읽기 구간을 다른 스레드에서 연다(시뮬레이터 스레드 자리). */
    static Thread reader(Runnable read) {
        Thread t = new Thread(read, "test-reader");
        t.setDaemon(true);
        t.start();
        return t;
    }

    @Test
    void anEditWaitsForTheReadSectionAndThenGoesIn() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit main = file.getMainCircuit();
        CircuitBuilder base = new CircuitBuilder(file, main);
        base.add("Gates", "NOT Gate", 200, 200);
        base.commit();
        int before = main.getNonWires().size();

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Integer> seenAtEnd = new AtomicReference<>();
        reader(() -> ModelRead.run(List.of(main), () -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            int n = 0;
            for (Object o : main.getNonWires()) { // 편집이 끼어들면 ConcurrentModificationException
                n += o == null ? 0 : 1;
            }
            seenAtEnd.set(n);
        }));
        assertTrue(entered.await(10, TimeUnit.SECONDS));

        CircuitBuilder more = new CircuitBuilder(file, main);
        more.add("Gates", "NOT Gate", 400, 200);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        edit(more, done, failed);
        assertFalse(done.await(300, TimeUnit.MILLISECONDS), "the edit went in during the read section");
        assertEquals(before, main.getNonWires().size());

        release.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "the edit never went in");
        assertNull(failed.get());
        assertEquals(before, seenAtEnd.get());
        assertEquals(before + 1, main.getNonWires().size());
    }

    @Test
    void theValueComesBackAndAnExceptionStillReleasesTheLock() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit main = file.getMainCircuit();
        assertEquals("x", ModelRead.call(List.of(main), () -> "x"));
        // 같은 스레드에서 겹쳐 열어도 된다(읽기 잠금은 다시 들어갈 수 있다)
        assertEquals(2, (int) ModelRead.call(List.of(main), () -> ModelRead.call(List.of(main), () -> 2)));
        assertEquals(3, (int) ModelRead.call(Collections.emptyList(), () -> 3));
        assertThrows(IllegalStateException.class, () -> ModelRead.run(List.of(main), () -> {
            throw new IllegalStateException("boom");
        }));

        CircuitBuilder more = new CircuitBuilder(file, main);
        more.add("Gates", "NOT Gate", 400, 200);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        edit(more, done, failed);
        assertTrue(done.await(10, TimeUnit.SECONDS), "the lock stayed held after the exception");
        assertNull(failed.get());
    }

    @Test
    void theRecorderLocksEveryCircuitOfTheFileIncludingNewOnes() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Project proj = new Project(file);
        try {
            Recorder rec = Recorder.of(proj);
            Circuit sub = new Circuit("sub");
            file.addCircuit(sub); // 모델 스레드(여기)의 라이브러리 사건으로 목록에 든다

            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            reader(() -> rec.readModel(() -> {
                entered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertTrue(entered.await(10, TimeUnit.SECONDS));

            CircuitBuilder inSub = new CircuitBuilder(file, sub);
            inSub.add("Gates", "NOT Gate", 200, 200);
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failed = new AtomicReference<>();
            edit(inSub, done, failed);
            assertFalse(done.await(300, TimeUnit.MILLISECONDS), "an edit of the new circuit went in");
            release.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
            assertNull(failed.get());
            assertEquals(1, sub.getNonWires().size());
        } finally {
            proj.getSimulator().shutDown();
        }
    }
}
