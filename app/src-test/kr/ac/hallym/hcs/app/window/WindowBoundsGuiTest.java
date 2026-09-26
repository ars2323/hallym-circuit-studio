/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Settings;
import kr.ac.hallym.hcs.app.gui.GuiTestSupport;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** X-01 GUI: 첫 실행 창은 작업 영역을 채우고, 원조 창 설정값은 건드리지 않으며, 저장은 포크 키로 간다. */
@Tag("gui")
class WindowBoundsGuiTest {
    @TempDir
    Path tmp;

    @Test
    void firstRunFillsTheWorkAreaAndLeavesOriginalPreferencesAlone() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        Settings s = Settings.get();
        for (String k : new String[] {WindowBounds.X, WindowBounds.Y, WindowBounds.W, WindowBounds.H,
            WindowBounds.MAX}) {
            s.set(k, "");
        }
        int origW = AppPreferences.WINDOW_WIDTH.get();
        int origH = AppPreferences.WINDOW_HEIGHT.get();
        String origLoc = AppPreferences.WINDOW_LOCATION.get();
        int origState = AppPreferences.WINDOW_STATE.get();
        double origSplit = AppPreferences.WINDOW_MAIN_SPLIT.get();
        WindowBounds.saveMainSplit(0.3); // 포크 키의 분할 비율이 창에 적용된다
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Project proj = new Project(file);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Frame f = new Frame(proj);
            proj.setFrame(f);
            f.setVisible(true);
            fr.set(f);
        });
        Frame frame = fr.get();
        try {
            Thread.sleep(500);
            Rectangle work = WindowBounds.workAreas().get(0);
            Rectangle[] got = new Rectangle[1];
            SwingUtilities.invokeAndWait(() -> got[0] = frame.getBounds());
            Rectangle b = got[0];
            // 최대화되면 작업 영역 전체, 아니면(Xvfb) 작업 영역 크기 그대로: 어느 쪽이든 90% 이상
            assertTrue(b.width >= work.width * 0.9 && b.height >= work.height * 0.9, "fills the work area: " + b
                    + " of " + work);
            assertTrue(frame.getMinimumSize().width >= Math.min(960, work.width));
            SwingUtilities.invokeAndWait(frame::savePreferences);
            assertEquals(origW, AppPreferences.WINDOW_WIDTH.get().intValue(), "original windowWidth untouched");
            assertEquals(origH, AppPreferences.WINDOW_HEIGHT.get().intValue());
            assertEquals(origLoc, AppPreferences.WINDOW_LOCATION.get());
            assertEquals(origState, AppPreferences.WINDOW_STATE.get().intValue());
            assertEquals(origSplit, AppPreferences.WINDOW_MAIN_SPLIT.get().doubleValue(), 1e-9,
                    "original windowMainSplit untouched");
            assertEquals(0.3, WindowBounds.mainSplit(), 0.05, "the fork's split was applied and saved back");
            assertTrue(WindowBounds.saved(s) != null, "the fork saved its own window keys");
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
            for (String k : new String[] {WindowBounds.X, WindowBounds.Y, WindowBounds.W, WindowBounds.H,
                WindowBounds.MAX}) {
                s.set(k, "");
            }
        }
    }
}
