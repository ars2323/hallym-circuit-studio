/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

/**
 * Y-02 GUI: 기준 창 크기 6가지에서 사이클 표가 최소 3열(683 폭은 2열) 온전히 보인다. Registers 칸은 좁아지면 2진수·10진수
 * 열을 숨기고, 그래도 모자라면 접혀 탭이 된다.
 */
@Tag("gui")
class CycleWidthGuiTest {
    static final int[][] SIZES = {{1920, 1040}, {1280, 800}, {1093, 582}, {1024, 728}, {910, 505}, {683, 512}};

    @TempDir
    Path tmp;

    static void settle() throws Exception {
        for (int i = 0; i < 4; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(120);
        }
    }

    @Test
    void tableShowsAtLeastThreeColumnsAtEveryLaptopSize() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = RecordingTestSupport.openRefMips(tmp);
        Project proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Frame f = new Frame(proj);
            proj.setFrame(f);
            f.setVisible(true);
            fr.set(f);
        });
        Frame frame = fr.get();
        try {
            CycleView view = CycleView.of(proj);
            SwingUtilities.invokeAndWait(view::open);
            settle();
            List<String> problems = new ArrayList<>();
            List<String> seen = new ArrayList<>();
            for (int[] sz : SIZES) {
                for (int i = 0; i < 8; i++) {
                    SwingUtilities.invokeAndWait(() -> {
                        frame.setExtendedState(java.awt.Frame.NORMAL);
                        frame.setMinimumSize(new Dimension(200, 150));
                        frame.setBounds(0, 0, sz[0], sz[1]);
                        frame.validate();
                    });
                    settle();
                    if (frame.getWidth() == sz[0] && frame.getHeight() == sz[1]) {
                        break;
                    }
                }
                int[] got = new int[2];
                boolean[] coll = new boolean[1];
                SwingUtilities.invokeAndWait(() -> {
                    got[0] = view.visibleColumns();
                    got[1] = view.registerPanel().compact();
                    coll[0] = view.isSideCollapsed();
                });
                int want = sz[0] <= 683 ? 2 : 3;
                String[] info = new String[1];
                SwingUtilities.invokeAndWait(() -> info[0] = view.layoutInfo());
                seen.add(sz[0] + "x" + sz[1] + ": columns " + got[0] + " compact " + got[1] + " " + info[0]);
                if (got[0] < want) {
                    problems.add(sz[0] + "x" + sz[1] + ": only " + got[0] + " columns (want " + want + ")"
                            + (coll[0] ? " collapsed" : " compact " + got[1]));
                }
            }
            assertTrue(problems.isEmpty(), String.join("\n", problems) + "\nseen: " + seen);
            // Registers 칸 탭 머리는 좁아도 두 줄로 꺾이지 않는다(스크롤 탭, v1.0.3 최종 세트 검토)
            assertTrue(view.sideTabs().getTabLayoutPolicy() == javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT,
                    "the side tabs scroll instead of wrapping");
            // 이름 열은 상한 안에서 가장 긴 이름을 따른다
            assertTrue(view.nameWidth() >= 80 && view.nameWidth() <= CycleView.NAME_W, "name column " + view.nameWidth());
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
