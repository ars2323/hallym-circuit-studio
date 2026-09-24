/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.zoom;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.generic.CanvasPane;
import com.cburch.logisim.gui.generic.ZoomModel;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;

/**
 * 캔버스 확대·축소와 이동(#69, PLAN.md 11.2). Ctrl+휠은 커서 중심, Ctrl+= / Ctrl+- 한 단계, Ctrl+0 전체 맞춤,
 * Ctrl+1 100%, F 선택 부분 맞춤. 스페이스+끌기와 가운데 버튼 끌기로 이동, 휠·Shift+휠로 상하·좌우 스크롤.
 * 배율은 그리기에만 쓰고 .circ에 저장하지 않는다(원조 2.7.1도 앱 환경설정에만 둔다).
 */
public final class ZoomController {
    static final int FIT_MARGIN = 20;

    private final Project proj;
    private final Canvas canvas;
    private final CanvasPane pane;
    private final ZoomModel model;
    /** 레이아웃 편집 화면일 때만 단축키가 동작한다(모양 편집 화면은 제 배율이 따로 있다). */
    private final java.util.function.BooleanSupplier active;
    private boolean spaceDown;
    private Point dragFrom;
    private Point viewFrom;
    private Cursor savedCursor;

    private ZoomController(Project proj, Canvas canvas, CanvasPane pane, ZoomModel model,
            java.util.function.BooleanSupplier active) {
        this.proj = proj;
        this.canvas = canvas;
        this.pane = pane;
        this.model = model;
        this.active = active;
    }

    /** 창(Frame)을 만들 때 한 번. */
    public static ZoomController install(Project proj, Canvas canvas, CanvasPane pane, ZoomModel model,
            JComponent keyRoot, java.util.function.BooleanSupplier layoutShown) {
        ZoomController z = new ZoomController(proj, canvas, pane, model, layoutShown);
        z.bindKeys(keyRoot);
        z.bindMouse();
        return z;
    }

