/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.edit;

import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.cburch.draw.actions.ModelAddAction;
import com.cburch.draw.actions.ModelChangeAttributeAction;
import com.cburch.draw.actions.ModelDeleteHandleAction;
import com.cburch.draw.actions.ModelEditTextAction;
import com.cburch.draw.actions.ModelInsertHandleAction;
import com.cburch.draw.actions.ModelMoveHandleAction;
import com.cburch.draw.actions.ModelRemoveAction;
import com.cburch.draw.actions.ModelTranslateAction;
import com.cburch.draw.canvas.Selection;
import com.cburch.draw.model.AttributeMapKey;
import com.cburch.draw.model.CanvasModel;
import com.cburch.draw.model.CanvasObject;
import com.cburch.draw.model.Handle;
import com.cburch.draw.model.HandleGesture;
import com.cburch.draw.shapes.Curve;
import com.cburch.draw.shapes.DrawAttr;
import com.cburch.draw.shapes.Oval;
import com.cburch.draw.shapes.Poly;
import com.cburch.draw.shapes.Rectangle;
import com.cburch.draw.shapes.RoundRectangle;
import com.cburch.draw.shapes.Text;
import com.cburch.draw.tools.DrawingAttributeSet;
import com.cburch.logisim.circuit.appear.AppearancePort;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.appear.AppearanceCanvas;
import com.cburch.logisim.gui.appear.AppearanceView;
import com.cburch.logisim.gui.main.EditHandler;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.menu.LogisimMenuItem;
import com.cburch.logisim.gui.appear.RevertAppearanceAction;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.edit.Intents.Result;
import kr.ac.hallym.hcs.engine.model.AppearanceEditJson;
import kr.ac.hallym.hcs.engine.rpc.Params;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * 서브회로 모양 편집(Project › Edit Circuit Appearance, N-11, D-153): 원조 모양 편집기의 동작을 의도로 한다.
 * 화면에 붙지 않은 원조 {@link AppearanceCanvas}(회로마다 하나)와 그 Edit 메뉴 처리기({@link AppearanceEditHandler})를
 * 두고, 원조 그리기 도구·고르기 도구가 마우스를 뗄 때 넘기는 동작(ModelAddAction, ModelTranslateAction,
 * ModelMoveHandleAction …)을 같은 {@code AppearanceCanvas.doAction}으로 보낸다. 그래서 포트·기준점은 늘 맨 위에
 * 남고(원조 getMaxIndex), 인스턴스가 있는 회로는 원조 {@code CanvasActionAdapter}의 거래 안에서 바뀐다. 되돌리기 한
 * 단계.
 *
 * <p>도형은 모양의 아래부터 번호(0부터, {@code CircuitAppearance.getObjectsFromBottom})로 가리킨다. 고른 것은 화면의
 * 몫이고, 의도마다 {@code shapes}로 준다. 결과의 {@code selected}는 원조 편집기가 그 동작 뒤에 고른 도형들이다.
 */
public final class AppearanceIntents {
    private AppearanceIntents() {
    }

    /**
     * 회로마다 화면 없는 원조 모양 편집 화면({@link AppearanceView}: Swing 앱의 Edit Circuit Appearance가 만드는 것과 같은
     * Canvas·Edit 메뉴 처리기, 창에 붙이지 않는다).
     */
    private static final class Session {
        final AppearanceView view;
        final AppearanceCanvas canvas;
        final EditHandler handler;

        Session(Doc d, Circuit c) {
            view = new AppearanceView();
            view.setCircuit(d.project(), d.project().getCircuitState(c));
            canvas = (AppearanceCanvas) view.getCanvas();
            handler = view.getEditHandler();
        }
    }

    private static final Map<Circuit, Session> SESSIONS = new WeakHashMap<>();

    private static Session session(Doc d, Circuit c) {
        Session s = SESSIONS.get(c);
        if (s == null) {
            s = new Session(d, c);
            SESSIONS.put(c, s);
        }
        return s;
    }

    /** 도형 번호 → 도형(아래부터). */
    static CanvasObject shape(Circuit c, int i) throws RpcError {
        List<CanvasObject> all = c.getAppearance().getObjectsFromBottom();
        if (i < 0 || i >= all.size()) {
            throw RpcError.notFound("shape", String.valueOf(i));
        }
        return all.get(i);
    }

