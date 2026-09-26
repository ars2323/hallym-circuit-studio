/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.edit;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * Undo History(E-05, PLAN.md 11.13 "되돌리기 목록(무엇을 되돌리는지 표시)"): 되돌릴 수 있는 동작을 오래된 것부터, 지금
 * 자리, 다시 실행할 동작을 한 목록에 보인다. 한 줄을 누르면 그 동작을 마친 상태까지 되돌리거나 다시 실행한다. 원조
 * 되돌리기 기록(Project)과 다시 실행 기록({@link RedoStack})을 읽고, 옮길 때는 원조 undoAction과 RedoStack.redo를
 * 여러 번 부를 뿐이다.
 */
public final class UndoHistory {
    /** 한 줄. */
    public static final class Row {
        public enum Kind {
            START, UNDO, NOW, REDO
        }

        public final Kind kind;
        public final String text;
        /** 누르면 되돌릴 수(음수) 또는 다시 실행할 수(양수). */
        public final int moves;

        Row(Kind kind, String text, int moves) {
            this.kind = kind;
            this.text = text;
            this.moves = moves;
        }

        @Override
        public String toString() {
            return kind + " " + text + " " + moves;
        }
    }

    private static final Map<Project, JDialog> OPEN = new WeakHashMap<>();

    private UndoHistory() {
    }

    /**
     * 목록: 맨 위 "기록의 처음"(누르면 모두 되돌림), 되돌릴 동작(오래된 것부터, 누르면 그 동작까지 남기고 되돌림),
     * 지금 자리, 다시 실행할 동작(바로 다음 것부터, 누르면 그 동작까지 다시 실행).
     */
    public static List<Row> rows(Project proj) {
        List<Row> out = new ArrayList<>();
        List<Action> undo = proj.getUndoActions();
        int n = undo.size();
        out.add(new Row(Row.Kind.START, Messages.get("history.start"), -n));
        for (int i = 0; i < n; i++) {
            out.add(new Row(Row.Kind.UNDO, undo.get(i).getName(), -(n - 1 - i)));
        }
        out.add(new Row(Row.Kind.NOW, Messages.get("history.now"), 0));
        List<String> redo = RedoStack.of(proj).names();
        for (int i = 0; i < redo.size(); i++) {
            out.add(new Row(Row.Kind.REDO, redo.get(i), i + 1));
        }
        return out;
    }

    /** 그 줄의 상태로 옮긴다. */
    public static void go(Project proj, Row row) {
        if (row.moves < 0) {
            for (int i = 0; i < -row.moves; i++) {
                proj.undoAction();
            }
        } else {
            RedoStack redo = RedoStack.of(proj);
            for (int i = 0; i < row.moves && redo.canRedo(); i++) {
                redo.redo();
            }
        }
    }

    /** Edit › Undo History…: 프로젝트마다 창 하나(이미 있으면 앞으로). */
    public static void show(Project proj, Window owner) {
        JDialog open = OPEN.get(proj);
        if (open != null && open.isDisplayable()) {
            open.toFront();
            return;
        }
        JDialog d = new JDialog(owner, Messages.get("history.title"));
        d.setModal(false);
        DefaultListModel<Row> model = new DefaultListModel<>();
        JList<Row> list = new JList<>(model);
        list.setName("history.list");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((l, row, i, sel, focus) -> {
            JLabel lab = new JLabel(row.text);
            lab.setOpaque(true);
            lab.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
            Color fg = Tokens.TEXT;
            Color bg = sel ? Tokens.BLUE_TINT : Tokens.WHITE;
            switch (row.kind) {
            case NOW:
                fg = Tokens.BLUE;
                lab.setText("▶ " + row.text);
                break;
            case REDO:
            case START:
                fg = Tokens.TEXT_2;
                break;
            default:
                break;
            }
            lab.setForeground(fg);
            lab.setBackground(bg);
            return lab;
        });
        Runnable refresh = () -> {
            model.clear();
            for (Row r : rows(proj)) {
                model.addElement(r);
            }
            list.ensureIndexIsVisible(indexOfNow(model));
        };
        ProjectListener pl = e -> {
            int a = e.getAction();
            if (a == ProjectEvent.ACTION_COMPLETE || a == ProjectEvent.UNDO_COMPLETE
                    || a == ProjectEvent.ACTION_SET_FILE) {
                javax.swing.SwingUtilities.invokeLater(refresh);
            }
        };
        proj.addProjectListener(pl);
        RedoStack.of(proj).addListener(() -> javax.swing.SwingUtilities.invokeLater(refresh));
        list.putClientProperty("hcs.listener", pl); // 원조 Project는 청취자를 약하게 잡는다
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i >= 0 && list.getCellBounds(i, i).contains(e.getPoint())) {
                    go(proj, model.get(i));
                    refresh.run();
                }
            }
        });
        refresh.run();
        JLabel hint = new JLabel(Messages.get("history.hint"));
        hint.setForeground(Tokens.TEXT_2);
        hint.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        d.getContentPane().setLayout(new BorderLayout());
        d.getContentPane().add(hint, BorderLayout.NORTH);
        d.getContentPane().add(new JScrollPane(list), BorderLayout.CENTER);
        d.setSize(new Dimension(360, 420));
        d.setLocationRelativeTo(owner);
        OPEN.put(proj, d);
        d.setVisible(true);
    }

    /** 편집 메뉴 항목. */
    public static javax.swing.JMenuItem menuItem(Project proj) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(Messages.get("history.menu"));
        item.setEnabled(proj != null);
        if (proj != null) {
            item.addActionListener(e -> show(proj, proj.getFrame()));
        }
        return item;
    }

    static int indexOfNow(DefaultListModel<Row> model) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).kind == Row.Kind.NOW) {
                return i;
            }
        }
        return 0;
    }
}
