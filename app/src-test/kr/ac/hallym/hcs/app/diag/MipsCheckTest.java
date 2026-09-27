/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** D-04 MIPS 부품 검사의 "이미 말함" 열쇠(D-129): 부품과 경로를 ==로 가른다. */
class MipsCheckTest {
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));

    @TempDir
    Path tmp;

    /**
     * 두 Data Memory가 같은 문제(정렬되지 않은 주소)를 가지면 부품이 둘이라 두 번 말한다. identity hash 문자열 열쇠는
     * 두 부품을 하나로 봐서 한 번만 말했다(-XX:hashCode=2 JVM에서 늘).
     */
    @Test
    void twoMemoriesWithTheSameProblemAreTwoMessages() throws Exception {
        Loader loader = new Loader(null);
        LogisimFile file = CircuitBuilder.newFile(loader, tmp.toFile());
        Path jar = tmp.resolve("hcs-mips.jar");
        Files.copy(MIPS_JAR.toPath(), jar, StandardCopyOption.REPLACE_EXISTING);
        Library mips = loader.loadJarLibrary(jar.toFile(), "kr.ac.hallym.hcs.mips.MipsLibrary");
        file.addLibrary(mips);
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component clk = b.add("Wiring", "Clock", 100, 700);
        b.tunnel(clk, 0, "clk");
        b.constant("one", 1, 1, 80, 200);
        b.constant("zero", 1, 0, 80, 240);
        // 영역이 겹치지 않는 두 메모리, 주소는 둘 다 워드 정렬이 아니다
        b.constant("addr1", 32, 0x10010002, 80, 100);
        b.constant("addr2", 32, 0x20000002, 80, 140);
        Component m1 = b.add(mips, "Data Memory", 600, 300);
        Component m2 = b.add(mips, "Data Memory", 600, 600, "base", "0x20000000", "size", "0x1000");
        int k = 1;
        for (Component m : new Component[] {m1, m2}) {
            b.tunnel(m, 0, "addr" + k);
            b.tunnel(m, 1, "addr" + k);
            b.tunnel(m, 2, "zero");
            b.tunnel(m, 3, "one");
            b.tunnel(m, 4, "clk");
            b.tunnel(m, 5, "data" + k);
            b.output("data" + k, 32, 900, 100 * k);
            k++;
        }
        b.commit();
        List<Diagnostic> ds = FaultCollectionTest.messages(file, 8);
        List<Diagnostic> mipsStatus = ds.stream().filter(d -> d.kind == Diagnostic.Kind.MIPS_STATUS).toList();
        assertEquals(2, mipsStatus.size(), ds.toString());
        assertTrue(mipsStatus.stream().anyMatch(d -> d.components.contains(m1)), ds.toString());
        assertTrue(mipsStatus.stream().anyMatch(d -> d.components.contains(m2)), ds.toString());
    }
}