    static List<CanvasObject> shapes(Circuit c, List<Integer> is) throws RpcError {
        List<CanvasObject> out = new ArrayList<>();
        for (int i : is) {
            CanvasObject o = shape(c, i);
            if (!out.contains(o)) {
                out.add(o);
            }
        }
        return out;
    }

    public static List<Integer> indices(Params p, String name) throws RpcError {
        List<Integer> out = new ArrayList<>();
        if (!p.has(name)) {
            return out;
        }
        JsonElement e = p.raw().get(name);
        if (!e.isJsonArray()) {
            throw RpcError.params("param '" + name + "' must be a list of shape numbers");
        }
        for (JsonElement x : e.getAsJsonArray()) {
            if (!x.isJsonPrimitive() || !x.getAsJsonPrimitive().isNumber()) {
                throw RpcError.params("param '" + name + "' must be a list of shape numbers");
            }
            out.add(x.getAsInt());
        }
        return out;
    }

    /**
     * edit.appearance {circuitId, op, …}. op:
     * <ul>
     * <li>{@code add} {shape:{kind, bounds|points|at, text?}, attrs?}: 그리기 도구가 놓는 도형(원조 도구처럼 도구 속성
     * {@link DrawingAttributeSet}을 입힌다; 원조 PolyTool은 입히지 않는다). result {@code index}.</li>
     * <li>{@code move} {shapes, dx, dy}: 고르기 도구 끌기(ModelTranslateAction).</li>
     * <li>{@code handle} {shape, at, dx, dy, shift?, ctrl?, alt?}: 손잡이 끌기(ModelMoveHandleAction).</li>
     * <li>{@code delete}·{@code cut}·{@code copy}·{@code paste}·{@code duplicate} {shapes}: Edit 메뉴(원조
     * AppearanceEditHandler, Copy도 원조처럼 되돌리기 한 단계).</li>
     * <li>{@code raise}·{@code lower}·{@code raiseTop}·{@code lowerBottom} {shapes}: Edit › Raise …</li>
     * <li>{@code addVertex}·{@code removeVertex} {shape, at}: Edit › Add/Remove Vertex.</li>
     * <li>{@code setAttr} {shapes, attr, value}: 속성 표(ModelChangeAttributeAction). 기준점의 {@code facing}도.</li>
     * <li>{@code text} {shape, text}: 글자 도구로 고치기(빈 글이면 지움, 원조 TextTool.commitText).</li>
     * <li>{@code revert}: Project › Revert To Default Appearance.</li>
     * </ul>
     */
    public static Result apply(Doc d, Circuit c, Params p) throws RpcError {
        Intents.editable(d, c);
        String op = p.str("op");
        d.show(c); // Swing: 모양 편집 화면은 그 회로를 지금 회로로 둔다
        Session s = session(d, c);
        CanvasModel model = c.getAppearance();
        Selection sel = s.canvas.getSelection();
        switch (op) {
        case "add":
            return add(d, c, s, p);
        case "move": {
            List<CanvasObject> objs = shapes(c, indices(p, "shapes"));
            int dx = p.integer("dx");
            int dy = p.integer("dy");
            if (objs.isEmpty() || dx == 0 && dy == 0) {
                return Result.unchanged("nothing");
            }
            return run(d, c, s, new ModelTranslateAction(model, objs, dx, dy), objs);
        }
        case "handle": {
            CanvasObject o = shape(c, p.integer("shape"));
            int[] at = p.point("at");
            Handle h = null;
            for (Handle x : o.getHandles(null)) {
                if (x.isAt(at[0], at[1]) && o.canMoveHandle(x)) {
                    h = x;
                }
            }
            if (h == null) {
                throw RpcError.params("no handle that moves at " + at[0] + "," + at[1]);
            }
            int dx = p.integer("dx");
            int dy = p.integer("dy");
            if (dx == 0 && dy == 0) {
                return Result.unchanged("nothing");
            }
            int mods = (p.optBool("shift", false) ? InputEvent.SHIFT_DOWN_MASK : 0)
                    | (p.optBool("ctrl", false) ? InputEvent.CTRL_DOWN_MASK : 0)
                    | (p.optBool("alt", false) ? InputEvent.ALT_DOWN_MASK : 0);
            ModelMoveHandleAction act = new ModelMoveHandleAction(model, new HandleGesture(h, dx, dy, mods));
            Result r = run(d, c, s, act, List.of(o));
            Handle result = act.getNewHandle();
            if (r.changed && result != null) {
                Handle del = result.getObject().canDeleteHandle(result.getLocation());
                if (del != null) {
                    r.with("handle", AppearanceEditJson.point(del.getLocation()));
                }
            }
            return r;
        }
        case "delete":
        case "cut":
        case "copy":
        case "duplicate":
        case "raise":
        case "lower":
        case "raiseTop":
        case "lowerBottom":
        case "paste": {
            List<CanvasObject> objs = shapes(c, indices(p, "shapes"));
            select(s, objs);
            Session ss = s;
            if (op.equals("paste") && !enabled(s).getOrDefault(LogisimMenuBar.PASTE, false)) {
                return Result.unchanged("emptyClipboard");
            }
            boolean changed = acted(d, () -> {
                switch (op) {
                case "delete": ss.handler.delete(); break;
                case "cut": ss.handler.cut(); break;
                case "copy": ss.handler.copy(); break;
                case "duplicate": ss.handler.duplicate(); break;
                case "raise": ss.handler.raise(); break;
                case "lower": ss.handler.lower(); break;
                case "raiseTop": ss.handler.raiseTop(); break;
                case "lowerBottom": ss.handler.lowerBottom(); break;
                default: ss.handler.paste(); break;
                }
            });
            Result r = changed ? new Result(true, null, null) : Result.unchanged("nothing");
            // 원조는 지울 수 없는 포트를 고른 채 둔다(SelectionAction): 고른 포트를 다시 더한다
            List<CanvasObject> now = new ArrayList<>(sel.getSelected());
            if (!op.equals("paste")) {
                for (CanvasObject o : objs) {
                    if (o instanceof AppearancePort && !now.contains(o)) {
                        now.add(o);
                    }
                }
            }
            return r.with("selected", selectedJson(c, now));
        }
        case "addVertex":
        case "removeVertex": {
            CanvasObject o = shape(c, p.integer("shape"));
            int[] at = p.point("at");
            Location loc = Location.create(at[0], at[1]);
            Handle h = op.equals("addVertex") ? o.canInsertHandle(loc) : o.canDeleteHandle(loc);
            if (h == null) {
                return Result.unchanged("noVertex");
            }
            return run(d, c, s, op.equals("addVertex") ? new ModelInsertHandleAction(model, h)
                    : new ModelDeleteHandleAction(model, h), List.of(o));
        }
        case "setAttr":
            return setAttr(d, c, s, shapes(c, indices(p, "shapes")), p.str("attr"), p.str("value"));
        case "text": {
            CanvasObject o = shape(c, p.integer("shape"));
            if (!(o instanceof Text)) {
                throw RpcError.params("shape " + p.integer("shape") + " is not a text");
            }
            String now = p.str("text");
            Text t = (Text) o;
            if (now.equals("")) {
                return run(d, c, s, new ModelRemoveAction(model, t), List.of());
            }
            if (now.equals(t.getText())) {
                return Result.unchanged("same");
            }
            return run(d, c, s, new ModelEditTextAction(model, t, now), List.of(o));
        }
        case "revert": {
            if (c.getAppearance().isDefaultAppearance()) {
                return Result.unchanged("same");
            }
            // 원조 Revert는 인스턴스가 있으면 거래 밖에서 포트를 바꿔 멈춘다(원조 결함, v1 AutoAppearance와 같은 까닭):
            // 원조 동작을 이 회로를 쓰는 회로들을 잠근 거래 안에서 부른다
            d.project().doAction(locked(c, new RevertAppearanceAction(c)));
            return new Result(true, null, null).with("selected", new JsonArray());
        }
        default:
            throw RpcError.params("op must be add, move, handle, delete, cut, copy, paste, duplicate, raise, lower,"
                    + " raiseTop, lowerBottom, addVertex, removeVertex, setAttr, text or revert");
        }
    }

