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
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

/**
 * 모델 스레드가 부품을 잇달아 넣는 동안 원조 시뮬레이터가 빠르게 틱해도 시뮬레이터 스레드가 죽지 않는다(D-143).
 * 기록기는 편집 뒤 첫 틱에 기록을 새로 시작하며 넷 목록을 다시 만들고 상태를 복제하고(Recording.Probe,
 * CircuitState.cloneState), 동적 진단은 스텝마다 부품 집합을 훑는다. 원조 회로 읽기 잠금 없이 하던 때는 편집과 겹쳐
 * ConcurrentModificationException으로 시뮬레이터 스레드가 끝났다(몇십 번의 편집 안에).
 * <p>
 * 넣기만 한다: 부품을 빼는 편집은 원조 자체의 경합(편집 스레드의 CircuitState 청취자가 원조 전파와 함께 쓰는
 * dirtyComponents를 훑는다, 원조 패키지)을 이렇게 빠른 반복에서 거의 늘 드러내므로 이 테스트가 볼 것이 아니다.
 */
class RecorderEditRaceTest {
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

            long end = System.currentTimeMillis() + 3000;
            int edits = 0;
            while (edits < 1000 && System.currentTimeMillis() < end && uncaught.isEmpty()) {
                CircuitBuilder add = new CircuitBuilder(file, main);
                add.add("Gates", "NOT Gate", 1000 + 60 * (edits % 25), 1000 + 60 * (edits / 25));
                add.commit();
                edits++;
            }
            if (!uncaught.isEmpty()) {
                fail("the simulator thread died after " + edits + " edits:\n" + String.join("\n", uncaught));
            }
            // 스레드가 살아 있다: 편집을 멈춘 뒤에도 기록이 늘어난다
            int last = rec.current().last();
            RecorderTest.waitFor(() -> rec.current().last() > last + 4, "steps after the edits");
            assertTrue(edits >= 50, "only " + edits + " edits");
            assertTrue(uncaught.isEmpty(), String.join("\n", uncaught));
            diags.detach();
        } finally {
            sim.setIsTicking(false);
            sim.shutDown();
            Thread.setDefaultUncaughtExceptionHandler(before);
        }
    }
}
