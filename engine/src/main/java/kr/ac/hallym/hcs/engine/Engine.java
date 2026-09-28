/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.libs.MipsShadow;
import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.engine.diag.DiagService;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.doc.Files;
import kr.ac.hallym.hcs.engine.doc.RecoveryFiles;
import kr.ac.hallym.hcs.engine.edit.ArrangeIntents;
import kr.ac.hallym.hcs.engine.edit.ExtEdits;
import kr.ac.hallym.hcs.engine.edit.Intents;
import kr.ac.hallym.hcs.engine.edit.SelectionIntents;
import kr.ac.hallym.hcs.engine.edit.ToolParts;
import kr.ac.hallym.hcs.engine.find.Find;
import kr.ac.hallym.hcs.engine.mips.Programs;
import kr.ac.hallym.hcs.engine.model.Ids;
import kr.ac.hallym.hcs.engine.record.RecordSession;
import kr.ac.hallym.hcs.engine.record.Records;
import kr.ac.hallym.hcs.engine.rpc.Params;
import kr.ac.hallym.hcs.engine.rpc.RpcError;
import kr.ac.hallym.hcs.engine.rpc.Server;
import kr.ac.hallym.hcs.engine.sim.SimGate;
import kr.ac.hallym.hcs.engine.sim.SimSession;

/**
 * 규약(docs/engine-api.md)의 메서드를 {@link Server}에 단다. 모든 메서드는 엔진 스레드에서 차례로 돈다.
 */
public final class Engine {
    public static final String NAME = "hcs-engine";
    public static final String LOGISIM = "2.7.1";
    /** 규약 판(docs/engine-api.md 5절 "v0"). */
    public static final String API = "0";

    private final Server server;
    private final Files files = new Files();
    private final Map<String, SimSession> sims = new HashMap<>();
    /** Messages·E/X 출처(diag.*, trace.*, D-143). */
    private final DiagService diags;
    /** MIPS 프로그램(mips.*, N-16, D-147): 불러오기, 다시 불러오기, 디스어셈블, Console, 사실. */
    private Programs programs;
    /** record.*(N-14): 사이클 기록, Run Until, Registers·Memory·Instruction. */
    private final Records records;
    /** 파일·회로 단위의 알림과 파일 사이의 일(N-11, D-153): file.changed, model.appearance, model.portImpact, 저장 반영. */
    private final kr.ac.hallym.hcs.engine.edit.CircuitService circuits = new kr.ac.hallym.hcs.engine.edit.CircuitService();

    public Engine(Server server) {
        this.server = server;
        this.diags = new DiagService(server, files);
        registerEngine();
        if (Boolean.getBoolean("hcs.testHooks")) {
            // 시험용(N-19): 엔진 스레드를 ms 동안 붙잡는다(멈춘 엔진도 화면이 사라지면 시한 안에 끝나는지 본다). 배포본은 켜지 않는다
            server.register("test.block", (p, call) -> {
                try {
                    Thread.sleep((long) p.optDouble("ms", 60_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new JsonObject();
            });
        }
        registerFile();
        registerModel();
        registerMips();
        registerEdit();
        registerSim();
        records = new Records(server, files);
        registerFindAndExt();
        registerFlow();
        registerCircuits();
        registerMenus();
        server.onShutdown(this::closeAll);
        server.executor().scheduleAtFixedRate(this::frame, SimSession.FRAME_MS, SimSession.FRAME_MS,
                TimeUnit.MILLISECONDS);
    }

    public static String version() {
        String v = Engine.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    /** 열린 파일(테스트). */
    public Files files() {
        return files;
    }

    public SimSession sim(String fileId) {
        return sims.get(fileId);
    }

    private void frame() {
        for (SimSession s : sims.values()) {
            try {
                s.frame();
            } catch (Throwable t) {
                server.log("error", "sim frame: " + t, false);
            }
        }
        diags.frame();
        programs.frame();
    }

    /** 진단(테스트). */
    public DiagService diags() {
        return diags;
    }

    /** MIPS 프로그램(테스트). */
    public Programs programs() {
        return programs;
    }

    /** 파일의 기록(테스트). */
    public RecordSession record(String fileId) {
        return records.get(fileId);
    }

    private void closeAll() {
        records.closeAll();
        for (SimSession s : sims.values()) {
            s.close();
        }
        sims.clear();
        diags.closeAll();
        programs.closeAll();
        // 화면이 engine.shutdown으로 끝내면(앱 정상 종료) 복구 파일을 지우고, 화면이 사라져 끝나면(stdin 닫힘·부모 끝남)
        // 저장하지 않은 파일의 복구 파일을 써 둔다(N-19, D-152)
        files.closeAll(SHUTDOWN.equals(server.shutdownReason()));
    }

    /** 화면이 engine.shutdown으로 끝낼 때의 끝 이유({@link Server#shutdownReason}). */
    static final String SHUTDOWN = "shutdown";

    // ---- engine ----

    private void registerEngine() {
        server.register("engine.hello", (p, call) -> {
            // 다시 시작한 엔진(D-142): 앞 엔진이 화면에 준 id보다 큰 번호부터 쓴다
            if (p.has("idFloor")) {
                double floor = p.optDouble("idFloor", 0);
                if (floor < 0 || floor != Math.rint(floor) || floor > 1e15) {
                    throw RpcError.params("idFloor must be a whole number from 0");
                }
                Ids.floor((long) floor);
            }
            // 학생 파일 옆의 복구 파일을 엔진이 맡는다(앱이 켠다, N-19, D-152)
            if (p.has("recoveryFiles")) {
                files.manageRecoveryFiles(p.bool("recoveryFiles"));
            }
            JsonObject o = new JsonObject();
            o.addProperty("engine", NAME);
            o.addProperty("version", version());
            o.addProperty("logisim", LOGISIM);
            o.addProperty("java", System.getProperty("java.version"));
            o.addProperty("api", API);
            return o;
        });
        server.register("engine.shutdown", (p, call) -> {
            call.after(() -> server.requestShutdown(SHUTDOWN));
            return new JsonObject();
        });
    }

    // ---- file ----

    private void registerFile() {
        server.register("file.new", (p, call) -> {
            Doc d = files.create(restore(p));
            attach(d);
            JsonObject o = new JsonObject();
            o.addProperty("fileId", d.id());
            o.addProperty("name", d.file().getName());
            o.add("circuits", d.circuitRefs());
            o.addProperty("main", d.mainId());
            o.add("libraries", Files.libraryRefs(d));
            return o;
        });
        server.register("file.open", (p, call) -> {
            File f = new File(p.str("path")).getAbsoluteFile();
            Doc d = files.findOpen(f);
            boolean already = d != null;
            List<String> messages = new ArrayList<>();
            if (!already) {
                String recovery = p.optStr("recovery", null);
                if (recovery != null && !recovery.equals("recover") && !recovery.equals("discard")) {
                    throw RpcError.params("param 'recovery' must be \"recover\" or \"discard\"");
                }
                d = files.open(f, p.optBool("readOnly", false), messages, restore(p), "recover".equals(recovery));
                attach(d);
                if ("discard".equals(recovery)) {
                    RecoveryFiles.delete(f); // 학생이 버리기를 골랐다: 연 뒤에 지운다(열지 못하면 남는다)
                }
            }
            JsonObject o = new JsonObject();
            o.addProperty("fileId", d.id());
            o.addProperty("name", d.file().getName());
            o.add("circuits", d.circuitRefs());
            o.addProperty("main", d.mainId());
            o.add("libraries", Files.libraryRefs(d));
            JsonArray m = new JsonArray();
            messages.forEach(m::add);
            o.add("messages", m);
            if (already) {
                o.addProperty("alreadyOpen", true);
            }
            return o;
        });
        server.register("file.save", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            String path = p.optStr("path", null);
            File saved = files.save(d, path == null ? null : new File(path).getAbsoluteFile());
            // 이 파일을 라이브러리로 쓰는 다른 열린 파일에 새 버전(v1 P-03 저장 반영, D-065·D-153): 파일마다 그
            // 시뮬레이터를 세운 채 바꾸고, 알림(file.libraryUpdated)으로 창의 되살리기 저널에 edit.reloadLibrary를 남긴다
            Map<Doc, LoadedLibrary> users = kr.ac.hallym.hcs.engine.edit.CircuitService.libraryUsers(files.all(), d);
            if (!users.isEmpty()) {
                reloadLibraries(users);
                call.after(() -> libraryUpdated(users, saved));
            }
            JsonObject o = new JsonObject();
            o.addProperty("path", saved.getPath());
            o.addProperty("bytes", saved.length());
            o.addProperty("needsMipsJar", Files.needsMipsJarBeside(d, saved));
            return o;
        });
        server.register("file.close", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            SimSession s = sims.remove(d.id());
            if (s != null) {
                s.close();
            }
            diags.detach(d);
            programs.detach(d);
            records.close(d.id());
            selectionSent.remove(d.id());
            circuits.detach(d);
            files.close(d, p.optBool("keepRecovery", false));
            return new JsonObject();
        });
        // 복구 파일(N-19, D-152): 저장하지 않은 편집이 있으면 연 파일 옆 <이름>.circ.hcs-recover에 쓰고, 없으면 지운다
        server.register("file.recoverWrite", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            File written = files.recoverWrite(d);
            File target = RecoveryFiles.target(d);
            JsonObject o = new JsonObject();
            if (target == null) {
                o.add("path", JsonNull.INSTANCE);
            } else {
                o.addProperty("path", RecoveryFiles.of(target).getPath());
            }
            o.addProperty("written", written != null);
            if (written != null) {
                o.addProperty("bytes", written.length());
            }
            return o;
        });
        server.register("file.dirty", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            JsonObject o = new JsonObject();
            o.addProperty("dirty", d.isDirty());
            return o;
        });
    }