    /**
     * 캔버스의 고른 것을 objs로. 포트는 원조 모양 편집 화면의 선택에 넣지 않는다: 포트를 고르면 원조 LayoutPopupManager가
     * 화면에 회로 축소 그림 창을 띄우려다(화면에 붙지 않아) 멈춘다. 포트는 어느 Edit 메뉴 동작에서도 옮기거나 지우거나
     * 복사하지 않으므로(canRemove 거짓) 동작은 같다.
     */
    private static void select(Session s, List<CanvasObject> objs) {
        Selection sel = s.canvas.getSelection();
        sel.clearSelected();
        List<CanvasObject> keep = new ArrayList<>();
        for (CanvasObject o : objs) {
            if (!(o instanceof AppearancePort)) {
                keep.add(o);
            }
        }
        sel.setSelected(keep, true);
    }

    /** 원조 동작을 c를 쓰는 회로들을 잠근 거래 안에서(원조 CanvasActionAdapter, v1 AutoAppearance.inUsers와 같은 방법). */
    static com.cburch.logisim.proj.Action locked(Circuit c, com.cburch.logisim.proj.Action inner) {
        return new com.cburch.logisim.proj.Action() {
            @Override
            public String getName() {
                return inner.getName();
            }

            @Override
            public boolean isModification() {
                return inner.isModification();
            }

            @Override
            public void doIt(com.cburch.logisim.proj.Project proj) {
                inUsers(c, () -> inner.doIt(proj));
            }

            @Override
            public void undo(com.cburch.logisim.proj.Project proj) {
                inUsers(c, () -> inner.undo(proj));
            }
        };
    }

