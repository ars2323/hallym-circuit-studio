/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #77: 한 사이클 = 원조 틱 두 번(카운터가 딱 1 오른다), 상태 표시줄 값. */
class StatusModelTest {
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));

    @TempDir
    Path tmp;

    @Test
    void oneCycleIsTwoTicks() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component clk = b.add("Wiring", "Clock", 100, 200);
        b.tunnel(clk, 0, "clk");
        Component ctr = b.add("Memory", "Counter", 400, 200, "width", "8", "label", "PC");
        b.tunnel(ctr, 2, "clk");
        b.commit();
        Project proj = new Project(file);
        CircuitState state = proj.getCircuitState();
        state.getPropagator().propagate();
        assertEquals("0x00000000", StatusModel.pc(state));
        for (int cycle = 1; cycle <= 3; cycle++) {
            StatusModel.oneCycle(state.getPropagator());
            assertEquals(String.format("0x%08x", cycle), StatusModel.pc(state), "cycle " + cycle);
        }
        assertEquals(3, StatusModel.cycles(7));
    }

    @Test
    void programAndMissingValues() throws Exception {
        Loader loader = new Loader(null);
        LogisimFile file = CircuitBuilder.newFile(loader, tmp.toFile());
        Path jar = tmp.resolve("hcs-mips.jar");
        Files.copy(MIPS_JAR.toPath(), jar, StandardCopyOption.REPLACE_EXISTING);
        Library mips = loader.loadJarLibrary(jar.toFile(), "kr.ac.hallym.hcs.mips.MipsLibrary");
        file.addLibrary(mips);
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        assertNull(StatusModel.program(file.getMainCircuit()));
        b.add(mips, "Instruction Memory", 400, 200, "source", "/home/s/lab/sum.s");
        b.commit();
        assertEquals("sum.s", StatusModel.program(file.getMainCircuit()));
        assertNull(StatusModel.pc(null));
    }
}
