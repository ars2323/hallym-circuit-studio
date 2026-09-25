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
        this.switchListener = e -> {
            if (e.getAction() == com.cburch.logisim.proj.ProjectEvent.ACTION_SET_CURRENT) {
                canvas.setHcsOrigin(0, 0);
                settled(); // 스크롤 자리가 그대로면 뷰포트 알림이 없다: 원점 0을 바로 기억한다
            }
        };
    }

    /** 창(Frame)을 만들 때 한 번. */
    public static ZoomController install(Project proj, Canvas canvas, CanvasPane pane, ZoomModel model,
            JComponent keyRoot, java.util.function.BooleanSupplier layoutShown) {
        ZoomController z = new ZoomController(proj, canvas, pane, model, layoutShown);
        z.bindKeys(keyRoot);
        z.bindMouse();
        // 원점 이동(S-10)은 회로를 바꾸면 없앤다(편집 중에는 그대로라 그림이 움직이지 않는다). 원조 배율 조절로 배율이
        // 바뀌면 보던 가운데를 새 배율에서 다시 가운데에 둔다(zoomedElsewhere)
        model.addPropertyChangeListener(ZoomModel.ZOOM, z::zoomedElsewhere);
        pane.getViewport().addChangeListener(e -> z.remember());
        proj.addProjectListener(z.switchListener); // 원조 Project는 청취자를 약하게 잡는다: 필드로 붙잡아 둔다
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

    /** 회로를 바꾸면 원점 이동을 없앤다(원조 Project는 청취자를 약하게 잡으므로 필드로 붙잡아 둔다). */
    private final com.cburch.logisim.proj.ProjectListener switchListener;

    /** 우리 코드(커서 배율, 화면 맞춤)가 배율을 바꾸는 중: 원조 배율 조절 처리를 건너뛴다. */
    private boolean adjusting;
    /** 마지막 스크롤 자리와 원점 이동(배율이 바뀌기 전 보던 가운데를 알기 위해). */
    private Point lastView = new Point();
    private int lastOx;
    private int lastOy;

    /** lastView를 잰 배율. 배율이 바뀐 뒤 원조 CanvasPane이 옮긴 자리는 기억하지 않는다. */
    private double lastZoom = Double.NaN;

    private void remember() {
        double z = model.getZoomFactor();
        if (!Double.isNaN(lastZoom) && z != lastZoom) {
            return; // 배율이 막 바뀌었다: 바뀌기 전 자리를 그대로 둔다
        }
        lastZoom = z;
        lastView = pane.getViewport().getViewPosition();
        lastOx = canvas.getHcsOriginX();
        lastOy = canvas.getHcsOriginY();
    }

    /**
     * 원조 배율 조절이 배율을 바꿨다. 원조 CanvasPane은 원점 이동을 모르고, 스크롤 막대 범위가 새 크기로 바뀌기 전에
     * 값을 넣어 가운데가 어긋날 수 있다. 바뀌기 전에 보던 가운데(원점을 뺀 회로 좌표)를 새 배율에서 다시 가운데에
     * 둔다. 모자라는 만큼은 원점 이동으로(커서 배율과 같은 규칙).
     */
    private void zoomedElsewhere(java.beans.PropertyChangeEvent e) {
        if (adjusting) {
            return;
        }
        double oldZ = ((Number) e.getOldValue()).doubleValue();
        double newZ = ((Number) e.getNewValue()).doubleValue();
        Rectangle r = pane.getViewport().getViewRect();
        double cx = (lastView.x + r.width / 2.0 - lastOx) / oldZ;
        double cy = (lastView.y + r.height / 2.0 - lastOy) / oldZ;
        int vx = (int) Math.round(cx * newZ - r.width / 2.0);
        int vy = (int) Math.round(cy * newZ - r.height / 2.0);
        placeOrigin(vx, vy);
        pane.validate();
        setView(new Point(Math.max(0, vx), Math.max(0, vy)));
        settled();
    }

    /** 배율 변경을 다 처리했다: 지금 자리를 기억한다. */
    private void settled() {
        lastZoom = model.getZoomFactor();
        remember();
    }

    /** 캔버스 마우스 처리 앞에서 부른다. 이동으로 쓴 이벤트면 true. */
    public boolean handlePan(MouseEvent e) {
        switch (e.getID()) {
        // 캔버스는 마우스 좌표를 배율로 나눠 넘기므로(원조 Canvas.zoomEvent) 이동량은 화면 좌표로 잰다
        case MouseEvent.MOUSE_PRESSED:
            if (spaceDown || SwingUtilities.isMiddleMouseButton(e)) {
                dragFrom = e.getLocationOnScreen();
                viewFrom = pane.getViewport().getViewPosition();
                return true;
            }
            return false;
        case MouseEvent.MOUSE_DRAGGED:
            if (dragFrom != null) {
                setView(panTarget(viewFrom, dragFrom, e.getLocationOnScreen()));
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

    /** 끌어 이동: 보이는 영역이 손을 따라 움직인다(화면 좌표 기준, 배율과 무관). */
    static Point panTarget(Point viewFrom, Point dragFromScreen, Point nowScreen) {
        return new Point(viewFrom.x - (nowScreen.x - dragFromScreen.x), viewFrom.y - (nowScreen.y - dragFromScreen.y));
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

    /** 배율 모델(테스트: 원조 배율 조절처럼 바로 바꾼다). */
    ZoomModel model() {
        return model;
    }

    /** 지금 배율. */
    public double zoomFactor() {
        return model.getZoomFactor();
    }

    /** 보이는 영역 안의 점 inView를 고정한 채 배율을 바꾼다. */
    public void zoomAt(double newZoom, Point inView) {
        double old = model.getZoomFactor();
        newZoom = ZoomMath.clamp(newZoom);
        if (newZoom == old) {
            return;
        }
        Point view = pane.getViewport().getViewPosition();
        // 원점 이동(S-10)은 "0보다 작은 스크롤"이다: 옮긴 만큼 뺀 가상 스크롤로 커서 아래 점을 고정하고, 새 가상
        // 스크롤이 0보다 작으면 그만큼을 다시 원점 이동으로 둔다(맨 위·왼쪽 가까이에서도 커서 아래 점이 그대로)
        view = new Point(view.x - canvas.getHcsOriginX(), view.y - canvas.getHcsOriginY());
        Point virtual = ZoomMath.anchor(view, inView, old, newZoom);
        adjusting = true;
        try {
            model.setZoomFactor(newZoom);
        } finally {
            adjusting = false;
        }
        // 커서 아래 점을 지키는 것이 먼저라 가운데 한도(placeOrigin)는 두지 않는다: 빈 띠는 커서 바로 옆에만 생긴다
        canvas.setHcsOrigin(Math.max(0, -virtual.x), Math.max(0, -virtual.y));
        pane.getViewport().validate();
        pane.validate();
        setView(new Point(Math.max(0, virtual.x), Math.max(0, virtual.y)));
        settled();
    }

    /** 보이는 영역 가운데를 고정한 채 배율을 바꾼다(상태 표시줄 배율 단추). */
    public void zoomTo(double newZoom) {
        zoomCentered(newZoom);
    }

    private void zoomCentered(double newZoom) {
        Rectangle r = pane.getViewport().getViewRect();
        zoomAt(newZoom, new Point(r.width / 2, r.height / 2));
    }

    public void fitCircuit() {
        fit(contentBounds());
    }

    /** 지금 회로 영역. 라벨 칩(부품 밖에 붙은 이름)도 넣는다(S-10). 빈 회로면 null. */
    private Bounds contentBounds() {
        Bounds b = proj.getCurrentCircuit() == null ? null : proj.getCurrentCircuit().getBounds();
        if (b == null || b == Bounds.EMPTY_BOUNDS) {
            return null;
        }
        for (Rectangle r : kr.ac.hallym.hcs.app.labels.LabelOverlay.chipRects(canvas)) {
            b = b.add(Bounds.create(r.x, r.y, r.width, r.height));
        }
        return b;
    }

    /**
     * 가상 스크롤(vx, vy)이 0보다 작은 만큼을 원점 이동으로 둔다. 다만 회로 영역을 가운데 두는 만큼까지만 옮긴다
     * (S-10 후속: 원조 배율 조절로 작게 봤다가 다시 키웠을 때, 넓어진 회로 왼쪽·위에 빈 띠가 남지 않게).
     */
    private void placeOrigin(int vx, int vy) {
        Bounds b = contentBounds();
        JViewport vp = pane.getViewport();
        double z = model.getZoomFactor();
        int capX = b == null ? 0 : ZoomMath.originCap(b.getX(), b.getWidth(), vp.getWidth(), z);
        int capY = b == null ? 0 : ZoomMath.originCap(b.getY(), b.getHeight(), vp.getHeight(), z);
        canvas.setHcsOrigin(Math.min(capX, Math.max(0, -vx)), Math.min(capY, Math.max(0, -vy)));
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
        canvas.setHcsOrigin(0, 0);
        adjusting = true;
        try {
            model.setZoomFactor(z);
        } finally {
            adjusting = false;
        }
        // 가로·세로 모두 가운데(S-10, 체크리스트 11): 작은 축은 원점을 옮긴다
        int[] at = ZoomMath.fitPlacement(r, vp.getWidth(), vp.getHeight(), z);
        canvas.setHcsOrigin(at[0], at[1]);
        pane.validate();
        setView(new Point(at[2], at[3]));
        settled();
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
