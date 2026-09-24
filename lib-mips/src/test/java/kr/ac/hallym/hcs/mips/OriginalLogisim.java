/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 테스트 회로를 원조 2.7.1 API로 만들고, 원조 jar를 헤드리스 {@code -tty table}로 돌려 행을 받는다.
 * MIPS 부품은 원조가 JAR 라이브러리로 불러온 {@code hcs-mips.jar}의 것이다.
 */
final class OriginalLogisim {
    static final Path LOGISIM_JAR = Path.of(System.getProperty("hcs.logisimJar"));
    static final Path MIPS_JAR = Path.of(System.getProperty("hcs.mipsJar"));

    final Path dir;
    final LogisimFile file;
    final Library mips;
    final CircuitBuilder b;

    OriginalLogisim(Path dir) throws Exception {
        this.dir = dir;
        Path jar = dir.resolve("hcs-mips.jar");
        Files.copy(MIPS_JAR, jar, StandardCopyOption.REPLACE_EXISTING);
        Loader loader = new Loader(null);
        file = CircuitBuilder.newFile(loader, dir.toFile());
        mips = loader.loadJarLibrary(jar.toFile(), "kr.ac.hallym.hcs.mips.MipsLibrary");
        file.addLibrary(mips);
        b = new CircuitBuilder(file, file.getMainCircuit());
    }

    /** 저장하고 원조 jar로 돌린다. 각 행은 출력 핀 값들(halt 제외, 위에서 아래 순). */
    List<String[]> run(String name) throws Exception {
        b.commit();
        Path circ = dir.resolve(name + ".circ");
        CircuitBuilder.save(file, circ.toFile());
        List<String> cmd = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-jar", LOGISIM_JAR.toString(), circ.toString(), "-tty", "table");
        Path out = dir.resolve(name + ".out");
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true)
                .redirectOutput(out.toFile()).start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), name + ": 60초 안에 halt하지 않음");
        String text = Files.readString(out);
        assertEquals(0, p.exitValue(), text);
        List<String[]> rows = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (!line.isBlank()) {
                rows.add(line.split("\t"));
            }
        }
        return rows;
    }

    /** -tty 값("0000 1010", "xxxx …")을 숫자로. 정의되지 않은 비트가 있으면 null. */
    static Long value(String cell) {
        String bits = cell.replace(" ", "");
        return bits.matches("[01]+") ? Long.parseLong(bits, 2) : null;
    }

    static String hex(Long v) {
        return v == null ? "x" : String.format("%08x", v);
    }
}
