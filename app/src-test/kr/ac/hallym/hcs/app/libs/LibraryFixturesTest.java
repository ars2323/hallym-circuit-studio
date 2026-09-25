/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.libs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;
import kr.ac.hallym.hcs.regress.Engine;

/**
 * P-03 예제 파일(tests/circ/libs/)과 원조 호환. 1bit_adder.circ(전가산기)를 "Open Files"와 같은 길(자동 Load
 * Library)로 불러 만든 ripple_carry.circ(4비트, 입력 핀)와 adder_check.circ(상수 11 + 6 + 1)를 만든다. 원조 2.7.1
 * jar가 adder_check.circ를 열어 상대 경로의 라이브러리를 찾고 합 1 0010(18)을 낸다. 파일은
 * {@code ./gradlew :app:test -Phcs.update=true}로 다시 만든다.
 */
class LibraryFixturesTest {
    static final File DIR = new File(new File(System.getProperty("hcs.circDir")), "libs");

    @TempDir
    Path tmp;

    private Supplier<Collection<Project>> saved;
    private final List<Project> open = new ArrayList<>();

    @BeforeEach
    void setUp() {
        saved = OpenFileLibraries.openProjects;
        OpenFileLibraries.openProjects = () -> open;
    }

    @AfterEach
    void tearDown() {
        OpenFileLibraries.openProjects = saved;
    }

