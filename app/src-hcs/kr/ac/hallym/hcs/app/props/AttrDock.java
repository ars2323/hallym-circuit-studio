/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.props;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.Timer;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.Settings;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 오른쪽 도킹 속성 패널(#74, PLAN.md 11.4). 원조 속성 표를 캔버스 오른쪽에 두고 머리의 단추로 접는다. 접으면
 * 좁은 띠만 남고 단추로 다시 편다. 폭과 접힘은 앱 환경설정에 둔다(.circ와 무관).
 */
public final class AttrDock {
    static final String WIDTH = "attrDock.width";
    static final String COLLAPSED = "attrDock.collapsed";
    static final String QUICK = "quickbar.show";

    private final JPanel root = new JPanel(new BorderLayout());
    private final JComponent center;
    private final JComponent body;
    private final JPanel panel = new JPanel(new BorderLayout());
    private final JPanel strip = new JPanel(new BorderLayout());
    private final JSplitPane split;
    private final JCheckBox quick = new JCheckBox(Messages.get("dock.quick"));
    private final Timer saver = new Timer(600, e -> save());
    private Runnable onQuickToggle = () -> { };
    private boolean collapsed;

    public AttrDock(JComponent center, JComponent body) {
        this.center = center;
        this.body = body;
        root.putClientProperty(AttrDock.class, this);
        saver.setRepeats(false);

        JLabel title = new JLabel(Messages.get("dock.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setForeground(Tokens.TEXT);
        JButton hide = chevron(true);
        hide.setToolTipText(Messages.get("dock.collapse"));
        hide.addActionListener(e -> setCollapsed(true));
        quick.setSelected(quickBarShown());
        quick.setFocusable(false);
        quick.setOpaque(false);
        quick.setFont(quick.getFont().deriveFont((float) Tokens.FONT_SMALL));
        quick.setToolTipText(Messages.get("dock.quickTip"));
        quick.addActionListener(e -> {
            Settings.get().set(QUICK, quick.isSelected());
            saver.restart();
            onQuickToggle.run();
        });
        JPanel head = new JPanel(new BorderLayout(Tokens.SPACE_1, 0));
        head.setBackground(Tokens.WINDOW);
        head.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Tokens.BORDER),
                BorderFactory.createEmptyBorder(2, Tokens.SPACE_2, 2, 2)));
        head.add(title, BorderLayout.WEST);
        head.add(hide, BorderLayout.EAST);
        JPanel foot = new JPanel(new BorderLayout());
        foot.setBackground(Tokens.WINDOW);
        foot.setBorder(BorderFactory.createEmptyBorder(2, Tokens.SPACE_1, 2, Tokens.SPACE_1));
        foot.add(quick, BorderLayout.WEST);
        panel.add(head, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        panel.add(foot, BorderLayout.SOUTH);
        panel.setMinimumSize(new Dimension(160, 0));

        JButton show = chevron(false);
        show.setToolTipText(Messages.get("dock.expand"));
        show.addActionListener(e -> setCollapsed(false));
        strip.setBackground(Tokens.WINDOW);
        strip.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Tokens.BORDER));
        strip.add(show, BorderLayout.NORTH);

        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(1.0);
        split.setBorder(null);
        split.setContinuousLayout(true);
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (!collapsed && split.getWidth() > 0 && panel.getWidth() > 0) {
                Settings.get().set(WIDTH, panel.getWidth());
                saver.restart();
            }
        });
        collapsed = Settings.get().getBoolean(COLLAPSED, false);
        layout();
    }

    /** 창 안의 속성 패널(없으면 null). */
    public static AttrDock find(java.awt.Component root) {
        if (root instanceof JComponent && ((JComponent) root).getClientProperty(AttrDock.class) instanceof AttrDock) {
            return (AttrDock) ((JComponent) root).getClientProperty(AttrDock.class);
        }
        if (root instanceof java.awt.Container) {
            for (java.awt.Component c : ((java.awt.Container) root).getComponents()) {
                AttrDock d = find(c);
                if (d != null) {
                    return d;
                }
            }
        }
        return null;
    }

    /** 빠른 속성 창을 보일지(앱 환경설정, 기본 켬). */
    public static boolean quickBarShown() {
        return Settings.get().getBoolean(QUICK, true);
    }

    void onQuickToggle(Runnable r) {
        onQuickToggle = r;
    }

    /** 창에 넣을 부품(캔버스 영역 + 오른쪽 속성 패널). */
    public JComponent component() {
        return root;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean c) {
        if (c == collapsed) {
            return;
        }
        collapsed = c;
        Settings.get().set(COLLAPSED, c);
        saver.restart();
        layout();
    }

    /** "모든 속성": 펴고 속성 표로 초점을 옮긴다. */
    public void showAll() {
        setCollapsed(false);
        body.requestFocusInWindow();
    }

    private void layout() {
        root.removeAll();
        if (collapsed) {
            root.add(center, BorderLayout.CENTER);
            root.add(strip, BorderLayout.EAST);
        } else {
            split.setLeftComponent(center);
            split.setRightComponent(panel);
            int w = Math.max(160, Settings.get().getInt(WIDTH, 240));
            panel.setPreferredSize(new Dimension(w, 0));
            root.add(split, BorderLayout.CENTER);
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (split.getWidth() > w) {
                    split.setDividerLocation(split.getWidth() - w - split.getDividerSize());
                }
            });
        }
        root.revalidate();
        root.repaint();
    }

    private static void save() {
        try {
            Settings.get().save();
        } catch (java.io.IOException e) {
            // 환경설정을 못 써도 편집은 계속한다
        }
    }

    private static JButton chevron(boolean right) {
        JButton b = new JButton(new Icon() {
            public int getIconWidth() {
                return 14;
            }

            public int getIconHeight() {
                return 14;
            }

            public void paintIcon(java.awt.Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Tokens.TEXT_2);
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.translate(x, y);
                int a = right ? 5 : 9;
                int b2 = right ? 9 : 5;
                g.drawLine(a, 3, b2, 7);
                g.drawLine(b2, 7, a, 11);
                g.dispose();
            }
        });
        b.setFocusable(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setMargin(new java.awt.Insets(2, 2, 2, 2));
        return b;
    }
}
