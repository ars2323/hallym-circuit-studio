/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.edit;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.libs.LibrarySync;
import kr.ac.hallym.hcs.app.libs.OpenFileLibraries;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.doc.Files;
import kr.ac.hallym.hcs.engine.model.AppearanceEditJson;

/**
 * 파일·회로 단위의 알림과 파일 사이의 일(N-11, D-153).
 * <ul>
 * <li>{@code file.changed}: 편집 뒤 파일의 회로 목록(차례·이름), 주 회로, 라이브러리가 바뀌었으면(회로 더하기·지우기·
 * 이름 바꾸기·차례, Set As Main, Load/Unload Library, Import, 그 되돌리기).</li>
 * <li>{@code model.appearance}: 화면이 모양 편집 화면으로 연 회로({@code model.appearance}로 물은 회로)의 모양이
 * 바뀌었으면 그 전체.</li>
 * <li>{@code model.portImpact}: {@link PortWatch}가 센 끊긴 인스턴스 연결.</li>
 * <li>저장 반영(v1 P-03 {@code LibrarySync}, D-065): 저장한 파일을 라이브러리로 쓰는 다른 열린 파일의 라이브러리를
 * 원조 {@code Loader.reload}로 새 버전으로 바꾸고 그 인스턴스를 새 회로로 바꾼다(원조 {@code LoadedLibrary}가 열린
 * 창의 프로젝트에 하는 일과 같은 코드; 엔진의 파일은 원조 창 목록에 없으므로 엔진이 한다).</li>
 * </ul>
 */
public final class CircuitService {
    private final Map<String, PortWatch> watches = new HashMap<>();
    private final Map<String, String> fileSent = new HashMap<>();
    /** 파일마다 모양 편집 화면이 본 회로 → 마지막으로 보낸 모양 글자. */
    private final Map<String, Map<Circuit, String>> appearanceSent = new HashMap<>();

    public void attach(Doc d) {
        watches.put(d.id(), new PortWatch(d));
        fileSent.put(d.id(), fileJson(d).toString());
    }

    public void detach(Doc d) {
        PortWatch w = watches.remove(d.id());
        if (w != null) {
            w.close();
        }
        fileSent.remove(d.id());
        appearanceSent.remove(d.id());
    }

    /** 의도 하나를 마친 뒤: 끊긴 인스턴스 연결(되살림 포함). */
    public List<JsonObject> settle(Doc d) {
        PortWatch w = watches.get(d.id());
        return w == null ? List.of() : w.settle();
    }

    /** file.changed = {fileId, name, circuits:[CircuitRef], main, libraries:[LibRef], dirty}. */
    public static JsonObject fileJson(Doc d) {
        JsonObject o = new JsonObject();
        o.addProperty("fileId", d.id());
        o.addProperty("name", d.file().getName());
        o.add("circuits", d.circuitRefs());
        o.addProperty("main", d.mainId());
        o.add("libraries", Files.libraryRefs(d));
        return o;
    }

    /** 편집 뒤에 보낼 알림들(method, params): 파일 구조, 열어 둔 모양. */
    public List<Object[]> changes(Doc d) {
        List<Object[]> out = new ArrayList<>();
        JsonObject f = fileJson(d);
        String text = f.toString();
        if (!text.equals(fileSent.get(d.id()))) {
            fileSent.put(d.id(), text);
            JsonObject sent = f.deepCopy();
            sent.addProperty("dirty", d.isDirty());
            out.add(new Object[] {"file.changed", sent});
        }
        Map<Circuit, String> seen = appearanceSent.get(d.id());
        if (seen != null) {
            for (Map.Entry<Circuit, String> e : new ArrayList<>(seen.entrySet())) {
                Circuit c = e.getKey();
                if (!d.file().contains(c)) {
                    seen.remove(c);
                    continue;
                }
                JsonObject a = appearance(d, c);
                String t = a.toString();
                if (!t.equals(e.getValue())) {
                    seen.put(c, t); // the copy of an IdentityHashMap entry does not write through
                    a.addProperty("fileId", d.id());
                    out.add(new Object[] {"model.appearance", a});
                }
            }
        }
        return out;
    }