    private void bindKeys(JComponent root) {
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        bind(im, am, "hcsZoomIn", () -> zoomCentered(ZoomMath.stepIn(model.getZoomFactor())),
                KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, menu), KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, menu),
                KeyStroke.getKeyStroke(KeyEvent.VK_ADD, menu),
                KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, menu | InputEvent.SHIFT_DOWN_MASK));
        bind(im, am, "hcsZoomOut", () -> zoomCentered(ZoomMath.stepOut(model.getZoomFactor())),
                KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, menu), KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, menu));
        bind(im, am, "hcsZoomFit", this::fitCircuit, KeyStroke.getKeyStroke(KeyEvent.VK_0, menu),
                KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, menu));
        bind(im, am, "hcsZoom100", () -> zoomCentered(1.0), KeyStroke.getKeyStroke(KeyEvent.VK_1, menu),
                KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD1, menu));
        // F는 글자 입력(라벨 편집 등)과 겹치지 않도록 캔버스에 초점이 있을 때만
        canvas.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "hcsZoomSel");
        canvas.getActionMap().put("hcsZoomSel", action(this::fitSelection));
    }

    private void bind(InputMap im, ActionMap am, String name, Runnable r, KeyStroke... keys) {
        for (KeyStroke k : keys) {
            im.put(k, name);
        }
        am.put(name, action(() -> {
            if (active.getAsBoolean()) {
                r.run();
            }
        }));
    }

    private static AbstractAction action(Runnable r) {
        return new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        };
    }

    private void bindMouse() {
        // 휠: Ctrl이면 확대, Shift면 좌우, 그 밖은 상하 스크롤
        pane.setWheelScrollingEnabled(false);
        pane.addMouseWheelListener(this::wheel);
        canvas.addMouseWheelListener(this::wheel);

        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE && !spaceDown && e.getModifiersEx() == 0
                        && !(proj.getTool() instanceof com.cburch.logisim.tools.TextTool)) { // 라벨 입력 중에는 공백
                    spaceDown = true;
                    savedCursor = canvas.getCursor();
                    canvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    e.consume();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE && spaceDown) {
                    spaceDown = false;
                    canvas.setCursor(savedCursor);
                    e.consume();
                }
            }
        });
    }

    /** 이동 끌기 중인가. 캔버스의 도구 처리는 이때 마우스를 무시해야 한다. */
    public boolean isPanning(MouseEvent e) {
        return spaceDown || SwingUtilities.isMiddleMouseButton(e) || dragFrom != null;
    }

    /** 캔버스 마우스 처리 앞에서 부른다. 이동으로 쓴 이벤트면 true. */
    public boolean handlePan(MouseEvent e) {
        switch (e.getID()) {
        case MouseEvent.MOUSE_PRESSED:
            if (spaceDown || SwingUtilities.isMiddleMouseButton(e)) {
                dragFrom = SwingUtilities.convertPoint(canvas, e.getPoint(), pane);
                viewFrom = pane.getViewport().getViewPosition();
                return true;
            }
            return false;
        case MouseEvent.MOUSE_DRAGGED:
            if (dragFrom != null) {
                Point now = SwingUtilities.convertPoint(canvas, e.getPoint(), pane);
                setView(new Point(viewFrom.x - (now.x - dragFrom.x), viewFrom.y - (now.y - dragFrom.y)));
                return true;
            }
            return false;
        case MouseEvent.MOUSE_RELEASED:
            if (dragFrom != null) {
                dragFrom = null;
                return true;
            }
            return false;
        default:
            return dragFrom != null;
        }
    }

    private void wheel(MouseWheelEvent e) {
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        if ((e.getModifiersEx() & (menu == InputEvent.META_MASK ? InputEvent.META_DOWN_MASK
                : InputEvent.CTRL_DOWN_MASK)) != 0) {
            double z = model.getZoomFactor();
            double nz = e.getWheelRotation() < 0 ? ZoomMath.stepIn(z) : ZoomMath.stepOut(z);
            Point inView = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), pane.getViewport());
            zoomAt(nz, inView);
        } else {
            boolean horizontal = e.isShiftDown();
            JScrollBar bar = horizontal ? pane.getHorizontalScrollBar() : pane.getVerticalScrollBar();
            int unit = pane.supportScrollableUnitIncrement(pane.getViewport().getViewRect(),
                    horizontal ? javax.swing.SwingConstants.HORIZONTAL : javax.swing.SwingConstants.VERTICAL,
                    e.getWheelRotation());
            bar.setValue(bar.getValue() + e.getUnitsToScroll() * unit);
        }
        e.consume();
    }

    /** 보이는 영역 안의 점 inView를 고정한 채 배율을 바꾼다. */
    public void zoomAt(double newZoom, Point inView) {
        double old = model.getZoomFactor();
        newZoom = ZoomMath.clamp(newZoom);
        if (newZoom == old) {
            return;
        }
        Point view = pane.getViewport().getViewPosition();
        model.setZoomFactor(newZoom);
        pane.getViewport().validate();
        pane.validate();
        setView(ZoomMath.anchor(view, inView, old, newZoom));
    }

    private void zoomCentered(double newZoom) {
        Rectangle r = pane.getViewport().getViewRect();
        zoomAt(newZoom, new Point(r.width / 2, r.height / 2));
    }

    public void fitCircuit() {
        Bounds b = proj.getCurrentCircuit() == null ? null : proj.getCurrentCircuit().getBounds();
        fit(b);
    }

    void fitSelection() {
        Bounds b = canvas.getSelection().getBounds();
        fit(b == null || b.getWidth() <= 0 ? null : b);
    }

    private void fit(Bounds b) {
        if (b == null || b == Bounds.EMPTY_BOUNDS) {
            return;
        }
        Rectangle r = new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight());
        JViewport vp = pane.getViewport();
        double z = ZoomMath.fit(r, vp.getWidth(), vp.getHeight(), FIT_MARGIN);
        model.setZoomFactor(z);
        pane.validate();
        setView(ZoomMath.center(r, vp.getWidth(), vp.getHeight(), z));
    }

    private void setView(Point p) {
        JViewport vp = pane.getViewport();
        java.awt.Dimension ext = vp.getViewSize();
        java.awt.Dimension size = vp.getExtentSize();
        int x = Math.max(0, Math.min(p.x, ext.width - size.width));
        int y = Math.max(0, Math.min(p.y, ext.height - size.height));
        vp.setViewPosition(new Point(x, y));
    }
}
