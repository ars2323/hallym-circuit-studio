/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;

/**
 * 원조 부품의 포트 이름(S-06). 원조 기본 부품은 몇 포트에 작은 이름을 부품 안쪽에 그린다(Adder의 "c in"·"c out",
 * Register의 "en"·"0", RAM의 "A"·"D" 등, {@code InstancePainter.drawPort(i, label, dir)}). 좁은 부품에서는 이 글자가
 * 기호·값과 겹친다. 편집 캔버스에서만 이 그리기 문맥을 써서, 포트 점은 원조대로 그리고 이름은 <b>마우스를 올렸을 때나
 * 200% 이상에서만</b> 부품 <b>바깥</b> 포트 옆(선이 나가는 방향을 비켜 선 위쪽·오른쪽)에 그린다. 인쇄·그림 내보내기,
 * 우리 MIPS 부품, 서브회로는 원조 그대로다. 엔진 코드는 그대로다(그리기 문맥은 GUI가 만든다).
 */
public final class PortLabels extends ComponentDrawContext {
    /** 이 배율 이상이면 늘 보인다. */
    static final double SHOW_ZOOM = 2.0;
    /** 이름 글자 크기(회로 좌표). 200%에서 화면 18px. */
    static final float FONT_PX = 9f;
    /** 마우스를 올린 낮은 배율에서도 이 화면 크기보다 작게 그리지 않는다. */
    static final float MIN_SCREEN_PX = 10f;

    private final double zoom;
    private final Component hovered;

    private PortLabels(java.awt.Component canvas, Circuit circuit, CircuitState state, Graphics base, Graphics g,
            boolean printerView, double zoom, Component hovered) {
        super(canvas, circuit, state, base, g, printerView);
        this.zoom = zoom;
        this.hovered = hovered;
    }

    /** CanvasPainter가 원조 문맥 대신 쓴다. */
    public static ComponentDrawContext context(Canvas canvas, Circuit circuit, CircuitState state, Graphics base,
            Graphics g, boolean printerView) {
        double z = canvas.getHcsZoom() == null ? 1.0 : canvas.getHcsZoom().zoomFactor();
        LabelOverlay o = LabelOverlay.peek(canvas);
        return new PortLabels(canvas, circuit, state, base, g, printerView, z, o == null ? null : o.hovered());
    }

    /** 테스트: 캔버스 없이 배율과 마우스 아래 부품을 준다. */
    static PortLabels forTest(Circuit circuit, CircuitState state, Graphics g, double zoom, Component hovered) {
        return new PortLabels(null, circuit, state, g, g, false, zoom, hovered);
    }

    /** 원조 기본 부품(com.cburch.logisim.std.*)인가. 우리 MIPS 부품은 포트 이름을 스스로 알맞게 그린다. */
    static boolean original(Component c) {
        return c.getFactory().getClass().getName().startsWith("com.cburch.logisim.std.");
    }

    static boolean visible(Component c, double zoom, Component hovered) {
        return zoom >= SHOW_ZOOM || c == hovered;
    }

    @Override
    public void drawPin(Component comp, int i, String label, Direction dir) {
        if (!original(comp) || label == null || label.isEmpty()) {
            super.drawPin(comp, i, label, dir);
            return;
        }
        if (i < 0 || i >= comp.getEnds().size()) {
            return;
        }
        super.drawPin(comp, i); // 포트 점(값 색)은 원조대로
        if (visible(comp, zoom, hovered)) {
            Graphics g = getGraphics();
            Font old = g.getFont();
            // 낮은 배율에서 마우스를 올렸을 때도 화면에서 읽히는 크기(최소 10px)
            g.setFont(old.deriveFont((float) Math.max(FONT_PX, MIN_SCREEN_PX / zoom)));
            int[] at = outside(comp, i, label, g.getFontMetrics(), Math.max(3, 3 / zoom));
            g.drawString(label, at[0], at[1]);
            g.setFont(old);
        }
    }

    /**
     * 부품 바깥, 포트 옆 이름 자리(기준선 왼쪽 끝). 선은 포트에서 변과 직각으로 나가므로 그 옆으로 비킨다: 왼쪽 변이면
     * 포트 왼쪽 위, 오른쪽 변이면 오른쪽 위, 윗변이면 위쪽 오른쪽, 아랫변이면 아래쪽 오른쪽.
     */
    static int[] outside(Component comp, int i, String label, FontMetrics fm) {
        return outside(comp, i, label, fm, 3);
    }

    /**
     * gap: 포트·선과 띄울 거리(회로 좌표). 왼쪽·오른쪽 변의 포트는 부품 가운데에서 먼 쪽(위쪽 절반이면 위, 아래쪽
     * 절반이면 아래)에 둔다: 같은 변의 다른 포트 선에 붙어 어느 포트의 이름인지 헷갈리지 않게(S-06 검토).
     */
    static int[] outside(Component comp, int i, String label, FontMetrics fm, double gap0) {
        Bounds b = comp.getBounds();
        Location p = comp.getEnd(i).getLocation();
        int w = fm.stringWidth(label);
        int gap = (int) Math.round(gap0);
        boolean upper = p.getY() < b.getY() + b.getHeight() / 2.0;
        int sideY = upper ? p.getY() - gap : p.getY() + gap + fm.getAscent();
        if (p.getX() <= b.getX() + LabelOverlay.SIDE) {
            return new int[] {p.getX() - gap - w, sideY};
        } else if (p.getX() >= b.getX() + b.getWidth() - LabelOverlay.SIDE) {
            return new int[] {p.getX() + gap, sideY};
        } else if (p.getY() <= b.getY() + LabelOverlay.SIDE) {
            return new int[] {p.getX() + gap, p.getY() - gap};
        }
        return new int[] {p.getX() + gap, p.getY() + gap + fm.getAscent()};
    }
}
