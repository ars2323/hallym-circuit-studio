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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.app.cycle.ConsoleTextAccess;
import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** 스크린샷용 console-demo.circ: 생성기와 커밋 파일이 같고, 돌리면 "ABCDEF"를 찍고 exit 한다. */
class ConsoleDemoTest {
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));
    static final File COMMITTED = new File(System.getProperty("hcs.circDir"), "console-demo.circ");

    @TempDir
    Path tmp;

    LogisimFile file;

    File generate(Path dir) throws Exception {
        Files.createDirectories(dir);
        Path jar = dir.resolve("hcs-mips.jar");
        Files.copy(MIPS_JAR.toPath(), jar, StandardCopyOption.REPLACE_EXISTING);
        Loader loader = new Loader(null);
        file = CircuitBuilder.newFile(loader, dir.toFile());
        Library lib = loader.loadJarLibrary(jar.toFile(), "kr.ac.hallym.hcs.mips.MipsLibrary");
        file.addLibrary(lib);
        ConsoleDemo.build(file, lib);
        File out = dir.resolve("console-demo.circ").toFile();
        CircuitBuilder.save(file, out);
        return out;
    }

    @Test
    void committedFileMatchesTheGenerator() throws Exception {
        File fresh = generate(tmp.resolve("gen"));
        if (Boolean.getBoolean("hcs.update") || !COMMITTED.exists()) {
            Files.copy(fresh.toPath(), COMMITTED.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        assertEquals(CircNormalizer.normalize(new String(Files.readAllBytes(COMMITTED.toPath()),
                StandardCharsets.UTF_8)), CircNormalizer.normalize(new String(Files.readAllBytes(fresh.toPath()),
                        StandardCharsets.UTF_8)));
    }

    @Test
    void printsLettersThenExits() throws Exception {
        generate(tmp.resolve("run"));
        Project proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
        CircuitState root = new CircuitState(proj, file.getMainCircuit());
        root.getPropagator().propagate();
        for (int s = 1; s <= 30; s++) {
            Propagator p = root.getPropagator();
            p.tick();
            p.propagate();
        }
        List<String[]> out = ConsoleTextAccess.collect(root);
        assertEquals(1, out.size());
        assertEquals("ABCDEF", out.get(0)[1]);
        assertTrue(Boolean.parseBoolean(out.get(0)[2]), "exited");
    }
}