    /** 전가산기: s = a ⊕ b ⊕ cin(홀수 패리티), cout = ab + a·cin + b·cin. 핀과 게이트는 선으로 잇는다. */
    static void fullAdder(File dir) throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), dir);
        Circuit c = f.getMainCircuit();
        c.setName("1bit_adder");
        CircuitBuilder b = new CircuitBuilder(f, c);
        b.input("a", 1, 100, 100);
        b.input("b", 1, 100, 160);
        b.input("cin", 1, 100, 220);
        b.output("s", 1, 560, 100);
        b.output("cout", 1, 560, 220);
        Component x = b.add("Gates", "XOR Gate", 400, 100, "inputs", "3", "xor", "odd");
        Component ab = b.add("Gates", "AND Gate", 300, 200, "inputs", "2", "size", "30");
        Component ac = b.add("Gates", "AND Gate", 300, 260, "inputs", "2", "size", "30");
        Component bc = b.add("Gates", "AND Gate", 300, 320, "inputs", "2", "size", "30");
        Component or = b.add("Gates", "OR Gate", 460, 220, "inputs", "3");
        String[][] ins = {{"a", "b", "cin"}, {"a", "b"}, {"a", "cin"}, {"b", "cin"}};
        Component[] gates = {x, ab, ac, bc};
        for (int g = 0; g < gates.length; g++) {
            for (int i = 0; i < ins[g].length; i++) {
                b.tunnelOutward(gates[g], i + 1, ins[g][i]);
            }
        }
        b.tunnelOutward(x, 0, "s");
        b.tunnelOutward(or, 0, "cout");
        b.tunnelOutward(ab, 0, "ab");
        b.tunnelOutward(ac, 0, "ac");
        b.tunnelOutward(bc, 0, "bc");
        b.tunnelOutward(or, 1, "ab");
        b.tunnelOutward(or, 2, "ac");
        b.tunnelOutward(or, 3, "bc");
        b.commit();
        CircuitBuilder.save(f, new File(dir, "1bit_adder.circ"));
    }

    /** 인스턴스에서 핀 이름의 끝 번호(원조 서브회로 포트 순서 = 모양의 포트 오프셋 순서). */
    static int end(Component inst, String pin) {
        Circuit sub = ((com.cburch.logisim.circuit.SubcircuitFactory) inst.getFactory()).getSubcircuit();
        int i = 0;
        for (String name : LibrarySync.ports(sub, com.cburch.logisim.data.Direction.EAST).keySet()) {
            if (name.equals(pin)) {
                return i;
            }
            i++;
        }
        throw new IllegalArgumentException(pin);
    }

    /**
     * 4비트 리플 캐리: fa0~fa3. inputs가 참이면 입력 핀(a0…, b0…, cin), 아니면 상수(A=11, B=6, cin=1)다. 자리올림은
     * 터널 c1~c3으로 잇는다. 합은 inputs면 출력 핀 s0…s3, cout이고, 아니면 5비트 출력 sum 하나(스플리터로 모음)다.
     */
    static void ripple(File dir, String name, boolean inputs) throws Exception {
        File adderFile = new File(dir, "1bit_adder.circ");
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), dir);
        File out = new File(dir, name + ".circ");
        CircuitBuilder.save(f, out); // 저장한 파일이어야 상대 경로가 된다
        Project p = new Project(f);
        p.getSimulator().setIsRunning(false);
        LoadedLibrary lib = OpenFileLibraries.ensureLoaded(p, adderFile).library;
        assertNotNull(lib);
        Circuit main = f.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(f, main);
        List<Component> fas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            fas.add(b.add(lib, "1bit_adder", 400, 120 + 140 * i, "label", "fa" + i));
        }
        b.commit();
        int[] a = {1, 1, 0, 1}; // 11 = 1011 (fa0이 가장 낮은 자리)
        int[] bb = {0, 1, 1, 0}; // 6 = 0110
        for (int i = 0; i < 4; i++) {
            Component fa = fas.get(i);
            Location pa = CircuitBuilder.port(fa, end(fa, "a"));
            Location pb = CircuitBuilder.port(fa, end(fa, "b"));
            Location ps = CircuitBuilder.port(fa, end(fa, "s"));
            if (inputs) {
                b.add("Wiring", "Pin", pa.getX() - 80, pa.getY(), "label", "a" + i);
                b.wire(pa.translate(-80, 0), pa);
                b.add("Wiring", "Pin", pb.getX() - 80, pb.getY(), "label", "b" + i);
                b.wire(pb.translate(-80, 0), pb);
                b.add("Wiring", "Pin", ps.getX() + 80, ps.getY(), "facing", "west", "output", "true",
                        "label", "s" + i);
                b.wire(ps, ps.translate(80, 0));
            } else {
                b.add("Wiring", "Constant", pa.getX() - 40, pa.getY(), "value", "0x" + a[i]);
                b.wire(pa.translate(-40, 0), pa);
                b.add("Wiring", "Constant", pb.getX() - 40, pb.getY(), "value", "0x" + bb[i]);
                b.wire(pb.translate(-40, 0), pb);
                b.tunnelOutward(fa, end(fa, "s"), "s" + i);
            }
            if (i == 0) {
                Location pc = CircuitBuilder.port(fa, end(fa, "cin"));
                if (inputs) {
                    b.add("Wiring", "Pin", pc.getX() - 80, pc.getY(), "label", "cin");
                    b.wire(pc.translate(-80, 0), pc);
                } else {
                    b.add("Wiring", "Constant", pc.getX() - 40, pc.getY(), "value", "0x1");
                    b.wire(pc.translate(-40, 0), pc);
                }
            } else {
                b.tunnelOutward(fa, end(fa, "cin"), "c" + i);
            }
            if (i < 3) {
                b.tunnelOutward(fa, end(fa, "cout"), "c" + (i + 1));
            } else if (inputs) {
                Location pc = CircuitBuilder.port(fa, end(fa, "cout"));
                b.add("Wiring", "Pin", pc.getX() + 80, pc.getY(), "facing", "west", "output", "true",
                        "label", "cout");
                b.wire(pc, pc.translate(80, 0));
            } else {
                b.tunnelOutward(fa, end(fa, "cout"), "c4");
            }
        }
        if (!inputs) {
            Component sp = b.add("Wiring", "Splitter", 700, 300, "facing", "west", "fanout", "5", "incoming", "5");
            for (int i = 0; i < 5; i++) {
                b.tunnelOutward(sp, i + 1, i < 4 ? "s" + i : "c4");
            }
            Location c0 = CircuitBuilder.port(sp, 0);
            b.add("Wiring", "Pin", c0.getX() - 60, c0.getY(), "facing", "east", "output", "true", "width", "5",
                    "label", "sum");
            b.wire(c0.translate(-60, 0), c0);
            // -tty는 halt 핀이 1이 될 때까지 돈다(원조 2.7.1): 한 번 계산하고 멈춘다
            b.add("Wiring", "Constant", 660, 500, "value", "0x1");
            b.add("Wiring", "Pin", 700, 500, "facing", "west", "output", "true", "label", "halt");
            b.wire(Location.create(660, 500), Location.create(700, 500));
        }
        b.commit();
        CircuitBuilder.save(f, out);
    }

    static void build(File dir) throws Exception {
        fullAdder(dir);
        ripple(dir, "ripple_carry", true);
        ripple(dir, "adder_check", false);
    }

    static String normalized(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    void fixturesMatchTheGenerator() throws Exception {
        File dir = tmp.toFile();
        build(dir);
        boolean update = Boolean.getBoolean("hcs.update");
        for (String n : new String[] {"1bit_adder", "ripple_carry", "adder_check"}) {
            File made = new File(dir, n + ".circ");
            File kept = new File(DIR, n + ".circ");
            if (update && (!kept.exists() || !normalized(made).equals(normalized(kept)))) {
                DIR.mkdirs();
                Files.copy(made.toPath(), kept.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            assertEquals(normalized(made), normalized(kept), n + " (update with -Phcs.update=true)");
        }
        String xml = normalized(new File(DIR, "ripple_carry.circ"));
        assertEquals(1, xml.split("<lib desc=\"file#1bit_adder.circ\"", -1).length - 1, "relative library path");
    }

    @Test
    void theOriginalOpensTheLibraryByItsRelativePath() throws Exception {
        Engine original = Engine.current(new File(System.getProperty("hcs.logisimJar")));
        assertEquals("exit=0\n1 0010\n", original.run(DIR, "adder_check"), "11 + 6 + 1 = 18");
        Engine fork = Engine.current(new File(System.getProperty("hcs.forkJar")));
        assertEquals("exit=0\n1 0010\n", fork.run(DIR, "adder_check"));
    }
}
