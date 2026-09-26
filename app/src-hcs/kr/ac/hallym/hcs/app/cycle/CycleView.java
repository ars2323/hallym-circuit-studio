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
import java.util.Collections;
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
    public static final int NAME_W = 180;
    static final int HEAD_ROWS = 3;

    private static final Map<Project, CycleView> ALL = new WeakHashMap<>();

    static {
        // 리셋할 때도 바뀐 .s를 다시 불러온다(리셋은 이미 일어나므로 또 리셋하지 않는다)
        Recorder.beforeReset(p -> {
            CycleView v;
            synchronized (ALL) {
                v = ALL.get(p);
            }
            if (v != null) {
                v.reloadPrograms(false, false);
            }
        });
    }

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
    // C-05·C-06: 오른쪽 Registers | Memory 탭
    private final RegisterPanel registers = new RegisterPanel(this::machine);
    private final MemoryPanel memory = new MemoryPanel(this::memories);
    private final JTabbedPane side = new JTabbedPane();
    private final JLabel memSummary = new JLabel();
    // C-07: Instruction 탭(보이는 동안 캔버스에 필드 색 덧그림)
    static final int INSPECT_TAB = 2;
    private final InstructionPanel instruction = new InstructionPanel(this::model);
    /** 레지스터 파일 표시가 없을 때 안내(줄바꿈하는 글, C-05 검토: 한 줄로 그리면 좁은 칸에서 잘린다). */
    private final javax.swing.JTextArea regsHint = new javax.swing.JTextArea();
    // C-09: Console 탭과 .s 자동 재로드(1.5초마다 수정 시각을 본다. 처음 한 번은 파일을 연 때의 확인이다)
    private final ConsolePanel console = new ConsolePanel(this::rootState);
    private final javax.swing.Timer watcher = new javax.swing.Timer(1500, e -> reloadPrograms(false));
    private final javax.swing.JSplitPane split;
    private final JLabel position = new JLabel();
    private final JLabel notice = new JLabel(Messages.get("cycle.pastNotice"));
    private final JLabel empty = new JLabel(Messages.get("cycle.empty"));
    private final JButton prev = new JButton(Messages.get("cycle.prev"));
    private final JButton next = new JButton(Messages.get("cycle.next"));
    private final JButton latest = new JButton(Messages.get("cycle.latest"));
    private final JButton runUntil = new JButton(Messages.get("cycle.runUntil"));
    private final javax.swing.JCheckBox activePath = new javax.swing.JCheckBox(Messages.get("cycle.activePath"));
    private RunUntilRunner runner;
    private boolean follow = true;
    // Recorder는 청취자를 강하게 잡지만, 창이 닫히면 함께 사라지도록 필드로 둔다
    private final Recorder.Listener recListener = r -> SwingUtilities.invokeLater(this::refresh);
    // 편집 동작(레지스터 파일 표시·대응 등) 뒤에도 패널을 다시 모은다. 원조 Project는 청취자를 약하게 잡는다
    private final com.cburch.logisim.proj.ProjectListener projListener = e -> {
        int a = e.getAction();
        if (a == com.cburch.logisim.proj.ProjectEvent.ACTION_COMPLETE
                || a == com.cburch.logisim.proj.ProjectEvent.UNDO_COMPLETE
                || a == com.cburch.logisim.proj.ProjectEvent.ACTION_SET_STATE) {
            SwingUtilities.invokeLater(this::afterEdit);
        }
    };

    private CycleView(Project proj) {
        this.projRef = new java.lang.ref.WeakReference<>(proj);
        this.recorder = Recorder.of(proj);
        recorder.addListener(recListener);
        proj.addProjectListener(projListener);
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
        // 보이는 폭이 바뀌면(옆 탭, 창 크기) 마지막 사이클을 따라가는 동안 다시 열 경계에 맞춘다
        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                CycleModel m = model();
                if (follow && m != null && !m.isEmpty() && m.cursorCycle() == m.lastCycle()) {
                    SwingUtilities.invokeLater(() -> scrollToColumnEdge(x(m, m.lastCycle()) + COL_W));
                }
            }
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_2, 2));
        bar.setBackground(Tokens.WINDOW);
        prev.setToolTipText(Messages.get("cycle.prevTip"));
        next.setToolTipText(Messages.get("cycle.nextTip"));
        latest.setToolTipText(Messages.get("cycle.latestTip"));
        prev.addActionListener(e -> step(-1));
        next.addActionListener(e -> step(+1));
        latest.addActionListener(e -> showLatest());
        runUntil.setToolTipText(Messages.get("runUntil.tip"));
        runUntil.addActionListener(e -> {
            if (runner != null && runner.isRunning()) {
                runner.stop();
            } else {
                askRunUntil();
            }
        });
        position.setForeground(Tokens.TEXT_2);
        notice.setForeground(Tokens.AMBER_TEXT);
        bar.add(prev);
        bar.add(next);
        bar.add(latest);
        bar.add(runUntil);
        // C-08: 고른 사이클의 MUX가 고른 입력을 캔버스에 진하게
        activePath.setSelected(ActivePathOverlay.enabled());
        activePath.setFocusable(false);
        activePath.setToolTipText(Messages.get("cycle.activePathTip"));
        activePath.addActionListener(e -> {
            ActivePathOverlay.setEnabled(activePath.isSelected());
            updateFieldOverlay();
        });
        bar.add(activePath);
        bar.add(position);
        bar.add(notice);
        empty.setForeground(Tokens.TEXT_2);
        empty.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        empty.setVerticalAlignment(SwingConstants.TOP);
        panel.add(bar, BorderLayout.NORTH);
        JPanel regTab = new JPanel(new BorderLayout());
        regsHint.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        regsHint.setForeground(Tokens.TEXT_2);
        regsHint.setLineWrap(true);
        regsHint.setWrapStyleWord(true);
        regsHint.setEditable(false);
        regsHint.setFocusable(false);
        regsHint.setOpaque(false);
        regsHint.setFont(new Font(Tokens.UI_FONT, Font.PLAIN, Tokens.FONT_SMALL));
        regsHint.setText(Messages.get("regs.notMarked"));
        regTab.add(regsHint, BorderLayout.NORTH);
        regTab.add(new JScrollPane(registers), BorderLayout.CENTER);
        side.addTab(Messages.get("regs.tab"), regTab);
        JPanel memTab = new JPanel(new BorderLayout());
        memSummary.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        memSummary.setForeground(Tokens.NAVY);
        memTab.add(memSummary, BorderLayout.NORTH);
        memTab.add(new JScrollPane(memory), BorderLayout.CENTER);
        side.addTab(Messages.get("mem.tab"), memTab);
        side.addTab(Messages.get("inspect.tab"), new JScrollPane(instruction));
        side.addChangeListener(e -> updateFieldOverlay());
        side.setMinimumSize(new Dimension(0, 0));
        split = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, scroll, side);
        split.setResizeWeight(0.6);
        split.setBorder(null);
        split.setContinuousLayout(true);
        panel.add(split, BorderLayout.CENTER);
        // 사이클 뷰가 가려지면(다른 아래 탭) 필드 색도 걷는다
        panel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                updateFieldOverlay();
            }
        });

        MouseAdapter pick = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    int c = columnAt(e.getX());
                    if (c >= 0) {
                        view(c);
                        // Instruction 줄을 누르면 그 명령어를 Instruction 탭에서 펼친다(C-07)
                        if (e.getSource() == head && e.getY() >= 2 * ROW_H && e.getY() < 3 * ROW_H) {
                            side.setSelectedIndex(INSPECT_TAB);
                        }
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
                if (SwingUtilities.isLeftMouseButton(e) && isPinnedClose(e)) {
                    unpin(); // 임시 줄의 ×(V-03)
                    return;
                }
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

    /** 이름 칸에서 임시 줄의 × 자리를 눌렀는가. */
    boolean isPinnedClose(MouseEvent e) {
        List<Row> rows = rows();
        int i = e.getY() / ROW_H;
        return i >= 0 && i < rows.size() && rows.get(i).pinned && e.getX() >= NAME_W - 22;
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
    static final int OPEN_HEIGHT = 40 + (HEAD_ROWS + 7) * ROW_H + 44;

    private kr.ac.hallym.hcs.app.diag.MessagesPanel bottom;

    /** 창을 만들 때 캔버스 아래 탭에 붙인다. */
    public static CycleView install(Frame frame, kr.ac.hallym.hcs.app.diag.MessagesPanel bottom) {
        CycleView v = of(frame.getProject());
        v.bottom = bottom;
        bottom.tabs().addTab(Messages.get("cycle.tab"), v.panel);
        bottom.tabs().addTab(Messages.get("console.tab"), v.console.component()); // C-09
        v.watcher.start();
        return v;
    }

    /** 보고 있는 사이클의 최상위 회로 상태(Console 탭, 메모리 패널). */
    com.cburch.logisim.circuit.CircuitState rootState() {
        Project proj = projRef.get();
        com.cburch.logisim.circuit.CircuitState s = proj == null ? null : proj.getCircuitState();
        while (s != null && s.getParentState() != null) {
            s = s.getParentState();
        }
        return s;
    }

    /** .s 자동 재로드(C-09): 바뀐 .s를 다시 불러오고 리셋, 오류는 한 줄 알림. GUI 스레드에서 부른다. */
    void reloadPrograms(boolean force) {
        reloadPrograms(force, true);
    }

    void reloadPrograms(boolean force, boolean reset) {
        Project proj = projRef.get();
        if (proj == null) {
            watcher.stop(); // 닫힌 파일
            return;
        }
        ProgramReload.Result r = ProgramReload.check(proj, force);
        for (java.util.Map.Entry<File, String> e : r.errors.entrySet()) {
            kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, Messages.get("reload.error", e.getKey().getName(),
                    e.getValue()));
        }
        if (r.changed()) {
            String names = String.join(", ", r.reloaded.stream().map(File::getName).toArray(String[]::new));
            if (reset) {
                Recorder.requestReset(proj);
            }
            kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, Messages.get("reload.done", names));
        }
    }

    ConsolePanel consolePanel() {
        return console;
    }

    /** Cycle View 탭을 앞으로 가져오고 표가 보일 만큼 아래 패널을 편다. */
    public void open() {
        if (bottom != null) {
            bottom.openTab(panel, OPEN_HEIGHT);
        } else if (panel.getParent() instanceof JTabbedPane) {
            ((JTabbedPane) panel.getParent()).setSelectedComponent(panel);
        }
    }

    static {
        // 동적 진단을 누르면 사이클 뷰가 그 사이클로(D-05)
        // 원인 신호와 E·X가 생긴 자리를 표 맨 위 임시 줄로 보인다(V-03)
        kr.ac.hallym.hcs.app.diag.Diagnostics.setStepViewer((proj, d) -> {
            CycleView v = of(proj);
            v.open();
            v.pin(d);
            v.view(kr.ac.hallym.hcs.app.diag.DynamicCheck.cycleOf(d.step));
        });
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
        for (CycleModel.Signal s : pinned) {
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

    // ---- 임시 줄(V-03) ----

    /** 메시지를 눌러 더한 임시 줄: 원인 신호, E·X가 생긴 자리. 파일에 저장되지 않고 다른 메시지를 누르면 바뀐다. */
    private final List<CycleModel.Signal> pinned = new ArrayList<>();
    private int pinnedCycle = -1;

    /** 진단 d의 원인 신호와 생긴 자리를 임시 줄로 둔다(있던 임시 줄은 대체). */
    public void pin(kr.ac.hallym.hcs.app.diag.Diagnostic d) {
        pinned.clear();
        pinnedCycle = -1;
        Recording r = recorder.current();
        if (d == null || r == null || d.step < 0) {
            refresh();
            return;
        }
        Circuit root = r.circuit();
        pinnedCycle = kr.ac.hallym.hcs.app.diag.DynamicCheck.cycleOf(d.step);
        CycleModel.Signal cause = d.location == null ? null
                : CycleModel.signalFor(root, d.instances, d.circuit, d.location);
        if (cause != null) {
            pinned.add(cause);
        }
        kr.ac.hallym.hcs.app.diag.Diagnostic.Spot ap = d.appeared();
        CycleModel.Signal at = ap == null ? null : CycleModel.signalFor(root, ap.instances, ap.circuit, ap.at);
        if (at != null && !pinned.contains(at)) {
            pinned.add(at);
        }
        CycleModel m = model();
        if (m != null) {
            for (CycleModel.Signal s : pinned) {
                m.add(s);
            }
        }
        refresh();
    }

    /** 임시 줄을 걷는다(이름 칸의 ×). */
    public void unpin() {
        pinned.clear();
        pinnedCycle = -1;
        refresh();
    }

    public List<CycleModel.Signal> pinnedSignals() {
        return Collections.unmodifiableList(pinned);
    }

    public int pinnedCycle() {
        return pinnedCycle;
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

    /** Run Until(C-04): 조건을 묻고 한 사이클씩 돌린다. 도는 동안 단추는 Stop이다. */
    public void askRunUntil() {
        Project proj = projRef.get();
        CycleModel m = model();
        if (proj == null || m == null || m.isEmpty()) {
            return;
        }
        if (!proj.getSimulator().isRunning()) {
            kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, Messages.get("runUntil.simOff"));
            return;
        }
        RunUntil until = RunUntilDialog.ask(panel, m, signals);
        if (until != null) {
            start(until);
        }
    }

    /** 조건으로 돌리기 시작한다(테스트도 부른다). */
    public RunUntilRunner start(RunUntil until) {
        Project proj = projRef.get();
        CycleModel m = model();
        if (proj == null || m == null || m.isEmpty() || runner != null && runner.isRunning()) {
            return null;
        }
        follow = true;
        runUntil.setText(Messages.get("cycle.stop"));
        runner = RunUntilRunner.start(proj, m, until, o -> SwingUtilities.invokeLater(() -> {
            runUntil.setText(Messages.get("cycle.runUntil"));
            kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, RunUntilDialog.outcomeText(until, o));
            follow = true;
            refresh();
        }));
        return runner;
    }

    public boolean isRunningUntil() {
        return runner != null && runner.isRunning();
    }

    // ---- 그리기 도움 ----

    static int x(CycleModel m, int cycle) {
        return (cycle - m.firstCycle()) * COL_W;
    }

    /** 줄 이름 칸(테스트). */
    public JComponent rowNames() {
        return rowNames;
    }

    int columnAt(int px) {
        CycleModel m = model();
        if (m == null || m.isEmpty()) {
            return -1;
        }
        int c = m.firstCycle() + Math.floorDiv(px, COL_W);
        return c <= m.lastCycle() ? c : -1;
    }

    /**
     * 오른쪽 끝이 right가 되게 스크롤하되 왼쪽 끝을 열 경계에 맞춘다(잘린 열 조각이 값처럼 보이지 않게, C-05 검토).
     */
    void scrollToColumnEdge(int right) {
        javax.swing.JViewport vp = scroll.getViewport();
        body.setSize(body.getPreferredSize()); // 새 폭(끝 빈칸 포함)이 자리 잡은 뒤 옮겨야 열 경계에 맞는다
        vp.validate();
        int w = vp.getWidth();
        int left = Math.max(0, right - w);
        left = (left + COL_W - 1) / COL_W * COL_W; // 올림: 오른쪽 열이 온전히 보이면 왼쪽 조각을 넘긴다
        int max = Math.max(0, body.getPreferredSize().width - w);
        vp.setViewPosition(new java.awt.Point(Math.min(left, max), vp.getViewPosition().y));
    }

    /** 줄 목록: 신호마다 한 줄, 비트로 펼친 버스는 비트마다 한 줄 더(높은 비트부터). */
    static final class Row {
        final CycleModel.Signal signal;
        final int bit; // -1: 신호 전체
        final boolean pinned; // 메시지에서 온 임시 줄(V-03)

        Row(CycleModel.Signal signal, int bit) {
            this(signal, bit, false);
        }

        Row(CycleModel.Signal signal, int bit, boolean pinned) {
            this.signal = signal;
            this.bit = bit;
            this.pinned = pinned;
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
        for (CycleModel.Signal s : pinned) {
            out.add(new Row(s, -1, true));
        }
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

    /** 레지스터·메모리 패널 모델(보고 있는 사이클). 기록이 없으면 null. */
    MachineState machine() {
        CycleModel m = model();
        Project proj = projRef.get();
        return m == null || proj == null ? null : new MachineState(m, proj.getLogisimFile());
    }

    /** 지금 보고 있는 회로 상태(지난 사이클이면 다시 만든 상태)의 메모리 내용. */
    List<MachineState.Memory> memories() {
        MachineState ms = machine();
        Project proj = projRef.get();
        if (ms == null || ms.model().isEmpty() || proj == null || proj.getCircuitState() == null) {
            return null;
        }
        com.cburch.logisim.circuit.CircuitState root = proj.getCircuitState();
        while (root.getParentState() != null) {
            root = root.getParentState();
        }
        return ms.memories(root, ms.model().cursorCycle());
    }

    /** 오른쪽 탭을 고른다(0 Registers, 1 Memory, 2 Instruction). 스크린샷·테스트. */
    public void showSide(int index) {
        side.setSelectedIndex(index);
    }

    public JComponent sideComponent() {
        return side;
    }

    /** 레지스터 파일 표시가 없다는 안내가 보이는가(테스트). */
    boolean registerHintShown() {
        return regsHint.isVisible();
    }

    RegisterPanel registerPanel() {
        return registers;
    }

    MemoryPanel memoryPanel() {
        return memory;
    }

    JTabbedPane sideTabs() {
        return side;
    }

    /** 편집 동작 뒤: 배선이 바뀌었을 수 있으니 필드 경로를 다시 찾고 패널을 다시 모은다. */
    private void afterEdit() {
        Project proj = projRef.get();
        if (proj != null) {
            FieldOverlay.invalidate(proj);
        }
        refresh();
    }

    JComponent headComponent() {
        return head;
    }

    InstructionPanel instructionPanel() {
        return instruction;
    }

    /**
     * 캔버스 덧그림을 맞춘다. Instruction 탭이 보이면 보고 있는 사이클의 명령어 필드 색(C-07), 사이클 뷰가 보이고
     * Active Path가 켜져 있으면 MUX가 고른 입력(C-08). 아니면 없앤다.
     */
    void updateFieldOverlay() {
        Project proj = projRef.get();
        if (proj == null) {
            return;
        }
        boolean showing = panel.isShowing();
        FieldOverlay.show(proj, showing && side.getSelectedIndex() == INSPECT_TAB ? instruction.word() : null);
        CycleModel m = model();
        ActivePathOverlay.setShown(proj, showing && activePath.isSelected() && m != null && !m.isEmpty());
    }

    javax.swing.JCheckBox activePathBox() {
        return activePath;
    }

    /** Active Path를 켜고 끈다(체크 상자와 같다, 스크린샷). */
    public void setActivePath(boolean on) {
        activePath.setSelected(on);
        ActivePathOverlay.setEnabled(on);
        updateFieldOverlay();
    }

    void refresh() {
        console.refresh();
        instruction.repaint();
        updateFieldOverlay();
        registers.refresh();
        memory.refresh();
        regsHint.setVisible(registers.isListMode() && !registers.lines().isEmpty());
        memSummary.setText(memory.summary());
        memSummary.setVisible(!memory.summary().isEmpty());
        CycleModel m = model();
        boolean has = m != null && !m.isEmpty();
        if (has) {
            position.setText(Messages.get("cycle.position", m.cursorCycle(), m.lastCycle()));
            notice.setVisible(m.recording().isViewingPast());
            boolean busy = isRunningUntil();
            prev.setEnabled(!busy && m.cursorCycle() > m.firstCycle());
            latest.setEnabled(!busy && m.cursorCycle() < m.lastCycle());
            runUntil.setEnabled(true);
        } else {
            position.setText("");
            notice.setVisible(false);
            prev.setEnabled(false);
            latest.setEnabled(false);
            runUntil.setEnabled(false);
        }
        next.setEnabled(!isRunningUntil());
        java.awt.Component center = ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        java.awt.Component want = has || !signals.isEmpty() ? split : empty;
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
            SwingUtilities.invokeLater(() -> scrollToColumnEdge(x(m, m.lastCycle()) + COL_W));
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
        boolean pinnedRow = rows.get(i).pinned;
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
            if (pinnedRow) {
                unpin(); // 임시 줄은 함께 걷힌다(V-03)
                return;
            }
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
            int content = cols * COL_W;
            // 끝까지 스크롤했을 때 왼쪽 끝이 열 경계에 오도록 오른쪽에 빈칸을 조금 더한다(잘린 열 조각이 값처럼 보이지 않게)
            int vw = scroll.getViewport().getWidth();
            int extra = content > vw && vw > 0 ? (COL_W - (content - vw) % COL_W) % COL_W : 0;
            return new Dimension(content + extra, Math.max(1, rows().size()) * ROW_H);
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
            if (rows.isEmpty()) {
                // 줄이 없을 때: 더하는 방법(보이는 영역 왼쪽에)
                Rectangle vis = getVisibleRect();
                g.setFont(new Font(Tokens.UI_FONT, Font.PLAIN, Tokens.FONT_SMALL));
                // 열 경계선이 글자 위를 지나지 않게 바탕을 깐다(C-05 검토)
                FontMetrics hf = g.getFontMetrics();
                String hint = Messages.get("cycle.noRowsHint");
                g.setColor(Tokens.WHITE);
                g.fillRect(vis.x + 4, 0, hf.stringWidth(hint) + 8, ROW_H);
                g.setColor(Tokens.TEXT_2);
                g.drawString(hint, vis.x + 8, (ROW_H + hf.getAscent() - hf.getDescent()) / 2);
                return;
            }
            int c0 = Math.max(m.firstCycle(), m.firstCycle() + clip.x / COL_W);
            int c1 = Math.min(m.lastCycle(), m.firstCycle() + (clip.x + clip.width) / COL_W);
            for (int i = 0; i < rows.size(); i++) {
                int y = i * ROW_H;
                if (y > clip.y + clip.height || y + ROW_H < clip.y) {
                    continue;
                }
                Row r = rows.get(i);
                if (r.pinned) {
                    g.setColor(Tokens.AMBER_TINT);
                    g.fillRect(clip.x, y, clip.width, ROW_H - 1);
                }
                g.setColor(Tokens.BORDER);
                g.drawLine(clip.x, y + ROW_H - 1, clip.x + clip.width, y + ROW_H - 1);
                for (int c = c0; c <= c1; c++) {
                    int x = x(m, c);
                    if (r.oneBit()) {
                        paintWave(g, m, r, c, x, y);
                    } else {
                        paintBus(g, m, r, c, x, y, fm);
                    }
                    if (r.pinned && c == pinnedCycle) {
                        // 메시지가 말한 사이클 칸(V-03)
                        g.setColor(Tokens.AMBER_TEXT);
                        g.drawRect(x + 1, y + 1, COL_W - 3, ROW_H - 4);
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
            int[] half = m.halfSteps(c);
            Value prevV = valueOf(m, r, Math.max(m.recording().first(), half[0] - 1));
            for (int h = 0; h < 2; h++) {
                int step = half[h];
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
                Row r = rows.get(i);
                int textW = NAME_W - 16;
                if (r.pinned) {
                    g.setColor(Tokens.AMBER_TINT);
                    g.fillRect(0, y, NAME_W - 1, ROW_H - 1);
                    g.setColor(Tokens.AMBER_TEXT);
                    g.fillRect(0, y, 3, ROW_H - 1); // 왼쪽 띠: 메시지에서 온 임시 줄
                    g.drawString("×", NAME_W - 16, y + (ROW_H + fm.getAscent() - fm.getDescent()) / 2);
                    textW = NAME_W - 30;
                }
                g.setColor(Tokens.TEXT);
                g.drawString(fit(fm, r.name(), textW), 8, y + (ROW_H + fm.getAscent() - fm.getDescent()) / 2);
                g.setColor(Tokens.BORDER);
                g.drawLine(0, y + ROW_H - 1, NAME_W, y + ROW_H - 1);
            }
            g.drawLine(NAME_W - 1, clip.y, NAME_W - 1, clip.y + clip.height);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            List<Row> rows = rows();
            int i = e.getY() / ROW_H;
            if (i >= 0 && i < rows.size() && rows.get(i).pinned) {
                return Messages.get("cycle.pinnedTip");
            }
            return Messages.get("cycle.rowTip");
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

    /** 표의 보이는 영역 왼쪽 x(테스트). */
    int viewX() {
        return scroll.getViewport().getViewPosition().x;
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
