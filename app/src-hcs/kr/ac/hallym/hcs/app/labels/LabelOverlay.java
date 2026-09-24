/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.swing.JButton;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.TextField;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.instance.InstanceTextField;
import com.cburch.logisim.tools.TextEditable;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.Settings;
import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.probe.QuickProbe;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 편집 캔버스의 가독성 층(#79, PLAN.md 11.12). 원조 라벨 글자 대신 옅은 배경의 칩(Pretendard, 확대 비율과 무관한
 * 최소 크기)을 겹치지 않게 놓고, 긴 버스에 넷 이름과 폭({@code ALUResult[31:0]}), 기본 모양 서브회로 상자 안에
 * 포트 이름과 회로 이름, 터널에 이름 해시 색을 그린다. 모두 그릴 때만 적용하고 .circ(라벨 글꼴 포함)는 그대로다.
 * 인쇄·그림 내보내기는 원조 그리기를 그대로 쓴다.
 */
public final class LabelOverlay {
    /** 라벨 밀도 3단계. */
    public enum Density {
        ALL, MAIN, HOVER;

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    static final String DENSITY = "labels.density";
    /** 칩 글자의 최소 화면 크기(px). */
    static final float MIN_SCREEN_PX = 10f;
    /** 칩 글자의 기본 크기(회로 좌표). */
    static final float BASE_PX = 11f;
    /** 버스 이름을 붙일 최소 선 길이(회로 좌표). */
    static final int BUS_MIN_LENGTH = 60;
    /** 포트가 상자의 어느 변에 있는지 볼 때의 여유(px). */
    static final int SIDE = 5;

    private static final Map<Canvas, LabelOverlay> OVERLAYS = new WeakHashMap<>();
    private static Field fieldOfTextField;

    private final Canvas canvas;
    private Component hovered;
    /** 이번 그리기의 원조 라벨들(wrap이 채우고 paint가 쓴다). */
    private List<LabelField> labels = new ArrayList<>();
    private long cachedSig;
    private List<LabelLayout.Placed> cached = new ArrayList<>();
    private Map<Object, String> cachedText = new HashMap<>();

    /** 부품의 원조 라벨: 글자, 글꼴, 자리(회로 좌표), 원조가 drawString에 넘기는 기준선 좌표. */
    static final class LabelField {
        final Component comp;
        final String text;
        final Rectangle bounds;
        final int drawX;
        final int drawY;

        LabelField(Component comp, String text, Rectangle bounds, int drawX, int drawY) {
            this.comp = comp;
            this.text = text;
            this.bounds = bounds;
            this.drawX = drawX;
            this.drawY = drawY;
        }
    }

