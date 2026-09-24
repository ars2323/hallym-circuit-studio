/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.splitter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 스플리터 편집기(#105, PLAN.md 11.11). 범위 입력, 비트 그림(경계를 눌러 나누기·합치기), 프리셋, MSB/LSB 방향,
 * 팔 이름, 배정 안 된 비트와 폭 불일치 표시. 적용하면 원조 표준 속성으로만 바꾸고(되돌리기 한 번), 팔 이름은
 * 확장 정보에 둔다.
 */
public final class SplitterEditor extends JDialog {
    private static final long serialVersionUID = 1L;

    /** 팔 색(색각 이상을 고려한 옅은 색). 색만으로 구별하지 않도록 칸마다 팔 번호를 함께 쓴다. */
    static final Color[] ARM_COLORS = {
        new Color(0xDCE9F7), new Color(0xFBE3C8), new Color(0xD9F0E3), new Color(0xF3D9E8),
        new Color(0xE6E0F5), new Color(0xF7F0C6), new Color(0xD5EEF0), new Color(0xEADFD3),
    };

    private final int width;
    private final List<Integer> wiredWidths;
    private final JTextField ranges = new JTextField(28);
    private final JRadioButton msb = new JRadioButton(Messages.get("splitter.msbTop"), true);
    private final JRadioButton lsb = new JRadioButton(Messages.get("splitter.lsbTop"));
    private final BitStrip strip = new BitStrip();
    private final JPanel armsPanel = new JPanel(new GridBagLayout());
    private final JLabel problems = new JLabel(" ");
    private final JButton apply = new JButton(Messages.get("splitter.apply"));
    private final List<JTextField> nameFields = new ArrayList<>();
    private SplitterSpec spec;
    private boolean updating;
    private boolean accepted;

    SplitterEditor(java.awt.Window owner, SplitterSpec initial, List<Integer> wiredWidths) {
        super(owner, Messages.get("splitter.title"), ModalityType.APPLICATION_MODAL);
        this.width = initial.width();
        this.wiredWidths = wiredWidths;
        this.spec = initial;

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(Tokens.SPACE_1, Tokens.SPACE_1, Tokens.SPACE_1, Tokens.SPACE_1);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        g.gridy = 0;
        top.add(new JLabel(Messages.get("splitter.ranges")), g);
        g.gridx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        top.add(ranges, g);
        g.gridx = 0;
        g.gridy = 1;
        g.fill = GridBagConstraints.NONE;
        g.weightx = 0;
        top.add(new JLabel(Messages.get("splitter.preset")), g);
        JComboBox<String> presets = new JComboBox<>();
        presets.addItem(Messages.get("splitter.presetNone"));
        for (SplitterSpec.Preset p : SplitterSpec.Preset.values()) {
            presets.addItem(Messages.get("splitter.preset." + p.name()));
        }
        presets.setEnabled(width == 32);
        g.gridx = 1;
        top.add(presets, g);
        g.gridx = 0;
        g.gridy = 2;
        top.add(new JLabel(Messages.get("splitter.direction")), g);
        JPanel dir = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_2, 0));
        ButtonGroup bg = new ButtonGroup();
        bg.add(msb);
        bg.add(lsb);
        dir.add(msb);
        dir.add(lsb);
        g.gridx = 1;
        top.add(dir, g);

        JPanel center = new JPanel(new BorderLayout(0, Tokens.SPACE_2));
        center.add(strip, BorderLayout.NORTH);
        center.add(armsPanel, BorderLayout.CENTER);
        problems.setForeground(Tokens.ERROR_TEXT);
        center.add(problems, BorderLayout.SOUTH);

        JButton cancel = new JButton(Messages.get("splitter.cancel"));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(apply);
        buttons.add(cancel);
        getRootPane().setDefaultButton(apply);

        JPanel content = new JPanel(new BorderLayout(0, Tokens.SPACE_3));
        content.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_4, Tokens.SPACE_4, Tokens.SPACE_3,
                Tokens.SPACE_4));
        content.add(top, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        ranges.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                reparse();
            }

            public void removeUpdate(DocumentEvent e) {
                reparse();
            }

            public void changedUpdate(DocumentEvent e) {
                reparse();
            }
        });
        msb.addActionListener(e -> reparse());
        lsb.addActionListener(e -> reparse());
        presets.addActionListener(e -> {
            int i = presets.getSelectedIndex();
            if (i > 0) {
                setSpec(SplitterSpec.Preset.values()[i - 1].spec(msb.isSelected()));
            }
        });
        apply.addActionListener(e -> {
            accepted = true;
            dispose();
        });
        cancel.addActionListener(e -> dispose());

        setSpec(initial);
        pack();
        setMinimumSize(new Dimension(Math.max(getWidth(), 560), getHeight()));
        setLocationRelativeTo(owner);
    }

    /** 결과 명세(팔 이름 포함). 취소했으면 null. */
    SplitterSpec result() {
        if (!accepted || spec == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (JTextField f : nameFields) {
            names.add(f.getText().trim());
        }
        return spec.withNames(names);
    }

    private void setSpec(SplitterSpec s) {
        updating = true;
        try {
            ranges.setText(s.toText());
        } finally {
            updating = false;
        }
        show(s);
    }

    private void reparse() {
        if (updating) {
            return;
        }
        try {
            show(SplitterSpec.parse(ranges.getText(), width, msb.isSelected()));
        } catch (SplitterSpec.ParseException ex) {
            spec = null;
            problems.setText(Messages.get("splitter.badInput", ex.getMessage()));
            apply.setEnabled(false);
            strip.repaint();
        }
    }

    private void show(SplitterSpec s) {
        List<String> oldNames = new ArrayList<>();
        for (JTextField f : nameFields) {
            oldNames.add(f.getText());
        }
        spec = s;
        strip.repaint();
        armsPanel.removeAll();
        nameFields.clear();
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, Tokens.SPACE_1, 2, Tokens.SPACE_1);
        g.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < s.arms().size(); i++) {
            SplitterSpec.Arm arm = s.arms().get(i);
            g.gridy = i;
            g.gridx = 0;
            JLabel tag = new JLabel(Messages.get("splitter.arm", i) + "  " + arm.range());
            tag.setOpaque(true);
            tag.setBackground(ARM_COLORS[i % ARM_COLORS.length]);
            tag.setForeground(Tokens.TEXT);
            tag.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            armsPanel.add(tag, g);
            g.gridx = 1;
            JTextField name = new JTextField(arm.name().isEmpty() && i < oldNames.size() && s.arms().size()
                    == oldNames.size() ? oldNames.get(i) : arm.name(), 10);
            name.setToolTipText(Messages.get("splitter.armName"));
            nameFields.add(name);
            armsPanel.add(name, g);
            g.gridx = 2;
            armsPanel.add(new JLabel(Messages.get("splitter.bits", arm.width())), g);
        }
        armsPanel.revalidate();
        armsPanel.repaint();
        List<String> issues = new ArrayList<>();
        if (!s.unassigned().isEmpty()) {
            issues.add(Messages.get("splitter.unassigned", SplitterSpec.ranges(s.unassigned())));
        }
        if (wiredWidths != null && wiredWidths.size() == s.arms().size()) {
            for (int i = 0; i < s.arms().size(); i++) {
                int w = wiredWidths.get(i);
                if (w > 0 && w != s.arms().get(i).width()) {
                    issues.add(Messages.get("splitter.mismatch", s.arms().get(i).range(), s.arms().get(i).width(),
                            w));
                }
            }
        }
        problems.setText(issues.isEmpty() ? " " : "<html>" + String.join("<br>", issues) + "</html>");
        apply.setEnabled(true);
        pack();
    }

    /** 32칸(폭만큼) 비트 그림. 왼쪽이 큰 비트. 칸 경계를 누르면 그 자리에서 나누거나 합친다. */
    final class BitStrip extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int CELL = 16;
        static final int H = 38;

        BitStrip() {
            setPreferredSize(new Dimension(CELL * width + 2, H));
            setToolTipText(Messages.get("splitter.stripTip"));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int boundary = Math.round(e.getX() / (float) CELL);
                    if (boundary > 0 && boundary < width && Math.abs(e.getX() - boundary * CELL) <= 4) {
                        toggle(width - boundary); // 경계 왼쪽 칸의 비트 번호 = width - boundary
                    }
                }
            });
        }

        /** bitLeft와 그 오른쪽 비트 사이 경계: 같은 팔이면 나누고, 다른 두 팔이면 합친다. */
        void toggle(int bitLeft) {
            if (spec != null) {
                setSpec(SplitterEditor.toggle(spec, bitLeft).ordered(msb.isSelected()));
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int[] arm = spec == null ? new int[width] : spec.armOfBit();
            Font small = getFont().deriveFont(9f);
            g.setFont(small);
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < width; i++) {
                int bit = width - 1 - i;
                int x = i * CELL;
                int a = spec == null ? -1 : arm[bit];
                g.setColor(a < 0 ? Tokens.WINDOW : ARM_COLORS[a % ARM_COLORS.length]);
                g.fillRect(x, 0, CELL, H);
                g.setColor(Tokens.BORDER);
                g.drawRect(x, 0, CELL, H);
                g.setColor(Tokens.TEXT_2);
                String b = Integer.toString(bit);
                g.drawString(b, x + (CELL - fm.stringWidth(b)) / 2, 12);
                g.setColor(Tokens.TEXT);
                String t = a < 0 ? "–" : Integer.toString(a); // 팔 번호를 글자로도
                g.drawString(t, x + (CELL - fm.stringWidth(t)) / 2, 30);
                boolean edge = i > 0 && spec != null && arm[bit] != arm[bit + 1];
                if (edge) {
                    g.setColor(Tokens.NAVY);
                    g.fillRect(x - 1, 0, 2, H);
                }
            }
        }
    }

    /** 경계 누르기(GUI 없이 테스트): bitLeft와 bitLeft-1이 같은 팔이면 나누고, 다른 팔이면 합친다. */
    static SplitterSpec toggle(SplitterSpec spec, int bitLeft) {
        int bitRight = bitLeft - 1;
        if (bitRight < 0 || bitLeft >= spec.width()) {
            return spec;
        }
        int[] armOf = spec.armOfBit();
        int a = armOf[bitLeft];
        int b = armOf[bitRight];
        if (a < 0 || b < 0) {
            return spec; // 배정 안 된 비트가 끼면 범위 입력으로 고친다
        }
        List<SplitterSpec.Arm> arms = new ArrayList<>();
        for (int i = 0; i < spec.arms().size(); i++) {
            SplitterSpec.Arm arm = spec.arms().get(i);
            if (a == b && i == a) {
                List<Integer> hi = new ArrayList<>();
                List<Integer> lo = new ArrayList<>();
                for (int bit : arm.bits()) {
                    (bit >= bitLeft ? hi : lo).add(bit);
                }
                arms.add(SplitterSpec.arm(arm.name(), hi));
                arms.add(SplitterSpec.arm("", lo));
            } else if (a != b && i == a) {
                List<Integer> merged = new ArrayList<>(arm.bits());
                merged.addAll(spec.arms().get(b).bits());
                arms.add(SplitterSpec.arm(arm.name(), merged));
            } else if (a != b && i == b) {
                continue;
            } else {
                arms.add(arm);
            }
        }
        return new SplitterSpec(spec.width(), arms);
    }

    // --- 여는 곳 ---

    /** 있는 스플리터 편집. */
    public static void editExisting(Project proj, Circuit circuit, Component splitter) {
        SplitterSpec cur = SplitterEdits.specOf(proj.getLogisimFile(), circuit, splitter);
        List<Integer> wired = new ArrayList<>();
        Netlist nl = Netlist.of(circuit);
        for (int i = 1; i < splitter.getEnds().size(); i++) {
            Netlist.Net net = nl.netOf(splitter, i);
            int w = 0;
            if (net != null) {
                for (Netlist.PortRef p : net.ports()) {
                    if (p.component != splitter) {
                        w = Math.max(w, p.width());
                    }
                }
            }
            wired.add(w);
        }
        SplitterEditor d = new SplitterEditor(proj.getFrame(), cur, wired);
        d.setVisible(true);
        SplitterSpec s = d.result();
        if (s == null) {
            return;
        }
        Location at = splitter.getLocation();
        boolean attrs = !s.toStandardAttrs().equals(cur.toStandardAttrs());
        boolean names = !s.names().equals(cur.names());
        if (attrs || names) {
            com.cburch.logisim.proj.Action base = attrs
                    ? SplitterEdits.change(circuit, splitter, s).toAction(() -> Messages.get("splitter.editAction"))
                    : null;
            proj.doAction(SplitterEdits.withNames(base, Messages.get("splitter.editAction"), proj.getLogisimFile(),
                    circuit, at, s));
        }
    }

    /** 선 위에 새 스플리터(비트 나누기). 처음 모양은 절반씩. */
    public static void createNew(Project proj, Circuit circuit, Location at, int width) {
        SplitterSpec init;
        try {
            init = SplitterSpec.parse(width == 32 ? SplitterSpec.Preset.MIPS_R.spec(true).toText()
                    : (width - 1) + ":" + (width / 2) + ", " + (width / 2 - 1) + ":0", width, true);
        } catch (SplitterSpec.ParseException e) {
            return;
        }
        SplitterEditor d = new SplitterEditor(proj.getFrame(), init, null);
        d.setVisible(true);
        SplitterSpec s = d.result();
        if (s == null) {
            return;
        }
        CircuitMutation m = SplitterEdits.create(proj.getLogisimFile(), circuit, at, Direction.EAST, s);
        proj.doAction(SplitterEdits.withNames(m.toAction(() -> Messages.get("splitter.createAction")),
                Messages.get("splitter.createAction"), proj.getLogisimFile(), circuit, at, s));
    }
}
