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
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.libs.MipsShadow;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.doc.Files;
import kr.ac.hallym.hcs.engine.edit.Intents;
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

    public Engine(Server server) {
        this.server = server;
        registerEngine();
        registerFile();
        registerModel();
        registerEdit();
        registerSim();
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
    }

    private void closeAll() {
        for (SimSession s : sims.values()) {
            s.close();
        }
        sims.clear();
        files.closeAll();
    }

    // ---- engine ----

    private void registerEngine() {
        server.register("engine.hello", (p, call) -> {
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
            Doc d = files.create();
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
                d = files.open(f, p.optBool("readOnly", false), messages);
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

    private void attach(Doc d) {
        sims.put(d.id(), new SimSession(d, server));
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
                    if (t instanceof AddTool) {
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
            Intents.Result r = e.apply(d, p);
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

    // ---- sim ----

    private void registerSim() {
        server.register("sim.reset", (p, call) -> {
            SimSession s = session(p);
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
            boolean poked = s.poke(c, comp, at == null ? null : Location.create(at[0], at[1]),
                    p.optStr("action", "click"));
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
