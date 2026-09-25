/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.edit.CircuitEdits;
import kr.ac.hallym.hcs.app.probe.QuickProbe;
import kr.ac.hallym.hcs.app.splitter.SplitterEdits;
import kr.ac.hallym.hcs.app.splitter.SplitterSpec;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * W-05: 새 선·부품을 자동으로 두는 모든 기능이 검사기 하나({@link WireGuard})를 거치고, 부록 A.4 규칙·넷 불변·뜻한
 * 점 밖 접촉 금지를 지킨다. 기능마다 통과하는 경우와 막히는 경우를 고정 기대값으로 본다.
 */
class WireGuardTest {
    @TempDir
    Path tmp;

    LogisimFile file;
    Circuit c;
    CircuitBuilder b;
    Project proj;

    void fresh() throws Exception {
        file = CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
        c = file.getMainCircuit();
        b = new CircuitBuilder(file, c);
    }

    void open() {
        b.commit();
        proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
    }

    /** 검사 뒤 회로가 그대로인가(시험 실행을 되돌렸는가). */
    List<String> check(CircuitMutation m, List<Location> allowed) {
        Set<String> geo = SafeMoveTest.geometry(c);
        List<String> p = WireGuard.problems(proj, c, m, allowed);
        assertEquals(geo, SafeMoveTest.geometry(c), "the trial is undone");
        return p;
    }

    /** 통과하면 실행하고, 되돌리기 한 번에 원래대로 돌아오는지까지 본다. */
    void runAndUndo(CircuitMutation m, List<Location> allowed) {
        Set<String> geo = SafeMoveTest.geometry(c);
        assertTrue(WireGuard.run(proj, c, m, allowed, () -> "test"));
        assertFalse(geo.equals(SafeMoveTest.geometry(c)), "the edit happened");
        proj.undoAction();
        assertEquals(geo, SafeMoveTest.geometry(c), "one undo restores everything");
    }

    static boolean any(List<String> problems, String prefix) {
        return problems.stream().anyMatch(s -> s.startsWith(prefix));
    }

    // ---- 붙이기 ----

    @Test
    void attachPinConstantProbeTunnelPass() throws Exception {
        fresh();
        Component g = b.add("Gates", "NOT Gate", 300, 200);
        open();
        for (CircuitEdits.Attach what : CircuitEdits.Attach.values()) {
            CircuitMutation m = CircuitEdits.attach(file, c, g, 1, what, "A");
            assertEquals(List.of(), check(m, List.of(CircuitBuilder.port(g, 1))), what.name());
        }
        runAndUndo(CircuitEdits.attach(file, c, g, 1, CircuitEdits.Attach.PIN, "A"),
                List.of(CircuitBuilder.port(g, 1)));
    }

    @Test
    void attachedTunnelMayJoinTheSameNamedSignal() throws Exception {
        fresh();
        Component g = b.add("Gates", "NOT Gate", 300, 200);
        b.add("Wiring", "Pin", 100, 300, "label", "src");
        b.add("Wiring", "Tunnel", 150, 300, "label", "RegWrite", "facing", "west");
        b.wire(Location.create(100, 300), Location.create(150, 300));
        open();
        // 이름으로 잇는 것은 학생이 뜻한 연결이다
        assertEquals(List.of(), check(CircuitEdits.attach(file, c, g, 1, CircuitEdits.Attach.TUNNEL, "RegWrite"),
                List.of(CircuitBuilder.port(g, 1))));
    }

    // ---- 넷 불변(규칙 3) ----

    @Test
    void anEditThatJoinsTwoOtherNetsIsRefused() throws Exception {
        fresh();
        b.add("Wiring", "Pin", 100, 100, "label", "A");
        b.add("Wiring", "Pin", 200, 100, "facing", "west", "output", "true", "label", "Y");
        b.wire(Location.create(100, 100), Location.create(140, 100));
        b.wire(Location.create(160, 100), Location.create(200, 100));
        open();
        CircuitMutation m = new CircuitMutation(c);
        m.add(Wire.create(Location.create(140, 100), Location.create(160, 100)));
        List<String> p = check(m, List.of());
        assertTrue(p.contains("changes other connections"), p.toString());
        assertFalse(WireGuard.run(proj, c, m, List.of(), () -> "test"));
    }

    // ---- 선을 터널로 ----

