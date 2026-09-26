/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tabs;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 창 위쪽의 탭 막대(#68). 윗줄은 파일 탭(모든 창이 같은 {@link TabModel}을 그린다), 아랫줄은 이 파일에서 연
 * 회로 탭과 시뮬레이션 경로({@code main › datapath}). 저장 안 된 파일은 이름 앞에 ● 글자와 툴팁으로 알린다
 * (색만으로 알리지 않는다).
 */
public final class FileTabBar extends JPanel {
    private static final long serialVersionUID = 1L;
    static final String DIRTY = "● ";
    static final String SEP = " › ";
    /** 분리한 창 표시(P-06): Updated 배지와 같은 글 배지(글꼴에 없는 기호를 쓰지 않는다). */
    static final String DETACHED = "\u00B7 ";

    private final Frame frame;
    private final Project proj;
    private final JTabbedPane files = strip();
    private final JTabbedPane circuits = strip();
    private final JPanel path = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    /** 이 파일에서 연 회로(연 순서). 지금 회로는 늘 들어 있다. */
    private final Set<Circuit> openCircuits = new LinkedHashSet<>();
    private boolean updating;
    // Logisim과 탭 모델에 붙이는 리스너. Logisim은 약한 참조로 두므로 필드로 붙잡는다.
    private final TabModel.Listener modelListener = () -> SwingUtilities.invokeLater(this::updateFiles);
    private final ProjectListener projectListener = e -> {
        if (e.getAction() == ProjectEvent.ACTION_SET_CURRENT || e.getAction() == ProjectEvent.ACTION_SET_STATE
                || e.getAction() == ProjectEvent.ACTION_SET_FILE) {
            SwingUtilities.invokeLater(this::updateCircuits);
        }
    };

    public FileTabBar(Frame frame) {
        super(new BorderLayout());
        this.frame = frame;
        this.proj = frame.getProject();
        setBackground(Tokens.WHITE);
        path.setOpaque(false);
        path.setBorder(BorderFactory.createEmptyBorder(0, Tokens.SPACE_3, 0, Tokens.SPACE_3));
        JPanel lower = new JPanel(new BorderLayout());
        lower.setOpaque(false);
        lower.add(circuits, BorderLayout.CENTER);
        lower.add(path, BorderLayout.EAST);
        add(files, BorderLayout.NORTH);
        add(lower, BorderLayout.SOUTH);

        TabModel<Project> model = FileTabs.get().model();
        files.addChangeListener(e -> {
            if (!updating && files.getSelectedIndex() >= 0) {
                model.activate(model.tabs().get(files.getSelectedIndex()).key());
            }
        });
        closable(files, (tabs, i) -> FileTabs.get().close(model.tabs().get(i).key()));
        // 파일 탭을 캔버스로 끌어 놓으면 그 파일을 라이브러리로 불러와 main 회로를 놓는다(P-03)
        MouseAdapter drag = new MouseAdapter() {
            int from = -1;

            @Override
            public void mousePressed(MouseEvent e) {
                from = files.indexAtLocation(e.getX(), e.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (from >= 0) {
                    files.setCursor(java.awt.Cursor.getPredefinedCursor(canvasPoint(e) != null
                            ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                int i = from;
                from = -1;
                files.setCursor(null);
                if (i < 0 || i >= model.tabs().size()) {
                    return;
                }
                com.cburch.logisim.data.Location at = canvasPoint(e);
                if (at != null) {
                    dropTab(model.tabs().get(i).key(), at);
                    return;
                }
                // 창 밖으로 끌어 놓으면 그 탭을 제 창으로 분리한다(P-06)
                java.awt.Point onScreen = e.getLocationOnScreen();
                if (!frame.getBounds().contains(onScreen) && !model.isDetached(model.tabs().get(i).key())) {
                    FileTabs.get().detach(model.tabs().get(i).key());
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int i = files.indexAtLocation(e.getX(), e.getY());
                    if (i >= 0 && i < model.tabs().size()) {
                        tabMenu(model.tabs().get(i).key()).show(files, e.getX(), e.getY());
                    }
                }
            }
        };
        files.addMouseListener(drag);
        files.addMouseMotionListener(drag);

        proj.addProjectListener(projectListener);
        circuits.addChangeListener(e -> {
            int i = circuits.getSelectedIndex();
            if (!updating && i >= 0) {
                Circuit c = new ArrayList<>(openCircuits).get(i);
                if (c != proj.getCurrentCircuit()) {
                    proj.setCurrentCircuit(c);
                }
            }
        });
        closable(circuits, (tabs, i) -> {
            Circuit c = new ArrayList<>(openCircuits).get(i);
            if (c != proj.getCurrentCircuit()) {
                openCircuits.remove(c);
                updateCircuits();
            }
        });
        updateFiles();
        updateCircuits();
    }

    /** 파일 탭 우클릭 메뉴(P-06): Detach Tab / Attach Tab, View Side by Side, Close. */
    javax.swing.JPopupMenu tabMenu(Project p) {
        javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
        boolean det = FileTabs.get().model().isDetached(p);
        javax.swing.JMenuItem toggle = new javax.swing.JMenuItem(Messages.get(det ? "tabs.attach" : "tabs.detach"));
        toggle.addActionListener(e -> {
            if (det) {
                FileTabs.get().attach(p);
            } else {
                FileTabs.get().detach(p);
            }
        });
        m.add(toggle);
        javax.swing.JMenuItem side = new javax.swing.JMenuItem(Messages.get("tabs.sideBySide"));
        side.setEnabled(FileTabs.get().model().size() > 1);
        side.addActionListener(e -> FileTabs.get().sideBySide(p));
        m.add(side);
        m.addSeparator();
        javax.swing.JMenuItem close = new javax.swing.JMenuItem(Messages.get("tabs.close"));
        close.addActionListener(e -> FileTabs.get().close(p));
        m.add(close);
        return m;
    }

    /** 탭 막대 위의 마우스 자리가 이 창 캔버스 위면 회로 좌표, 아니면 null. */
    private com.cburch.logisim.data.Location canvasPoint(MouseEvent e) {
        com.cburch.logisim.gui.main.Canvas canvas = frame.getCanvas();
        java.awt.Point p = SwingUtilities.convertPoint(files, e.getPoint(), canvas);
        java.awt.Rectangle vis = canvas.getVisibleRect();
        if (!canvas.isShowing() || !vis.contains(p)) {
            return null;
        }
        return canvas.hcsToCircuit(p.x, p.y); // 배율과 화면 맞춤의 원점 이동(S-10)
    }

    /** 탭 other의 파일을 이 창 회로의 at에 놓는다. 같은 파일이면 아무것도 안 한다. */
    boolean dropTab(Project other, com.cburch.logisim.data.Location at) {
        java.io.File f = kr.ac.hallym.hcs.app.libs.OpenFileLibraries.fileOf(other);
        if (other == proj || f == null) {
            if (other != proj) {
                kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, Messages.get("libs.unsaved"));
            }
            return false;
        }
        return kr.ac.hallym.hcs.app.palette.PaletteActions.dropFile(proj, f,
                other.getLogisimFile().getMainCircuit().getName(), at);
    }

    private static JTabbedPane strip() {
        JTabbedPane t = new JTabbedPane();
        t.putClientProperty("JTabbedPane.tabType", "underlined");
        t.putClientProperty("JTabbedPane.showContentSeparator", true);
        t.putClientProperty("JTabbedPane.tabHeight", 28);
        t.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        return t;
    }

    private static void closable(JTabbedPane t, BiConsumer<JTabbedPane, Integer> onClose) {
        t.putClientProperty("JTabbedPane.tabClosable", true);
        t.putClientProperty("JTabbedPane.tabCloseCallback", onClose);
        t.putClientProperty("JTabbedPane.tabCloseToolTipText", Messages.get("tabs.close"));
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    void updateFiles() {
        updating = true;
        try {
            List<TabModel.Tab<Project>> tabs = FileTabs.get().model().tabs();
            java.util.Map<Project, String> suffix = FileTabs.get().suffixes(); // V-05
            while (files.getTabCount() > tabs.size()) {
                files.removeTabAt(files.getTabCount() - 1);
            }
            for (int i = 0; i < tabs.size(); i++) {
                TabModel.Tab<Project> t = tabs.get(i);
                boolean det = FileTabs.get().model().isDetached(t.key());
                String title = (t.dirty() ? DIRTY : "") + t.title() + (t.updated() ? " · " + Messages.get(
                        "tabs.updatedBadge") : "") + (det ? " " + DETACHED + Messages.get("tabs.detachedBadge") : "");
                String s = suffix.get(t.key());
                if (s != null) {
                    // 같은 이름의 다른 파일: 구분되는 폴더 이름을 흐리게(V-05)
                    title = "<html>" + esc(title) + " <span style='color:#" + String.format("%06X",
                            kr.ac.hallym.hcs.app.theme.Tokens.TEXT_2.getRGB() & 0xFFFFFF) + "'>" + FileTabs.SUFFIX_SEP
                            + " " + esc(s) + "</span></html>";
                }
                String tip = t.file() == null ? Messages.get("tabs.unsaved") : t.file().getPath();
                if (det) {
                    tip = tip + " — " + Messages.get("tabs.detachedTip");
                }
                if (t.dirty()) {
                    tip = tip + " (" + Messages.get("tabs.modified") + ")";
                }
                if (t.updated()) {
                    tip = tip + " — " + Messages.get("tabs.updatedTip");
                }
                if (i >= files.getTabCount()) {
                    files.addTab(title, empty());
                }
                files.setTitleAt(i, title);
                files.setToolTipTextAt(i, tip);
                if (t.key() == FileTabs.get().model().active()) {
                    files.setSelectedIndex(i);
                }
            }
        } finally {
            updating = false;
        }
    }

    void updateCircuits() {
        Circuit cur = proj.getCurrentCircuit();
        List<Circuit> all = proj.getLogisimFile() == null ? new ArrayList<>() : proj.getLogisimFile().getCircuits();
        openCircuits.retainAll(all); // 지운 회로는 뺀다
        if (cur != null) {
            openCircuits.add(cur);
        }
        updating = true;
        try {
            circuits.removeAll();
            int sel = -1;
            for (Circuit c : openCircuits) {
                circuits.addTab(c.getName(), empty());
                if (c == cur) {
                    sel = circuits.getTabCount() - 1;
                }
            }
            if (sel >= 0) {
                circuits.setSelectedIndex(sel);
            }
        } finally {
            updating = false;
        }
        updatePath();
    }

    /** 시뮬레이션 경로: 맨 위 회로부터 지금 보는 회로까지. 앞 단계를 누르면 그 상태로 올라간다. */
    private void updatePath() {
        path.removeAll();
        List<CircuitState> chain = new ArrayList<>();
        for (CircuitState s = proj.getCircuitState(); s != null; s = s.getParentState()) {
            chain.add(0, s);
        }
        if (chain.size() > 1) {
            for (int i = 0; i < chain.size(); i++) {
                CircuitState s = chain.get(i);
                JLabel l = new JLabel(s.getCircuit().getName());
                boolean last = i == chain.size() - 1;
                l.setForeground(last ? Tokens.NAVY : Tokens.BLUE);
                if (!last) {
                    l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    l.setToolTipText(Messages.get("tabs.goUp", s.getCircuit().getName()));
                    l.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            proj.setCircuitState(s);
                        }
                    });
                }
                path.add(l);
                if (!last) {
                    JLabel sep = new JLabel(SEP);
                    sep.setForeground(Tokens.TEXT_MUTED);
                    path.add(sep);
                }
            }
        }
        path.revalidate();
        path.repaint();
    }

    /** 창이 만들어질 때 탭 모델을 따라가고, 창이 닫히면(dispose) 놓는다. 숨긴 창은 계속 따라간다. */
    @Override
    public void addNotify() {
        super.addNotify();
        FileTabs.get().model().addListener(modelListener);
        updateFiles();
    }

    @Override
    public void removeNotify() {
        FileTabs.get().model().removeListener(modelListener);
        super.removeNotify();
    }

    /** 탭 제목만 쓰므로 내용은 높이 0. */
    private static JPanel empty() {
        JPanel p = new JPanel();
        p.setPreferredSize(new java.awt.Dimension(0, 0));
        return p;
    }

    Frame frame() {
        return frame;
    }
}
