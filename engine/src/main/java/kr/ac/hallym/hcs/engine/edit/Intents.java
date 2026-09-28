/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.SetAttributeAction;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.tools.WireRepair;
import com.cburch.logisim.tools.WireRepairData;
import com.cburch.logisim.tools.move.MoveGesture;
import com.cburch.logisim.tools.move.MoveResult;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.StringGetter;

import kr.ac.hallym.hcs.app.edit.RedoStack;
import kr.ac.hallym.hcs.app.libs.MipsShadow;
import kr.ac.hallym.hcs.app.wiring.SafeMove;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * 편집 의도(edit.*, docs/engine-api.md 5절). 각 의도는 Swing 도구가 마우스를 뗄 때 하는 일에서 GUI를 뺀 것이다:
 * 같은 Logisim 클래스({@link CircuitMutation}, {@link SetAttributeAction}, {@code WireRepair}, {@link MoveGesture})를
 * 같은 순서로 부르고 {@link Project#doAction}에 한 동작으로 넘긴다. 그래서 되돌리기 한 단계이고, 선 합치기·나누기는
 * 원조 {@code CircuitTransaction}이 한다. 원조 도구 코드는 바꾸지 않는다.
 */
public final class Intents {
    /** 원조 도구·편집 창의 동작 이름(되돌리기 기록에 남는 글자). */
    private static final LocaleManager TOOLS = new LocaleManager("resources/logisim", "tools");
    static final LocaleManager GUI = new LocaleManager("resources/logisim", "gui");

    private Intents() {
    }

    /** 의도 하나의 결과. */
    public static final class Result {
        public final boolean changed;
        public final String outcome;
        public final Component added;
        /** 새로 생긴 회로(edit.createCircuit). */
        public final Circuit circuit;
        /** 응답에 더 싣는 것(N-11, D-153: 끊어질 연결, 가져오기 계획, 모양 도형 번호 등). 없으면 null. */
        public com.google.gson.JsonObject extra;

        Result(boolean changed, String outcome, Component added) {
            this(changed, outcome, added, null);
        }

        Result(boolean changed, String outcome, Component added, Circuit circuit) {
            this.changed = changed;
            this.outcome = outcome;
            this.added = added;
            this.circuit = circuit;
        }

        static Result unchanged(String outcome) {
            return new Result(false, outcome, null);
        }

        /** 응답에 key를 더한다(N-11). */
        Result with(String key, com.google.gson.JsonElement value) {
            if (extra == null) {
                extra = new com.google.gson.JsonObject();
            }
            extra.add(key, value);
            return this;
        }
    }

    // ---- edit.addComponent: AddTool.mousePressed + mouseReleased ----

    /** 부품 놓기. lib가 null이면 이 파일의 회로(서브회로)이고 name은 회로 이름이다. */
    public static Result addComponent(Doc d, Circuit c, String lib, String name, Location loc,
            Map<String, String> attrs) throws RpcError {
        editable(d, c);
        AddTool tool = findTool(d, lib, name);
        ComponentFactory factory = tool.getFactory();
        if (factory == null) {
            throw RpcError.notFound("tool", (lib == null ? "" : lib + "/") + name);
        }
        if (factory instanceof SubcircuitFactory
                && !d.project().getDependencies().canAdd(c, ((SubcircuitFactory) factory).getSubcircuit())) {
            throw RpcError.notEditable("circular", "a circuit cannot contain itself");
        }
        AttributeSet as = (AttributeSet) tool.getAttributeSet().clone();
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            set(as, e.getKey(), e.getValue());
        }
        Component comp = factory.createComponent(loc, as);
        if (c.hasConflict(comp)) {
            throw RpcError.notEditable("exclusive", "another component already drives that point");
        }
        Bounds b = comp.getBounds();
        if (b.getX() < 0 || b.getY() < 0) {
            throw RpcError.notEditable("negativeCoord", "a component cannot be placed at negative coordinates");
        }
        d.show(c);
        // 부품 도구를 들면 원조 Project.setTool이 떠 있는 것을 내려놓고 선택을 비운다(N-08, D-146)
        Selection sel = d.selection();
        d.project().doAction(SelectionActions.dropAll(sel));
        CircuitMutation m = new CircuitMutation(c);
        m.add(comp);
        d.project().doAction(m.toAction(TOOLS.getter("addComponentAction", factory.getDisplayGetter())));
        // 놓은 뒤 Edit Tool로 돌아가 놓은 부품을 고른다(AddTool.mouseReleased, "After adding component" 기본값)
        sel.add(comp);
        return new Result(true, null, comp);
    }

    /** 부품 목록에서 도구를 찾는다(파일의 라이브러리와, 아직 파일에 없는 번들 MIPS 라이브러리). */
    static AddTool findTool(Doc d, String lib, String name) throws RpcError {
        if (lib == null || lib.isEmpty()) {
            for (AddTool t : d.file().getTools()) {
                if (t.getName().equals(name)) {
                    return t;
                }
            }
            throw RpcError.notFound("tool", name);
        }
        for (Library l : MipsShadow.libraries(d.file())) {
            if (l.getName().equals(lib)) {
                Tool t = l.getTool(name);
                if (t instanceof AddTool) {
                    return (AddTool) t;
                }
            }
        }
        throw RpcError.notFound("tool", lib + "/" + name);
    }

    // ---- edit.addWire: WiringTool.mousePressed/Dragged/Released ----

    /** 선 긋기. points는 [시작, 끝] 또는 [시작, 꺾는 점, 끝](ㄱ자). */
    public static Result addWire(Doc d, Circuit c, List<Location> points) throws RpcError {
        return addWire(d, c, points, true);
    }

    /**
     * wiringTool: Wiring Tool로 긋는다(그 도구를 들 때 원조 Project.setTool이 선택을 내려놓고 비운다). 아니면 Edit
     * Tool이 선 잇는 점에서 긋는 것이라 선택을 그대로 둔다(EditTool.mousePressed, N-08 D-146).
     */
    public static Result addWire(Doc d, Circuit c, List<Location> points, boolean wiringTool) throws RpcError {
        editable(d, c);
        if (points.size() < 2 || points.size() > 3) {
            throw RpcError.params("points must have 2 or 3 points");
        }
        Location start = points.get(0);
        Location cur = points.get(points.size() - 1);
        boolean straight = start.getX() == cur.getX() || start.getY() == cur.getY();
        boolean horizontalFirst = true;
        if (!straight) {
            if (points.size() != 3) {
                throw RpcError.params("an L-shaped wire needs 3 points");
            }
            Location m = points.get(1);
            if (m.equals(Location.create(cur.getX(), start.getY()))) {
                horizontalFirst = true;
            } else if (m.equals(Location.create(start.getX(), cur.getY()))) {
                horizontalFirst = false;
            } else {
                throw RpcError.params("the middle point must be the corner of the L");
            }
        } else if (points.size() == 3) {
            Location m = points.get(1);
            if (!Wire.create(start, cur).contains(m)) {
                throw RpcError.params("the middle point of a straight wire must lie on it");
            }
        }
        if (start.equals(cur)) {
            return Result.unchanged("empty");
        }
        d.show(c);
        if (wiringTool) {
            d.project().doAction(SelectionActions.dropAll(d.selection()));
        }
        List<Wire> ws = new ArrayList<>(2);
        if (straight) {
            // WiringTool.mouseDragged: 끝에서 시작해 선 위로 되돌아가면 그 선을 줄인다
            Wire shorten = null;
            if (!c.getWires(start).isEmpty()) {
                for (Wire w : c.getWires(start)) {
                    if (w.contains(cur)) {
                        shorten = w;
                        break;
                    }
                }
            }
            if (shorten == null) {
                for (Wire w : c.getWires(cur)) {
                    if (w.contains(start)) {
                        shorten = w;
                        break;
                    }
                }
            }
            Wire w = Wire.create(cur, start);
            w = repair(c, w, w.getEnd0());
            w = repair(c, w, w.getEnd1());
            if (shorten != null) {
                return shorten(d, c, shorten, start, cur);
            }
            if (w.getLength() > 0) {
                ws.add(w);
            }
        } else {
            Location m = horizontalFirst ? Location.create(cur.getX(), start.getY())
                    : Location.create(start.getX(), cur.getY());
            Wire w0 = repair(c, Wire.create(start, m), start);
            Wire w1 = repair(c, Wire.create(m, cur), cur);
            if (w0.getLength() > 0) {
                ws.add(w0);
            }
            if (w1.getLength() > 0) {
                ws.add(w1);
            }
        }
        if (ws.isEmpty()) {
            return Result.unchanged("empty");
        }
        CircuitMutation mutation = new CircuitMutation(c);
        mutation.addAll(ws);
        StringGetter desc = TOOLS.getter(ws.size() == 1 ? "addWireAction" : "addWiresAction");
        d.project().doAction(mutation.toAction(desc));
        return new Result(true, null, null);
    }

    /** WiringTool.checkForRepairs: 부품 가장자리에서 한 칸 모자란 선을 포트까지 잇는다. */
    static Wire repair(Circuit c, Wire w, Location end) {
        if (w.getLength() <= 10) {
            return w;
        }
        if (!c.getNonWires(end).isEmpty()) {
            return w;
        }
        int delta = end.equals(w.getEnd0()) ? 10 : -10;
        Location cand = w.isVertical() ? Location.create(end.getX(), end.getY() + delta)
                : Location.create(end.getX() + delta, end.getY());
        for (Component comp : c.getNonWires(cand)) {
            if (comp.getBounds().contains(end)) {
                WireRepair repair = (WireRepair) comp.getFeature(WireRepair.class);
                if (repair != null && repair.shouldRepairWire(new WireRepairData(w, cand))) {
                    return Wire.create(w.getOtherEnd(end), cand);
                }
            }
        }
        return w;
    }

    /** WiringTool.performShortening. */
    private static Result shorten(Doc d, Circuit c, Wire shorten, Location drag0, Location drag1) {
        Location e0;
        Location e1;
        if (shorten.endsAt(drag0)) {
            e0 = drag1;
            e1 = shorten.getOtherEnd(drag0);
        } else {
            e0 = drag0;
            e1 = shorten.getOtherEnd(drag1);
        }
        Wire result = e0.equals(e1) ? null : Wire.create(e0, e1);
        CircuitMutation xn = new CircuitMutation(c);
        StringGetter name;
        if (result == null) {
            xn.remove(shorten);
            name = TOOLS.getter("removeComponentAction", shorten.getFactory().getDisplayGetter());
        } else {
            xn.replace(shorten, result);
            name = TOOLS.getter("shortenWireAction");
        }
        d.project().doAction(xn.toAction(name));
        return new Result(true, result == null ? "removed" : "shortened", null);
    }

    // ---- edit.move: SelectTool.mouseReleased(MOVING) + SafeMove(v1 따라오는 선, D-055) ----

    /** 옮기기. connect면 원조처럼 연결을 유지하는 선을 찾고 SafeMove의 기준으로 남긴다(끄면 선 없이 옮긴다). */
    public static Result move(Doc d, Circuit c, List<Component> comps, int dx, int dy, boolean connect)
            throws RpcError {
        editable(d, c);
        if (dx == 0 && dy == 0) {
            return Result.unchanged("empty");
        }
        d.show(c);
        Selection sel = d.canvas().getSelection();
        clear(d.project(), sel);
        sel.addAll(comps);
        try {
            if (sel.hasConflictWhenMoved(dx, dy)) {
                throw RpcError.notEditable("exclusive", "the moved components would overlap another driver");
            }
            MoveResult result = null;
            if (connect) {
                MoveGesture g = new MoveGesture((gesture, x, y) -> { }, c, sel.getAnchoredComponents());
                result = g.forceRequest(dx, dy);
            }
            SafeMove.Outcome o = SafeMove.move(d.project(), sel, dx, dy, result);
            switch (o) {
            case MOVED:
                return new Result(true, "moved", null);
            case MOVED_WITHOUT_WIRES:
                return new Result(true, "movedWithoutWires", null);
            default:
                return Result.unchanged("refused");
            }
        } finally {
            clear(d.project(), sel);
        }
    }

    private static void clear(Project proj, Selection sel) {
        Action drop = SelectionActions.dropAll(sel); // 회로에 놓인 것만 고르므로 동작 없이 선택만 비운다
        if (drop != null) {
            proj.doAction(drop);
        }
    }

    // ---- edit.delete: EditTool Delete 키(SelectionActions.clear)의 몸체 ----

    /**
     * 지우기. 원조 {@code SelectionActions.Delete}와 같은 변경(고른 것을 {@link CircuitMutation#remove})을 한 동작으로
     * 한다. 선택에 기대지 않는 {@link CircuitMutation#toAction}이라 다시 실행해도 같은 것을 지운다.
     */
    public static Result delete(Doc d, Circuit c, List<Component> comps) throws RpcError {
        editable(d, c);
        if (comps.isEmpty()) {
            return Result.unchanged("empty");
        }
        d.show(c);
        CircuitMutation xn = new CircuitMutation(c);
        for (Component comp : comps) {
            xn.remove(comp);
        }
        d.project().doAction(xn.toAction(GUI.getter("deleteSelectionAction")));
        return new Result(true, null, null);
    }

    // ---- edit.setAttr: 속성 표(AttrTableComponentModel / AttrTableSelectionModel) ----

    /** 속성 바꾸기. value는 .circ에 저장되는 글자. 선은 건너뛴다(원조 선택 속성 표와 같다). */
    public static Result setAttr(Doc d, Circuit c, List<Component> comps, String attr, String value)
            throws RpcError {
        editable(d, c);
        List<Component> targets = new ArrayList<>();
        for (Component comp : comps) {
            if (!(comp instanceof Wire)) {
                targets.add(comp);
            }
        }
        if (targets.isEmpty()) {
            return Result.unchanged("empty");
        }
        SetAttributeAction act = new SetAttributeAction(c,
                GUI.getter(targets.size() == 1 ? "changeAttributeAction" : "selectionAttributeAction"));
        for (Component comp : targets) {
            AttributeSet as = comp.getAttributeSet();
            @SuppressWarnings("unchecked")
            Attribute<Object> a = (Attribute<Object>) as.getAttribute(attr);
            if (a == null) {
                throw RpcError.params("component " + d.ids().of(comp) + " has no attribute '" + attr + "'");
            }
            if (as.isReadOnly(a)) {
                throw RpcError.notEditable("readOnlyAttribute", "attribute '" + attr + "' is read-only");
            }
            act.set(comp, a, parse(a, value));
        }
        d.show(c);
        d.project().doAction(act);
        return new Result(true, null, null);
    }

    // ---- edit.undo / edit.redo ----

    /** 원조 되돌리기(Project.undoAction). */
    public static Result undo(Doc d) {
        return undo(d, null);
    }

    /**
     * Edit › Undo를 화면이 보던 회로 c에서(원조: 보던 회로의 창에서 Ctrl+Z). 먼저 c를 편집하는 회로로 둔다
     * ({@link Doc#show}: 다른 회로의 떠 있는 선택은 그 회로에 내려놓는다, 원조가 탭을 바꿀 때처럼). 그래서 되살리기
     * 재생이 저널에 없는 sim.watch·record.view와 상관없이 같은 지금 회로에서 되돌린다. 원조 {@code undoAction}은 그
     * 동작을 한 때의 회로 상태로 돌아가 되돌린다(ActionData). c가 null이면(의도 파일·옛 저널) 지금 회로 그대로.
     */
    public static Result undo(Doc d, Circuit c) {
        showIfOwn(d, c);
        if (d.project().getLastAction() == null) {
            return Result.unchanged("nothing");
        }
        d.project().undoAction();
        return new Result(true, null, null);
    }

    /** 포크의 다시 실행(RedoStack, 원조 doAction으로 다시 적용). */
    public static Result redo(Doc d) {
        return redo(d, null);
    }

    /** Edit › Redo를 화면이 보던 회로 c에서({@link #undo(Doc, Circuit)}와 같다). */
    public static Result redo(Doc d, Circuit c) {
        showIfOwn(d, c);
        RedoStack r = RedoStack.of(d.project());
        if (!r.canRedo()) {
            return Result.unchanged("nothing");
        }
        r.redo();
        return new Result(true, null, null);
    }

    // ---- 공통 ----

    /** c가 이 파일의 회로면 편집하는 회로로 둔다(라이브러리 회로는 편집하지 않으므로 그대로). */
    private static void showIfOwn(Doc d, Circuit c) {
        if (c != null && d.file().contains(c)) {
            d.show(c);
        }
    }

    static void editable(Doc d, Circuit c) throws RpcError {
        if (d.isReadOnly()) {
            throw RpcError.notEditable("readOnly", "the file is read-only");
        }
        if (!d.file().contains(c)) {
            throw RpcError.notEditable("cannotModify", "the circuit belongs to a library");
        }
    }

    @SuppressWarnings("unchecked")
    static void set(AttributeSet as, String name, String value) throws RpcError {
        Attribute<Object> a = (Attribute<Object>) as.getAttribute(name);
        if (a == null) {
            throw RpcError.params("unknown attribute '" + name + "'");
        }
        try {
            as.setValue(a, parse(a, value));
        } catch (UnsupportedOperationException e) {
            throw RpcError.notEditable("readOnlyAttribute", "attribute '" + name + "' is read-only");
        }
    }

    /**
     * 원조 속성 표의 읽기(AttributeSetTableModel.AttrRow.setValue: {@code Attribute.parse}). 읽지 못하면 -32602에
     * data {@code {reason:"badValue", attr, value}}(화면이 한국어 문장을 짓는다, N-10).
     */
    static Object parse(Attribute<Object> a, String value) throws RpcError {
        Object v;
        try {
            v = a.parse(value);
        } catch (RuntimeException e) {
            throw badValue(a, value, e.getMessage());
        }
        if (v == null) {
            throw badValue(a, value, null);
        }
        return v;
    }

    private static RpcError badValue(Attribute<Object> a, String value, String why) {
        com.google.gson.JsonObject data = new com.google.gson.JsonObject();
        data.addProperty("reason", "badValue");
        data.addProperty("attr", a.getName());
        data.addProperty("value", value);
        return new RpcError(RpcError.INVALID_PARAMS, "invalid value '" + value + "' for attribute '" + a.getName() + "'"
                + (why == null ? "" : ": " + why), data);
    }
}