    private static void inUsers(Circuit c, Runnable r) {
        new com.cburch.logisim.circuit.CircuitTransaction() {
            @Override
            protected Map<Circuit, Integer> getAccessedCircuits() {
                Map<Circuit, Integer> m = new HashMap<>();
                for (Circuit sup : c.getCircuitsUsingThis()) {
                    m.put(sup, READ_WRITE);
                }
                return m;
            }

            @Override
            protected void run(com.cburch.logisim.circuit.CircuitMutator mutator) {
                r.run();
            }
        }.execute();
    }

    /** r 동안 원조 Project.doAction이 불렸는가(원조 처리기·AppearanceCanvas가 동작을 넘겼는지; 되돌리기 한 단계). */
    private static boolean acted(Doc d, Runnable r) {
        int[] n = {0};
        com.cburch.logisim.proj.ProjectListener l = e -> {
            if (e.getAction() == com.cburch.logisim.proj.ProjectEvent.ACTION_START) {
                n[0]++;
            }
        };
        d.project().addProjectListener(l);
        try {
            r.run();
        } finally {
            d.project().removeProjectListener(l);
        }
        return n[0] > 0;
    }

    /** 원조 편집기처럼 AppearanceCanvas.doAction으로 넘기고, 그 동작의 도형을 고른 것으로 돌려준다. */
    private static Result run(Doc d, Circuit c, Session s, com.cburch.draw.undo.Action act,
            Collection<CanvasObject> selected) {
        boolean changed = acted(d, () -> s.canvas.doAction(act));
        Result r = changed ? new Result(true, null, null) : Result.unchanged("nothing");
        List<CanvasObject> still = new ArrayList<>();
        for (CanvasObject o : selected) {
            if (c.getAppearance().getObjectsFromBottom().contains(o)) {
                still.add(o);
            }
        }
        return r.with("selected", selectedJson(c, still));
    }

    static JsonArray selectedJson(Circuit c, Collection<CanvasObject> objs) {
        List<CanvasObject> all = c.getAppearance().getObjectsFromBottom();
        List<Integer> is = new ArrayList<>();
        for (CanvasObject o : objs) {
            int i = all.indexOf(o);
            if (i >= 0) {
                is.add(i);
            }
        }
        is.sort(null);
        JsonArray a = new JsonArray();
        is.forEach(a::add);
        return a;
    }

    // ---- 그리기 도구 ----

