/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
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
import kr.ac.hallym.hcs.engine.edit.ExtEdits;
import kr.ac.hallym.hcs.engine.edit.Intents;
import kr.ac.hallym.hcs.engine.find.Find;
import kr.ac.hallym.hcs.engine.mips.Programs;
import kr.ac.hallym.hcs.engine.model.Ids;
import kr.ac.hallym.hcs.engine.record.RecordSession;
import kr.ac.hallym.hcs.engine.record.Records;
import kr.ac.hallym.hcs.engine.rpc.Params;
import kr.ac.hallym.hcs.engine.rpc.RpcError;
import kr.ac.hallym.hcs.engine.rpc.Server;
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

    public Engine(Server server) {
        this.server = server;
        this.diags = new DiagService(server, files);
        registerEngine();
        registerFile();
        registerModel();
        registerMips();
        registerEdit();
        registerSim();
        records = new Records(server, files);
        registerFindAndExt();
        registerFlow();
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
        files.closeAll();
    }

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
            JsonObject o = new JsonObject();
            o.addProperty("engine", NAME);
            o.addProperty("version", version());
            o.addProperty("logisim", LOGISIM);
            o.addProperty("java", System.getProperty("java.version"));
            o.addProperty("api", API);
            return o;
        });
        server.register("engine.shutdown", (p, call) -> {
            call.after(() -> server.requestShutdown("shutdown"));
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
                d = files.open(f, p.optBool("readOnly", false), messages, restore(p));
                attach(d);
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
            files.close(d);
            return new JsonObject();
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
            Intents.Result r = s == null ? e.apply(d, p) : s.quiet(() -> e.apply(d, p));
            JsonObject o = new JsonObject();
            o.addProperty("changed", r.changed);
            if (r.outcome != null) {
                o.addProperty("outcome", r.outcome);
            }
            if (r.added != null) {
                o.addProperty("id", d.ids().of(r.added));
            }
            if (r.changed) {
                d.project().getSimulator().requestPropagate(); // Canvas.completeAction과 같다
                call.after(() -> publishChanges(d));
            }
            return o;
        });
    }

    /** 바뀐 회로마다 model.changed를 보낸다(응답 뒤). */
    public void publishChanges(Doc d) {
        for (JsonObject change : d.tracker().changes(d.id(), d.isDirty())) {
            server.notify("model.changed", change);
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
            return Intents.addWire(d, c, pts);
        });
        edit("edit.move", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            List<Component> comps = d.components(c, p.strings("ids"));
            return Intents.move(d, c, comps, p.integer("dx"), p.integer("dy"), p.optBool("connect", true));
        });
        edit("edit.delete", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return Intents.delete(d, c, d.components(c, p.strings("ids")));
        });
        edit("edit.setAttr", true, (d, p) -> {
            Circuit c = d.circuit(p.str("circuitId"));
            return Intents.setAttr(d, c, d.components(c, p.strings("ids")), p.str("attr"), p.str("value"));
        });
        edit("edit.undo", false, (d, p) -> Intents.undo(d));
        edit("edit.redo", false, (d, p) -> Intents.redo(d));
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
            return o;
        });
        server.register("sim.cycles", (p, call) -> {
            session(p).cycles(p.integer("n"));
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
            return new JsonObject();
        });
        server.register("sim.state", (p, call) -> session(p).state());
    }

    private SimSession session(Params p) throws RpcError {
        Doc d = files.get(p.str("fileId"));
        return sims.get(d.id());
    }
}