    /** file.new·file.open의 {@code restore: {fileId, circuits: {이름: id}}}(다시 시작한 엔진이 파일을 되살릴 때, D-142). */
    private static Files.Restore restore(Params p) throws RpcError {
        if (!p.has("restore")) {
            return null;
        }
        if (!p.raw().get("restore").isJsonObject()) {
            throw RpcError.params("param 'restore' must be an object");
        }
        Params r = new Params(p.raw().getAsJsonObject("restore"));
        return new Files.Restore(r.str("fileId"), r.optStringMap("circuits"));
    }

    private void attach(Doc d) {
        // 기록기가 먼저 붙어 틱을 먼저 적는다(N-14): 세션이 사이클을 세거나 N Cycles를 끝낼 때 그 틱이 기록에 있다
        RecordSession r = records.attach(d);
        SimSession s = new SimSession(d, server);
        sims.put(d.id(), s);
        records.bind(r, s);
        diags.attach(d);
        programs.attach(d);
        circuits.attach(d);
        records.ready(r); // 진단도 붙은 뒤 스텝 0(진단이 스텝 0을 본다, D-143)
    }

    // ---- model ----

    private void registerModel() {
        server.register("model.circuit", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            Circuit c = d.circuit(p.str("circuitId"));
            return d.tracker().snapshot(c);
        });
        server.register("model.library", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            JsonArray out = new JsonArray();
            JsonObject own = new JsonObject();
            own.add("lib", JsonNull.INSTANCE);
            own.addProperty("display", d.file().getName());
            JsonArray circuits = new JsonArray();
            for (AddTool t : d.file().getTools()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", t.getName());
                o.addProperty("display", t.getDisplayName());
                if (t.getFactory() instanceof SubcircuitFactory) {
                    o.addProperty("circuitId", d.ids().of(((SubcircuitFactory) t.getFactory()).getSubcircuit()));
                }
                circuits.add(o);
            }
            own.add("tools", circuits);
            out.add(own);
            List<Library> inFile = d.file().getLibraries();
            for (Library lib : MipsShadow.libraries(d.file())) {
                JsonObject o = new JsonObject();
                o.addProperty("lib", lib.getName());
                o.addProperty("display", lib.getDisplayName());
                if (!inFile.contains(lib)) {
                    o.addProperty("pending", true); // 처음 놓을 때 파일에 들어간다(V-01, D-096)
                }
                JsonArray tools = new JsonArray();
                for (Tool t : lib.getTools()) {
                    // 옛 파일을 위해서만 남긴 부품(Stack)은 새로 놓는 목록에 보이지 않는다(D-140, Kinds)
                    if (t instanceof AddTool && Kinds.offeredForNewPlacement(t.getName())) {
                        JsonObject to = new JsonObject();
                        to.addProperty("name", t.getName());
                        to.addProperty("display", t.getDisplayName());
                        tools.add(to);
                    }
                }
                o.add("tools", tools);
                out.add(o);
            }
            return out;
        });
    }

    // ---- mips ----

    private void registerMips() {
        // mips.facts(파일의 MIPS 사실, D-140·D-141, 프로그램 사실), mips.load·reload·disasm·console(N-16, D-147)
        programs = new Programs(server, files, d -> sims.get(d.id()), this::publishChanges);
    }

    // ---- edit ----

    private interface Edit {
        Intents.Result apply(Doc d, Params p) throws RpcError;
    }

    private void edit(String method, boolean needsCircuit, Edit e) {
        server.register(method, (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            if (needsCircuit) {
                d.circuit(p.str("circuitId"));
            }
            // 원조 전파와 겹치지 않게: 원조 CircuitState 청취자가 전파와 함께 쓰는 집합을 고친다(D-143)
            SimSession s = sims.get(d.id());
            int drops = d.drops();
            Intents.Result r = s == null ? e.apply(d, p) : s.quiet(() -> e.apply(d, p));
            // 고른 차례(v1 SelectionOrder, N-10): 의도 하나가 한 입력이다
            d.selectionOrder().update(d.selection().getComponents(), new Object());
            // 다른 회로의 떠 있는 선택을 내려놓았으면(Doc.show) 의도의 답과 상관없이 모델이 바뀌었다
            boolean changed = r.changed || d.drops() != drops;
            // 서브회로 핀을 바꿔 끊긴 인스턴스 연결: 되살리고 알린다(v1 P-02 InstanceBanner, N-11)
            List<JsonObject> impacts = s == null ? circuits.settle(d) : s.quiet(() -> circuits.settle(d));
            JsonObject o = new JsonObject();
            o.addProperty("changed", changed);
            if (r.outcome != null) {
                o.addProperty("outcome", r.outcome);
            }
            if (r.added != null) {
                o.addProperty("id", d.ids().of(r.added));
            }
            if (r.circuit != null) {
                o.addProperty("circuitId", d.ids().of(r.circuit));
            }
            if (r.extra != null) {
                for (Map.Entry<String, com.google.gson.JsonElement> x : r.extra.entrySet()) {
                    o.add(x.getKey(), x.getValue());
                }
            }
            if (changed || !impacts.isEmpty()) {
                d.project().getSimulator().requestPropagate(); // Canvas.completeAction과 같다
                call.after(() -> publishChanges(d));
            }
            for (JsonObject impact : impacts) {
                call.after(() -> server.notify("model.portImpact", impact));
            }
            // 고른 것이 바뀌었으면 edit.selection(N-08, D-146): 모델 알림 뒤(새로 놓인 부품의 id를 화면이 먼저 안다)
            // edit.select always tells it (the screen shows its guess at once and takes the engine's word after)
            boolean always = method.equals("edit.select");
            call.after(() -> publishSelection(d, always));
            return o;
        });
    }

    /** 파일마다 마지막으로 알린 선택(edit.selection). */
    private final Map<String, String> selectionSent = new HashMap<>();

    /**
     * edit.selection = {fileId, circuitId, ids, floating}: 원조 선택(편집 대상)이 앞에 알린 것과 다르면 보낸다. ids는
     * 회로에 있는 고른 부품·선(id 차례), floating은 붙여넣거나 복제해 아직 떠 있는 부품·선의 모습(Component·Wire JSON:
     * 회로에 없어 model.changed에 오지 않는다).
     */
    public void publishSelection(Doc d) {
        publishSelection(d, false);
    }

    /** always: 같아도 보낸다(edit.select: 화면이 누른 즉시 그린 짐작을 엔진의 선택으로 바로잡는다). */
    public void publishSelection(Doc d, boolean always) {
        if (!files.all().contains(d)) {
            return; // 닫혔다
        }
        JsonObject o = selectionJson(d);
        String text = o.toString();
        if (!always && text.equals(selectionSent.get(d.id()))) {
            return;
        }
        selectionSent.put(d.id(), text);
        server.notify("edit.selection", o);
    }

    private static JsonObject selectionJson(Doc d) {
        JsonObject o = new JsonObject();
        o.addProperty("fileId", d.id());
        Circuit c = d.selectionCircuit(); // 선택이 생긴 회로(시뮬레이션이 다른 회로를 보고 있어도)
        o.addProperty("circuitId", c == null ? null : d.ids().of(c));
        List<String> ids = new ArrayList<>();
        for (Component x : kr.ac.hallym.hcs.engine.edit.SelectionIntents.anchored(d)) {
            ids.add(d.ids().of(x));
        }
        ids.sort(Engine::byNumber);
        JsonArray a = new JsonArray();
        ids.forEach(a::add);
        o.add("ids", a);
        List<JsonObject> floating = new ArrayList<>();
        for (Component x : kr.ac.hallym.hcs.engine.edit.SelectionIntents.floating(d)) {
            floating.add(x instanceof com.cburch.logisim.circuit.Wire ? d.json().wire((com.cburch.logisim.circuit.Wire) x)
                    : d.json().component(x));
        }
        floating.sort((x, y) -> byNumber(x.get("id").getAsString(), y.get("id").getAsString()));
        JsonArray f = new JsonArray();
        floating.forEach(f::add);
        o.add("floating", f);
        return o;
    }

    /** "k12" < "k101"(글자가 아니라 번호로), 부품 뒤에 선. */
    private static int byNumber(String a, String b) {
        if (a.charAt(0) != b.charAt(0)) {
            return Character.compare(a.charAt(0), b.charAt(0));
        }
        return Long.compare(Long.parseLong(a.substring(1)), Long.parseLong(b.substring(1)));
    }

    /** 바뀐 회로마다 model.changed를 보낸다(응답 뒤). */
    public void publishChanges(Doc d) {
        for (JsonObject change : d.tracker().changes(d.id(), d.isDirty())) {
            server.notify("model.changed", change);
        }
        // 회로 목록·주 회로·라이브러리, 열어 둔 모양(N-11)
        for (Object[] n : circuits.changes(d)) {
            server.notify((String) n[0], (JsonObject) n[1]);
        }
        SimSession s = sims.get(d.id());
        if (s != null) {
            s.modelChanged();
        }
        diags.modelChanged(d);
    }

    private void registerEdit() {
        edit("edit.addComponent", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            int[] at = p.point("loc");
            return Intents.addComponent(d, c, p.optStr("lib", null), p.str("name"), Location.create(at[0], at[1]),
                    p.optStringMap("attrs"));
        });
        edit("edit.addWire", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            List<Location> pts = new ArrayList<>();
            for (int[] xy : p.points("points")) {
                pts.add(Location.create(xy[0], xy[1]));
            }
            String tool = p.optStr("tool", "wiring");
            if (!tool.equals("wiring") && !tool.equals("edit")) {
                throw RpcError.params("tool must be wiring or edit");
            }
            return Intents.addWire(d, c, pts, tool.equals("wiring"));
        });
        // 고른 것에 하는 편집(N-08, D-146): ids를 주면 그것을 고른 뒤, 빼면 지금 고른 것에
        edit("edit.select", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            int[] at = p.optPoint("at");
            if (at != null) {
                // Edit 도구로 누름(SelectTool.mousePressed): 답의 outcome이 끌기의 뜻("moving"·"rect")
                return SelectionIntents.press(d, c, Location.create(at[0], at[1]), p.optBool("toggle", false));
            }
            List<Component> ids = p.has("ids") ? d.components(c, p.strings("ids")) : null;
            int[] rect = null;
            if (p.has("rect")) {
                List<String> r = new ArrayList<>();
                for (com.google.gson.JsonElement e : p.raw().getAsJsonArray("rect")) {
                    r.add(e.getAsString());
                }
                if (r.size() != 4) {
                    throw RpcError.params("rect must be [x0, y0, x1, y1]");
                }
                rect = new int[4];
                for (int i = 0; i < 4; i++) {
                    rect[i] = (int) Math.floor(Double.parseDouble(r.get(i)));
                }
            }
            return SelectionIntents.select(d, c, ids, rect, p.optBool("add", false), p.optBool("toggle", false),
                    p.optStr("filter", null), p.optBool("all", false));
        });
        edit("edit.move", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            List<Component> comps = p.has("ids") ? d.components(c, p.strings("ids")) : null;
            return SelectionIntents.move(d, c, comps, p.integer("dx"), p.integer("dy"), p.optBool("connect", true));
        });
        edit("edit.delete", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return SelectionIntents.delete(d, c, p.has("ids") ? d.components(c, p.strings("ids")) : null);
        });
        edit("edit.copy", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return SelectionIntents.copy(d, c, p.has("ids") ? d.components(c, p.strings("ids")) : null);
        });
        edit("edit.cut", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return SelectionIntents.cut(d, c, p.has("ids") ? d.components(c, p.strings("ids")) : null);
        });
        edit("edit.paste", true, (d, p) -> SelectionIntents.paste(d, d.circuit(p.str("circuitId"))));
        edit("edit.duplicate", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return SelectionIntents.duplicate(d, c, p.has("ids") ? d.components(c, p.strings("ids")) : null);
        });
        edit("edit.setAttr", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            if (p.optBool("keepSelection", false) && p.has("ids")) {
                // 우클릭 메뉴의 한 부품(N-10, v1 EditMenus: 선택은 그대로, 원조 "Change Attribute" 한 단계)
                return Intents.setAttr(d, c, d.components(c, p.strings("ids")), p.str("attr"), p.str("value"));
            }
            return SelectionIntents.setAttr(d, c, p.has("ids") ? d.components(c, p.strings("ids")) : null,
                    p.str("attr"), p.str("value"));
        });
        edit("edit.rotate", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return SelectionIntents.rotate(d, c, p.has("ids") ? d.components(c, p.strings("ids")) : null,
                    p.optBool("clockwise", true));
        });
        edit("edit.keyConfig", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            com.cburch.logisim.tools.Tool tool = p.has("name")
                    ? SelectionIntents.findTool(d, p.optStr("lib", null), p.str("name")) : null;
            return SelectionIntents.keyConfig(d, c, tool, p.str("key"), p.optBool("alt", false),
                    p.optBool("chain", false));
        });
        edit("edit.setToolAttr", false, (d, p) -> SelectionIntents.setToolAttr(d, p.optStr("lib", null), p.str("name"),
                p.str("attr"), p.str("value")));
        edit("edit.text", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            Component comp = p.has("id") ? d.component(c, p.str("id")) : null;
            int[] at = p.optPoint("loc");
            return SelectionIntents.text(d, c, comp, at == null ? null : Location.create(at[0], at[1]),
                    p.optStr("text", ""));
        });
        // 편집 동등성(N-09)이 쓰는 나머지 가벼운 의도: v1 Duplicate N·Align·Distribute, 회로 속성, 회로 더하기, 주 회로
        edit("edit.duplicateN", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return ArrangeIntents.duplicateN(d, c, p.has("ids") ? d.components(c, p.strings("ids")) : null,
                    p.integer("count"), p.optStr("direction", "down"), p.has("spacing") ? p.integer("spacing") : null,
                    p.has("number") ? p.bool("number") : null);
        });
        edit("edit.align", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return ArrangeIntents.align(d, c, p.has("ids") ? d.components(c, p.strings("ids")) : null, p.str("mode"));
        });
        edit("edit.distribute", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return ArrangeIntents.distribute(d, c, p.has("ids") ? d.components(c, p.strings("ids")) : null,
                    p.str("axis"));
        });
        edit("edit.setCircuitAttr", true, (d, p) -> ArrangeIntents.setCircuitAttr(d, d.circuit(p.str("circuitId")),
                p.str("attr"), p.str("value")));
        edit("edit.createCircuit", false, (d, p) -> ArrangeIntents.createCircuit(d, p.str("name")));
        edit("edit.setMainCircuit", true, (d, p) -> ArrangeIntents.setMainCircuit(d, d.circuit(p.str("circuitId"))));
        // 화면이 보던 회로(circuitId, 화면은 늘 보낸다)를 먼저 편집하는 회로로 둔다: 저널에 적혀 재생이 같다(D-146)
        edit("edit.undo", false, (d, p) -> Intents.undo(d, p.has("circuitId") ? d.circuit(p.str("circuitId")) : null));
        edit("edit.redo", false, (d, p) -> Intents.redo(d, p.has("circuitId") ? d.circuit(p.str("circuitId")) : null));
        // 모델을 바꾸지 않는 물음(model.*): 놓을 부품의 모습, 끄는 동안의 연결 유지 선
        server.register("model.tool", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            int[] at = p.optPoint("loc");
            return ToolParts.ghost(d, p.optStr("lib", null), p.str("name"), at == null ? Location.create(0, 0)
                    : Location.create(at[0], at[1]), p.optStringMap("attrs"));
        });
        server.register("model.textAt", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            Circuit c = d.circuit(p.str("circuitId"));
            int[] at = p.point("loc");
            return ToolParts.textAt(d, c, Location.create(at[0], at[1]));
        });
        server.register("model.movePreview", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            Circuit c = d.circuit(p.str("circuitId"));
            return new com.google.gson.Gson().toJsonTree(SelectionIntents.movePreview(d, c, p.integer("dx"),
                    p.integer("dy"), p.optBool("connect", true)));
        });
    }

    // ---- find, 터널 색, Splitter 편집기(N-12, D-150) ----

    private void registerFindAndExt() {
        server.register("find.query", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            int limit = p.optInt("limit", Find.LIMIT);
            if (limit < 1) {
                throw RpcError.params("limit must be at least 1");
            }
            return Find.query(d, p.str("text"), limit);
        });
        edit("edit.tunnelColor", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return ExtEdits.tunnelColor(d, c, d.component(c, p.str("id")), p.optStr("color", null));
        });
        edit("edit.splitterEdit", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return ExtEdits.splitterEdit(d, c, d.component(c, p.str("id")), p.str("ranges"),
                    p.has("names") ? p.strings("names") : null, p.optBool("lsbTop", false));
        });
        edit("edit.splitterSplit", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            Component w = d.component(c, p.str("wire"));
            if (!(w instanceof Wire)) {
                throw RpcError.params("component " + p.str("wire") + " is not a wire");
            }
            int[] at = p.point("at");
            return ExtEdits.splitterSplit(d, c, (Wire) w, Location.create(at[0], at[1]), p.str("ranges"),
                    p.has("names") ? p.strings("names") : null, p.optBool("lsbTop", false));
        });
    }

    // ---- 캔버스 덧그림(N-15, D-151) ----

    /**
     * 영향 경로·넷 정보·Signal Flow·활성 경로의 자료(trace.influence, trace.net, flow.path, flow.activePath)와 학생이 두는
     * 표시 정보의 편집 의도(edit.signalGroup, edit.areaMemo: hcs:ext, 되돌리기 한 단계).
     */
    private void registerFlow() {
        kr.ac.hallym.hcs.engine.flow.FlowService.register(server, files, d -> sims.get(d.id()));
        edit("edit.signalGroup", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return kr.ac.hallym.hcs.engine.edit.ExtIntents.signalGroup(d, c, d.component(c, p.str("wire")),
                    p.optStr("group", null));
        });
        edit("edit.areaMemo", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            kr.ac.hallym.hcs.engine.edit.ExtIntents.Memo m = new kr.ac.hallym.hcs.engine.edit.ExtIntents.Memo();
            int[] at = p.point("at");
            m.at = Location.create(at[0], at[1]);
            if (p.has("ids")) {
                m.around = d.components(c, p.strings("ids"));
            }
            m.text = p.optStr("text", null);
            m.color = p.has("color") ? p.integer("color") : null;
            m.bounds = p.has("bounds") ? p.ints("bounds") : null;
            m.delete = p.optBool("delete", false);
            return kr.ac.hallym.hcs.engine.edit.ExtIntents.areaMemo(d, c, m);
        });
    }

    // ---- 회로·모양·라이브러리·다른 파일(N-11, D-153) ----

    private void registerCircuits() {
        edit("edit.deleteCircuit", true, (d, p) -> kr.ac.hallym.hcs.engine.edit.CircuitIntents.deleteCircuit(d,
                d.circuit(p.str("circuitId"))));
        edit("edit.moveCircuit", true, (d, p) -> kr.ac.hallym.hcs.engine.edit.CircuitIntents.moveCircuit(d,
                d.circuit(p.str("circuitId")), p.integer("to")));
        edit("edit.portOrder", true, (d, p) -> {
            if (!p.has("order") || !p.raw().get("order").isJsonObject()) {
                throw RpcError.params("param 'order' must be an object of sides");
            }
            return kr.ac.hallym.hcs.engine.edit.CircuitIntents.portOrder(d, d.circuit(p.str("circuitId")),
                    p.raw().getAsJsonObject("order"), p.optBool("confirm", true));
        });
        edit("edit.autoAppearance", true, (d, p) -> kr.ac.hallym.hcs.engine.edit.CircuitIntents.autoAppearance(d,
                d.circuit(p.str("circuitId")), p.optBool("confirm", true)));
        edit("edit.appearance", true, (d, p) -> kr.ac.hallym.hcs.engine.edit.AppearanceIntents.apply(d,
                d.circuit(p.str("circuitId")), p));
        edit("edit.importCircuits", false, (d, p) -> kr.ac.hallym.hcs.engine.edit.LibraryIntents.importCircuits(d,
                kr.ac.hallym.hcs.engine.edit.LibraryIntents.resolve(d, p.str("path")), p.strings("circuits")));
        edit("edit.loadLibrary", false, (d, p) -> kr.ac.hallym.hcs.engine.edit.LibraryIntents.loadLibrary(d,
                p.str("kind"), p.optStr("name", null),
                p.has("path") ? kr.ac.hallym.hcs.engine.edit.LibraryIntents.resolve(d, p.str("path")) : null,
                p.optStr("className", null), files.all()));
        // 다른 파일에서 저장한 라이브러리의 새 버전(file.save가 한 일): 창의 되살리기 저널이 file.libraryUpdated를 이
        // 의도로 적어 재생한다(D-153). 이 라이브러리를 쓰는 열린 파일 모두가 새 버전을 받고, 이 파일의 답이 편집이다
        edit("edit.reloadLibrary", false, (d, p) -> {
            LoadedLibrary lib = kr.ac.hallym.hcs.engine.edit.CircuitService.circLibrary(d, p.str("lib"));
            if (lib == null) {
                throw RpcError.notFound("library", p.str("lib"));
            }
            Map<Doc, LoadedLibrary> users = new java.util.LinkedHashMap<>();
            users.put(d, lib);
            for (Doc o : files.all()) {
                if (o != d && kr.ac.hallym.hcs.engine.edit.CircuitService.circLibrary(o, lib.getName()) == lib) {
                    users.put(o, lib);
                }
            }
            List<Doc> changed = reloadLibraries(users);
            for (Doc o : changed) {
                if (o != d) {
                    publishChanges(o);
                }
            }
            SimSession s = sims.get(d.id());
            if (s != null && changed.contains(d) && s.reset()) {
                s.sendState(true);
            }
            return kr.ac.hallym.hcs.engine.edit.CircuitService.reloaded(changed.contains(d));
        });
        edit("edit.unloadLibrary", false, (d, p) -> kr.ac.hallym.hcs.engine.edit.LibraryIntents.unloadLibrary(d,
                p.str("name")));
        // 물음(모델을 바꾸지 않는다)
        server.register("model.ports", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            return kr.ac.hallym.hcs.engine.edit.CircuitIntents.ports(d, d.circuit(p.str("circuitId")));
        });
        server.register("model.instances", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            return kr.ac.hallym.hcs.engine.edit.CircuitIntents.instances(d, d.circuit(p.str("circuitId")));
        });
        server.register("model.pinImpact", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            Circuit c = d.circuit(p.str("circuitId"));
            return kr.ac.hallym.hcs.engine.edit.CircuitIntents.pinImpact(d, c, d.components(c, p.strings("ids")));
        });
        server.register("model.appearance", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            return circuits.watchAppearance(d, d.circuit(p.str("circuitId")));
        });
        server.register("model.appearanceHit", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            Circuit c = d.circuit(p.str("circuitId"));
            List<Integer> sel = kr.ac.hallym.hcs.engine.edit.AppearanceIntents.indices(p, "selected");
            int[] rect = p.has("rect") ? p.ints("rect") : null;
            if (rect != null && rect.length != 4) {
                throw RpcError.params("rect must be [x0, y0, x1, y1]");
            }
            return kr.ac.hallym.hcs.engine.edit.AppearanceIntents.hit(d, c, p.optPoint("at"), sel,
                    p.optDouble("zoom", 1), rect);
        });
        server.register("model.appearanceMenu", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            List<Integer> sel = kr.ac.hallym.hcs.engine.edit.AppearanceIntents.indices(p, "shapes");
            return kr.ac.hallym.hcs.engine.edit.AppearanceIntents.menu(d, d.circuit(p.str("circuitId")), sel,
                    p.has("vertexShape") ? p.integer("vertexShape") : null, p.optPoint("vertexAt"));
        });
        server.register("model.appearanceHandles", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            return kr.ac.hallym.hcs.engine.edit.AppearanceIntents.preview(d, d.circuit(p.str("circuitId")),
                    p.integer("shape"), p.point("at"), p.integer("dx"), p.integer("dy"), p.optBool("shift", false),
                    p.optBool("ctrl", false), p.optBool("alt", false));
        });
        server.register("model.libraries", (p, call) -> kr.ac.hallym.hcs.engine.edit.LibraryIntents.libraries(
                files.get(p.str("fileId")), files.all()));
        server.register("model.importPlan", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            return kr.ac.hallym.hcs.engine.edit.LibraryIntents.plan(d,
                    kr.ac.hallym.hcs.engine.edit.LibraryIntents.resolve(d, p.str("path")), p.strings("circuits"));
        });
        server.register("file.peek", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            return kr.ac.hallym.hcs.engine.edit.LibraryIntents.peek(d,
                    kr.ac.hallym.hcs.engine.edit.LibraryIntents.resolve(d, p.str("path")));
        });
        server.register("file.info", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            JsonObject o = kr.ac.hallym.hcs.engine.edit.CircuitService.fileJson(d);
            o.addProperty("dirty", d.isDirty());
            File main = d.loader().getMainFile();
            o.addProperty("saved", main != null);
            o.addProperty("readOnly", d.isReadOnly());
            return o;
        });
        server.register("file.saveImpact", (p, call) -> {
            JsonObject o = new JsonObject();
            o.add("cuts", kr.ac.hallym.hcs.engine.edit.CircuitService.saveImpact(files.all(),
                    files.get(p.str("fileId"))));
            return o;
        });
        server.register("file.originOf", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            Circuit c = d.circuit(p.str("circuitId"));
            File f = kr.ac.hallym.hcs.engine.edit.CircuitService.originOf(d, c);
            JsonObject o = new JsonObject();
            o.addProperty("path", f == null ? null : f.getPath());
            o.addProperty("circuit", c.getName());
            return o;
        });
        server.register("file.copyMipsJar", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            File main = d.loader().getMainFile();
            if (main == null) {
                throw RpcError.params("the file was never saved");
            }
            try {
                File dest = MipsShadow.copyJarBeside(main);
                JsonObject o = new JsonObject();
                o.addProperty("name", dest.getName());
                return o;
            } catch (java.io.IOException e) {
                throw RpcError.file(MipsShadow.siblingJar(main).getPath(), "writeFailed", e.getMessage());
            }
        });
    }

    /**
     * 저장한 파일을 라이브러리로 쓰던 파일들이 새 버전을 받은 뒤(v1 LibrarySync.afterSave): 모델 알림, 시뮬레이션을
     * 처음으로(v1 Recorder.requestReset), {@code file.libraryUpdated {fileId, library}}(화면의 " · Updated").
     */
    private void libraryUpdated(Map<Doc, LoadedLibrary> users, File saved) {
        for (Map.Entry<Doc, LoadedLibrary> u : users.entrySet()) {
            Doc t = u.getKey();
            if (!files.all().contains(t)) {
                continue;
            }
            publishChanges(t);
            SimSession s = sims.get(t.id());
            if (s != null && s.reset()) {
                s.sendState(true);
            }
            JsonObject o = new JsonObject();
            o.addProperty("fileId", t.id());
            o.addProperty("library", saved.getName());
            o.addProperty("lib", u.getValue().getName()); // 이 파일에서의 라이브러리 이름: 저널의 edit.reloadLibrary
            server.notify("file.libraryUpdated", o);
        }
    }

    /**
     * 라이브러리 새 버전을 쓰는 파일들에 넣는다(D-153): 라이브러리마다 한 번 디스크에서 다시 읽고(그 라이브러리를 쓰는
     * 모든 파일의 시뮬레이터를 세운 채: 나눠 쓰는 LoadedLibrary라 모두의 부품 팩토리가 바뀐다), 파일마다 그 파일의
     * 시뮬레이터를 세운 채 옛 버전 부품을 바꾼다. 바뀐 파일들.
     */
    private List<Doc> reloadLibraries(Map<Doc, LoadedLibrary> users) {
        Map<LoadedLibrary, Map<ComponentFactory, ComponentFactory>> known = new IdentityHashMap<>();
        for (Map.Entry<Doc, LoadedLibrary> u : users.entrySet()) {
            LoadedLibrary lib = u.getValue();
            if (known.containsKey(lib)) {
                continue;
            }
            List<SimSession> quiet = new ArrayList<>();
            for (Map.Entry<Doc, LoadedLibrary> v : users.entrySet()) {
                SimSession s = sims.get(v.getKey().id());
                if (v.getValue() == lib && s != null) {
                    quiet.add(s);
                }
            }
            known.put(lib, quietAll(quiet, 0, () -> kr.ac.hallym.hcs.engine.edit.CircuitService.reload(u.getKey(), lib)));
        }
        List<Doc> changed = new ArrayList<>();
        for (Map.Entry<Doc, LoadedLibrary> u : users.entrySet()) {
            Doc t = u.getKey();
            SimSession s = sims.get(t.id());
            SimGate.Body<Boolean, RuntimeException> body =
                    () -> kr.ac.hallym.hcs.engine.edit.CircuitService.refresh(t, u.getValue(), known.get(u.getValue()));
            if (s == null ? body.run() : s.quiet(body)) {
                changed.add(t);
            }
        }
        return changed;
    }

    private static <T> T quietAll(List<SimSession> ss, int i, SimGate.Body<T, RuntimeException> body) {
        return i == ss.size() ? body.run() : ss.get(i).quiet(() -> quietAll(ss, i + 1, body));
    }

    // ---- 속성 표·우클릭 메뉴·RAM·ROM 내용(N-10, D-157) ----

    private void registerMenus() {
        // 속성 표(model.attributes): 든 도구(lib·name), 회로(circuit:true), 아니면 고른 것(ids 또는 엔진의 선택,
        // 없으면 회로 속성: 원조 AttrTableSelectionModel)
        server.register("model.attributes", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            if (p.has("name")) {
                String lib = p.optStr("lib", null);
                return kr.ac.hallym.hcs.engine.model.AttrTable.tool(d, lib,
                        SelectionIntents.findTool(d, lib, p.str("name")));
            }
            Circuit c = d.circuit(p.str("circuitId"));
            if (p.optBool("circuit", false)) {
                return kr.ac.hallym.hcs.engine.model.AttrTable.circuit(d, c);
            }
            java.util.Collection<Component> chosen;
            if (p.has("ids")) {
                chosen = d.components(c, p.strings("ids"));
            } else if (d.selectionCircuit() == c) {
                chosen = d.selection().getComponents();
            } else {
                chosen = java.util.Collections.emptyList();
            }
            return kr.ac.hallym.hcs.engine.model.AttrTable.selection(d, c, chosen);
        });
        // 우클릭 메뉴의 사실(model.menu): 항목은 화면의 메뉴 등록표가 정한다
        server.register("model.menu", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            Circuit c = d.circuit(p.str("circuitId"));
            int[] at = p.point("at");
            Component hit = p.has("id") ? d.component(c, p.str("id")) : null;
            return kr.ac.hallym.hcs.engine.edit.MenuFacts.at(d, c, Location.create(at[0], at[1]), hit);
        });
        edit("edit.labels", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            Map<Component, String> labels = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, String> e : p.optStringMap("labels").entrySet()) {
                labels.put(d.component(c, e.getKey()), e.getValue());
            }
            return kr.ac.hallym.hcs.engine.edit.MenuIntents.labels(d, c, labels);
        });
        edit("edit.attach", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return kr.ac.hallym.hcs.engine.edit.MenuIntents.attach(d, c, d.component(c, p.str("id")), p.integer("port"),
                    p.str("what"));
        });
        edit("edit.swapGate", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return kr.ac.hallym.hcs.engine.edit.MenuIntents.swapGate(d, c, d.component(c, p.str("id")), p.str("to"));
        });
        edit("edit.deleteNet", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return kr.ac.hallym.hcs.engine.edit.MenuIntents.deleteNet(d, c, wire(d, c, p.str("wire")));
        });
        edit("edit.wireToTunnels", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return kr.ac.hallym.hcs.engine.edit.MenuIntents.wireToTunnels(d, c, wire(d, c, p.str("wire")),
                    p.str("label"));
        });
        edit("edit.probe", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            int[] at = p.point("at");
            return kr.ac.hallym.hcs.engine.edit.MenuIntents.probe(d, c, wire(d, c, p.str("wire")),
                    Location.create(at[0], at[1]), p.optStr("radix", null));
        });
        edit("edit.deleteProbes", true, (d, p) -> kr.ac.hallym.hcs.engine.edit.MenuIntents.deleteProbes(d,
                d.circuit(p.str("circuitId"))));
        edit("edit.combineBus", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return kr.ac.hallym.hcs.engine.edit.MenuIntents.combineBus(d, c, d.components(c, p.strings("ids")));
        });
        edit("edit.originalItem", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return kr.ac.hallym.hcs.engine.edit.MenuIntents.originalItem(d, c, d.component(c, p.str("id")),
                    p.integer("index"));
        });
        // ROM 내용(Contents 속성, .circ에 저장): 원조 "Edit ROM Contents" 동작, 되돌리기
        edit("edit.memContents", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return kr.ac.hallym.hcs.engine.edit.MenuIntents.memContents(d, c, d.component(c, p.str("id")),
                    p.has("addr") ? Long.valueOf((long) p.optDouble("addr", 0)) : null,
                    p.has("values") ? longs(p, "values") : null, p.optBool("clear", false), p.optStr("file", null));
        });
        // RAM 내용(시뮬레이션 상태: .circ에 남지 않고 되돌리기에 들지 않는다, 원조 HexFrame)
        server.register("mem.read", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            Circuit root = d.circuit(p.str("circuitId"));
            List<Component> path = instancePath(d, root, p);
            Component x = memory(d, p);
            SimSession s = sims.get(d.id());
            long from = (long) p.optDouble("from", 0);
            int count = p.optInt("count", 256);
            return s == null ? kr.ac.hallym.hcs.engine.sim.Memories.read(d, root, path, x, from, count)
                    : s.quiet(() -> kr.ac.hallym.hcs.engine.sim.Memories.read(d, root, path, x, from, count));
        });
        server.register("mem.write", (p, call) -> memState(p, (d, root, path, x) -> {
            kr.ac.hallym.hcs.engine.sim.Memories.writeRam(d, root, path, x, (long) p.optDouble("addr", -1),
                    longs(p, "values"));
            return true;
        }));
        server.register("mem.clear", (p, call) -> memState(p, (d, root, path, x) ->
                kr.ac.hallym.hcs.engine.sim.Memories.clearRam(d, root, path, x)));
        server.register("mem.loadImage", (p, call) -> memState(p, (d, root, path, x) -> {
            kr.ac.hallym.hcs.engine.sim.Memories.loadRam(d, root, path, x, p.str("file"));
            return true;
        }));
        server.register("mem.saveImage", (p, call) -> {
            Doc d = files.get(p.str("fileId"));
            Circuit root = d.circuit(p.str("circuitId"));
            List<Component> path = instancePath(d, root, p);
            Component x = memory(d, p);
            SimSession s = sims.get(d.id());
            if (s == null) {
                kr.ac.hallym.hcs.engine.sim.Memories.save(d, root, path, x, p.str("file"));
            } else {
                s.quiet(() -> {
                    kr.ac.hallym.hcs.engine.sim.Memories.save(d, root, path, x, p.str("file"));
                    return null;
                });
            }
            return new JsonObject();
        });
    }

    private interface MemBody {
        boolean apply(Doc d, Circuit root, List<Component> path, Component x) throws RpcError;
    }

    /** RAM 상태를 고치는 mem.*: 원조 전파와 겹치지 않게(SimGate), 고친 뒤 다시 전파하고 값을 보낸다. result {changed}. */
    private JsonObject memState(Params p, MemBody body) throws RpcError {
        Doc d = files.get(p.str("fileId"));
        Circuit root = d.circuit(p.str("circuitId"));
        List<Component> path = instancePath(d, root, p);
        Component x = memory(d, p);
        SimSession s = sims.get(d.id());
        boolean changed = s == null ? body.apply(d, root, path, x) : s.quiet(() -> body.apply(d, root, path, x));
        if (changed && s != null) {
            s.stateEdited();
        }
        JsonObject o = new JsonObject();
        o.addProperty("changed", changed);
        return o;
    }

    private static Component memory(Doc d, Params p) throws RpcError {
        Component x = d.ids().component(p.str("componentId"));
        if (x == null) {
            throw RpcError.notFound("component", p.str("componentId"));
        }
        return x;
    }

    private static List<Component> instancePath(Doc d, Circuit root, Params p) throws RpcError {
        List<Component> path = new ArrayList<>();
        Circuit cur = root;
        for (String id : p.optStrings("path")) {
            Component inst = d.ids().component(id);
            if (inst == null || !cur.contains(inst) || !(inst.getFactory() instanceof SubcircuitFactory)) {
                throw RpcError.notFound("instance", id);
            }
            path.add(inst);
            cur = ((SubcircuitFactory) inst.getFactory()).getSubcircuit();
        }
        return path;
    }

    private static List<Long> longs(Params p, String name) throws RpcError {
        List<Long> out = new ArrayList<>();
        com.google.gson.JsonElement e = p.raw().get(name);
        if (e == null || !e.isJsonArray()) {
            throw RpcError.params(name + " must be a list of numbers");
        }
        for (com.google.gson.JsonElement x : e.getAsJsonArray()) {
            try {
                out.add(x.getAsLong());
            } catch (RuntimeException ex) {
                throw RpcError.params(name + " must be a list of numbers");
            }
        }
        return out;
    }

    private static Wire wire(Doc d, Circuit c, String id) throws RpcError {
        Component w = d.component(c, id);
        if (!(w instanceof Wire)) {
            throw RpcError.params("component " + id + " is not a wire");
        }
        return (Wire) w;
    }

    // ---- sim ----

    private void registerSim() {
        server.register("sim.reset", (p, call) -> {
            SimSession s = session(p);
            programs.beforeReset(files.get(p.str("fileId"))); // 바뀐 .hmx를 먼저 다시 넣는다(PLAN.md 6.8, D-147)
            if (s.reset()) {
                call.after(() -> s.sendState(true));
            }
            return new JsonObject();
        });
        server.register("sim.poke", (p, call) -> {
            SimSession s = session(p);
            Doc d = files.get(p.str("fileId"));
            Circuit c = d.circuit(p.str("circuitId"));
            Component comp = d.component(c, p.str("componentId"));
            int[] at = p.optPoint("at");
            // Poke도 원조 CircuitState를 고친다: 원조 전파와 겹치지 않게(D-143)
            boolean poked = s.quiet(() -> s.poke(c, comp, at == null ? null : Location.create(at[0], at[1]),
                    p.optStr("action", "click")));
            JsonObject o = new JsonObject();
            o.addProperty("poked", poked);
            o.addProperty("caret", s.hasCaret(comp));
            return o;
        });
        server.register("sim.pokeKey", (p, call) -> {
            JsonObject o = new JsonObject();
            o.addProperty("poked", session(p).pokeKey(p.str("key")));
            return o;
        });
        server.register("sim.pokeStop", (p, call) -> {
            session(p).dropCaret();
            return new JsonObject();
        });
        server.register("sim.cycles", (p, call) -> {
            SimSession s = session(p);
            s.cycles(p.integer("n"));
            call.after(() -> s.sendState(false));
            return new JsonObject();
        });
        server.register("sim.tick", (p, call) -> {
            SimSession s = session(p);
            s.tickOnce();
            call.after(() -> s.sendState(false));
            return new JsonObject();
        });
        server.register("sim.step", (p, call) -> {
            session(p).step();
            return new JsonObject();
        });
        server.register("sim.run", (p, call) -> {
            SimSession s = session(p);
            s.run(p.bool("on"), p.has("hz") ? p.optDouble("hz", 0) : null);
            call.after(() -> s.sendState(false));
            return new JsonObject();
        });
        server.register("sim.enable", (p, call) -> {
            SimSession s = session(p);
            s.enable(p.bool("on"));
            call.after(() -> s.sendState(false));
            return new JsonObject();
        });
        server.register("sim.watch", (p, call) -> {
            SimSession s = session(p);
            Doc d = files.get(p.str("fileId"));
            Circuit root = d.circuit(p.str("circuitId"));
            List<Component> path = new ArrayList<>();
            Circuit cur = root;
            for (String id : p.optStrings("path")) {
                Component inst = d.ids().component(id);
                if (inst == null || !cur.contains(inst) || !(inst.getFactory() instanceof SubcircuitFactory)) {
                    throw RpcError.notFound("instance", id);
                }
                path.add(inst);
                cur = ((SubcircuitFactory) inst.getFactory()).getSubcircuit();
            }
            s.watch(root, path);
            // 보는 회로만 바뀐다: 모델도 선택도 그대로(D-146 V2; 화면이 바꾸기 전에 edit.select로 내려놓는다)
            return new JsonObject();
        });
        server.register("sim.pinValue", (p, call) -> {
            SimSession s = session(p);
            Doc d = files.get(p.str("fileId"));
            Circuit c = d.circuit(p.str("circuitId"));
            Component pin = d.component(c, p.str("componentId"));
            // 원조 CircuitState를 고친다: 원조 전파와 겹치지 않게(D-143, sim.poke와 같다)
            s.quiet(() -> {
                s.pinValue(c, pin, p.str("value"));
                return null;
            });
            return new JsonObject();
        });
        server.register("sim.state", (p, call) -> session(p).state());
    }

    private SimSession session(Params p) throws RpcError {
        Doc d = files.get(p.str("fileId"));
        return sims.get(d.id());
    }
}
