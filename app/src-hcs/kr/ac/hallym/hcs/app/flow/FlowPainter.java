/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.flow;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * Signal Flow 한 장면 그리기(P-07). 시각 t(회로 좌표 거리)에 앞단이 닿은 곳까지 선 위로 신호 방향의 광택 대시를
 * 그리고, 앞단이 모든 끝점에 닿은 뒤에는 경로 전체에서 대시가 계속 흐른다. 선 본래의 값 색은 대시 사이로 그대로
 * 보인다(선 색을 바꾸지 않는다). 강조색은 디자인 토큰의 accent(TEAL). GUI 상태 없이 그림만 그린다(테스트가 같은
 * 함수로 화소를 본다).
 */
public final class FlowPainter {
    /** accent. */
    static final Color ACCENT = Tokens.TEAL;
    static final Color HALO = new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 80);
    static final Color HALO_NEXT = new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 60);
    /** 선 옆 띠(선 밖이라 조금 진하게). */
    static final Color HALO_RAIL = new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 130);
    static final Color DASH = new Color(255, 255, 255, 235);
    /** 화면 px: 대시, 빈칸, 앞단 뒤 짧은 꼬리. */
    static final float DASH_PX = 8f;
    static final float GAP_PX = 10f;
    static final float HALO_PX = 4f;
    static final float RING_PX = 6f;
    /** 끝점 라벨을 링에서 떨어뜨려 보는 거리(화면 px, 가까운 것부터). */
    static final double[] LABEL_FAR = {0, 12, 28, 48, 72, 104};
    /** 끝점 라벨과 다른 라벨 칩 사이 최소 간격(화면 px). */
    static final double LABEL_GAP = 4;
    /** 부품 외곽선이 빛나는 거리(회로 단위). */
    static final double GLOW = 60;

    private FlowPainter() {
    }

    /** 이 회로에서 보일 것만: 맨 위 회로면 경로 없는 것, 서브회로면 그 회로의 모든 인스턴스. */
    static boolean inView(SignalFlowPath p, Circuit shown, Circuit circuit, List<Component> instances) {
        return circuit == shown && (shown != p.top || instances.isEmpty());
    }

    /** 보이는 모든 것을 감싸는 회로 좌표 상자(프레임마다 이 상자만 다시 그린다). 없으면 null. */
    public static Rectangle bounds(SignalFlowPath p, Circuit shown) {
        return bounds(p, shown, 1.0);
    }

    /**
     * 배율 z에서의 상자. 끝점 라벨 칩은 화면 크기가 일정하므로 배율이 낮을수록 회로 좌표로는 커지고, 링에서 가장 멀리
     * 놓일 수 있는 자리({@link #LABEL_FAR})까지 어느 쪽으로든 간다.
     */
    public static Rectangle bounds(SignalFlowPath p, Circuit shown, double z) {
        Rectangle2D.Double r = null;
        for (SignalFlowPath.Segment s : p.segments) {
            if (inView(p, shown, s.circuit, s.instances)) {
                r = add(r, s.from);
                r = add(r, s.to);
            }
        }
        for (SignalFlowPath.Jump j : p.jumps) {
            if (inView(p, shown, j.circuit, j.instances)) {
                r = add(r, j.from);
                r = add(r, j.to);
                // 호는 위·아래·옆 어느 쪽으로든 부풀 수 있다(V-06): 가장 큰 후보만큼 넓힌다
                int rise = 2 * arcRise(j);
                int mx = (j.from.getX() + j.to.getX()) / 2;
                int my = (j.from.getY() + j.to.getY()) / 2;
                r = add(r, Location.create(mx, Math.min(j.from.getY(), j.to.getY()) - rise));
                r = add(r, Location.create(mx, Math.max(j.from.getY(), j.to.getY()) + rise));
                r = add(r, Location.create(Math.min(j.from.getX(), j.to.getX()) - rise, my));
                r = add(r, Location.create(Math.max(j.from.getX(), j.to.getX()) + rise, my));
            }
        }
        for (SignalFlowPath.Pass x : p.passes) {
            if (inView(p, shown, x.circuit, x.instances)) {
                Bounds b = x.component.getBounds();
                r = add(r, Location.create(b.getX() - 30, b.getY() - 40));
                r = add(r, Location.create(b.getX() + b.getWidth() + 30, b.getY() + b.getHeight() + 10));
            }
        }
        for (SignalFlowPath.Endpoint e : p.endpoints) {
            if (inView(p, shown, e.circuit, e.instances)) {
                // 라벨 칩: 링 + 가장 먼 자리 + 칩 크기(글자 폭은 넉넉히)
                int w = (int) Math.ceil(px(RING_PX + 3 + LABEL_FAR[LABEL_FAR.length - 1] + 20 + 8 * e.label.length(),
                        z));
                int h = (int) Math.ceil(px(RING_PX + 3 + LABEL_FAR[LABEL_FAR.length - 1] + 24, z));
                r = add(r, Location.create(e.at.getX() - w, e.at.getY() - h));
                r = add(r, Location.create(e.at.getX() + w, e.at.getY() + h));
            }
        }
        if (p.click != null && shown == p.top) {
            r = add(r, p.click);
        }
        if (r == null) {
            return null;
        }
        Rectangle out = r.getBounds();
        out.grow(24, 24);
        return out;
    }

    private static Rectangle2D.Double add(Rectangle2D.Double r, Location l) {
        if (r == null) {
            return new Rectangle2D.Double(l.getX(), l.getY(), 0, 0);
        }
        r.add(l.getX(), l.getY());
        return r;
    }

    static int arcRise(SignalFlowPath.Jump j) {
        int d = Math.abs(j.from.getX() - j.to.getX()) + Math.abs(j.from.getY() - j.to.getY());
        return Math.max(10, Math.min(60, d / 4));
    }

    /**
     * 시각 t에서의 장면. z는 배율(화면 px = 회로 × z). reduceMotion이면 움직임 없이 방향 화살표와 순서 번호만.
     * tunnelColor는 터널 색 팔레트(없으면 accent).
     */
    public static void paint(Graphics2D g0, SignalFlowPath p, Circuit shown, double t, double z, boolean reduceMotion,
            java.util.function.Function<Location, Color> tunnelColor) {
        paint(g0, p, shown, t, z, reduceMotion, tunnelColor, null);
    }

    /** obstacles: 끝점 라벨이 피할 곳(라벨 칩 등, 회로 좌표). 부품 몸체는 여기서 더한다. */
    public static void paint(Graphics2D g0, SignalFlowPath p, Circuit shown, double t, double z, boolean reduceMotion,
            java.util.function.Function<Location, Color> tunnelColor, List<Rectangle> obstacles) {
        // 앞단이 모두 닿은 뒤(연속 흐름)에는 움직이는 대시 말고는 그대로다: 그 부분을 한 번 그림으로 만들어 두고
        // 프레임마다 그림 + 대시만 그린다(프레임 4ms 기준, CI 기계)
        if (!reduceMotion && t >= p.total) {
            Layer layer = layer(g0, p, shown, z, tunnelColor, obstacles);
            if (layer != null) {
                g0.drawImage(layer.image, new java.awt.geom.AffineTransform(1 / layer.scale, 0, 0, 1 / layer.scale,
                        layer.box.x, layer.box.y), null);
                paintParts(g0, p, shown, t, z, false, tunnelColor, obstacles, false, true);
                return;
            }
        }
        paintParts(g0, p, shown, t, z, reduceMotion, tunnelColor, obstacles, true, true);
    }

    /** 연속 흐름의 멈춘 부분(띠, 호, 링, 라벨, 칩, 경계 테두리)을 그린 그림. */
    static final class Layer {
        Object key;
        java.awt.image.BufferedImage image;
        Rectangle box;
        double scale;
    }

    /** 그림이 너무 크면(높은 배율에 큰 회로) 만들지 않고 그때그때 그린다. */
    static final long LAYER_MAX_PIXELS = 8_000_000L;

    private static final Map<SignalFlowPath, Layer> LAYERS = java.util.Collections.synchronizedMap(
            new java.util.WeakHashMap<>());

    static Layer layer(Graphics2D g0, SignalFlowPath p, Circuit shown, double z,
            java.util.function.Function<Location, Color> tunnelColor, List<Rectangle> obstacles) {
        double sc = deviceScale(g0);
        Rectangle box = bounds(p, shown, z);
        if (box == null) {
            return null;
        }
        if ((long) (box.width * sc) * (long) (box.height * sc) > LAYER_MAX_PIXELS) {
            // 큰 회로: 지금 보이는 영역(그리는 영역)만 그림으로 만든다(스크롤하면 다시 만든다)
            Rectangle clip = g0.getClipBounds();
            if (clip == null) {
                return null;
            }
            box = box.intersection(clip);
            if (box.isEmpty() || (long) (box.width * sc) * (long) (box.height * sc) > LAYER_MAX_PIXELS) {
                return null;
            }
        }
        Object key = java.util.Arrays.asList(shown, Math.round(sc * 1000), Math.round(z * 1000),
                obstacles == null ? 0 : obstacles.hashCode(), box);
        Layer hit = LAYERS.get(p);
        if (hit != null && hit.key.equals(key)) {
            return hit;
        }
        Layer l = new Layer();
        l.key = key;
        l.box = box;
        l.scale = sc;
        l.image = new java.awt.image.BufferedImage(Math.max(1, (int) Math.ceil(box.width * sc)),
                Math.max(1, (int) Math.ceil(box.height * sc)), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D ig = l.image.createGraphics();
        ig.scale(sc, sc);
        ig.translate(-box.x, -box.y);
        ig.setClip(box);
        paintParts(ig, p, shown, p.total + 1, z, false, tunnelColor, obstacles, true, false);
        ig.dispose();
        LAYERS.put(p, l);
        return l;
    }

    /** 그리기 본체. drawStatic: 띠·호·링·라벨 등, drawDashes: 움직이는 대시. */
    static void paintParts(Graphics2D g0, SignalFlowPath p, Circuit shown, double t, double z, boolean reduceMotion,
            java.util.function.Function<Location, Color> tunnelColor, List<Rectangle> obstacles, boolean drawStatic,
            boolean drawDashes) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            Graphics2D main = g;
            // 선 위 띠와 대시는 가로·세로 직선이라 안티에일리어싱 없이 또렷하고 빠르다(프레임 4ms 기준)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
            double time = reduceMotion ? Double.MAX_VALUE / 4 : t;
            boolean looping = time >= p.total;
            // 1. 띠(halo): 앞단이 지난 부분의 선 양옆에 가는 띠 두 줄(선 자체의 값 색은 덮지 않는다,
            //    ui-reviewer P-07). 가로·세로 직선이라 사각형으로 채운다(선 긋기보다 빠르다, 프레임 4ms 기준).
            // 2. 대시: 선 굵기 안에서 신호 방향으로 흐르는 흰 조각. 위치를 셈해 사각형으로 한 번에 채운다.
            Path2D.Double now = new Path2D.Double();
            Path2D.Double next = new Path2D.Double();
            Path2D.Double dashes = new Path2D.Double();
            double railW = px(HALO_PX / 2, z);
            double off = Wire.WIDTH / 2.0;
            double period = px(DASH_PX + GAP_PX, z);
            for (SignalFlowPath.Segment s : p.segments) {
                if (!inView(p, shown, s.circuit, s.instances)) {
                    continue;
                }
                double lit = Math.min(s.length, time - s.start);
                if (lit <= 0) {
                    continue;
                }
                boolean vertical = s.from.getX() == s.to.getX();
                double sign = vertical ? Math.signum(s.to.getY() - s.from.getY())
                        : Math.signum(s.to.getX() - s.from.getX());
                Path2D.Double into = s.cycle > 0 ? next : now;
                // 선 양옆 띠
                if (drawStatic) {
                    rail(into, s.from, vertical, sign, 0, lit, off, railW);
                    rail(into, s.from, vertical, sign, 0, lit, -off - railW, railW);
                }
                if (!reduceMotion && drawDashes) {
                    double w = Math.min(Wire.WIDTH - 0.6, px(dashWidth(s.width), z));
                    double dash = px(s.cycle > 0 ? DASH_PX / 2 : DASH_PX, z);
                    // 켜짐: (time − 거리) mod period ∈ [0, dash). 거리 = s.start + a(선 위 자리)
                    double first = mod(time - s.start, period); // a = 0에서의 위상
                    // 위상이 dash보다 작으면 a=0부터 켜져 있다
                    for (double a0 = first - period; a0 < lit; a0 += period) {
                        double lo = Math.max(0, a0 - dash);
                        double hi = Math.min(lit, a0);
                        // (time − s.start − a) mod period < dash ⇔ a ∈ (a0 − dash, a0], a0 = first + k·period
                        if (hi > lo) {
                            rail(dashes, s.from, vertical, sign, lo, hi, -w / 2, w);
                        }
                    }
                }
            }
            g.setColor(HALO_RAIL);
            g.fill(now);
            // 다음 사이클 경로는 더 옅은 띠(점선 흐름은 짧은 대시가 보인다)
            g.setColor(HALO_NEXT);
            g.fill(next);
            g.setColor(DASH);
            g.fill(dashes);
            if (!drawStatic) {
                return;
            }
            // 3. 터널 점프: 두 터널을 잇는 점선 호(색마다 한 경로). 부품 몸체·터널 이름·라벨 칩 위는 잘라 낸다.
            //    잘라 낼 영역은 경로마다 한 번 만들고, 프레임마다 한 번만 잘라 호와 링을 모두 그 안에 그린다.
            Graphics2D cg = (Graphics2D) g.create();
            java.awt.geom.Area free = arcAreaCached(p, shown, g, obstacles);
            cg.clip(free);
            // 호의 모양은 부품 몸체·라벨 칩·끝점 칩을 가장 적게 가리는 후보로 한 번 정한다(V-06)
            Map<SignalFlowPath.Jump, double[]> shapes = arcShapesCached(p, shown, g, z, obstacles);
            Map<Color, Path2D.Double> arcs = new LinkedHashMap<>();
            for (SignalFlowPath.Jump j : p.jumps) {
                if (!inView(p, shown, j.circuit, j.instances) || time < j.start) {
                    continue;
                }
                double f = Math.min(1, (time - j.start) / SignalFlowPath.JUMP);
                Color c = tunnelColor != null ? tunnelColor.apply(j.from) : null;
                arcs.computeIfAbsent(c != null ? c : ACCENT, k -> new Path2D.Double()).append(arc(j, f, shapes.get(j)),
                        false);
            }
            cg.setStroke(arcStroke(z));
            for (Map.Entry<Color, Path2D.Double> e : arcs.entrySet()) {
                cg.setColor(e.getKey());
                cg.draw(e.getValue());
            }
            // 피할 수 없어 부품 몸체·칩 위를 지나는 구간은 옅게(V-06)
            if (!arcs.isEmpty()) {
                Graphics2D fg = (Graphics2D) g.create();
                Rectangle clip = g.getClipBounds();
                java.awt.geom.Area covered = new java.awt.geom.Area(clip != null ? clip
                        : new Rectangle(-100000, -100000, 200000, 200000));
                covered.subtract(free);
                fg.clip(covered);
                fg.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, FAINT_ALPHA));
                fg.setStroke(arcStroke(z));
                for (Map.Entry<Color, Path2D.Double> e : arcs.entrySet()) {
                    fg.setColor(e.getKey());
                    fg.draw(e.getValue());
                }
                fg.dispose();
            }
            // 4. 부품을 지날 때 외곽선이 잠깐 빛난다(서브회로 경계 포함)
            Map<Component, Integer> inside = new LinkedHashMap<>();
            for (SignalFlowPath.Pass x : p.passes) {
                if (shown == p.top && !x.instances.isEmpty() && time >= x.time) {
                    inside.merge(x.instances.get(0), 1, Integer::sum);
                }
                if (!inView(p, shown, x.circuit, x.instances)) {
                    continue;
                }
                double since = time - x.time;
                if (since < 0 || (!reduceMotion && !looping && since > GLOW && !x.boundary)) {
                    continue;
                }
                int alpha = reduceMotion || x.boundary && since >= 0 ? 160
                        : (int) (200 * Math.max(0, 1 - since / GLOW));
                if (alpha <= 0 || looping && !x.boundary && !reduceMotion) {
                    continue;
                }
                outline(g, x.component.getBounds(), new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(),
                        alpha), z);
            }
            for (Map.Entry<Component, Integer> e : inside.entrySet()) {
                if (shown.contains(e.getKey())) {
                    chip(g, e.getKey().getBounds(), places(e.getKey().getFactory().getName(), e.getValue()), z);
                }
            }
            // 5. 끝점: 링과 짧은 라벨(자리는 부품·라벨 칩·다른 끝점 라벨을 피해 한 번 정한다)
            Map<SignalFlowPath.Endpoint, double[]> places = layout(g, p, shown, z, obstacles);
            // 지시선은 다른 끝점 칩 위도 지나지 않는다(칩 글자를 가리지 않게)
            Graphics2D leaders = (Graphics2D) cg.create();
            if (!places.isEmpty()) {
                leaders.setClip(leaderArea(g, places, z, leaders.getClip()));
            }
            for (SignalFlowPath.Endpoint e : p.endpoints) {
                if (!inView(p, shown, e.circuit, e.instances) || time < e.time) {
                    continue;
                }
                // 링은 부품 몸체·터널 이름·라벨 칩 밖에서만 보인다(포트 글자·값 표시를 가리지 않는다)
                ring(cg, e.at, z, e.kind == SignalFlowPath.EndKind.UNCONNECTED ? RING_PX * 0.6f : RING_PX);
                double[] at = places.get(e);
                if (at != null) {
                    java.awt.image.BufferedImage img = chipImage(g, e.label, false, z);
                    double sc = deviceScale(g);
                    double w = img.getWidth() / sc;
                    double h = img.getHeight() / sc;
                    // 링에서 떨어져 놓였으면 가는 지시선으로 잇는다
                    double cx = Math.max(at[0], Math.min(e.at.getX(), at[0] + w));
                    double cy = Math.max(at[1], Math.min(e.at.getY(), at[1] + h));
                    double gap = Math.hypot(cx - e.at.getX(), cy - e.at.getY());
                    if (gap > ringRadius(RING_PX, z) + px(8, z)) {
                        // 지시선도 링·호처럼 부품 몸체와 라벨 칩 위에는 그리지 않는다
                        leaders.setColor(ACCENT);
                        leaders.setStroke(new BasicStroke((float) px(1, z)));
                        double ux = (cx - e.at.getX()) / gap;
                        double uy = (cy - e.at.getY()) / gap;
                        double rr = ringRadius(RING_PX, z);
                        leaders.draw(new Line2D.Double(e.at.getX() + ux * rr, e.at.getY() + uy * rr,
                                cx, cy));
                    }
                    drawChip(g, img, at[0], at[1], sc);
                }
            }
            leaders.dispose();
            cg.dispose();
            // 6. 누른 점
            if (p.click != null && shown == p.top) {
                float r = (float) px(4, z);
                g.setColor(Tokens.NAVY);
                g.fill(new Ellipse2D.Float(p.click.getX() - r, p.click.getY() - r, 2 * r, 2 * r));
            }
            // 7. 고리 닫힘, 선택 미확정
            for (Component c : p.loops) {
                if (shown.contains(c)) {
                    badge(g, c.getBounds(), "↻", z);
                }
            }
            for (Component c : p.undetermined) {
                if (shown.contains(c)) {
                    badge(g, c.getBounds(), "?", z);
                }
            }
            // 8. Reduce Motion: 구간 가운데 방향 화살표, 부품 순서 번호
            if (reduceMotion) {
                g.setColor(ACCENT.darker());
                for (SignalFlowPath.Segment s : p.segments) {
                    if (inView(p, shown, s.circuit, s.instances) && s.length >= 20) {
                        arrow(g, s, z);
                    }
                }
                int n = 1;
                for (SignalFlowPath.Pass x : p.passes) {
                    if (inView(p, shown, x.circuit, x.instances) && !x.boundary) {
                        number(g, x.component.getBounds(), n++, z);
                    }
                }
            }
        } finally {
            g.dispose();
        }
    }

    /** 선 위 [a, b] 구간(from에서 잰 거리)을 선 방향과 직각으로 offset만큼 옮긴 폭 w의 사각형. */
    static void rail(Path2D.Double into, Location from, boolean vertical, double sign, double a, double b,
            double offset, double w) {
        if (vertical) {
            double y0 = from.getY() + sign * a;
            double y1 = from.getY() + sign * b;
            into.append(new Rectangle2D.Double(from.getX() + offset, Math.min(y0, y1), w, Math.abs(y1 - y0)), false);
        } else {
            double x0 = from.getX() + sign * a;
            double x1 = from.getX() + sign * b;
            into.append(new Rectangle2D.Double(Math.min(x0, x1), from.getY() + offset, Math.abs(x1 - x0), w), false);
        }
    }

    /** 버스는 폭에 따라 조금 굵게(화면 px, 선 굵기를 넘지 않는다). */
    static double dashWidth(int bits) {
        return Math.min(2.4, 2.0 + 0.08 * (Math.log(Math.max(1, bits)) / Math.log(2)));
    }

    /** from에서 to로 가는 선 위 [a, b] 구간(from에서 잰 거리). a > b면 거꾸로 그린다. */
    static Line2D line(Location from, Location to, double a, double b) {
        double len = Math.abs(from.getX() - to.getX()) + Math.abs(from.getY() - to.getY());
        if (len == 0) {
            return new Line2D.Double(from.getX(), from.getY(), to.getX(), to.getY());
        }
        double dx = (to.getX() - from.getX()) / len;
        double dy = (to.getY() - from.getY()) / len;
        return new Line2D.Double(from.getX() + dx * a, from.getY() + dy * a, from.getX() + dx * b,
                from.getY() + dy * b);
    }

    /** 터널 점프 호의 앞부분 [0, f](de Casteljau로 정확히 자른다). 기본 모양은 위로 부푼 호. */
    static QuadCurve2D arc(SignalFlowPath.Jump j, double f) {
        return arc(j, f, control(j, 0));
    }

    /** 호의 조절점 후보(V-06, 결정적 순서): 위, 아래, 왼쪽, 오른쪽, 그리고 각각 두 배 높이. */
    static final int CANDIDATES = 8;
    /** 부품 몸체 위를 지나는 구간의 불투명도. */
    static final float FAINT_ALPHA = 0.3f;

    static double[] control(SignalFlowPath.Jump j, int candidate) {
        double mx = (j.from.getX() + j.to.getX()) / 2.0;
        double my = (j.from.getY() + j.to.getY()) / 2.0;
        int rise = arcRise(j) * (candidate >= 4 ? 2 : 1);
        switch (candidate % 4) {
        case 0:
            return new double[] {mx, Math.min(j.from.getY(), j.to.getY()) - rise};
        case 1:
            return new double[] {mx, Math.max(j.from.getY(), j.to.getY()) + rise};
        case 2:
            return new double[] {Math.min(j.from.getX(), j.to.getX()) - rise, my};
        default:
            return new double[] {Math.max(j.from.getX(), j.to.getX()) + rise, my};
        }
    }

    /**
     * 후보 중 부품 몸체(점프 양끝 터널 제외)·라벨 칩·끝점 칩을 가장 적게 가리는 조절점. 가리는 정도는 호 위 표본점 수로
     * 재고, 같으면 앞 후보(위로 부푼 기본 모양)를 고른다.
     */
    static double[] chooseControl(SignalFlowPath.Jump j, List<Rectangle2D> blockers) {
        double[] best = null;
        int bestHits = Integer.MAX_VALUE;
        for (int c = 0; c < CANDIDATES; c++) {
            double[] ctrl = control(j, c);
            int hits = 0;
            for (int i = 1; i < 40; i++) {
                double u = i / 40.0;
                double x = (1 - u) * (1 - u) * j.from.getX() + 2 * (1 - u) * u * ctrl[0] + u * u * j.to.getX();
                double y = (1 - u) * (1 - u) * j.from.getY() + 2 * (1 - u) * u * ctrl[1] + u * u * j.to.getY();
                for (Rectangle2D r : blockers) {
                    if (r.contains(x, y)) {
                        hits++;
                        break;
                    }
                }
            }
            if (hits < bestHits) {
                bestHits = hits;
                best = ctrl;
            }
            if (hits == 0) {
                break;
            }
        }
        return best;
    }

    /** 호가 피할 것: 보이는 회로의 부품 몸체(점프 양끝 터널 제외), 라벨 칩, 끝점 라벨 칩. */
    static List<Rectangle2D> blockers(SignalFlowPath.Jump j, Circuit shown, List<Rectangle> obstacles,
            java.util.Collection<Rectangle2D> chips) {
        List<Rectangle2D> out = new ArrayList<>();
        for (Component c : shown.getNonWires()) {
            Bounds b = c.getBounds();
            Rectangle r = new Rectangle(b.getX() - 2, b.getY() - 2, b.getWidth() + 4, b.getHeight() + 4);
            if (r.contains(j.from.getX(), j.from.getY()) || r.contains(j.to.getX(), j.to.getY())) {
                continue; // 양끝 터널 몸체는 어느 후보나 스친다
            }
            out.add(r);
        }
        if (obstacles != null) {
            for (Rectangle r : obstacles) {
                out.add(new Rectangle(r.x - 2, r.y - 2, r.width + 4, r.height + 4));
            }
        }
        if (chips != null) {
            out.addAll(chips);
        }
        return out;
    }

    /** 경로의 점프마다 고른 조절점(경로·회로·장애물마다 한 번). */
    static Map<SignalFlowPath.Jump, double[]> arcShapes(SignalFlowPath p, Circuit shown, Graphics2D g, double z,
            List<Rectangle> obstacles) {
        Map<SignalFlowPath.Endpoint, double[]> places = layout(g, p, shown, z, obstacles);
        List<Rectangle2D> chips = new ArrayList<>();
        double s = deviceScale(g);
        for (Map.Entry<SignalFlowPath.Endpoint, double[]> e : places.entrySet()) {
            java.awt.image.BufferedImage ci = chipImage(g, e.getKey().label, false, z);
            chips.add(new Rectangle2D.Double(e.getValue()[0], e.getValue()[1], ci.getWidth() / s, ci.getHeight() / s));
        }
        Map<SignalFlowPath.Jump, double[]> out = new java.util.HashMap<>();
        for (SignalFlowPath.Jump j : p.jumps) {
            if (inView(p, shown, j.circuit, j.instances)) {
                out.put(j, chooseControl(j, blockers(j, shown, obstacles, chips)));
            }
        }
        return out;
    }

    private static final Map<SignalFlowPath, Object[]> ARC_SHAPES = java.util.Collections.synchronizedMap(
            new java.util.WeakHashMap<>());

    static Map<SignalFlowPath.Jump, double[]> arcShapesCached(SignalFlowPath p, Circuit shown, Graphics2D g, double z,
            List<Rectangle> obstacles) {
        Object key = java.util.Arrays.asList(shown, Math.round(z * 1000), obstacles == null ? 0 : obstacles.hashCode());
        Object[] hit = ARC_SHAPES.get(p);
        if (hit != null && hit[0].equals(key)) {
            @SuppressWarnings("unchecked")
            Map<SignalFlowPath.Jump, double[]> m = (Map<SignalFlowPath.Jump, double[]>) hit[1];
            return m;
        }
        Map<SignalFlowPath.Jump, double[]> m = arcShapes(p, shown, g, z, obstacles);
        ARC_SHAPES.put(p, new Object[] {key, m});
        return m;
    }

    static QuadCurve2D arc(SignalFlowPath.Jump j, double f, double[] ctrl) {
        if (ctrl == null) {
            ctrl = control(j, 0);
        }
        double x0 = j.from.getX();
        double y0 = j.from.getY();
        double x1 = ctrl[0];
        double y1 = ctrl[1];
        double x2 = j.to.getX();
        double y2 = j.to.getY();
        double u = Math.max(0, Math.min(1, f));
        double cx = x0 + (x1 - x0) * u;
        double cy = y0 + (y1 - y0) * u;
        double ex = (1 - u) * (1 - u) * x0 + 2 * (1 - u) * u * x1 + u * u * x2;
        double ey = (1 - u) * (1 - u) * y0 + 2 * (1 - u) * u * y1 + u * u * y2;
        return new QuadCurve2D.Double(x0, y0, cx, cy, ex, ey);
    }

    static void outline(Graphics2D g, Bounds b, Color c, double z) {
        float gap = (float) px(3, z);
        g.setColor(c);
        g.setStroke(new BasicStroke((float) px(2.5f, z)));
        g.draw(new RoundRectangle2D.Float(b.getX() - gap, b.getY() - gap, b.getWidth() + 2 * gap,
                b.getHeight() + 2 * gap, (float) px(6, z), (float) px(6, z)));
    }

    /**
     * 링 반지름(회로 좌표). 100% 이하에서는 화면 radiusPx 그대로이고, 확대하면 선(버스 굵기 4)과 레일이 굵어지므로
     * 그 밖으로 나오게 조금씩 키운다(400%에서 화면 약 13px).
     */
    static double ringRadius(double radiusPx, double z) {
        return px(radiusPx, z) + (z > 1 ? 2.5 * (1 - 1 / z) : 0);
    }

    static void ring(Graphics2D g, Location at, double z) {
        ring(g, at, z, RING_PX);
    }

    static void ring(Graphics2D g, Location at, double z, float radiusPx) {
        float r = (float) ringRadius(radiusPx, z);
        g.setColor(Tokens.WHITE);
        g.setStroke(new BasicStroke((float) px(4, z)));
        g.draw(new Ellipse2D.Float(at.getX() - r, at.getY() - r, 2 * r, 2 * r));
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke((float) px(2, z)));
        g.draw(new Ellipse2D.Float(at.getX() - r, at.getY() - r, 2 * r, 2 * r));
    }

    private static final Map<SignalFlowPath, Object[]> LAYOUTS = java.util.Collections.synchronizedMap(
            new java.util.WeakHashMap<>());

    /**
     * 끝점 라벨 자리(회로 좌표의 왼쪽 위). 링 둘레 여덟 자리(오른쪽 위, 오른쪽 아래, 왼쪽 위, 왼쪽 아래, 위, 아래, 오른쪽,
     * 왼쪽)를 가까운 것부터, 다음에는 조금 더 떨어뜨려 본다. 부품 몸체, 라벨 칩, 먼저 놓은 끝점 라벨과 겹치지 않는
     * 첫 자리, 없으면 겹친 넓이가 가장 적은 자리. 부품이 스스로 같은 글자의 라벨 칩을 가지면(핀·LED 라벨) 라벨을
     * 달지 않는다(링만). 경로·배율마다 한 번 계산한다.
     */
    static Map<SignalFlowPath.Endpoint, double[]> layout(Graphics2D g, SignalFlowPath p, Circuit shown, double z,
            List<Rectangle> obstacles) {
        double s = deviceScale(g);
        // 라벨 칩(장애물)이 바뀌면(라벨 밀도, 편집) 다시 놓는다
        String key = shown.getName() + '@' + Math.round(z * 1000) + '@' + Math.round(s * 1000) + '@'
                + (obstacles == null ? 0 : obstacles.hashCode());
        Object[] hit = LAYOUTS.get(p);
        if (hit != null && hit[0].equals(key)) {
            @SuppressWarnings("unchecked")
            Map<SignalFlowPath.Endpoint, double[]> m = (Map<SignalFlowPath.Endpoint, double[]>) hit[1];
            return m;
        }
        List<Rectangle2D> avoid = new ArrayList<>();
        for (Component c : shown.getNonWires()) {
            Bounds b = c.getBounds();
            avoid.add(new Rectangle2D.Double(b.getX() - 1, b.getY() - 1, b.getWidth() + 2, b.getHeight() + 2));
        }
        if (obstacles != null) {
            // 라벨 칩과는 조금 띄운다(맞닿지 않게)
            double gap = px(LABEL_GAP, z);
            for (Rectangle r : obstacles) {
                avoid.add(new Rectangle2D.Double(r.x - gap, r.y - gap, r.width + 2 * gap, r.height + 2 * gap));
            }
        }
        // 선도 장애물이다(칩이 선을 덮으면 이어진 선이 끊겨 보인다)
        List<Rectangle2D> soft = new ArrayList<>();
        for (Wire w : shown.getWires()) {
            double x0 = Math.min(w.getEnd0().getX(), w.getEnd1().getX()) - 2;
            double y0 = Math.min(w.getEnd0().getY(), w.getEnd1().getY()) - 2;
            soft.add(new Rectangle2D.Double(x0, y0, Math.abs(w.getEnd0().getX() - w.getEnd1().getX()) + 4,
                    Math.abs(w.getEnd0().getY() - w.getEnd1().getY()) + 4));
        }
        Map<SignalFlowPath.Endpoint, double[]> out = new java.util.HashMap<>();
        List<Rectangle2D> placed = new ArrayList<>(); // 먼저 놓은 끝점 칩: 지시선이 가로지르지 않게
        double ring = ringRadius(RING_PX, z);
        for (SignalFlowPath.Endpoint e : p.endpoints) {
            if (!inView(p, shown, e.circuit, e.instances) || e.label.equals(kr.ac.hallym.hcs.app.model.Names.label(
                    e.component)) || e.component.getFactory().getName().equals("Splitter")) {
                continue; // 핀·LED의 같은 라벨 칩이나 스플리터 팔 라벨이 이미 이름을 보인다: 링만
            }
            if (!labelled(e)) {
                continue; // 연결 없는 출력 포트 등은 링만(V-06)
            }
            java.awt.image.BufferedImage img = chipImage(g, e.label, false, z);
            double w = img.getWidth() / s;
            double h = img.getHeight() / s;
            double best = Double.MAX_VALUE;
            double bestHard = Double.MAX_VALUE;
            double[] pick = null;
            outer:
            for (double farPx : LABEL_FAR) {
                double r = ring + px(3, z) + px(farPx, z);
                double[][] cands = {
                    {e.at.getX() + r, e.at.getY() - r - h}, {e.at.getX() + r, e.at.getY() + r},
                    {e.at.getX() - r - w, e.at.getY() - r - h}, {e.at.getX() - r - w, e.at.getY() + r},
                    {e.at.getX() - w / 2, e.at.getY() - r - h}, {e.at.getX() - w / 2, e.at.getY() + r},
                    {e.at.getX() + r, e.at.getY() - h / 2}, {e.at.getX() - r - w, e.at.getY() - h / 2},
                };
                for (double[] c : cands) {
                    Rectangle2D rect = new Rectangle2D.Double(c[0], c[1], w, h);
                    double hard = overlap(rect, avoid);
                    // 캔버스 원점 왼쪽·위(음수 좌표)는 그려지지 않는다: 그만큼 가리는 것과 같게 친다
                    double outX = Math.max(0, -c[0]);
                    double outY = Math.max(0, -c[1]);
                    hard += outX * h + outY * w;
                    // 멀리 놓여 지시선이 생기면 그 선이 다른 끝점 칩을 가로지르지 않아야 한다(가리는 것과 같게 친다)
                    double lx = Math.max(c[0], Math.min(e.at.getX(), c[0] + w));
                    double ly = Math.max(c[1], Math.min(e.at.getY(), c[1] + h));
                    if (Math.hypot(lx - e.at.getX(), ly - e.at.getY()) > ring + px(8, z)) {
                        Line2D leader = new Line2D.Double(e.at.getX(), e.at.getY(), lx, ly);
                        for (Rectangle2D o : placed) {
                            if (leader.intersects(o)) {
                                hard += o.getWidth() * o.getHeight();
                            }
                        }
                    }
                    // 선도 가리지 않는다(체크리스트 1): 선을 덮는 자리는 가리는 자리와 같게 친다
                    double wires = overlap(rect, soft);
                    hard += wires;
                    double area = 1000 * hard;
                    if (area < best) {
                        best = area;
                        bestHard = hard;
                        pick = c;
                        if (area == 0) {
                            break outer;
                        }
                    }
                }
            }
            // 부품이나 다른 라벨을 가리지 않고는 놓을 곳이 없으면(낮은 배율의 빽빽한 곳) 라벨 없이 링만 둔다.
            // 확대하면 자리가 생겨 다시 보인다
            if (pick != null && bestHard == 0) {
                out.put(e, pick);
                avoid.add(new Rectangle2D.Double(pick[0], pick[1], w, h));
                placed.add(new Rectangle2D.Double(pick[0], pick[1], w, h));
            }
        }
        LAYOUTS.put(p, new Object[] {key, out});
        return out;
    }

    /** 지시선을 그려도 되는 곳: clip에서 놓인 끝점 칩을 모두 뺀 곳. */
    static java.awt.geom.Area leaderArea(Graphics2D g, Map<SignalFlowPath.Endpoint, double[]> places, double z,
            java.awt.Shape clip) {
        java.awt.geom.Area keep = new java.awt.geom.Area(clip != null ? clip
                : new Rectangle(-100000, -100000, 200000, 200000));
        double s0 = deviceScale(g);
        for (Map.Entry<SignalFlowPath.Endpoint, double[]> pe : places.entrySet()) {
            java.awt.image.BufferedImage ci = chipImage(g, pe.getKey().label, false, z);
            keep.subtract(new java.awt.geom.Area(new Rectangle2D.Double(pe.getValue()[0], pe.getValue()[1],
                    ci.getWidth() / s0, ci.getHeight() / s0)));
        }
        return keep;
    }

    static BasicStroke arcStroke(double z) {
        return new BasicStroke((float) px(2, z), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                new float[] {(float) px(4, z), (float) px(4, z)}, 0f);
    }

    private static final Map<SignalFlowPath, Object[]> ARC_AREAS = java.util.Collections.synchronizedMap(
            new java.util.WeakHashMap<>());

    /** arcArea를 경로·그리는 영역마다 한 번만(Area 빼기는 느리다: 프레임 4ms 기준). */
    static java.awt.geom.Area arcAreaCached(SignalFlowPath p, Circuit shown, Graphics2D g, List<Rectangle> obstacles) {
        Rectangle clip = g.getClipBounds();
        Object key = java.util.Arrays.asList(shown, clip, obstacles == null ? 0 : obstacles.hashCode());
        Object[] hit = ARC_AREAS.get(p);
        if (hit != null && hit[0].equals(key)) {
            return (java.awt.geom.Area) hit[1];
        }
        java.awt.geom.Area a = arcArea(shown, g, obstacles);
        ARC_AREAS.put(p, new Object[] {key, a});
        return a;
    }

    /** 호·링이 지나도 되는 곳: 그리는 영역에서 부품 몸체(터널 포함)와 라벨 칩·스플리터 팔 라벨을 뺀 곳. */
    static java.awt.geom.Area arcArea(Circuit shown, Graphics2D g, List<Rectangle> obstacles) {
        Rectangle clip = g.getClipBounds();
        if (clip == null) {
            clip = new Rectangle(-100000, -100000, 200000, 200000);
        }
        java.awt.geom.Area a = new java.awt.geom.Area(clip);
        // 터널 몸체(이름 글자)도 뺀다: 호는 터널 끝(포트)에서 나고 들어 몸체 밖에서 보인다(ui-reviewer P-07)
        for (Component c : shown.getNonWires()) {
            Bounds b = c.getBounds();
            a.subtract(new java.awt.geom.Area(new Rectangle(b.getX() - 2, b.getY() - 2, b.getWidth() + 4,
                    b.getHeight() + 4)));
        }
        if (obstacles != null) {
            for (Rectangle r : obstacles) {
                a.subtract(new java.awt.geom.Area(new Rectangle(r.x - 2, r.y - 2, r.width + 4, r.height + 4)));
            }
        }
        return a;
    }

    private static double overlap(Rectangle2D r, List<Rectangle2D> rects) {
        double area = 0;
        for (Rectangle2D a : rects) {
            Rectangle2D i = r.createIntersection(a);
            if (i.getWidth() > 0 && i.getHeight() > 0) {
                area += i.getWidth() * i.getHeight();
            }
        }
        return area;
    }

    /**
     * 글자 라벨을 다는 끝점(V-06): 출력 Pin, 순차 부품 입력(STATE), 뒤로 갈 때의 출처, 서브회로 안(경계를 건넌 곳).
     * 연결 없는 포트(Comparator lt 등)와 핀이 아닌 출력 끝은 링만.
     */
    static boolean labelled(SignalFlowPath.Endpoint e) {
        switch (e.kind) {
        case UNCONNECTED:
            return false;
        case OUTPUT:
            return e.component.getFactory().getName().equals("Pin") || !e.instances.isEmpty();
        default:
            return true;
        }
    }

    /** 끝점 라벨: 링 오른쪽 위. */
    static void label(Graphics2D g, Location at, String text, double z) {
        java.awt.image.BufferedImage img = chipImage(g, text, false, z);
        double s = deviceScale(g);
        double w = img.getWidth() / s;
        double h = img.getHeight() / s;
        drawChip(g, img, at.getX() + px(RING_PX + 4, z), at.getY() - px(RING_PX + 2, z) - h, s);
    }

    /** 서브회로 칩: 부품 오른쪽 위 바깥. */
    static void chip(Graphics2D g, Bounds b, String text, double z) {
        java.awt.image.BufferedImage img = chipImage(g, text, true, z);
        double s = deviceScale(g);
        double w = img.getWidth() / s;
        double h = img.getHeight() / s;
        drawChip(g, img, b.getX() + b.getWidth() - w, b.getY() - px(6, z) - h, s);
    }

    /** 장치 화소 / 회로 단위(배율 × 화면 배율). */
    static double deviceScale(Graphics2D g) {
        double s = Math.abs(g.getTransform().getScaleX());
        return s <= 0 ? 1 : s;
    }

    private static final Map<String, java.awt.image.BufferedImage> CHIPS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 칩 그림(글자와 둥근 테두리)을 장치 화소로 한 번 그려 둔다. 프레임마다 글자를 다시 그리지 않아 빠르다(프레임
     * 4ms 기준). 화면 배율이 바뀌면 새로 그린다.
     */
    static java.awt.image.BufferedImage chipImage(Graphics2D g, String text, boolean places, double z) {
        double s = deviceScale(g);
        double dev = s / (z <= 0 ? 1 : z); // 화면 배율(HiDPI)
        String key = (places ? "p" : "l") + Math.round(dev * 100) + "\u0000" + text;
        if (CHIPS.size() > 500) {
            CHIPS.clear();
        }
        return CHIPS.computeIfAbsent(key, k -> {
            Font f = new Font(Tokens.UI_FONT, Font.BOLD, 1).deriveFont((float) (11 * dev));
            java.awt.image.BufferedImage probe = new java.awt.image.BufferedImage(1, 1,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D pg = probe.createGraphics();
            pg.setFont(f);
            FontMetrics fm = pg.getFontMetrics();
            pg.dispose();
            double padX = (places ? 5 : 4) * dev;
            double padY = (places ? 2 : 1.5) * dev;
            int w = (int) Math.ceil(fm.stringWidth(text) + 2 * padX) + 2;
            int h = (int) Math.ceil(fm.getAscent() + fm.getDescent() + 2 * padY) + 2;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D cg = img.createGraphics();
            cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            cg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, w - 1.5, h - 1.5, (places ? 6 : 5) * dev,
                    (places ? 6 : 5) * dev);
            cg.setColor(Tokens.WHITE);
            cg.fill(box);
            cg.setColor(places ? ACCENT.darker() : ACCENT);
            cg.setStroke(new BasicStroke((float) Math.max(1, dev)));
            cg.draw(box);
            cg.setFont(f);
            cg.setColor(places ? ACCENT.darker() : Tokens.TEXT);
            cg.drawString(text, (float) (padX + 1), (float) (padY + 1 + fm.getAscent()));
            cg.dispose();
            return img;
        });
    }

    private static void drawChip(Graphics2D g, java.awt.image.BufferedImage img, double x, double y, double s) {
        java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform(1 / s, 0, 0, 1 / s, x, y);
        g.drawImage(img, at, null);
    }

    static void badge(Graphics2D g, Bounds b, String text, double z) {
        float r = (float) px(7, z);
        // 모서리에서 대각선으로 반지름만큼 떨어뜨려 부품 외곽선을 덮지 않는다
        float cx = b.getX() - r;
        float cy = b.getY() - r;
        g.setColor(Tokens.WHITE);
        g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        g.setColor(ACCENT.darker());
        g.setStroke(new BasicStroke((float) px(1.2f, z)));
        g.draw(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        g.setFont(font(10, z));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2f, cy + (fm.getAscent() - fm.getDescent()) / 2f);
    }

    static void arrow(Graphics2D g, SignalFlowPath.Segment s, double z) {
        double mx = (s.from.getX() + s.to.getX()) / 2.0;
        double my = (s.from.getY() + s.to.getY()) / 2.0;
        double dx = Integer.signum(s.to.getX() - s.from.getX());
        double dy = Integer.signum(s.to.getY() - s.from.getY());
        double a = px(5, z);
        Path2D.Double tri = new Path2D.Double();
        tri.moveTo(mx + dx * a, my + dy * a);
        tri.lineTo(mx - dx * a - dy * a, my - dy * a + dx * a);
        tri.lineTo(mx - dx * a + dy * a, my - dy * a - dx * a);
        tri.closePath();
        g.fill(tri);
    }

    static void number(Graphics2D g, Bounds b, int n, double z) {
        String text = Integer.toString(n);
        g.setFont(font(10, z));
        FontMetrics fm = g.getFontMetrics();
        float r = (float) px(7, z);
        float cx = b.getX() + b.getWidth() + r;
        float cy = b.getY() - r;
        g.setColor(ACCENT.darker());
        g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        g.setColor(Tokens.WHITE);
        g.drawString(text, cx - fm.stringWidth(text) / 2f, cy + (fm.getAscent() - fm.getDescent()) / 2f);
    }

    private static final Map<String, String> PLACES = new java.util.concurrent.ConcurrentHashMap<>();

    /** "alu: 3 places"(문구는 한 번만 만든다: 프레임마다 MessageFormat을 만들지 않는다). */
    static String places(String name, int n) {
        return PLACES.computeIfAbsent(name + '\u0000' + n, k -> Messages.get("influence.places", name, n));
    }

    private static final Map<Long, Font> FONTS = new java.util.concurrent.ConcurrentHashMap<>();

    /** 화면 size px 굵은 글꼴(배율마다 한 번 만든다). */
    static Font font(double size, double z) {
        float pt = (float) px(size, z);
        return FONTS.computeIfAbsent((long) Float.floatToIntBits(pt), k -> new Font(Tokens.UI_FONT, Font.BOLD, 1)
                .deriveFont(pt));
    }

    static double px(double screen, double z) {
        return screen / (z <= 0 ? 1 : z);
    }

    static double mod(double a, double m) {
        double r = a % m;
        return r < 0 ? r + m : r;
    }

    /** 테스트: 한 장면의 선 구간 중 앞단이 닿은 것. */
    static List<SignalFlowPath.Segment> litAt(SignalFlowPath p, Circuit shown, double t) {
        List<SignalFlowPath.Segment> ret = new ArrayList<>();
        for (SignalFlowPath.Segment s : p.segments) {
            if (inView(p, shown, s.circuit, s.instances) && t > s.start) {
                ret.add(s);
            }
        }
        return ret;
    }
}
