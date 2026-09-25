/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.zoom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * S-10 GUI({@code xvfb-run -a ./gradlew :app:guiTest}): 화면 맞춤은 가로로 긴 회로를 가로·세로 모두 가운데 둔다(작은
 * 축은 캔버스 원점을 옮긴다). 옮긴 원점에서도 누른 자리의 부품을 고르고, 커서 자리에서 배율을 바꾸면 원점 이동은
 * 없어지고 커서 아래 점은 그대로다.
 */
@Tag("gui")
class FitCenterGuiTest {
    @TempDir
    Path tmp;

    static void settle() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(200);
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void fitCentersAWideCircuitAndKeepsClicksRight() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        kr.ac.hallym.hcs.app.gui.GuiTestSupport.keepAlive();
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component left = b.add("Gates", "AND Gate", 100, 100, "inputs", "2");
        b.add("Gates", "OR Gate", 1600, 140, "inputs", "2");
        b.commit();
        Circuit main = f.getMainCircuit();
        Project proj = new Project(f);
        proj.getSimulator().setIsRunning(false);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> fr.set(new Frame(proj)));
        Frame frame = fr.get();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame.setSize(1400, 900);
                frame.setVisible(true);
                proj.setTool(f.getLoader().getBuiltin().getLibrary("Base").getTool("Edit Tool"));
            });
            settle();
            Canvas canvas = frame.getCanvas();
            javax.swing.JViewport vp = (javax.swing.JViewport) canvas.getParent();
            // 창 배치가 끝난 뒤(도구 모음·Messages 칸이 자리를 잡은 뒤) 맞춘다: 사용자가 누르는 때와 같다.
            // 파일 탭은 새 창을 앞 창 자리·크기로 두므로(앞 테스트의 작은 창) 보인 뒤 크기를 다시 정한다
            Thread.sleep(500);
            SwingUtilities.invokeAndWait(() -> {
                frame.setBounds(0, 0, 1400, 900);
                frame.validate();
            });
            settle();
            SwingUtilities.invokeAndWait(() -> canvas.getHcsZoom().fitCircuit());
            settle();
            // 회로 영역이 보이는 영역 가운데(가로·세로)
            Bounds cb = main.getBounds();
            Rectangle on = canvas.hcsToScreen(new Rectangle(cb.getX(), cb.getY(), cb.getWidth(), cb.getHeight()));
            Rectangle view = vp.getViewRect();
            double left0 = on.x - view.x;
            double right0 = view.x + view.width - (on.x + on.width);
            double top0 = on.y - view.y;
            double bottom0 = view.y + view.height - (on.y + on.height);
            String why = "z=" + canvas.getHcsZoom().zoomFactor() + " view=" + view + " on=" + on + " origin="
                    + canvas.getHcsOriginX() + "," + canvas.getHcsOriginY() + " chips="
                    + kr.ac.hallym.hcs.app.labels.LabelOverlay.chipRects(canvas) + " ext=" + vp.getViewSize()
                    + " bounds=" + cb;
            assertEquals(left0, right0, 3, "horizontally centered: " + why);
            assertEquals(top0, bottom0, 3, "vertically centered");
            assertTrue(canvas.getHcsOriginY() > 0, "a wide circuit: the origin moved down");

            // 옮긴 원점에서 누른 자리의 부품을 고른다
            Bounds lb = left.getBounds();
            Rectangle ls = canvas.hcsToScreen(new Rectangle(lb.getX(), lb.getY(), lb.getWidth(), lb.getHeight()));
            int sx = ls.x + ls.width / 2;
            int sy = ls.y + ls.height / 2;
            for (int id : new int[] {MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED}) {
                SwingUtilities.invokeAndWait(() -> canvas.dispatchEvent(new MouseEvent(canvas, id,
                        System.currentTimeMillis(), id == MouseEvent.MOUSE_PRESSED ? InputEvent.BUTTON1_DOWN_MASK : 0,
                        sx, sy, 1, false, MouseEvent.BUTTON1)));
            }
            settle();
            assertTrue(proj.getSelection().contains(left), "the click selects the gate under the mouse");

            // 원조 배율 조절(ZoomModel을 바로 바꿈)도 보던 가운데를 그대로 둔다(모자라면 원점 이동으로)
            java.util.function.Supplier<com.cburch.logisim.data.Location> mid = () -> {
                Rectangle v = vp.getViewRect();
                return canvas.hcsToCircuit(v.x + v.width / 2, v.y + v.height / 2);
            };
            com.cburch.logisim.data.Location before = mid.get();
            double z0 = canvas.getHcsZoom().zoomFactor();
            SwingUtilities.invokeAndWait(() -> canvas.getHcsZoom().model().setZoomFactor(z0 * 1.5));
            settle();
            com.cburch.logisim.data.Location after0 = mid.get();
            assertEquals(before.getX(), after0.getX(), 4 / z0, "same point in the middle (x)");
            assertEquals(before.getY(), after0.getY(), 4 / z0, "same point in the middle (y)");
            SwingUtilities.invokeAndWait(() -> canvas.getHcsZoom().fitCircuit());
            settle();
            // 커서 자리에서 두 배로: 커서 아래 점은 그대로(맨 위 가까이라 원점 이동이 그 몫을 맡는다)
            Point inView = new Point(sx - vp.getViewPosition().x, sy - vp.getViewPosition().y);
            SwingUtilities.invokeAndWait(() -> canvas.getHcsZoom().zoomAt(canvas.getHcsZoom().zoomFactor() * 2, inView));
            settle();
            Rectangle after = canvas.hcsToScreen(new Rectangle(lb.getX(), lb.getY(), lb.getWidth(), lb.getHeight()));
            Point now = vp.getViewPosition();
            assertEquals(inView.x, after.x + after.width / 2 - now.x, 3, "the point under the cursor stays (x)");
            assertEquals(inView.y, after.y + after.height / 2 - now.y, 3, "the point under the cursor stays (y)");

            // 회로를 바꾸면 원점 이동이 없어지고, 그 뒤 원조 배율 조절도 옛 원점을 쓰지 않는다
            Circuit other = new Circuit("other");
            SwingUtilities.invokeAndWait(() -> {
                f.addCircuit(other);
                proj.setCurrentCircuit(other);
            });
            settle();
            assertEquals(0, canvas.getHcsOriginX() + canvas.getHcsOriginY(), "switching circuits drops the offset");
            double z1 = canvas.getHcsZoom().zoomFactor();
            SwingUtilities.invokeAndWait(() -> canvas.getHcsZoom().model().setZoomFactor(z1 * 1.25));
            settle();
            // 빈 회로라 캔버스가 보이는 영역 크기뿐이다(가운데를 옮길 곳이 없다). 옛 원점을 기억해 쓰면 원점 이동이
            // 생기므로 0이어야 한다
            assertEquals(0, canvas.getHcsOriginX() + canvas.getHcsOriginY(), "no offset from the old circuit");
            SwingUtilities.invokeAndWait(() -> {
                proj.setCurrentCircuit(main);
                canvas.getHcsZoom().fitCircuit();
            });
            settle();

            // S-10 후속(P-07 검토 18o): 왼쪽 게이트를 크게 본 뒤 원조 배율 조절로 100%로 돌아오면, 보던 가운데를
            // 지키려는 원점 이동은 회로를 가운데 두는 만큼까지만이다. 100%의 이 회로는 보이는 영역보다 넓으므로
            // 왼쪽에 빈 띠가 없다(원점 0)
            Rectangle ls2 = canvas.hcsToScreen(new Rectangle(lb.getX(), lb.getY(), lb.getWidth(), lb.getHeight()));
            Point at = new Point(ls2.x + ls2.width / 2 - vp.getViewPosition().x,
                    ls2.y + ls2.height / 2 - vp.getViewPosition().y);
            SwingUtilities.invokeAndWait(() -> canvas.getHcsZoom().zoomAt(4.0, at));
            settle();
            SwingUtilities.invokeAndWait(() -> canvas.getHcsZoom().model().setZoomFactor(1.0));
            settle();
            Bounds all = main.getBounds();
            assertTrue(all.getWidth() > vp.getWidth(), "wider than the view at 100%: " + all + " " + vp.getSize());
            assertEquals(0, canvas.getHcsOriginX(), "no blank strip left of a circuit wider than the view");
            int capY = ZoomMath.originCap(all.getY(), all.getHeight(), vp.getHeight(), 1.0);
            for (Rectangle r : kr.ac.hallym.hcs.app.labels.LabelOverlay.chipRects(canvas)) {
                capY = Math.max(capY, ZoomMath.originCap(Math.min(all.getY(), r.y), Math.max(all.getY()
                        + all.getHeight(), r.y + r.height) - Math.min(all.getY(), r.y), vp.getHeight(), 1.0));
            }
            assertTrue(canvas.getHcsOriginY() <= capY, "at most the centering amount (y): " + canvas.getHcsOriginY()
                    + " > " + capY);
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
