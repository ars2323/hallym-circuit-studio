/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.diag.MessagesPanel;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * v1.0.2 검토: 캔버스 아래 Messages 칸이 0 아래로 굳어도(스크린샷 실행기의 새 창에서 나눔선이 창 밖으로 나갔다)
 * 다음 배치에서 기본 높이로 돌아온다. 창이 제 높이를 얻기 전의 0 높이 배치(JSplitPane resizeWeight 1.0의 함정)도 같다.
 */
@Tag("gui")
class BottomPanelGuiTest {
    @TempDir
    Path tmp;

    @Test
    void messagesAreaSurvivesATinyFirstLayout() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
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
            SwingUtilities.invokeAndWait(() -> {
                frame.setBounds(0, 0, 1400, 900);
                frame.validate();
            });
            Thread.sleep(400);
            MessagesPanel mp = MessagesPanel.of(frame);
            int[] got = new int[3];
            SwingUtilities.invokeAndWait(() -> got[0] = mp.bottomHeight());
            assertTrue(got[0] >= 100, "a fresh window shows the Messages area: " + got[0]);
            // 스크린샷 실행기에서 본 상태: 나눔선이 창 아래로 넘어가 아래 칸 높이가 음수(-33)로 굳었다
            SwingUtilities.invokeAndWait(() -> {
                mp.setBottomHeight(-33);
                frame.validate();
            });
            SwingUtilities.invokeAndWait(() -> got[1] = mp.bottomHeight());
            SwingUtilities.invokeAndWait(() -> {
                frame.setBounds(0, 0, 1400, 901); // 다음 배치에서 되돌린다
                frame.validate();
            });
            Thread.sleep(400);
            SwingUtilities.invokeAndWait(() -> got[2] = mp.bottomHeight());
            assertTrue(got[2] >= 100, "a bottom area pushed below the window comes back: " + got[1] + " -> " + got[2]);
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
