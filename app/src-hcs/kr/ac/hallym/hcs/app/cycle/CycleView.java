/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.record.Recorder;
import kr.ac.hallym.hcs.app.record.Recording;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 캔버스 아래 Cycle View 탭(C-02, C-03, PLAN.md 5.1·5.2). 조작 막대(이전·다음·마지막 사이클, 지금 사이클)와 사이클 표.
 * 표의 열 하나가 한 사이클이고, 머리는 사이클 번호·PC·명령어(.s 원래 줄, 없으면 디스어셈블)다. 줄은 회로도의 선을
 * 오른쪽 클릭해 "Add to Cycle View"로 더한 신호다(1비트는 반 사이클 단위 파형, 버스는 16진 값). 열을 누르면 회로도
 * 전체가 그 사이클 값으로 바뀐다({@link Recorder#view}).
 */
public final class CycleView {
    static final int COL_W = 132;
    static final int ROW_H = 22;
    static final int NAME_W = 180;
    static final int HEAD_ROWS = 3;

    private static final Map<Project, CycleView> ALL = new WeakHashMap<>();

    /** 약한 참조: ALL(WeakHashMap)의 값이 키인 프로젝트를 붙잡지 않게. */
    private final java.lang.ref.WeakReference<Project> projRef;
    private final Recorder recorder;
    private final List<CycleModel.Signal> signals = new ArrayList<>();
    private CycleModel model;

    private final JPanel panel = new JPanel(new BorderLayout());
    private final Body body = new Body();
    private final Head head = new Head();
    private final RowNames rowNames = new RowNames();
    private final JScrollPane scroll = new JScrollPane(body);
    private final JLabel position = new JLabel();
    private final JLabel notice = new JLabel(Messages.get("cycle.pastNotice"));
    private final JLabel empty = new JLabel(Messages.get("cycle.empty"));
    private final JButton prev = new JButton(Messages.get("cycle.prev"));
    private final JButton next = new JButton(Messages.get("cycle.next"));
    private final JButton latest = new JButton(Messages.get("cycle.latest"));
    private boolean follow = true;
    // Recorder는 청취자를 강하게 잡지만, 창이 닫히면 함께 사라지도록 필드로 둔다
    private final Recorder.Listener recListener = r -> SwingUtilities.invokeLater(this::refresh);

    private CycleView(Project proj) {
        this.projRef = new java.lang.ref.WeakReference<>(proj);
        this.recorder = Recorder.of(proj);
        recorder.addListener(recListener);
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, Tokens.FONT_SMALL);
        body.setFont(mono);
        head.setFont(mono);
        rowNames.setFont(new Font(Tokens.UI_FONT, Font.PLAIN, Tokens.FONT_SMALL));
        scroll.setColumnHeaderView(head);
        scroll.setRowHeaderView(rowNames);
        scroll.setCorner(JScrollPane.UPPER_LEFT_CORNER, new CornerLabels());
        scroll.getHorizontalScrollBar().setUnitIncrement(COL_W / 2);
        scroll.getVerticalScrollBar().setUnitIncrement(ROW_H);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_2, 2));
        bar.setBackground(Tokens.WINDOW);
        prev.setToolTipText(Messages.get("cycle.prevTip"));
        next.setToolTipText(Messages.get("cycle.nextTip"));
        latest.setToolTipText(Messages.get("cycle.latestTip"));
        prev.addActionListener(e -> step(-1));
        next.addActionListener(e -> step(+1));
        latest.addActionListener(e -> showLatest());
        position.setForeground(Tokens.TEXT_2);
        notice.setForeground(Tokens.AMBER_TEXT);
        bar.add(prev);
        bar.add(next);
        bar.add(latest);
        bar.add(position);
        bar.add(notice);
        empty.setForeground(Tokens.TEXT_2);
        empty.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        empty.setVerticalAlignment(SwingConstants.TOP);
        panel.add(bar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        MouseAdapter pick = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    int c = columnAt(e.getX());
                    if (c >= 0) {
                        view(c);
                    }
                    body.requestFocusInWindow();
                }
            }
        };
        body.addMouseListener(pick);
        head.addMouseListener(pick);
        rowNames.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeRowMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeRowMenu(e);
            }
        });
        body.setFocusable(true);
        body.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "hcs.prevCycle");
        body.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "hcs.nextCycle");
        body.getActionMap().put("hcs.prevCycle", action(() -> step(-1)));
        body.getActionMap().put("hcs.nextCycle", action(() -> step(+1)));
        refresh();
    }

    private static AbstractAction action(Runnable r) {
        return new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                r.run();
            }
        };
    }

    /** 표가 보일 아래 패널 높이: 조작 막대, 머리 세 줄, 신호 다섯 줄. */
    static final int OPEN_HEIGHT = 40 + (HEAD_ROWS + 5) * ROW_H + 44;

    private kr.ac.hallym.hcs.app.diag.MessagesPanel bottom;

    /** 창을 만들 때 캔버스 아래 탭에 붙인다. */
    public static CycleView install(Frame frame, kr.ac.hallym.hcs.app.diag.MessagesPanel bottom) {
        CycleView v = of(frame.getProject());
        v.bottom = bottom;
        bottom.tabs().addTab(Messages.get("cycle.tab"), v.panel);
        return v;
    }

    /** Cycle View 탭을 앞으로 가져오고 표가 보일 만큼 아래 패널을 편다. */
    public void open() {
        if (bottom != null) {
            bottom.openTab(panel, OPEN_HEIGHT);
        } else if (panel.getParent() instanceof JTabbedPane) {
            ((JTabbedPane) panel.getParent()).setSelectedComponent(panel);
        }
    }

    public static CycleView of(Project proj) {
        synchronized (ALL) {
            CycleView v = ALL.get(proj);
            if (v == null) {
                v = new CycleView(proj);
                ALL.put(proj, v);
            }
            return v;
        }
    }

    public JComponent component() {
        return panel;
    }

    // ---- 모델 ----

    /** 지금 기록의 표 모델. 기록이 없으면 null. 회로·.s가 바뀔 수 있어 매번 가볍게 다시 만든다. */
    public CycleModel model() {
        Recording r = recorder.current();
        if (r == null) {
            return null;
        }
        Circuit root = r.circuit();
        CycleModel.Cpu cpu = CycleModel.findCpu(root);
        Project proj = projRef.get();
        File circ = proj == null || proj.getLogisimFile() == null ? null
                : proj.getLogisimFile().getLoader().getMainFile();
        File src = CycleModel.sourceFile(cpu, circ);
        ProgramSource ps = src == null ? ProgramSource.EMPTY : ProgramSource.of(src);
        if (model == null || model.recording() != r || model.cpu() == null != (cpu == null)
                || model.source() != ps || cpu != null && model.cpu().imem != cpu.imem) {
            model = new CycleModel(root, r, cpu, ps);
        }
        for (CycleModel.Signal s : signals) {
            model.add(s);
        }
        return model;
    }

    /** 줄을 더한다(회로도 우클릭 "Add to Cycle View"). 탭을 앞으로 가져온다. */
    public void add(CycleModel.Signal s) {
        if (s == null || signals.contains(s)) {
            return;
        }
        signals.add(s);
        if (model != null) {
            model.add(s);
        }
        open();
        refresh();
    }

    /** 선 w(지금 보는 회로 상태의 인스턴스 경로)를 줄로 더한다. */
    public static void addWire(Project proj, Circuit circuit, com.cburch.logisim.circuit.Wire w) {
        CycleView v = of(proj);
        Recording r = v.recorder.current();
        Circuit root = r != null ? r.circuit() : circuit;
        v.add(CycleModel.signalFor(root, Recorder.pathOf(proj.getCircuitState()), circuit, w));
    }

    /** 포트 자리 at(터널·핀 등)을 줄로 더한다. */
    public static void addPort(Project proj, Circuit circuit, Location at) {
        CycleView v = of(proj);
        Recording r = v.recorder.current();
        Circuit root = r != null ? r.circuit() : circuit;
        v.add(CycleModel.signalFor(root, Recorder.pathOf(proj.getCircuitState()), circuit, at));
    }

    List<CycleModel.Signal> signals() {
        return signals;
    }

    // ---- 조작 ----

    /** 열 c를 본다: 회로도 전체가 그 사이클 값으로(C-03). */
    public void view(int cycle) {
        CycleModel m = model();
        if (m == null || m.isEmpty()) {
            return;
        }
        int c = Math.max(m.firstCycle(), Math.min(m.lastCycle(), cycle));
        follow = c == m.lastCycle();
        recorder.view(CycleModel.stepOf(c));
        refresh();
        // 고른 열이 보이는 영역 가운데 오게
        int vw = scroll.getViewport().getWidth();
        body.scrollRectToVisible(new Rectangle(Math.max(0, x(m, c) - (vw - COL_W) / 2), 0, Math.max(COL_W, vw), 1));
    }

    public void step(int d) {
        CycleModel m = model();
        if (m == null || m.isEmpty()) {
            return;
        }
        int c = m.cursorCycle() + d;
        if (d > 0 && m.cursorCycle() >= m.lastCycle()) {
            follow = true;
            Project proj = projRef.get();
            if (proj != null) {
                kr.ac.hallym.hcs.app.sim.SimControls.runCycles(proj, 1); // 마지막 사이클에서 "다음" = 한 사이클 실행
            }
            return;
        }
        view(c);
    }

    public void showLatest() {
        CycleModel m = model();
        if (m != null && !m.isEmpty()) {
            view(m.lastCycle());
        }
    }

    // ---- 그리기 도움 ----

    static int x(CycleModel m, int cycle) {
        return (cycle - m.firstCycle()) * COL_W;
    }

    int columnAt(int px) {
        CycleModel m = model();
        if (m == null || m.isEmpty()) {
            return -1;
        }
        int c = m.firstCycle() + Math.floorDiv(px, COL_W);
        return c <= m.lastCycle() ? c : -1;
    }

    /** 줄 목록: 신호마다 한 줄, 비트로 펼친 버스는 비트마다 한 줄 더(높은 비트부터). */
    static final class Row {
        final CycleModel.Signal signal;
        final int bit; // -1: 신호 전체

        Row(CycleModel.Signal signal, int bit) {
            this.signal = signal;
            this.bit = bit;
        }

        String name() {
            return bit < 0 ? signal.name : "   [" + bit + "]";
        }

        boolean oneBit() {
            return bit >= 0 || signal.width == 1;
        }
    }

    List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (CycleModel.Signal s : signals) {
            out.add(new Row(s, -1));
            if (s.bits && s.width > 1) {
                for (int b = s.width - 1; b >= 0; b--) {
                    out.add(new Row(s, b));
                }
            }
        }
        return out;
    }

    static Value valueOf(CycleModel m, Row r, int step) {
        Value v = m.value(r.signal, step);
        if (v == null || r.bit < 0) {
            return v;
        }
        return r.bit < v.getWidth() ? v.get(r.bit) : null;
    }

    static String hex(Value v) {
        if (v == null) {
            return "";
        }
        if (v.isFullyDefined()) {
            int digits = Math.max(1, (v.getWidth() + 3) / 4);
            long x = v.toIntValue() & (v.getWidth() >= 32 ? 0xffffffffL : (1L << v.getWidth()) - 1);
            return String.format("%0" + digits + "x", x);
        }
        return v.toHexString();
    }

    static String fit(FontMetrics fm, String s, int w) {
        if (fm.stringWidth(s) <= w) {
            return s;
        }
        String dots = "…";
        int n = s.length();
        while (n > 0 && fm.stringWidth(s.substring(0, n) + dots) > w) {
            n--;
        }
        return s.substring(0, n) + dots;
    }

    void refresh() {
        CycleModel m = model();
        boolean has = m != null && !m.isEmpty();
        if (has) {
            position.setText(Messages.get("cycle.position", m.cursorCycle(), m.lastCycle()));
            notice.setVisible(m.recording().isViewingPast());
            prev.setEnabled(m.cursorCycle() > m.firstCycle());
            latest.setEnabled(m.cursorCycle() < m.lastCycle());
        } else {
            position.setText("");
            notice.setVisible(false);
            prev.setEnabled(false);
            latest.setEnabled(false);
        }
        next.setEnabled(true);
        java.awt.Component center = ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        java.awt.Component want = has || !signals.isEmpty() ? scroll : empty;
        if (center != want) {
            if (center != null) {
                panel.remove(center);
            }
            panel.add(want, BorderLayout.CENTER);
            panel.revalidate();
        }
        body.revalidate();
        head.revalidate();
        rowNames.revalidate();
        body.repaint();
        head.repaint();
        rowNames.repaint();
        if (has && follow && m.cursorCycle() == m.lastCycle()) {
            SwingUtilities.invokeLater(() -> body.scrollRectToVisible(new Rectangle(x(m, m.lastCycle()), 0, COL_W,
                    1)));
        }
    }

    private void maybeRowMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        List<Row> rows = rows();
        int i = e.getY() / ROW_H;
        if (i < 0 || i >= rows.size()) {
            return;
        }
        CycleModel.Signal s = rows.get(i).signal;
        JPopupMenu menu = new JPopupMenu();
        if (s.width > 1) {
            JMenuItem bits = new JMenuItem(Messages.get(s.bits ? "cycle.hideBits" : "cycle.showBits"));
            bits.addActionListener(a -> {
                s.bits = !s.bits;
                refresh();
            });
            menu.add(bits);
        }
        JMenuItem remove = new JMenuItem(Messages.get("cycle.remove"));
        remove.addActionListener(a -> {
            signals.remove(s);
            if (model != null) {
                model.remove(s);
            }
            refresh();
        });
        menu.add(remove);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private Graphics2D prepare(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    private void paintColumns(Graphics2D g, CycleModel m, Rectangle clip, int height) {
        int c0 = Math.max(m.firstCycle(), m.firstCycle() + clip.x / COL_W);
        int c1 = Math.min(m.lastCycle(), m.firstCycle() + (clip.x + clip.width) / COL_W);
        int cur = m.cursorCycle();
        for (int c = c0; c <= c1; c++) {
            int x = x(m, c);
            if (c == cur) {
                g.setColor(Tokens.BLUE_TINT);
                g.fillRect(x, clip.y, COL_W, clip.height);
            }
            g.setColor(Tokens.BORDER);
            g.drawLine(x + COL_W - 1, clip.y, x + COL_W - 1, clip.y + clip.height);
        }
        if (c1 == m.lastCycle()) {
            // 지금(마지막 사이클)의 오른쪽 끝
            g.setColor(Tokens.TEAL);
            int x = x(m, m.lastCycle()) + COL_W - 2;
            g.fillRect(x, clip.y, 2, clip.height);
        }
    }

    /** 표 몸통. */
    final class Body extends JComponent implements Scrollable {
        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredSize() {
            CycleModel m = model();
            int cols = m == null || m.isEmpty() ? 0 : m.lastCycle() - m.firstCycle() + 1;
            return new Dimension(cols * COL_W, Math.max(1, rows().size()) * ROW_H);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = prepare(g0);
            Rectangle clip = g.getClipBounds();
            g.setColor(Tokens.WHITE);
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            CycleModel m = model();
            if (m == null || m.isEmpty()) {
                return;
            }
            paintColumns(g, m, clip, getHeight());
            List<Row> rows = rows();
            FontMetrics fm = g.getFontMetrics();
            int c0 = Math.max(m.firstCycle(), m.firstCycle() + clip.x / COL_W);
            int c1 = Math.min(m.lastCycle(), m.firstCycle() + (clip.x + clip.width) / COL_W);
            for (int i = 0; i < rows.size(); i++) {
                int y = i * ROW_H;
                if (y > clip.y + clip.height || y + ROW_H < clip.y) {
                    continue;
                }
                Row r = rows.get(i);
                g.setColor(Tokens.BORDER);
                g.drawLine(clip.x, y + ROW_H - 1, clip.x + clip.width, y + ROW_H - 1);
                for (int c = c0; c <= c1; c++) {
                    int x = x(m, c);
                    if (r.oneBit()) {
                        paintWave(g, m, r, c, x, y);
                    } else {
                        paintBus(g, m, r, c, x, y, fm);
                    }
                }
            }
        }

        /**
         * 1비트 파형: 열 c의 앞 절반은 사이클 c를 연 상승 에지 뒤(스텝 2c-1), 뒤 절반은 다음 상승 에지 앞(스텝 2c)이다.
         * 그래서 한 열의 파형·버스 값·머리 명령어가 모두 같은 사이클을 가리키고, 상승 에지는 열 경계에 온다.
         */
        private void paintWave(Graphics2D g, CycleModel m, Row r, int c, int x, int y) {
            int hi = y + 5;
            int lo = y + ROW_H - 6;
            int mid = (hi + lo) / 2;
            int first = m.recording().first();
            Value prevV = valueOf(m, r, Math.max(first, CycleModel.stepOf(c) - 2));
            for (int h = 0; h < 2; h++) {
                int step = Math.max(first, CycleModel.stepOf(c) - 1 + h);
                if (step > m.recording().last()) {
                    break;
                }
                Value v = valueOf(m, r, step);
                int x0 = x + h * COL_W / 2;
                int x1 = x + (h + 1) * COL_W / 2 - (h == 1 ? 1 : 0);
                if (v == null) {
                    continue;
                }
                g.setStroke(new BasicStroke(1.5f));
                if (v == Value.TRUE || v == Value.FALSE) {
                    int level = v == Value.TRUE ? hi : lo;
                    g.setColor(Tokens.BLUE);
                    g.drawLine(x0, level, x1, level);
                    if (prevV != null && (prevV == Value.TRUE || prevV == Value.FALSE) && prevV != v) {
                        g.drawLine(x0, hi, x0, lo);
                    }
                } else {
                    g.setColor(v.isErrorValue() ? Tokens.ERROR : Tokens.GRAY);
                    g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
                            new float[] {3f, 3f}, 0f));
                    g.drawLine(x0, mid, x1, mid);
                }
                prevV = v;
            }
            g.setStroke(new BasicStroke(1f));
        }

        private void paintBus(Graphics2D g, CycleModel m, Row r, int c, int x, int y, FontMetrics fm) {
            Value v = valueOf(m, r, CycleModel.stepOf(c));
            if (v == null) {
                return;
            }
            // 앞 열과 같은 값은 옅게: 바뀐 값이 눈에 띈다(열마다 칠하면 표 전체가 칠해진다)
            boolean changed = c == m.firstCycle() || m.changed(r.signal, c);
            g.setColor(v.isErrorValue() ? Tokens.ERROR_TEXT : !v.isFullyDefined() ? Tokens.TEXT_MUTED
                    : changed ? Tokens.TEXT : Tokens.TEXT_MUTED);
            String s = fit(fm, hex(v), COL_W - 10);
            g.drawString(s, x + 6, y + (ROW_H + fm.getAscent() - fm.getDescent()) / 2);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            CycleModel m = model();
            int c = columnAt(e.getX());
            List<Row> rows = rows();
            int i = e.getY() / ROW_H;
            if (m == null || c < 0 || i < 0 || i >= rows.size()) {
                return null;
            }
            Row r = rows.get(i);
            Value v = valueOf(m, r, CycleModel.stepOf(c));
            if (v == null) {
                return null;
            }
            String dec = v.isFullyDefined() ? " = " + (r.signal.width >= 32 ? Integer.toString(v.toIntValue())
                    : Long.toString(v.toIntValue() & ((1L << v.getWidth()) - 1))) : "";
            return Messages.get("cycle.valueTip", r.name().trim(), c, "0x" + hex(v) + dec);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return orientation == SwingConstants.HORIZONTAL ? COL_W / 2 : ROW_H;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return orientation == SwingConstants.HORIZONTAL ? Math.max(COL_W, r.width - COL_W)
                    : Math.max(ROW_H, r.height - ROW_H);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return false;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }

        {
            javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
        }
    }

    /** 열 머리: 사이클 번호, PC, 명령어. */
    final class Head extends JComponent {
        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(body.getPreferredSize().width, HEAD_ROWS * ROW_H);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = prepare(g0);
            Rectangle clip = g.getClipBounds();
            g.setColor(Tokens.WINDOW);
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            CycleModel m = model();
            if (m == null || m.isEmpty()) {
                return;
            }
            paintColumns(g, m, clip, getHeight());
            FontMetrics fm = g.getFontMetrics();
            int c0 = Math.max(m.firstCycle(), m.firstCycle() + clip.x / COL_W);
            int c1 = Math.min(m.lastCycle(), m.firstCycle() + (clip.x + clip.width) / COL_W);
            int base = (ROW_H + fm.getAscent() - fm.getDescent()) / 2;
            for (int c = c0; c <= c1; c++) {
                int x = x(m, c) + 6;
                g.setColor(c == m.cursorCycle() ? Tokens.BLUE : Tokens.TEXT_2);
                g.drawString(Integer.toString(c), x, base);
                g.setColor(Tokens.TEXT);
                g.drawString(m.pcText(c), x, ROW_H + base);
                g.drawString(fit(fm, m.instructionText(c), COL_W - 10), x, 2 * ROW_H + base);
            }
            g.setColor(Tokens.BORDER);
            g.drawLine(clip.x, HEAD_ROWS * ROW_H - 1, clip.x + clip.width, HEAD_ROWS * ROW_H - 1);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            CycleModel m = model();
            int c = columnAt(e.getX());
            if (m == null || c < 0) {
                return null;
            }
            Value pc = m.pc(c);
            int line = pc != null && pc.isFullyDefined() ? m.source().lineNumber(pc.toIntValue()) : -1;
            String text = m.instructionText(c);
            return line > 0 ? Messages.get("cycle.lineTip", c, m.pcText(c), line, text)
                    : Messages.get("cycle.instrTip", c, m.pcText(c), text);
        }

        {
            javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
        }
    }

    /** 줄 이름(왼쪽). */
    final class RowNames extends JComponent {
        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(NAME_W, Math.max(1, rows().size()) * ROW_H);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = prepare(g0);
            Rectangle clip = g.getClipBounds();
            g.setColor(Tokens.WINDOW);
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            FontMetrics fm = g.getFontMetrics();
            List<Row> rows = rows();
            for (int i = 0; i < rows.size(); i++) {
                int y = i * ROW_H;
                g.setColor(Tokens.TEXT);
                g.drawString(fit(fm, rows.get(i).name(), NAME_W - 16), 8,
                        y + (ROW_H + fm.getAscent() - fm.getDescent()) / 2);
                g.setColor(Tokens.BORDER);
                g.drawLine(0, y + ROW_H - 1, NAME_W, y + ROW_H - 1);
            }
            g.drawLine(NAME_W - 1, clip.y, NAME_W - 1, clip.y + clip.height);
        }

        {
            setToolTipText(Messages.get("cycle.rowTip"));
        }
    }

    /** 왼쪽 위: 머리 줄 이름. */
    final class CornerLabels extends JComponent {
        private static final long serialVersionUID = 1L;

        CornerLabels() {
            setFont(new Font(Tokens.UI_FONT, Font.PLAIN, Tokens.FONT_SMALL));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = prepare(g0);
            g.setColor(Tokens.WINDOW);
            g.fillRect(0, 0, getWidth(), getHeight());
            FontMetrics fm = g.getFontMetrics();
            String[] names = {Messages.get("cycle.headCycle"), Messages.get("cycle.headPc"),
                Messages.get("cycle.headInstr")};
            g.setColor(Tokens.TEXT_2);
            for (int i = 0; i < names.length; i++) {
                g.drawString(names[i], 8, i * ROW_H + (ROW_H + fm.getAscent() - fm.getDescent()) / 2);
            }
            g.setColor(Tokens.BORDER);
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
        }
    }

    // 테스트
    static CycleView forTest(Project proj) {
        return of(proj);
    }

    JComponent body() {
        return body;
    }

    JLabel positionLabel() {
        return position;
    }

    JLabel noticeLabel() {
        return notice;
    }

    boolean isFollowing() {
        return follow;
    }
}
