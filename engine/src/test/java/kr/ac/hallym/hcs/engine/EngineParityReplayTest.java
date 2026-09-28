/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitTransactionResult;
import com.cburch.logisim.circuit.ReplacementMap;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import kr.ac.hallym.hcs.app.libs.MipsShadow;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.regress.CircNormalizer;

/**
 * 편집 동등성 골든(tests/parity, N-01, D-136)을 v2 엔진이 같은 의도로 만든다(N-08, D-146). Swing 하네스(SwingReplayer)가
 * 창에서 한 일을 엔진의 JSON-RPC 의도로 보낸다: 회로 이름은 circuitId로, 가리키는 말(기호, label:, at:, wire:)은 엔진 id로
 * 바꾼다. 기호는 Swing 하네스처럼 원조 ReplacementMap의 바꿔치기와 같은 종류·같은 속성 한 쌍을 따라간다. 끝에 file.save로
 * 저장한 글자를 골든과 D-006 정규화로 비교한다. 아직 엔진에 없는 의도(Port Order, Auto Appearance, Import, 라이브러리 싣기)를
 * 쓰는 장면은 {@link #LATER}에 이유와 함께 적고, 그 목록이 정확한지도 본다.
 */
class EngineParityReplayTest {
    static final Path PARITY = new File(System.getProperty("hcs.circDir")).toPath().getParent().resolve("parity");
    static final String TEMPLATE = "resources/logisim/default.templ";

    /** 엔진에 아직 없는 의도를 쓰는 장면 → 그 의도(docs/engine-api.md의 제안 의도, 맡는 항목). */
    static final Map<String, String> LATER = new LinkedHashMap<>();
    // 08·09·11(Port Order·Auto Appearance, Import, Load/Unload Library)은 N-11(D-153)에서 엔진에 들어왔다: 남은 장면 없음

    @TempDir
    Path tmp;

    InProcess e;
    String fileId;
    Doc doc;
    final Map<String, Component> symbols = new LinkedHashMap<>();
    final Map<Component, Component> replaced = new IdentityHashMap<>();
    final Set<Circuit> watched = Collections.newSetFromMap(new IdentityHashMap<>());
    final CircuitListener tracker = this::track;

    @BeforeEach
    void start() throws Exception {
        resetSharedTools();
        e = new InProcess();
    }

    @AfterEach
    void stop() throws Exception {
        e.onEngine(() -> {
            for (Circuit c : watched) {
                c.removeCircuitListener(tracker);
            }
            return null;
        });
        e.close();
        resetSharedTools(); // 뒤 테스트에 도구 속성을 남기지 않는다
    }

