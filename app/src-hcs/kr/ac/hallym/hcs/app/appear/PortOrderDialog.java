/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.appear;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

import com.cburch.draw.model.CanvasObject;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 서브회로 우클릭 "Port Order…"(P-04, PLAN.md 11.10): 변마다 포트 목록을 끌어(또는 ▲▼로) 순서를 바꾸고 Apply하면 그
 * 순서로 Auto Appearance 모양을 만든다(D-051의 형식 그대로). 끊어질 인스턴스 연결이 있으면 먼저 알린다. 되돌리기 한 번.
 */
public final class PortOrderDialog {
    private PortOrderDialog() {
    }

    /** 순서 바꾸기(GUI 없이 테스트): from의 항목을 to 자리로. */
    public static <T> List<T> moved(List<T> list, int from, int to) {
        List<T> out = new ArrayList<>(list);
        if (from < 0 || from >= out.size() || to < 0 || to >= out.size() || from == to) {
            return out;
        }
        T t = out.remove(from);
        out.add(to, t);
        return out;
    }

    static String sideName(Direction d) {
        return Messages.get("portOrder.side." + d.toString().toLowerCase());
    }

    public static void show(Project proj, Circuit circuit, java.awt.Component parent) {
        Map<Direction, List<Instance>> sides = AutoAppearance.sides(circuit);
        Map<Direction, DefaultListModel<Instance>> models = new LinkedHashMap<>();
        JPanel lists = new JPanel(new GridLayout(1, 0, Tokens.SPACE_3, 0));
        for (Map.Entry<Direction, List<Instance>> e : sides.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            DefaultListModel<Instance> m = new DefaultListModel<>();
            for (Instance p : e.getValue()) {
                m.addElement(p);
            }
            models.put(e.getKey(), m);
            lists.add(column(sideName(e.getKey()), m));
        }
        if (models.isEmpty()) {
            JOptionPane.showMessageDialog(parent, Messages.get("portOrder.noPorts"), Messages.get("portOrder.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JPanel p = new JPanel(new BorderLayout(0, Tokens.SPACE_3));
        JLabel hint = new JLabel(Messages.get("portOrder.hint"));
        hint.setForeground(Tokens.TEXT_2);
        p.add(hint, BorderLayout.NORTH);
        p.add(lists, BorderLayout.CENTER);
        Object[] options = {Messages.get("portOrder.apply"), Messages.get("autoAppearance.cancel")};
        int r = JOptionPane.showOptionDialog(parent, p, Messages.get("portOrder.title", circuit.getName()),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (r != 0) {
            return;
        }
        Map<Direction, List<Instance>> order = new LinkedHashMap<>();
        for (Direction d : sides.keySet()) {
            List<Instance> l = new ArrayList<>();
            DefaultListModel<Instance> m = models.get(d);
            if (m != null) {
                for (int i = 0; i < m.size(); i++) {
                    l.add(m.get(i));
                }
            }
            order.put(d, l);
        }
        apply(proj, circuit, order, parent);
    }

    /** 준 순서로 모양을 만들고, 끊어질 연결이 있으면 알린 뒤 되돌릴 수 있게 바꾼다. */
    public static boolean apply(Project proj, Circuit circuit, Map<Direction, List<Instance>> order,
            java.awt.Component parent) {
        List<CanvasObject> shapes = AutoAppearance.build(circuit, order);
        AutoAppearance.Impact impact = AutoAppearance.impact(proj.getLogisimFile(), circuit, shapes);
        if (impact.connections > 0 && parent != null) {
            String list = String.join("\n", impact.where.subList(0, Math.min(8, impact.where.size())));
            Object[] options = {Messages.get("autoAppearance.apply"), Messages.get("autoAppearance.cancel")};
            int r = JOptionPane.showOptionDialog(parent,
                    Messages.get("autoAppearance.impact", impact.instances, impact.connections) + "\n\n" + list,
                    Messages.get("portOrder.title", circuit.getName()), JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, options, options[1]);
            if (r != 0) {
                return false;
            }
        }
        proj.doAction(AutoAppearance.action(circuit, shapes));
        return true;
    }

    /** 한 변의 목록: 끌어 놓기와 ▲▼. */
    static JPanel column(String title, DefaultListModel<Instance> model) {
        JList<Instance> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(8);
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel,
                    boolean focus) {
                String name = AutoAppearance.portName((Instance) v);
                return super.getListCellRendererComponent(l, (i + 1) + ".  " + (name.isEmpty() ? "(pin)" : name), i,
                        sel, focus);
            }
        });
        list.setDragEnabled(true);
        list.setDropMode(DropMode.INSERT);
        list.setTransferHandler(new Reorder(list, model));
        JButton up = new JButton("▲");
        JButton down = new JButton("▼");
        up.setToolTipText(Messages.get("portOrder.up"));
        down.setToolTipText(Messages.get("portOrder.down"));
        up.addActionListener(e -> shift(list, model, -1));
        down.addActionListener(e -> shift(list, model, 1));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, Tokens.SPACE_1, 0));
        buttons.add(up);
        buttons.add(down);
        JPanel col = new JPanel(new BorderLayout(0, Tokens.SPACE_1));
        JLabel head = new JLabel(title);
        head.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        col.add(head, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(150, 170));
        col.add(scroll, BorderLayout.CENTER);
        col.add(buttons, BorderLayout.SOUTH);
        return col;
    }

    static void shift(JList<Instance> list, DefaultListModel<Instance> model, int by) {
        int i = list.getSelectedIndex();
        int j = i + by;
        if (i < 0 || j < 0 || j >= model.size()) {
            return;
        }
        Instance t = model.remove(i);
        model.add(j, t);
        list.setSelectedIndex(j);
    }

    /** 목록 안에서 끌어 놓기. */
    static final class Reorder extends TransferHandler {
        private static final long serialVersionUID = 1L;
        static final DataFlavor FLAVOR = new DataFlavor(Integer.class, "port-index");
        private final JList<Instance> list;
        private final DefaultListModel<Instance> model;

        Reorder(JList<Instance> list, DefaultListModel<Instance> model) {
            this.list = list;
            this.model = model;
        }

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            final Integer index = list.getSelectedIndex();
            return new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[] {FLAVOR};
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor f) {
                    return FLAVOR.equals(f);
                }

                @Override
                public Object getTransferData(DataFlavor f) {
                    return index;
                }
            };
        }

        @Override
        public boolean canImport(TransferSupport s) {
            return s.isDrop() && s.getComponent() == list && s.isDataFlavorSupported(FLAVOR);
        }

        @Override
        public boolean importData(TransferSupport s) {
            try {
                int from = (Integer) s.getTransferable().getTransferData(FLAVOR);
                int to = ((JList.DropLocation) s.getDropLocation()).getIndex();
                if (to > from) {
                    to--;
                }
                if (from < 0 || from >= model.size() || to < 0 || to >= model.size() || from == to) {
                    return false;
                }
                Instance t = model.remove(from);
                model.add(to, t);
                list.setSelectedIndex(to);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
