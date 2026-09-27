/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;

import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;
import kr.ac.hallym.hcs.app.window.VerticalBalance;

/**
 * 캔버스 아래 Messages 탭(PLAN.md 6.8: 아래쪽 Console / Messages, #27). 진단 목록을 보이고, 한 줄을 누르면 그곳으로
 * 가 강조한다(4.4). 상태 표시줄의 개수를 누르면 이 탭을 편다. 3단계에서 Console 탭이 옆에 붙는다.
 */
public final class MessagesPanel {
    /** 펼쳤을 때 아래 패널 높이. */
    static final int HEIGHT = 150;

    private final Diagnostics diags;
    private final DefaultListModel<Diagnostic> model = new DefaultListModel<>();
    private final JList<Diagnostic> list = new JList<>(model);
    private final JLabel empty = new JLabel(Messages.get("messages.none"));
    private final JPanel body = new JPanel(new BorderLayout());
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
    private final JLabel status = new JLabel();
    /** 진동(D-02) 메시지가 있을 때만 보이는 Reset 단추 줄. */
    private final JPanel resetBar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 2));
    private final javax.swing.JButton reset = new javax.swing.JButton(Messages.get("messages.reset"));
    private JSplitPane split;
    private boolean userClosed;
    private boolean dragging;
    /** 학생이 정한(끌거나 탭을 열어 요청한) 아래 칸 높이. 창이 좁아 줄였다가 넓어지면 이 값으로 돌아간다(Y-01). */
    private int userBottom = HEIGHT;
    /** 창 높이 때문에 탭 줄만 남기고 접혔다. */
    private boolean autoCollapsed;
    /** 접힌 상태에서 탭을 눌러 폈다: 창 크기가 바뀔 때까지 다시 접지 않는다. */
    private boolean holdOpen;
    /** 캔버스(스크롤 영역)의 지금 높이. 창이 준다. */
    private java.util.function.IntSupplier canvasHeight = () -> -1;
    private String messagesTitle;

    MessagesPanel(Project proj) {
        diags = Diagnostics.of(proj);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setToolTipText(Messages.get("messages.tip"));
        list.setCellRenderer((l, d, i, sel, focus) -> {
            JLabel lab = new JLabel(row(d));
            lab.setOpaque(true);
            lab.setBackground(sel ? Tokens.ERROR_TINT : Tokens.WHITE);
            lab.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            return lab;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i >= 0 && list.getCellBounds(i, i).contains(e.getPoint())) {
                    diags.go(model.get(i));
                }
            }
        });
        empty.setForeground(Tokens.TEXT_2);
        empty.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        messagesTitle = Messages.get("messages.tab");
        tabs.addTab(messagesTitle, body);
        status.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        status.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                open();
            }
        });
        reset.setFocusable(false);
        reset.addActionListener(e -> kr.ac.hallym.hcs.app.record.Recorder.requestReset(proj));
        resetBar.setBackground(Tokens.WHITE);
        resetBar.add(reset);
        diags.addListener(this::update);
        update();
    }

    /** 목록 한 줄: 빨간 점과 문장. */
    static String row(Diagnostic d) {
        String red = String.format("%06X", Tokens.ERROR.getRGB() & 0xFFFFFF);
        return "<html><span style='color:#" + red + "'>●</span>&nbsp; " + esc(d.message()) + "</html>";
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 상태 표시줄 글자: 개수(이름, 영어). */
    static String statusText(int n) {
        return Messages.get("messages.count", n);
    }

    private void update() {
        List<Diagnostic> ds = diags.list();
        Diagnostic keep = list.getSelectedValue();
        model.clear();
        for (Diagnostic d : ds) {
            model.addElement(d);
        }
        if (keep != null) {
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i).kind == keep.kind && model.get(i).args().equals(keep.args())) {
                    list.setSelectedIndex(i);
                }
            }
        }
        body.removeAll();
        body.add(ds.isEmpty() ? empty : new JScrollPane(list), ds.isEmpty() ? BorderLayout.NORTH : BorderLayout.CENTER);
        boolean osc = false;
        for (Diagnostic d : ds) {
            osc |= d.kind == Diagnostic.Kind.OSCILLATION;
        }
        if (osc) {
            body.add(resetBar, BorderLayout.SOUTH);
        }
        body.revalidate();
        body.repaint();
        status.setText(statusText(ds.size()));
        status.setForeground(ds.isEmpty() ? Tokens.TEXT_2 : Tokens.ERROR_TEXT);
        badge();
    }

    /** 접혀 있을 때 새 메시지는 탭 이름의 개수 배지로만 알린다(자동으로 펴서 캔버스를 가리지 않는다, Y-01). */
    private void badge() {
        int n = model.size();
        tabs.setTitleAt(0, autoCollapsed && n > 0 ? messagesTitle + " (" + n + ")" : messagesTitle);
    }

    /** 목록의 메시지 수(테스트). */
    public int messageCount() {
        return model.size();
    }

    /** Messages 탭 이름(테스트: 접혔을 때 "Messages (N)"). */
    public String messagesTabTitle() {
        return tabs.getTitleAt(0);
    }

    /** 아래 패널을 펴고 Messages 탭을 고른다(학생이 눌렀다). */
    void open() {
        tabs.setSelectedIndex(0);
        expand(HEIGHT);
    }

    /** 학생의 뜻으로 편다: 요청 높이를 기억하고, 접혀 있었으면 창 크기가 바뀔 때까지 열어 둔다. */
    private void expand(int height) {
        if (split == null) {
            return;
        }
        userBottom = Math.max(userBottom, height);
        userClosed = false;
        holdOpen = true;
        autoCollapsed = false;
        int h = split.getHeight();
        if (h > 0 && bottomHeight() < height) {
            split.setDividerLocation(Math.max(h / 3, h - height - split.getDividerSize()));
        }
        badge();
    }

    /** 접었을 때 남는 탭 줄 높이. */
    private int stripHeight() {
        try {
            java.awt.Rectangle r = tabs.getBoundsAt(0);
            return r == null ? 30 : r.y + r.height + 2;
        } catch (RuntimeException e) {
            return 30;
        }
    }

    /** 창 크기에 맞춰 아래 칸을 잡는다(Y-01): 캔버스가 창 내부 높이의 절반 이상을 갖도록 줄이고, 모자라면 접는다. */
    void balance() {
        int h = split.getHeight();
        if (h <= 0 || dragging || userClosed) {
            return;
        }
        javax.swing.JRootPane root = javax.swing.SwingUtilities.getRootPane(split);
        int contentH = root == null ? h : root.getContentPane().getHeight();
        int canvasH = canvasHeight.getAsInt();
        int topH = split.getDividerLocation();
        int chrome = canvasH > 0 && topH > canvasH ? topH - canvasH : 0;
        int strip = stripHeight();
        int want = holdOpen ? userBottom : VerticalBalance.bottom(h, contentH, chrome, userBottom, split.getDividerSize(),
                strip);
        want = Math.min(want, Math.max(0, h - split.getDividerSize()));
        autoCollapsed = !holdOpen && VerticalBalance.collapsed(want, strip);
        if (Math.abs(bottomHeight() - want) > 1) {
            split.setDividerLocation(h - want - split.getDividerSize());
        }
        badge();
    }

    /** 창이 아래 칸 규칙에 쓸 캔버스 높이를 준다. */
    public void setCanvasHeight(java.util.function.IntSupplier supplier) {
        canvasHeight = supplier;
    }

    /** 창 높이 때문에 접혀 있는가(테스트). */
    public boolean isAutoCollapsed() {
        return autoCollapsed;
    }

    /** 학생이 정한 아래 칸 높이(테스트). */
    public int userBottom() {
        return userBottom;
    }

    /** 창 크기 규칙을 지금 적용한다(테스트). */
    public void rebalance() {
        holdOpen = false;
        balance();
    }

    /** 캔버스 영역 아래에 붙인다. */
    JComponent wrap(JComponent center) {
        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, center, tabs);
        split.putClientProperty(MessagesPanel.class, this);
        split.setResizeWeight(1.0);
        split.setBorder(null);
        split.setContinuousLayout(true);
        tabs.setMinimumSize(new java.awt.Dimension(0, 0));
        tabs.setPreferredSize(new java.awt.Dimension(100, HEIGHT));
        // 창이 크기를 얻기 전(높이 0)에 먼저 배치되거나 나눔선이 창 밖으로 밀리면 resizeWeight 1.0 때문에 아래 칸이 0으로
        // 굳는다(X-01 뒤 새 창에서 보였다). 학생이 직접 끌어 닫은 것이 아니면 창 크기가 바뀔 때 기본 높이로 되돌린다
        javax.swing.plaf.basic.BasicSplitPaneUI ui = split.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI
                ? (javax.swing.plaf.basic.BasicSplitPaneUI) split.getUI() : null;
        if (ui != null) {
            ui.getDivider().addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragging = true;
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragging = false;
                    int b = bottomHeight();
                    userClosed = b < HEIGHT / 2; // 직접 끌어 닫았다: 창 크기가 바뀌어도 그대로 둔다
                    if (!userClosed) {
                        userBottom = b; // 직접 정한 높이: 창이 넓어지면 이 값으로
                        holdOpen = true;
                        autoCollapsed = false;
                    }
                    badge();
                }
            });
        }
        // 접힌 상태에서 탭을 누르면 편다(Y-01)
        tabs.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (autoCollapsed && tabs.indexAtLocation(e.getX(), e.getY()) >= 0) {
                    javax.swing.SwingUtilities.invokeLater(() -> expand(userBottom));
                }
            }
        });
        // 프로그램이 나눔선을 밀어 아래 칸이 사라진 경우(끌기 중이 아닐 때)도 규칙대로 되돌린다
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> javax.swing.SwingUtilities
                .invokeLater(() -> {
                    if (split.getHeight() > 0 && !userClosed && !dragging && bottomHeight() < 0) {
                        balance();
                    }
                }));
        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                holdOpen = false; // 창 크기가 바뀌면 규칙을 다시 적용한다
                balance();
            }
        });
        return split;
    }

    /** 창 안의 Messages 패널(테스트). 없으면 null. */
    public static MessagesPanel of(java.awt.Component root) {
        if (root instanceof JComponent && ((JComponent) root).getClientProperty(MessagesPanel.class) instanceof MessagesPanel) {
            return (MessagesPanel) ((JComponent) root).getClientProperty(MessagesPanel.class);
        }
        if (root instanceof java.awt.Container) {
            for (java.awt.Component c : ((java.awt.Container) root).getComponents()) {
                MessagesPanel m = of(c);
                if (m != null) {
                    return m;
                }
            }
        }
        return null;
    }

    /** 아래 탭 칸 높이를 직접 둔다(테스트: 잘못 굳은 상태를 만든다). */
    public void setBottomHeight(int height) {
        if (split != null) {
            split.setDividerLocation(split.getHeight() - height - split.getDividerSize());
        }
    }

    /** 아래 탭 칸의 지금 높이(테스트). */
    public int bottomHeight() {
        return split == null ? 0 : split.getHeight() - split.getDividerLocation() - split.getDividerSize();
    }

    /** 진동 Reset 단추가 보이는가(테스트). */
    boolean resetShown() {
        return resetBar.getParent() == body;
    }

    /** 진동 Reset 단추(테스트). */
    javax.swing.JButton resetButton() {
        return reset;
    }

    /** 지금 목록(테스트). */
    List<Diagnostic> rows() {
        java.util.List<Diagnostic> ret = new java.util.ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            ret.add(model.get(i));
        }
        return ret;
    }

    /** comp 탭을 고르고, 아래 패널이 height보다 낮으면 그만큼 편다(Cycle View 탭은 표가 들어갈 높이). */
    public void openTab(java.awt.Component comp, int height) {
        tabs.setSelectedComponent(comp);
        expand(height);
    }

    /** 캔버스 아래 탭들(Cycle View 탭 등이 더해진다). */
    public JTabbedPane tabs() {
        return tabs;
    }

    public JLabel statusLabel() {
        return status;
    }

    /** 창을 만들 때: center(캔버스와 속성 패널) 아래에 Messages 탭을 붙인 컴포넌트를 돌려준다. */
    public static MessagesPanel install(Frame frame) {
        return new MessagesPanel(frame.getProject());
    }

    public JComponent around(JComponent center) {
        return wrap(center);
    }
}
