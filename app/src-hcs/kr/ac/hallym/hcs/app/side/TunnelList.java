/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.side;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.labels.TunnelColorStore;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 왼쪽 아래 Tunnels 탭(S-11): 지금 회로의 터널 이름, 개수, 터널 색. 누르면 그 이름의 다음 터널을 고르고 보이게
 * 옮긴다(누를 때마다 다음 것). GUI 없이 쓸 수 있는 목록은 {@link #entries}.
 */
public final class TunnelList extends JPanel {
    private static final long serialVersionUID = 1L;

    /** 이름 하나: 터널들(위치 순). */
    public static final class Entry {
        public final String name;
        public final List<com.cburch.logisim.comp.Component> tunnels;

        Entry(String name, List<com.cburch.logisim.comp.Component> tunnels) {
            this.name = name;
            this.tunnels = tunnels;
        }

        /** 같은 이름의 터널이 하나뿐인가(V-08: 흐린 주황 개수와 툴팁, 판정은 아니다). */
        public boolean lone() {
            return tunnels.size() == 1;
        }

        @Override
        public String toString() {
            return name + " (" + tunnels.size() + ")";
        }
    }

    /** 회로의 터널 이름별 목록(이름 순, 같은 이름은 위→아래·왼쪽→오른쪽). */
    public static List<Entry> entries(Circuit circuit) {
        Map<String, List<com.cburch.logisim.comp.Component>> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (circuit != null) {
            for (com.cburch.logisim.comp.Component c : circuit.getNonWires()) {
                String n = TunnelColorStore.name(c);
                if (n != null) {
                    byName.computeIfAbsent(n, k -> new ArrayList<>()).add(c);
                }
            }
        }
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, List<com.cburch.logisim.comp.Component>> e : byName.entrySet()) {
            List<com.cburch.logisim.comp.Component> l = e.getValue();
            l.sort(java.util.Comparator.<com.cburch.logisim.comp.Component>comparingInt(c -> c.getLocation().getY())
                    .thenComparingInt(c -> c.getLocation().getX()));
            out.add(new Entry(e.getKey(), l));
        }
        return out;
    }

    private final Project proj;
    private final Canvas canvas;
    private final DefaultListModel<Entry> model = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(model);
    private final JLabel empty = new JLabel(Messages.get("side.noTunnels"));
    private Circuit watched;
    private int cycle;
    private String lastName;

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 원조 Project·Circuit은 청취자를 약하게 잡는다: 필드로 붙잡아 둔다. */
    private final ProjectListener projectListener = this::projectChanged;
    private final CircuitListener circuitListener = this::circuitChanged;

    public TunnelList(Project proj, Canvas canvas) {
        super(new BorderLayout());
        this.proj = proj;
        this.canvas = canvas;
        setOpaque(false);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                    boolean focus) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, value, index, selected, focus);
                Entry e = (Entry) value;
                Color col = TunnelColorStore.display(proj.getLogisimFile(), watched, e.name);
                c.setIcon(new Swatch(col == null ? Tokens.TEXT_MUTED : col));
                if (e.lone()) {
                    // V-08: 같은 이름이 하나뿐 — 개수를 흐린 주황으로, 툴팁으로만 알린다(Messages에는 올리지 않는다)
                    c.setText("<html>" + esc(e.name) + " <span style='color:#" + String.format("%06X",
                            Tokens.AMBER_TEXT.getRGB() & 0xFFFFFF) + "'>(1)</span></html>");
                    c.setToolTipText(Messages.get("side.tunnelLoneTip", e.name));
                } else {
                    c.setToolTipText(Messages.get("side.tunnelTip", e.name, e.tunnels.size()));
                }
                return c;
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent ev) {
                int i = list.locationToIndex(ev.getPoint());
                if (i >= 0) {
                    goTo(model.get(i));
                }
            }
        });
        empty.setForeground(Tokens.TEXT_MUTED);
        empty.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        empty.setVerticalAlignment(JLabel.TOP);
        add(new JScrollPane(list), BorderLayout.CENTER);
        proj.addProjectListener(projectListener);
        watch(proj.getCurrentCircuit());
        refresh();
    }

    private void projectChanged(ProjectEvent e) {
        if (e.getAction() == ProjectEvent.ACTION_SET_CURRENT) {
            watch(proj.getCurrentCircuit());
            javax.swing.SwingUtilities.invokeLater(this::refresh);
        }
    }

    private void circuitChanged(CircuitEvent e) {
        javax.swing.SwingUtilities.invokeLater(this::refresh);
    }

    private void watch(Circuit c) {
        if (watched != null) {
            watched.removeCircuitListener(circuitListener);
        }
        watched = c;
        if (c != null) {
            c.addCircuitListener(circuitListener);
        }
    }

    /** 목록을 다시 만든다. */
    void refresh() {
        model.clear();
        for (Entry e : entries(watched)) {
            model.addElement(e);
        }
        removeAll();
        add(model.isEmpty() ? empty : new JScrollPane(list), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /** 그 이름의 다음 터널을 고르고 보이게 옮긴다. */
    void goTo(Entry e) {
        if (e.tunnels.isEmpty()) {
            return;
        }
        cycle = e.name.equals(lastName) ? (cycle + 1) % e.tunnels.size() : 0;
        lastName = e.name;
        com.cburch.logisim.comp.Component t = e.tunnels.get(cycle);
        proj.doAction(com.cburch.logisim.gui.main.SelectionActions.dropAll(proj.getSelection()));
        proj.getSelection().add(t);
        com.cburch.logisim.data.Bounds b = t.getBounds();
        canvas.scrollRectToVisible(canvas.hcsToScreen(new Rectangle(b.getX() - 80, b.getY() - 80,
                b.getWidth() + 160, b.getHeight() + 160)));
    }

    /** 테스트: 지금 목록. */
    List<Entry> shown() {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            out.add(model.get(i));
        }
        return out;
    }

    /** 터널 색 네모 아이콘. */
    static final class Swatch implements javax.swing.Icon {
        private final Color color;

        Swatch(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 110));
            g.fillRect(x, y + 1, 12, 12);
            g.setColor(color);
            g.drawRect(x, y + 1, 11, 11);
        }

        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }
}