    static Stream<String> scenes() throws IOException {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(PARITY)) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".intents"))
                    .map(n -> n.substring(0, n.length() - ".intents".length())).sorted().forEach(out::add);
        }
        return out.stream().filter(n -> !LATER.containsKey(n));
    }

    /** LATER의 장면은 정말 엔진에 없는 의도를 쓰고, 나머지 장면은 엔진의 의도만 쓴다(목록이 낡지 않게). */
    @Test
    void theScenesLeftForLaterUseIntentsTheEngineDoesNotHaveYet() throws Exception {
        Set<String> all = new TreeSet<>();
        try (Stream<Path> s = Files.list(PARITY)) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".intents"))
                    .forEach(n -> all.add(n.substring(0, n.length() - ".intents".length())));
        }
        assertTrue(all.containsAll(LATER.keySet()), "LATER names a scene that is not there: " + LATER.keySet());
        for (String scene : all) {
            Set<String> missing = new TreeSet<>();
            for (JsonObject i : intents(scene)) {
                String m = i.get("method").getAsString();
                if (m.startsWith("edit.") && !e.server.handles(m)) {
                    missing.add(m);
                }
            }
            assertEquals(LATER.containsKey(scene), !missing.isEmpty(),
                    scene + ": missing " + missing + (LATER.containsKey(scene) ? " (listed for later)" : ""));
        }
    }

    @ParameterizedTest
    @MethodSource("scenes")
    void theEngineSavesWhatTheSwingAppSaved(String scene) throws Exception {
        List<JsonObject> script = intents(scene);
        Path work = tmp.resolve("work");
        Files.createDirectories(work);
        File circ = work.resolve(scene + ".circ").toFile();
        prepare(script, work, circ);

        JsonObject opened = e.client.callObject("file.open", params("path", circ.getPath()));
        fileId = opened.get("fileId").getAsString();
        doc = e.onEngine(() -> e.engine.files().get(fileId));

        int n = 0;
        for (JsonObject i : script) {
            n++;
            String m = i.get("method").getAsString();
            if (m.equals("file.open") || m.equals("view.zoom")) {
                continue; // 연 파일은 위에서, 배율은 화면 상태(엔진은 아무것도 하지 않는다)
            }
            try {
                apply(i);
            } catch (Throwable t) {
                throw new AssertionError(scene + " intent " + n + " " + i + ": " + t.getMessage(), t);
            }
        }
        e.client.callObject("file.save", params("fileId", fileId));
        String got = CircNormalizer.normalize(new String(Files.readAllBytes(circ.toPath()), StandardCharsets.UTF_8));
        String want = CircNormalizer.normalize(new String(Files.readAllBytes(PARITY.resolve(scene + ".circ")),
                StandardCharsets.UTF_8));
        assertEquals(want, got, scene + ": the engine's save differs from the Swing app's golden");
    }

    // ---- 준비 ----

    static List<JsonObject> intents(String scene) throws IOException {
        List<JsonObject> out = new ArrayList<>();
        for (String line : Files.readAllLines(PARITY.resolve(scene + ".intents"), StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) {
                out.add(JsonParser.parseString(t).getAsJsonObject());
            }
        }
        return out;
    }

    /** Swing 하네스와 같은 작업 폴더: inputs/, 번들 hcs-mips.jar, 시작 파일(file.open의 파일 또는 원조 기본 템플릿). */
    static void prepare(List<JsonObject> script, Path work, File circ) throws IOException {
        Path inputs = PARITY.resolve("inputs");
        try (Stream<Path> s = Files.walk(inputs)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path to = work.resolve(PARITY.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(p, to, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Files.copy(new File(System.getProperty("hcs.bundledMips")).toPath(), work.resolve("hcs-mips.jar"),
                StandardCopyOption.REPLACE_EXISTING);
        JsonObject first = script.isEmpty() ? null : script.get(0);
        if (first != null && first.get("method").getAsString().equals("file.open")) {
            Files.copy(PARITY.resolve(first.get("path").getAsString()), circ.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } else {
            try (InputStream in = com.cburch.logisim.file.Loader.class.getClassLoader().getResourceAsStream(TEMPLATE)) {
                if (in == null) {
                    throw new IOException("no " + TEMPLATE);
                }
                Files.copy(in, circ.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * 새로 띄운 앱처럼: 원조 Wiring 도구 7개와 번들 MIPS 라이브러리의 도구 속성은 static이라 한 JVM의 모든 파일이 함께
     * 쓴다(tests/parity/README.md "알아 둘 동작"). 앞 테스트가 바꾼 값이 새지 않게 처음 값으로 되돌린다.
     */
    static void resetSharedTools() throws Exception {
        resetTools(new com.cburch.logisim.std.wiring.Wiring());
        MipsShadow.reset();
        Library shadow = MipsShadow.shadow(new com.cburch.logisim.file.Loader(null));
        if (shadow != null) {
            resetTools(shadow);
        }
    }

    static void resetTools(Library lib) throws Exception {
        for (Tool t : lib.getTools()) {
            if (t instanceof AddTool) {
                Object attrs = field(t, "attrs");
                setField(attrs, "baseAttrs", null);
                setField(t, "bounds", null);
            }
        }
    }

    static void setField(Object target, String name, Object value) throws Exception {
        Field f = find(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
    }

    static Object field(Object target, String name) throws Exception {
        Field f = find(target.getClass(), name);
        f.setAccessible(true);
        return f.get(target);
    }

    static Field find(Class<?> k, String name) throws NoSuchFieldException {
        for (Class<?> c = k; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException x) {
                // 위 클래스에서 찾는다
            }
        }
        throw new NoSuchFieldException(name);
    }

    // ---- 의도 → 엔진 ----

    void apply(JsonObject i) throws Exception {
        String method = i.get("method").getAsString();
        JsonObject p = new JsonObject();
        p.addProperty("fileId", fileId);
        // Swing 하네스처럼 그 의도의 회로를 먼저 보고(가리키는 말도 그 회로에서 찾는다), 엔진에는 circuitId로 보낸다
        Circuit on = e.onEngine(() -> {
            watchCircuits();
            return i.has("circuit") ? circuitNamed(i.get("circuit").getAsString()) : doc.project().getCurrentCircuit();
        });
        String circuitId = e.onEngine(() -> doc.ids().of(on));
        for (Map.Entry<String, JsonElement> x : i.entrySet()) {
            String k = x.getKey();
            switch (k) {
            case "method":
            case "circuit":
            case "as":
                break;
            case "target":
                p.addProperty("circuitId", e.onEngine(() -> doc.ids().of(circuitNamed(x.getValue().getAsString()))));
                break;
            case "ids": {
                JsonArray ids = new JsonArray();
                for (String id : e.onEngine(() -> resolveAll(on, x.getValue().getAsJsonArray()))) {
                    ids.add(id);
                }
                p.add("ids", ids);
                break;
            }
            case "id":
            case "wire":
                // one part or wire named as in the ids (a symbol, label:, at:, wire:)
                p.addProperty(k, e.onEngine(() -> doc.ids().of(resolve(on, x.getValue().getAsString()))));
                break;
            case "value":
                p.addProperty("value", x.getValue().getAsString());
                break;
            case "lib":
                // 의도 파일은 부품 목록에 보이는 이름(Hallym MIPS)을 쓴다: 엔진의 라이브러리 이름(model.library lib)으로
                p.addProperty("lib", e.onEngine(() -> libraryName(x.getValue().getAsString())));
                break;
            default:
                p.add(k, x.getValue());
            }
        }
        if (!p.has("circuitId")) {
            p.addProperty("circuitId", circuitId);
        }
        JsonObject r = e.client.callObject(method, p);
        if (i.has("as")) {
            String name = i.get("as").getAsString();
            String id = r.get("id").getAsString();
            e.onEngine(() -> {
                Component c = doc.ids().component(id);
                if (c == null || symbols.containsKey(name)) {
                    throw new AssertionError("symbol " + name + ": " + id);
                }
                symbols.put(name, c);
                return null;
            });
        }
    }

    Circuit circuitNamed(String name) {
        Circuit c = doc.file().getCircuit(name);
        if (c == null) {
            throw new AssertionError("no circuit " + name);
        }
        return c;
    }

    String libraryName(String name) {
        for (Library l : MipsShadow.libraries(doc.file())) {
            if (name.equals(l.getName()) || name.equals(l.getDisplayName())) {
                return l.getName();
            }
        }
        throw new AssertionError("no library " + name);
    }

    List<String> resolveAll(Circuit c, JsonArray refs) {
        List<String> out = new ArrayList<>();
        for (JsonElement r : refs) {
            String id = doc.ids().of(resolve(c, r.getAsString()));
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** Swing 하네스의 resolve와 같다: 기호, label:글, at:x,y(/부품 이름), wire:x,y, wire:x0,y0,x1,y1. */
    Component resolve(Circuit c, String ref) {
        if (ref.startsWith("label:")) {
            String want = ref.substring(6);
            return unique(ref, c.getNonWires(), comp -> {
                AttributeSet as = comp.getAttributeSet();
                return as.containsAttribute(StdAttr.LABEL) && want.equals(as.getValue(StdAttr.LABEL));
            });
        }
        if (ref.startsWith("at:")) {
            String body = ref.substring(3);
            String kind = null;
            int slash = body.indexOf('/');
            if (slash >= 0) {
                kind = body.substring(slash + 1);
                body = body.substring(0, slash);
            }
            int[] xy = numbers(body);
            Location at = Location.create(xy[0], xy[1]);
            String k = kind;
            return unique(ref, c.getNonWires(), comp -> comp.getLocation().equals(at)
                    && (k == null || comp.getFactory().getName().equals(k)));
        }
        if (ref.startsWith("wire:")) {
            int[] n = numbers(ref.substring(5));
            if (n.length == 2) {
                Location at = Location.create(n[0], n[1]);
                return unique(ref, c.getWires(), w -> ((Wire) w).contains(at));
            }
            Location a = Location.create(n[0], n[1]);
            Location b = Location.create(n[2], n[3]);
            return unique(ref, c.getWires(), w -> ((Wire) w).endsAt(a) && ((Wire) w).endsAt(b));
        }
        Component s = symbols.get(ref);
        if (s == null) {
            throw new AssertionError("unknown symbol " + ref);
        }
        Component now = follow(s);
        if (!c.contains(now)) {
            throw new AssertionError("symbol " + ref + " is not in circuit " + c.getName());
        }
        return now;
    }

    static int[] numbers(String body) {
        String[] parts = body.split(",");
        int[] out = new int[parts.length];
        for (int k = 0; k < parts.length; k++) {
            out[k] = Integer.parseInt(parts[k].trim());
        }
        return out;
    }

    static Component unique(String ref, Collection<? extends Component> in, Predicate<Component> p) {
        List<Component> hits = new ArrayList<>();
        for (Component c : in) {
            if (p.test(c)) {
                hits.add(c);
            }
        }
        if (hits.size() != 1) {
            throw new AssertionError(ref + " matches " + hits.size() + " components " + hits);
        }
        return hits.get(0);
    }

    Component follow(Component c) {
        Set<Component> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Component now = c;
        while (!inFile(now) && replaced.containsKey(now) && seen.add(now)) {
            now = replaced.get(now);
        }
        return now;
    }

    boolean inFile(Component c) {
        for (Circuit k : doc.file().getCircuits()) {
            if (k.contains(c)) {
                return true;
            }
        }
        return false;
    }

    void watchCircuits() {
        for (Circuit c : doc.file().getCircuits()) {
            if (watched.add(c)) {
                c.addCircuitListener(tracker);
            }
        }
    }

    /** Swing 하네스의 track과 같다: 바꿔치기와, 지워지고 더해진 같은 종류·같은 속성 한 쌍(엔진 스레드). */
    void track(CircuitEvent ev) {
        if (ev.getAction() != CircuitEvent.TRANSACTION_DONE) {
            return;
        }
        CircuitTransactionResult r = ev.getResult();
        ReplacementMap rm = r == null ? null : r.getReplacementMap(ev.getCircuit());
        if (rm == null) {
            return;
        }
        Set<Component> used = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Component> removedOnly = new ArrayList<>();
        for (Component old : rm.getRemovals()) {
            if (old instanceof Wire) {
                continue;
            }
            Collection<Component> news = rm.get(old);
            List<Component> same = new ArrayList<>();
            for (Component n : news == null ? Collections.<Component>emptyList() : news) {
                if (!(n instanceof Wire) && n.getFactory() == old.getFactory()) {
                    same.add(n);
                }
            }
            if (same.size() == 1) {
                replaced.put(old, same.get(0));
                used.add(same.get(0));
            } else if (news == null || news.isEmpty()) {
                removedOnly.add(old);
            }
        }
        List<Component> addedOnly = new ArrayList<>();
        for (Component n : rm.getAdditions()) {
            if (!(n instanceof Wire) && !used.contains(n) && !rm.getRemovals().contains(n)) {
                addedOnly.add(n);
            }
        }
        for (Component old : removedOnly) {
            List<Component> hits = new ArrayList<>();
            for (Component n : addedOnly) {
                if (n.getFactory() == old.getFactory() && sameAttrs(old, n)) {
                    hits.add(n);
                }
            }
            if (hits.size() == 1) {
                List<Component> back = new ArrayList<>();
                for (Component o : removedOnly) {
                    if (o.getFactory() == old.getFactory() && sameAttrs(o, hits.get(0))) {
                        back.add(o);
                    }
                }
                if (back.size() == 1) {
                    replaced.put(old, hits.get(0));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    static boolean sameAttrs(Component a, Component b) {
        AttributeSet x = a.getAttributeSet();
        AttributeSet y = b.getAttributeSet();
        if (!x.getAttributes().equals(y.getAttributes())) {
            return false;
        }
        for (Attribute<?> at : x.getAttributes()) {
            Attribute<Object> o = (Attribute<Object>) at;
            Object u = x.getValue(o);
            Object v = y.getValue(o);
            if (u == null ? v != null : v == null || !o.toStandardString(u).equals(o.toStandardString(v))) {
                return false;
            }
        }
        return true;
    }
}
