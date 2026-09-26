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
    /** 서브회로 상자 안 포트 이름의 가장 작은 글자(회로 좌표). */
    static final float PORT_MIN_PX = 5f;

    private static final Map<Canvas, LabelOverlay> OVERLAYS = new WeakHashMap<>();
    private static Field fieldOfTextField;

    private final Canvas canvas;
    private Component hovered;
    /** 이번 그리기의 원조 라벨들(wrap이 채우고 paint가 쓴다). */
    private List<LabelField> labels = new ArrayList<>();
    private long cachedSig;
    /** 칩과 선 사이 최소 간격(회로 단위): 강조 띠의 반 폭보다 3 크다(X-04, D-108; 150%에서도 띠 밖 4px 이상). */
    public static final int WIRE_GAP = (int) (kr.ac.hallym.hcs.app.cycle.FieldOverlay.BAND / 2) + 3;
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
                // 원조 캔버스는 마우스 좌표를 회로 좌표로 바꿔 넘긴다(Canvas.repairMouseEvent)
                double z = zoom();
                Component now = componentAt(canvas.getCircuit(), Location.create(e.getX(), e.getY()));
                // 원조 부품의 포트 이름(S-06)은 라벨 밀도와 상관없이 마우스를 올린 부품에 보인다
                if (now != portHover) {
                    repaintPart(portHover, z);
                    portHover = now;
                    repaintPart(portHover, z);
                }
                if (density() != Density.HOVER) {
                    return;
                }
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

    /** 이미 만든 덧그림(없으면 null). */
    static synchronized LabelOverlay peek(Canvas canvas) {
        return OVERLAYS.get(canvas);
    }

    /** 마우스가 올라가 있는 부품(라벨 밀도와 상관없이, 없으면 null). */
    Component hovered() {
        return portHover;
    }

    private Component portHover;

    /** 포트 이름이 부품 바깥에 그려지므로 둘레를 넉넉히 다시 그린다. */
    private void repaintPart(Component c, double z) {
        if (c == null || !PortLabels.original(c)) {
            return;
        }
        // 이름은 부품 밖 화면 크기(최소 10px) 글자로 그려진다: 화면 60px만큼 넉넉히
        Bounds b = c.getBounds().expand((int) Math.ceil(Math.max(30, 60 / z)));
        canvas.repaint((int) Math.floor(b.getX() * z), (int) Math.floor(b.getY() * z), (int) Math.ceil(b.getWidth() * z),
                (int) Math.ceil(b.getHeight() * z));
    }

    /** 지난번에 그린 스플리터 팔 라벨 자리(회로 좌표). 흐름 라벨·링이 피할 곳(P-07). */
    private List<Rectangle> armRects = new ArrayList<>();

    /** 라벨 칩과 스플리터 팔 라벨 자리(회로 좌표): 다른 덧그림이 가리면 안 되는 글자. */
    public static List<Rectangle> textRects(Canvas canvas) {
        List<Rectangle> ret = chipRects(canvas);
        LabelOverlay o;
        synchronized (LabelOverlay.class) {
            o = OVERLAYS.get(canvas);
        }
        if (o != null) {
            for (Rectangle r : o.armRects) {
                ret.add(new Rectangle(r));
            }
        }
        return ret;
    }

    /** 지금 그려진 라벨 칩들의 자리(회로 좌표). 빠른 속성 창이 칩을 덮지 않게 쓴다. */
    public static List<Rectangle> chipRects(Canvas canvas) {
        List<Rectangle> ret = new ArrayList<>();
        LabelOverlay o;
        synchronized (LabelOverlay.class) {
            o = OVERLAYS.get(canvas);
        }
        if (o != null) {
            for (LabelLayout.Placed p : o.cached) {
                ret.add(new Rectangle(p.rect));
            }
        }
        return ret;
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
        repaintAll();
    }

    /** 칩이 있는 캔버스를 모두 다시 그린다(표시 설정이 바뀌었을 때). */
    static void repaintAll() {
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
        Density d = density();
        boolean readable = ARM_PX * o.zoom() >= ARM_MIN_SCREEN_PX; // splitterArms와 같은 조건
        com.cburch.logisim.file.LogisimFile file = canvas.getProject().getLogisimFile();
        return filter((Graphics2D) g, circuit, hidden, o.labels, c -> readable
                && (d == Density.ALL || c == o.hovered) && !armLabels(file, circuit, c).isEmpty());
    }

    /**
     * 부품 하나를 다시 그릴 때(영향 경로가 흐리게 한 뒤 닿은 부품을 선명하게, P-01): 원조 라벨 글자를 빼는 Graphics.
     * 돌려준 Graphics의 첫 create()가 그 부품을 그릴 Graphics다(원조 그리기와 같은 방식).
     */
    public static Graphics filterOne(Canvas canvas, Graphics g, Circuit circuit, Component c) {
        if (!(g instanceof Graphics2D) || circuit == null) {
            return g;
        }
        List<LabelField> fields = labelFields(circuit, g);
        Map<Component, String> keys = new HashMap<>();
        for (LabelField f : fields) {
            if (f.comp == c) {
                keys.put(f.comp, FilterGraphics.key(f.text, f.drawX, f.drawY));
            }
        }
        Map<Component, Set<String>> texts = new HashMap<>();
        if (canvas != null && c.getFactory().getName().equals("Splitter")
                && !armLabels(canvas.getProject().getLogisimFile(), circuit, c).isEmpty()) {
            texts.put(c, originalSplitterTexts(c));
        }
        return new FilterGraphics((Graphics2D) g, java.util.Collections.singletonList(c).iterator(), keys, texts);
    }

    static FilterGraphics filter(Graphics2D g, Circuit circuit, java.util.Collection<Component> hidden,
            List<LabelField> fields) {
        return filter(g, circuit, hidden, fields, c -> false);
    }

    /**
     * 원조 그리기 순서(선 한 번, 그다음 가려지지 않은 부품)에 맞춘 거르기 Graphics. armLabels가 참인 스플리터는
     * 우리 팔 라벨이 원조 "0-7" 표시를 대신하므로 그 글자를 뺀다(라벨 한 벌만).
     */
    static FilterGraphics filter(Graphics2D g, Circuit circuit, java.util.Collection<Component> hidden,
            List<LabelField> fields, java.util.function.Predicate<Component> armLabels) {
        Map<Component, Set<String>> texts = new HashMap<>();
        for (Component c : circuit.getNonWires()) {
            if (c.getFactory().getName().equals("Splitter") && armLabels.test(c)) {
                texts.put(c, originalSplitterTexts(c));
            }
        }
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
        return new FilterGraphics(g, order.iterator(), keys, texts);
    }

    /**
     * 원조 스플리터가 팔 옆에 그리는 글자들(원조 {@code SplitterPainter.drawLabels}와 같은 식): 팔마다 이어진 비트
     * 구간을 "0-5", 한 비트면 "7", 여러 구간이면 쉼표로 잇는다.
     */
    static Set<String> originalSplitterTexts(Component s) {
        int[] arm = Netlist.splitterArms(s); // 비트마다 팔 번호(0부터), 없으면 -1
        int fanout = s.getEnds().size() - 1;
        String[] ends = new String[fanout + 1];
        int curEnd = -1;
        int cur0 = 0;
        for (int i = 0, n = arm.length; i <= n; i++) {
            int bit = i == n ? -1 : arm[i] + 1;
            if (bit != curEnd) {
                int cur1 = i - 1;
                String add = curEnd <= 0 ? null : cur0 == cur1 ? "" + cur0 : cur0 + "-" + cur1;
                if (add != null && curEnd < ends.length) {
                    ends[curEnd] = ends[curEnd] == null ? add : ends[curEnd] + "," + add;
                }
                curEnd = bit;
                cur0 = i;
            }
        }
        Set<String> ret = new HashSet<>();
        for (int i = 1; i < ends.length; i++) {
            if (ends[i] != null) {
                ret.add(ends[i]);
            }
        }
        return ret;
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
            of(canvas).paintAll(g, circuit, hidden, state);
        } finally {
            g.dispose();
        }
    }

    private double zoom() {
        return canvas.getHcsZoom() == null ? 1.0 : canvas.getHcsZoom().zoomFactor();
    }

    private void paintAll(Graphics2D g, Circuit circuit, java.util.Set<Component> hidden, CircuitState state) {
        double z = zoom();
        tunnels(g, circuit, hidden);
        subcircuits(g, circuit, hidden, z);
        List<Rectangle> covered = new ArrayList<>(splitterArms(g, circuit, hidden, z));
        armRects = new ArrayList<>(covered);
        chips(g, circuit, hidden, z, state);
        for (LabelLayout.Placed p : cached) {
            covered.add(p.rect);
        }
        redrawWires(g, canvas, circuit, state, covered);
    }

    /**
     * 칩(라벨, 팔 라벨, 캡션, 버스 이름) 밑을 지나는 선을 칩 위에 한 번 더 그린다(S-01, 체크리스트 2: 이어진 선이
     * 끊겨 보이지 않게). 칩 영역으로만 잘라 그리므로 글자는 선 옆에 그대로 보인다.
     */
    static void redrawWires(Graphics2D g, java.awt.Component canvas, Circuit circuit, CircuitState state,
            List<Rectangle> covered) {
        if (covered.isEmpty()) {
            return;
        }
        java.awt.geom.Area area = new java.awt.geom.Area();
        for (Rectangle r : covered) {
            area.add(new java.awt.geom.Area(r));
        }
        Graphics2D gc = (Graphics2D) g.create();
        try {
            gc.clip(area);
            com.cburch.logisim.comp.ComponentDrawContext ctx =
                    new com.cburch.logisim.comp.ComponentDrawContext(canvas, circuit, state, gc, gc);
            for (Wire w : circuit.getWires()) {
                Bounds b = w.getBounds();
                if (area.intersects(b.getX(), b.getY(), Math.max(1, b.getWidth()), Math.max(1, b.getHeight()))) {
                    w.draw(ctx);
                }
            }
        } finally {
            gc.dispose();
        }
    }

    // ---- 터널 색 ----

    private long tunnelSig;
    private Map<String, Color> tunnelColors = new HashMap<>();

    private void tunnels(Graphics2D g, Circuit circuit, java.util.Set<Component> hidden) {
        com.cburch.logisim.file.LogisimFile file = canvas.getProject().getLogisimFile();
        long sig = System.identityHashCode(circuit) + 31L * CircExtensionsSig.of(file, circuit);
        for (Component c : circuit.getNonWires()) {
            String n = TunnelColorStore.name(c);
            if (n != null) {
                sig = sig * 31 + System.identityHashCode(c) + n.hashCode();
            }
        }
        if (sig != tunnelSig) {
            tunnelColors = TunnelColorStore.colors(file, circuit); // 이름 배정은 회로가 바뀔 때만
            tunnelSig = sig;
        }
        for (Component c : circuit.getNonWires()) {
            String name = TunnelColorStore.name(c);
            if (name == null || hidden.contains(c)) {
                continue;
            }
            Color col = tunnelColors.getOrDefault(name, TunnelColors.of(name));
            Bounds b = c.getBounds();
            // 터널 끝(포트) 둘레는 칠하지 않는다(S-12 검토): 붙은 핀·부품의 포트 점과 테두리를 덮지 않게
            Graphics2D tg = (Graphics2D) g.create();
            try {
                java.awt.geom.Area area = new java.awt.geom.Area(new Rectangle(b.getX(), b.getY(), b.getWidth() + 1,
                        b.getHeight() + 1));
                Location port = c.getEnd(0).getLocation();
                area.subtract(new java.awt.geom.Area(new Rectangle(port.getX() - PORT_CLEAR, port.getY() - PORT_CLEAR,
                        2 * PORT_CLEAR, 2 * PORT_CLEAR)));
                // 포트가 있는 변 전체도 조금 비운다: 맞닿은 부품의 테두리(굵기 2)를 칩 테두리가 덮지 않게
                if (port.getX() <= b.getX() + 1) {
                    area.subtract(new java.awt.geom.Area(new Rectangle(b.getX() - 1, b.getY() - 1, EDGE_CLEAR + 1,
                            b.getHeight() + 3)));
                } else if (port.getX() >= b.getX() + b.getWidth() - 1) {
                    area.subtract(new java.awt.geom.Area(new Rectangle(b.getX() + b.getWidth() - EDGE_CLEAR,
                            b.getY() - 1, EDGE_CLEAR + 2, b.getHeight() + 3)));
                } else if (port.getY() <= b.getY() + 1) {
                    area.subtract(new java.awt.geom.Area(new Rectangle(b.getX() - 1, b.getY() - 1, b.getWidth() + 3,
                            EDGE_CLEAR + 1)));
                } else {
                    area.subtract(new java.awt.geom.Area(new Rectangle(b.getX() - 1, b.getY() + b.getHeight()
                            - EDGE_CLEAR, b.getWidth() + 3, EDGE_CLEAR + 2)));
                }
                tg.clip(area);
                tg.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 110));
                tg.fillRect(b.getX() + 1, b.getY() + 1, b.getWidth() - 1, b.getHeight() - 1);
                tg.setColor(col);
                tg.setStroke(new BasicStroke(1.5f));
                tg.drawRect(b.getX() + 1, b.getY() + 1, b.getWidth() - 2, b.getHeight() - 2);
            } finally {
                tg.dispose();
            }
        }
    }

    /** 터널 색 칩이 비워 둘 포트 둘레 반 폭(회로 좌표): 포트 점(지름 약 4)과 붙은 부품 테두리까지. */
    static final int PORT_CLEAR = 4;
    /** 포트가 있는 변에서 칩이 비워 둘 폭(회로 좌표): 맞닿은 부품 테두리 굵기(2)와 칩 테두리(1.5)가 겹치지 않게. */
    static final int EDGE_CLEAR = 3;

    /** 직접 지정한 터널 색이 바뀌면 배정을 다시 하도록 서명에 넣는다. */
    static final class CircExtensionsSig {
        static int of(com.cburch.logisim.file.LogisimFile file, Circuit circuit) {
            return file == null ? 0 : kr.ac.hallym.hcs.app.ext.CircExtensions.of(file).items(circuit.getName()).hashCode();
        }
    }

    // ---- 서브회로 상자 ----

    /**
     * 기본 모양 서브회로 상자 안에 포트 이름(안쪽 핀 라벨). 원조 기본 상자는 포트 간격이 10px이라 글자는 그보다
     * 작아야 하고, 화면에서 읽을 수 없을 만큼 작아지면 그리지 않는다. 회로 이름은 상자에 들어가지 않아 칩 배치의
     * 캡션으로 그린다({@link #chips}).
     */
    private static void subcircuits(Graphics2D g, Circuit circuit, java.util.Set<Component> hidden, double z) {
        float maxPx = 9f; // 검토 반영 1: 200%에서 또렷하게(포트 간격 10px 안)
        for (Component c : circuit.getNonWires()) {
            if (!defaultSubcircuit(c) || hidden.contains(c)) {
                continue;
            }
            Bounds b = c.getBounds();
            int half = Math.max(6, b.getWidth() / 2 - 4);
            // 상자마다 글자 크기: 가장 긴 이름이 상자 반 폭에 들어가게(9px에서 5px까지), 그래도 넘치면 끝을 줄인다
            g.setFont(new Font(Tokens.UI_FONT, Font.BOLD, 1).deriveFont(maxPx));
            int longest = 1;
            for (int i = 0; i < c.getEnds().size(); i++) {
                longest = Math.max(longest, g.getFontMetrics().stringWidth(Kinds.portName(c, i)));
            }
            float px = portPx(half, longest, maxPx);
            if (px * z < 5) {
                continue; // 너무 작아 읽을 수 없다
            }
            g.setFont(new Font(Tokens.UI_FONT, Font.BOLD, 1).deriveFont(px));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Tokens.TEXT); // 검토 반영 1: 대비를 높인다
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

    /**
     * 서브회로 상자 안 포트 이름 글자 크기: 가장 긴 이름(maxPx에서 잰 폭 longestAtMax)이 반 폭 half에 들어가는 크기.
     * maxPx를 넘지 않고 {@link #PORT_MIN_PX}보다 작지 않다(그래도 넘치면 끝을 줄인다).
     */
    static float portPx(int half, int longestAtMax, float maxPx) {
        return Math.max(PORT_MIN_PX, Math.min(maxPx, maxPx * half / Math.max(1, longestAtMax)));
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

    // ---- 스플리터 팔 라벨 ----

    /** 팔 라벨 하나: 팔 끝 위치, 글자({@code [31:26] op}), 팔이 뻗는 방향. */
    static final class ArmLabel {
        final Location end;
        final String text;
        final com.cburch.logisim.data.Direction facing;

        ArmLabel(Location end, String text, com.cburch.logisim.data.Direction facing) {
            this.end = end;
            this.text = text;
            this.facing = facing;
        }
    }

    /**
     * 팔 라벨 글자 크기(회로 좌표)와, 이보다 작게 보이면 그리지 않는 화면 크기. 팔 간격(원조 기본 10px) 안에 들어가는
     * 가장 큰 크기다(검토 2차 E: 7px은 200%에서도 흐렸다).
     */
    static final float ARM_PX = 8.5f;
    static final float ARM_MIN_SCREEN_PX = 5f;
    /** 팔 라벨 글자 색: 청록 계열에서 흰 바탕 대비 7:1 이상(검토 2차 E: 더 진하게). */
    static final Color ARM_COLOR = new Color(0x004D4A);
    /** 팔 라벨 뒤 바탕(선이 글자를 지나가도 읽히게). */
    static final Color ARM_BACKGROUND = new Color(255, 255, 255, 225);

    /** 스플리터의 팔 라벨들(팔 순서). 팔 이름은 .circ 확장 정보(D-032)에서 읽는다. */
    static List<ArmLabel> armLabels(com.cburch.logisim.file.LogisimFile file, Circuit circuit, Component s) {
        List<ArmLabel> ret = new ArrayList<>();
        kr.ac.hallym.hcs.app.splitter.SplitterSpec spec;
        try {
            spec = kr.ac.hallym.hcs.app.splitter.SplitterEdits.specOf(file, circuit, s);
        } catch (RuntimeException e) {
            return ret;
        }
        com.cburch.logisim.data.Direction facing = s.getAttributeSet()
                .getValue(com.cburch.logisim.instance.StdAttr.FACING);
        List<kr.ac.hallym.hcs.app.splitter.SplitterSpec.Arm> arms = spec.arms();
        for (int i = 0; i < arms.size() && i + 1 < s.getEnds().size(); i++) {
            if (arms.get(i).width() == 0) {
                continue;
            }
            ret.add(new ArmLabel(s.getEnds().get(i + 1).getLocation(), arms.get(i).label(),
                    facing == null ? com.cburch.logisim.data.Direction.EAST : facing));
        }
        return ret;
    }

    /**
     * 스플리터 팔 끝 옆에 {@code [31:26] op}(이름이 없으면 범위만). 원조 스플리터는 팔에 아무것도 적지 않는다.
     * 밀도 "전부"에서, 또는 마우스를 올린 스플리터에. 그릴 때만 적용한다.
     */
    private List<Rectangle> splitterArms(Graphics2D g, Circuit circuit, java.util.Set<Component> hidden, double z) {
        List<Rectangle> drawn = new ArrayList<>();
        float px = ARM_PX;
        if (px * z < ARM_MIN_SCREEN_PX) {
            return drawn; // 원조 "0-5" 표시는 이때 빼지 않는다(wrap)
        }
        Density d = density();
        com.cburch.logisim.file.LogisimFile file = canvas.getProject().getLogisimFile();
        g.setFont(new Font(Tokens.UI_FONT, Font.PLAIN, 1).deriveFont(Font.BOLD, px)); // Pretendard Bold
        FontMetrics fm = g.getFontMetrics();
        for (Component c : circuit.getNonWires()) {
            if (!c.getFactory().getName().equals("Splitter") || hidden.contains(c)
                    || (d != Density.ALL && c != hovered)) {
                continue;
            }
            List<ArmLabel> arms = armLabels(file, circuit, c);
            boolean opposite = oppositeSideFree(circuit, c, arms, fm);
            for (ArmLabel a : arms) {
                Rectangle r = armRect(c, a, fm, opposite);
                g.setColor(ARM_BACKGROUND);
                g.fillRect(r.x, r.y, r.width, r.height);
                g.setColor(ARM_COLOR);
                g.drawString(a.text, r.x + 1, r.y + fm.getAscent());
                drawn.add(r);
            }
        }
        return drawn;
    }

    /**
     * 팔 라벨 자리. 가로로 뻗는 팔은 opposite면 스플리터 막대 반대쪽(팔 선과 이어지는 선을 피한다, S-02), 아니면 팔 끝
     * 옆 선 위. 세로로 뻗는 팔은 팔 끝 오른쪽.
     */
    static Rectangle armRect(Component s, ArmLabel a, FontMetrics fm, boolean opposite) {
        int w = fm.stringWidth(a.text);
        int h = fm.getAscent() + fm.getDescent();
        int x;
        int base;
        if (a.facing == com.cburch.logisim.data.Direction.NORTH || a.facing == com.cburch.logisim.data.Direction.SOUTH) {
            x = a.end.getX() + 3;
            base = a.end.getY() + (a.facing == com.cburch.logisim.data.Direction.NORTH ? -3 : fm.getAscent() + 2);
        } else {
            boolean west = a.facing == com.cburch.logisim.data.Direction.WEST;
            Bounds b = s.getBounds();
            if (opposite) {
                x = west ? b.getX() + b.getWidth() + 3 : b.getX() - w - 4; // 막대 반대쪽
                base = a.end.getY() + fm.getAscent() / 2 - 1; // 팔 높이 가운데
            } else {
                x = west ? a.end.getX() - w - 2 : a.end.getX() + 2;
                base = a.end.getY() - 2; // 선 위
            }
        }
        return new Rectangle(x - 1, base - fm.getAscent(), w + 2, h);
    }

    /** 막대 반대쪽 자리들이 선·부품에 닿지 않는가(가로로 뻗는 스플리터만). */
    static boolean oppositeSideFree(Circuit circuit, Component s, List<ArmLabel> arms, FontMetrics fm) {
        if (arms.isEmpty() || arms.get(0).facing == com.cburch.logisim.data.Direction.NORTH
                || arms.get(0).facing == com.cburch.logisim.data.Direction.SOUTH) {
            return false;
        }
        for (ArmLabel a : arms) {
            Rectangle r = armRect(s, a, fm, true);
            r.grow(1, 0);
            for (Wire w : circuit.getWires()) {
                Bounds b = w.getBounds();
                if (r.intersects(b.getX(), b.getY(), Math.max(1, b.getWidth()), Math.max(1, b.getHeight()))) {
                    return false;
                }
            }
            for (Component o : circuit.getNonWires()) {
                Bounds b = o.getBounds();
                if (o != s && r.intersects(b.getX(), b.getY(), b.getWidth(), b.getHeight())) {
                    return false;
                }
            }
        }
        return true;
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

    private void chips(Graphics2D g, Circuit circuit, java.util.Set<Component> hidden, double z, CircuitState state) {
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
            if (hidden.contains(f.comp) || !shown(d, f.comp) || namedByTunnel(circuit, f.comp, f.text)) {
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
        // 버스 이름(밀도가 "마우스 올린 것만"이 아니면)과 시뮬레이션 중 값(C-08). 배치는 값 자리에 가장 넓은 글자를 넣어
        // 계산하므로 값이 바뀌어도 칩이 움직이지 않는다
        BusValues.Mode mode = BusValues.mode();
        boolean values = mode != BusValues.Mode.OFF && state != null && simulating();
        Map<Object, String> shownText = new HashMap<>();
        Set<Object> valueChips = new HashSet<>();
        if (d != Density.HOVER || values) {
            for (Map.Entry<Wire, Bus> e : buses(circuit).entrySet()) {
                Wire w = e.getKey();
                Bus bus = e.getValue();
                String name = d != Density.HOVER && !bus.name.isEmpty() ? bus.name + "[" + (bus.width - 1) + ":0]"
                        : null;
                String value = values ? BusValues.format(state.getValue(w.getEnd0()), mode) : null;
                if (name == null && value == null) {
                    continue;
                }
                String template = value == null ? null : BusValues.template(bus.width, mode, fm);
                String text = value == null ? name : name == null ? template : name + " = " + template;
                shownText.put(w, value == null ? name : name == null ? value : name + " = " + value);
                if (value != null) {
                    valueChips.add(w);
                }
                int tw = fm.stringWidth(text) + 2 * padX;
                Location m = Location.create((w.getEnd0().getX() + w.getEnd1().getX()) / 2,
                        (w.getEnd0().getY() + w.getEnd1().getY()) / 2);
                Rectangle anchor = w.getEnd0().getY() == w.getEnd1().getY()
                        ? new Rectangle(m.getX() - tw / 2, m.getY() - h - 3, tw, h)
                        : new Rectangle(m.getX() + 4, m.getY() - h / 2, tw, h);
                reqs.add(new LabelLayout.Req(w, anchor, tw, h, 1, new java.awt.geom.Line2D.Double(w.getEnd0().getX(),
                        w.getEnd0().getY(), w.getEnd1().getX(), w.getEnd1().getY())));
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
        // 선도 피한다(S-05: 칩이 옆 선 위에 놓이지 않게). 피할 수 없으면 칩 위에 선을 다시 그린다(redrawWires).
        // 강조 띠(Active Path·필드 색, 선 둘레 BAND)만큼 더 띄운다(X-04: 칩이 강조된 버스에 닿지 않게)
        for (Wire w : circuit.getWires()) {
            sig = sig * 31 + w.hashCode();
            Bounds b = w.getBounds();
            obstacles.add(new Rectangle(b.getX() - WIRE_GAP, b.getY() - WIRE_GAP, b.getWidth() + 2 * WIRE_GAP,
                    b.getHeight() + 2 * WIRE_GAP));
        }
        if (sig != cachedSig || !texts.equals(cachedText)) {
            cached = LabelLayout.layout(reqs, obstacles, Math.max(3, Math.round(px / 3)), 14);
            cachedSig = sig;
            cachedText = texts;
        }
        Map<Wire, kr.ac.hallym.hcs.app.groups.SignalGroups.Group> groups =
                kr.ac.hallym.hcs.app.groups.SignalGroups.showGroups() && canvas.getProject() != null
                        ? kr.ac.hallym.hcs.app.groups.SignalGroups.wireGroups(canvas.getProject().getLogisimFile(),
                                circuit)
                        : null;
        for (LabelLayout.Placed p : cached) {
            String text = shownText.containsKey(p.key) ? shownText.get(p.key) : texts.get(p.key);
            if (text == null) {
                continue;
            }
            boolean bus = p.key instanceof Wire;
            boolean caption = p.key instanceof Caption;
            if (p.overlapped && valueChips.contains(p.key)) {
                // 값 칩을 둘 빈 자리가 없다(촘촘한 버스 묶음): 다른 선 위에 얹거나 이웃 선을 가리키게 두지 않고 뺀다.
                // 값은 마우스를 올리면 보인다(C-08 검토)
                continue;
            }
            Rectangle r = p.rect;
            if (shownText.containsKey(p.key)) {
                // 값 칩: 자리는 가장 넓은 값으로 잡아 두고(움직이지 않게), 칩은 지금 글자 폭으로 그 가운데에 그린다
                int w = fm.stringWidth(text) + 2 * padX;
                if (w < r.width) {
                    r = new Rectangle(r.x + (r.width - w) / 2, r.y, w, r.height);
                }
            }
            if (bus) {
                // 버스 칩의 지시선은 그 선 위의 가장 가까운 점에서 긋는다(C-08 검토: 원래 자리 가운데에서 그으면
                // 옆 버스를 가리키는 것처럼 보인다). 칩이 선에 붙어 있으면 긋지 않는다
                java.awt.Point from = nearestOnWire((Wire) p.key, new java.awt.Point(r.x + r.width / 2,
                        r.y + r.height / 2));
                int ex = Math.max(r.x, Math.min(from.x, r.x + r.width));
                int ey = Math.max(r.y, Math.min(from.y, r.y + r.height));
                if (Math.abs(ex - from.x) + Math.abs(ey - from.y) > 6) {
                    g.setColor(Tokens.GRAY);
                    g.setStroke(new BasicStroke(1f / (float) Math.max(1, z)));
                    g.drawLine(from.x, from.y, ex, ey);
                }
            } else if (p.leader) {
                g.setColor(Tokens.GRAY);
                g.setStroke(new BasicStroke(1f / (float) Math.max(1, z)));
                int ex = Math.max(r.x, Math.min(p.anchor.x, r.x + r.width));
                int ey = Math.max(r.y, Math.min(p.anchor.y, r.y + r.height));
                g.drawLine(p.anchor.x, p.anchor.y, ex, ey);
            }
            int arc = Math.round(px * 0.5f);
            g.setColor(bus ? Tokens.TEAL_TINT : caption ? Tokens.WHITE : new Color(0xF3, 0xF6, 0xFA, 235));
            g.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc);
            // E-04: 그룹 색 보기면 그룹이 있는 버스 칩 테두리를 그룹 색으로(굵게)
            java.awt.Color groupColor = bus && groups != null ? groupColor(circuit, groups, (Wire) p.key) : null;
            g.setColor(groupColor != null ? groupColor : bus ? Tokens.TEAL : Tokens.BORDER.darker());
            g.setStroke(new BasicStroke((groupColor != null ? 2f : 1f) / (float) Math.max(1, z)));
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

    /**
     * 포트 하나짜리 부품(핀·프로브 등)의 포트에 같은 이름의 터널이 바로 붙어 있는가(S-12). 그러면 터널이 이름을
     * 보이므로 부품의 라벨 칩은 그리지 않는다(원조 라벨 글자도 칩과 함께 빠진다): 같은 이름이 두 번 보이지 않게.
     */
    static boolean namedByTunnel(Circuit circuit, Component c, String text) {
        if (c.getEnds().size() != 1 || text == null || c.getFactory().getName().equals("Tunnel")) {
            return false;
        }
        Location at = c.getEnd(0).getLocation();
        for (Component t : circuit.getNonWires(at)) {
            if (t != c && t.getFactory().getName().equals("Tunnel") && text.equals(Names.label(t))) {
                return true;
            }
        }
        return false;
    }

    /** 선(가로 또는 세로) 위에서 점 p에 가장 가까운 점. */
    static java.awt.Point nearestOnWire(Wire w, java.awt.Point p) {
        int x0 = Math.min(w.getEnd0().getX(), w.getEnd1().getX());
        int x1 = Math.max(w.getEnd0().getX(), w.getEnd1().getX());
        int y0 = Math.min(w.getEnd0().getY(), w.getEnd1().getY());
        int y1 = Math.max(w.getEnd0().getY(), w.getEnd1().getY());
        return new java.awt.Point(Math.max(x0, Math.min(p.x, x1)), Math.max(y0, Math.min(p.y, y1)));
    }

    /** 이 선 넷의 그룹 색(없으면 null). */
    static java.awt.Color groupColor(Circuit circuit,
            Map<Wire, kr.ac.hallym.hcs.app.groups.SignalGroups.Group> groups, Wire w) {
        kr.ac.hallym.hcs.app.groups.SignalGroups.Group g = groups.get(w);
        return g == null ? null : g.color;
    }

    /** 이름 있는 버스(폭 2 이상)의 가장 긴 선과 그 표시 글자: {@code 이름[w-1:0]}. */
    static Map<Wire, String> busNames(Circuit circuit) {
        Map<Wire, String> ret = new java.util.LinkedHashMap<>();
        for (Map.Entry<Wire, Bus> e : buses(circuit).entrySet()) {
            if (!e.getValue().name.isEmpty()) {
                ret.put(e.getKey(), e.getValue().name + "[" + (e.getValue().width - 1) + ":0]");
            }
        }
        return ret;
    }

    /** 버스 하나: 넷 이름(없으면 빈 글자)과 폭. */
    static final class Bus {
        final String name;
        final int width;

        Bus(String name, int width) {
            this.name = name;
            this.width = width;
        }
    }

    /** 버스(폭 2 이상)마다 가장 긴 선(칩 자리). 그 선이 {@link #BUS_MIN_LENGTH}보다 짧으면 뺀다. */
    static Map<Wire, Bus> buses(Circuit circuit) {
        Map<Wire, Bus> ret = new java.util.LinkedHashMap<>();
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
            Wire longest = null;
            for (Wire o : net.wires()) {
                if (longest == null || o.getLength() > longest.getLength()) {
                    longest = o;
                }
            }
            if (longest != null && longest.getLength() >= BUS_MIN_LENGTH) {
                ret.put(longest, new Bus(QuickProbe.netName(circuit, net), width));
            }
        }
        return ret;
    }

    /** 이 캔버스의 프로젝트가 시뮬레이션 중인가(값 칩은 그때만). */
    private boolean simulating() {
        com.cburch.logisim.proj.Project p = canvas.getProject();
        return p != null && p.getSimulator() != null && p.getSimulator().isRunning();
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
