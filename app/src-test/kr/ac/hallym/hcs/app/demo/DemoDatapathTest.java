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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.app.ext.CircExtensionIO;
import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.splitter.SplitterEdits;
import kr.ac.hallym.hcs.regress.CircEquivalence;
import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;
import kr.ac.hallym.hcs.regress.Engine;

/** 스크린샷용 데모 회로: 생성기와 커밋 파일이 같고, 선 연결이 의도대로(합선 없음)이며, 원조 2.7.1에서 돈다. */
class DemoDatapathTest {
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));
    static final File ORIGINAL = new File(System.getProperty("hcs.logisimJar"));
    static final File COMMITTED = new File(System.getProperty("hcs.circDir"), "demo-datapath.circ");

    @TempDir
    Path tmp;

    DemoDatapath demo;
    LogisimFile file;

    /** dir에 jar를 두고 만들어 저장한다(원조처럼 jar를 회로 옆에서 찾는다). */
    File generate(Path dir) throws Exception {
        Files.createDirectories(dir);
        Path jar = dir.resolve("hcs-mips.jar");
        Files.copy(MIPS_JAR.toPath(), jar, StandardCopyOption.REPLACE_EXISTING);
        Loader loader = new Loader(null);
        file = CircuitBuilder.newFile(loader, dir.toFile());
        Library lib = loader.loadJarLibrary(jar.toFile(), "kr.ac.hallym.hcs.mips.MipsLibrary");
        file.addLibrary(lib);
        demo = DemoDatapath.build(file, lib);
        File out = dir.resolve("demo-datapath.circ").toFile();
        CircuitBuilder.save(file, out);
        CircExtensions.afterSave(file, out);
        return out;
    }

    @Test
    void committedFileMatchesTheGenerator() throws Exception {
        File fresh = generate(tmp.resolve("gen"));
        if (Boolean.getBoolean("hcs.update")) {
            Files.copy(fresh.toPath(), COMMITTED.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        File committed = tmp.resolve("gen/committed.circ").toFile(); // jar 옆에 둔다
        Files.copy(COMMITTED.toPath(), committed.toPath());
        assertEquals(CircNormalizer.normalize(new String(Files.readAllBytes(committed.toPath()), StandardCharsets.UTF_8)),
                CircNormalizer.normalize(new String(Files.readAllBytes(fresh.toPath()), StandardCharsets.UTF_8)));
        assertEquals(java.util.Collections.<String>emptyList(), CircEquivalence.compare(committed, fresh));
        assertEquals(CircExtensionIO.read(committed), CircExtensionIO.read(fresh), "splitter arm names");
    }

    private static Set<String> ports(Netlist nl, Component c, int end) {
        Set<String> ret = new HashSet<>();
        for (Netlist.PortRef p : nl.netOf(c, end).ports()) {
            if (!p.component.getFactory().getName().equals("Tunnel")) {
                ret.add(p.component.getFactory().getName() + "#" + p.end);
            }
        }
        return ret;
    }

    private static Set<String> set(String... s) {
        return new HashSet<>(java.util.Arrays.asList(s));
    }

    /** PLAN 부록 A.4: 넷마다 의도한 포트만 있다(선이 남의 포트를 지나거나 겹쳐 합쳐지지 않았다). */
    @Test
    void wiresConnectExactlyWhatWasIntended() throws Exception {
        generate(tmp.resolve("gen"));
        Circuit main = file.getMainCircuit();
        Netlist nl = Netlist.of(main);
        for (Netlist.Net n : nl.nets()) {
            assertTrue(n.drivers().size() <= 1, "no short: " + n);
        }
        assertEquals(set("Register#0", "Instruction Memory#0", "Adder#0", "Comparator#0"),
                ports(nl, demo.pc, 0), "PC → IMem, +4, halt");
        assertEquals(set("Adder#2", "Register#1"), ports(nl, demo.adder, 2), "+4 → PC.D");
        assertEquals(set("Instruction Memory#1", "Splitter#0"), ports(nl, demo.imem, 1));
        String rf = demo.regfile.getFactory().getName();
        String al = demo.alu.getFactory().getName();
        int rr1 = DemoDatapath.index(demo.regfile, "RR1");
        int rr2 = DemoDatapath.index(demo.regfile, "RR2");
        int wr = DemoDatapath.index(demo.regfile, "WR");
        assertEquals(set("Splitter#2", rf + "#" + rr1), ports(nl, demo.splitter, 2), "rs → RR1");
        assertEquals(set("Splitter#3", rf + "#" + rr2), ports(nl, demo.splitter, 3), "rt → RR2");
        assertEquals(set("Splitter#4", rf + "#" + wr), ports(nl, demo.splitter, 4), "rd → WR");
        int rd1 = DemoDatapath.index(demo.regfile, "RD1");
        int rd2 = DemoDatapath.index(demo.regfile, "RD2");
        assertEquals(set(rf + "#" + rd1, al + "#" + DemoDatapath.index(demo.alu, "A")), ports(nl, demo.regfile, rd1));
        assertEquals(set(rf + "#" + rd2, al + "#" + DemoDatapath.index(demo.alu, "B"), "Data Memory#1"),
                ports(nl, demo.regfile, rd2), "RD2 → ALU B and WriteData");
        int res = DemoDatapath.index(demo.alu, "Result");
        assertEquals(set(al + "#" + res, "Data Memory#0", "Multiplexer#0"), ports(nl, demo.alu, res));
        assertEquals(set("Data Memory#5", "Multiplexer#1"), ports(nl, demo.dmem, 5));
        assertEquals(set("Multiplexer#3", rf + "#" + DemoDatapath.index(demo.regfile, "WD")), ports(nl, demo.mux, 3),
                "MemtoReg → WD");
        assertEquals(6, SplitterEdits.names(file, main, demo.splitter.getLocation()).size(), "R-type arm names");
        List<Component> tunnels = new java.util.ArrayList<>();
        for (Component c : main.getNonWires()) {
            if (c.getFactory().getName().equals("Tunnel")) {
                tunnels.add(c);
            }
        }
        Set<String> names = new HashSet<>();
        for (Component t : tunnels) {
            names.add(t.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.LABEL));
        }
        Set<String> allowed = new HashSet<>(DemoDatapath.CONTROL);
        allowed.add("pc"); // 테스트용 halt
        assertTrue(allowed.containsAll(names), "tunnels only for control lines: " + names);
    }

    /** 포크 엔진(원조와 같은 엔진)에서 클럭마다 PC가 4씩 늘고, 원조 2.7.1 jar가 halt까지 돈다. */
    @Test
    void runsInTheForkAndTheOriginal() throws Exception {
        File out = generate(tmp.resolve("gen"));
        Project proj = new Project(file);
        CircuitState st = proj.getCircuitState();
        st.getPropagator().propagate();
        for (int cycle = 1; cycle <= 3; cycle++) {
            for (int t = 0; t < 2; t++) {
                st.getPropagator().tick();
                st.getPropagator().propagate();
            }
            Value pc = st.getValue(demo.pc.getEnds().get(0).getLocation());
            assertEquals(4 * cycle, pc.toIntValue(), "PC after cycle " + cycle);
        }
        String run = Engine.current(ORIGINAL).run(out.getParentFile(), "demo-datapath");
        assertTrue(run.startsWith("exit=0"), run);
    }
}
