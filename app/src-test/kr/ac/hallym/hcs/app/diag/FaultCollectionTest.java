/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.app.demo.FaultCircuits;
import kr.ac.hallym.hcs.app.record.Recording;
import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 고장 회로 모음(D-06, Q-05): tests/circ/faults/의 회로마다 생성기와 파일이 같고, 정적 검사와 몇 사이클 시뮬레이션
 * 뒤 Messages가 기대한 종류 한 줄뿐이다(원인 한 곳, PLAN.md 4.4). 정상 회로 0건은 StaticCheckTest·DynamicCheckTest가
 * 본다.
 */
class FaultCollectionTest {
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));
    static final File DIR = new File(System.getProperty("hcs.circDir"), "faults");

    @TempDir
    Path tmp;

    /** 회로를 만들어 dir에 저장한다. 파일과 만든 LogisimFile을 돌려준다. */
    static Object[] generate(String name, Path dir) throws Exception {
        Files.createDirectories(dir);
        Loader loader = new Loader(null);
        LogisimFile file = CircuitBuilder.newFile(loader, dir.toFile());
        Library mips = null;
        if (FaultCircuits.usesMips(name)) {
            Path jar = dir.resolve("hcs-mips.jar");
            Files.copy(MIPS_JAR.toPath(), jar, StandardCopyOption.REPLACE_EXISTING);
            mips = loader.loadJarLibrary(jar.toFile(), "kr.ac.hallym.hcs.mips.MipsLibrary");
            file.addLibrary(mips);
        }
        FaultCircuits.build(name, file, mips);
        File out = dir.resolve(name + ".circ").toFile();
        CircuitBuilder.save(file, out);
        return new Object[] {out, file};
    }

    /** 정적 검사 + steps 스텝 시뮬레이션(동적 진단·MIPS 검사·진동) 뒤의 Messages. */
    static List<Diagnostic> messages(LogisimFile file, int steps) {
        Project proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
        return messages(proj, steps);
    }

    static List<Diagnostic> messages(Project proj, int steps) {
        LogisimFile file = proj.getLogisimFile();
        CircuitState root = proj.getCircuitState();
        root.getPropagator().propagate();
        Recording r = new Recording(file.getMainCircuit());
        r.restart(root, 0);
        Diagnostics d = Diagnostics.of(proj);
        d.onRecording(r);
        d.checkOscillation();
        for (int s = 1; s <= steps && !proj.getSimulator().isOscillating(); s++) {
            Propagator p = root.getPropagator();
            p.tick();
            p.propagate();
            r.capture(root, s, false);
            d.onRecording(r);
            d.checkOscillation();
        }
        return new ArrayList<>(d.list());
    }

    @Test
    void everyFaultCircuitGivesItsOneMessage() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Diagnostic.Kind> e : FaultCircuits.EXPECTED.entrySet()) {
            String name = e.getKey();
            Object[] g = generate(name, tmp.resolve(name));
            File fresh = (File) g[0];
            File committed = new File(DIR, name + ".circ");
            if (Boolean.getBoolean("hcs.update") || !committed.exists()) {
                Files.createDirectories(DIR.toPath());
                Files.copy(fresh.toPath(), committed.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            assertEquals(CircNormalizer.normalize(new String(Files.readAllBytes(committed.toPath()),
                    StandardCharsets.UTF_8)), CircNormalizer.normalize(new String(Files.readAllBytes(fresh.toPath()),
                            StandardCharsets.UTF_8)), name + ": committed file matches the generator");
            List<Diagnostic> ds = messages((LogisimFile) g[1], 8);
            if (ds.size() != 1 || ds.get(0).kind != e.getValue()) {
                problems.add(name + " → " + ds);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void mipsMessagesUseTheBodyText() throws Exception {
        Object[] g = generate("mips-unaligned", tmp.resolve("u"));
        List<Diagnostic> ds = messages((LogisimFile) g[1], 4);
        assertEquals(1, ds.size(), ds.toString());
        String text = (String) ds.get(0).args().get(2);
        assertTrue(text.equals("Addr not word-aligned") || text.equals("Addr가 워드 정렬 안 됨"), text);
        assertTrue(ds.get(0).message().contains(text));
    }

    @Test
    void oscillationReplacesTheStaticLoopAndOffersReset() throws Exception {
        Object[] g = generate("dynamic-oscillation", tmp.resolve("o"));
        LogisimFile file = (LogisimFile) g[1];
        Project proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
        Diagnostics d = Diagnostics.of(proj);
        assertEquals(Diagnostic.Kind.COMBINATIONAL_LOOP, d.list().get(0).kind, "before running: the static loop");
        MessagesPanel panel = new MessagesPanel(proj);
        assertTrue(!panel.resetShown());
        List<Diagnostic> ds = messages(proj, 8);
        assertEquals(1, ds.size(), ds.toString());
        assertEquals(Diagnostic.Kind.OSCILLATION, ds.get(0).kind);
        assertTrue(ds.get(0).components.stream().anyMatch(c -> c.getFactory().getName().equals("NAND Gate")),
                ds.get(0).components.toString());
        javax.swing.SwingUtilities.invokeAndWait(() -> { }); // 알림은 GUI 스레드로 간다
        assertTrue(panel.resetShown(), "a Reset button under the oscillation message");
        // Reset: 원조 리셋(기록기가 다음 전파에서 새로 시작). 진동이 멈추면 진단도 걷힌다
        javax.swing.SwingUtilities.invokeAndWait(() -> panel.resetButton().doClick());
        for (int i = 0; i < 100 && proj.getSimulator().isOscillating(); i++) {
            Thread.sleep(20); // 원조 시뮬레이터 스레드가 리셋한다
        }
        d(proj).checkOscillation();
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        assertTrue(!proj.getSimulator().isOscillating());
        assertTrue(d(proj).list().stream().noneMatch(x -> x.kind == Diagnostic.Kind.OSCILLATION), d(proj).list()
                .toString());
    }

    static Diagnostics d(Project proj) {
        return Diagnostics.of(proj);
    }
}
