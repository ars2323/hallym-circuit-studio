/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.edit;

import java.awt.Graphics;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.gui.main.ToolAttributeAction;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.base.Text;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.SetAttributeAction;
import com.cburch.logisim.tools.TextEditable;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.tools.key.KeyConfigurationEvent;
import com.cburch.logisim.tools.key.KeyConfigurationResult;
import com.cburch.logisim.tools.key.KeyConfigurator;
import com.cburch.logisim.tools.move.MoveGesture;
import com.cburch.logisim.tools.move.MoveResult;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.StringGetter;

import kr.ac.hallym.hcs.app.keys.Shortcuts;
import kr.ac.hallym.hcs.app.libs.MipsShadow;
import kr.ac.hallym.hcs.app.wiring.SafeDuplicate;
import kr.ac.hallym.hcs.app.wiring.SafeMove;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.edit.Intents.Result;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * 고른 것에 하는 편집(N-08, D-146). 원조 창의 Canvas 선택({@link Doc#selection()})을 그대로 들고, Swing 도구와 Edit
 * 메뉴가 하는 일을 GUI 없이 같은 순서로 한다: 고르기(SelectTool 누름·사각형, Shift 뒤집기, Only Components/Wires),
 * 옮기기(SelectTool 뗌 + v1 SafeMove), Edit 메뉴(LayoutEditHandler의 Delete·Copy·Cut·Paste·Duplicate), 선택 속성 표
 * (AttrTableSelectionModel), 도구 속성 표(AttrTableToolModel), 숫자·Alt+숫자 키(SelectTool·AddTool의 KeyConfigurator),
 * 글자 도구(TextTool), v1 돌리기(R). 모두 {@link Project#doAction}이라 원조 되돌리기 기록이 그대로 붙는다: 붙여넣은 뒤
 * 옮기기·내려놓기가 붙여넣기 단계에 합쳐지고(원조 {@code shouldAppendTo}), 고른 것이 없을 때의 Delete도 빈 단계를
 * 남긴다.
 *
 * <p>{@code ids}를 주면 원조 선택 도구처럼 떠 있는 것을 내려놓고(dropAll) 그것들을 고른 뒤 하고(편집 동등성 재생기의
 * {@code selectRefs}와 같다), 빼면 지금 고른 것에 한다.
 */
public final class SelectionIntents {
    private static final LocaleManager TOOLS = new LocaleManager("resources/logisim", "tools");
    /** 경계를 잴 그림판(원조는 캔버스의 Graphics를 넘긴다). */
    static final Graphics G = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).createGraphics();

    private SelectionIntents() {
    }

    /** 이 편집이 되돌리기 기록에 단계를 남겼다(모델이 바뀌었거나 복사처럼 기록만). */
    private static final class Mark {
        private final Project proj;
        private final Action before;

        Mark(Project proj) {
            this.proj = proj;
            this.before = proj.getLastAction();
        }

        boolean changed() {
            return proj.getLastAction() != before;
        }

        Result result(String outcome) {
            return new Result(changed(), outcome, null);
        }
    }

    /** 다른 것을 누를 때처럼 떠 있는 것을 내려놓고 comps를 하나씩 고른다. */
    static void selectOnly(Doc d, Collection<Component> comps) {
        Selection sel = d.selection();
        d.project().doAction(SelectionActions.dropAll(sel));
        for (Component c : comps) {
            sel.add(c);
        }
    }

    // ---- edit.select ----

    /**
     * Edit 도구로 한 점을 누름(원조 {@code SelectTool.mousePressed} 그대로): 고른 것 안이면 Shift가 없을 때 그대로 두고
     * 옮기기로(outcome "moving"), Shift면 그것들을 뺀다. 고르지 않은 부품·선 위면(Shift가 없고 고른 것 밖이면 먼저
     * 비우고) 그 점을 포함한 것을 모두 더하고 옮기기로. 빈 곳이면 Shift가 없을 때 비우고 사각형 고르기로("rect").
     * 화면은 이 답으로 끌기가 옮기기인지 사각형인지 안다(누른 점의 판정이 원조 {@code contains}와 같다).
     */
    public static Result press(Doc d, Circuit c, Location start, boolean shift) throws RpcError {
        d.show(c);
        Project proj = d.project();
        Selection sel = d.selection();
        Mark m = new Mark(proj);
        Collection<Component> inSel = sel.getComponentsContaining(start, G);
        if (!inSel.isEmpty()) {
            if (!shift) {
                return m.result("moving");
            }
            Action act = SelectionActions.drop(sel, inSel);
            if (act != null) {
                proj.doAction(act);
            }
        }
        Collection<Component> clicked = c.getAllContaining(start, G);
        if (!clicked.isEmpty()) {
            if (!shift && sel.getComponentsContaining(start).isEmpty()) {
                Action act = SelectionActions.dropAll(sel);
                if (act != null) {
                    proj.doAction(act);
                }
            }
            for (Component comp : clicked) {
                if (!inSel.contains(comp)) {
                    sel.add(comp);
                }
            }
            return m.result("moving");
        }
        if (!shift) {
            Action act = SelectionActions.dropAll(sel);
            if (act != null) {
                proj.doAction(act);
            }
        }
        return m.result("rect");
    }

    /**
     * 고르기. ids(+add: 더하기, +toggle: Shift 누름처럼 뒤집기), rect(빈 곳에서 끈 사각형: 안에 다 든 것을 고르고
     * add면 뒤집기), filter("components"·"wires": 여러 개를 고른 우클릭 Only Components / Only Wires), all(Ctrl+A).
     * 아무것도 없으면 비우기.
     */
    public static Result select(Doc d, Circuit c, List<Component> ids, int[] rect, boolean add, boolean toggle,
            String filter, boolean all) throws RpcError {
        if (filter != null && !filter.equals("components") && !filter.equals("wires")) {
            throw RpcError.params("filter must be components or wires");
        }
        if (rect != null && rect.length != 4) {
            throw RpcError.params("rect must be [x0, y0, x1, y1]");
        }
        d.show(c);
        Project proj = d.project();
        Selection sel = d.selection();
        Mark m = new Mark(proj);
        if (all) {
            // Edit › Select All(LayoutEditHandler.selectAll): 떠 있는 것은 그대로 두고 회로의 모든 선·부품을 더한다
            sel.addAll(c.getWires());
            sel.addAll(c.getNonWires());
        }
        if (ids != null) {
            if (toggle) {
                // SelectTool.mousePressed(Shift): 고른 것은 빼고(떠 있으면 내려놓고), 아니면 더한다
                for (Component x : ids) {
                    if (sel.contains(x)) {
                        proj.doAction(SelectionActions.drop(sel, Collections.singletonList(x)));
                    } else {
                        sel.add(x);
                    }
                }
            } else {
                if (!add) {
                    proj.doAction(SelectionActions.dropAll(sel));
                }
                for (Component x : ids) {
                    sel.add(x);
                }
            }
        } else if (rect == null && filter == null && !all && !add) {
            proj.doAction(SelectionActions.dropAll(sel)); // 빈 곳 누름: 비우기
        }
        if (rect != null) {
            // SelectTool: 빈 곳 누름(Shift가 아니면 비움) → 끌기 → 뗌(RECT_SELECT)
            if (!add) {
                proj.doAction(SelectionActions.dropAll(sel));
            }
            Bounds bds = Bounds.create(Location.create(rect[0], rect[1])).add(rect[2], rect[3]);
            Collection<Component> inSel = sel.getComponentsWithin(bds, G);
            for (Component comp : c.getAllWithin(bds, G)) {
                if (!inSel.contains(comp)) {
                    sel.add(comp);
                }
            }
            proj.doAction(SelectionActions.drop(sel, inSel));
        }
        if (filter != null) {
            // v1 ArrangeActions.filter: 거른 것만 남긴다(내려놓기 한 단계)
            boolean wires = filter.equals("wires");
            List<Component> keep = new ArrayList<>();
            for (Component x : sel.getComponents()) {
                if ((x instanceof Wire) == wires) {
                    keep.add(x);
                }
            }
            proj.doAction(SelectionActions.dropAll(sel));
            sel.addAll(keep);
        }
        return m.result(null);
    }

    // ---- edit.move ----

    /**
     * 고른 것을 (dx, dy)만큼(SelectTool 뗌). 원조 {@code computeDxDy}처럼 선택 경계가 0 밑으로 가지 않게 자르고 격자에
     * 붙는 부품이 있으면 10에 맞춘다. connect면 원조 MoveGesture 뒤 v1 SafeMove(D-055)의 기준으로 남긴다. 옮긴 것은
     * 고른 채로 남는다(원조와 같다).
     */
    public static Result move(Doc d, Circuit c, List<Component> ids, int dx, int dy, boolean connect)
            throws RpcError {
        Intents.editable(d, c);
        d.show(c);
        Project proj = d.project();
        Selection sel = d.selection();
        if (ids != null) {
            selectOnly(d, ids);
        }
        if (sel.isEmpty()) {
            return Result.unchanged("empty");
        }
        Bounds bds = sel.getBounds(G);
        if (bds != Bounds.EMPTY_BOUNDS) {
            dx = Math.max(dx, -bds.getX());
            dy = Math.max(dy, -bds.getY());
        }
        if (sel.shouldSnap()) {
            dx = Canvas.snapXToGrid(dx);
            dy = Canvas.snapYToGrid(dy);
        }
        if (dx == 0 && dy == 0) {
            return Result.unchanged("empty");
        }
        if (sel.hasConflictWhenMoved(dx, dy)) {
            throw RpcError.notEditable("exclusive", "the moved components would overlap another driver");
        }
        MoveResult result = null;
        if (connect) {
            MoveGesture g = new MoveGesture((gesture, x, y) -> { }, c, sel.getAnchoredComponents());
            result = g.forceRequest(dx, dy);
        }
        Mark m = new Mark(proj);
        SafeMove.Outcome o = SafeMove.move(proj, sel, dx, dy, result);
        switch (o) {
        case MOVED:
            return m.result("moved");
        case MOVED_WITHOUT_WIRES:
            return m.result("movedWithoutWires");
        default:
            return m.result("refused");
        }
    }

    /**
     * model.movePreview: 끄는 동안 원조가 그리는 연결 유지 선(SelectTool.handleMoveDrag의 MoveGesture): 더할 선, 뺄 선,
     * 잇지 못한 점. dx·dy는 move와 같이 자르고 맞춘 값을 돌려준다. 모델은 바꾸지 않는다.
     */
    public static Map<String, Object> movePreview(Doc d, Circuit c, int dx, int dy, boolean connect) {
        Selection sel = d.selection();
        Map<String, Object> out = new LinkedHashMap<>();
        List<int[][]> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<int[]> unconnected = new ArrayList<>();
        if (!sel.isEmpty() && d.project().getCurrentCircuit() == c) {
            Bounds bds = sel.getBounds(G);
            if (bds != Bounds.EMPTY_BOUNDS) {
                dx = Math.max(dx, -bds.getX());
                dy = Math.max(dy, -bds.getY());
            }
            if (sel.shouldSnap()) {
                dx = Canvas.snapXToGrid(dx);
                dy = Canvas.snapYToGrid(dy);
            }
            if (connect && (dx != 0 || dy != 0) && !sel.getAnchoredComponents().isEmpty()) {
                MoveResult r = new MoveGesture((gesture, x, y) -> { }, c, sel.getAnchoredComponents())
                        .forceRequest(dx, dy);
                for (Wire w : r.getWiresToAdd()) {
                    added.add(new int[][] {{w.getEnd0().getX(), w.getEnd0().getY()},
                        {w.getEnd1().getX(), w.getEnd1().getY()}});
                }
                for (Wire w : r.getWiresToRemove()) {
                    String id = d.ids().peek(w);
                    if (id != null) {
                        removed.add(id);
                    }
                }
                for (Location l : r.getUnconnectedLocations()) {
                    unconnected.add(new int[] {l.getX(), l.getY()});
                }
            }
        }
        out.put("dx", dx);
        out.put("dy", dy);
        out.put("added", added);
        out.put("removed", removed);
        out.put("unconnected", unconnected);
        return out;
    }

    // ---- Edit 메뉴(LayoutEditHandler) ----

    /** Edit › Delete. 고른 것이 없어도 원조처럼 빈 단계 하나를 남긴다(outcome "empty"). */
    public static Result delete(Doc d, Circuit c, List<Component> ids) throws RpcError {
        Intents.editable(d, c);
        d.show(c);
        if (ids != null) {
            selectOnly(d, ids);
        }
        Selection sel = d.selection();
        boolean empty = sel.isEmpty();
        Mark m = new Mark(d.project());
        d.project().doAction(SelectionActions.clear(sel));
        return m.result(empty ? "empty" : null);
    }

    /** Edit › Copy: 엔진 프로세스의 원조 클립보드에(열린 파일끼리 붙여넣기 된다). 고른 것이 없으면 하지 않는다. */
    public static Result copy(Doc d, Circuit c, List<Component> ids) throws RpcError {
        d.show(c);
        if (ids != null) {
            selectOnly(d, ids);
        }
        Selection sel = d.selection();
        if (sel.isEmpty()) {
            return Result.unchanged("empty");
        }
        Mark m = new Mark(d.project());
        d.project().doAction(SelectionActions.copy(sel));
        return m.result(null);
    }

    /** Edit › Cut. */
    public static Result cut(Doc d, Circuit c, List<Component> ids) throws RpcError {
        Intents.editable(d, c);
        d.show(c);
        if (ids != null) {
            selectOnly(d, ids);
        }
        Selection sel = d.selection();
        if (sel.isEmpty()) {
            return Result.unchanged("empty");
        }
        Mark m = new Mark(d.project());
        d.project().doAction(SelectionActions.cut(sel));
        return m.result(null);
    }

    /**
     * Edit › Paste: 사본을 떠 있는 선택으로(원조 자리: 복사한 자리에서 겹치지 않는 첫 곳). 이 파일에 없는 라이브러리
     * 부품이라 원조가 창으로 묻는 경우는 오류 3 {@code needsDialog}.
     */
    public static Result paste(Doc d, Circuit c) throws RpcError {
        Intents.editable(d, c);
        d.show(c);
        Mark m = new Mark(d.project());
        Action a;
        try {
            a = SelectionActions.pasteMaybe(d.project(), d.selection());
        } catch (NullPointerException e) {
            // 원조 클립보드가 비었다(아직 복사한 적 없음): 원조 Edit › Paste 항목이 꺼져 있는 때다
            return Result.unchanged("empty");
        } catch (java.awt.HeadlessException e) {
            throw RpcError.notEditable("needsDialog", "the clipboard has parts of a library this file does not have");
        }
        d.project().doAction(a);
        return m.result(null);
    }

    /** Edit › Duplicate(Ctrl+D): v1 SafeDuplicate(사본이 옛 선·포트에 닿지 않는 자리, W-05). */
    public static Result duplicate(Doc d, Circuit c, List<Component> ids) throws RpcError {
        Intents.editable(d, c);
        d.show(c);
        if (ids != null) {
            selectOnly(d, ids);
        }
        Selection sel = d.selection();
        if (sel.isEmpty()) {
            return Result.unchanged("empty");
        }
        Mark m = new Mark(d.project());
        SafeDuplicate.run(d.project(), sel);
        return m.result(null);
    }

    // ---- 속성 ----

    /** 선택 속성 표(AttrTableSelectionModel.setValueRequested): 고른 것 가운데 선이 아닌 것 모두에 한 동작. */
    public static Result setAttr(Doc d, Circuit c, List<Component> ids, String attr, String value) throws RpcError {
        Intents.editable(d, c);
        d.show(c);
        if (ids != null) {
            selectOnly(d, ids);
        }
        Selection sel = d.selection();
        List<Component> targets = new ArrayList<>();
        for (Component comp : sel.getComponents()) {
            if (!(comp instanceof Wire)) {
                targets.add(comp);
            }
        }
        if (targets.isEmpty()) {
            if (sel.isEmpty()) {
                throw RpcError.params("nothing is selected (circuit attributes: edit.setCircuitAttr)");
            }
            return Result.unchanged("empty");
        }
        SetAttributeAction act = new SetAttributeAction(c, Intents.GUI.getter("selectionAttributeAction"));
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
            act.set(comp, a, Intents.parse(a, value));
        }
        Mark m = new Mark(d.project());
        d.project().doAction(act);
        return m.result(null);
    }

    /** v1 R / Shift+R(Shortcuts.rotate): 방향이 있는 고른 부품을 90도씩, 한 동작 "Rotate". */
    public static Result rotate(Doc d, Circuit c, List<Component> ids, boolean clockwise) throws RpcError {
        Intents.editable(d, c);
        d.show(c);
        if (ids != null) {
            selectOnly(d, ids);
        }
        List<Component> faced = new ArrayList<>();
        for (Component comp : d.selection().getComponents()) {
            if (!(comp instanceof Wire) && comp.getAttributeSet().getAttribute("facing") != null) {
                faced.add(comp);
            }
        }
        if (faced.isEmpty()) {
            return Result.unchanged("empty");
        }
        faced.sort((a, b) -> d.ids().of(a).compareTo(d.ids().of(b))); // 차례가 결과에 영향이 없게
        CircuitMutation mutation = Shortcuts.rotation(c, faced, clockwise);
        Mark m = new Mark(d.project());
        d.project().doAction(mutation.toAction(ROTATE));
        return m.result(null);
    }

    private static final StringGetter ROTATE = () -> "Rotate"; // v1 names.properties keys.rotateAction

    /**
     * 숫자·Alt+숫자·Alt+방향 키(원조 KeyConfigurator: 게이트 입력 수, 비트 폭, Select Bits …). tool이 없으면 고른 부품에
     * (SelectTool.processKeyEvent, 되돌리기 한 단계), 있으면 그 놓기 도구에(AddTool.processKeyEvent, ToolAttributeAction).
     * chain이 아니면 설정기를 새로 만든다(여러 자리 수의 앞 글자를 잊는다). chain이면 앞 키의 설정기를 이어 쓴다: 원조처럼
     * 0.8초 안의 숫자를 이어 여러 자리 수로 읽는다(화면이 그 시간을 재어 chain을 정한다).
     */
    public static Result keyConfig(Doc d, Circuit c, Tool tool, String key, boolean alt, boolean chain)
            throws RpcError {
        KeyEvent[] events = keyEvents(d.canvas(), key, alt);
        if (tool == null) {
            Intents.editable(d, c);
            d.show(c);
            Selection sel = d.selection();
            if (!chain || d.keyHandlers == null) {
                Map<Component, KeyConfigurator> handlers = new HashMap<>();
                for (Component comp : sel.getComponents()) {
                    Object h = comp.getFactory().getFeature(KeyConfigurator.class, comp.getAttributeSet());
                    if (h != null) {
                        handlers.put(comp, ((KeyConfigurator) h).clone());
                    }
                }
                d.keyHandlers = handlers;
            }
            Map<Component, KeyConfigurator> handlers = d.keyHandlers;
            Mark m = new Mark(d.project());
            int[] types = {KeyConfigurationEvent.KEY_PRESSED, KeyConfigurationEvent.KEY_TYPED,
                KeyConfigurationEvent.KEY_RELEASED};
            for (int k = 0; k < 3; k++) {
                if (events[k] == null || handlers.isEmpty()) {
                    continue;
                }
                List<KeyConfigurationResult> results = new ArrayList<>();
                for (Map.Entry<Component, KeyConfigurator> entry : handlers.entrySet()) {
                    Component comp = entry.getKey();
                    KeyConfigurationEvent event = new KeyConfigurationEvent(types[k], comp.getAttributeSet(),
                            events[k], comp);
                    KeyConfigurationResult r = entry.getValue().keyEventReceived(event);
                    if (r != null) {
                        results.add(r);
                    }
                }
                if (!results.isEmpty()) {
                    SetAttributeAction act = new SetAttributeAction(c,
                            TOOLS.getter("changeComponentAttributesAction"));
                    for (KeyConfigurationResult r : results) {
                        Component comp = (Component) r.getEvent().getData();
                        for (Map.Entry<Attribute<?>, Object> e : r.getAttributeValues().entrySet()) {
                            act.set(comp, e.getKey(), e.getValue());
                        }
                    }
                    if (!act.isEmpty()) {
                        d.project().doAction(act);
                    }
                }
            }
            return m.result(null);
        }
        if (!(tool instanceof AddTool)) {
            return Result.unchanged("empty");
        }
        AddTool add = (AddTool) tool;
        KeyConfigurator handler = chain ? d.toolKeyHandlers.get(tool) : null;
        if (handler == null) {
            ComponentFactory f = add.getFactory();
            Object h = f == null ? null : f.getFeature(KeyConfigurator.class, add.getAttributeSet());
            handler = h == null ? null : (KeyConfigurator) h;
            if (handler != null) {
                d.toolKeyHandlers.put(tool, handler);
            }
        }
        Mark m = new Mark(d.project());
        int[] types = {KeyConfigurationEvent.KEY_PRESSED, KeyConfigurationEvent.KEY_TYPED,
            KeyConfigurationEvent.KEY_RELEASED};
        boolean consumed = false;
        for (int k = 0; k < 3 && handler != null; k++) {
            if (events[k] == null) {
                continue;
            }
            KeyConfigurationEvent e = new KeyConfigurationEvent(types[k], add.getAttributeSet(), events[k], add);
            KeyConfigurationResult r = handler.keyEventReceived(e);
            if (r != null) {
                d.project().doAction(ToolAttributeAction.create(r));
            }
            consumed |= e.isConsumed();
        }
        if (!consumed && !alt && key.startsWith("Arrow")) {
            // AddTool.keyPressed: 설정기가 받지 않은 방향 키는 놓을 부품의 방향(setFacing, ToolAttributeAction)
            Direction facing = key.equals("ArrowUp") ? Direction.NORTH : key.equals("ArrowDown") ? Direction.SOUTH
                    : key.equals("ArrowLeft") ? Direction.WEST : Direction.EAST;
            ComponentFactory f = add.getFactory();
            Object feature = f == null ? null
                    : f.getFeature(ComponentFactory.FACING_ATTRIBUTE_KEY, add.getAttributeSet());
            if (feature != null) {
                @SuppressWarnings("unchecked")
                Attribute<Direction> attr = (Attribute<Direction>) feature;
                d.project().doAction(ToolAttributeAction.create(add, attr, facing)); // 같은 값이어도(원조와 같다)
            }
        }
        return m.result(null);
    }

    /** 원조 캔버스가 도구에 주는 키 세 가지(누름, 글자, 뗌). key: 숫자·글자 한 개, 또는 ArrowUp·Down·Left·Right. */
    static KeyEvent[] keyEvents(Canvas canvas, String key, boolean alt) throws RpcError {
        int mods = alt ? InputEvent.ALT_DOWN_MASK : 0;
        long when = System.currentTimeMillis();
        int vk;
        char ch;
        switch (key) {
        case "ArrowUp": vk = KeyEvent.VK_UP; ch = KeyEvent.CHAR_UNDEFINED; break;
        case "ArrowDown": vk = KeyEvent.VK_DOWN; ch = KeyEvent.CHAR_UNDEFINED; break;
        case "ArrowLeft": vk = KeyEvent.VK_LEFT; ch = KeyEvent.CHAR_UNDEFINED; break;
        case "ArrowRight": vk = KeyEvent.VK_RIGHT; ch = KeyEvent.CHAR_UNDEFINED; break;
        default:
            if (key.length() != 1) {
                throw RpcError.params("key must be one character or an arrow");
            }
            ch = key.charAt(0);
            vk = KeyEvent.getExtendedKeyCodeForChar(ch);
        }
        KeyEvent pressed = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, when, mods, vk, ch);
        KeyEvent typed = ch == KeyEvent.CHAR_UNDEFINED ? null
                : new KeyEvent(canvas, KeyEvent.KEY_TYPED, when, mods, KeyEvent.VK_UNDEFINED, ch);
        KeyEvent released = new KeyEvent(canvas, KeyEvent.KEY_RELEASED, when, mods, vk, ch);
        return new KeyEvent[] {pressed, typed, released};
    }

    // ---- 도구 속성 ----

    /** 도구 찾기: 부품 놓기 도구(파일의 회로·라이브러리)와 Base의 글자 도구 등. */
    public static Tool findTool(Doc d, String lib, String name) throws RpcError {
        if (lib == null || lib.isEmpty()) {
            return Intents.findTool(d, lib, name);
        }
        for (Library l : MipsShadow.libraries(d.file())) {
            if (l.getName().equals(lib)) {
                Tool t = l.getTool(name);
                if (t != null) {
                    return t;
                }
            }
        }
        throw RpcError.notFound("tool", lib + "/" + name);
    }

    /**
     * 도구 속성 표(AttrTableToolModel → ToolAttributeAction, 되돌리기 한 단계). 값은 도구에 남아 다음 놓기에 쓰이고
     * {@code <lib><tool>}에 저장된다. 이미 같은 값이면 아무것도 하지 않는다(편집 동등성 재생기와 같다).
     */
    public static Result setToolAttr(Doc d, String lib, String name, String attr, String value) throws RpcError {
        Tool tool = findTool(d, lib, name);
        AttributeSet as = tool.getAttributeSet();
        @SuppressWarnings("unchecked")
        Attribute<Object> a = as == null ? null : (Attribute<Object>) as.getAttribute(attr);
        if (a == null) {
            throw RpcError.params("tool " + name + " has no attribute '" + attr + "'");
        }
        Object v = Intents.parse(a, value);
        Object now = as.getValue(a);
        if (now != null && now.equals(v)) {
            return Result.unchanged("same");
        }
        if (d.isReadOnly()) {
            throw RpcError.notEditable("readOnly", "the file is read-only");
        }
        Mark m = new Mark(d.project());
        d.project().doAction(ToolAttributeAction.create(tool, a, v));
        d.toolKeyHandlers.remove(tool);
        return m.result(null);
    }

    // ---- 글자 도구 ----

    /**
     * 글자 도구(TextTool.editingStopped). comp가 없으면 loc에 새 Label을 글자 도구의 속성으로 더하고(빈 글이면 하지
     * 않음), 있으면 그 부품의 글 칸(라벨, Label 글)을 원조 {@code TextEditable.getCommitAction}으로 바꾼다. 있는 Label의
     * 글을 모두 지우면 원조처럼 "Remove Label" 이름의 동작이 되지만 이미 있는 부품을 다시 더하는 것이라 그대로 남는다
     * (원조 결함, docs/interaction-parity.md I-82).
     */
    public static Result text(Doc d, Circuit c, Component comp, Location loc, String text) throws RpcError {
        Intents.editable(d, c);
        d.show(c);
        boolean empty = text == null || text.isEmpty();
        Mark m = new Mark(d.project());
        Action a;
        if (comp == null) {
            if (loc == null) {
                throw RpcError.params("loc or id is required");
            }
            if (empty || loc.getX() < 0 || loc.getY() < 0) {
                return Result.unchanged("empty");
            }
            Tool textTool = d.file().getLibrary("Base") == null ? null : d.file().getLibrary("Base").getTool("Text Tool");
            AttributeSet attrs = textTool == null ? Text.FACTORY.createAttributeSet()
                    : (AttributeSet) textTool.getAttributeSet().clone();
            attrs.setValue(Text.ATTR_TEXT, text);
            Component label = Text.FACTORY.createComponent(loc, attrs);
            CircuitMutation xn = new CircuitMutation(c);
            xn.add(label);
            a = xn.toAction(TOOLS.getter("addComponentAction", Text.FACTORY.getDisplayGetter()));
            d.project().doAction(a);
            return new Result(m.changed(), null, label);
        }
        if (empty && comp.getFactory() instanceof Text) {
            CircuitMutation xn = new CircuitMutation(c);
            xn.add(comp);
            a = xn.toAction(TOOLS.getter("removeComponentAction", Text.FACTORY.getDisplayGetter()));
        } else {
            TextEditable editable = (TextEditable) comp.getFeature(TextEditable.class);
            if (editable == null) {
                throw RpcError.params("component " + d.ids().of(comp) + " has no text to edit");
            }
            a = editable.getCommitAction(c, null, text == null ? "" : text);
        }
        if (a != null) {
            d.project().doAction(a);
        }
        return m.result(null);
    }

    /** 고른 것(화면에 알리는 edit.selection의 재료): 회로에 있는 것과 떠 있는 것. */
    public static List<Component> anchored(Doc d) {
        return new ArrayList<>(d.selection().getAnchoredComponents());
    }

    public static List<Component> floating(Doc d) {
        return new ArrayList<>(d.selection().getFloatingComponents());
    }
}
