/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.libs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;

import kr.ac.hallym.hcs.app.gui.GuiTestSupport;
import kr.ac.hallym.hcs.app.palette.PaletteActions;
import kr.ac.hallym.hcs.app.sim.SimControls;
import kr.ac.hallym.hcs.app.tabs.FileTabs;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * P-03 GUI({@code xvfb-run -a ./gradlew :app:guiTest}): 두 탭(1bit_adder, ripple_carry). ripple_carry에 1bit_adder
 * 파일을 끌어 놓으면 라이브러리로 불러와 회로가 놓이고, 1bit_adder를 고쳐 저장하면 ripple_carry의 인스턴스가 새
 * 버전으로 바뀌고 탭에 "Updated"가 붙는다. "Edit Original File"은 열린 1bit_adder 탭으로 간다.
 */
@Tag("gui")
class LibrarySyncGuiTest {
    @TempDir
    Path tmp;

    static void settle() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(150);
        SwingUtilities.invokeAndWait(() -> { });
    }

    static Frame show(Project p) throws Exception {
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Frame f = new Frame(p);
            p.setFrame(f);
            f.setVisible(true);
            fr.set(f);
        });
        return fr.get();
    }

    static boolean updated(Project p) {
        return FileTabs.get().model().tabs().stream().anyMatch(t -> t.key() == p && t.updated());
    }

    @Test
    void savingOneTabUpdatesTheOtherTab() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        FileTabs.get().install();
        File dir = tmp.toFile();
        File adderFile = OpenFileLibrariesTest.adder(dir);
        Project adder = OpenFileLibrariesTest.open(adderFile);
        Project ripple = OpenFileLibrariesTest.ripple(dir);
        List<Frame> frames = new ArrayList<>();
        try {
            frames.add(show(adder));
            frames.add(show(ripple));
            settle();

            // 1bit_adder 탭을 ripple_carry 캔버스에 끌어 놓았다
            AtomicBoolean dropped = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> dropped.set(PaletteActions.dropFile(ripple, adderFile, "1bit_adder",
                    Location.create(302, 198))));
            settle();
            assertTrue(dropped.get());
            LoadedLibrary lib = OpenFileLibraries.loaded(ripple, adderFile);
            assertNotNull(lib, "Load Library was done automatically");
            Circuit main = ripple.getLogisimFile().getMainCircuit();
            Component inst = main.getNonWires().stream().filter(c -> c.getFactory() instanceof SubcircuitFactory)
                    .findFirst().orElse(null);
            assertNotNull(inst, "the circuit was placed");
            assertEquals(Location.create(300, 200), inst.getLocation(), "on the grid");
            Circuit before = ((SubcircuitFactory) inst.getFactory()).getSubcircuit();
            assertTrue(OpenFileLibraries.candidates(ripple).isEmpty());

            // 1bit_adder를 고쳐(속만) 저장한다: 원조 저장 경로에 HCS 앞뒤가 붙어 있다
            Circuit adderMain = adder.getLogisimFile().getCircuit("1bit_adder");
            SwingUtilities.invokeAndWait(() -> {
                FileTabs.get().model().activate(adder); // 저장하는 탭을 보고 있다
                CircuitBuilder b = new CircuitBuilder(adder.getLogisimFile(), adderMain);
                b.add("Gates", "NOT Gate", 200, 300);
                b.commit();
                assertTrue(ProjectActions.doSave(adder)); // File › Save
            });
            settle();
            Component now = main.getNonWires().stream().filter(c -> c.getFactory() instanceof SubcircuitFactory)
                    .findFirst().get();
            Circuit after = ((SubcircuitFactory) now.getFactory()).getSubcircuit();
            assertTrue(after != before, "the instance now uses the saved version");
            assertTrue(after.getNonWires().stream().anyMatch(c -> "NOT Gate".equals(c.getFactory().getName())),
                    "with the new NOT gate");
            assertTrue(updated(ripple), "Updated badge on the ripple_carry tab");
            assertTrue(SimControls.lastNotice(ripple).contains("1bit_adder.circ"), SimControls.lastNotice(ripple));

            // 탭을 고르면 배지가 사라진다
            SwingUtilities.invokeAndWait(() -> FileTabs.get().model().activate(ripple));
            settle();
            assertTrue(!updated(ripple));

            // Edit Original File: 열린 1bit_adder 탭으로 가서 그 회로를 보인다
            File origin = LibrarySync.originFile(ripple, after);
            assertTrue(OpenFileLibraries.same(adderFile, origin));
            AtomicReference<Project> went = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> went.set(LibrarySync.editOriginal(ripple, origin, "1bit_adder")));
            settle();
            assertSame(adder, went.get());
            assertSame(adderMain, adder.getCurrentCircuit());
            assertSame(adder, FileTabs.get().model().active());
        } finally {
            for (Frame f : frames) {
                SwingUtilities.invokeAndWait(f::dispose);
            }
        }
    }
}
