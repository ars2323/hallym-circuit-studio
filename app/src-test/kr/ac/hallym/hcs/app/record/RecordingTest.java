/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * C-01 기록 엔진(GUI 없음): 스텝마다 모든 넷(서브회로 안까지)의 변화분을 적고, 적은 값은 그때의 실제 값과 같으며,
 * 지난 스텝을 체크포인트에서 원조 엔진으로 다시 돌려 만든 상태가 기록과 넷마다 같다(기록 재생 = 실제 재실행). 입력
 * 바꿈, 뒤 기록 버리기, 보관 상한도 확인한다.
 */
class RecordingTest {
    @TempDir
    Path tmp;

    LogisimFile file;
    Circuit main;
    Circuit inc;
    Component reg;
    Component incInst;
    Component stepPin;
    Component adder;
    CircuitState root;

    /** 8비트 누산기: R ← R + step. 더하기는 서브회로 inc 안의 Adder. */
    void build() throws Exception {
        file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        main = file.getMainCircuit();
        inc = new Circuit("inc");
        file.addCircuit(inc);
        CircuitBuilder sb = new CircuitBuilder(file, inc);
        sb.input("a", 8, 100, 100);
        sb.input("k", 8, 100, 200);
        adder = sb.add("Arithmetic", "Adder", 300, 150, "width", "8");
        sb.tunnel(adder, 0, "a");
        sb.tunnel(adder, 1, "k");
        sb.output("s", 8, 500, 150);
        sb.tunnel(adder, 2, "s");
        sb.commit();

        CircuitBuilder b = new CircuitBuilder(file, main);
        Component clk = b.add("Wiring", "Clock", 100, 400);
        b.tunnel(clk, 0, "clk");
        reg = b.add("Memory", "Register", 400, 200, "width", "8", "label", "R");
        b.tunnel(reg, 0, "q");
        b.tunnel(reg, 1, "next");
        b.tunnel(reg, 2, "clk");
        stepPin = b.add("Wiring", "Pin", 100, 300, "width", "8", "tristate", "false", "label", "step");
        b.tunnel(stepPin, 0, "k");
        incInst = b.addSubcircuit(inc, 600, 400);
        // 서브회로 포트: 입력은 y 순서로 a, k, 출력은 s
        List<Integer> ins = new ArrayList<>();
        int out = -1;
        for (int i = 0; i < incInst.getEnds().size(); i++) {
            if (incInst.getEnd(i).isOutput()) {
                out = i;
            } else {
                ins.add(i);
            }
        }
        ins.sort((x, y) -> incInst.getEnd(x).getLocation().getY() - incInst.getEnd(y).getLocation().getY());
        b.tunnel(incInst, ins.get(0), "q");
        b.tunnel(incInst, ins.get(1), "k");
        b.tunnel(incInst, out, "next");
        b.commit();

        Project proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
        // 시뮬레이터 스레드가 쓰지 않는 상태(테스트가 직접 틱한다)
        root = new CircuitState(proj, main);
        poke(1);
        root.getPropagator().propagate();
    }

    void poke(int v) {
        poke(root, v);
    }

    /** 원조 Poke Tool과 같다: 핀 값을 바꾸고 다시 계산하게 알린다. */
    void poke(CircuitState s, int v) {
        com.cburch.logisim.instance.InstanceState st = s.getInstanceState(stepPin);
        Pin.FACTORY.setValue(st, Value.createKnown(BitWidth.create(8), v));
        st.fireInvalidated();
    }

    Location q() {
        return reg.getEnd(0).getLocation();
    }

    CircuitState sub(CircuitState s) {
        return (CircuitState) s.getData(incInst);
    }

    Location sum() {
        return adder.getEnd(2).getLocation();
    }

    static void step(CircuitState s) {
        Propagator p = s.getPropagator();
        p.tick();
        p.propagate();
    }

    /** 기록된 모든 넷에서 recording과 상태 s가 같은가. 다르면 첫 차이를 돌려준다. */
    static String diff(Recording r, CircuitState s, int step) {
        for (List<Component> path : r.paths()) {
            CircuitState at = s;
            for (Component c : path) {
                at = (CircuitState) at.getData(c);
            }
            for (Location loc : r.points(path)) {
                Value want = r.value(path, loc, step);
                Value got = at.getValue(loc);
                if (!got.equals(want)) {
                    return "step " + step + " " + path + " " + loc + ": recorded " + want + ", rerun " + got;
                }
            }
        }
        return null;
    }

    @Test
    void recordsEveryNetAndMatchesTheLiveValues() throws Exception {
        build();
        Recording r = new Recording(main);
        r.restart(root, 0);
        List<Value> liveQ = new ArrayList<>();
        List<Value> liveSum = new ArrayList<>();
        liveQ.add(root.getValue(q()));
        liveSum.add(sub(root).getValue(sum()));
        for (int s = 1; s <= 40; s++) {
            step(root);
            r.capture(root, s, false);
            liveQ.add(root.getValue(q()));
            liveSum.add(sub(root).getValue(sum()));
        }
        assertEquals(0, r.first());
        assertEquals(40, r.last());
        assertTrue(r.paths().contains(Collections.singletonList(incInst)), "the subcircuit instance is recorded");
        for (int s = 0; s <= 40; s++) {
            assertEquals(liveQ.get(s), r.value(q(), s), "R at step " + s);
            assertEquals(liveSum.get(s), r.value(Collections.singletonList(incInst), sum(), s), "sum at " + s);
        }
        // 한 사이클(스텝 둘)에 R이 1 오른다: 20사이클 뒤 20
        assertEquals(20, r.value(q(), 40).toIntValue());
        // 바뀐 것만 적는다: R은 사이클마다 한 번 바뀐다
        assertEquals(21, r.changes(Collections.<Component>emptyList(), q(), 0, 40).size());
        // 기록에 없는 스텝·자리는 null
        assertNull(r.value(q(), 41));
        assertNull(r.value(Location.create(9990, 9990), 3));
    }

