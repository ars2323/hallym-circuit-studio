/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.edit;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.cburch.draw.model.CanvasObject;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.AddTool;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.appear.AutoAppearance;
import kr.ac.hallym.hcs.app.model.InstancePaths;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.edit.Intents.Result;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * 회로 단위의 편집(N-11, D-153): Project 메뉴와 부품 목록이 회로에 하는 일(원조 {@code ProjectCircuitActions}·
 * {@code ToolboxManip})과 v1의 Port Order…(P-04)·Auto Appearance(S-08). 모두 원조 동작 객체
 * ({@link LogisimFileActions})나 v1 동작({@link AutoAppearance#action})을 {@code Project.doAction}에 한 번 넘긴다:
 * 되돌리기 한 단계다. 원조가 오류 창으로 거절하던 것은 오류 3(까닭 {@code data.reason})으로 돌려준다.
 */
public final class CircuitIntents {
    private CircuitIntents() {
    }

    /**
     * Project › Remove Circuit(원조 {@code ProjectCircuitActions.doRemoveCircuit}): 마지막 회로는 지우지 않고
     * ({@code lastCircuit}), 다른 회로가 쓰는 회로도 지우지 않는다({@code inUse}). 보던 회로를 지우면 원조 Project가
     * 주 회로로 돌아간다.
     */
    public static Result deleteCircuit(Doc d, Circuit c) throws RpcError {
        Intents.editable(d, c);
        if (d.file().getTools().size() == 1) {
            throw RpcError.notEditable("lastCircuit", "a file keeps at least one circuit");
        }
        if (!d.project().getDependencies().canRemove(c)) {
            throw RpcError.notEditable("inUse", "another circuit uses " + c.getName());
        }
        d.project().doAction(LogisimFileActions.removeCircuit(c));
        return new Result(true, null, null);
    }

    /**
     * 회로 차례 바꾸기(원조 Move Circuit Up/Down, 부품 목록 끌기 {@code LogisimFileActions.moveCircuit}). to는 옮긴 뒤의
     * 자리(0부터).
     */
    public static Result moveCircuit(Doc d, Circuit c, int to) throws RpcError {
        Intents.editable(d, c);
        AddTool tool = d.file().getAddTool(c);
        List<AddTool> tools = d.file().getTools();
        if (tool == null || to < 0 || to >= tools.size()) {
            throw RpcError.params("to must be from 0 to " + (tools.size() - 1));
        }
        if (tools.indexOf(tool) == to) {
            return Result.unchanged("same");
        }
        d.project().doAction(LogisimFileActions.moveCircuit(tool, to));
        return new Result(true, null, null);
    }

    // ---- 포트 차례와 자동 모양(v1 P-04, S-08) ----

    static final Direction[] SIDES = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};

    static String sideName(Direction d) {
        return d.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Port Order…(v1 {@code PortOrderDialog.apply}): 변마다 준 차례로 v1 Auto Appearance 모양을 만든다. order는 변 이름
     * ({@code west east north south}) → 포트 목록이고, 목록의 항목은 핀 이름(라벨, 같은 이름이 여럿이면 지금 차례로 하나씩)
     * 또는 지금 차례의 번호다. 준 변은 그 변의 포트를 모두 한 번씩 들어야 한다. 빠진 변은 지금 차례 그대로.
     * 인스턴스 연결이 끊어지면 confirm이 거짓일 때 바꾸지 않고 {@code outcome:"needsConfirm"}과 {@code impact}를 준다
     * (v1의 확인 창, 화면이 묻고 confirm:true로 다시 보낸다). confirm 기본은 참(Swing의 Apply).
     */
    public static Result portOrder(Doc d, Circuit c, JsonObject order, boolean confirm) throws RpcError {
        Intents.editable(d, c);
        Map<Direction, List<Instance>> now = AutoAppearance.sides(c);
        if (now.values().stream().allMatch(List::isEmpty)) {
            return Result.unchanged("noPorts");
        }
        Map<Direction, List<Instance>> chosen = new LinkedHashMap<>();
        for (Direction side : now.keySet()) {
            chosen.put(side, new ArrayList<>(now.get(side)));
        }
        for (Map.Entry<String, JsonElement> e : order.entrySet()) {
            Direction side = side(e.getKey());
            if (!e.getValue().isJsonArray()) {
                throw RpcError.params("order." + e.getKey() + " must be a list");
            }
            chosen.put(side, reorder(now.get(side), e.getValue().getAsJsonArray(), e.getKey()));
        }
        List<CanvasObject> shapes = AutoAppearance.build(c, chosen);
        return apply(d, c, shapes, confirm);
    }

    /** Auto Appearance(v1 S-08): 지금 포트 차례로 v1 표준 모양. 끊어질 연결은 {@link #portOrder}와 같이 묻는다. */
    public static Result autoAppearance(Doc d, Circuit c, boolean confirm) throws RpcError {
        Intents.editable(d, c);
        if (AutoAppearance.sides(c).values().stream().allMatch(List::isEmpty)) {
            return Result.unchanged("noPorts");
        }
        return apply(d, c, AutoAppearance.build(c), confirm);
    }

    private static Result apply(Doc d, Circuit c, List<CanvasObject> shapes, boolean confirm) {
        AutoAppearance.Impact impact = AutoAppearance.impact(d.file(), c, shapes);
        if (impact.connections > 0 && !confirm) {
            return Result.unchanged("needsConfirm").with("impact", impactJson(impact));
        }
        d.project().doAction(AutoAppearance.action(c, shapes));
        Result r = new Result(true, null, null);
        return impact.connections > 0 ? r.with("impact", impactJson(impact)) : r;
    }

    static JsonObject impactJson(AutoAppearance.Impact impact) {
        JsonObject o = new JsonObject();
        o.addProperty("instances", impact.instances);
        o.addProperty("connections", impact.connections);
        JsonArray where = new JsonArray();
        impact.where.forEach(where::add);
        o.add("where", where);
        return o;
    }

    private static Direction side(String name) throws RpcError {
        for (Direction s : SIDES) {
            if (sideName(s).equals(name)) {
                return s;
            }
        }
        throw RpcError.params("order has west, east, north or south, not " + name);
    }

    private static List<Instance> reorder(List<Instance> side, JsonArray wanted, String name) throws RpcError {
        if (wanted.size() != side.size()) {
            throw RpcError.params("order." + name + " must list the side's " + side.size() + " ports");
        }
        Map<Instance, Boolean> used = new IdentityHashMap<>();
        List<Instance> out = new ArrayList<>();
        for (JsonElement w : wanted) {
            Instance hit = null;
            if (w.isJsonPrimitive() && w.getAsJsonPrimitive().isNumber()) {
                int i = w.getAsInt();
                if (i >= 0 && i < side.size() && !used.containsKey(side.get(i))) {
                    hit = side.get(i);
                }
            } else if (w.isJsonPrimitive()) {
                String want = w.getAsString().trim();
                for (Instance p : side) {
                    if (!used.containsKey(p) && AutoAppearance.portName(p).equals(want)) {
                        hit = p;
                        break;
                    }
                }
            }
            if (hit == null) {
                throw RpcError.params("order." + name + ": no port " + w + " on that side (or it is there twice)");
            }
            used.put(hit, true);
            out.add(hit);
        }
        return out;
    }

    // ---- 물음(모델을 바꾸지 않는다) ----

    /**
     * model.ports: Port Order 창의 목록. 변마다 지금 차례의 포트 {name, width, input}(v1 {@code AutoAppearance.sides}:
     * 핀이 보는 방향의 반대 변), 기본 모양인지, 이 회로를 쓰는 인스턴스 수.
     */
    public static JsonObject ports(Doc d, Circuit c) {
        JsonObject o = new JsonObject();
        o.addProperty("circuitId", d.ids().of(c));
        o.addProperty("name", c.getName());
        o.addProperty("default", c.getAppearance().isDefaultAppearance());
        JsonObject sides = new JsonObject();
        Map<Direction, List<Instance>> now = AutoAppearance.sides(c);
        for (Direction s : SIDES) {
            JsonArray a = new JsonArray();
            for (Instance p : now.get(s)) {
                JsonObject po = new JsonObject();
                po.addProperty("name", AutoAppearance.portName(p));
                po.addProperty("width", p.getAttributeValue(StdAttr.WIDTH).getWidth());
                po.addProperty("input", Pin.FACTORY.isInputPin(p));
                a.add(po);
            }
            sides.add(sideName(s), a);
        }
        o.add("sides", sides);
        o.addProperty("instances", instanceCount(d, c));
        return o;
    }

    static int instanceCount(Doc d, Circuit sub) {
        int n = 0;
        for (Circuit k : d.file().getCircuits()) {
            for (Component x : k.getNonWires()) {
                if (x.getFactory() instanceof SubcircuitFactory
                        && ((SubcircuitFactory) x.getFactory()).getSubcircuit() == sub) {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * model.instances(v1 P-02 {@code InstanceBanner}): 이 회로가 주 회로에서 쓰이는 인스턴스 경로들(위치 차례, 많아야
     * 64개, v1 {@code InstancePaths.paths})과 핀을 더하거나 옮길 때의 영향 미리 보기에 쓰는 수. 경로는 인스턴스 id와
     * 사람이 읽는 글({@code main › cpu › alu #1}).
     */
    public static JsonObject instances(Doc d, Circuit c) {
        JsonObject o = new JsonObject();
        Circuit main = d.file().getMainCircuit();
        o.addProperty("circuitId", d.ids().of(c));
        o.addProperty("main", main == null ? null : d.ids().of(main));
        o.addProperty("mainName", main == null ? null : main.getName());
        JsonArray paths = new JsonArray();
        if (main != null && main != c) {
            for (List<Component> p : InstancePaths.paths(main, c)) {
                JsonObject po = new JsonObject();
                JsonArray ids = new JsonArray();
                JsonArray names = new JsonArray();
                Circuit at = main;
                for (Component inst : p) {
                    ids.add(d.ids().of(inst));
                    names.add(Names.name(at, inst));
                    at = ((SubcircuitFactory) inst.getFactory()).getSubcircuit();
                }
                po.add("ids", ids);
                po.add("names", names);
                po.addProperty("text", InstancePaths.describe(main, p));
                paths.add(po);
            }
        }
        o.add("paths", paths);
        List<InstancePaths.PortUse> uses = InstancePaths.snapshot(d.file(), c);
        int connected = 0;
        for (InstancePaths.PortUse u : uses) {
            connected += u.connected ? 1 : 0;
        }
        o.addProperty("instances", instanceCount(d, c));
        o.addProperty("connected", connected);
        o.addProperty("default", c.getAppearance().isDefaultAppearance());
        return o;
    }

    /** model.pinImpact(v1 {@code InstanceBanner.previewText}): 이 핀들을 지우거나 옮기면 끊길 수 있는 연결과 인스턴스 수. */
    public static JsonObject pinImpact(Doc d, Circuit c, List<Component> pins) {
        List<InstancePaths.PortUse> a = InstancePaths.affected(d.file(), c, pins);
        JsonObject o = new JsonObject();
        o.addProperty("connections", a.size());
        o.addProperty("instances", InstancePaths.instances(a));
        return o;
    }
}
