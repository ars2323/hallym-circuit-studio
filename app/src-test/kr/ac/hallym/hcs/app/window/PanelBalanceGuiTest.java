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
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Settings;
import kr.ac.hallym.hcs.app.gui.GuiTestSupport;
import kr.ac.hallym.hcs.app.props.AttrDock;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** X-03 GUI: 폭 960에서 캔버스 폭 ≥ 창 폭의 50%; 넓히면 학생이 정한 칸 폭이 돌아온다. */
@Tag("gui")
class PanelBalanceGuiTest {
    @TempDir
    Path tmp;

    @Test
    void canvasKeepsHalfTheWindowAndUserWidthsComeBack() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        Settings s = Settings.get();
        String width0 = s.getString("attrDock.width", null);
        String coll0 = s.getString("attrDock.collapsed", null);
        s.set("attrDock.width", 300);
        s.set("attrDock.collapsed", false);
        WindowBounds.saveMainSplit(0.3);
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
            for (int width : new int[] {1920, 960, 1920}) {
                for (int i = 0; i < 10; i++) { // 첫 실행은 최대화로 열린다(X-01): 보통 상태로 두고 크기가 잡힐 때까지
                    SwingUtilities.invokeAndWait(() -> {
                        frame.setExtendedState(java.awt.Frame.NORMAL);
                        frame.setBounds(0, 0, width, 900);
                    });
                    Thread.sleep(400);
                    if (frame.getWidth() == width) {
                        break;
                    }
                }
                int[] got = new int[3];
                boolean[] coll = new boolean[1];
                SwingUtilities.invokeAndWait(() -> {
                    frame.validate();
                    got[0] = frame.getCanvas().getParent().getParent().getWidth(); // 캔버스 스크롤 영역
                    got[1] = frame.getContentPane().getWidth();
                    AttrDock d = AttrDock.find(frame.getContentPane());
                    coll[0] = d.isCollapsed();
                    got[2] = d.isCollapsed() ? 0 : d.component().getWidth() - frame.getCanvas().getParent().getParent()
                            .getParent().getWidth();
                });
                assertTrue(got[0] >= got[1] / 2, width + ": canvas " + got[0] + " >= half of " + got[1]);
                if (width == 1920) {
                    assertTrue(!coll[0], "the user's dock is back at 1920");
                    PanelBalance b = PanelBalance.of(frame);
                    assertEquals(0.3, b.userFraction(), 1e-6, "the user's left fraction is kept");
                    assertTrue(!b.isBalanced());
                } else {
                    assertTrue(PanelBalance.of(frame).isBalanced(), "auto-balanced at 960: " + PanelBalance.of(frame).last());
                }
            }
            assertEquals("300", s.getString("attrDock.width", null), "the user's dock width was not overwritten");
            assertEquals("false", s.getString("attrDock.collapsed", null));
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
            s.set("attrDock.width", width0);
            s.set("attrDock.collapsed", coll0);
        }
    }
}
