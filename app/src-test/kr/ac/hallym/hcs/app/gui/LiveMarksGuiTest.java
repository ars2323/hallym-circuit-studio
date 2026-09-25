/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
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

import kr.ac.hallym.hcs.app.diag.Diagnostic;
import kr.ac.hallym.hcs.app.diag.Diagnostics;
import kr.ac.hallym.hcs.app.theme.Tokens;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * S-29 회귀({@code xvfb-run -a ./gradlew :app:guiTest}): 진단 표시는 실제 캔버스에 늘 보인다. Messages에서 누르기
 * 전에도, 누른 뒤(굵은 테두리)에도, 선택을 푼 뒤에도, 25%와 400%에서도 원인 부품 둘레에 진단 색이 그려진다.
 */
@Tag("gui")
class LiveMarksGuiTest {
    @TempDir
    Path tmp;

    static void settle() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(200);
        SwingUtilities.invokeAndWait(() -> { });
    }

    static boolean near(int rgb, Color c) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return Math.abs(r - c.getRed()) + Math.abs(g - c.getGreen()) + Math.abs(b - c.getBlue()) <= 40;
    }

    /** 캔버스를 그려 부품 화면 사각형(여유 pad) 안의 진단 색 픽셀 수. */
    static int markPixels(Canvas canvas, Component c, int pad) throws Exception {
        AtomicReference<Integer> n = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage img = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            canvas.paint(g);
            g.dispose();
            Bounds b = c.getBounds();
            Rectangle r = canvas.hcsToScreen(new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
            r.grow(pad, pad);
            r = r.intersection(new Rectangle(0, 0, img.getWidth(), img.getHeight()));
            int count = 0;
            for (int y = r.y; y < r.y + r.height; y++) {
                for (int x = r.x; x < r.x + r.width; x++) {
                    if (near(img.getRGB(x, y), Tokens.ERROR)) {
                        count++;
                    }
                }
            }
            n.set(count);
        });
        return n.get();
    }

    @Test
    void marksStayVisibleOnTheCanvas() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        // 클럭이 떠 있는 레지스터: 동작할 수 없는 연결(CLOCK_UNCONNECTED)
        Component reg = b.add("Memory", "Register", 300, 200, "width", "8", "label", "PC");
        b.input("d", 8, 100, 100);
        b.output("q", 8, 500, 100);
        b.tunnel(reg, 1, "d");
        b.tunnel(reg, 0, "q");
        b.commit();
        Project proj = new Project(f);
        proj.getSimulator().setIsRunning(false);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Frame frame = new Frame(proj);
            proj.setFrame(frame);
            frame.setVisible(true);
            frame.setBounds(0, 0, 1400, 900);
            frame.validate();
            proj.setTool(f.getLoader().getBuiltin().getLibrary("Base").getTool("Edit Tool"));
            fr.set(frame);
        });
        Frame frame = fr.get();
        try {
            settle();
            Canvas canvas = frame.getCanvas();
            SwingUtilities.invokeAndWait(() -> Diagnostics.of(proj).refresh());
            Diagnostics diags = Diagnostics.of(proj);
            Diagnostic d = diags.list().get(0);
            assertEquals(Diagnostic.Kind.CLOCK_UNCONNECTED, d.kind);
            SwingUtilities.invokeAndWait(() -> canvas.getHcsZoom().zoomTo(1.0));
            settle();

            int before = markPixels(canvas, reg, 12);
            assertTrue(before > 20, "a mark before anything is clicked: " + before);

            SwingUtilities.invokeAndWait(() -> diags.go(d));
            settle();
            int focused = markPixels(canvas, reg, 12);
            assertTrue(focused > before, "the clicked cause gets the thicker border: " + focused + " vs " + before);

            SwingUtilities.invokeAndWait(() -> proj.doAction(com.cburch.logisim.gui.main.SelectionActions.dropAll(
                    proj.getSelection())));
            settle();
            int after = markPixels(canvas, reg, 12);
            assertTrue(after > 20, "still marked after the selection is cleared: " + after);

            for (double z : new double[] {0.25, 4.0}) {
                SwingUtilities.invokeAndWait(() -> {
                    canvas.getHcsZoom().zoomTo(z);
                });
                settle();
                // 부품이 보이게 옮긴다
                SwingUtilities.invokeAndWait(() -> {
                    Bounds rb = reg.getBounds();
                    Rectangle r = canvas.hcsToScreen(new Rectangle(rb.getX(), rb.getY(), rb.getWidth(),
                            rb.getHeight()));
                    r.grow(40, 40);
                    canvas.scrollRectToVisible(r);
                });
                settle();
                int at = markPixels(canvas, reg, 12);
                assertTrue(at > 10, "marked at " + Math.round(z * 100) + "%: " + at);
            }
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
