/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.influence;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Influence;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 영향 경로 표시(P-01, PLAN.md 11.12). 켜면 나머지를 흐리게 하고, 앞(영향을 줌)은 파랑 띠, 뒤(영향을 받음)는 호박색
 * 띠로 선을 강조하고 닿은 부품을 다시 선명하게 그린다. 시작 부품은 굵은 테두리, 멈춘 상태 부품은 점선 테두리,
 * 안쪽에 닿은 서브회로는 테두리와 "alu: 3 places" 칩, 같은 이름 터널 사이는 점선이다. 선 색(값)은 그대로다.
 * 그릴 때만 적용하고 파일은 바꾸지 않는다. 회로를 고치면 지운다.
 */
public final class InfluenceOverlay {
    static final Color FORWARD = Tokens.BLUE;
    static final Color BACKWARD = Tokens.WARNING;
    static final Color DIM = new Color(255, 255, 255, 170);
    /** 화면 px. */
    static final float BAND_PX = 9f;
    static final float ORIGIN_PX = 3f;
    static final float STOP_PX = 2f;
    static final float LINK_PX = 2f;
    /** Signal Flow가 흐르는 동안 영향 경로의 불투명도. */
    static final float FADED = 0.4f;

    private static final Map<Project, InfluenceOverlay> ALL = Collections.synchronizedMap(new WeakHashMap<>());

    private final Project proj;
    private Circuit top;
    private List<Component> start = Collections.emptyList();
    private Component betweenA;
    private Component betweenB;
    private Influence.Mode mode = Influence.Mode.FORWARD;
    private boolean through;
    /** 보이는 깊이(건넌 부품 수). 음수면 끝까지. */
    private int depth = -1;
    private Influence result;
    private final Map<Circuit, Influence.View> views = new HashMap<>();
    private final Map<Circuit, Long> signature = new HashMap<>();

    private InfluenceOverlay(Project proj) {
        this.proj = proj;
    }

    public static InfluenceOverlay of(Project proj) {
        return ALL.computeIfAbsent(proj, InfluenceOverlay::new);
    }

    public boolean active() {
        return result != null;
    }

    public Influence.Mode mode() {
        return mode;
    }

    public boolean through() {
        return through;
    }

    public int depth() {
        return depth;
    }

    public Influence result() {
        return result;
    }

    /** 부품·선 start에서 mode로 보인다. */
    public void show(Circuit top, List<Component> start, Influence.Mode mode) {
        this.top = top;
        this.start = new ArrayList<>(start);
        this.betweenA = null;
        this.betweenB = null;
        this.mode = mode;
        this.depth = -1;
        compute();
        notice();
    }

    /** 두 부품 사이 경로. */
    public void between(Circuit top, Component a, Component b) {
        this.top = top;
        this.start = new ArrayList<>(List.of(a, b));
        this.betweenA = a;
        this.betweenB = b;
        this.mode = Influence.Mode.FORWARD;
        this.depth = -1;
        compute();
        notice();
    }

    public void setThrough(boolean t) {
        through = t;
        if (active()) {
            compute();
            notice();
        }
    }

    /** 한 단계 넓히거나(+1) 좁힌다(-1). 끝까지 보이던 중이면 가장 깊은 곳에서 시작한다. */
    public void widen(int delta) {
        if (!active()) {
            return;
        }
        int max = full().maxDepth();
        int now = depth < 0 ? max : depth;
        int next = Math.max(1, now + delta);
        depth = next >= max ? -1 : next;
        compute();
        notice();
    }

    public void clear() {
        result = null;
        views.clear();
        signature.clear();
        repaint();
    }

    private Influence full() {
        if (betweenA != null) {
            return Influence.between(top, betweenA, betweenB, through);
        }
        return Influence.of(top, start, mode, through, -1);
    }

