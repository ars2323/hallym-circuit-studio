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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.app.cycle.CycleModel;
import kr.ac.hallym.hcs.app.cycle.MachineState;
import kr.ac.hallym.hcs.app.cycle.ProgramSource;
import kr.ac.hallym.hcs.app.record.Recording;
import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** 스크린샷용 stack-demo.circ: 생성기와 커밋 파일이 같고, k사이클 뒤 $sp 깊이가 4k이며 Stack 화살표가 그 칸에 있다. */
class StackDemoTest {
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));
    static final File COMMITTED = new File(System.getProperty("hcs.circDir"), "stack-demo.circ");

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
        StackDemo.build(file, lib);
        File out = dir.resolve("stack-demo.circ").toFile();
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
    void stackDeepensFourBytesPerCycle() throws Exception {
        generate(tmp.resolve("run"));
        Project proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
        CircuitState root = new CircuitState(proj, file.getMainCircuit());
        root.getPropagator().propagate();
        Recording r = new Recording(file.getMainCircuit());
        r.restart(root, 0);
        for (int s = 1; s <= 12; s++) {
            Propagator p = root.getPropagator();
            p.tick();
            p.propagate();
            r.capture(root, s, false);
        }
        CycleModel m = new CycleModel(file.getMainCircuit(), r, null, ProgramSource.EMPTY);
        MachineState ms = new MachineState(m, file);
        for (int k = 1; k <= 6; k++) {
            assertEquals(4L * k, MachineState.depth(ms.sp(k)), "cycle " + k);
        }
        MachineState.Memory stack = ms.memories(root, 6).get(0);
        assertTrue(stack.stack);
        assertEquals(24, stack.depth);
        assertEquals(24, stack.peak);
        assertEquals(6, stack.words.size());
        assertTrue(stack.words.get(5).sp);
        assertEquals(5, stack.words.get(5).value, "count 5 written at the sixth push");
    }
}
