/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tutorial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.gui.GuiTestSupport;
import kr.ac.hallym.hcs.app.record.RecordingTestSupport;

/** E-10 GUI: 실제 창에서 모든 단계의 대상이 찾아지고, 말풍선이 대상을 가리지 않으며, 닫으면 유리판이 돌아온다. */
@Tag("gui")
class TourGuiTest {
    @TempDir
    Path tmp;

    @Test
    void everyStepFindsItsTargetInTheRealWindow() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = RecordingTestSupport.openRefMips(tmp);
        Project proj = new Project(file);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Frame f = new Frame(proj);
            proj.setFrame(f);
            f.setVisible(true);
            f.setBounds(0, 0, 1400, 900);
            fr.set(f);
        });
        Frame frame = fr.get();
        try {
            AtomicReference<Tour.Overlay> ov = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> ov.set(Tour.show(frame)));
            Tour.Overlay o = ov.get();
            for (int i = 0; i < 100 && o.getWidth() == 0; i++) {
                Thread.sleep(50); // 유리판이 창 크기로 놓일 때까지(CI 화면에서는 늦을 수 있다)
            }
            SwingUtilities.invokeAndWait(() -> {
                frame.validate();
                o.go(0);
            });
            for (int i = 0; i < Tour.steps().size(); i++) {
                final int step = i;
                SwingUtilities.invokeAndWait(() -> o.go(step));
                Tour.Step s = Tour.steps().get(i);
                Rectangle hole = o.hole();
                if (s.target != null) {
                    assertNotNull(hole, s.key + " target not found");
                    assertTrue(hole.width > 0 && hole.height > 0, s.key);
                    assertFalse(o.bubble.getBounds().intersects(hole) && hole.width < 1000, s.key + " bubble covers target");
                }
                Rectangle pane = new Rectangle(0, 0, o.getWidth(), o.getHeight());
                assertTrue(pane.contains(o.bubble.getBounds()), s.key + " bubble inside " + pane + " but "
                        + o.bubble.getBounds() + " frame " + frame.getSize());
            }
            assertEquals(Tour.steps().size() - 1, o.step());
            SwingUtilities.invokeAndWait(o::end);
            assertFalse(frame.getRootPane().getGlassPane() instanceof Tour.Overlay);
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
