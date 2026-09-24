/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.find;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * Ctrl+F 찾기와 터널 이름 목록(#80). 찾기는 모든 서브회로의 라벨·터널·서브회로 이름을 경로와 함께 보이고, 고르면
 * 그 서브회로로 들어가(시뮬레이션 경로 그대로) 부품을 선택하고 보이는 곳으로 옮긴다. 터널 탭은 지금 회로의 터널
 * 이름과 개수를 보이고, 고르면 같은 이름 터널을 모두 선택한다.
 */
public final class FindDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final Project proj;
    private final JTextField query = new JTextField(24);
    private final DefaultListModel<NameIndex.Entry> results = new DefaultListModel<>();
    private final JList<NameIndex.Entry> list = new JList<>(results);
    private NameIndex index;

    private FindDialog(Frame frame) {
        super(frame, Messages.get("find.title"), ModalityType.MODELESS);
        this.proj = frame.getProject();
        list.setCellRenderer((l, e, i, sel, focus) -> {
            javax.swing.JLabel lab = new javax.swing.JLabel("<html><b>" + esc(e.text) + "</b>  <span style='color:#"
                    + String.format("%06X", Tokens.TEXT_2.getRGB() & 0xFFFFFF) + "'>"
                    + Messages.get("find.kind." + e.kind.name()) + " · " + esc(e.path) + "</span></html>");
            lab.setOpaque(true);
            lab.setBackground(sel ? Tokens.BLUE_TINT_2 : Tokens.WHITE);
            lab.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return lab;
        });
        query.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                search();
            }

            public void removeUpdate(DocumentEvent e) {
                search();
            }

            public void changedUpdate(DocumentEvent e) {
                search();
            }
        });
        query.addActionListener(e -> {
            if (!results.isEmpty()) {
                go(list.getSelectedIndex() >= 0 ? list.getSelectedValue() : results.get(0));
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                    go(list.getSelectedValue());
                }
            }
        });
        JPanel find = new JPanel(new BorderLayout(0, Tokens.SPACE_2));
        find.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_2, Tokens.SPACE_2, Tokens.SPACE_2, Tokens.SPACE_2));
        find.add(query, BorderLayout.NORTH);
        find.add(new JScrollPane(list), BorderLayout.CENTER);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Messages.get("find.tab"), find);
        tabs.addTab(Messages.get("find.tunnels"), tunnelsPanel());
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 1) {
                tabs.setComponentAt(1, tunnelsPanel()); // 지금 회로로 새로
            }
        });
        setContentPane(tabs);
        setPreferredSize(new Dimension(520, 420));
        pack();
        setLocationRelativeTo(frame);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void search() {
        if (index == null) {
            index = NameIndex.of(proj.getLogisimFile());
        }
        results.clear();
        for (NameIndex.Entry e : index.find(query.getText())) {
            results.addElement(e);
        }
        if (!results.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private JPanel tunnelsPanel() {
        Circuit c = proj.getCurrentCircuit();
        DefaultListModel<String> names = new DefaultListModel<>();
        Map<String, List<Component>> map = NameIndex.tunnels(c);
        List<String> keys = new ArrayList<>(map.keySet());
        for (String k : keys) {
            names.addElement(Messages.get("find.tunnelRow", k, map.get(k).size()));
        }
        JList<String> l = new JList<>(names);
        l.addListSelectionListener(e -> {
            int i = l.getSelectedIndex();
            if (!e.getValueIsAdjusting() && i >= 0) {
                select(map.get(keys.get(i)));
            }
        });
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_2, Tokens.SPACE_2, Tokens.SPACE_2, Tokens.SPACE_2));
        p.add(new JScrollPane(l), BorderLayout.CENTER);
        return p;
    }

    /** 찾은 것으로: 서브회로 경로를 따라 시뮬레이션 상태로 들어가 부품을 선택한다. */
    void go(NameIndex.Entry e) {
        CircuitState state = stateFor(proj, e);
        if (state != null) {
            proj.setCircuitState(state);
        } else {
            proj.setCurrentCircuit(e.circuit);
        }
        select(java.util.Collections.singletonList(e.component));
    }

    /** 맨 위 회로의 상태에서 서브회로 부품들을 따라 내려간 상태. */
    static CircuitState stateFor(Project proj, NameIndex.Entry e) {
        CircuitState s = proj.getCircuitState(e.top);
        for (Component inst : e.instances) {
            if (s == null) {
                return null;
            }
            s = ((SubcircuitFactory) inst.getFactory()).getSubstate(s, inst);
        }
        return s;
    }

    private void select(List<Component> comps) {
        proj.doAction(SelectionActions.dropAll(proj.getSelection()));
        proj.getSelection().addAll(comps);
        if (!comps.isEmpty() && proj.getFrame() != null) {
            Bounds b = comps.get(0).getBounds();
            com.cburch.logisim.gui.main.Canvas canvas = proj.getFrame().getCanvas();
            double z = canvas.getHcsZoom() == null ? 1.0 : canvas.getHcsZoom().zoomFactor();
            proj.getFrame().getCanvas().scrollRectToVisible(new java.awt.Rectangle((int) ((b.getX() - 60) * z),
                    (int) ((b.getY() - 60) * z), (int) ((b.getWidth() + 120) * z), (int) ((b.getHeight() + 120) * z)));
        }
    }

    /** 창에 Ctrl+F를 단다. */
    public static void install(Frame frame) {
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        JComponent root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, menu),
                "hcsFind");
        root.getActionMap().put("hcsFind", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(java.awt.event.ActionEvent ev) {
                FindDialog d = new FindDialog(frame);
                d.setVisible(true);
                d.query.requestFocusInWindow();
            }
        });
    }
}