    private static Result add(Doc d, Circuit c, Session s, Params p) throws RpcError {
        if (!p.has("shape") || !p.raw().get("shape").isJsonObject()) {
            throw RpcError.params("param 'shape' must be an object");
        }
        Params sh = new Params(p.raw().getAsJsonObject("shape"));
        DrawingAttributeSet attrs = new DrawingAttributeSet();
        for (Map.Entry<String, String> e : p.optStringMap("attrs").entrySet()) {
            @SuppressWarnings("unchecked")
            Attribute<Object> a = (Attribute<Object>) attrs.getAttribute(e.getKey());
            if (a == null) {
                throw RpcError.params("drawing tools have no attribute '" + e.getKey() + "'");
            }
            attrs.setValue(a, parse(a, e.getValue()));
        }
        String kind = sh.str("kind");
        CanvasObject made;
        switch (kind) {
        case "rect":
        case "roundrect":
        case "oval": {
            int[] b = sh.ints("bounds");
            if (b.length != 4 || b[2] <= 0 || b[3] <= 0) {
                throw RpcError.params("bounds must be [x, y, w, h] with w and h above 0");
            }
            // 원조 RectangularTool.createShape: 도구 속성을 입힌 새 도형
            made = kind.equals("rect") ? attrs.applyTo(new Rectangle(b[0], b[1], b[2], b[3]))
                    : kind.equals("roundrect") ? attrs.applyTo(new RoundRectangle(b[0], b[1], b[2], b[3]))
                    : attrs.applyTo(new Oval(b[0], b[1], b[2], b[3]));
            break;
        }
        case "line": {
            List<Location> pts = locations(sh, 2, 2);
            made = attrs.applyTo(new Poly(false, pts)); // 원조 LineTool: 두 점의 열린 다각선, 테두리만
            made.setValue(DrawAttr.PAINT_TYPE, DrawAttr.PAINT_STROKE);
            break;
        }
        case "polyline":
        case "polygon": {
            List<Location> pts = locations(sh, 2, Integer.MAX_VALUE);
            // 원조 PolyTool.commit: 같은 점이 이어지면 하나로, 도구 속성은 입히지 않는다(원조 그대로)
            for (int i = pts.size() - 2; i >= 0; i--) {
                if (pts.get(i).equals(pts.get(i + 1))) {
                    pts.remove(i);
                }
            }
            if (pts.size() < 2) {
                return Result.unchanged("nothing");
            }
            made = new Poly(kind.equals("polygon"), pts);
            break;
        }
        case "curve": {
            List<Location> pts = locations(sh, 3, 3);
            made = attrs.applyTo(new Curve(pts.get(0), pts.get(1), pts.get(2))); // 원조 CurveTool: 끝, 끝, 조절점
            break;
        }
        case "text": {
            int[] at = sh.point("at");
            String text = sh.str("text");
            if (text.isEmpty()) {
                return Result.unchanged("empty"); // 원조 TextTool: 빈 새 글은 놓지 않는다
            }
            Text t = attrs.applyTo(new Text(at[0], at[1], ""));
            t.setText(text);
            made = t;
            break;
        }
        default:
            throw RpcError.params("shape kind must be rect, roundrect, oval, line, polyline, polygon, curve or text");
        }
        boolean added = acted(d, () -> s.canvas.doAction(new ModelAddAction(c.getAppearance(), made)));
        if (!added) {
            return Result.unchanged("nothing");
        }
        int index = c.getAppearance().getObjectsFromBottom().indexOf(made);
        Result r = new Result(true, null, null);
        r.with("index", new com.google.gson.JsonPrimitive(index));
        JsonArray selected = new JsonArray();
        if (index >= 0) {
            selected.add(index); // 원조 AppearanceCanvas.toolGestureComplete: 놓은 도형을 고른다
        }
        return r.with("selected", selected);
    }

    private static List<Location> locations(Params sh, int min, int max) throws RpcError {
        List<int[]> raw = sh.points("points");
        if (raw.size() < min || raw.size() > max) {
            throw RpcError.params("points must have " + (min == max ? String.valueOf(min) : min + " or more")
                    + " points");
        }
        List<Location> out = new ArrayList<>();
        for (int[] xy : raw) {
            out.add(Location.create(xy[0], xy[1]));
        }
        return out;
    }

    // ---- 속성 ----

    /** 원조 AttrTableSelectionModel.setValueRequested: 고른 도형 가운데 그 속성을 가진 것마다 옛 값·새 값. */
    private static Result setAttr(Doc d, Circuit c, Session s, List<CanvasObject> objs, String name, String value)
            throws RpcError {
        HashMap<AttributeMapKey, Object> oldVals = new HashMap<>();
        HashMap<AttributeMapKey, Object> newVals = new HashMap<>();
        Attribute<Object> attr = null;
        Object v = null;
        for (CanvasObject o : objs) {
            for (Attribute<?> a : o.getAttributeSet().getAttributes()) {
                if (a.getName().equals(name)) {
                    @SuppressWarnings("unchecked")
                    Attribute<Object> aa = (Attribute<Object>) a;
                    attr = aa;
                    if (v == null) {
                        v = parse(aa, value);
                    }
                    AttributeMapKey key = new AttributeMapKey(aa, o);
                    oldVals.put(key, o.getValue(aa));
                    newVals.put(key, v);
                }
            }
        }
        if (attr == null) {
            throw RpcError.params("the chosen shapes have no attribute '" + name + "'");
        }
        boolean same = true;
        for (Map.Entry<AttributeMapKey, Object> e : oldVals.entrySet()) {
            same &= java.util.Objects.equals(e.getValue(), newVals.get(e.getKey()));
        }
        if (same) {
            return Result.unchanged("same");
        }
        return run(d, c, s, new ModelChangeAttributeAction(c.getAppearance(), oldVals, newVals), objs);
    }