    private void compute() {
        if (betweenA != null) {
            result = Influence.between(top, betweenA, betweenB, through);
        } else {
            result = Influence.of(top, start, mode, through, depth);
        }
        views.clear();
        signature.clear();
        signature.put(top, sig(top));
        repaint();
    }

    private void notice() {
        String dir = Messages.get("influence.mode." + (betweenA != null ? "BETWEEN" : mode.name()));
        String steps = depth < 0 ? Messages.get("influence.allSteps") : Messages.get("influence.steps", depth);
        kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, Messages.get("influence.notice", dir, steps,
                through ? Messages.get("influence.through") : ""));
    }

    private void repaint() {
        if (proj.getFrame() != null && proj.getFrame().getCanvas() != null) {
            proj.getFrame().getCanvas().repaint();
        }
    }

    /** 회로 모양이 바뀌었는가(부품·선의 수와 정체). */
    static long sig(Circuit c) {
        long h = c.getWires().size() * 31L + c.getNonWires().size();
        for (Wire w : c.getWires()) {
            h += System.identityHashCode(w);
        }
        for (Component x : c.getNonWires()) {
            h += 7L * System.identityHashCode(x);
        }
        return h;
    }

    /** 이 회로에서의 모습. 보여 줄 것이 없으면 null. 회로를 고쳤으면 지우고 null. */
    public Influence.View viewFor(Circuit shown) {
        if (result == null || shown == null) {
            return null;
        }
        Long before = signature.get(top);
        if (before != null && before != sig(top)) {
            clear();
            return null;
        }
        Long s = signature.get(shown);
        long now = sig(shown);
        if (s != null && s != now) {
            clear();
            return null;
        }
        signature.put(shown, now);
        Influence.View v = views.computeIfAbsent(shown, result::view);
        boolean any = !v.forwardWires.isEmpty() || !v.backwardWires.isEmpty() || !v.forwardParts.isEmpty()
                || !v.backwardParts.isEmpty() || !v.inside.isEmpty() || shown == top;
        return any ? v : null;
    }

    // ---- 그리기 ----

    /** CanvasPainter: 회로·연결점을 그린 뒤, 선택을 그리기 전에 부른다. */
    public static void paint(Canvas canvas, ComponentDrawContext context, Graphics g0, Circuit circ) {
        if (!(g0 instanceof Graphics2D) || canvas.getProject() == null) {
            return;
        }
        InfluenceOverlay o = ALL.get(canvas.getProject());
        if (o == null) {
            return;
        }
        Influence.View v = o.viewFor(circ);
        if (v == null) {
            return;
        }
        double z = canvas.getHcsZoom() == null ? 1.0 : canvas.getHcsZoom().zoomFactor();
        // Signal Flow가 흐르는 동안에는 영향 경로를 옅게(띠·테두리 투명도를 낮추고 흐리게 하기는 그대로) 그린다(D-063)
        Graphics2D g = (Graphics2D) g0;
        float alpha = opacity(canvas);
        if (alpha < 1f) {
            g = (Graphics2D) g0.create();
            g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
        }
        paint(g, context, circ, v, z, canvas);
        if (g != g0) {
            g.dispose();
        }
    }

    /** 영향 경로의 불투명도: Signal Flow가 이 캔버스에서 흐르는 동안은 {@link #FADED}, 아니면 1. */
    public static float opacity(Canvas canvas) {
        return kr.ac.hallym.hcs.app.flow.FlowController.of(canvas).running() ? FADED : 1f;
    }

    /** 뷰 v를 그린다(회로 좌표의 Graphics, 배율 z). context가 null이면 부품은 다시 그리지 않는다(테스트). */
    public static void paint(Graphics2D g0, ComponentDrawContext context, Circuit circ, Influence.View v, double z) {
        paint(g0, context, circ, v, z, null);
    }

    /** canvas가 있으면 다시 그리는 부품의 원조 라벨 글자를 뺀다(라벨 칩이 대신한다, ui-reviewer #246). */
    static void paint(Graphics2D g0, ComponentDrawContext context, Circuit circ, Influence.View v, double z,
            Canvas canvas) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle clip = g.getClipBounds();
            if (clip == null) {
                Bounds b = circ.getBounds();
                clip = new Rectangle(b.getX() - 50, b.getY() - 50, b.getWidth() + 100, b.getHeight() + 100);
            }
            g.setColor(DIM);
            g.fill(clip);
            float band = Math.max(6f, px(BAND_PX, z));
            band(g, v.forwardWires, FORWARD, band);
            band(g, v.backwardWires, BACKWARD, v.forwardWires.isEmpty() ? band : band * 0.55f);
            // 닿은 선과 부품을 다시 선명하게(값 색 그대로)
            List<Wire> wires = new ArrayList<>(v.forwardWires);
            wires.addAll(v.backwardWires);
            List<Component> parts = new ArrayList<>(v.forwardParts);
            parts.addAll(v.backwardParts);
            parts.addAll(v.stops);
            parts.addAll(v.origin);
            parts.addAll(v.inside.keySet());
            parts.addAll(v.tunnels);
            if (context != null) {
                Graphics saved = context.getGraphics();
                for (Wire w : wires) {
                    Graphics2D wg = (Graphics2D) saved.create();
                    context.setGraphics(wg);
                    w.draw(context);
                    wg.dispose();
                }
                // 원조와 같은 그리기 순서로(겹친 부품이 원조처럼 겹치게)
                java.util.Set<Component> want = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                want.addAll(parts);
                for (Component c : circ.getNonWires()) {
                    if (!want.contains(c)) {
                        continue;
                    }
                    Graphics base = canvas == null ? saved
                            : kr.ac.hallym.hcs.app.labels.LabelOverlay.filterOne(canvas, g0, circ, c);
                    Graphics2D cg = (Graphics2D) base.create();
                    context.setGraphics(cg);
                    c.draw(context);
                    cg.dispose();
                }
                context.setGraphics(saved);
            }
            kr.ac.hallym.hcs.app.wiring.WireMarks.paintAt(g, circ, context == null ? null
                    : context.getCircuitState(), wires, z);
            // 같은 이름 터널 사이 점선
            g.setColor(v.backwardWires.isEmpty() || !v.forwardWires.isEmpty() ? FORWARD : BACKWARD);
            float lw = Math.max(1.5f, px(LINK_PX, z));
            g.setStroke(new BasicStroke(lw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                    new float[] {px(6, z), px(5, z)}, 0f));
            if (!v.tunnelLinks.isEmpty()) {
                // 점선은 부품 몸체(터널 이름 포함)와 라벨 칩 위를 지나지 않는다: 그 자리를 잘라 낸다(ui-reviewer #246)
                Graphics2D lg = (Graphics2D) g.create();
                lg.clip(linkArea(circ, clip, canvas, z));
                for (List<Location> link : v.tunnelLinks) {
                    for (int i = 1; i < link.size(); i++) {
                        Location a = link.get(i - 1);
                        Location b = link.get(i);
                        lg.draw(new Line2D.Float(a.getX(), a.getY(), b.getX(), b.getY()));
                    }
                }
                lg.dispose();
            }
            // 닿은 터널: 얇은 테두리(점선 대신, 터널이 셋 이상인 넷)
            for (Component c : v.tunnels) {
                if (circ.contains(c)) {
                    outline(g, c.getBounds(), v.backwardWires.isEmpty() || !v.forwardWires.isEmpty() ? FORWARD
                            : BACKWARD, Math.max(1f, px(1.5f, z)), null, z);
                }
            }
            // 시작, 멈춘 곳, 서브회로 안쪽
            for (Component c : v.origin) {
                if (circ.contains(c) && !(c instanceof Wire)) {
                    outline(g, c.getBounds(), Tokens.NAVY, Math.max(2f, px(ORIGIN_PX, z)), null, z);
                }
            }
            for (Component c : v.stops) {
                outline(g, c.getBounds(), BACKWARD.darker(), Math.max(1.5f, px(STOP_PX, z)),
                        new float[] {px(4, z), px(3, z)}, z);
            }
            for (Map.Entry<Component, Integer> e : v.inside.entrySet()) {
                if (!circ.contains(e.getKey())) {
                    continue;
                }
                outline(g, e.getKey().getBounds(), FORWARD, Math.max(1.5f, px(STOP_PX, z)), null, z);
                if (e.getValue() > 0) {
                    placesChip(g, e.getKey(), e.getValue(), z);
                }
            }
        } finally {
            g.dispose();
        }
    }

    /** 점선이 지나도 되는 곳: 보이는 영역에서 부품 몸체와 라벨 칩을 뺀 곳. */
    static java.awt.geom.Area linkArea(Circuit circ, Rectangle clip, Canvas canvas, double z) {
        java.awt.geom.Area a = new java.awt.geom.Area(clip);
        for (Component c : circ.getNonWires()) {
            Bounds b = c.getBounds();
            a.subtract(new java.awt.geom.Area(new Rectangle(b.getX() - 2, b.getY() - 2, b.getWidth() + 4,
                    b.getHeight() + 4)));
        }
        if (canvas != null) {
            // 라벨 칩 자리는 회로 좌표다(QuickBar도 배율을 곱해 캔버스 좌표로 쓴다)
            for (Rectangle r : kr.ac.hallym.hcs.app.labels.LabelOverlay.chipRects(canvas)) {
                a.subtract(new java.awt.geom.Area(new Rectangle(r.x - 2, r.y - 2, r.width + 4, r.height + 4)));
            }
        }
        return a;
    }

    private static void band(Graphics2D g, java.util.Collection<Wire> wires, Color c, float w) {
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 110));
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Wire x : wires) {
            g.draw(new Line2D.Float(x.getEnd0().getX(), x.getEnd0().getY(), x.getEnd1().getX(), x.getEnd1().getY()));
        }
    }

    private static void outline(Graphics2D g, Bounds b, Color c, float w, float[] dash, double z) {
        float gap = px(4, z);
        g.setColor(c);
        g.setStroke(dash == null ? new BasicStroke(w) : new BasicStroke(w, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND, 1f, dash, 0f));
        g.draw(new RoundRectangle2D.Float(b.getX() - gap, b.getY() - gap, b.getWidth() + 2 * gap,
                b.getHeight() + 2 * gap, px(6, z), px(6, z)));
    }

    /** 서브회로 부품 위(오른쪽 위 바깥)에 "alu: 3 places". 글자는 화면 11px. */
    static String placesText(Component inst, int n) {
        return Messages.get("influence.places", inst.getFactory().getName(), n);
    }

    private static void placesChip(Graphics2D g, Component inst, int n, double z) {
        String text = placesText(inst, n);
        g.setFont(new Font(Tokens.UI_FONT, Font.BOLD, 1).deriveFont(px(11, z)));
        FontMetrics fm = g.getFontMetrics();
        Bounds b = inst.getBounds();
        float padX = px(5, z);
        float padY = px(2, z);
        float w = fm.stringWidth(text) + 2 * padX;
        float h = fm.getAscent() + fm.getDescent() + 2 * padY;
        float x = b.getX() + b.getWidth() - w;
        float y = b.getY() - px(6, z) - h;
        g.setColor(Tokens.WHITE);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, px(6, z), px(6, z)));
        g.setColor(FORWARD);
        g.setStroke(new BasicStroke(Math.max(1f, px(1, z))));
        g.draw(new RoundRectangle2D.Float(x, y, w, h, px(6, z), px(6, z)));
        g.drawString(text, x + padX, y + padY + fm.getAscent());
    }

    static float px(float screen, double zoom) {
        return (float) (screen / (zoom <= 0 ? 1.0 : zoom));
    }
}
