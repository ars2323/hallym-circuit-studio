/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.sim;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.SimulatorEvent;
import com.cburch.logisim.circuit.SimulatorListener;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.Settings;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 창 위 툴바 그룹, 캔버스 아래 상태 표시줄, 시뮬레이션이 꺼졌을 때의 띠(#77). 툴바 구성은 앱 환경설정이고 .circ의
 * {@code <toolbar>}는 그대로다(원조 도구 모음은 따로 남는다).
 */
public final class SimControls {
    static final String STYLE = "toolbar.style"; // "text" | "icons"

    private final Frame frame;
    private final Project proj;
    private final JLabel simState = new JLabel();
    private final JLabel cycleLabel = new JLabel();
    private final JLabel pcLabel = new JLabel();
    private final JLabel programLabel = new JLabel();
    private final kr.ac.hallym.hcs.app.zoom.ZoomStatus zoom = new kr.ac.hallym.hcs.app.zoom.ZoomStatus();
    private final JPanel banner = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_2, 2));
    private long ticks;
    /** 원조 Simulator는 리스너를 약하게 두지 않지만 창이 사는 동안 붙잡아 둔다. */
    private final SimulatorListener listener;

    private SimControls(Frame frame) {
        this.frame = frame;
        this.proj = frame.getProject();
        listener = new SimulatorListener() {
            public void propagationCompleted(SimulatorEvent e) {
                SwingUtilities.invokeLater(SimControls.this::refresh);
            }

            public void tickCompleted(SimulatorEvent e) {
                ticks++;
                SwingUtilities.invokeLater(SimControls.this::refresh);
            }

            public void simulatorStateChanged(SimulatorEvent e) {
                SwingUtilities.invokeLater(SimControls.this::refresh);
            }
        };
        proj.getSimulator().addSimulatorListener(listener);
    }

    public static SimControls install(Frame frame) {
        return new SimControls(frame);
    }

    private boolean text() {
        return !"icons".equals(Settings.get().getString(STYLE, "text"));
    }

    private JButton button(String icon, String key, Runnable r) {
        JButton b = new JButton(text() ? Messages.get(key) : null, new BarIcons(icon));
        b.setToolTipText(Messages.get(key));
        b.setFocusable(false);
        b.addActionListener(e -> r.run());
        return b;
    }

    private Tool baseTool(String name) {
        Library base = proj.getLogisimFile().getLoader().getBuiltin().getLibrary("Base");
        return base == null ? null : base.getTool(name);
    }

    private Tool wiringTool(String name) {
        for (Library lib : proj.getLogisimFile().getLibraries()) {
            if (lib.getName().equals("Wiring")) {
                return lib.getTool(name);
            }
        }
        return null;
    }

    private void use(Tool t) {
        if (t != null) {
            proj.setTool(t);
        }
    }

    /** 툴바 그룹: 파일·되돌리기 / 도구 / 자주 쓰는 부품 / 시뮬레이션 / 프로그램. */
    public JToolBar toolbar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.add(button("new", "bar.new", () -> ProjectActions.doNew(proj)));
        tb.add(button("open", "bar.open", () -> ProjectActions.doOpen(frame, proj)));
        tb.add(button("save", "bar.save", () -> ProjectActions.doSave(proj)));
        tb.add(button("undo", "bar.undo", proj::undoAction));
        kr.ac.hallym.hcs.app.edit.RedoStack redo = kr.ac.hallym.hcs.app.edit.RedoStack.of(proj);
        JButton redoButton = button("redo", "bar.redo", redo::redo);
        redo.addListener(() -> redoButton.setEnabled(redo.canRedo()));
        redoButton.setEnabled(redo.canRedo());
        tb.add(redoButton);
        tb.addSeparator();
        tb.add(button("select", "bar.select", () -> use(baseTool("Edit Tool"))));
        tb.add(button("poke", "bar.poke", () -> use(baseTool("Poke Tool"))));
        tb.add(button("wire", "bar.wire", () -> use(baseTool("Wiring Tool"))));
        tb.add(button("text", "bar.text", () -> use(baseTool("Text Tool")))); // 원조 도구 모음에만 있던 도구
        tb.addSeparator();
        tb.add(button("pin", "bar.input", () -> use(wiringTool("Pin"))));
        tb.add(button("tunnel", "bar.tunnel", () -> use(wiringTool("Tunnel"))));
        tb.add(button("probe", "bar.probe", () -> use(wiringTool("Probe"))));
        tb.addSeparator();
        tb.add(button("run", "bar.run", () -> {
            proj.getSimulator().setIsRunning(!proj.getSimulator().isRunning());
        }));
        tb.add(button("cycle", "bar.cycle", () -> cycles(1)));
        tb.add(button("cycles", "bar.cycles", () -> {
            Object s = JOptionPane.showInputDialog(frame, Messages.get("bar.cyclesPrompt"),
                    Messages.get("bar.cycles"), JOptionPane.PLAIN_MESSAGE, null, null, "10");
            if (s != null) {
                try {
                    cycles(Math.max(1, Math.min(100000, Integer.parseInt(s.toString().trim()))));
                } catch (NumberFormatException e) {
                    // 숫자가 아니면 아무것도 하지 않는다
                }
            }
        }));
        tb.add(button("reset", "bar.reset", () -> {
            ticks = 0;
            proj.getSimulator().requestReset();
        }));
        JComboBox<String> speed = new JComboBox<>(new String[] {"1 Hz", "4 Hz", "16 Hz", "64 Hz", "256 Hz",
            "1 kHz", "4 kHz"});
        speed.setToolTipText(Messages.get("bar.speed"));
        speed.setMaximumSize(speed.getPreferredSize());
        speed.setFocusable(false);
        speed.addActionListener(e -> {
            double[] f = {1, 4, 16, 64, 256, 1024, 4096};
            proj.getSimulator().setTickFrequency(f[speed.getSelectedIndex()]);
        });
        tb.add(speed);
        tb.addSeparator();
        tb.add(button("program", "bar.program", () -> kr.ac.hallym.hcs.app.palette.PaletteActions.loadProgram(proj)));
        JButton style = new JButton(text() ? Messages.get("bar.iconsOnly") : Messages.get("bar.withText"));
        style.setFocusable(false);
        style.addActionListener(e -> {
            Settings.get().set(STYLE, text() ? "icons" : "text");
            try {
                Settings.get().save();
            } catch (java.io.IOException ex) {
                // 무시
            }
            JOptionPane.showMessageDialog(frame, Messages.get("bar.styleNext"));
        });
        tb.add(javax.swing.Box.createHorizontalGlue());
        tb.add(style);
        return tb;
    }

    /** n 사이클: 원조 틱 2n번. */
    void cycles(int n) {
        Simulator sim = proj.getSimulator();
        Timer t = new Timer(0, null);
        StatusModel.Run run = new StatusModel.Run(n);
        t.addActionListener(e -> {
            if (!run.step(sim::tick)) {
                t.stop();
            }
        });
        t.setDelay(n == 1 ? 0 : 5);
        t.start();
    }

    /** 상태 표시줄의 배율 단추. 창이 배율 모델을 붙인다. */
    public kr.ac.hallym.hcs.app.zoom.ZoomStatus zoomStatus() {
        return zoom;
    }

    /** 캔버스 아래 상태 표시줄. */
    public JPanel statusBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_4, 2));
        p.setBackground(Tokens.WINDOW);
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Tokens.BORDER));
        for (JLabel l : new JLabel[] {simState, cycleLabel, pcLabel, programLabel}) {
            l.setForeground(Tokens.TEXT_2);
            p.add(l);
        }
        p.add(zoom.component()); // 옛 왼쪽 아래 배율 칸 대신(검토 반영 1)
        JLabel legend = new JLabel(Messages.get("bar.legend"));
        legend.setForeground(Tokens.BLUE);
        legend.setToolTipText(Messages.get("bar.legendTip"));
        p.add(legend);
        p.add(kr.ac.hallym.hcs.app.labels.LabelOverlay.densityButton()); // #79
        refresh();
        return p;
    }

    /** 시뮬레이션이 꺼져 있을 때 캔버스 위 띠. */
    public JPanel banner() {
        banner.setBackground(Tokens.AMBER_TINT);
        banner.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new java.awt.Color(0xF1DDB4)));
        JLabel msg = new JLabel(Messages.get("bar.simOff"));
        msg.setForeground(Tokens.AMBER_TEXT);
        JLabel on = new JLabel("<html><u>" + Messages.get("bar.simOn") + "</u></html>");
        on.setForeground(Tokens.BLUE);
        on.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        on.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                proj.getSimulator().setIsRunning(true);
            }
        });
        banner.add(msg);
        banner.add(on);
        refresh();
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(banner, BorderLayout.CENTER);
        return wrap;
    }

    void refresh() {
        Simulator sim = proj.getSimulator();
        boolean running = sim.isRunning();
        simState.setText(Messages.get(running ? "bar.running" : "bar.stopped"));
        cycleLabel.setText(Messages.get("bar.cycleCount", StatusModel.cycles(ticks)));
        String pc = StatusModel.pc(proj.getCircuitState());
        pcLabel.setText(pc == null ? "" : "PC " + pc);
        String prog = StatusModel.program(proj.getCurrentCircuit());
        programLabel.setText(prog == null ? "" : Messages.get("bar.programLoaded", prog));
        banner.setVisible(!running);
    }
}
