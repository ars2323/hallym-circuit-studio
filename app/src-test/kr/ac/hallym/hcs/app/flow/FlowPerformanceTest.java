/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.flow;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;

/**
 * P-07 성능(CI 로그에 수치를 남기고 넘으면 실패): ref-mips.circ 전체와 demo-datapath.circ에서 경로 계산 50ms 이하.
 * 중앙값으로 잰다(처음 몇 번은 JIT 예열로 버린다). 그리기는 화면(electron/)의 몫이다(v1 Swing 그리기는 N-27에서 지웠다).
 */
// 벽시계 시간을 잰다: 상수 identity hash 실행(testConstantIdentityHash)에서는 뺀다(D-129)
@Tag("timing")
class FlowPerformanceTest {
    static final double PATH_MS = 50;

    @TempDir
    Path tmp;

    Circuit open(File circ) throws Exception {
        Path dir = Files.createTempDirectory(tmp, "p");
        Files.copy(new File(System.getProperty("hcs.mipsJar")).toPath(), dir.resolve("hcs-mips.jar"),
                StandardCopyOption.REPLACE_EXISTING);
        Path to = dir.resolve(circ.getName());
        Files.copy(circ.toPath(), to);
        return new Loader(null).openLogisimFile(to.toFile()).getMainCircuit();
    }

    static double median(int warmup, int runs, Runnable r) {
        for (int i = 0; i < warmup; i++) {
            r.run();
        }
        List<Double> t = new ArrayList<>();
        for (int i = 0; i < runs; i++) {
            long s = System.nanoTime();
            r.run();
            t.add((System.nanoTime() - s) / 1e6);
        }
        Collections.sort(t);
        return t.get(t.size() / 2);
    }

    /** 가장 멀리 닿는 시작(레지스터 전부를 넘어 끝까지): 부품마다 재 보고 가장 느린 것. */
    static double worstPath(Circuit c, List<String> log, String name) {
        SignalFlowPath.Options o = new SignalFlowPath.Options();
        o.throughRegisters = true;
        List<Component> starts = new ArrayList<>();
        for (Component x : c.getNonWires()) {
            String f = x.getFactory().getName();
            if (f.equals("Register") || f.equals("Pin") || f.equals("Clock")) {
                starts.add(x);
            }
        }
        starts.sort(java.util.Comparator.<Component>comparingInt(x -> x.getLocation().getY())
                .thenComparingInt(x -> x.getLocation().getX()));
        double worst = 0;
        String at = "";
        for (Component x : starts.subList(0, Math.min(12, starts.size()))) {
            double ms = median(3, 7, () -> SignalFlowPath.fromComponent(c, x, -1, o));
            if (ms > worst) {
                worst = ms;
                at = x.getFactory().getName() + "@" + x.getLocation();
            }
        }
        log.add(String.format("[perf] %s path (worst of %d starts, %s): %.2f ms", name, Math.min(12, starts.size()),
                at, worst));
        return worst;
    }

    @Test
    void pathIsFastEnough() throws Exception {
        List<String> log = new ArrayList<>();
        Circuit ref = open(new File(System.getProperty("hcs.refMips")));
        Circuit demo = open(new File(System.getProperty("hcs.circDir"), "demo-datapath.circ"));
        double refPath = worstPath(ref, log, "ref-mips");
        double demoPath = worstPath(demo, log, "demo-datapath");
        log.forEach(System.out::println);
        assertTrue(refPath <= PATH_MS && demoPath <= PATH_MS, "path: " + log);
    }
}