    /** 속성 글자 → 값. 맞춤은 left·center·right로도 받는다(화면의 이름). */
    static Object parse(Attribute<Object> a, String value) throws RpcError {
        if ((Object) a == DrawAttr.ALIGNMENT) {
            switch (value) {
            case "left": return DrawAttr.ALIGN_LEFT;
            case "center": return DrawAttr.ALIGN_CENTER;
            case "right": return DrawAttr.ALIGN_RIGHT;
            default:
                break;
            }
        }
        Object v = Intents.parse(a, value);
        if (v instanceof AttributeOption || v != null) {
            return v;
        }
        throw RpcError.params("invalid value '" + value + "' for attribute '" + a.getName() + "'");
    }

    // ---- 물음 ----

    /** 원조 Edit 메뉴 처리기의 켜짐(computeEnabled가 알리는 것). */
    private static Map<LogisimMenuItem, Boolean> enabled(Session s) {
        Map<LogisimMenuItem, Boolean> out = new HashMap<>();
        s.handler.setListener((h, item, value) -> out.put(item, value));
        try {
            s.handler.computeEnabled();
        } finally {
            s.handler.setListener(null);
        }
        return out;
    }

    /**
     * model.appearanceMenu: 원조 모양 편집기의 Edit 메뉴에서 켜지는 항목(원조 AppearanceEditHandler.computeEnabled):
     * 고른 도형(shapes)과 고른 손잡이(vertex: 도형과 자리)로 묻는다. 모델을 바꾸지 않는다.
     */
    public static JsonObject menu(Doc d, Circuit c, List<Integer> shapes, Integer vertexShape, int[] vertexAt)
            throws RpcError {
        Session s = session(d, c);
        Selection sel = s.canvas.getSelection();
        List<CanvasObject> objs = shapes(c, shapes);
        select(s, objs);
        sel.setHandleSelected(null);
        if (vertexShape != null && vertexAt != null) {
            CanvasObject o = shape(c, vertexShape);
            Location loc = Location.create(vertexAt[0], vertexAt[1]);
            Handle h = o.canDeleteHandle(loc);
            sel.setHandleSelected(h != null ? h : o.canInsertHandle(loc));
        }
        Map<LogisimMenuItem, Boolean> on = enabled(s);
        sel.setHandleSelected(null);
        JsonObject o = new JsonObject();
        o.addProperty("cut", on.getOrDefault(LogisimMenuBar.CUT, false));
        // 포트만 고른 것(캔버스 선택에 넣지 않은 것)도 원조에서는 고른 것이 있어 Copy·Duplicate가 켜진다
        boolean portsOnly = !objs.isEmpty() && sel.getSelected().isEmpty();
        boolean canChange = d.file().contains(c);
        o.addProperty("copy", on.getOrDefault(LogisimMenuBar.COPY, false) || portsOnly);
        o.addProperty("paste", on.getOrDefault(LogisimMenuBar.PASTE, false));
        o.addProperty("delete", on.getOrDefault(LogisimMenuBar.DELETE, false));
        o.addProperty("duplicate", on.getOrDefault(LogisimMenuBar.DUPLICATE, false) || portsOnly && canChange);
        o.addProperty("raise", on.getOrDefault(LogisimMenuBar.RAISE, false));
        o.addProperty("lower", on.getOrDefault(LogisimMenuBar.LOWER, false));
        o.addProperty("raiseTop", on.getOrDefault(LogisimMenuBar.RAISE_TOP, false));
        o.addProperty("lowerBottom", on.getOrDefault(LogisimMenuBar.LOWER_BOTTOM, false));
        o.addProperty("addVertex", on.getOrDefault(LogisimMenuBar.ADD_CONTROL, false));
        o.addProperty("removeVertex", on.getOrDefault(LogisimMenuBar.REMOVE_CONTROL, false));
        return o;
    }

