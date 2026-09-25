/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
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

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.tabs.FileTabBar;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * S-20 회귀({@code xvfb-run -a ./gradlew :app:guiTest}): 편집 화면에서 원조 도구 모음(캔버스 위 도구 아이콘 줄)과
 * 탐색기 아이콘 줄(회로 추가·옮기기·지우기)이 보이지 않는다. 캔버스 위는 네 줄뿐이다: 메뉴, 앱 도구 모음, 파일
 * 탭, 회로 탭.
 */
@Tag("gui")
class TopRowsGuiTest {
    @TempDir
    Path tmp;

    static void settle() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(200);
        SwingUtilities.invokeAndWait(() -> { });
    }

    static <T> List<T> all(Container root, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                out.add(type.cast(c));
            }
            if (c instanceof Container) {
                out.addAll(all((Container) c, type));
            }
        }
        return out;
    }

    @Test
    void originalToolbarsAreHiddenAndFourRowsSitAboveTheCanvas() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Project proj = new Project(f);
        proj.getSimulator().setIsRunning(false);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Frame frame = new Frame(proj);
            proj.setFrame(frame);
            frame.setVisible(true);
            frame.setBounds(0, 0, 1400, 900);
            frame.validate();
            fr.set(frame);
        });
        Frame frame = fr.get();
        try {
            settle();
            AtomicReference<String> fail = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                // 원조 도구 모음과 탐색기 아이콘 줄(둘 다 com.cburch.draw.toolbar.Toolbar)은 편집 화면에 보이지 않는다
                for (com.cburch.draw.toolbar.Toolbar t : all(frame.getRootPane(),
                        com.cburch.draw.toolbar.Toolbar.class)) {
                    if (t.isShowing()) {
                        fail.set("an original toolbar is showing at " + t.getBounds() + " in " + t.getParent());
                    }
                }
            });
            assertEquals(null, fail.get());

            AtomicReference<int[]> rows = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                Container content = frame.getContentPane();
                Component north = ((BorderLayout) content.getLayout()).getLayoutComponent(BorderLayout.NORTH);
                FileTabBar tabs = all(frame.getRootPane(), FileTabBar.class).get(0);
                JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class,
                        frame.getCanvas());
                int canvasTop = SwingUtilities.convertPoint(pane, 0, 0, frame.getRootPane()).y;
                int menu = frame.getJMenuBar().getHeight();
                rows.set(new int[] {canvasTop, menu, north == null ? -1 : north.getHeight(), tabs.getHeight(),
                        SwingUtilities.convertPoint(tabs, 0, 0, frame.getRootPane()).y});
            });
            int[] r = rows.get();
            String why = "canvas top " + r[0] + ", menu " + r[1] + ", toolbar " + r[2] + ", tabs " + r[3] + " at "
                    + r[4];
            assertNotNull(r);
            assertTrue(r[2] > 0 && r[2] <= 48, "one toolbar row: " + why);
            assertTrue(r[3] > 0 && r[3] <= 44, "one file tab row: " + why);
            // 메뉴·도구 모음 바로 아래 파일 탭, 그 아래 회로 탭 한 줄, 그 아래 캔버스(사이에 다른 줄이 없다)
            assertEquals(r[1] + r[2], r[4], 2, "the file tabs sit right under the toolbar: " + why);
            int circuitTabs = r[0] - (r[4] + r[3]);
            assertTrue(circuitTabs > 0 && circuitTabs <= 36, "one circuit tab row, then the canvas: " + why);
            assertTrue(r[0] <= 4 * 36, "four rows above the canvas: " + why);
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
