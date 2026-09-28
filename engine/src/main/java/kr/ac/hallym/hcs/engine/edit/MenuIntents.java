/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.edit;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.tools.MenuExtender;
import com.cburch.logisim.tools.SetAttributeAction;
import com.cburch.logisim.util.StringGetter;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.edit.CircuitEdits;
import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.probe.QuickProbe;
import kr.ac.hallym.hcs.app.splitter.SplitterEdits;
import kr.ac.hallym.hcs.app.splitter.SplitterSpec;
import kr.ac.hallym.hcs.app.wiring.WireGuard;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * 우클릭 메뉴의 편집 의도(N-10, D-157). 모두 v1 항목의 동작(v1 {@code EditMenus}·{@code ProbeMenu}·
 * {@code SplitterMenu}·{@code LabelsDialog}, 원조 {@code SplitterDistributeItem})을 그대로 {@code Project.doAction}
 * 한 번에 넘긴다: 되돌리기 한 단계, 원조 부품과 원조 속성만 바뀐다. 새 부품·선을 자동으로 두는 것은 v1 검사기
 * ({@link WireGuard}, W-05)를 거치고, 막히면 {@code changed:false, outcome:"refused"}다.
 */
public final class MenuIntents {
    private MenuIntents() {
    }

    // ---- edit.labels: Edit Labels of N Components…(v1 LabelsDialog: 한 동작) ----

    /** 부품마다 라벨(id → 글). 같은 것은 건너뛰고, 모두 같으면 {@code same}. */
    public static Intents.Result labels(Doc d, Circuit c, Map<Component, String> labels) throws RpcError {
        Intents.editable(d, c);
        SetAttributeAction act = new SetAttributeAction(c, () -> Messages.get("menu.bulkLabelsAction"));
        boolean any = false;
        for (Map.Entry<Component, String> e : labels.entrySet()) {
            Component x = e.getKey();
            @SuppressWarnings("unchecked")
            Attribute<Object> a = (Attribute<Object>) x.getAttributeSet().getAttribute("label");
            if (a == null) {
                throw RpcError.params("component " + d.ids().of(x) + " has no label");
            }
            Object v = Intents.parse(a, e.getValue().trim());
            if (!v.equals(x.getAttributeSet().getValue(a))) {
                act.set(x, a, v);
                any = true;
            }
        }
        if (!any) {
            return Intents.Result.unchanged("same");
        }
        d.show(c);
        d.project().doAction(act);
        return new Intents.Result(true, null, null);
    }

    // ---- edit.attach: Attach to <port> ▸ Pin / Constant / Probe / Tunnel(v1 EditMenus.port) ----

