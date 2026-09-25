/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * P-02 GUI({@code xvfb-run -a ./gradlew :app:guiTest}): 탐색기에서 연 서브회로(자기만의 상태)에는 안내 띠와 "Go to
 * Instance in main"이 보이고, 누르면 main 안의 실행 중 인스턴스로 가서 띠가 사라진다. 인스턴스 포트가 이어진 핀을
 * 고르면 끊길 연결 수를 미리 보인다.
 */
@Tag("gui")
class InstanceBannerGuiTest {
    @TempDir
    Path tmp;

    static InstanceBanner find(Container root) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof JPanel && ((JPanel) c).getClientProperty(InstanceBanner.class) != null) {
                return InstanceBanner.of((JPanel) c);
            }
            if (c instanceof Container) {
                InstanceBanner b = find((Container) c);
                if (b != null) {
                    return b;
                }
            }
        }
        return null;
    }

    static void settle() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(100);
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void standaloneSubcircuitOffersTheRunningInstance() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        kr.ac.hallym.hcs.app.gui.GuiTestSupport.keepAlive();
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit blk = new Circuit("blk");
        f.addCircuit(blk);
        CircuitBuilder sb = new CircuitBuilder(f, blk);
        Component a = sb.add("Wiring", "Pin", 100, 100, "label", "a");
        sb.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "label", "y");
        sb.wire(Location.create(100, 100), Location.create(300, 100));
        sb.commit();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component inst = b.addSubcircuit(blk, 300, 200);
        b.commit();
        CircuitMutation m = new CircuitMutation(f.getMainCircuit());
        for (int i = 0; i < inst.getEnds().size(); i++) {
            Location p = inst.getEnd(i).getLocation();
            m.add(Wire.create(p, p.translate(inst.getEnd(i).isOutput() ? 40 : -40, 0)));
        }
        m.execute();
        Project proj = new Project(f);
        proj.getSimulator().setIsRunning(false);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> fr.set(new Frame(proj)));
        Frame frame = fr.get();
        try {
            SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
            settle();
            InstanceBanner banner = find(frame.getContentPane());
            assertNotNull(banner);
            assertFalse(banner.visible(), "main itself: no banner");
            // 탐색기에서 blk를 연다: 자기만의 상태
            SwingUtilities.invokeAndWait(() -> proj.setCurrentCircuit(blk));
            settle();
            assertTrue(banner.visible() && banner.offersGoTo(), "standalone subcircuit: banner with the link");
            assertEquals(1, banner.paths().size());
            // 인스턴스 포트가 이어진 핀을 고르면 미리 보기
            SwingUtilities.invokeAndWait(() -> proj.getSelection().add(a));
            settle();
            assertNotNull(banner.previewShown(), "pin preview");
            assertTrue(banner.previewShown().contains("1"), banner.previewShown());
            // Go to Instance in main
            SwingUtilities.invokeAndWait(() -> banner.goTo(banner.paths().get(0)));
            settle();
            assertNotNull(proj.getCircuitState().getParentState(), "now the running instance inside main");
            assertFalse(banner.offersGoTo(), "no link once inside the running instance");
            SwingUtilities.invokeAndWait(() -> proj.doAction(com.cburch.logisim.gui.main.SelectionActions.dropAll(
                    proj.getSelection())));
            settle();
            assertNull(banner.previewShown());
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
