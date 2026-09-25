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

    /** Messages(정적 진단)에 label 인스턴스의 "연결되지 않은 서브회로 포트"가 있는가. */
    static boolean portUnconnected(Project p, String label) {
        return kr.ac.hallym.hcs.app.diag.StaticCheck.run(p.getLogisimFile()).stream().anyMatch(x -> x.kind
                == kr.ac.hallym.hcs.app.diag.Diagnostic.Kind.SUBCIRCUIT_PORT_UNCONNECTED && x.components.stream()
                        .anyMatch(c -> label.equals(kr.ac.hallym.hcs.app.model.Names.label(c))));
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
                    Location.create(302, 598))));
            settle();
            assertTrue(dropped.get());
            LoadedLibrary lib = OpenFileLibraries.loaded(ripple, adderFile);
            assertNotNull(lib, "Load Library was done automatically");
            Circuit main = ripple.getLogisimFile().getMainCircuit();
            Component inst = main.getNonWires().stream().filter(c -> c.getFactory() instanceof SubcircuitFactory)
                    .findFirst().orElse(null);
            assertNotNull(inst, "the circuit was placed");
            assertEquals(Location.create(300, 600), inst.getLocation(), "on the grid");
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

            // 포트가 바뀌는 저장(경고에서 Save Anyway를 고른 것과 같다)
            AtomicReference<List<Component>> fas = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                fas.set(OpenFileLibrariesTest.placeAdders(ripple, OpenFileLibraries.loaded(ripple, adderFile)));
                // 선 끝마다 핀을 달아 포트가 다른 포트와 이어지게 한다
                CircuitBuilder pb = new CircuitBuilder(ripple.getLogisimFile(), main);
                for (Component fa : fas.get()) {
                    for (int i = 0; i < fa.getEnds().size(); i++) {
                        Location p = fa.getEnd(i).getLocation();
                        if (fa.getEnd(i).isOutput()) {
                            pb.add("Wiring", "Pin", p.getX() + 30, p.getY(), "facing", "west", "output", "true");
                        } else {
                            pb.add("Wiring", "Pin", p.getX() - 30, p.getY(), "tristate", "false");
                        }
                    }
                }
                pb.commit();
            });
            settle();
            assertTrue(!portUnconnected(ripple, "fa0"), "fa0's ports are wired before the change");
            Component b = adderMain.getNonWires().stream().filter(c -> "b".equals(
                    kr.ac.hallym.hcs.app.model.Names.label(c))).findFirst().get();
            SwingUtilities.invokeAndWait(() -> {
                com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(
                        adderMain);
                m.remove(b);
                m.execute();
                List<LibrarySync.Cut> cuts = LibrarySync.impact(adder, adderFile);
                assertEquals(1, cuts.size(), "the warning would show");
                assertEquals(java.util.Arrays.asList("fa0", "fa1"), cuts.get(0).instances);
                try {
                    CircuitBuilder.save(adder.getLogisimFile(), adderFile);
                } catch (java.io.IOException e) {
                    throw new AssertionError(e);
                }
                LibrarySync.afterSave(adder, adderFile);
            });
            settle();
            // 인스턴스가 새 모양(포트 4개)으로 바뀌었다. 남은 포트는 옛 a·b 자리로 당겨져 다른 핀과 이어진다:
            // 정적 진단으로는 알 수 없는 끊김이라 저장 전 경고가 핀 이름으로 센다(D-065)
            Component fa0 = main.getNonWires().stream().filter(c -> "fa0".equals(kr.ac.hallym.hcs.app.model.Names
                    .label(c))).findFirst().get();
            assertEquals(4, fa0.getEnds().size());
            assertTrue(!portUnconnected(ripple, "fa0"), "no static message for a port pulled onto another wire");

        } finally {
            for (Frame f : frames) {
                SwingUtilities.invokeAndWait(f::dispose);
            }
        }
    }
}
