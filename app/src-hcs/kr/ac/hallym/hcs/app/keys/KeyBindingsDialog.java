/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.keys;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 단축키 설정 창(E-09): 바꿀 수 있는 명령과 지금 키. 줄을 고르고 Change…를 누른 뒤 새 키를 누르면 바꾼다(Esc는 취소).
 * 다른 명령이나 고정 키와 겹치면 바꾸지 않고 한 줄로 알린다. Reset은 그 명령을, Reset All은 모두 기본 키로. 앱
 * 환경설정에 두고 곧바로 모든 창에 적용된다. ? 표도 같은 키를 보인다.
 */
public final class KeyBindingsDialog {
    private KeyBindingsDialog() {
    }

    /** 표 모델: 설명 | 키. */
    static final class Model extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        final List<KeyBindings.Command> commands = KeyBindings.commands();

        @Override
        public int getRowCount() {
            return commands.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int c) {
            return Messages.get(c == 0 ? "keys.colCommand" : "keys.colKey");
        }

        @Override
        public Object getValueAt(int r, int c) {
            KeyBindings.Command cmd = commands.get(r);
            if (c == 0) {
                return Messages.get(cmd.descKey);
            }
            String d = KeyBindings.display(cmd.id);
            return KeyBindings.customized(cmd.id) ? d + " *" : d;
        }
    }

    /** 새 키를 겹침 검사와 함께 준다. 알릴 글(바꿨으면 null). */
    static String assign(String id, KeyStroke ks) {
        String clash = KeyBindings.set(id, ks);
        if (clash == null) {
            return null;
        }
        if (clash.equals("fixed")) {
            return Messages.get("keys.fixedKey", KeyBindings.display(ks));
        }
        return Messages.get("keys.usedBy", KeyBindings.display(ks), Messages.get(KeyBindings.command(clash).descKey));
    }

    /** 명령 팔레트·? 표에서 연다. */
    public static void show(Window owner) {
        JDialog d = new JDialog(owner, Messages.get("keys.settingsTitle"));
        d.setModal(true);
        Model model = new Model();
        JTable table = new JTable(model);
        table.setName("keys.table");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(420);
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        JLabel status = new JLabel(" ");
        status.setForeground(Tokens.TEXT_2);
        status.setBorder(BorderFactory.createEmptyBorder(6, 10, 0, 10));

        JButton change = new JButton(Messages.get("keys.change"));
        JButton reset = new JButton(Messages.get("keys.reset"));
        JButton resetAll = new JButton(Messages.get("keys.resetAll"));
        JButton close = new JButton(Messages.get("keys.close"));
        change.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r < 0) {
                status.setText(Messages.get("keys.pickRow"));
                return;
            }
            KeyStroke ks = capture(d);
            if (ks == null) {
                status.setText(" ");
                return;
            }
            String msg = assign(model.commands.get(r).id, ks);
            status.setText(msg == null ? Messages.get("keys.changed") : msg);
            status.setForeground(msg == null ? Tokens.TEXT_2 : Tokens.ERROR_TEXT);
            model.fireTableDataChanged();
            table.setRowSelectionInterval(r, r);
        });
        reset.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r >= 0) {
                KeyBindings.reset(model.commands.get(r).id);
                model.fireTableDataChanged();
                table.setRowSelectionInterval(r, r);
            }
        });
        resetAll.addActionListener(e -> {
            KeyBindings.resetAll();
            model.fireTableDataChanged();
        });
        close.addActionListener(e -> d.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(change);
        buttons.add(reset);
        buttons.add(resetAll);
        buttons.add(close);
        JPanel south = new JPanel(new BorderLayout());
        south.add(status, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        JLabel hint = new JLabel(Messages.get("keys.settingsHint"));
        hint.setForeground(Tokens.TEXT_2);
        hint.setBorder(BorderFactory.createEmptyBorder(10, 10, 8, 10));
        d.getContentPane().setLayout(new BorderLayout());
        d.getContentPane().add(hint, BorderLayout.NORTH);
        d.getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
        d.getContentPane().add(south, BorderLayout.SOUTH);
        d.setSize(new Dimension(640, 520));
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    /** 다음에 누른 키(수식 키만 누른 것은 기다린다). Esc면 null. */
    static KeyStroke capture(Window owner) {
        JDialog d = new JDialog(owner, Messages.get("keys.captureTitle"));
        d.setModal(true);
        JLabel l = new JLabel(Messages.get("keys.capture"));
        l.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        d.getContentPane().add(l);
        KeyStroke[] got = new KeyStroke[1];
        l.setFocusable(true);
        l.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT
                        || code == KeyEvent.VK_META || code == KeyEvent.VK_ALT_GRAPH) {
                    return;
                }
                if (code != KeyEvent.VK_ESCAPE || e.getModifiersEx() != 0) {
                    got[0] = KeyStroke.getKeyStroke(code, e.getModifiersEx() & KeyBindings.KEY_MODS);
                }
                e.consume();
                d.dispose();
            }
        });
        d.pack();
        d.setLocationRelativeTo(owner);
        javax.swing.SwingUtilities.invokeLater(l::requestFocusInWindow);
        d.setVisible(true);
        return got[0];
    }
}