    /** model.appearance: 모양 편집 화면의 자료. 이 회로를 지켜보다 바뀌면 알림으로 다시 보낸다. */
    public JsonObject watchAppearance(Doc d, Circuit c) {
        JsonObject a = appearance(d, c);
        appearanceSent.computeIfAbsent(d.id(), k -> new IdentityHashMap<>()).put(c, a.toString());
        JsonObject o = a.deepCopy();
        o.addProperty("fileId", d.id());
        return o;
    }

    static JsonObject appearance(Doc d, Circuit c) {
        return AppearanceEditJson.of(d.ids(), c, d.file().contains(c) && !d.isReadOnly());
    }

    // ---- 파일 사이(v1 P-03) ----

    /** file#… 라이브러리로 f를 쓰는 다른 열린 파일들과 그 라이브러리. */
    static Map<Doc, LoadedLibrary> users(Collection<Doc> all, Doc saving, File f) {
        Map<Doc, LoadedLibrary> out = new LinkedHashMap<>();
        for (Doc o : all) {
            if (o == saving) {
                continue;
            }
            LoadedLibrary lib = OpenFileLibraries.loaded(o.project(), f);
            if (lib != null) {
                out.put(o, lib);
            }
        }
        return out;
    }

    /**
     * file.saveImpact(v1 {@code LibrarySync.impact}, D-065): 이 파일을 지금 내용으로 저장하면 이 파일을 라이브러리로
     * 쓰는 다른 열린 파일의 인스턴스에서 끊길 연결. 이어진 포트마다 같은 이름의 핀이 없어지거나 포트 자리(모양
     * 기준 오프셋)가 달라지면 끊긴다. [{fileId, file, instances:[이름], connections}].
     */
    public static JsonArray saveImpact(Collection<Doc> all, Doc saving) {
        JsonArray out = new JsonArray();
        File f = saving.loader().getMainFile();
        if (f == null) {
            return out;
        }
        for (Map.Entry<Doc, LoadedLibrary> u : users(all, saving, f).entrySet()) {
            Set<Circuit> libCircuits = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            for (Tool t : u.getValue().getTools()) {
                if (t instanceof AddTool && ((AddTool) t).getFactory(false) instanceof SubcircuitFactory) {
                    libCircuits.add(((SubcircuitFactory) ((AddTool) t).getFactory(false)).getSubcircuit());
                }
            }
            Set<String> names = new LinkedHashSet<>();
            int count = 0;
            for (Circuit parent : u.getKey().file().getCircuits()) {
                Netlist nl = null;
                List<Component> comps = new ArrayList<>(parent.getNonWires());
                comps.sort(java.util.Comparator.<Component>comparingInt(c -> c.getLocation().getY())
                        .thenComparingInt(c -> c.getLocation().getX()));
                for (Component inst : comps) {
                    if (!(inst.getFactory() instanceof SubcircuitFactory)) {
                        continue;
                    }
                    Circuit oldC = ((SubcircuitFactory) inst.getFactory()).getSubcircuit();
                    if (!libCircuits.contains(oldC)) {
                        continue;
                    }
                    Circuit newC = saving.file().getCircuit(oldC.getName());
                    if (nl == null) {
                        nl = Netlist.of(parent);
                    }
                    Direction facing = inst.getAttributeSet().getValue(StdAttr.FACING);
                    Map<String, Location> before = ports(oldC, facing);
                    Map<String, Location> after = newC == null ? new LinkedHashMap<>() : ports(newC, facing);
                    int i = 0;
                    for (Map.Entry<String, Location> e : before.entrySet()) {
                        int end = i++;
                        if (!connected(nl, inst, end)) {
                            continue;
                        }
                        Location a = after.get(e.getKey());
                        if (a == null || !a.equals(e.getValue())) {
                            count++;
                            String l = Names.label(inst);
                            names.add(l != null ? l : oldC.getName());
                        }
                    }
                }
            }
            if (count > 0) {
                JsonObject o = new JsonObject();
                o.addProperty("fileId", u.getKey().id());
                File uf = u.getKey().loader().getMainFile();
                o.addProperty("file", uf != null ? uf.getName() : u.getKey().file().getName());
                JsonArray n = new JsonArray();
                names.forEach(n::add);
                o.add("instances", n);
                o.addProperty("connections", count);
                out.add(o);
            }
        }
        return out;
    }

