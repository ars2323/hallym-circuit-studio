/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.palette;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 커서 옆 검색창(#76): Ctrl+K 또는 빈 선택에서 글자를 치면 뜬다. 위·아래로 고르고 Enter로 커서 자리에 놓거나 명령을
 * 실행한다. Alt+Enter는 즐겨찾기 넣기·빼기, Esc는 닫기.
 */
public final class PaletteWindow extends JWindow {
    private static final long serialVersionUID = 1L;

    private final Frame frame;
    private final Location at;
    private final JTextField field = new JTextField(26);
    private final DefaultListModel<Palette.Item> model = new DefaultListModel<>();
    private final JList<Palette.Item> list = new JList<>(model);

    private PaletteWindow(Frame frame, Location at, String initial) {
        super(frame);
        this.frame = frame;
        this.at = at;
        list.setCellRenderer((l, it, i, sel, focus) -> {
            JLabel lab = new JLabel(label(it));
            lab.setOpaque(true);
            lab.setBackground(sel ? Tokens.BLUE_TINT_2 : Tokens.WHITE);
            lab.setForeground(Tokens.TEXT);
            lab.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            return lab;
        });
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Tokens.BORDER),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        p.setBackground(Tokens.WHITE);
        p.add(field, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(360, 220));
        p.add(sp, BorderLayout.CENTER);
        JLabel hint = new JLabel(Messages.get("palette.hint"));
        hint.setForeground(Tokens.TEXT_2);
        p.add(hint, BorderLayout.SOUTH);
        setContentPane(p);
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int i = list.getSelectedIndex();
                if (e.getKeyCode() == KeyEvent.VK_DOWN && i + 1 < model.size()) {
                    list.setSelectedIndex(i + 1);
                    list.ensureIndexIsVisible(i + 1);
                } else if (e.getKeyCode() == KeyEvent.VK_UP && i > 0) {
                    list.setSelectedIndex(i - 1);
                    list.ensureIndexIsVisible(i - 1);
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isAltDown() && list.getSelectedValue() != null) {
                    PaletteActions.toggleFavorite(list.getSelectedValue().name);
                    refresh();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    choose();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                } else {
                    return;
                }
                e.consume();
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    choose();
                }
            }
        });
        field.setText(initial);
        refresh();
        pack();
    }

    static String label(Palette.Item it) {
        String kind = Messages.get("palette.kind." + it.kind.name());
        if (it.kind == Palette.Kind.OPEN_FILE) {
            kind = Messages.get("palette.openFiles") + " · " + new java.io.File(it.command).getName();
        }
        String name = it.kind == Palette.Kind.COMMAND ? Messages.get("palette.cmd." + it.command)
                : Palette.displayName(it);
        String attrs = Palette.attrText(it);
        attrs = attrs.isEmpty() ? "" : "  " + attrs;
        return "<html><b>" + name + "</b>" + attrs + "  <span style='color:#"
                + String.format("%06X", Tokens.TEXT_2.getRGB() & 0xFFFFFF) + "'>" + kind + "</span></html>";
    }

    private void refresh() {
        Project proj = frame.getProject();
        List<Library> libs = new ArrayList<>(proj.getLogisimFile().getLibraries());
        List<Circuit> subs = new ArrayList<>(proj.getLogisimFile().getCircuits());
        subs.remove(proj.getCurrentCircuit());
        model.clear();
        for (Palette.Item it : Palette.search(field.getText(), libs, subs, PaletteActions.recent(),
                PaletteActions.favorites(), kr.ac.hallym.hcs.app.libs.OpenFileLibraries.candidates(proj))) {
            model.addElement(it);
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void choose() {
        Palette.Item it = list.getSelectedValue();
        dispose();
        if (it != null) {
            PaletteActions.run(frame.getProject(), it, at);
        }
    }

    /** 커서 자리(at, 논리 좌표)에서 연다. */
    public static void open(Frame frame, Location at, String initial) {
        PaletteWindow w = new PaletteWindow(frame, at, initial);
        Point mouse = frame.getCanvas().getMousePosition();
        Point base = mouse != null ? mouse : new Point(80, 80);
        SwingUtilities.convertPointToScreen(base, frame.getCanvas());
        w.setLocation(base.x + 12, base.y + 12);
        w.setVisible(true);
        w.field.requestFocusInWindow();
        w.field.setCaretPosition(w.field.getText().length());
    }

    /** 창에 Ctrl+K를 단다. */
    public static void install(Frame frame, java.util.function.Supplier<Location> cursor) {
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        JComponent root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_K, menu),
                "hcsPalette");
        root.getActionMap().put("hcsPalette", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(java.awt.event.ActionEvent ev) {
                open(frame, cursor.get(), "");
            }
        });
    }
}
