/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.cycle.CycleView;
import kr.ac.hallym.hcs.app.diag.MessagesPanel;
import kr.ac.hallym.hcs.app.gui.GuiTestSupport;
import kr.ac.hallym.hcs.app.side.SidePanel;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * Y-01 GUI: 학생 노트북 기준 창 크기 6가지 모두에서 캔버스가 창 내부의 절반 이상(가로·세로)을 갖는다. Cycle View를 편 상태로
 * 잰다. 작은 창에서 접힌 아래 칸은 탭을 누르면 펴지고, 창이 커지면 학생이 정한 높이로 돌아온다.
 */
@Tag("gui")
class VerticalBalanceGuiTest {
    /** 1920×1040, 1280×800, 1093×582(1366×768 125%), 1024×728, 910×505(150%), 683×512(1024×768 150%). */
    public static final int[][] SIZES = {{1920, 1040}, {1280, 800}, {1093, 582}, {1024, 728}, {910, 505}, {683, 512}};

    @TempDir
    Path tmp;

    static void settle() throws Exception {
        for (int i = 0; i < 4; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(120);
        }
    }

    static void size(Frame frame, int w, int h) throws Exception {
        for (int i = 0; i < 8; i++) {
            SwingUtilities.invokeAndWait(() -> {
                frame.setExtendedState(java.awt.Frame.NORMAL);
                frame.setMinimumSize(new Dimension(200, 150)); // 작은 화면의 창 시스템이 정하는 최소를 흉내 낸다
                frame.setBounds(0, 0, w, h);
                frame.validate();
            });
            settle();
            if (frame.getWidth() == w && frame.getHeight() == h) {
                return;
            }
        }
    }

    @Test
    void canvasKeepsHalfOfTheWindowAtEveryLaptopSize() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
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
            size(frame, 1920, 1040);
            SwingUtilities.invokeAndWait(() -> CycleView.of(proj).open()); // 표가 보이는 높이로 아래 칸을 편다
            settle();
            MessagesPanel mp = MessagesPanel.of(frame);
            int userBottom = mp.userBottom();
            assertTrue(userBottom >= 200, "Cycle View asked for a tall bottom: " + userBottom);
            List<String> problems = new ArrayList<>();
            for (int[] sz : SIZES) {
                size(frame, sz[0], sz[1]);
                int[] got = new int[6];
                SwingUtilities.invokeAndWait(() -> {
                    JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class,
                            frame.getCanvas());
                    got[0] = sp.getWidth();
                    got[1] = sp.getHeight();
                    got[2] = frame.getContentPane().getWidth();
                    got[3] = frame.getContentPane().getHeight();
                    SidePanel side = SidePanel.of(frame.getContentPane());
                    got[4] = side == null ? -1 : side.treeHeight();
                    got[5] = side == null ? -1 : side.treeHeight() + side.tabsHeight();
                });
                String tag = sz[0] + "x" + sz[1] + " (frame " + frame.getWidth() + "x" + frame.getHeight() + ")";
                if (got[0] * 2 < got[2]) {
                    problems.add(tag + ": canvas width " + got[0] + " < half of " + got[2]);
                }
                if (got[1] * 2 < got[3]) {
                    problems.add(tag + ": canvas height " + got[1] + " < half of " + got[3] + " bottom "
                            + mp.bottomHeight() + (mp.isAutoCollapsed() ? " collapsed" : ""));
                }
                if (got[4] >= 0 && got[4] * 2 < got[5] - 2) {
                    problems.add(tag + ": tree height " + got[4] + " < half of side " + got[5]);
                }
            }
            assertTrue(problems.isEmpty(), String.join("\n", problems));

            // 가장 작은 창(683×512)에서 접힌 아래 칸: 새 메시지는 탭 이름의 개수 배지로만, 탭을 누르면 펴진다
            size(frame, 683, 512);
            boolean collapsed = mp.isAutoCollapsed();
            if (collapsed) {
                int before = mp.bottomHeight();
                SwingUtilities.invokeAndWait(() -> {
                    CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
                    com.cburch.logisim.comp.Component and = b.add("Gates", "AND Gate", 200, 200);
                    com.cburch.logisim.comp.Component y = b.output("y", 1, 300, 200);
                    b.wire(CircuitBuilder.port(and, 0), CircuitBuilder.port(y, 0)); // 출력은 잇고 입력은 비워 메시지
                    b.commit();
                    kr.ac.hallym.hcs.app.diag.Diagnostics.of(proj).refresh();
                });
                settle();
                assertTrue(mp.messagesTabTitle().matches(".*\\(\\d+\\)"), "badge on the collapsed tab: "
                        + mp.messagesTabTitle() + " messages " + mp.messageCount() + " collapsed " + mp.isAutoCollapsed()
                        + " bottom " + mp.bottomHeight());
                assertEquals(before, mp.bottomHeight(), 2, "a new message does not expand the collapsed bottom");
                SwingUtilities.invokeAndWait(() -> {
                    javax.swing.JTabbedPane tabs = mp.tabs();
                    java.awt.Rectangle r = tabs.getBoundsAt(0);
                    tabs.dispatchEvent(new java.awt.event.MouseEvent(tabs, java.awt.event.MouseEvent.MOUSE_PRESSED,
                            System.currentTimeMillis(), java.awt.event.InputEvent.BUTTON1_DOWN_MASK, r.x + 5,
                            r.y + 5, 1, false, java.awt.event.MouseEvent.BUTTON1));
                });
                settle();
                assertTrue(mp.bottomHeight() >= VerticalBalance.BOTTOM_MIN, "clicking the tab expands: "
                        + mp.bottomHeight());
            }
            // 창이 다시 커지면 학생이 정한 높이로
            size(frame, 1920, 1040);
            assertEquals(userBottom, mp.bottomHeight(), 2, "the user's bottom height comes back");
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
