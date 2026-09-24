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
        Rectangle r = new Rectangle((int) (b.getX() * z), (int) (b.getY() * z), (int) Math.ceil(b.getWidth() * z),
                (int) Math.ceil(b.getHeight() * z));
        Rectangle vis = canvas.getVisibleRect();
        if (!vis.intersects(r)) {
            bar.setVisible(false);
            return;
        }
        Dimension d = bar.getPreferredSize();
        int y = r.y - d.height - 8;
        if (y < vis.y) {
            y = r.y + r.height + 8;
        }
        int x = Math.max(vis.x, Math.min(r.x, vis.x + vis.width - d.width));
        JLayeredPane layer = frame.getLayeredPane();
        Point p = SwingUtilities.convertPoint(canvas, x, y, layer);
        Point top = SwingUtilities.convertPoint(canvas, vis.x, vis.y, layer);
        p.y = Math.max(top.y, Math.min(p.y, top.y + vis.height - d.height));
        bar.setBounds(p.x, p.y, d.width, d.height);
        bar.setVisible(true);
        bar.revalidate();
        layer.repaint();
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
