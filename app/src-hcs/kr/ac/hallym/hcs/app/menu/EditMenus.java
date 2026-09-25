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
import kr.ac.hallym.hcs.app.wiring.SafeDuplicate;
import kr.ac.hallym.hcs.app.wiring.WireGuard;
import kr.ac.hallym.hcs.app.wiring.WireMarks;

/**
 * 대상별 우클릭 항목(#72, #73): 포트(핀·상수·프로브·터널 붙이기, 입력 부정), 핀(방향·폭·3상태·풀·라벨),
 * 빈 곳(붙여넣기·화면 맞춤), 여러 부품(속성·라벨 일괄), 게이트(입력 수·크기·방향·폭·종류 바꾸기·복제),
 * 선(넷 정보·넷 선택·넷 강조·넷 선 지우기·터널로 바꾸기), 서브회로(모양 편집), 터널(같은 이름으로 이동·모두 선택). 모든 항목은
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
        menu.add(duplicate(t, c));
        menu.add(showAttributes(t, c));
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

    /** 공통 "속성 패널에서 보기": 이 부품만 고르고 오른쪽 속성 패널을 편다(원조 "속성 보기"를 대신). */
    static javax.swing.JMenuItem showAttributes(ContextMenus.Target t, Component c) {
        return MenuLayout.group(item("menu.showAttrs", () -> {
            t.project.doAction(SelectionActions.dropAll(t.project.getSelection()));
            t.project.getSelection().add(c);
            kr.ac.hallym.hcs.app.props.AttrDock dock = kr.ac.hallym.hcs.app.props.AttrDock.find(t.project.getFrame());
            if (dock != null) {
                dock.showAll();
            }
        }), MenuLayout.COMMON);
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

    static JMenuItem item(String key, Runnable r, Object... args) {
        JMenuItem it = new JMenuItem(Messages.get(key, args));
        it.addActionListener(e -> r.run());
        return it;
    }

    private static void run(Project proj, CircuitMutation m, String actionKey, Object... args) {
        StringGetter name = () -> Messages.get(actionKey, args);
        proj.doAction(m.toAction(name));
    }

    /** 새 부품·선을 자동으로 두는 편집: 검사기 하나(W-05)를 거친다. allowed는 옛 넷에 닿아도 되는 점. */
    private static void guarded(Project proj, Circuit circuit, CircuitMutation m,
            java.util.Collection<Location> allowed, String actionKey, Object... args) {
        WireGuard.run(proj, circuit, m, allowed, () -> Messages.get(actionKey, args));
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
            attach.add(item("menu.attach." + what.name(), () -> guarded(t.project, t.circuit,
                    CircuitEdits.attach(t.project.getLogisimFile(), t.circuit, c, end, what, name),
                    Collections.singletonList(c.getEnd(end).getLocation()), "menu.attachAction", name)));
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
        menu.add(cycleRow(t, c));
        JMenu pull = optionMenu(t, c, "pull", "menu.pull");
        if (pull != null) {
            menu.add(pull);
        }
        menu.add(item("menu.label", () -> askLabel(t, Collections.singletonList(c))));
    }

    /**
     * 속성의 선택지(원조 저장 값, 원조 표시 이름). 손으로 적지 않고 원조 속성에서 읽는다. 선택지가 정해진 속성이
     * 아니면 빈 목록.
     */
    static List<String[]> options(Component c, String attr) {
        List<String[]> ret = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Attribute<Object> a = (Attribute<Object>) c.getAttributeSet().getAttribute(attr);
        if (a == null) {
            return ret;
        }
        java.awt.Component editor = a.getCellEditor(null, c.getAttributeSet().getValue(a));
        if (editor instanceof javax.swing.JComboBox) {
            javax.swing.JComboBox<?> combo = (javax.swing.JComboBox<?>) editor;
            for (int i = 0; i < combo.getItemCount(); i++) {
                Object v = combo.getItemAt(i);
                ret.add(new String[] {a.toStandardString(v), a.toDisplayString(v)});
            }
        }
        return ret;
    }

    JMenu optionMenu(ContextMenus.Target t, Component c, String attr, String titleKey) {
        List<String[]> opts = options(c, attr);
        if (opts.isEmpty()) {
            return null;
        }
        JMenu m = new JMenu(Messages.get(titleKey));
        for (String[] o : opts) {
            JMenuItem it = new JMenuItem(o[1]);
            it.addActionListener(e -> set(t, c, attr, o[0]));
            m.add(it);
        }
        return m;
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
        JMenu size = optionMenu(t, c, "size", "menu.size");
        if (size != null) {
            menu.add(size); // 선택지는 부품마다 다르다(NOT은 20·30, AND 등은 30·50·70)
        }
        menu.add(facingMenu(t, one));
        menu.add(widthMenu(t, one));
        if (CircuitEdits.isSwappableGate(c)) {
            JMenu kind = new JMenu(Messages.get("menu.kind"));
            for (String g : CircuitEdits.swappableGates()) {
                if (!g.equals(c.getFactory().getName())) {
                    kind.add(item("menu.kind.item", () -> guarded(t.project, t.circuit, CircuitEdits.swapGate(t.circuit,
                            c, CircuitEdits.builtin(t.project.getLogisimFile(), "Gates", g)), WireGuard.ends(c),
                            "menu.kindAction",
                            g.replace(" Gate", "")), g.replace(" Gate", "")));
                }
            }
            menu.add(kind);
        }
        menu.add(item("menu.label", () -> askLabel(t, one)));
    }

    /** 공통: 이 부품 하나를 복제(원조 선택 복제). 부품 종류와 무관하게 공통 묶음에 둔다. */
    static javax.swing.JMenuItem duplicate(ContextMenus.Target t, Component c) {
        return MenuLayout.group(item("menu.duplicate", () -> {
            t.project.doAction(SelectionActions.dropAll(t.project.getSelection()));
            t.project.getSelection().add(c);
            SafeDuplicate.run(t.project, t.project.getSelection());
        }), MenuLayout.COMMON);
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
        menu.add(item("cycle.add", () -> kr.ac.hallym.hcs.app.cycle.CycleView.addWire(t.project, t.circuit, w)));
        boolean lit = WireMarks.highlighted(t.circuit).contains(w);
        menu.add(item(lit ? "menu.unhighlightNet" : "menu.highlightNet", () -> {
            WireMarks.highlight(t.circuit, lit ? null : net);
            t.project.getFrame().getCanvas().repaint();
        }));
        menu.add(item("menu.deleteNet", () -> {
            t.project.doAction(SelectionActions.dropAll(t.project.getSelection()));
            run(t.project, CircuitEdits.deleteNetWires(t.circuit, net), "menu.deleteNetAction");
        }));
        menu.add(item("menu.toTunnels", () -> {
            Object s = JOptionPane.showInputDialog(t.project.getFrame(), Messages.get("menu.tunnelPrompt"),
                    Messages.get("menu.toTunnels"), JOptionPane.PLAIN_MESSAGE, null, null, "");
            if (s != null && !s.toString().trim().isEmpty()) {
                guarded(t.project, t.circuit, CircuitEdits.wireToTunnels(t.project.getLogisimFile(), t.circuit, w,
                        s.toString().trim(), Math.max(1, net.width())), java.util.Arrays.asList(w.getEnd0(),
                        w.getEnd1()), "menu.toTunnelsAction");
            }
        }));
    }

    // --- 서브회로 ---

    void subcircuit(ContextMenus.Target t, Component c, JPopupMenu menu) {
        Circuit sub = ((SubcircuitFactory) c.getFactory()).getSubcircuit();
        // 다른 파일(라이브러리)의 회로는 원본 파일에서 고친다(P-03): 여기서 모양을 바꾸면 그 파일에 저장되지 않고
        // 다음 갱신 때 사라지므로 모양 편집 항목 대신 원본 파일로 가는 항목만 둔다
        java.io.File origin = kr.ac.hallym.hcs.app.libs.LibrarySync.originFile(t.project, sub);
        if (origin != null) {
            menu.add(item("menu.editOriginal", () -> kr.ac.hallym.hcs.app.libs.LibrarySync.editOriginal(t.project,
                    origin, sub.getName()), origin.getName()));
            return;
        }
        menu.add(item("menu.editAppearance", () -> {
            t.project.setCurrentCircuit(sub);
            t.project.getFrame().setEditorView(Frame.EDIT_APPEARANCE);
        }, sub.getName()));
        menu.add(item("menu.autoAppearance", () -> kr.ac.hallym.hcs.app.appear.AutoAppearance.run(t.project, sub,
                t.canvas)));
        // C-05: 레지스터 파일 표시(파일에 저장, 조교가 템플릿에 해 둘 수도 있다)
        com.cburch.logisim.file.LogisimFile file = t.project.getLogisimFile();
        boolean marked = kr.ac.hallym.hcs.app.cycle.RegisterFile.marked(file) == sub;
        menu.add(item(marked ? "regfile.unmark" : "regfile.mark", () -> t.project.doAction(
                kr.ac.hallym.hcs.app.cycle.RegisterFile.markAction(file, sub, !marked))));
        if (marked) {
            menu.add(item("regfile.mapping", () -> kr.ac.hallym.hcs.app.cycle.RegisterMappingDialog.show(t.project,
                    sub)));
        }
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
                t.canvas.scrollRectToVisible(t.canvas.hcsToScreen(new java.awt.Rectangle(
                        next.getBounds().getX() - 40, next.getBounds().getY() - 40, next.getBounds().getWidth() + 80,
                        next.getBounds().getHeight() + 80)));
            }, label));
        }
        menu.add(item("menu.selectTunnels", () -> {
            t.project.doAction(SelectionActions.dropAll(t.project.getSelection()));
            t.project.getSelection().addAll(same);
        }, label, same.size()));
        menu.add(tunnelColorMenu(t, c));
        menu.add(cycleRow(t, c));
    }

    /** C-02: 이 부품(터널·핀)의 넷을 사이클 표 줄로. 선이 없는 회로(터널로만 잇는)에서도 신호를 고를 수 있게. */
    static javax.swing.JMenuItem cycleRow(ContextMenus.Target t, Component c) {
        return item("cycle.add", () -> kr.ac.hallym.hcs.app.cycle.CycleView.addPort(t.project, t.circuit,
                c.getEnd(0).getLocation()));
    }

    /** 터널 색 직접 지정(.circ 확장 정보에 저장, D-042). "자동"은 이름으로 정한 색(저장 안 함). */
    javax.swing.JMenu tunnelColorMenu(ContextMenus.Target t, Component c) {
        String name = kr.ac.hallym.hcs.app.labels.TunnelColorStore.name(c);
        javax.swing.JMenu m = new javax.swing.JMenu(Messages.get("tunnel.color"));
        if (name == null) {
            m.setEnabled(false);
            return m;
        }
        com.cburch.logisim.file.LogisimFile file = t.project.getLogisimFile();
        java.awt.Color now = kr.ac.hallym.hcs.app.labels.TunnelColorStore.get(file, t.circuit, name);
        javax.swing.JRadioButtonMenuItem auto = new javax.swing.JRadioButtonMenuItem(
                Messages.get("tunnel.color.auto"), now == null);
        auto.addActionListener(e -> t.project.doAction(
                kr.ac.hallym.hcs.app.labels.TunnelColorStore.action(file, t.circuit, name, null)));
        m.add(auto);
        m.addSeparator();
        java.awt.Color[] pal = kr.ac.hallym.hcs.app.labels.TunnelColors.PALETTE;
        for (int i = 0; i < pal.length; i++) {
            java.awt.Color col = pal[i];
            javax.swing.JRadioButtonMenuItem it = new javax.swing.JRadioButtonMenuItem(
                    Messages.get("tunnel.color." + i), swatch(col), col.equals(now));
            it.addActionListener(e -> t.project.doAction(
                    kr.ac.hallym.hcs.app.labels.TunnelColorStore.action(file, t.circuit, name, col)));
            m.add(it);
        }
        return m;
    }

    private static javax.swing.Icon swatch(java.awt.Color col) {
        return new javax.swing.Icon() {
            public int getIconWidth() {
                return 12;
            }

            public int getIconHeight() {
                return 12;
            }

            public void paintIcon(java.awt.Component comp, java.awt.Graphics g, int x, int y) {
                g.setColor(col);
                g.fillRect(x, y, 12, 12);
                g.setColor(java.awt.Color.DARK_GRAY);
                g.drawRect(x, y, 11, 11);
            }
        };
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
            drivers.add(Names.portTitle(circuit, p.component, p.end));
        }
        List<String> readers = new ArrayList<>();
        for (Netlist.PortRef p : net.readers()) {
            readers.add(Names.portTitle(circuit, p.component, p.end));
        }
        List<String> others = new ArrayList<>();
        for (Netlist.PortRef p : net.ports()) {
            if (!net.drivers().contains(p) && !net.readers().contains(p)) {
                others.add(Names.portTitle(circuit, p.component, p.end));
            }
        }
        m.put("drivers", drivers);
        m.put("readers", readers);
        m.put("others", others);
        return m;
    }
}