    /**
     * model.appearanceHit: 원조 고르기 도구가 누른 자리에서 묻는 것들(draw SelectTool.mousePressed·mouseReleased).
     * handle = 고른 도형(selected)의 손잡이 가운데 그 자리(배율에 따른 손잡이 크기 안)의 것, top = 그 자리에 그려진
     * 맨 위 도형(채움 없이), topFilled = 채운 것으로 볼 때의 맨 위 도형, vertex = 그 도형에서 지울 수 있는 꼭짓점
     * 또는 더할 수 있는 꼭짓점(Add/Remove Vertex가 쓰는 손잡이), inRect = 사각형 안의 도형들.
     */
    public static JsonObject hit(Doc d, Circuit c, int[] at, List<Integer> selected, double zoom, int[] rect)
            throws RpcError {
        JsonObject o = new JsonObject();
        List<CanvasObject> all = c.getAppearance().getObjectsFromBottom();
        if (at != null) {
            int half = (int) Math.ceil(8 / Math.sqrt(zoom <= 0 ? 1 : zoom)) / 2;
            JsonObject handle = null;
            for (int i : selected) {
                CanvasObject s = shape(c, i);
                for (Handle h : s.getHandles(null)) {
                    int dx = h.getX() - at[0];
                    int dy = h.getY() - at[1];
                    if (dx >= -half && dx <= half && dy >= -half && dy <= half) {
                        if (s.canMoveHandle(h)) {
                            handle = new JsonObject();
                            handle.addProperty("shape", i);
                            handle.add("at", AppearanceEditJson.point(h.getLocation()));
                            break;
                        } else if (!o.has("clicked")) {
                            o.addProperty("clicked", i);
                        }
                    }
                }
                if (handle != null) {
                    break;
                }
            }
            if (handle != null) {
                o.add("handle", handle);
            }
            Location loc = Location.create(at[0], at[1]);
            CanvasObject top = null;
            CanvasObject filled = null;
            for (CanvasObject x : c.getAppearance().getObjectsFromTop()) {
                if (top == null && x.contains(loc, false)) {
                    top = x;
                }
                if (filled == null && x.contains(loc, true)) {
                    filled = x;
                }
            }
            o.addProperty("top", top == null ? null : all.indexOf(top));
            o.addProperty("topFilled", filled == null ? null : all.indexOf(filled));
            if (top != null) {
                Handle v = top.canDeleteHandle(loc);
                if (v != null) {
                    o.add("removable", AppearanceEditJson.point(v.getLocation()));
                } else {
                    v = top.canInsertHandle(loc);
                    if (v != null) {
                        o.add("insertable", AppearanceEditJson.point(v.getLocation()));
                    }
                }
            }
        }
        if (rect != null) {
            Bounds b = Bounds.create(rect[0], rect[1], 0, 0).add(rect[2], rect[3]);
            JsonArray in = new JsonArray();
            for (CanvasObject x : c.getAppearance().getObjectsIn(b)) {
                in.add(all.indexOf(x));
            }
            o.add("inRect", in);
        }
        return o;
    }

    /** model.appearanceHandles: 손잡이를 끄는 동안의 모습(원조 getHandles(gesture): 끈 뒤의 손잡이 자리들). */
    public static JsonObject preview(Doc d, Circuit c, int index, int[] at, int dx, int dy, boolean shift, boolean ctrl,
            boolean alt) throws RpcError {
        CanvasObject o = shape(c, index);
        Handle h = null;
        for (Handle x : o.getHandles(null)) {
            if (x.isAt(at[0], at[1]) && o.canMoveHandle(x)) {
                h = x;
            }
        }
        if (h == null) {
            throw RpcError.params("no handle that moves at " + at[0] + "," + at[1]);
        }
        int mods = (shift ? InputEvent.SHIFT_DOWN_MASK : 0) | (ctrl ? InputEvent.CTRL_DOWN_MASK : 0)
                | (alt ? InputEvent.ALT_DOWN_MASK : 0);
        JsonArray pts = new JsonArray();
        for (Handle x : o.getHandles(new HandleGesture(h, dx, dy, mods))) {
            pts.add(AppearanceEditJson.point(x.getLocation()));
        }
        JsonObject out = new JsonObject();
        out.add("handles", pts);
        return out;
    }
}
