/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.util.LocaleManager;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.edit.Arrange;
import kr.ac.hallym.hcs.app.wiring.WireGuard;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.edit.Intents.Result;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * 편집 동등성(N-09)이 쓰는 나머지 가벼운 의도(N-08, D-146): v1 Duplicate N·Align·Distribute(E-01, 창·메뉴 없이 v1
 * {@code Arrange}의 계산과 {@code WireGuard} 검사기, 결과를 고른다), 회로 속성 표(AttrTableCircuitModel), Project ›
 * Add Circuit(이름 창 없이 원조 이름 검사), Set As Main Circuit. 창을 여는 v1 메뉴(ArrangeActions)가 하는 일에서 창만 뺐다.
 */
public final class ArrangeIntents {
    private static final LocaleManager GUI = new LocaleManager("resources/logisim", "gui");

    private ArrangeIntents() {
    }

    /** 결과를 고른다(v1 ArrangeActions.apply: 원조 선택 도구처럼 떠 있는 것을 내려놓고). */
    private static void select(Doc d, List<Component> out) {
        Selection sel = d.selection();
        d.project().doAction(SelectionActions.dropAll(sel));
        sel.addAll(out);
    }

    /** 대상 부품(선은 빼고): ids를 주면 그것을 고른 뒤(재생기의 selectRefs), 아니면 지금 고른 것. */
    private static List<Component> targets(Doc d, List<Component> ids) {
        List<Component> out = new ArrayList<>();
        if (ids != null) {
            SelectionIntents.selectOnly(d, ids);
            for (Component x : ids) {
                if (!(x instanceof com.cburch.logisim.circuit.Wire)) {
                    out.add(x);
                }
            }
            return out; // 준 차례대로(재생기와 같다)
        }
        for (Component x : d.selection().getComponents()) {
            if (!(x instanceof com.cburch.logisim.circuit.Wire)) {
                out.add(x);
            }
        }
        out.sort((a, b) -> Integer.compare(a.getLocation().getY(), b.getLocation().getY()) != 0
                ? Integer.compare(a.getLocation().getY(), b.getLocation().getY())
                : Integer.compare(a.getLocation().getX(), b.getLocation().getX()));
        return out;
    }