    /** 회로 모양의 포트: 핀 이름(없으면 순번) → 오프셋(v1 LibrarySync.ports와 같다). */
    static Map<String, Location> ports(Circuit c, Direction facing) {
        SortedMap<Location, Instance> offs = c.getAppearance().getPortOffsets(facing == null ? Direction.EAST : facing);
        Map<String, Location> out = new LinkedHashMap<>();
        int i = 0;
        for (Map.Entry<Location, Instance> e : offs.entrySet()) {
            String l = Names.label(Instance.getComponentFor(e.getValue()));
            out.put(l != null ? l : "#" + i, e.getKey());
            i++;
        }
        return out;
    }

    static boolean connected(Netlist nl, Component inst, int end) {
        if (end >= inst.getEnds().size()) {
            return false;
        }
        Netlist.Net n = nl.netOf(inst, end);
        if (n == null) {
            return false;
        }
        if (!n.wires().isEmpty()) {
            return true;
        }
        for (Netlist.PortRef p : n.ports()) {
            if (p.component != inst) {
                return true;
            }
        }
        return false;
    }

    /**
     * 저장 뒤(v1 {@code LibrarySync.afterSave}) 새 버전을 받을 파일들: 저장한 파일을 file# 라이브러리로 쓰는 다른 열린 파일과
     * 그 라이브러리. 새 버전을 넣는 일은 엔진이 파일마다 그 파일의 시뮬레이터를 세운 채 한다({@link #reload},
     * {@link #refresh}, Engine.reloadLibraries, D-153).
     */
    public static Map<Doc, LoadedLibrary> libraryUsers(Collection<Doc> all, Doc saved) {
        File f = saved.loader().getMainFile();
        return f == null ? new LinkedHashMap<>() : users(all, saved, f);
    }

    /** edit.reloadLibrary의 답: 이 파일의 부품이 바뀌었는지. */
    public static Intents.Result reloaded(boolean changed) {
        return changed ? new Intents.Result(true, null, null) : Intents.Result.unchanged("same");
    }

    /** 이 파일의 라이브러리 중 이름이 name인 Logisim 라이브러리(.circ). */
    public static LoadedLibrary circLibrary(Doc d, String name) {
        for (com.cburch.logisim.tools.Library l : d.file().getLibraries()) {
            if (l instanceof LoadedLibrary && l.getName().equals(name)) {
                try {
                    if (com.cburch.logisim.file.LibraryManager.instance.getDescriptor(d.loader(), l).startsWith("file#")) {
                        return (LoadedLibrary) l;
                    }
                } catch (RuntimeException unknown) {
                    // 원조 LibraryManager가 모르는 라이브러리: 다시 읽을 곳이 없다
                }
            }
        }
        return null;
    }

    /** 라이브러리의 도구 팩토리, 이름으로. */
    static Map<String, ComponentFactory> factories(com.cburch.logisim.tools.Library lib) {
        Map<String, ComponentFactory> out = new LinkedHashMap<>();
        for (Tool t : lib.getTools()) {
            if (t instanceof AddTool && ((AddTool) t).getFactory(false) != null) {
                out.put(t.getName(), ((AddTool) t).getFactory(false));
            }
        }
        return out;
    }

