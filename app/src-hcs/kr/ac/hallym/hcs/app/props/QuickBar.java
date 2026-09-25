/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.props;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.tools.EditTool;
import com.cburch.logisim.tools.SelectTool;
import com.cburch.logisim.tools.Tool;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 빠른 속성 창(#74, PLAN.md 11.4). 선택·편집 도구로 같은 종류 부품을 고르면 그 위(자리가 없으면 아래)에 자주 바꾸는
 * 속성 단추 묶음을 띄운다. 단추는 원조 선택지를 펼치거나(목록 속성) 제자리 칸을 연다(글자 속성). "모든 속성"은
 * 오른쪽 속성 패널을 편다. 아래 줄에 원조 2.7.1의 숨은 단축키를 보인다. 그리는 층만 쓰고 .circ는 건드리지 않는다.
 */
public final class QuickBar implements Selection.Listener, ProjectListener {
    private final Frame frame;
    private final Canvas canvas;
    private final AttrDock dock;
    private final JPanel bar = new JPanel();
    private List<Component> targets = new ArrayList<>();
    private boolean pressed;

    private QuickBar(Frame frame, Canvas canvas, AttrDock dock) {
        this.frame = frame;
        this.canvas = canvas;
        this.dock = dock;
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(Tokens.WHITE);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Tokens.BORDER, 1),
                BorderFactory.createEmptyBorder(2, 4, 3, 4)));
        bar.setVisible(false);
    }

    /** 창에 빠른 속성 창을 단다. 반환값은 선택 목록이 붙들어 둔다. */
    public static QuickBar install(Frame frame, Canvas canvas, AttrDock dock) {
        QuickBar q = new QuickBar(frame, canvas, dock);
        frame.getLayeredPane().add(q.bar, JLayeredPane.PALETTE_LAYER);
        canvas.getSelection().addListener(q);
        canvas.getProject().addProjectListener(q);
        dock.onQuickToggle(q::refresh);
        canvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                q.place(); // 배율이 바뀌면 캔버스 크기가 바뀐다
            }
        });
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                clearQuiet(canvas.getProject()); // 사용자가 직접 누른 선택부터는 다시 띄운다
                if (!hidesWhilePressed(e)) {
                    return;
                }
                q.pressed = true; // 끌어 옮기는 동안은 숨긴다
                q.bar.setVisible(false);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                q.pressed = false;
                SwingUtilities.invokeLater(q::refresh);
            }
        });
        SwingUtilities.invokeLater(() -> {
            JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, canvas);
            if (vp != null) {
                vp.addChangeListener(e -> q.place());
            }
        });
        return q;
    }

    /**
     * 누른 동안 숨길지: 왼쪽 단추(끌어 옮기기)만. 우클릭은 메뉴가 떠서 놓기가 캔버스로 오지 않으므로, 숨기면 다시
     * 나타나지 않는다.
     */
    static boolean hidesWhilePressed(MouseEvent e) {
        return SwingUtilities.isLeftMouseButton(e);
    }

    /** 프로그램이 고른 선택(빠른 속성 창을 띄우지 않는다). 사용자가 캔버스를 누르면 지운다. */
    private static final java.util.Map<Project, java.util.Set<Component>> QUIET = new java.util.WeakHashMap<>();

    /**
     * 곧 고를 부품들을 "조용한 선택"으로 표시한다(2c 검토 반영: Messages에서 눌러 고른 원인 부품에는 빠른 속성 창을
     * 띄우지 않는다). 선택이 이 집합과 같은 동안 창을 숨긴다.
     */
    public static synchronized void markQuiet(Project proj, java.util.Collection<? extends Component> comps) {
        QUIET.put(proj, new java.util.HashSet<>(comps));
    }

    static synchronized void clearQuiet(Project proj) {
        QUIET.remove(proj);
    }

    /** 지금 선택이 조용한 선택인가. */
    public static synchronized boolean isQuiet(Project proj, java.util.Collection<? extends Component> selection) {
        java.util.Set<Component> q = QUIET.get(proj);
        return q != null && !selection.isEmpty() && q.equals(new java.util.HashSet<>(selection));
    }

    @Override
    public void selectionChanged(Selection.Event event) {
        SwingUtilities.invokeLater(this::refresh);
    }

    @Override
    public void projectChanged(ProjectEvent event) {
        int t = event.getAction();
        if (t == ProjectEvent.ACTION_COMPLETE || t == ProjectEvent.UNDO_COMPLETE || t == ProjectEvent.ACTION_SET_TOOL
                || t == ProjectEvent.ACTION_SET_CURRENT || t == ProjectEvent.ACTION_MERGE) {
            SwingUtilities.invokeLater(this::refresh);
        }
    }

    private boolean editing() {
        Tool t = canvas.getProject().getTool();
        return t instanceof EditTool || t instanceof SelectTool;
    }

    /** 대상과 단추를 다시 만든다. */
    void refresh() {
        Project proj = canvas.getProject();
        List<Component> now = QuickAttrs.targets(canvas.getSelection().getComponents());
        if (pressed || now.isEmpty() || !editing() || !AttrDock.quickBarShown() || proj.getFrame() != frame
                || isQuiet(proj, canvas.getSelection().getComponents())
                || !proj.getLogisimFile().contains(canvas.getCircuit())) {
            targets = now;
            bar.setVisible(false);
            return;
        }
        targets = now;
        bar.removeAll();
        Component first = now.get(0);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.setOpaque(false);
        for (QuickAttrs.Entry e : QuickAttrs.entries(first)) {
            row.add(button(e, first));
        }
        JButton all = small(Messages.get("quick.all"));
        all.setForeground(Tokens.BLUE);
        all.addActionListener(ev -> dock.showAll());
        row.add(all);
        row.setAlignmentX(0f);
        bar.add(row);
        String hint = hintText(first);
        if (!hint.isEmpty()) {
            JLabel h = new JLabel(hint);
            h.setFont(h.getFont().deriveFont((float) Tokens.FONT_BADGE));
            h.setForeground(Tokens.TEXT_MUTED);
            h.setBorder(BorderFactory.createEmptyBorder(1, 4, 0, 4));
            h.setAlignmentX(0f);
            bar.add(h);
        }
        bar.setSize(bar.getPreferredSize());
        bar.revalidate();
        place();
    }

    /** 숨은 단축키 줄: 원조 숫자 키, 그리고 우리 R(회전)·F2(라벨). */
    String hintText(Component c) {
        List<String> parts = new ArrayList<>();
        for (QuickAttrs.Hint h : QuickAttrs.hints(c)) {
            parts.add(Messages.get("quick.hintKey", h.keys, h.attr.getDisplayName()));
        }
        if (c.getAttributeSet().getAttribute("facing") != null) {
            parts.add(Messages.get("quick.hintRotate"));
        }
        if (QuickAttrs.labelAttr(c) != null) {
            parts.add(Messages.get("quick.hintLabel"));
        }
        return String.join("  ·  ", parts);
    }

    private JButton button(QuickAttrs.Entry e, Component first) {
        String name = e.attr.getDisplayName();
        String value = e.value.isEmpty() ? Messages.get("quick.empty") : e.value;
        JButton b = small("<html><span style='color:#" + hex(Tokens.TEXT_2) + "'>" + esc(name) + "</span> "
                + esc(value) + "</html>");
        b.setToolTipText(Messages.get("quick.tip", name));
        b.getAccessibleContext().setAccessibleName(name + " " + value);
        b.addActionListener(ev -> {
            if (!e.options.isEmpty()) {
                JPopupMenu menu = new JPopupMenu();
                for (String[] o : e.options) {
                    JRadioButtonMenuItem it = new JRadioButtonMenuItem(o[1], o[1].equals(e.value));
                    it.addActionListener(x -> set(e.attr, o[0]));
                    menu.add(it);
                }
                menu.show(b, 0, b.getHeight());
            } else if (e.attr == QuickAttrs.labelAttr(first) && targets.size() == 1) {
                InlineEditor.editLabel(frame, canvas, first);
            } else {
                Point p = SwingUtilities.convertPoint(b, 0, b.getHeight() + 2, frame.getLayeredPane());
                InlineEditor.start(frame, canvas, targets, e.attr,
                        new Rectangle(p.x, p.y, Math.max(120, b.getWidth()), 24));
            }
        });
        return b;
    }

    private void set(Attribute<Object> a, String std) {
        try {
            canvas.getProject().doAction(QuickAttrs.parse(canvas.getCircuit(), targets, a, std));
        } catch (IllegalArgumentException ex) {
            // 원조 선택지에서 고른 값이라 해석은 늘 된다
        }
    }

    /** 대상 위(자리가 없으면 아래)에 둔다. 대상이 화면 밖이면 숨긴다. */
    void place() {
        if (targets.isEmpty() || bar.getComponentCount() == 0 || pressed || !AttrDock.quickBarShown()
                || !editing()) {
            return;
        }
        Bounds b = targets.get(0).getBounds();
        for (Component c : targets) {
            b = b.add(c.getBounds());
        }
        double z = InlineEditor.zoom(canvas);
        Rectangle r = canvas.hcsToScreen(new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
        Rectangle vis = canvas.getVisibleRect();
        if (!vis.intersects(r)) {
            bar.setVisible(false);
            return;
        }
        Dimension d = bar.getPreferredSize();
        // 피할 것: 대상의 선택 테두리(손잡이 포함), 다른 부품, 선, 라벨 칩(모두 캔버스 좌표로)
        Rectangle self = new Rectangle(r);
        self.grow(6, 6);
        // 가리면 안 되는 것(부품, 라벨 칩)과 지나가도 되는 것(선)을 나눈다(ui-reviewer: clk 터널을 덮음)
        List<Rectangle> hard = obstacles(new ArrayList<>(canvas.getCircuit().getNonWires()), targets,
                kr.ac.hallym.hcs.app.labels.LabelOverlay.chipRects(canvas), z);
        List<Rectangle> soft = obstacles(new ArrayList<>(canvas.getCircuit().getWires()), targets,
                java.util.Collections.emptyList(), z);
        // 화면 맞춤의 원점 이동(S-10): 캔버스 좌표로 옮긴다
        for (List<Rectangle> l : java.util.Arrays.asList(hard, soft)) {
            for (Rectangle o : l) {
                o.translate(canvas.getHcsOriginX(), canvas.getHcsOriginY());
            }
        }
        Rectangle at = placement(self, d, hard, soft, vis, 6);
        int x = at.x;
        int y = at.y;
        JLayeredPane layer = frame.getLayeredPane();
        Point p = SwingUtilities.convertPoint(canvas, x, y, layer);
        Point top = SwingUtilities.convertPoint(canvas, vis.x, vis.y, layer);
        p.y = Math.max(top.y, Math.min(p.y, top.y + vis.height - d.height));
        bar.setBounds(p.x, p.y, d.width, d.height);
        bar.setVisible(true);
        bar.revalidate();
        layer.repaint();
    }

    /** 선 둘레에 더 비워 둘 폭(화면 px). 선 굵기(버스 4px)와 연결점이 가리지 않게. */
    static final int WIRE_MARGIN = 4;

    /**
     * 빠른 속성 창이 피할 것(검토 2차 E): 라벨 칩(캔버스 좌표 그대로), 대상이 아닌 부품, 모든 선(대상에 붙은 선 포함).
     * 부품·선은 회로 좌표라 배율 z를 곱한다. 선은 {@link #WIRE_MARGIN}만큼 넓힌다.
     */
    static List<Rectangle> obstacles(java.util.Collection<? extends Component> components,
            java.util.Collection<? extends Component> targets, List<Rectangle> chips, double z) {
        List<Rectangle> avoid = new ArrayList<>();
        for (Rectangle chip : chips) {
            avoid.add(scale(chip, z));
        }
        for (Component c : components) {
            if (targets.contains(c)) {
                continue;
            }
            Rectangle cr = scale(new Rectangle(c.getBounds().getX(), c.getBounds().getY(), c.getBounds().getWidth(),
                    c.getBounds().getHeight()), z);
            if (c instanceof com.cburch.logisim.circuit.Wire) {
                cr.grow(WIRE_MARGIN, WIRE_MARGIN);
            }
            avoid.add(cr);
        }
        return avoid;
    }

    private static Rectangle scale(Rectangle r, double z) {
        return new Rectangle((int) (r.x * z), (int) (r.y * z), (int) Math.ceil(r.width * z),
                (int) Math.ceil(r.height * z));
    }

    /** 부품·칩 한 칸을 가리는 것은 선 한 칸을 지나는 것보다 이만큼 나쁘다. */
    static final long HARD_WEIGHT = 1000;
    /** 가까운 자리에 빈 곳이 없을 때 더 떨어뜨려 볼 거리(화면 px). */
    static final int[] FARTHER = {0, 20, 40, 60};

    static Rectangle placement(Rectangle target, Dimension bar, List<Rectangle> avoid, Rectangle visible, int gap) {
        return placement(target, bar, avoid, java.util.Collections.emptyList(), visible, gap);
    }

    /**
     * 빠른 속성 창 자리(검토 반영 1, 2차 E, S-04): 대상(선택 테두리 포함) 위(왼쪽 맞춤, 오른쪽 맞춤), 아래(같은 둘),
     * 오른쪽, 왼쪽(위 맞춤, 아래 맞춤) 순으로 놓아 보고, 다음에는 같은 순서로 20·40·60px 더 떨어뜨려 본다. 보이는 영역
     * 안이면서 아무것도 겹치지 않는 첫 자리. 없으면 겹친 넓이(부품·라벨 칩 hard는 {@link #HARD_WEIGHT}배, 선 soft는
     * 1배)가 가장 적은 자리(같으면 앞 순서). 좌표는 캔버스 기준.
     */
    static Rectangle placement(Rectangle target, Dimension bar, List<Rectangle> hard, List<Rectangle> soft,
            Rectangle visible, int gap) {
        List<Rectangle> cands = new ArrayList<>();
        for (int far : FARTHER) {
            int above = target.y - gap - far - bar.height;
            int below = target.y + target.height + gap + far;
            int rightAligned = target.x + target.width - bar.width;
            int right = target.x + target.width + gap + far;
            int left = target.x - gap - far - bar.width;
            cands.add(new Rectangle(target.x, above, bar.width, bar.height));
            cands.add(new Rectangle(rightAligned, above, bar.width, bar.height));
            cands.add(new Rectangle(target.x, below, bar.width, bar.height));
            cands.add(new Rectangle(rightAligned, below, bar.width, bar.height));
            cands.add(new Rectangle(right, target.y, bar.width, bar.height));
            cands.add(new Rectangle(left, target.y, bar.width, bar.height));
            cands.add(new Rectangle(right, target.y + target.height - bar.height, bar.width, bar.height));
            cands.add(new Rectangle(left, target.y + target.height - bar.height, bar.width, bar.height));
        }
        Rectangle best = null;
        long bestScore = Long.MAX_VALUE;
        for (Rectangle c : cands) {
            Rectangle in = new Rectangle(c);
            in.x = Math.max(visible.x, Math.min(in.x, visible.x + visible.width - in.width));
            in.y = Math.max(visible.y, Math.min(in.y, visible.y + visible.height - in.height));
            if (in.intersects(target)) {
                continue; // 보이는 영역에 맞추다 대상 위로 올라왔다
            }
            long score = HARD_WEIGHT * overlap(in, hard) + overlap(in, soft);
            if (score == 0) {
                return in;
            }
            if (score < bestScore) {
                bestScore = score;
                best = in;
            }
        }
        return best != null ? best : cands.get(0);
    }

    private static long overlap(Rectangle in, List<Rectangle> rects) {
        long area = 0;
        for (Rectangle a : rects) {
            Rectangle i = in.intersection(a);
            if (!i.isEmpty()) {
                area += (long) i.width * i.height;
            }
        }
        return area;
    }

    private static JButton small(String text) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.setFont(b.getFont().deriveFont((float) Tokens.FONT_SMALL));
        b.setMargin(new java.awt.Insets(1, 6, 1, 6));
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        return b;
    }

    private static String hex(java.awt.Color c) {
        return String.format("%06X", c.getRGB() & 0xFFFFFF);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