    @Test
    void reconstructIsARealRerun() throws Exception {
        build();
        Recording r = new Recording(main);
        r.restart(root, 0);
        for (int s = 1; s <= 150; s++) {
            step(root);
            r.capture(root, s, false);
        }
        assertEquals(3, r.checkpointCount(), "steps 0, 64, 128");
        for (int s : new int[] {0, 1, 2, 63, 64, 65, 100, 127, 128, 149, 150}) {
            CircuitState again = r.reconstruct(s);
            assertNotNull(again);
            assertNull(diff(r, again, s));
        }
        // 복원은 체크포인트를 바꾸지 않는다: 같은 스텝을 다시 만들어도 같다
        assertNull(diff(r, r.reconstruct(100), 100));
        assertNull(r.reconstruct(151));
    }

    @Test
    void pokedInputIsKeptForReplay() throws Exception {
        build();
        Recording r = new Recording(main);
        r.restart(root, 0);
        for (int s = 1; s <= 10; s++) {
            step(root);
            r.capture(root, s, false);
        }
        // 스텝 10에서 사용자가 step 입력을 3으로 바꿨다(원조 Poke와 같은 경로)
        poke(3);
        root.getPropagator().propagate();
        r.capture(root, 10, true);
        for (int s = 11; s <= 90; s++) {
            step(root);
            r.capture(root, s, false);
        }
        assertEquals(3, r.value(stepPin.getEnd(0).getLocation(), 10).toIntValue(), "the poke is at step 10");
        assertEquals(5, r.value(q(), 10).toIntValue(), "R after five cycles of +1");
        assertEquals(5 + 3, r.value(q(), 12).toIntValue(), "then +3");
        for (int s : new int[] {9, 10, 11, 40, 73, 74, 90}) {
            assertNull(diff(r, r.reconstruct(s), s));
        }
    }

    @Test
    void truncateDropsTheFuture() throws Exception {
        build();
        Recording r = new Recording(main);
        r.restart(root, 0);
        for (int s = 1; s <= 100; s++) {
            step(root);
            r.capture(root, s, false);
        }
        r.truncateAfter(30);
        assertEquals(30, r.last());
        assertNull(r.value(q(), 31));
        assertEquals(1, r.checkpointCount(), "the checkpoint at 64 is gone");
        // 스텝 30의 상태에서 다시 진행: 새 기록이 이어진다
        CircuitState back = r.reconstruct(30);
        poke(back, 2);
        back.getPropagator().propagate();
        r.capture(back, 30, true);
        for (int s = 31; s <= 40; s++) {
            step(back);
            r.capture(back, s, false);
        }
        assertEquals(15 + 2 * 5, r.value(q(), 40).toIntValue());
        assertNull(diff(r, r.reconstruct(35), 35));
        // 앞 스텝으로 캡처하면 그 뒤를 버린다
        r.capture(r.reconstruct(20), 20, true);
        assertEquals(20, r.last());
    }

    @Test
    void oldStepsAreDroppedAtTheCap() throws Exception {
        build();
        Recording r = new Recording(main, 256);
        r.restart(root, 0);
        for (int s = 1; s <= 1000; s++) {
            step(root);
            r.capture(root, s, false);
        }
        assertTrue(r.first() > 0, "old steps dropped");
        assertTrue(r.last() - r.first() <= 256 + 256 / 4 + Recording.CHECKPOINT_EVERY, r.first() + ".." + r.last());
        assertEquals(0, r.first() % Recording.CHECKPOINT_EVERY, "starts at a checkpoint");
        assertNull(r.value(q(), r.first() - 1));
        assertNull(diff(r, r.reconstruct(r.first()), r.first()));
        assertNull(diff(r, r.reconstruct(r.first() + 37), r.first() + 37));
        assertEquals(500, r.value(q(), 1000).toIntValue() + 256 * (500 / 256), "8-bit R wraps: 500 mod 256");
    }

    @Test
    void oldCheckpointsThinOutButPokesStay() throws Exception {
        build();
        Recording r = new Recording(main);
        r.restart(root, 0);
        int pokeAt = 700;
        for (int s = 1; s <= 5000; s++) {
            step(root);
            r.capture(root, s, false);
            if (s == pokeAt) {
                poke(2);
                root.getPropagator().propagate();
                r.capture(root, s, true);
            }
        }
        // 최근 창 안은 64스텝마다, 그 밖은 512스텝 칸마다 하나(+ 맨 앞, 입력 바꾼 700)
        int dense = Recording.DENSE_WINDOW / Recording.CHECKPOINT_EVERY;
        int sparse = (5000 - Recording.DENSE_WINDOW) / Recording.SPARSE_EVERY + 1;
        assertTrue(r.checkpointCount() <= dense + sparse + 2, "checkpoints " + r.checkpointCount());
        // 성긴 곳에서 멀리 떨어진 스텝도, 입력을 바꾼 스텝 앞뒤도 다시 돌린 값이 기록과 같다
        for (int s : new int[] {511, 699, 700, 701, 1023, 1500, 2900, 5000}) {
            assertNull(diff(r, r.reconstruct(s), s));
        }
    }
}
