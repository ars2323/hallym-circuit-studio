/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.StringGetter;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.edit.CircuitEdits;
import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;

/**
 * 대상별 우클릭 항목(#72, #73): 포트(핀·상수·프로브·터널 붙이기, 입력 부정), 핀(방향·폭·3상태·풀·라벨),
 * 빈 곳(붙여넣기·화면 맞춤), 여러 부품(속성·라벨 일괄), 게이트(입력 수·크기·방향·폭·종류 바꾸기·복제),
 * 선(넷 정보·넷 선택·터널로 바꾸기), 서브회로(모양 편집), 터널(같은 이름으로 이동·모두 선택). 모든 항목은
 * 원조 부품·속성만 바꾸고 되돌리기 한 번으로 취소된다.
 */
public final class EditMenus implements ContextMenus.Provider {
    static final int[] WIDTHS = {1, 2, 4, 8, 16, 32};

    @Override
    public void contribute(ContextMenus.Target t, JPopupMenu menu) {
        Component c = t.component;
        List<Component> many = nonWires(t.selection);
        if (many.size() >= 2 && (c == null || t.selection.contains(c))) {
            multi(t, many, menu);
            return;
        }
        if (c == null) {
            empty(t, menu);
            return;
        }
        if (c instanceof Wire) {
            wire(t, (Wire) c, menu);
            return;
        }
        int port = portAt(c, t.point);
        if (port >= 0) {
            port(t, c, port, menu);
        }
        String f = c.getFactory().getName();
        if (f.equals("Pin")) {
            pin(t, c, menu);
        } else if (CircuitEdits.isSwappableGate(c) || f.equals("NOT Gate") || f.equals("Buffer")) {
            gate(t, c, menu);
        } else if (f.equals("Tunnel")) {
            tunnel(t, c, menu);
        } else if (c.getFactory() instanceof SubcircuitFactory) {
            subcircuit(t, c, menu);
        }
    }

    static List<Component> nonWires(List<Component> sel) {
        List<Component> ret = new ArrayList<>();
        for (Component c : sel) {
            if (!(c instanceof Wire)) {
                ret.add(c);
            }
        }
        return ret;
    }

    /** 누른 점 가까이(5px 안)에 있는 포트. 없으면 -1. */
    static int portAt(Component c, Location p) {
        for (int i = 0; i < c.getEnds().size(); i++) {
            Location e = c.getEnds().get(i).getLocation();
            if (Math.abs(e.getX() - p.getX()) <= 5 && Math.abs(e.getY() - p.getY()) <= 5) {
                return i;
            }
        }
        return -1;
    }

    private static JMenuItem item(String key, Runnable r, Object... args) {
        JMenuItem it = new JMenuItem(Messages.get(key, args));
        it.addActionListener(e -> r.run());
        return it;
    }

    private static void run(Project proj, CircuitMutation m, String actionKey, Object... args) {
        StringGetter name = () -> Messages.get(actionKey, args);
        proj.doAction(m.toAction(name));
    }

    // --- 포트 ---

    void port(ContextMenus.Target t, Component c, int end, JPopupMenu menu) {
        String name = Kinds.portName(c, end);
        JMenu attach = new JMenu(Messages.get("menu.attach", name));
        boolean input = c.getEnds().get(end).isInput();
        for (CircuitEdits.Attach what : CircuitEdits.Attach.values()) {
            if (what == CircuitEdits.Attach.CONSTANT && !input) {
                continue; // 상수는 입력에만
            }
            attach.add(item("menu.attach." + what.name(), () -> run(t.project,
                    CircuitEdits.attach(t.project.getLogisimFile(), t.circuit, c, end, what, name),
                    "menu.attachAction", name)));
        }
        menu.add(attach);
        Attribute<?> negate = c.getAttributeSet().getAttribute("negate" + (end - 1));
        if (negate != null && end >= 1) {
            boolean on = Boolean.TRUE.equals(c.getAttributeSet().getValue(negate));
            menu.add(item(on ? "menu.unnegate" : "menu.negate", () -> run(t.project,
                    CircuitEdits.setAttribute(t.circuit, Collections.singletonList(c), negate.getName(),
                            Boolean.toString(!on)), "menu.negateAction", name), name));
        }
    }

    // --- 핀 ---

    void pin(ContextMenus.Target t, Component c, JPopupMenu menu) {
        boolean output = "true".equals(value(c, "output"));
        menu.add(item(output ? "menu.pinToInput" : "menu.pinToOutput",
                () -> set(t, c, "output", Boolean.toString(!output))));
        menu.add(widthMenu(t, Collections.singletonList(c)));
        boolean tri = "true".equals(value(c, "tristate"));
        menu.add(item(tri ? "menu.triOff" : "menu.triOn", () -> set(t, c, "tristate", Boolean.toString(!tri))));
        JMenu pull = new JMenu(Messages.get("menu.pull"));
        for (String p : new String[] {"none", "0", "1"}) {
            pull.add(item("menu.pull." + p, () -> set(t, c, "pull", p)));
        }
        menu.add(pull);
        menu.add(item("menu.label", () -> askLabel(t, Collections.singletonList(c))));
    }

