/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.edit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.libs.CircuitImport;
import kr.ac.hallym.hcs.app.libs.MipsShadow;
import kr.ac.hallym.hcs.app.libs.OpenFileLibraries;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.doc.EngineLoader;
import kr.ac.hallym.hcs.engine.edit.Intents.Result;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * 라이브러리와 다른 파일(N-11, D-153): Project › Load Library(원조 {@code ProjectLibraryActions}: Built-in·Logisim·JAR),
 * Unload Library, v1 탭 간 라이브러리(P-03, {@code OpenFileLibraries}), v1 File › Import Subcircuits…(P-05,
 * {@code CircuitImport}). 파일을 여는 곳은 원조 {@code Loader}이고 파일에 넣고 빼는 것은 원조 동작
 * ({@link LogisimFileActions})이다: 되돌리기 한 단계.
 *
 * <p>경로는 절대 경로이거나, 이 파일(.circ)의 폴더 기준 상대 경로다({@link #resolve}).
 */
public final class LibraryIntents {
    private LibraryIntents() {
    }

    /** 상대 경로는 이 파일의 폴더 기준(저장한 적 없는 파일이면 엔진의 작업 폴더 기준). */
    public static File resolve(Doc d, String path) {
        File f = new File(path);
        if (!f.isAbsolute()) {
            File main = d.loader().getMainFile();
            f = main != null ? new File(main.getAbsoluteFile().getParentFile(), path) : f.getAbsoluteFile();
        }
        return f.getAbsoluteFile();
    }

    // ---- Load Library ----

    /**
     * Project › Load Library. kind {@code builtin}: 이 파일에 없는 기본 라이브러리 name(원조 목록 창). {@code circ}:
     * Logisim 라이브러리(원조 {@code Loader.loadLogisimLibrary}; v1처럼 자기 자신 {@code self}, 순환 {@code circular}은
     * 거절, others는 엔진이 연 다른 파일들). {@code jar}: JAR 라이브러리(원조처럼 manifest의 {@code Library-Class},
     * 없으면 className). 이미 들어 있으면 {@code outcome:"already"}.
     */
    public static Result loadLibrary(Doc d, String kind, String name, File path, String className,
            Collection<Doc> others) throws RpcError {
        if (d.isReadOnly()) {
            throw RpcError.notEditable("readOnly", "the file is read-only");
        }
        switch (kind) {
        case "builtin": {
            if (name == null) {
                throw RpcError.params("a built-in library needs a name");
            }
            Library lib = d.loader().getBuiltin().getLibrary(name);
            if (lib == null) {
                throw RpcError.params("no built-in library " + name);
            }
            if (d.file().getLibraries().contains(lib)) {
                return Result.unchanged("already");
            }
            d.project().doAction(LogisimFileActions.loadLibraries(new Library[] {lib}));
            return new Result(true, null, null).with("lib", new com.google.gson.JsonPrimitive(lib.getName()));
        }
        case "circ":
            return loadCirc(d, need(path), others);
        case "jar":
            return loadJar(d, need(path), className);
        default:
            throw RpcError.params("kind must be builtin, circ or jar");
        }
    }

    private static File need(File path) throws RpcError {
        if (path == null) {
            throw RpcError.params("path is required");
        }
        return path;
    }

    private static Result loadCirc(Doc d, File f, Collection<Doc> others) throws RpcError {
        if (!f.isFile()) {
            throw RpcError.file(f.getPath(), "notFound", "no such file: " + f.getPath());
        }
        File mine = d.loader().getMainFile();
        if (mine != null && same(mine, f)) {
            throw RpcError.notEditable("self", "a file cannot use itself as a library");
        }
        if (OpenFileLibraries.loaded(d.project(), f) != null) {
            return Result.unchanged("already");
        }
        // 엔진이 연 그 파일이 이미 이 파일을 쓰면 순환이다(v1 OpenFileLibraries.candidates)
        for (Doc o : others) {
            File of = o.loader().getMainFile();
            if (o != d && of != null && same(of, f) && OpenFileLibraries.circular(mine, o.file())) {
                throw RpcError.notEditable("circular", "that file already uses this one");
            }
        }
        d.loader().drainErrors();
        OpenFileLibraries.Result r = OpenFileLibraries.ensureLoaded(d.project(), f);
        List<String> errs = d.loader().drainErrors();
        if (r.library == null) {
            if ("circular".equals(r.refused)) {
                throw RpcError.notEditable("circular", "that file already uses this one");
            }
            if ("self".equals(r.refused)) {
                throw RpcError.notEditable("self", "a file cannot use itself as a library");
            }
            throw RpcError.file(f.getPath(), "loadFailed", errs.isEmpty() ? "cannot load " + f.getPath()
                    : String.join("\n", errs));
        }
        return new Result(true, null, null).with("lib", new com.google.gson.JsonPrimitive(r.library.getName()));
    }

    private static Result loadJar(Doc d, File f, String className) throws RpcError {
        if (!f.isFile()) {
            throw RpcError.file(f.getPath(), "notFound", "no such file: " + f.getPath());
        }
        String cls = className;
        if (cls == null) {
            // 원조: manifest의 Library-Class(Christophe Jacquet의 기여), 없으면 이름을 묻는 창
            try (JarFile jar = new JarFile(f)) {
                Manifest m = jar.getManifest();
                cls = m == null ? null : m.getMainAttributes().getValue("Library-Class");
            } catch (IOException e) {
                throw RpcError.file(f.getPath(), "unreadable", "cannot read the JAR file: " + e.getMessage());
            }
        }
        if (cls == null) {
            throw reason("noLibraryClass", "the JAR file names no Library-Class: give className");
        }
        d.loader().drainErrors();
        Library lib = d.loader().loadJarLibrary(f, cls);
        List<String> errs = d.loader().drainErrors();
        if (lib == null) {
            throw RpcError.file(f.getPath(), "loadFailed", errs.isEmpty() ? "cannot load " + f.getPath()
                    : String.join("\n", errs));
        }
        if (d.file().getLibraries().contains(lib)) {
            return Result.unchanged("already");
        }
        d.project().doAction(LogisimFileActions.loadLibrary(lib));
        return new Result(true, null, null).with("lib", new com.google.gson.JsonPrimitive(lib.getName()));
    }

    /**
     * Unload Library(원조 {@code ProjectLibraryActions.doUnloadLibrary}): 이 파일의 회로가 그 라이브러리 부품을 쓰면
     * 원조 문구로 거절한다({@code inUse}, message는 원조 글).
     */
    public static Result unloadLibrary(Doc d, String name) throws RpcError {
        if (d.isReadOnly()) {
            throw RpcError.notEditable("readOnly", "the file is read-only");
        }
        Library lib = null;
        for (Library l : d.file().getLibraries()) {
            if (l.getName().equals(name) || name.equals(l.getDisplayName())) {
                lib = l;
                break;
            }
        }
        if (lib == null) {
            throw RpcError.notFound("library", name);
        }
        // 원조와 같은 물음(원조 글은 영어라 까닭과 회로 이름만 준다: 회로가 부품을 씀 inUse, 원조 도구 모음·마우스 설정이 씀 toolbar)
        String message = d.file().getUnloadLibraryMessage(lib);
        if (message != null) {
            String circuit = usedIn(d, lib);
            RpcError e = RpcError.notEditable(circuit != null ? "inUse" : "toolbar", message);
            if (circuit != null) {
                ((JsonObject) e.data()).addProperty("circuit", circuit);
            }
            throw e;
        }
        d.project().doAction(LogisimFileActions.unloadLibrary(lib));
        return new Result(true, null, null);
    }

    // ---- Import Subcircuits(v1 P-05) ----

    /** 다른 .circ를 원조 Loader로 연다(이 파일의 도구와 나누지 않는 새 Loader, D-149). */
    static LogisimFile openSource(Doc d, File f) throws RpcError {
        File mine = d.loader().getMainFile();
        if (mine != null && same(mine, f)) {
            throw RpcError.notEditable("sameFile", "that is this file");
        }
        if (!f.isFile()) {
            throw RpcError.file(f.getPath(), "notFound", "no such file: " + f.getPath());
        }
        EngineLoader loader = new EngineLoader();
        try {
            LogisimFile file = loader.openLogisimFile(f);
            if (file == null) {
                throw RpcError.file(f.getPath(), "loadFailed", String.join("\n", loader.drainErrors()));
            }
            return file;
        } catch (LoadFailedException | RuntimeException e) {
            List<String> errs = loader.drainErrors();
            throw RpcError.file(f.getPath(), "loadFailed", errs.isEmpty() ? String.valueOf(e.getMessage())
                    : String.join("\n", errs));
        }
    }

    static List<Circuit> chosen(LogisimFile source, List<String> names) throws RpcError {
        List<Circuit> out = new ArrayList<>();
        for (String n : names) {
            Circuit c = source.getCircuit(n);
            if (c == null) {
                throw RpcError.params("that file has no circuit " + n);
            }
            if (!out.contains(c)) {
                out.add(c);
            }
        }
        if (out.isEmpty()) {
            throw RpcError.params("circuits must name at least one circuit");
        }
        return out;
    }

    /**
     * File › Import Subcircuits…(v1 {@code ImportDialog}의 Apply): 고른 회로와 그 안에서 쓰는 서브회로를 이 파일의
     * 회로로 복사한다(딸린 것 먼저, 이미 있는 이름은 {@code 이름-2}, 이 파일에 없는 라이브러리의 부품은 빠짐). 한 동작.
     * 결과의 {@code plan}은 {@link #plan}과 같다.
     *
     * <p>v1은 원조 {@code new Loader(frame)}로 그 파일을 열어, 원조가 모든 파일에 나눠 쓰는 Wiring 도구 일곱 개의
     * 속성(그 파일의 {@code <lib desc="#Wiring"><tool>})이 이 파일의 도구에 남았다(편집 동등성 골든 09가 그 결과다).
     * 엔진은 파일마다 제 도구를 두므로(D-149) 그 값을 이 파일의 Wiring 도구에만 똑같이 옮긴다({@link #wiringToolsOf}).
     */
    public static Result importCircuits(Doc d, File f, List<String> names) throws RpcError {
        if (d.isReadOnly()) {
            throw RpcError.notEditable("readOnly", "the file is read-only");
        }
        LogisimFile source = openSource(d, f);
        CircuitImport.Plan p = CircuitImport.plan(d.file(), source, chosen(source, names));
        wiringToolsOf(d, f);
        d.project().doAction(CircuitImport.action(d.file(), p));
        return new Result(true, null, null).with("plan", planJson(d, p));
    }

    /** model.importPlan: 가져오기 전에 보일 계획(v1 계획 창). 모델을 바꾸지 않는다. */
    public static JsonObject plan(Doc d, File f, List<String> names) throws RpcError {
        LogisimFile source = openSource(d, f);
        return planJson(d, CircuitImport.plan(d.file(), source, chosen(source, names)));
    }

    static JsonObject planJson(Doc d, CircuitImport.Plan p) {
        JsonObject o = new JsonObject();
        JsonArray order = new JsonArray();
        for (Circuit c : p.order) {
            JsonObject x = new JsonObject();
            x.addProperty("name", c.getName());
            x.addProperty("as", p.names.get(c));
            order.add(x);
        }
        o.add("order", order);
        JsonArray skipped = new JsonArray();
        p.skipped.forEach(skipped::add);
        o.add("skipped", skipped);
        return o;
    }

    /**
     * file.peek: 가져오기 창의 목록. 그 파일의 회로들(파일 차례)과 각 회로가 안에서 쓰는 그 파일의 회로들, 주 회로.
     */
    public static JsonObject peek(Doc d, File f) throws RpcError {
        LogisimFile source = openSource(d, f);
        JsonObject o = new JsonObject();
        o.addProperty("name", f.getName());
        Circuit main = source.getMainCircuit();
        o.addProperty("main", main == null ? null : main.getName());
        JsonArray circuits = new JsonArray();
        for (Circuit c : source.getCircuits()) {
            JsonObject x = new JsonObject();
            x.addProperty("name", c.getName());
            Set<String> uses = new LinkedHashSet<>();
            for (Component comp : c.getNonWires()) {
                if (comp.getFactory() instanceof SubcircuitFactory) {
                    Circuit sub = ((SubcircuitFactory) comp.getFactory()).getSubcircuit();
                    if (source.getCircuits().contains(sub)) {
                        uses.add(sub.getName());
                    }
                }
            }
            JsonArray u = new JsonArray();
            uses.forEach(u::add);
            x.add("uses", u);
            circuits.add(x);
        }
        o.add("circuits", circuits);
        return o;
    }

    /**
     * 그 파일의 Wiring 도구 설정({@code <lib desc="#Wiring">} 안의 {@code <tool>}의 {@code <a>})을 이 파일의 같은
     * 이름 Wiring 도구에 넣는다. 원조 XmlReader가 파일을 열며 도구에 하는 일(적힌 속성만 바꾼다)과 같다.
     */
    static void wiringToolsOf(Doc d, File f) {
        Library wiring = null;
        for (Library l : d.file().getLibraries()) {
            if (d.loader().getBuiltin().getLibraries().contains(l) && l.getName().equals("Wiring")) {
                wiring = l;
            }
        }
        if (wiring == null) {
            return;
        }
        org.w3c.dom.Document doc;
        try (InputStream in = Files.newInputStream(f.toPath())) {
            DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
            fac.setExpandEntityReferences(false);
            doc = fac.newDocumentBuilder().parse(in);
        } catch (Exception e) {
            return; // 원조 로더가 이미 읽은 파일이다: 여기서 못 읽으면 옮길 것이 없다
        }
        NodeList libs = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < libs.getLength(); i++) {
            Node n = libs.item(i);
            if (!(n instanceof Element) || !((Element) n).getTagName().equals("lib")
                    || !"#Wiring".equals(((Element) n).getAttribute("desc"))) {
                continue;
            }
            NodeList tools = n.getChildNodes();
            for (int k = 0; k < tools.getLength(); k++) {
                Node t = tools.item(k);
                if (!(t instanceof Element) || !((Element) t).getTagName().equals("tool")) {
                    continue;
                }
                Tool tool = wiring.getTool(((Element) t).getAttribute("name"));
                if (tool == null) {
                    continue;
                }
                AttributeSet as = tool.getAttributeSet();
                NodeList attrs = t.getChildNodes();
                for (int j = 0; j < attrs.getLength(); j++) {
                    Node a = attrs.item(j);
                    if (!(a instanceof Element) || !((Element) a).getTagName().equals("a") || as == null) {
                        continue;
                    }
                    set(as, ((Element) a).getAttribute("name"), ((Element) a).hasAttribute("val")
                            ? ((Element) a).getAttribute("val") : a.getTextContent());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void set(AttributeSet as, String name, String value) {
        Attribute<Object> a = (Attribute<Object>) as.getAttribute(name);
        if (a == null) {
            return;
        }
        try {
            Object v = a.parse(value);
            if (v != null) {
                as.setValue(a, v);
            }
        } catch (RuntimeException e) {
            // 원조도 읽을 수 없는 값은 건너뛴다
        }
    }

    // ---- 목록(모델을 바꾸지 않는다) ----

    /**
     * model.libraries: Load Library·Unload Library 창과 탭 간 라이브러리(P-03)의 자료. builtins = 이 파일에 없는 기본
     * 라이브러리(원조 Built-in 목록 창), loaded = 이 파일의 라이브러리와 뺄 수 없는 까닭(원조 글, 없으면 null),
     * openFiles = 엔진이 연 다른 파일마다 이 파일에 넣을 수 있는지({@code ok}, 이미 넣음 {@code loaded}, 저장한 적 없음
     * {@code unsaved}, 순환 {@code circular}), mips = 번들 Hallym MIPS가 파일에 들어 있는지.
     */
    public static JsonObject libraries(Doc d, Collection<Doc> all) {
        JsonObject o = new JsonObject();
        o.addProperty("fileId", d.id());
        JsonArray builtins = new JsonArray();
        for (Library l : d.loader().getBuiltin().getLibraries()) {
            if (!d.file().getLibraries().contains(l)) {
                JsonObject x = new JsonObject();
                x.addProperty("name", l.getName());
                x.addProperty("display", l.getDisplayName());
                builtins.add(x);
            }
        }
        o.add("builtins", builtins);
        JsonArray loaded = new JsonArray();
        for (Library l : d.file().getLibraries()) {
            JsonObject x = new JsonObject();
            x.addProperty("name", l.getName());
            x.addProperty("display", l.getDisplayName());
            x.addProperty("usedIn", usedIn(d, l));
            loaded.add(x);
        }
        o.add("loaded", loaded);
        JsonArray open = new JsonArray();
        File mine = d.loader().getMainFile();
        for (Doc other : all) {
            if (other == d) {
                continue;
            }
            JsonObject x = new JsonObject();
            x.addProperty("fileId", other.id());
            File of = other.loader().getMainFile();
            String state;
            if (of == null || !of.isFile()) {
                state = "unsaved";
            } else if (mine != null && same(of, mine)) {
                state = "self";
            } else if (OpenFileLibraries.loaded(d.project(), of) != null) {
                state = "loaded";
            } else if (mine != null && OpenFileLibraries.circular(mine, other.file())) {
                state = "circular";
            } else {
                state = "ok";
            }
            x.addProperty("state", state);
            LoadedLibrary lib = of == null ? null : OpenFileLibraries.loaded(d.project(), of);
            if (lib != null) {
                x.addProperty("lib", lib.getName());
            }
            JsonArray circuits = new JsonArray();
            for (Circuit c : other.file().getCircuits()) {
                circuits.add(c.getName());
            }
            x.add("circuits", circuits);
            Circuit m = other.file().getMainCircuit();
            x.addProperty("main", m == null ? null : m.getName());
            open.add(x);
        }
        o.add("openFiles", open);
        o.addProperty("mips", MipsShadow.inFile(d.file()) != null);
        return o;
    }

    /**
     * 이 파일에서 그 라이브러리 부품을 쓰는 첫 회로(원조 {@code getUnloadLibraryMessage}의 첫 물음과 같지만 도구의
     * 팩토리를 새로 불러오지 않는다: 불러오면 저장 결과가 달라진다, D-134 7). 없으면 null.
     */
    static String usedIn(Doc d, Library lib) {
        Set<Object> factories = new java.util.HashSet<>();
        for (Tool t : lib.getTools()) {
            if (t instanceof AddTool && ((AddTool) t).getFactory(false) != null) {
                factories.add(((AddTool) t).getFactory(false));
            }
        }
        for (Circuit c : d.file().getCircuits()) {
            for (Component comp : c.getNonWires()) {
                if (factories.contains(comp.getFactory())) {
                    return c.getName();
                }
            }
        }
        return null;
    }

    static boolean same(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    private static RpcError reason(String reason, String message) {
        JsonObject data = new JsonObject();
        data.addProperty("reason", reason);
        return new RpcError(RpcError.INVALID_PARAMS, message, data);
    }
}
