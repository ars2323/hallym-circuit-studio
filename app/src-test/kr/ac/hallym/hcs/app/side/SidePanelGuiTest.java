/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.side;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * S-11 GUI({@code xvfb-run -a ./gradlew :app:guiTest}): 왼쪽 칸 아래에 Tunnels·Minimap 탭. 터널 이름을 누르면 그
 * 터널을 고르고 다시 누르면 다음 것, 편집하면 목록이 바뀐다. 미니맵을 누르면 그 자리가 캔버스 가운데로 온다.
 */
@Tag("gui")
class SidePanelGuiTest {
    @TempDir
    Path tmp;

    static SidePanel find(Container root) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof SidePanel) {
                return (SidePanel) c;
            }
            if (c instanceof Container) {
                SidePanel s = find((Container) c);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    static void settle() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(200);
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void tunnelsAndMinimapWork() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        kr.ac.hallym.hcs.app.gui.GuiTestSupport.keepAlive();
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component t1 = b.add("Wiring", "Tunnel", 100, 100, "label", "pc");
        Component t2 = b.add("Wiring", "Tunnel", 2000, 1400, "label", "pc");
        b.commit();
        Project proj = new Project(f);
        proj.getSimulator().setIsRunning(false);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> fr.set(new Frame(proj)));
        Frame frame = fr.get();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame.setVisible(true);
                frame.setBounds(0, 0, 1400, 900);
                frame.validate();
            });
            settle();
            SidePanel side = find(frame.getContentPane());
            assertNotNull(side, "the left panel has the lower tabs");
            assertEquals(Messages.get("side.tunnels"), side.tabs().getTitleAt(0));
            assertEquals(Messages.get("side.minimap"), side.tabs().getTitleAt(1));
            // 처음 나눔은 칸 높이의 절반(S-11 검토: 트리 선호 크기대로면 탭이 아래 1/3뿐이었다)
            javax.swing.JSplitPane split = side.split();
            int half = (split.getHeight() - split.getDividerSize()) / 2;
            assertTrue(Math.abs(split.getDividerLocation() - half) <= 2, split.getDividerLocation() + " vs " + half);
            List<TunnelList.Entry> shown = side.tunnels().shown();
            assertEquals(1, shown.size());
            SwingUtilities.invokeAndWait(() -> side.tunnels().goTo(side.tunnels().shown().get(0)));
            settle();
            assertTrue(proj.getSelection().contains(t1), "the first pc tunnel");
            SwingUtilities.invokeAndWait(() -> side.tunnels().goTo(side.tunnels().shown().get(0)));
            settle();
            assertTrue(proj.getSelection().contains(t2), "then the next one");
            Canvas canvas = frame.getCanvas();
            Rectangle vis = canvas.getVisibleRect();
            assertTrue(canvas.hcsToScreen(new Rectangle(2000, 1400, 1, 1)).intersects(vis), "scrolled to it");

            // 편집하면 목록이 바뀐다
            SwingUtilities.invokeAndWait(() -> {
                CircuitBuilder more = new CircuitBuilder(f, f.getMainCircuit());
                more.add("Wiring", "Tunnel", 400, 400, "label", "npc");
                more.commit();
            });
            settle();
            assertEquals(2, side.tunnels().shown().size());

            // 미니맵의 t1 자리를 누르면 캔버스 가운데가 t1 근처로
            SwingUtilities.invokeAndWait(() -> side.tabs().setSelectedIndex(1));
            settle();
            Minimap m = side.minimap();
            Minimap.Fit fit = m.fit();
            int mx = (int) Math.round(fit.x(100));
            int my = (int) Math.round(fit.y(100));
            SwingUtilities.invokeAndWait(() -> m.centerAt(mx, my));
            settle();
            Rectangle v = canvas.getVisibleRect();
            Location mid = canvas.hcsToCircuit(v.x + v.width / 2, v.y + v.height / 2);
            // 맨 위·왼쪽이라 스크롤이 0에서 멈출 수 있다: t1이 보이는 영역 안이면 된다
            assertTrue(canvas.hcsToScreen(new Rectangle(100, 100, 1, 1)).intersects(v), "t1 is in view: " + mid);
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
