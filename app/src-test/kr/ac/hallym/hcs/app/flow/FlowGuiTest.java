/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.flow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * P-07 GUI(화면 필요: {@code xvfb-run -a ./gradlew :app:guiTest}): Timer는 멈춤 조건마다 선다(Esc, 편집, 탭 전환,
 * 빈 곳 누름). 멈춘 뒤 캔버스는 시작 전과 화소까지 같다(잔상 없음). 한 번 누르면 150ms 뒤 시작하고, 더블클릭은
 * 시작하지 않는다.
 */
@Tag("gui")
class FlowGuiTest {
    @TempDir
    Path tmp;

    Project proj;
    Frame frame;
    Canvas canvas;
    FlowController flow;
    Component a;
    Circuit other;

    void open() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        kr.ac.hallym.hcs.app.gui.GuiTestSupport.keepAlive(); // 창을 닫아도 JVM이 끝나지 않게
        FlowSettings.setReduceMotion(false);
        FlowSettings.setOnClick(true);
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        a = b.add("Wiring", "Pin", 100, 100, "label", "A");
        Component not = b.add("Gates", "NOT Gate", 300, 100);
        b.wire(Location.create(100, 100), not.getEnd(1).getLocation());
        b.add("Wiring", "Pin", 500, 100, "facing", "west", "output", "true", "label", "Y");
        b.wire(not.getEnd(0).getLocation(), Location.create(500, 100));
        b.commit();
        other = new Circuit("other");
        f.addCircuit(other);
        proj = new Project(f);
        proj.getSimulator().setIsRunning(false);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> fr.set(new Frame(proj)));
        frame = fr.get();
        com.cburch.logisim.tools.Library base = f.getLoader().getBuiltin().getLibrary("Base");
        SwingUtilities.invokeAndWait(() -> {
            proj.setCurrentCircuit(f.getMainCircuit());
            proj.setTool(base.getTool("Edit Tool"));
            frame.setVisible(true);
        });
        canvas = frame.getCanvas();
        flow = FlowController.of(canvas);
        Thread.sleep(300);
    }

    @AfterEach
    void close() throws Exception {
        if (frame != null) {
            SwingUtilities.invokeAndWait(() -> {
                flow.stop();
                frame.dispose();
            });
        }
    }

    void startOnA() throws Exception {
        SwingUtilities.invokeAndWait(() -> flow.start(a, -1, false));
        assertTrue(flow.running());
        assertTrue(flow.timerRunning(), "the timer runs while flowing");
    }

    void settle() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(50);
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void timerStopsOnEveryStopCondition() throws Exception {
        open();
        // Esc
        startOnA();
        SwingUtilities.invokeAndWait(() -> canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)));
        settle();
        assertFalse(flow.running() || flow.timerRunning(), "Esc");
        // 회로 편집
        startOnA();
        SwingUtilities.invokeAndWait(() -> {
            CircuitMutation m = new CircuitMutation(proj.getCurrentCircuit());
            m.add(Wire.create(Location.create(100, 300), Location.create(200, 300)));
            proj.doAction(m.toAction(() -> "edit"));
        });
        settle();
        assertFalse(flow.running() || flow.timerRunning(), "an edit");
        // 탭·서브회로 전환
        startOnA();
        SwingUtilities.invokeAndWait(() -> proj.setCurrentCircuit(other));
        settle();
        assertFalse(flow.running() || flow.timerRunning(), "switching the circuit");
        SwingUtilities.invokeAndWait(() -> proj.setCurrentCircuit(proj.getLogisimFile().getMainCircuit()));
        // 빈 곳 누름
        startOnA();
        SwingUtilities.invokeAndWait(() -> flow.clickAt(Location.create(700, 500), false));
        settle();
        assertFalse(flow.running() || flow.timerRunning(), "a click on an empty spot");
    }

    static int[] pixels(Canvas c) throws Exception {
        AtomicReference<int[]> out = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage img = new BufferedImage(Math.min(800, c.getWidth()), Math.min(500, c.getHeight()),
                    BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            c.paint(g);
            g.dispose();
            out.set(img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth()));
        });
        return out.get();
    }

    @Test
    void stoppingLeavesNoTrace() throws Exception {
        open();
        int[] before = pixels(canvas);
        startOnA();
        SwingUtilities.invokeAndWait(() -> flow.freeze(120.0));
        int[] during = pixels(canvas);
        boolean differs = false;
        for (int i = 0; i < before.length && !differs; i++) {
            differs = before[i] != during[i];
        }
        assertTrue(differs, "the flow is drawn");
        SwingUtilities.invokeAndWait(() -> {
            flow.freeze(null);
            flow.stop();
        });
        settle();
        assertArrayEquals(before, pixels(canvas), "after stopping the canvas is exactly as before");
    }

    void mouse(int id, int x, int y, int count) throws Exception {
        SwingUtilities.invokeAndWait(() -> canvas.dispatchEvent(new MouseEvent(canvas, id, System.currentTimeMillis(),
                id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK, x, y, count, false,
                MouseEvent.BUTTON1)));
    }

    /** 배율을 바꾸면 라벨 칩이 흐름보다 나중에 자리를 정한다: 멈춘 흐름도 새 칩 자리를 피하도록 다시 그린다. */
    @Test
    void flowLabelsFollowTheLabelChipsAfterZooming() throws Exception {
        open();
        startOnA();
        SwingUtilities.invokeAndWait(() -> flow.freeze(10_000.0));
        for (double z : new double[] {0.25, 1.0, 4.0}) {
            SwingUtilities.invokeAndWait(() -> canvas.getHcsZoom().zoomTo(z));
            Thread.sleep(400);
            settle();
            assertTrue(flow.avoidsCurrentChips(), "the last flow paint avoided the current chips at " + z);
        }
    }

    /** 200%에서 누른 화면 자리(원조 캔버스가 회로 좌표로 바꿔 준다)의 선에서 시작한다. */
    @Test
    void clickStartsOnTheWireUnderTheMouseWhenZoomed() throws Exception {
        open();
        SwingUtilities.invokeAndWait(() -> canvas.getHcsZoom().zoomTo(2.0));
        settle();
        mouse(MouseEvent.MOUSE_PRESSED, 400, 200, 1);
        mouse(MouseEvent.MOUSE_RELEASED, 400, 200, 1);
        Thread.sleep(FlowController.CLICK_DELAY + 250);
        settle();
        assertTrue(flow.running(), "the wire at (200, 100) under the mouse at 200%");
    }

    @Test
    void oneClickStartsAfterTheDelayButADoubleClickDoesNot() throws Exception {
        open();
        double z = canvas.getHcsZoom() == null ? 1 : canvas.getHcsZoom().zoomFactor();
        int x = (int) Math.round(200 * z);
        int y = (int) Math.round(100 * z);
        mouse(MouseEvent.MOUSE_PRESSED, x, y, 1);
        mouse(MouseEvent.MOUSE_RELEASED, x, y, 1);
        assertFalse(flow.running(), "not yet: waits for a second click");
        Thread.sleep(FlowController.CLICK_DELAY + 250);
        settle();
        assertTrue(flow.running(), "a single click on a wire starts the flow");
        SwingUtilities.invokeAndWait(flow::stop);
        // 더블클릭: 두 번째 누름이 150ms 안에 온다
        mouse(MouseEvent.MOUSE_PRESSED, x, y, 1);
        mouse(MouseEvent.MOUSE_RELEASED, x, y, 1);
        mouse(MouseEvent.MOUSE_PRESSED, x, y, 2);
        mouse(MouseEvent.MOUSE_RELEASED, x, y, 2);
        Thread.sleep(FlowController.CLICK_DELAY + 250);
        settle();
        assertFalse(flow.running(), "a double click does not start the flow");
    }

    @Test
    void reduceMotionRunsNoTimer() throws Exception {
        open();
        FlowSettings.setReduceMotion(true);
        try {
            SwingUtilities.invokeAndWait(() -> flow.start(a, -1, false));
            assertTrue(flow.running());
            assertFalse(flow.timerRunning(), "static arrows and numbers: no timer, CPU stays idle");
        } finally {
            FlowSettings.setReduceMotion(false);
        }
    }

    /** 캔버스가 가려지면(창을 숨기거나 최소화) Timer가 서고, 다시 보이면 이어서 흐른다. */
    @Test
    void hiddenCanvasPausesTheTimer() throws Exception {
        open();
        startOnA();
        SwingUtilities.invokeAndWait(() -> frame.setVisible(false));
        settle();
        assertTrue(flow.running(), "still showing the flow");
        assertFalse(flow.timerRunning(), "paused while hidden: no CPU for frames nobody sees");
        SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
        Thread.sleep(300);
        settle();
        assertTrue(flow.timerRunning(), "resumes when shown again");
    }

    /** 흐르는 동안 영향 경로는 옅게(40%), 멈추면 다시 그대로(D-063). */
    @Test
    void influenceFadesWhileTheFlowRuns() throws Exception {
        open();
        assertTrue(kr.ac.hallym.hcs.app.influence.InfluenceOverlay.opacity(canvas) == 1f);
        startOnA();
        assertTrue(kr.ac.hallym.hcs.app.influence.InfluenceOverlay.opacity(canvas) < 0.5f, "faded while flowing");
        SwingUtilities.invokeAndWait(flow::stop);
        assertTrue(kr.ac.hallym.hcs.app.influence.InfluenceOverlay.opacity(canvas) == 1f);
    }

    /** Ctrl+Shift+F와 툴바 토글: 설정이 바뀌고, 끄면 흐르던 것이 멈추고, 툴바 단추가 따라온다. */
    @Test
    void toggleOnClickFromTheKeyAndTheToolbar() throws Exception {
        open();
        javax.swing.JToggleButton button = findToggle(frame.getContentPane());
        if (button == null) {
            button = findToggle(frame.getRootPane());
        }
        assertTrue(button != null, "toolbar toggle");
        assertTrue(button.isSelected() && FlowSettings.onClick());
        startOnA();
        SwingUtilities.invokeAndWait(() -> canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_F,
                KeyEvent.CHAR_UNDEFINED)));
        settle();
        assertFalse(FlowSettings.onClick(), "Ctrl+Shift+F turns it off");
        assertFalse(flow.running(), "turning it off stops the flow");
        assertFalse(button.isSelected(), "the toolbar toggle follows");
        javax.swing.JToggleButton b = button;
        SwingUtilities.invokeAndWait(b::doClick);
        settle();
        assertTrue(FlowSettings.onClick() && b.isSelected(), "the toolbar toggle turns it back on");
    }

    static javax.swing.JToggleButton findToggle(java.awt.Container root) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.JToggleButton && kr.ac.hallym.hcs.app.Messages.get("bar.flow").equals(
                    ((javax.swing.JToggleButton) c).getText())) {
                return (javax.swing.JToggleButton) c;
            }
            if (c instanceof java.awt.Container) {
                javax.swing.JToggleButton t = findToggle((java.awt.Container) c);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }
}