    static String value(Component c, String attr) {
        @SuppressWarnings("unchecked")
        Attribute<Object> a = (Attribute<Object>) c.getAttributeSet().getAttribute(attr);
        if (a == null) {
            return null;
        }
        Object v = c.getAttributeSet().getValue(a);
        return v == null ? null : a.toStandardString(v);
    }

    void set(ContextMenus.Target t, Component c, String attr, String v) {
        run(t.project, CircuitEdits.setAttribute(t.circuit, Collections.singletonList(c), attr, v),
                "menu.setAction", attr);
    }

    JMenu widthMenu(ContextMenus.Target t, List<Component> comps) {
        JMenu m = new JMenu(Messages.get("menu.width"));
        for (int w : WIDTHS) {
            m.add(item("menu.bits", () -> run(t.project, CircuitEdits.setAttribute(t.circuit, comps, "width",
                    Integer.toString(w)), "menu.setAction", "width"), w));
        }
        return m;
    }

    JMenu facingMenu(ContextMenus.Target t, List<Component> comps) {
        JMenu m = new JMenu(Messages.get("menu.facing"));
        for (Direction d : new Direction[] {Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
            m.add(item("menu.facing." + d, () -> run(t.project, CircuitEdits.setAttribute(t.circuit, comps,
                    "facing", d.toString()), "menu.setAction", "facing")));
        }
        return m;
    }

    void askLabel(ContextMenus.Target t, List<Component> comps) {
        String cur = comps.size() == 1 ? Names.label(comps.get(0)) : "";
        Object s = JOptionPane.showInputDialog(t.project.getFrame(), Messages.get("menu.labelPrompt"),
                Messages.get("menu.label"), JOptionPane.PLAIN_MESSAGE, null, null, cur == null ? "" : cur);
        if (s != null) {
            run(t.project, CircuitEdits.setAttribute(t.circuit, comps, "label", s.toString().trim()),
                    "menu.setAction", "label");
        }
    }

    // --- 빈 곳 ---

    void empty(ContextMenus.Target t, JPopupMenu menu) {
        menu.add(item("menu.paste", () -> t.project.doAction(SelectionActions.pasteMaybe(t.project,
                t.project.getSelection()))));
        menu.add(item("menu.fit", () -> {
            if (t.canvas.getHcsZoom() != null) {
                t.canvas.getHcsZoom().fitCircuit();
            }
        }));
    }

    // --- 여러 부품 ---

    void multi(ContextMenus.Target t, List<Component> comps, JPopupMenu menu) {
        Set<String> common = null;
        for (Component c : comps) {
            Set<String> names = new LinkedHashSet<>();
            for (Attribute<?> a : c.getAttributeSet().getAttributes()) {
                names.add(a.getName());
            }
            if (common == null) {
                common = names;
            } else {
                common.retainAll(names);
            }
        }
        JMenu attrs = new JMenu(Messages.get("menu.bulk", comps.size()));
        if (common.contains("facing")) {
            attrs.add(facingMenu(t, comps));
        }
        if (common.contains("width")) {
            attrs.add(widthMenu(t, comps));
        }
        if (attrs.getItemCount() > 0) {
            menu.add(attrs);
        }
        if (common.contains("label")) {
            menu.add(item("menu.bulkLabels", () -> LabelsDialog.show(t, comps), comps.size()));
        }
    }

    // --- 게이트 ---

    void gate(ContextMenus.Target t, Component c, JPopupMenu menu) {
        List<Component> one = Collections.singletonList(c);
        if (c.getAttributeSet().getAttribute("inputs") != null) {
            JMenu inputs = new JMenu(Messages.get("menu.inputs"));
            for (int n = 2; n <= 8; n++) {
                int k = n;
                inputs.add(item("menu.count", () -> run(t.project, CircuitEdits.setAttribute(t.circuit, one,
                        "inputs", Integer.toString(k)), "menu.setAction", "inputs"), k));
            }
            menu.add(inputs);
        }
        if (c.getAttributeSet().getAttribute("size") != null) {
            JMenu size = new JMenu(Messages.get("menu.size"));
            for (String s : new String[] {"30", "50", "70"}) {
                size.add(item("menu.size." + s, () -> set(t, c, "size", s)));
            }
            menu.add(size);
        }
        menu.add(facingMenu(t, one));
        menu.add(widthMenu(t, one));
        if (CircuitEdits.isSwappableGate(c)) {
            JMenu kind = new JMenu(Messages.get("menu.kind"));
            for (String g : CircuitEdits.swappableGates()) {
                if (!g.equals(c.getFactory().getName())) {
                    kind.add(item("menu.kind.item", () -> run(t.project, CircuitEdits.swapGate(t.circuit, c,
                            CircuitEdits.builtin(t.project.getLogisimFile(), "Gates", g)), "menu.kindAction",
                            g.replace(" Gate", "")), g.replace(" Gate", "")));
                }
            }
            menu.add(kind);
        }
        menu.add(item("menu.label", () -> askLabel(t, one)));
        menu.add(item("menu.duplicate", () -> {
            t.project.doAction(SelectionActions.dropAll(t.project.getSelection()));
            t.project.getSelection().add(c);
            t.project.doAction(SelectionActions.duplicate(t.project.getSelection()));
        }));
    }

    // --- 선 ---

    void wire(ContextMenus.Target t, Wire w, JPopupMenu menu) {
        Netlist nl = Netlist.of(t.circuit);
        Netlist.Net net = nl.netOf(w);
        menu.add(item("menu.netInfo", () -> NetInfoDialog.show(t, net)));
        menu.add(item("menu.selectNet", () -> {
            t.project.doAction(SelectionActions.dropAll(t.project.getSelection()));
            t.project.getSelection().addAll(net.wires());
        }));
        menu.add(item("menu.toTunnels", () -> {
            Object s = JOptionPane.showInputDialog(t.project.getFrame(), Messages.get("menu.tunnelPrompt"),
                    Messages.get("menu.toTunnels"), JOptionPane.PLAIN_MESSAGE, null, null, "");
            if (s != null && !s.toString().trim().isEmpty()) {
                run(t.project, CircuitEdits.wireToTunnels(t.project.getLogisimFile(), t.circuit, w,
                        s.toString().trim(), Math.max(1, net.width())), "menu.toTunnelsAction");
            }
        }));
    }

    // --- 서브회로 ---

    void subcircuit(ContextMenus.Target t, Component c, JPopupMenu menu) {
        Circuit sub = ((SubcircuitFactory) c.getFactory()).getSubcircuit();
        menu.add(item("menu.editAppearance", () -> {
            t.project.setCurrentCircuit(sub);
            t.project.getFrame().setEditorView(Frame.EDIT_APPEARANCE);
        }, sub.getName()));
    }

    // --- 터널 ---

    void tunnel(ContextMenus.Target t, Component c, JPopupMenu menu) {
        String label = Names.label(c);
        if (label == null) {
            return;
        }
        List<Component> same = sameTunnels(t.circuit, label);
        if (same.size() > 1) {
            menu.add(item("menu.nextTunnel", () -> {
                int i = same.indexOf(c);
                Component next = same.get((i + 1) % same.size());
                t.project.doAction(SelectionActions.dropAll(t.project.getSelection()));
                t.project.getSelection().add(next);
                t.canvas.scrollRectToVisible(new java.awt.Rectangle(next.getBounds().getX() - 40,
                        next.getBounds().getY() - 40, next.getBounds().getWidth() + 80,
                        next.getBounds().getHeight() + 80));
            }, label));
        }
        menu.add(item("menu.selectTunnels", () -> {
            t.project.doAction(SelectionActions.dropAll(t.project.getSelection()));
            t.project.getSelection().addAll(same);
        }, label, same.size()));
    }

    /** 같은 이름 터널(위→아래, 왼쪽→오른쪽 순). */
    static List<Component> sameTunnels(Circuit circuit, String label) {
        List<Component> ret = new ArrayList<>();
        for (Component o : circuit.getNonWires()) {
            if (o.getFactory().getName().equals("Tunnel") && label.equals(Names.label(o))) {
                ret.add(o);
            }
        }
        ret.sort((a, b) -> a.getLocation().getY() != b.getLocation().getY()
                ? a.getLocation().getY() - b.getLocation().getY() : a.getLocation().getX() - b.getLocation().getX());
        return ret;
    }

    /** 넷 정보: 폭, 구동하는 포트, 읽는 포트(공용 식별자 이름). */
    static Map<String, List<String>> netInfo(Circuit circuit, Netlist.Net net) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        List<String> drivers = new ArrayList<>();
        for (Netlist.PortRef p : net.drivers()) {
            drivers.add(Names.port(circuit, p.component, p.end));
        }
        List<String> readers = new ArrayList<>();
        for (Netlist.PortRef p : net.readers()) {
            readers.add(Names.port(circuit, p.component, p.end));
        }
        List<String> others = new ArrayList<>();
        for (Netlist.PortRef p : net.ports()) {
            if (!net.drivers().contains(p) && !net.readers().contains(p)) {
                others.add(Names.port(circuit, p.component, p.end));
            }
        }
        m.put("drivers", drivers);
        m.put("readers", readers);
        m.put("others", others);
        return m;
    }
}
