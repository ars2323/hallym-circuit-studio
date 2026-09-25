/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.flow;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;

/**
 * P-07 성능(CI 로그에 수치를 남기고 넘으면 실패): ref-mips.circ 전체와 demo-datapath.circ에서 경로 계산 50ms 이하,
 * 프레임 그리기 4ms 이하. 중앙값으로 잰다(처음 몇 번은 JIT 예열로 버린다).
 */
class FlowPerformanceTest {
    static final double PATH_MS = 50;
    static final double FRAME_MS = 4;
    /** 앞단이 퍼지는 동안(처음 약 2초)은 그때그때 그린다: 30fps 한 장 33ms 안에서 넉넉히. */
    static final double FRONT_MS = 8;

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

    static double frame(Circuit c, Supplier<SignalFlowPath> path, List<String> log, String name) {
        return frame(c, path, log, name, false);
    }

    /** front가 참이면 앞단이 절반쯤 퍼진 장면, 아니면 연속 흐름. */
    static double frame(Circuit c, Supplier<SignalFlowPath> path, List<String> log, String name, boolean front) {
        SignalFlowPath p = path.get();
        BufferedImage img = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB); // 캔버스 바탕처럼
        double t = front ? p.total / 2 : p.total + 123; // 연속 흐름: 경로 전체를 그린다
        java.awt.Rectangle whole = FlowPainter.bounds(p, c);
        // 캔버스처럼: 다시 그리는 영역은 경로 상자 ∩ 보이는 창(1600×900)
        java.awt.Rectangle box = whole == null ? null
                : whole.intersection(new java.awt.Rectangle(whole.x, whole.y, 1600, 900));
        double ms = median(10, 30, () -> {
            Graphics2D g = img.createGraphics();
            if (box != null) {
                g.setClip(box); // 캔버스처럼 경로 상자만 다시 그린다
            }
            FlowPainter.paint(g, p, c, t, 1.0, false, null);
            g.dispose();
        });
        log.add(String.format("[perf] %s " + (front ? "front " : "") + "frame (%d segments): %.3f ms (%d jumps, %d endpoints)", name, p.segments.size(), ms, p.jumps.size(), p.endpoints.size()));
        return ms;
    }

    @Test
    void pathAndFrameAreFastEnough() throws Exception {
        List<String> log = new ArrayList<>();
        Circuit ref = open(new File(System.getProperty("hcs.refMips")));
        Circuit demo = open(new File(System.getProperty("hcs.circDir"), "demo-datapath.circ"));
        double refPath = worstPath(ref, log, "ref-mips");
        double demoPath = worstPath(demo, log, "demo-datapath");
        Component pc = null;
        for (Component x : demo.getNonWires()) {
            if ("PC".equals(x.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.LABEL))) {
                pc = x;
            }
        }
        final Component start = pc;
        SignalFlowPath.Options through = new SignalFlowPath.Options();
        through.throughRegisters = true;
        double demoFrame = frame(demo, () -> SignalFlowPath.fromComponent(demo, start, 0, through), log,
                "demo-datapath");
        Component refStart = null;
        for (Component x : ref.getNonWires()) {
            if (x.getFactory().getName().equals("Register")) {
                refStart = x;
                break;
            }
        }
        final Component rs = refStart;
        double refFrame = frame(ref, () -> SignalFlowPath.fromComponent(ref, rs, -1, through), log, "ref-mips");
        double demoFront = frame(demo, () -> SignalFlowPath.fromComponent(demo, start, 0, through), log,
                "demo-datapath", true);
        double refFront = frame(ref, () -> SignalFlowPath.fromComponent(ref, rs, -1, through), log, "ref-mips", true);
        log.forEach(System.out::println);
        assertTrue(refPath <= PATH_MS && demoPath <= PATH_MS, "path: " + log);
        assertTrue(demoFrame <= FRAME_MS && refFrame <= FRAME_MS, "frame: " + log);
        assertTrue(demoFront <= FRONT_MS && refFront <= FRONT_MS, "front frame: " + log);
    }
}
