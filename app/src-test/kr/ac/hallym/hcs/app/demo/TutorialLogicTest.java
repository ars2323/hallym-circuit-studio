/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.app.ext.CircExtensionIO;
import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.splitter.SplitterEdits;
import kr.ac.hallym.hcs.regress.CircEquivalence;
import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 논리설계 튜토리얼 예제(N-18, D-161): 생성기와 커밋 파일이 같고, 선이 의도한 포트만 잇고(합선·우연한 연결 없음),
 * half_adder 자리의 선 끝이 화면의 [건너뛰기]가 놓는 자리(tutorial/actions.json)의 포트와 같다.
 */
class TutorialLogicTest {
    static final File TUTORIAL = new File(System.getProperty("hcs.testsDir"), "tutorial");
    static final File COMMITTED = new File(TUTORIAL, "tutorial-logic.circ");
    static final File ACTIONS = new File(TUTORIAL.getParentFile().getParentFile(),
            "electron/src/renderer/app/tutorial/actions.json");

    @TempDir
    Path tmp;

    TutorialLogic t;
    LogisimFile file;

    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));

    /** dir에 jar를 두고 만들어 저장한다(원조처럼 jar를 회로 옆에서 찾는다: Radix Probe). */
    File generate(Path dir) throws Exception {
        Files.createDirectories(dir);
        Path jar = dir.resolve("hcs-mips.jar");
        Files.copy(MIPS_JAR.toPath(), jar, StandardCopyOption.REPLACE_EXISTING);
        Loader loader = new Loader(null);
        file = CircuitBuilder.newFile(loader, dir.toFile());
        com.cburch.logisim.tools.Library lib = loader.loadJarLibrary(jar.toFile(), "kr.ac.hallym.hcs.mips.MipsLibrary");
        file.addLibrary(lib);
        t = TutorialLogic.build(file, lib);
        File out = dir.resolve("tutorial-logic.circ").toFile();
        CircuitBuilder.save(file, out);
        CircExtensions.afterSave(file, out);
        return out;
    }

    @Test
    void committedFileMatchesTheGenerator() throws Exception {
        File fresh = generate(tmp.resolve("gen"));
        if (Boolean.getBoolean("hcs.update")) {
            Files.createDirectories(TUTORIAL.toPath());
            Files.copy(fresh.toPath(), COMMITTED.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        File committed = tmp.resolve("gen/committed.circ").toFile(); // jar 옆에 둔다
        Files.copy(COMMITTED.toPath(), committed.toPath());
        assertEquals(CircNormalizer.normalize(new String(Files.readAllBytes(committed.toPath()), StandardCharsets.UTF_8)),
                CircNormalizer.normalize(new String(Files.readAllBytes(fresh.toPath()), StandardCharsets.UTF_8)));
        assertEquals(java.util.Collections.<String>emptyList(), CircEquivalence.compare(committed, fresh));
        assertEquals(CircExtensionIO.read(committed), CircExtensionIO.read(fresh), "memos and arm names");
    }

    private static Set<String> ports(Netlist nl, Component c, int end) {
        Set<String> ret = new HashSet<>();
        for (Netlist.PortRef p : nl.netOf(c, end).ports()) {
            ret.add(p.component.getFactory().getName() + "#" + p.end);
        }
        return ret;
    }

    private static Set<String> set(String... s) {
        return new HashSet<>(java.util.Arrays.asList(s));
    }

    /** 넷마다 의도한 포트만 있다. A·B·Y와 half_adder 자리의 선은 아직 아무것에도 닿지 않는다. */
    @Test
    void wiresConnectExactlyWhatWasIntended() throws Exception {
        generate(tmp.resolve("gen"));
        Circuit main = file.getMainCircuit();
        Netlist nl = Netlist.of(main);
        for (Netlist.Net n : nl.nets()) {
            assertTrue(n.drivers().size() <= 1, "no short: " + n);
        }
        assertEquals(set("Pin#0"), ports(nl, t.a, 0), "A waits for the AND gate (L4)");
        assertEquals(set("Pin#0"), ports(nl, t.b, 0));
        assertEquals(set("Pin#0"), ports(nl, t.y, 0));
        assertEquals(set("Register#0", "Adder#0", "Radix Probe#0", "Splitter#0"), ports(nl, t.register, 0),
                "count → +1, the Radix Probe, the Splitter");
        assertEquals(set("Adder#2", "Register#1"), ports(nl, t.adder, 2));
        assertEquals(set("Adder#1", "Constant#0"), ports(nl, t.adder, 1));
        assertEquals(set("Register#2"), ports(nl, t.register, 2), "the clock input is empty (L11, L12)");
        assertEquals(set("Clock#0"), ports(nl, t.clock, 0));
        for (int k = 1; k <= 4; k++) {
            assertEquals(set("Splitter#" + k, "LED#0"), ports(nl, t.splitter, k), "arm " + k);
        }
        assertEquals(List.of("q0", "q1", "q2", "q3"), SplitterEdits.names(file, main, t.splitter.getLocation()));
        assertEquals(2, t.halfAdder.getNonWires().stream().filter(c -> c.getFactory().getName().endsWith("Gate")).count());
    }

    /**
     * 논리설계 및 실험 모드(v2 추가 3 A-08)에서 숨는 MIPS 전용 부품(Instruction Memory·Data Memory·Stack·Console)을 쓰지
     * 않는다. Hallym MIPS 라이브러리에서는 모드와 상관없이 보이는 Radix Probe 하나만 쓴다.
     */
    @Test
    void noMipsOnlyPart() throws Exception {
        generate(tmp.resolve("gen"));
        Set<String> used = new HashSet<>();
        for (Circuit c : file.getCircuits()) {
            for (Component x : c.getNonWires()) {
                used.add(x.getFactory().getName());
            }
        }
        for (String mipsOnly : new String[] {"Instruction Memory", "Data Memory", "Stack", "Console"}) {
            assertTrue(!used.contains(mipsOnly), mipsOnly + " in " + used);
        }
        assertTrue(used.contains("Radix Probe"), used.toString());
    }

    /** L8: half_adder를 HALF_ADDER_AT에 놓으면 두 입력이 선 끝에 닿는다. 화면의 자리(actions.json)도 같다. */
    @Test
    void theHalfAdderSpotIsWhereTheScreenPlacesIt() throws Exception {
        generate(tmp.resolve("gen"));
        Circuit main = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, main);
        Component inst = b.addSubcircuit(t.halfAdder, TutorialLogic.HALF_ADDER_AT.getX(),
                TutorialLogic.HALF_ADDER_AT.getY());
        b.commit();
        Netlist nl = Netlist.of(main);
        for (String port : new String[] {"a", "b"}) {
            assertEquals(2, ports(nl, inst, DemoDatapath.index(inst, port)).size(), port + " meets its wire's pin");
        }
        String actions = new String(Files.readAllBytes(ACTIONS.toPath()), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"halfAdder\"[^}]*\"loc\":\\s*\\[(\\d+),\\s*(\\d+)\\]").matcher(actions);
        assertTrue(m.find(), "halfAdder.loc in " + ACTIONS);
        assertEquals(TutorialLogic.HALF_ADDER_AT.getX(), Integer.parseInt(m.group(1)));
        assertEquals(TutorialLogic.HALF_ADDER_AT.getY(), Integer.parseInt(m.group(2)));
    }
}