    @Test
    void wireToTunnelsKeepsTheNet() throws Exception {
        fresh();
        b.add("Wiring", "Pin", 100, 100, "label", "A");
        b.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "label", "Y");
        b.wire(Location.create(100, 100), Location.create(300, 100));
        open();
        Wire w = c.getWires().iterator().next();
        List<Location> ends = List.of(w.getEnd0(), w.getEnd1());
        assertEquals(List.of(), check(CircuitEdits.wireToTunnels(file, c, w, "sig", 1), ends));
        runAndUndo(CircuitEdits.wireToTunnels(file, c, w, "sig", 1), ends);
    }

    // ---- 게이트 바꾸기 ----

    @Test
    void swapGateKeepsTheConnections() throws Exception {
        fresh();
        Component g = b.add("Gates", "AND Gate", 300, 200);
        Location in0 = CircuitBuilder.port(g, 1);
        Location in1 = CircuitBuilder.port(g, 2);
        b.add("Wiring", "Pin", 100, in0.getY(), "label", "a");
        b.wire(Location.create(100, in0.getY()), in0);
        b.add("Wiring", "Pin", 100, in1.getY(), "label", "b");
        b.wire(Location.create(100, in1.getY()), in1);
        b.add("Wiring", "Pin", 400, 200, "facing", "west", "output", "true", "label", "y");
        b.wire(CircuitBuilder.port(g, 0), Location.create(400, 200));
        open();
        for (String kind : CircuitEdits.swappableGates()) {
            if (kind.equals("AND Gate")) {
                continue;
            }
            CircuitMutation m = CircuitEdits.swapGate(c, g, CircuitEdits.builtin(file, "Gates", kind));
            assertEquals(List.of(), check(m, WireGuard.ends(g)), kind);
        }
        runAndUndo(CircuitEdits.swapGate(c, g, CircuitEdits.builtin(file, "Gates", "XOR Gate")), WireGuard.ends(g));
    }

    // ---- 빠른 프로브 ----

    @Test
    void quickProbeBranchesOffTheWire() throws Exception {
        fresh();
        b.add("Wiring", "Pin", 100, 200, "label", "A");
        b.add("Wiring", "Pin", 400, 200, "facing", "west", "output", "true", "label", "Y");
        b.wire(Location.create(100, 200), Location.create(400, 200));
        open();
        Wire w = c.getWires().iterator().next();
        QuickProbe.Placement pl = QuickProbe.find(file, c, w, Location.create(250, 200));
        assertNotNull(pl);
        CircuitMutation m = QuickProbe.place(file, c, pl, "16", "A");
        assertEquals(List.of(), check(m, List.of(pl.p)));
        runAndUndo(QuickProbe.place(file, c, pl, "16", "A"), List.of(pl.p));
    }

    // ---- 비트 나누기·합치기 ----

    /** x에 세로 8비트 선 하나. */
    void verticalAt(int x) {
        b.add("Wiring", "Pin", x, 40, "facing", "south", "width", "8", "label", "v");
        b.add("Wiring", "Pin", x, 400, "facing", "north", "width", "8", "output", "true", "label", "w");
        b.wire(Location.create(x, 40), Location.create(x, 400));
    }

    @Test
    void combinedSplitterMustNotLandItsArmsOnAnotherWire() throws Exception {
        fresh();
        verticalAt(300);
        open();
        SplitterSpec spec = SplitterSpec.combine(List.of(4, 4), null);
        // 서쪽을 보는 스플리터의 팔 끝은 묶인 끝에서 x-20: 묶인 끝 (320, 200)이면 팔 끝 (300, 210), (300, 220)이 세로선 위
        List<String> p = check(SplitterEdits.create(file, c, Location.create(320, 200), Direction.WEST, spec),
                List.of());
        assertTrue(any(p, "a port of Splitter touches another net"), p.toString());
        assertEquals(List.of(), check(SplitterEdits.create(file, c, Location.create(500, 200), Direction.WEST, spec),
                List.of()));
        runAndUndo(SplitterEdits.create(file, c, Location.create(500, 200), Direction.WEST, spec), List.of());
    }

    @Test
    void extractOnABusConnectsOnlyAtTheClickedPoint() throws Exception {
        fresh();
        b.add("Wiring", "Pin", 100, 200, "width", "8", "label", "bus");
        b.add("Wiring", "Pin", 400, 200, "facing", "west", "width", "8", "output", "true", "label", "out");
        b.wire(Location.create(100, 200), Location.create(400, 200));
        verticalAt(220);
        open();
        Location at = Location.create(160, 200);
        // 묶인 끝은 누른 점(뜻한 점)이라 괜찮고, 팔 끝(x+20 = 180)은 어디에도 닿지 않는다
        assertEquals(List.of(), check(SplitterEdits.create(file, c, at, Direction.EAST,
                SplitterSpec.extract(8, 3)), List.of(at)));
        // 세로선 바로 왼쪽을 누르면 팔 끝(x+20 = 220)이 세로선 위에 온다
        Location near = Location.create(200, 200);
        List<String> p = check(SplitterEdits.create(file, c, near, Direction.EAST, SplitterSpec.extract(8, 3)),
                List.of(near));
        assertTrue(any(p, "a port of Splitter touches another net"), p.toString());
    }

    // ---- 복제 ----

    @Test
    void duplicateIsDroppedWhereItTouchesNothing() throws Exception {
        fresh();
        Component g = b.add("Gates", "NOT Gate", 300, 200);
        Location in = CircuitBuilder.port(g, 1);
        // 원조 복제 자리(+10, +10)의 사본 입력 포트가 이 가로선 위에 온다
        b.add("Wiring", "Pin", 100, in.getY() + 10, "label", "p");
        b.add("Wiring", "Pin", 500, in.getY() + 10, "facing", "west", "output", "true", "label", "q");
        b.wire(Location.create(100, in.getY() + 10), Location.create(500, in.getY() + 10));
        open();
        Selection sel = new Canvas(proj).getSelection();
        sel.add(g);
        assertTrue(WireGuard.touches(c, List.of(g), 10, 10), "the original offset would touch the wire");
        Set<String> geo = SafeMoveTest.geometry(c);
        runDuplicate(sel);
        List<Component> copies = new ArrayList<>();
        for (Component x : c.getNonWires()) {
            if (x != g && x.getFactory() == g.getFactory()) {
                copies.add(x);
            }
        }
        copies.addAll(sel.getFloatingComponents());
        assertEquals(1, copies.size(), copies.toString());
        Component copy = copies.get(0);
        int dx = copy.getLocation().getX() - g.getLocation().getX();
        int dy = copy.getLocation().getY() - g.getLocation().getY();
        List<Component> others = new ArrayList<>(c.getNonWires());
        others.remove(copy);
        assertEquals(List.of(20, 20), List.of(dx, dy), "nearest free spot in the original spiral order");
        proj.undoAction();
        assertEquals(geo, SafeMoveTest.geometry(c), "one undo removes the copy and the nudge");
    }

    /** 앱과 같은 경로: 원조 복제 뒤 닿으면 옮긴다. Project.getSelection은 창이 없으면 null이므로 선택을 넘긴다. */
    void runDuplicate(Selection sel) {
        SafeDuplicate.run(proj, sel);
    }

    @Test
    void freeOffsetIsDeterministic() throws Exception {
        fresh();
        Component g = b.add("Gates", "NOT Gate", 300, 200);
        Location in = CircuitBuilder.port(g, 1);
        b.wire(Location.create(100, in.getY() + 10), Location.create(500, in.getY() + 10));
        open();
        int[] first = SafeDuplicate.freeOffset(c, List.of(g));
        for (int i = 0; i < 50; i++) {
            int[] again = SafeDuplicate.freeOffset(c, List.of(g));
            assertEquals(first[0], again[0]);
            assertEquals(first[1], again[1]);
        }
    }

    // ---- 모든 기능이 이 검사기를 거치는가 ----

    /**
     * 선·부품을 자동으로 두는 변경(붙이기, 게이트 바꾸기, 선을 터널로, 빠른 프로브, 스플리터 만들기·고치기)을 부르는
     * 파일은 모두 WireGuard를 거치고, 원조 복제는 SafeDuplicate 안에서만 부른다.
     */
    @Test
    void everyWireCreatingFeatureGoesThroughTheGuard() throws IOException {
        Path src = Paths.get(System.getProperty("user.dir")).resolve("src-hcs");
        if (!Files.isDirectory(src)) {
            src = Paths.get(System.getProperty("user.dir")).resolve("app/src-hcs");
        }
        String[] creators = {"CircuitEdits.attach(", "CircuitEdits.swapGate(", "CircuitEdits.wireToTunnels(",
            "QuickProbe.place(", "SplitterEdits.create(", "SplitterEdits.change("};
        List<Path> files;
        try (Stream<Path> s = Files.walk(src)) {
            files = s.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
        List<String> unguarded = new ArrayList<>();
        for (Path p : files) {
            String text = new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8);
            for (String k : creators) {
                if (text.contains(k) && !text.contains("WireGuard")) {
                    unguarded.add(p.getFileName() + " " + k);
                }
            }
            if (text.contains("SelectionActions.duplicate(") && !p.endsWith("SafeDuplicate.java")) {
                unguarded.add(p.getFileName() + " duplicate");
            }
        }
        assertEquals(Collections.emptyList(), unguarded);
    }
}