    /**
     * 라이브러리를 디스크에서 다시 읽는다(원조 {@code Loader.reload}; 여러 파일이 같은 LoadedLibrary를 나눠 쓰므로 한 번).
     * 앞 도구 팩토리 → 새 팩토리(없어진 도구는 null). 부르는 쪽이 이 라이브러리를 쓰는 모든 파일의 시뮬레이터를 세운다.
     */
    public static Map<ComponentFactory, ComponentFactory> reload(Doc d, LoadedLibrary lib) {
        Map<String, ComponentFactory> before = factories(lib);
        d.loader().reload(lib);
        d.loader().drainErrors();
        Map<String, ComponentFactory> now = factories(lib);
        Map<ComponentFactory, ComponentFactory> map = new IdentityHashMap<>();
        for (Map.Entry<String, ComponentFactory> e : before.entrySet()) {
            ComponentFactory n = now.get(e.getKey());
            if (n != e.getValue()) {
                map.put(e.getValue(), n);
            }
        }
        return map;
    }

    /**
     * 이 파일의 부품 가운데 라이브러리의 옛 버전을 가리키는 것을 지금 버전의 부품으로 바꾼다(원조
     * {@code LoadedLibrary.replaceAll}과 같은 CircuitMutation, 되돌리기 기록에 남지 않는다). known은 {@link #reload}의 답.
     * 저장이 이미 바꾼 라이브러리(원조 LibraryManager.fileSaved)나 앞서 다른 파일이 다시 읽은 것처럼 known에 없는 옛
     * 버전은 이름으로 찾는다: 이 파일의 회로도, 어느 라이브러리의 지금 도구도 아닌 서브회로 팩토리. 부르는 쪽이 이
     * 파일의 시뮬레이터를 세운다. 바뀐 것이 있는지.
     */
    public static boolean refresh(Doc d, LoadedLibrary lib, Map<ComponentFactory, ComponentFactory> known) {
        Map<String, ComponentFactory> now = factories(lib);
        Set<ComponentFactory> live = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Circuit c : d.file().getCircuits()) {
            live.add(c.getSubcircuitFactory());
        }
        for (com.cburch.logisim.tools.Library l : d.file().getLibraries()) {
            live.addAll(factories(l).values());
        }
        Map<ComponentFactory, ComponentFactory> map = new IdentityHashMap<>();
        for (Circuit c : d.file().getCircuits()) {
            for (Component comp : c.getNonWires()) {
                ComponentFactory f = comp.getFactory();
                if (known.containsKey(f)) {
                    map.put(f, known.get(f));
                } else if (f instanceof SubcircuitFactory && !live.contains(f) && now.containsKey(f.getName())) {
                    map.put(f, now.get(f.getName()));
                }
            }
        }
        boolean changed = false;
        for (Circuit c : d.file().getCircuits()) {
            changed |= replaceAll(c, map);
        }
        return changed;
    }

    /** 원조 {@code LoadedLibrary.replaceAll(Circuit, …)}과 같다(그 메서드는 원조 창의 프로젝트에만 불린다). */
    static boolean replaceAll(Circuit circuit, Map<ComponentFactory, ComponentFactory> map) {
        List<Component> toReplace = new ArrayList<>();
        for (Component comp : circuit.getNonWires()) {
            if (map.containsKey(comp.getFactory())) {
                toReplace.add(comp);
            }
        }
        if (toReplace.isEmpty()) {
            return false;
        }
        CircuitMutation xn = new CircuitMutation(circuit);
        for (Component comp : toReplace) {
            xn.remove(comp);
            ComponentFactory factory = map.get(comp.getFactory());
            if (factory != null) {
                AttributeSet dest = factory.createAttributeSet();
                AttributeSet src = comp.getAttributeSet();
                for (Attribute<?> a : dest.getAttributes()) {
                    Attribute<?> s = src.getAttribute(a.getName());
                    if (s != null) {
                        @SuppressWarnings("unchecked")
                        Attribute<Object> a2 = (Attribute<Object>) a;
                        dest.setValue(a2, src.getValue(s));
                    }
                }
                xn.add(factory.createComponent(comp.getLocation(), dest));
            }
        }
        xn.execute();
        return true;
    }

    /** file.originOf(v1 Edit Original File, {@code LibrarySync.originFile}): 라이브러리 회로의 파일. 이 파일의 회로면 null. */
    public static File originOf(Doc d, Circuit c) {
        return LibrarySync.originFile(d.project(), c);
    }
}
