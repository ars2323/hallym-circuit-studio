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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.app.cycle.RegisterFile;
import kr.ac.hallym.hcs.app.ext.CircExtensionIO;
import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.regress.CircEquivalence;
import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 컴퓨터구조 튜토리얼 예제(N-18, D-161): 생성기와 커밋 파일이 같고, 합선이 없고, 오타 터널 하나(RegWirte)만 짝이 없으며,
 * 튜토리얼 프로그램은 Hallym MIPS v2.4.0이 data.s에서 내보낸 골든 그대로다(이름만 tutorial). 동작(불러오기, 오타 고치기,
 * exit까지 실행)은 엔진 쪽 TutorialExamplesTest가 본다.
 */
class TutorialMipsTest {
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));
    static final File TESTS = new File(System.getProperty("hcs.testsDir"));
    static final File TUTORIAL = new File(TESTS, "tutorial");
    static final File COMMITTED = new File(TUTORIAL, "tutorial-mips.circ");

    @TempDir
    Path tmp;

    TutorialMips t;
    LogisimFile file;

    File generate(Path dir) throws Exception {
        Files.createDirectories(dir);
        Path jar = dir.resolve("hcs-mips.jar");
        Files.copy(MIPS_JAR.toPath(), jar, StandardCopyOption.REPLACE_EXISTING);
        Loader loader = new Loader(null);
        file = CircuitBuilder.newFile(loader, dir.toFile());
        Library lib = loader.loadJarLibrary(jar.toFile(), "kr.ac.hallym.hcs.mips.MipsLibrary");
        file.addLibrary(lib);
        t = TutorialMips.build(file, lib);
        File out = dir.resolve("tutorial-mips.circ").toFile();
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
        assertEquals(CircExtensionIO.read(committed), CircExtensionIO.read(fresh), "arm names, register file mark");
    }

    /** 합선 없음, 짝 없는 터널은 오타 하나, regfile은 레지스터 파일로 표시, 레지스터 이름은 $at~$ra. */
    @Test
    void oneTypoAndNoShorts() throws Exception {
        generate(tmp.resolve("gen"));
        for (Component x : new Component[] {t.controlInst, t.regfileInst, t.aluInst}) {
            System.out.println("DBG " + x.getFactory().getName() + " " + x.getLocation() + " " + x.getBounds() + " " + DemoDatapath.ports(x));
        }
        for (Circuit c : file.getCircuits()) {
            Netlist nl = Netlist.of(c);
            for (Netlist.Net n : nl.nets()) {
                assertTrue(n.drivers().size() <= 1, c.getName() + ": no short: " + n);
            }
        }
        Set<String> lone = new HashSet<>();
        Circuit main = file.getMainCircuit();
        Set<String> names = new HashSet<>();
        Set<String> twice = new HashSet<>();
        for (Component x : main.getNonWires()) {
            if (x.getFactory().getName().equals("Tunnel")) {
                String l = x.getAttributeSet().getValue(StdAttr.LABEL);
                if (!names.add(l)) {
                    twice.add(l);
                }
            }
        }
        for (String n : names) {
            if (!twice.contains(n)) {
                lone.add(n);
            }
        }
        assertTrue(lone.contains(TutorialMips.TYPO), "the typo is alone: " + lone);
        assertEquals(t.regfile, RegisterFile.marked(file));
        for (int i = 1; i < 32; i++) {
            assertEquals(TutorialMips.REG_NAMES[i], t.regs[i].getAttributeSet().getValue(StdAttr.LABEL));
            assertEquals(i, RegisterFile.numberOf(TutorialMips.REG_NAMES[i]));
        }
    }

    /**
     * 튜토리얼 프로그램: Hallym MIPS 2.6.0이 tutorial.s를 예외 처리기 없이 어셈블해 내보낸 이미지(tests/tutorial/README.md).
     * 시작 코드가 없어 .text는 프로그램 자신의 워드뿐이고 entry = main = 0x00400000이다. 그 워드·데이터는 Hallym MIPS
     * v2.4.0 골든 data(같은 프로그램을 처리기와 함께 어셈블한 것)의 시작 코드 9워드 뒤와 같다: 이 도구는 인코딩하지 않는다.
     */
    @Test
    void theProgramIsHallymMipsExportWithNoStartUpCode() throws Exception {
        String h = new String(Files.readAllBytes(new File(TUTORIAL, "tutorial.hmx").toPath()), StandardCharsets.UTF_8);
        byte[] s = Files.readAllBytes(new File(TUTORIAL, "tutorial.s").toPath());
        assertTrue(h.startsWith("HALLYM-EXEC 1\n"), "the file as Hallym MIPS wrote it");
        assertTrue(h.contains("\nproduced-by   Hallym MIPS 2.6.0\n"), h);
        assertTrue(h.contains("\nsource-sha256 " + sha256(s) + "\n"), "its source is tutorial.s");
        assertTrue(new String(s, StandardCharsets.UTF_8).startsWith("# assemble: no exception handler\n"));
        assertTrue(h.contains("\nentry         0x00400000\n") && h.contains("\nsymbol main   0x00400000\n"), h);
        String g = new String(Files.readAllBytes(new File(TESTS, "hmx/hallym-mips-v2.4.0/data.hmx").toPath()),
                StandardCharsets.UTF_8);
        List<String> withHandler = words(g);
        assertEquals(27, withHandler.size());
        assertEquals(withHandler.subList(9, 27), words(h), "the program's own words, the start-up code's nine not there");
        assertEquals(section(g, ".data"), section(h, ".data"));
    }

    /** .text의 워드들. */
    static List<String> words(String hmx) {
        List<String> out = new ArrayList<>();
        boolean in = false;
        for (String line : hmx.split("\n", -1)) {
            if (line.startsWith(".text ")) {
                in = true;
            } else if (in && line.matches("[0-9a-f]{8}")) {
                out.add(line);
            } else if (in) {
                break;
            }
        }
        return out;
    }

    /** 한 구간의 머리 줄부터 빈 줄 앞까지. */
    static String section(String hmx, String head) {
        int i = hmx.indexOf("\n" + head + " ");
        int j = hmx.indexOf("\n\n", i + 1);
        return i < 0 ? "" : hmx.substring(i, j < 0 ? hmx.length() : j);
    }

    static String sha256(byte[] b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte x : java.security.MessageDigest.getInstance("SHA-256").digest(b)) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }
}