    private LabelOverlay(Canvas canvas) {
        this.canvas = canvas;
        canvas.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (density() != Density.HOVER) {
                    return;
                }
                Component now = componentAt(canvas.getCircuit(), Location.create(e.getX(), e.getY()));
                if (now != hovered) {
                    hovered = now;
                    canvas.repaint();
                }
            }
        });
    }

    static synchronized LabelOverlay of(Canvas canvas) {
        return OVERLAYS.computeIfAbsent(canvas, LabelOverlay::new);
    }

    public static Density density() {
        String s = Settings.get().getString(DENSITY, "all");
        for (Density d : Density.values()) {
            if (d.key().equals(s)) {
                return d;
            }
        }
        return Density.ALL;
    }

    public static void setDensity(Density d) {
        Settings.get().set(DENSITY, d.key());
        try {
            Settings.get().save();
        } catch (java.io.IOException e) {
            // 환경설정을 못 써도 표시는 바뀐다
        }
        synchronized (LabelOverlay.class) {
            for (Canvas c : OVERLAYS.keySet()) {
                c.repaint();
            }
        }
    }

    /** 상태 표시줄의 라벨 밀도 단추(누를 때마다 다음 단계). */
    public static JButton densityButton() {
        JButton b = new JButton(Messages.get("labels.density." + density().key()));
        b.setFocusable(false);
        b.setToolTipText(Messages.get("labels.densityTip"));
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setForeground(Tokens.TEXT_2);
        b.addActionListener(e -> {
            Density next = Density.values()[(density().ordinal() + 1) % Density.values().length];
            setDensity(next);
            b.setText(Messages.get("labels.density." + next.key()));
        });
        return b;
    }

    /**
     * 원조가 회로를 그릴 Graphics. 부품마다 그 부품 자신의 원조 라벨 글자만 빼는 Graphics를 돌려주고, 뺀 라벨은
     * paint가 칩으로 그린다. hidden은 원조 {@code Circuit.draw}에 넘기는 것과 같아야 한다(가려진 부품은 원조가
     * create()를 부르지 않는다). Graphics2D가 아니면 그대로 돌려준다(원조 라벨 그대로).
     */
    public static Graphics wrap(Canvas canvas, Graphics g, Circuit circuit, java.util.Collection<Component> hidden) {
        if (!(g instanceof Graphics2D) || circuit == null) {
            return g;
        }
        LabelOverlay o = of(canvas);
        o.labels = labelFields(circuit, g);
        return filter((Graphics2D) g, circuit, hidden, o.labels);
    }

    /** 원조 그리기 순서(선 한 번, 그다음 가려지지 않은 부품)에 맞춘 거르기 Graphics. */
    static FilterGraphics filter(Graphics2D g, Circuit circuit, java.util.Collection<Component> hidden,
            List<LabelField> fields) {
        Map<Component, String> keys = new HashMap<>();
        for (LabelField f : fields) {
            keys.put(f.comp, FilterGraphics.key(f.text, f.drawX, f.drawY));
        }
        List<Component> order = new ArrayList<>();
        order.add(null); // 원조는 선을 그릴 Graphics를 먼저 만든다
        for (Component c : circuit.getNonWires()) {
            if (hidden == null || !hidden.contains(c)) {
                order.add(c);
            }
        }
        return new FilterGraphics(g, order.iterator(), keys);
    }

    /** 원조 회로 그리기 뒤에 부른다. */
    public static void paint(Canvas canvas, Graphics g0, Circuit circuit, CircuitState state,
            java.util.Set<Component> hidden) {
        if (!(g0 instanceof Graphics2D) || circuit == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            of(canvas).paintAll(g, circuit, hidden);
        } finally {
            g.dispose();
        }
    }

    private double zoom() {
        return canvas.getHcsZoom() == null ? 1.0 : canvas.getHcsZoom().zoomFactor();
    }

    private void paintAll(Graphics2D g, Circuit circuit, java.util.Set<Component> hidden) {
        double z = zoom();
        tunnels(g, circuit, hidden);
        subcircuits(g, circuit, hidden, z);
        chips(g, circuit, hidden, z);
    }

    // ---- 터널 색 ----

    private static void tunnels(Graphics2D g, Circuit circuit, java.util.Set<Component> hidden) {
        for (Component c : circuit.getNonWires()) {
            if (!c.getFactory().getName().equals("Tunnel") || hidden.contains(c)) {
                continue;
            }
            String name = Names.label(c);
            if (name == null) {
                continue;
            }
            Color col = TunnelColors.of(name);
            Bounds b = c.getBounds();
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 70));
            g.fillRect(b.getX() + 1, b.getY() + 1, b.getWidth() - 1, b.getHeight() - 1);
        }
    }

    // ---- 서브회로 상자 ----

    /**
     * 기본 모양 서브회로 상자 안에 포트 이름(안쪽 핀 라벨). 원조 기본 상자는 포트 간격이 10px이라 글자는 그보다
     * 작아야 하고, 화면에서 읽을 수 없을 만큼 작아지면 그리지 않는다. 회로 이름은 상자에 들어가지 않아 칩 배치의
     * 캡션으로 그린다({@link #chips}).
     */
    private static void subcircuits(Graphics2D g, Circuit circuit, java.util.Set<Component> hidden, double z) {
        float portPx = 7f;
        if (portPx * z < 5) {
            return;
        }
        g.setFont(new Font(Tokens.UI_FONT, Font.PLAIN, 1).deriveFont(portPx));
        FontMetrics fm = g.getFontMetrics();
        for (Component c : circuit.getNonWires()) {
            if (!defaultSubcircuit(c) || hidden.contains(c)) {
                continue;
            }
            Bounds b = c.getBounds();
            int half = Math.max(6, b.getWidth() / 2 - 4);
            g.setColor(Tokens.TEXT_2);
            int mid = (fm.getAscent() - fm.getDescent()) / 2;
            for (int i = 0; i < c.getEnds().size(); i++) {
                Location p = c.getEnds().get(i).getLocation();
                String name = fit(Kinds.portName(c, i), fm, half);
                int w = fm.stringWidth(name);
                // 포트는 상자 테두리 위에 있고, 부품 경계는 포트 표시만큼 조금 더 넓다
                if (p.getX() <= b.getX() + SIDE) {
                    g.drawString(name, p.getX() + 4, p.getY() + mid);
                } else if (p.getX() >= b.getX() + b.getWidth() - SIDE) {
                    g.drawString(name, p.getX() - 4 - w, p.getY() + mid);
                } else if (p.getY() <= b.getY() + SIDE) {
                    g.drawString(name, p.getX() - w / 2, p.getY() + 12 + fm.getAscent()); // 홈 아래
                } else {
                    g.drawString(name, p.getX() - w / 2, p.getY() - 3);
                }
            }
        }
    }

    private static boolean defaultSubcircuit(Component c) {
        return c.getFactory() instanceof SubcircuitFactory
                && ((SubcircuitFactory) c.getFactory()).getSubcircuit().getAppearance().isDefaultAppearance();
    }

    /** 서브회로 이름 캡션의 열쇠(부품 라벨 칩과 구별). */
    static final class Caption {
        final Component comp;

        Caption(Component comp) {
            this.comp = comp;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Caption && ((Caption) o).comp == comp;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(comp) * 7;
        }
    }

    /** 폭에 맞게 줄인 글자(넘치면 끝을 …로). */
    static String fit(String s, FontMetrics fm, int max) {
        if (fm.stringWidth(s) <= max) {
            return s;
        }
        for (int n = s.length() - 1; n > 0; n--) {
            String t = s.substring(0, n) + "…";
            if (fm.stringWidth(t) <= max) {
                return t;
            }
        }
        return "…";
    }

    // ---- 라벨 칩 ----

    private void chips(Graphics2D g, Circuit circuit, java.util.Set<Component> hidden, double z) {
        Density d = density();
        float px = (float) Math.max(BASE_PX, MIN_SCREEN_PX / z);
        Font font = new Font(Tokens.UI_FONT, Font.PLAIN, 1).deriveFont(px);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int padX = Math.round(px * 0.35f);
        int h = fm.getAscent() + fm.getDescent() + 2;

        List<LabelLayout.Req> reqs = new ArrayList<>();
        Map<Object, String> texts = new HashMap<>();
        long sig = 17L * circuit.hashCode() + Double.hashCode(z) * 31L + d.ordinal();
        for (LabelField f : labels) {
            if (hidden.contains(f.comp) || !shown(d, f.comp)) {
                continue;
            }
            reqs.add(new LabelLayout.Req(f.comp, f.bounds, fm.stringWidth(f.text) + 2 * padX, h, 0));
            texts.put(f.comp, f.text);
            sig = sig * 31 + System.identityHashCode(f.comp) + f.text.hashCode() + f.bounds.hashCode();
        }
        for (Component c : circuit.getNonWires()) {
            if (!defaultSubcircuit(c) || hidden.contains(c) || !shown(d, c)) {
                continue;
            }
            String name = ((SubcircuitFactory) c.getFactory()).getSubcircuit().getName();
            int tw = g.getFontMetrics(font.deriveFont(Font.BOLD)).stringWidth(name) + 2 * padX;
            Bounds b = c.getBounds();
            boolean southPorts = false;
            for (int i = 0; i < c.getEnds().size(); i++) {
                Location p = c.getEnds().get(i).getLocation();
                southPorts |= p.getY() >= b.getY() + b.getHeight() - SIDE && p.getX() > b.getX() + SIDE
                        && p.getX() < b.getX() + b.getWidth() - SIDE;
            }
            int y = southPorts ? b.getY() - h - 2 : b.getY() + b.getHeight() + 2;
            Caption key = new Caption(c);
            reqs.add(new LabelLayout.Req(key, new Rectangle(b.getX() + (b.getWidth() - tw) / 2, y, tw, h), tw, h, 0));
            texts.put(key, name);
            sig = sig * 31 + System.identityHashCode(c) * 3 + name.hashCode();
        }
        if (d != Density.HOVER) {
            for (Map.Entry<Wire, String> e : busNames(circuit).entrySet()) {
                Wire w = e.getKey();
                String text = e.getValue();
                int tw = fm.stringWidth(text) + 2 * padX;
                Location m = Location.create((w.getEnd0().getX() + w.getEnd1().getX()) / 2,
                        (w.getEnd0().getY() + w.getEnd1().getY()) / 2);
                Rectangle anchor = w.getEnd0().getY() == w.getEnd1().getY()
                        ? new Rectangle(m.getX() - tw / 2, m.getY() - h - 3, tw, h)
                        : new Rectangle(m.getX() + 4, m.getY() - h / 2, tw, h);
                reqs.add(new LabelLayout.Req(w, anchor, tw, h, 1));
                texts.put(w, text);
                sig = sig * 31 + System.identityHashCode(w) + text.hashCode();
            }
        }
        List<Rectangle> obstacles = new ArrayList<>();
        for (Component c : circuit.getNonWires()) {
            sig = sig * 31 + System.identityHashCode(c);
            Bounds b = c.getBounds();
            obstacles.add(new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
        }
        if (sig != cachedSig || !texts.equals(cachedText)) {
            cached = LabelLayout.layout(reqs, obstacles, Math.max(3, Math.round(px / 3)), 14);
            cachedSig = sig;
            cachedText = texts;
        }
        for (LabelLayout.Placed p : cached) {
            String text = texts.get(p.key);
            if (text == null) {
                continue;
            }
            boolean bus = p.key instanceof Wire;
            boolean caption = p.key instanceof Caption;
            Rectangle r = p.rect;
            if (p.leader) {
                g.setColor(Tokens.GRAY);
                g.setStroke(new BasicStroke(1f / (float) Math.max(1, z)));
                int ex = Math.max(r.x, Math.min(p.anchor.x, r.x + r.width));
                int ey = Math.max(r.y, Math.min(p.anchor.y, r.y + r.height));
                g.drawLine(p.anchor.x, p.anchor.y, ex, ey);
            }
            int arc = Math.round(px * 0.5f);
            g.setColor(bus ? Tokens.TEAL_TINT : caption ? Tokens.WHITE : new Color(0xF3, 0xF6, 0xFA, 235));
            g.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc);
            g.setColor(bus ? Tokens.TEAL : Tokens.BORDER.darker());
            g.setStroke(new BasicStroke(1f / (float) Math.max(1, z)));
            g.drawRoundRect(r.x, r.y, r.width, r.height, arc, arc);
            g.setColor(bus ? Tokens.TEAL_TEXT : caption ? Tokens.NAVY : Tokens.TEXT);
            g.setFont(caption ? font.deriveFont(Font.BOLD) : font);
            g.drawString(text, r.x + padX, r.y + 1 + fm.getAscent());
        }
    }

    private boolean shown(Density d, Component c) {
        switch (d) {
        case MAIN:
            String f = c.getFactory().getName();
            return f.equals("Pin") || f.equals("Tunnel") || c.getFactory() instanceof SubcircuitFactory;
        case HOVER:
            return c == hovered;
        default:
            return true;
        }
    }

    /** 이름 있는 버스(폭 2 이상)의 가장 긴 선과 그 표시 글자: {@code 이름[w-1:0]}. */
    static Map<Wire, String> busNames(Circuit circuit) {
        Map<Wire, String> ret = new java.util.LinkedHashMap<>();
        Netlist nl = Netlist.of(circuit);
        Set<Netlist.Net> seen = new HashSet<>();
        for (Wire w : circuit.getWires()) {
            Netlist.Net net = nl.netOf(w);
            if (net == null || !seen.add(net)) {
                continue;
            }
            BitWidth bw = circuit.getWidth(w.getEnd0());
            int width = bw == null ? 0 : bw.getWidth();
            if (width < 2) {
                continue;
            }
            String name = QuickProbe.netName(circuit, net);
            if (name.isEmpty()) {
                continue;
            }
            Wire longest = null;
            for (Wire o : net.wires()) {
                if (longest == null || o.getLength() > longest.getLength()) {
                    longest = o;
                }
            }
            if (longest != null && longest.getLength() >= BUS_MIN_LENGTH) {
                ret.put(longest, name + "[" + (width - 1) + ":0]");
            }
        }
        return ret;
    }

    /** 점 p를 몸체로 덮는 부품(선 제외). */
    static Component componentAt(Circuit circuit, Location p) {
        if (circuit == null) {
            return null;
        }
        for (Component c : circuit.getNonWires()) {
            if (c.getBounds().expand(3).contains(p)) {
                return c;
            }
        }
        return null;
    }

    /**
     * 원조 라벨들. 부품의 원조 {@link InstanceTextField}(공개 기능 {@link TextEditable})가 가진 {@link TextField}의 공개
     * 값(글자, 글꼴, 자리, 정렬)으로 원조 {@code TextField.draw}가 쓰는 기준선 좌표를 같은 식으로 계산한다.
     */
    static List<LabelField> labelFields(Circuit circuit, Graphics g) {
        List<LabelField> ret = new ArrayList<>();
        for (Component c : circuit.getNonWires()) {
            if (c.getFactory().getName().equals("Tunnel")) {
                continue; // 터널은 이름이 곧 몸체라 원조 그대로 둔다
            }
            TextField tf = textField(c);
            if (tf == null || tf.getText() == null || tf.getText().isEmpty()) {
                continue;
            }
            com.cburch.logisim.data.Attribute<?> la = c.getAttributeSet().getAttribute("label");
            if (la == null || !tf.getText().equals(c.getAttributeSet().getValue(la))) {
                continue; // 라벨 속성의 글자만(글자 부품의 본문 등은 원조 그대로)
            }
            int[] d = drawPoint(tf, g);
            Rectangle r = new Rectangle(d[0], d[1] - d[2], d[4], d[2] + d[3]);
            ret.add(new LabelField(c, tf.getText(), r, d[0], d[1]));
        }
        return ret;
    }

    /**
     * 원조 {@code TextField.draw}가 drawString에 넘기는 기준선 좌표를 같은 식으로 계산한다.
     * 반환: {x, y, ascent, descent, width}.
     */
    static int[] drawPoint(TextField tf, Graphics g) {
        Font font = tf.getFont() != null ? tf.getFont() : g.getFont();
        FontMetrics fm = g.getFontMetrics(font);
        int x = tf.getX();
        int y = tf.getY();
        int width = fm.stringWidth(tf.getText());
        int ascent = fm.getAscent();
        int descent = fm.getDescent();
        switch (tf.getHAlign()) {
        case TextField.H_CENTER:
            x -= width / 2;
            break;
        case TextField.H_RIGHT:
            x -= width;
            break;
        default:
            break;
        }
        switch (tf.getVAlign()) {
        case TextField.V_TOP:
            y += ascent;
            break;
        case TextField.V_CENTER:
            y += ascent / 2;
            break;
        case TextField.V_CENTER_OVERALL:
            y += (ascent - descent) / 2;
            break;
        case TextField.V_BOTTOM:
            y -= descent;
            break;
        default:
            break;
        }
        return new int[] {x, y, ascent, descent, width};
    }

    /** 부품의 원조 라벨 TextField. 없으면 null. */
    static TextField textField(Component c) {
        Object f = c.getFeature(TextEditable.class);
        if (!(f instanceof InstanceTextField)) {
            return null;
        }
        try {
            if (fieldOfTextField == null) {
                Field fld = InstanceTextField.class.getDeclaredField("field");
                fld.setAccessible(true);
                fieldOfTextField = fld;
            }
            return (TextField) fieldOfTextField.get(f);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
