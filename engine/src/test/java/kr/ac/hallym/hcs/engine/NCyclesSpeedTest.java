/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.sim.CyclePacer;

/**
 * N Cycles의 속도(N-07, D-145, v2 지시 3-5 "N Cycles 1000이 v1보다 느리지 않음"). ref-mips에서 1000 사이클을 세 번
 * 돌려 가장 빠른 시간을 재고, 틱이 빠지지 않았는지(정확히 1000), 처리 중 틱이 8개를 넘지 않았는지(D-123), 학생이 고른
 * 틱 주파수(1 Hz)가 그대로 알려지는지 본다. 잰 값은 {@code build/engine-measure.txt}에 한 줄 더한다.
 */
@Tag("timing")
class NCyclesSpeedTest {
    /**
     * v1(Swing v1.0.3의 CyclePacer)이 ref-mips 1000 사이클에 쓴 시간(ms, D-145에 잰 값): 1 Hz(처음 값) 25,026ms,
     * 1 kHz·4 kHz 10,139ms(5ms 타이머가 한도). 화면을 그리지 않은 채로 잰 v1이라 실제 v1보다 빠른 쪽이다. 엔진은 v1이
     * 가장 빨랐을 때보다 빨라야 한다.
     */
    static final long V1_MS = 10_000;

    @TempDir
    Path tmp;

    InProcess e;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
    }

    @AfterEach
    void stop() {
        e.close();
    }

    @Test
    void thousandCyclesOfRefMipsAreExactAndFasterThanV1() throws Exception {
        File ref = Fixtures.copyWithSiblings(Fixtures.REF_MIPS, tmp);
        JsonObject r = e.client.callObject("file.open", params("path", ref.getPath()));
        String fileId = r.get("fileId").getAsString();
        String main = r.get("main").getAsString();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 3; round++) {
            int mark = e.client.mark();
            e.client.call("sim.reset", params("fileId", fileId));
            e.client.awaitNotificationAfter(mark, "sim.state", s -> s.get("cycle").getAsLong() == 0);
            int m2 = e.client.mark();
            long t0 = System.nanoTime();
            e.client.call("sim.cycles", params("fileId", fileId, "n", 1000));
            JsonObject done = e.client.awaitNotificationAfter(m2, "sim.state",
                    s -> s.get("cycle").getAsLong() >= 1000);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            best = Math.min(best, ms);
            assertEquals(1000, done.get("cycle").getAsLong(), "no tick is lost (D-123)");
            assertEquals(1.0, done.get("hz").getAsDouble(), "sim.state tells the student's clock speed");
            int maxPending = e.onEngine(() -> e.engine.sim(fileId).maxPendingTicks());
            assertTrue(maxPending <= CyclePacer.MAX_PENDING, "never more than 8 pending ticks: " + maxPending);
        }
        JsonObject st = e.client.callObject("sim.state", params("fileId", fileId));
        assertEquals(1.0, st.get("hz").getAsDouble(), "the clock speed is the student's again");
        String line = "nCycles1000RefMipsMs=" + best + System.lineSeparator();
        String out = System.getProperty("hcs.measureFile");
        if (out != null) {
            Files.writeString(Path.of(out), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        System.err.println("[measure] " + line.trim());
        assertTrue(best < V1_MS, "N Cycles 1000 on ref-mips took " + best + " ms; v1 took " + V1_MS + " ms");
    }
}