    private static String label(Component c) {
        return c.getAttributeSet().containsAttribute(com.cburch.logisim.instance.StdAttr.LABEL)
                ? c.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.LABEL) : null;
    }

    /**
     * Duplicate N…(v1 E-01): count(1~64), direction, spacing(없으면 v1 기본: 묶음 크기 + 10), number(없으면 라벨이
     * 있을 때 켬). 검사기가 막으면 outcome "refused"(바꾸지 않음).
     */
    public static Result duplicateN(Doc d, Circuit c, List<Component> ids, int count, String direction, Integer spacing,
            Boolean number) throws RpcError {
        Intents.editable(d, c);
        d.show(c);
        List<Component> comps = targets(d, ids);
        if (comps.isEmpty()) {
            return Result.unchanged("empty");
        }
        if (count < 1 || count > Arrange.MAX_COPIES) {
            throw RpcError.params("count must be from 1 to " + Arrange.MAX_COPIES);
        }
        Arrange.Dir dir;
        try {
            dir = Arrange.Dir.valueOf(direction.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw RpcError.params("direction must be right, down, left or up");
        }
        int space = spacing != null ? spacing : Arrange.defaultSpacing(comps, dir);
        boolean numbered = number != null ? number : comps.stream().anyMatch(x -> {
            String l = label(x);
            return l != null && !l.isEmpty();
        });
        List<Component> made = new ArrayList<>();
        CircuitMutation m = Arrange.copies(c, comps, count, dir, space, numbered, made);
        if (!WireGuard.run(d.project(), c, m, Collections.<Location>emptyList(),
                () -> Messages.get("replicate.action", count))) {
            return Result.unchanged("refused");
        }
        select(d, made);
        return new Result(true, null, null);
    }

    /** Align(v1 E-01): 이어진 부품이 있으면 옮기지 않는다(outcome "connected"), 움직일 것이 없으면 "nothing". */
    public static Result align(Doc d, Circuit c, List<Component> ids, String mode) throws RpcError {
        Intents.editable(d, c);
        d.show(c);
        Arrange.Align a;
        switch (mode) {
        case "left": a = Arrange.Align.LEFT; break;
        case "centerX": a = Arrange.Align.CENTER_X; break;
        case "right": a = Arrange.Align.RIGHT; break;
        case "top": a = Arrange.Align.TOP; break;
        case "centerY": a = Arrange.Align.CENTER_Y; break;
        case "bottom": a = Arrange.Align.BOTTOM; break;
        default: throw RpcError.params("mode must be left, centerX, right, top, centerY or bottom");
        }
        List<Component> comps = targets(d, ids);
        if (!Arrange.connectedOnes(c, comps).isEmpty()) {
            return Result.unchanged("connected");
        }
        List<Component> out = new ArrayList<>();
        CircuitMutation m = Arrange.align(c, comps, a, out);
        if (m == null) {
            return Result.unchanged("nothing");
        }
        if (!WireGuard.run(d.project(), c, m, Collections.<Location>emptyList(),
                () -> Messages.get("arrange.alignAction"))) {
            return Result.unchanged("refused");
        }
        select(d, out);
        return new Result(true, null, null);
    }

    /** Distribute(v1 E-01, 셋 이상, 양 끝 고정). */
    public static Result distribute(Doc d, Circuit c, List<Component> ids, String axis) throws RpcError {
        Intents.editable(d, c);
        d.show(c);
        if (!axis.equals("h") && !axis.equals("v")) {
            throw RpcError.params("axis must be h or v");
        }
        List<Component> comps = targets(d, ids);
        if (!Arrange.connectedOnes(c, comps).isEmpty()) {
            return Result.unchanged("connected");
        }
        List<Component> out = new ArrayList<>();
        CircuitMutation m = Arrange.distribute(c, comps, axis.equals("h"), out);
        if (m == null) {
            return Result.unchanged("nothing");
        }
        if (!WireGuard.run(d.project(), c, m, Collections.<Location>emptyList(),
                () -> Messages.get("arrange.distributeAction"))) {
            return Result.unchanged("refused");
        }
        select(d, out);
        return new Result(true, null, null);
    }

    /** 회로 속성 표(AttrTableCircuitModel.setValueRequested): 이름 {@code circuit}, 라벨 {@code clabel} … 한 단계. */
    public static Result setCircuitAttr(Doc d, Circuit c, String attr, String value) throws RpcError {
        Intents.editable(d, c);
        @SuppressWarnings("unchecked")
        Attribute<Object> a = (Attribute<Object>) c.getStaticAttributes().getAttribute(attr);
        if (a == null) {
            throw RpcError.params("circuits have no attribute '" + attr + "'");
        }
        CircuitMutation xn = new CircuitMutation(c);
        xn.setForCircuit(a, Intents.parse(a, value));
        d.project().doAction(xn.toAction(GUI.getter("changeCircuitAttrAction")));
        return new Result(true, null, null);
    }

    /**
     * Project › Add Circuit…(이름 창 없이): 원조 이름 검사(빈 이름·이미 있는 이름이면 -32602, data.reason
     * {@code nameMissing}·{@code nameTaken}). 새 회로가 지금 회로가 된다.
     */
    public static Result createCircuit(Doc d, String name) throws RpcError {
        if (d.isReadOnly()) {
            throw RpcError.notEditable("readOnly", "the file is read-only");
        }
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw reason("nameMissing", "a circuit needs a name");
        }
        if (d.file().getTool(n) != null) {
            throw reason("nameTaken", "there is a circuit or tool named " + n);
        }
        Circuit circuit = new Circuit(n);
        d.project().doAction(LogisimFileActions.addCircuit(circuit));
        d.show(circuit);
        return new Result(true, null, null, circuit);
    }

    /** Project › Set As Main Circuit. */
    public static Result setMainCircuit(Doc d, Circuit c) throws RpcError {
        Intents.editable(d, c);
        if (d.file().getMainCircuit() == c) {
            return Result.unchanged("same");
        }
        d.project().doAction(LogisimFileActions.setMainCircuit(c));
        return new Result(true, null, null);
    }

    private static RpcError reason(String reason, String message) {
        com.google.gson.JsonObject data = new com.google.gson.JsonObject();
        data.addProperty("reason", reason);
        return new RpcError(RpcError.INVALID_PARAMS, message, data);
    }

}
