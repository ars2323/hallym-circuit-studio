/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/** Run Until 조건을 묻는 창(C-04). 조건 종류, 값(PC·명령어 이름·줄), 최대 사이클 수. */
final class RunUntilDialog {
    private RunUntilDialog() {
    }

    /** 사용자가 고른 조건. 취소하면 null. */
    static RunUntil ask(Component parent, CycleModel m, List<CycleModel.Signal> rows) {
        JComboBox<RunUntil.Kind> kind = new JComboBox<>(RunUntil.Kind.values());
        kind.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                    boolean focus) {
                return super.getListCellRendererComponent(list, Messages.get("runUntil." + value), index, selected,
                        focus);
            }
        });
        JTextField pc = new JTextField(16);
        JTextField instr = new JTextField(16);
        JComboBox<CycleModel.Signal> row = new JComboBox<>(rows.toArray(new CycleModel.Signal[0]));
        JPanel value = new JPanel(new CardLayout());
        value.add(labeled(pc, "runUntil.pcHelp"), RunUntil.Kind.PC.name());
        value.add(labeled(instr, "runUntil.instrHelp"), RunUntil.Kind.INSTRUCTION.name());
        value.add(rows.isEmpty() ? help("runUntil.noRows") : row, RunUntil.Kind.ROW_CHANGES.name());
        value.add(help("runUntil.errorHelp"), RunUntil.Kind.ERROR_OR_X.name());
        value.add(help("runUntil.haltHelp"), RunUntil.Kind.HALT.name());
        kind.addActionListener(e -> ((CardLayout) value.getLayout()).show(value,
                ((RunUntil.Kind) kind.getSelectedItem()).name()));
        JSpinner max = new JSpinner(new SpinnerNumberModel(RunUntil.DEFAULT_MAX_CYCLES, 1, 1_000_000, 100));

        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        JLabel tip = new JLabel("<html><div style='width:340px'>" + Messages.get("runUntil.tip") + "</div></html>");
        tip.setForeground(Tokens.TEXT_2);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        p.add(tip, c);
        c.gridwidth = 1;
        c.gridy = 1;
        p.add(new JLabel(Messages.get("runUntil.condition")), c);
        c.gridx = 1;
        p.add(kind, c);
        c.gridx = 0;
        c.gridy = 2;
        p.add(new JLabel(Messages.get("runUntil.value")), c);
        c.gridx = 1;
        p.add(value, c);
        c.gridx = 0;
        c.gridy = 3;
        p.add(new JLabel(Messages.get("runUntil.max")), c);
        c.gridx = 1;
        p.add(max, c);

        while (true) {
            int r = JOptionPane.showConfirmDialog(parent, p, Messages.get("runUntil.title"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) {
                return null;
            }
            int limit = (Integer) max.getValue();
            RunUntil.Kind k = (RunUntil.Kind) kind.getSelectedItem();
            String problem = null;
            RunUntil until = null;
            switch (k) {
            case PC: {
                Integer a = RunUntil.parsePc(pc.getText(), m.source());
                if (a == null) {
                    problem = Messages.get("runUntil.badPc", pc.getText().trim());
                } else {
                    until = RunUntil.pc(a, limit);
                }
                break;
            }
            case INSTRUCTION:
                if (instr.getText().trim().isEmpty()) {
                    problem = Messages.get("runUntil.badInstr");
                } else {
                    until = RunUntil.instruction(instr.getText().trim().split("\\s+")[0], limit);
                }
                break;
            case ROW_CHANGES:
                if (rows.isEmpty()) {
                    problem = Messages.get("runUntil.noRows");
                } else {
                    until = RunUntil.rowChanges((CycleModel.Signal) row.getSelectedItem(), limit);
                }
                break;
            case ERROR_OR_X:
                until = RunUntil.errorOrX(limit);
                break;
            default:
                until = RunUntil.halt(limit);
                break;
            }
            if (until != null) {
                return until;
            }
            JOptionPane.showMessageDialog(parent, problem, Messages.get("runUntil.title"),
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private static JPanel labeled(JTextField f, String helpKey) {
        JPanel p = new JPanel(new java.awt.BorderLayout(0, 2));
        p.add(f, java.awt.BorderLayout.NORTH);
        p.add(help(helpKey), java.awt.BorderLayout.SOUTH);
        return p;
    }

    private static JLabel help(String key) {
        JLabel l = new JLabel("<html><div style='width:240px'>" + Messages.get(key) + "</div></html>");
        l.setForeground(Tokens.TEXT_2);
        return l;
    }

    /** 끝난 뒤 한 줄 알림(상태 표시줄). */
    static String outcomeText(RunUntil until, RunUntilRunner.Outcome o) {
        if (o.result == null) {
            return Messages.get("runUntil.stopped", o.cycle);
        }
        if (o.result == RunUntil.Result.LIMIT) {
            return Messages.get("runUntil.limit", until.maxCycles);
        }
        String why;
        switch (until.kind) {
        case PC:
            why = Messages.get("runUntil.why.PC", String.format("0x%08x", until.pc));
            break;
        case INSTRUCTION:
            why = Messages.get("runUntil.why.INSTRUCTION", until.mnemonic);
            break;
        case ROW_CHANGES:
            why = Messages.get("runUntil.why.ROW_CHANGES", until.row.name);
            break;
        case ERROR_OR_X:
            why = Messages.get("runUntil.why.ERROR_OR_X");
            break;
        default:
            why = Messages.get("runUntil.why.HALT");
            break;
        }
        return Messages.get("runUntil.met", o.cycle, why);
    }
}