    public static Intents.Result attach(Doc d, Circuit c, Component x, int port, String what) throws RpcError {
        Intents.editable(d, c);
        if (x instanceof Wire || port < 0 || port >= x.getEnds().size()) {
            throw RpcError.params("component " + d.ids().of(x) + " has no port " + port);
        }
        CircuitEdits.Attach kind;
        try {
            kind = CircuitEdits.Attach.valueOf(what.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw RpcError.params("what must be pin, constant, probe or tunnel");
        }
        if (kind == CircuitEdits.Attach.CONSTANT && !x.getEnds().get(port).isInput()) {
            throw RpcError.params("a constant goes on an input only");
        }
        String name = Kinds.portName(x, port);
        CircuitMutation m = CircuitEdits.attach(d.file(), c, x, port, kind, name);
        return guarded(d, c, m, Collections.singletonList(x.getEnd(port).getLocation()),
                () -> Messages.get("menu.attachAction", name));
    }

    // ---- edit.swapGate: Change Gate To ▸(v1 CircuitEdits.swapGate) ----

    public static Intents.Result swapGate(Doc d, Circuit c, Component gate, String to) throws RpcError {
        Intents.editable(d, c);
        if (!CircuitEdits.isSwappableGate(gate)) {
            throw RpcError.params("component " + d.ids().of(gate) + " is not an AND/OR/NAND/NOR/XOR/XNOR gate");
        }
        if (!Arrays.asList(CircuitEdits.swappableGates()).contains(to)) {
            throw RpcError.params("unknown gate " + to);
        }
        if (to.equals(gate.getFactory().getName())) {
            return Intents.Result.unchanged("same");
        }
        CircuitMutation m = CircuitEdits.swapGate(c, gate, CircuitEdits.builtin(d.file(), "Gates", to));
        String short_ = to.replace(" Gate", "");
        return guarded(d, c, m, WireGuard.ends(gate), () -> Messages.get("menu.kindAction", short_));
    }

    // ---- edit.deleteNet: Delete Net Wires(v1 W-04) ----

    public static Intents.Result deleteNet(Doc d, Circuit c, Wire w) throws RpcError {
        Intents.editable(d, c);
        Netlist.Net net = Netlist.of(c).netOf(w);
        if (net == null || net.wires().isEmpty()) {
            return Intents.Result.unchanged("empty");
        }
        d.show(c);
        d.project().doAction(com.cburch.logisim.gui.main.SelectionActions.dropAll(d.selection()));
        d.project().doAction(CircuitEdits.deleteNetWires(c, net).toAction(() -> Messages.get("menu.deleteNetAction")));
        return new Intents.Result(true, null, null);
    }

    // ---- edit.wireToTunnels: Replace Wire with Tunnels…(v1) ----

    public static Intents.Result wireToTunnels(Doc d, Circuit c, Wire w, String label) throws RpcError {
        Intents.editable(d, c);
        String name = label == null ? "" : label.trim();
        if (name.isEmpty()) {
            throw RpcError.params("a tunnel needs a name");
        }
        Netlist.Net net = Netlist.of(c).netOf(w);
        int width = Math.max(1, net == null ? 1 : net.width());
        CircuitMutation m = CircuitEdits.wireToTunnels(d.file(), c, w, name, width);
        return guarded(d, c, m, Arrays.asList(w.getEnd0(), w.getEnd1()), () -> Messages.get("menu.toTunnelsAction"));
    }

    // ---- edit.probe: Attach Probe ▸ radix, P on a wire(v1 ProbeMenu, QuickProbe) ----

    /** 선 w 위 at 가까이에 프로브. 자리가 없으면 {@code noRoom}, 검사기가 막으면 {@code refused}. */
    public static Intents.Result probe(Doc d, Circuit c, Wire w, Location at, String radix) throws RpcError {
        Intents.editable(d, c);
        if (radix == null) {
            Netlist.Net net = Netlist.of(c).netOf(w);
            radix = net != null && net.width() == 1 ? "2" : "16"; // P 키(v1 installKey)
        }
        if (!Arrays.asList(QuickProbe.RADICES).contains(radix)) {
            throw RpcError.params("radix must be one of " + Arrays.toString(QuickProbe.RADICES));
        }
        QuickProbe.Placement pl = QuickProbe.find(d.file(), c, w, at);
        if (pl == null) {
            return Intents.Result.unchanged("noRoom");
        }
        String label = QuickProbe.netName(c, Netlist.of(c).netOf(w));
        CircuitMutation m = QuickProbe.place(d.file(), c, pl, radix, label);
        List<Component> before = new ArrayList<>(QuickProbe.probes(c));
        Intents.Result r = guarded(d, c, m, Collections.singletonList(pl.p), () -> Messages.get("probe.attachAction"));
        if (!r.changed) {
            return r;
        }
        for (Component x : QuickProbe.probes(c)) {
            if (!before.contains(x)) {
                return new Intents.Result(true, null, x);
            }
        }
        return r;
    }

    // ---- edit.deleteProbes: Delete All Probes (n)(v1 ProbeMenu) ----

    public static Intents.Result deleteProbes(Doc d, Circuit c) throws RpcError {
        Intents.editable(d, c);
        List<Component> probes = QuickProbe.probes(c);
        if (probes.isEmpty()) {
            return Intents.Result.unchanged("empty");
        }
        d.show(c);
        d.project().doAction(com.cburch.logisim.gui.main.SelectionActions.dropAll(d.selection()));
        d.project().doAction(QuickProbe.removeAll(c, probes).toAction(() -> Messages.get("probe.deleteAllAction")));
        return new Intents.Result(true, null, null);
    }

    // ---- edit.combineBus: Combine N Wires into One Bus (in the order chosen)(v1 SplitterMenu.combine) ----

    /** 고른 차례의 선들을 한 버스로. 새 스플리터는 선들 오른쪽 +60에 서쪽을 보고, 잇지 않는다. result id = 새 스플리터. */
    public static Intents.Result combineBus(Doc d, Circuit c, List<Component> wires) throws RpcError {
        Intents.editable(d, c);
        if (wires.size() < 2) {
            throw RpcError.params("pick two or more wires");
        }
        List<Integer> widths = new ArrayList<>();
        Bounds box = null;
        for (Component x : wires) {
            if (!(x instanceof Wire)) {
                throw RpcError.params("component " + d.ids().of(x) + " is not a wire");
            }
            int w = MenuFacts.wireWidth(c, (Wire) x);
            if (w <= 0) {
                throw RpcError.params("the width of wire " + d.ids().of(x) + " is not known yet");
            }
            widths.add(w);
            box = box == null ? x.getBounds() : box.add(x.getBounds());
        }
        SplitterSpec spec = SplitterSpec.combine(widths, null);
        if (spec.width() > 32) {
            return Intents.Result.unchanged("tooWide");
        }
        int x0 = (box.getX() + box.getWidth() + 60) / 10 * 10;
        int y0 = (box.getY() + box.getHeight() / 2) / 10 * 10;
        Location at = Location.create(x0, y0);
        CircuitMutation m = SplitterEdits.create(d.file(), c, at, Direction.WEST, spec);
        List<Component> before = splitters(c, at);
        // 연결은 학생이 한다: 새 스플리터는 어디에도 닿지 않아야 한다(v1)
        Intents.Result r = guarded(d, c, m, Collections.emptyList(), () -> Messages.get("splitter.combineAction"));
        if (!r.changed) {
            return r;
        }
        for (Component x : splitters(c, at)) {
            if (!before.contains(x)) {
                return new Intents.Result(true, null, x);
            }
        }
        return r;
    }

    private static List<Component> splitters(Circuit c, Location at) {
        List<Component> ret = new ArrayList<>();
        for (Component x : c.getNonWires(at)) {
            if (x.getFactory().getName().equals("Splitter")) {
                ret.add(x);
            }
        }
        return ret;
    }

    // ---- edit.originalItem: 원조 부품 메뉴의 항목(Splitter Distribute Ascending·Descending) ----

    /**
     * 원조 부품 메뉴(MenuExtender)의 i번째 항목을 원조 코드 그대로 한다(SplitterDistributeItem.actionPerformed:
     * 원조 CircuitMutation 한 단계). 창이 필요한 항목이 없는 부품(Splitter)만 받는다. 꺼진 항목은 {@code disabled}.
     */
    public static Intents.Result originalItem(Doc d, Circuit c, Component x, int index) throws RpcError {
        Intents.editable(d, c);
        if (!x.getFactory().getName().equals("Splitter")) {
            throw RpcError.params("component " + d.ids().of(x) + " has no menu items the engine can do");
        }
        Object ext = x.getFeature(MenuExtender.class);
        if (!(ext instanceof MenuExtender)) {
            throw RpcError.params("component " + d.ids().of(x) + " has no menu items");
        }
        d.show(c); // 원조 항목은 지금 회로(proj.getCircuitState())에 한다
        JPopupMenu menu = new JPopupMenu();
        ((MenuExtender) ext).configureMenu(menu, d.project());
        List<JMenuItem> items = new ArrayList<>();
        for (java.awt.Component item : menu.getComponents()) {
            if (item instanceof JMenuItem) {
                items.add((JMenuItem) item);
            }
        }
        if (index < 0 || index >= items.size()) {
            throw RpcError.params("no menu item " + index);
        }
        JMenuItem item = items.get(index);
        if (!item.isEnabled()) {
            return Intents.Result.unchanged("disabled");
        }
        com.cburch.logisim.proj.Action before = d.project().getLastAction();
        ActionEvent ev = new ActionEvent(item, ActionEvent.ACTION_PERFORMED, item.getActionCommand());
        for (ActionListener l : item.getActionListeners()) {
            l.actionPerformed(ev);
        }
        return new Intents.Result(d.project().getLastAction() != before, null, null);
    }

    // ---- edit.memContents: ROM Edit Contents…·Clear Contents·Load Image…(원조 MemMenu, RomContentsListener) ----

    public static Intents.Result memContents(Doc d, Circuit c, Component x, Long addr, List<Long> values,
            boolean clear, String file) throws RpcError {
        Intents.editable(d, c);
        boolean changed = kr.ac.hallym.hcs.engine.sim.Memories.editRom(d, c, x, addr, values, clear, file);
        return changed ? new Intents.Result(true, null, null) : Intents.Result.unchanged("same");
    }

    // ---- 공통 ----

    private static Intents.Result guarded(Doc d, Circuit c, CircuitMutation m, java.util.Collection<Location> allowed,
            StringGetter name) {
        if (!WireGuard.problems(d.project(), c, m, allowed).isEmpty()) {
            return Intents.Result.unchanged("refused");
        }
        d.show(c);
        d.project().doAction(m.toAction(name));
        return new Intents.Result(true, null, null);
    }
}
