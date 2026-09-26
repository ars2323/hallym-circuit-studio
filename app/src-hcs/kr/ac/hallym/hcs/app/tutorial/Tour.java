/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tutorial;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Projects;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.Settings;
import kr.ac.hallym.hcs.app.sim.OverflowToolbar;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 첫 실행 튜토리얼(E-10): 창 위를 짚어 가는 단계형 안내. 창의 유리판(glass pane)에 어두운 막을 깔고 그 단계의 대상(부품
 * 목록, 도구 모음, 캔버스, Attributes, Messages 탭…)만 밝게 남긴 뒤 옆에 말풍선(제목·설명·Back·Next)을 둔다. 대상 이름은
 * 영어 그대로, 설명은 한국어다. 처음 실행할 때 한 번 뜨고 Help › Tutorial로 다시 본다. 캐릭터는 첫 장과 마지막 장에만
 * 흰 바탕에 원형 그대로 둔다(CLAUDE.md 8절). 카드형 Getting Started(#23)는 그대로 남는다.
 */
public final class Tour {
    static final String SEEN = "tour.seen";
    static final int PAD = 8;
    static final int GAP = 14;
    static final int BUBBLE_W = 380;
    static final int DIM_ALPHA = 120;

    /** 단계: 문구 키, 캐릭터 그림(없으면 null), 창에서 대상을 찾는 함수(없으면 창 가운데). */
    public static final class Step {
        public final String key;
        final String image;
        final Function<Frame, Component> target;

        Step(String key, String image, Function<Frame, Component> target) {
            this.key = key;
            this.image = image;
            this.target = target;
        }
    }

    static final List<Step> STEPS = new ArrayList<>();
    static {
        STEPS.add(new Step("tour.welcome", "haram-hari-greeting.png", null));
        STEPS.add(new Step("tour.search", null, f -> find(f, x -> x instanceof JTextField
                && Messages.get("toolbox.search").equals(((JTextField) x).getClientProperty("JTextField.placeholderText")))));
        STEPS.add(new Step("tour.tree", null, f -> find(f, x -> x.getClass().getSimpleName().equals("ProjectExplorer"))));
        STEPS.add(new Step("tour.tools", null, f -> parentOf(button(f, "bar.poke"))));
        STEPS.add(new Step("tour.canvas", null, f -> find(f, x -> x instanceof Canvas)));
        STEPS.add(new Step("tour.attributes", null, f -> parentOf(find(f,
                x -> x.getClass().getSimpleName().equals("AttrTable")))));
        STEPS.add(new Step("tour.mips", null, f -> button(f, "bar.program")));
        STEPS.add(new Step("tour.run", null, f -> button(f, "bar.cycle")));
        STEPS.add(new Step("tour.messages", null, f -> tab(f, "messages.tab")));
        STEPS.add(new Step("tour.cycles", null, f -> tab(f, "cycle.tab")));
        STEPS.add(new Step("tour.status", null, f -> parentOf(find(f, x -> x instanceof JLabel
                && Messages.get("bar.legend").equals(((JLabel) x).getText())))));
        STEPS.add(new Step("tour.save", "haram-hari-ok.png", f -> button(f, "bar.save")));
    }

    private Tour() {
    }

    public static List<Step> steps() {
        return Collections.unmodifiableList(STEPS);
    }

    /** 처음 실행이면 맨 앞 창 위에 띄우고 true. Getting Started 카드도 본 것으로 적는다(둘 다 뜨지 않게). */
    public static boolean showOnFirstRun() {
        Settings s = Settings.get();
        if (s.getBoolean(SEEN, false)) {
            return false;
        }
        s.set(SEEN, true);
        s.set(QuickStart.SEEN, true);
        try {
            s.save();
        } catch (IOException e) {
            // 저장하지 못하면 다음에 또 보일 뿐이다
        }
        java.awt.Window top = Projects.getTopFrame();
        if (top instanceof Frame) {
            SwingUtilities.invokeLater(() -> show((Frame) top));
            return true;
        }
        return false;
    }

    /** 창 위에 튜토리얼을 연다(열려 있으면 처음으로). */
    public static Overlay show(Frame frame) {
        JRootPane root = frame.getRootPane();
        Component old = root.getGlassPane();
        if (old instanceof Overlay) {
            ((Overlay) old).go(0);
            return (Overlay) old;
        }
        Overlay o = new Overlay(frame, old);
        root.setGlassPane(o);
        o.setVisible(true);
        o.go(0);
        return o;
    }

    // ---- 대상 찾기 ----

    static Component find(Container root, java.util.function.Predicate<Component> p) {
        if (root == null) {
            return null;
        }
        for (Component c : root.getComponents()) {
            if (p.test(c) && c.isShowing()) {
                return c;
            }
            if (c instanceof Container) {
                Component r = find((Container) c, p);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    static Component button(Frame f, String nameKey) {
        // 도구 모음 항목은 열쇠로(X-02: 좁으면 글자를 숨기거나 » 메뉴로 보낸다: 그때는 » 단추가 대상)
        Component bar = find(f, x -> x instanceof OverflowToolbar);
        if (bar != null) {
            Component c = ((OverflowToolbar) bar).visibleFor(nameKey);
            if (c != null && c.isShowing()) {
                return c;
            }
        }
        String text = Messages.get(nameKey);
        return find(f, x -> x instanceof AbstractButton && text.equals(((AbstractButton) x).getText()));
    }

    static Component parentOf(Component c) {
        return c == null ? null : c.getParent();
    }

    /** 탭 머리 하나를 대상으로: 탭 창 안의 그 탭 머리 사각형을 나타내는 가짜 부품 대신 탭 창 자체를 주고 사각형은 따로 잰다. */
    static Component tab(Frame f, String tabKey) {
        String title = Messages.get(tabKey);
        return find(f, x -> x instanceof JTabbedPane && ((JTabbedPane) x).indexOfTab(title) >= 0);
    }

    /** 대상의 사각형(유리판 좌표). 탭 창이면 그 단계의 탭 머리만. */
    static Rectangle targetRect(Overlay o, Step step, Component target) {
        if (target == null) {
            return null;
        }
        Rectangle r = new Rectangle(0, 0, target.getWidth(), target.getHeight());
        if (target instanceof JTabbedPane) {
            JTabbedPane tabs = (JTabbedPane) target;
            String title = Messages.get(step.key.equals("tour.messages") ? "messages.tab" : "cycle.tab");
            int i = tabs.indexOfTab(title);
            if (i >= 0 && tabs.getBoundsAt(i) != null) {
                r = tabs.getBoundsAt(i);
            }
        }
        Point p = SwingUtilities.convertPoint(target, r.x, r.y, o);
        return new Rectangle(p.x, p.y, r.width, r.height);
    }

    /**
     * 말풍선 자리: 대상 오른쪽, 안 되면 왼쪽, 아래, 위 차례로 두고 창 안으로 밀어 넣는다. 대상이 없으면 가운데.
     * GUI 없이 테스트한다.
     */
    static Rectangle place(Dimension bubble, Rectangle target, Dimension pane) {
        int w = bubble.width;
        int h = bubble.height;
        if (target == null) {
            return clamp(new Rectangle((pane.width - w) / 2, (pane.height - h) / 2, w, h), pane);
        }
        Rectangle[] tries = {
            new Rectangle(target.x + target.width + GAP, target.y, w, h),
            new Rectangle(target.x - GAP - w, target.y, w, h),
            new Rectangle(target.x, target.y + target.height + GAP, w, h),
            new Rectangle(target.x, target.y - GAP - h, w, h),
        };
        for (Rectangle t : tries) {
            Rectangle c = clamp(t, pane);
            if (!c.intersects(target)) {
                return c;
            }
        }
        // 대상이 커서 옆에 못 두면 대상 안쪽 아래에 겹쳐 둔다
        return clamp(new Rectangle(target.x + GAP, target.y + target.height - h - GAP, w, h), pane);
    }

    static Rectangle clamp(Rectangle r, Dimension pane) {
        int x = Math.max(PAD, Math.min(r.x, pane.width - r.width - PAD));
        int y = Math.max(PAD, Math.min(r.y, pane.height - r.height - PAD));
        return new Rectangle(x, y, r.width, r.height);
    }

    // ---- 유리판 ----

    /** 유리판: 어두운 막 + 대상 구멍 + 말풍선. 마우스는 삼킨다(Esc로 닫기). */
    public static final class Overlay extends JComponent {
        private static final long serialVersionUID = 1L;
        final Frame frame;
        final Component previousGlass;
        int at = -1;
        Rectangle hole;
        final JPanel bubble = new JPanel(new BorderLayout(0, Tokens.SPACE_3));
        final JLabel title = new JLabel();
        final JTextArea body = new JTextArea();
        final JLabel pageNo = new JLabel();
        final JLabel picture = new JLabel();
        final JButton prev = new JButton(Messages.get("quickstart.prev"));
        final JButton next = new JButton(Messages.get("quickstart.next"));
        final JButton close = new JButton(Messages.get("tour.close"));

        Overlay(Frame frame, Component previousGlass) {
            this.frame = frame;
            this.previousGlass = previousGlass;
            setLayout(null);
            setOpaque(false);
            MouseAdapter swallow = new MouseAdapter() {
            };
            addMouseListener(swallow);
            addMouseMotionListener(swallow);
            addMouseWheelListener(swallow);
            bubble.setBackground(Tokens.WHITE);
            bubble.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Tokens.NAVY, 2),
                    BorderFactory.createEmptyBorder(Tokens.SPACE_4, Tokens.SPACE_4, Tokens.SPACE_3, Tokens.SPACE_4)));
            title.setForeground(Tokens.NAVY);
            title.setFont(new Font(Tokens.UI_FONT, Font.BOLD, Tokens.FONT_TITLE + 1));
            body.setLineWrap(true);
            body.setWrapStyleWord(true);
            body.setEditable(false);
            body.setFocusable(false);
            body.setOpaque(false);
            body.setBorder(null);
            body.setForeground(Tokens.TEXT);
            body.setFont(new Font(Tokens.UI_FONT, Font.PLAIN, Tokens.FONT_UI + 1));
            pageNo.setForeground(Tokens.TEXT_2);
            JPanel text = new JPanel(new BorderLayout(0, Tokens.SPACE_2));
            text.setOpaque(false);
            text.add(title, BorderLayout.NORTH);
            text.add(body, BorderLayout.CENTER);
            JPanel center = new JPanel(new BorderLayout(Tokens.SPACE_4, 0));
            center.setOpaque(false);
            center.add(text, BorderLayout.CENTER);
            picture.setVerticalAlignment(SwingConstants.TOP);
            center.add(picture, BorderLayout.EAST);
            bubble.add(center, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.SPACE_2, 0));
            buttons.setOpaque(false);
            buttons.add(prev);
            buttons.add(next);
            buttons.add(close);
            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setOpaque(false);
            bottom.add(pageNo, BorderLayout.WEST);
            bottom.add(buttons, BorderLayout.EAST);
            bubble.add(bottom, BorderLayout.SOUTH);
            add(bubble);
            prev.addActionListener(e -> go(at - 1));
            next.addActionListener(e -> go(at + 1));
            close.addActionListener(e -> end());
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "tour.end");
            getActionMap().put("tour.end", new javax.swing.AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    end();
                }
            });
        }

        public int step() {
            return at;
        }

        public Rectangle hole() {
            return hole;
        }

        /** i번째 단계로. */
        public void go(int i) {
            if (i < 0 || i >= STEPS.size()) {
                return;
            }
            at = i;
            Step s = STEPS.get(i);
            Component target = s.target == null ? null : s.target.apply(frame);
            hole = targetRect(this, s, target);
            title.setText(Messages.get(s.key + ".title"));
            body.setText(Messages.get(s.key + ".body"));
            pageNo.setText(Messages.get("quickstart.page", i + 1, STEPS.size()));
            ImageIcon icon = s.image == null ? null : QuickStart.character(s.image);
            picture.setIcon(icon);
            picture.setVisible(icon != null);
            prev.setEnabled(i > 0);
            next.setVisible(i < STEPS.size() - 1);
            close.setText(Messages.get(i < STEPS.size() - 1 ? "tour.close" : "tour.finish"));
            layoutBubble();
            repaint();
        }

        void layoutBubble() {
            int w = BUBBLE_W + (picture.isVisible() ? QuickStart.IMAGE_SIZE + Tokens.SPACE_4 : 0);
            bubble.setSize(w, Integer.MAX_VALUE);
            body.setSize(w - 2 * Tokens.SPACE_4 - 4 - (picture.isVisible() ? QuickStart.IMAGE_SIZE + Tokens.SPACE_4 : 0),
                    Integer.MAX_VALUE);
            Dimension pref = new Dimension(w, bubble.getPreferredSize().height);
            Dimension pane = getSize().width == 0 ? frame.getRootPane().getSize() : getSize();
            if (pane.width == 0) {
                pane = frame.getSize();
            }
            Rectangle r = place(pref, hole, pane);
            bubble.setBounds(r);
            bubble.validate();
        }

        @Override
        public void doLayout() {
            if (at >= 0) {
                Step s = STEPS.get(at);
                Component target = s.target == null ? null : s.target.apply(frame);
                hole = targetRect(this, s, target);
                layoutBubble();
            }
        }

        public void end() {
            JRootPane root = frame.getRootPane();
            if (root.getGlassPane() == this) {
                setVisible(false);
                if (previousGlass != null) {
                    root.setGlassPane(previousGlass);
                }
            }
            frame.repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Area dim = new Area(new Rectangle(0, 0, getWidth(), getHeight()));
                if (hole != null) {
                    Rectangle h = new Rectangle(hole.x - 4, hole.y - 4, hole.width + 8, hole.height + 8);
                    dim.subtract(new Area(new RoundRectangle2D.Float(h.x, h.y, h.width, h.height, 10, 10)));
                    g.setColor(new Color(0, 0, 0, DIM_ALPHA));
                    g.fill(dim);
                    g.setColor(Tokens.TEAL);
                    g.setStroke(new java.awt.BasicStroke(3f));
                    g.draw(new RoundRectangle2D.Float(h.x, h.y, h.width, h.height, 10, 10));
                } else {
                    g.setColor(new Color(0, 0, 0, DIM_ALPHA));
                    g.fill(dim);
                }
                g.setComposite(AlphaComposite.SrcOver);
            } finally {
                g.dispose();
            }
        }
    }
}
