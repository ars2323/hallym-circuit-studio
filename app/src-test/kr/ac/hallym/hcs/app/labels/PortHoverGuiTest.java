/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * S-06 GUI({@code xvfb-run -a ./gradlew :app:guiTest}): 캔버스 위 마우스 좌표를 배율로 나눠 마우스 아래 부품을 찾는다.
 * 200%에서 가산기 화면 자리에 마우스를 올리면 그 가산기, 빈 곳이면 없음.
 */
@Tag("gui")
class PortHoverGuiTest {
    @TempDir
    Path tmp;

    static void settle() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(150);
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void hoverFindsThePartAtZoom() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        kr.ac.hallym.hcs.app.gui.GuiTestSupport.keepAlive();
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component adder = b.add("Arithmetic", "Adder", 200, 150, "width", "8");
        b.commit();
        Project proj = new Project(f);
        proj.getSimulator().setIsRunning(false);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> fr.set(new Frame(proj)));
        Frame frame = fr.get();
        try {
            SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
            settle();
            Canvas canvas = frame.getCanvas();
            SwingUtilities.invokeAndWait(() -> canvas.getHcsZoom().zoomTo(2.0));
            settle();
            SwingUtilities.invokeAndWait(canvas::repaint);
            settle();
            LabelOverlay o = LabelOverlay.peek(canvas);
            assertNotNull(o, "the overlay exists after painting");
            Bounds bb = adder.getBounds();
            int sx = (int) Math.round((bb.getX() + bb.getWidth() / 2.0) * 2);
            int sy = (int) Math.round((bb.getY() + bb.getHeight() / 2.0) * 2);
            SwingUtilities.invokeAndWait(() -> canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED,
                    System.currentTimeMillis(), 0, sx, sy, 0, false)));
            settle();
            assertSame(adder, o.hovered(), "screen point / zoom = the adder");
            // 확대 전 좌표(나누지 않은 좌표)였다면 가산기 오른쪽 아래 빈 곳이다
            SwingUtilities.invokeAndWait(() -> canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED,
                    System.currentTimeMillis(), 0, sx / 2 - 60, sy / 2 - 60, 0, false)));
            settle();
            assertNull(o.hovered(), "empty space");
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
