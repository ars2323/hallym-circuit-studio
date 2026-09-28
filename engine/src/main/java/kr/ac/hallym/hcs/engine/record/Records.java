/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.record;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.doc.Files;
import kr.ac.hallym.hcs.engine.rpc.Params;
import kr.ac.hallym.hcs.engine.rpc.RpcError;
import kr.ac.hallym.hcs.engine.rpc.Server;
import kr.ac.hallym.hcs.engine.sim.SimSession;

/**
 * record.* 메서드(docs/engine-api.md "record", N-14, D-144)와 파일마다 하나인 {@link RecordSession}. 화면 프레임마다
 * 바뀐 {@code record.state}를 보낸다.
 */
public final class Records {
    private final Server server;
    private final Files files;
    private final Map<String, RecordSession> sessions = new HashMap<>();

    public Records(Server server, Files files) {
        this.server = server;
        this.files = files;
        register();
        server.executor().scheduleAtFixedRate(this::frame, SimSession.FRAME_MS, SimSession.FRAME_MS,
                TimeUnit.MILLISECONDS);
    }

    /** 파일을 열 때(SimSession보다 먼저: 틱 완료 알림을 먼저 받는다). */
    public RecordSession attach(Doc d) {
        RecordSession s = new RecordSession(d, server);
        sessions.put(d.id(), s);
        return s;
    }

    /** 곧 붙는 시뮬레이션 세션을 알린다. */
    public void bind(RecordSession r, SimSession s) {
        r.bind(s);
    }

    /** 모든 청취자가 붙은 뒤: 스텝 0이 적힐 때까지(file.open·file.new 안에서). */
    public void ready(RecordSession r) {
        r.ready();
    }

    public void close(String fileId) {
        RecordSession s = sessions.remove(fileId);
        if (s != null) {
            s.close();
        }
    }

    public void closeAll() {
        for (RecordSession s : sessions.values()) {
            s.close();
        }
        sessions.clear();
    }

    public RecordSession get(String fileId) {
        return sessions.get(fileId);
    }

    private void frame() {
        for (RecordSession s : new ArrayList<>(sessions.values())) {
            try {
                s.frame();
            } catch (Throwable t) {
                server.log("error", "record frame: " + t, false);
            }
        }
    }

    private RecordSession session(Params p) throws RpcError {
        Doc d = files.get(p.str("fileId"));
        RecordSession s = sessions.get(d.id());
        if (s == null) {
            throw RpcError.notFound("file", d.id());
        }
        return s;
    }

    /** 인스턴스 경로(부품 id들)를 부품으로. 없는 id는 오류 1. */
    private static List<Component> path(Doc d, List<String> ids) throws RpcError {
        List<Component> out = new ArrayList<>();
        for (String id : ids) {
            Component c = d.ids().component(id);
            if (c == null) {
                throw RpcError.notFound("instance", id);
            }
            out.add(c);
        }
        return out;
    }

    private static Location at(int[] xy) {
        return xy == null ? null : Location.create(xy[0], xy[1]);
    }

    private static Integer optInteger(Params p, String name) throws RpcError {
        return p.has(name) ? Integer.valueOf(p.integer(name)) : null;
    }

    private static JsonObject ok(String key, boolean value) {
        JsonObject o = new JsonObject();
        o.addProperty(key, value);
        return o;
    }

    private void register() {
        server.register("record.state", (p, call) -> session(p).state());
        server.register("record.table", (p, call) -> session(p).table(optInteger(p, "from"), optInteger(p, "to")));
        server.register("record.addRow", (p, call) -> {
            RecordSession s = session(p);
            Doc d = files.get(p.str("fileId"));
            Circuit c = d.circuit(p.str("circuitId"));
            Wire w = null;
            String wireId = p.optStr("wireId", null);
            if (wireId != null) {
                Object o = d.ids().object(wireId);
                if (!(o instanceof Wire)) {
                    throw RpcError.notFound("wire", wireId);
                }
                w = (Wire) o;
            }
            return s.addRow(c, path(d, p.optStrings("path")), at(p.optPoint("at")), w, p.optStr("netId", null));
        });
        server.register("record.removeRow", (p, call) -> ok("removed", session(p).removeRow(p.str("id"))));
        server.register("record.rowBits", (p, call) -> ok("changed", session(p).rowBits(p.str("id"),
                p.bool("bits"))));
        server.register("record.pin", (p, call) -> {
            RecordSession s = session(p);
            Doc d = files.get(p.str("fileId"));
            List<RecordSession.Spot> spots = new ArrayList<>();
            JsonElement rows = p.raw().get("rows");
            if (rows == null || !rows.isJsonArray()) {
                throw RpcError.params("rows must be an array");
            }
            for (JsonElement e : rows.getAsJsonArray()) {
                if (!e.isJsonObject()) {
                    throw RpcError.params("rows must hold objects");
                }
                Params r = new Params(e.getAsJsonObject());
                spots.add(new RecordSession.Spot(d.circuit(r.str("circuitId")), path(d, r.optStrings("path")),
                        at(r.point("at"))));
            }
            return s.pin(p.optInt("cycle", -1), spots);
        });
        server.register("record.unpin", (p, call) -> {
            session(p).unpin();
            return new JsonObject();
        });
        server.register("record.view", (p, call) -> session(p).view(p.optInt("cycle", 0),
                p.optBool("latest", false)));
        server.register("record.values", (p, call) -> {
            RecordSession s = session(p);
            Doc d = files.get(p.str("fileId"));
            return s.values(p.integer("cycle"), d.circuit(p.str("circuitId")), path(d, p.optStrings("path")));
        });
        server.register("record.runUntil", (p, call) -> {
            session(p).runUntil(p.str("kind"), p.optStr("value", null), p.optInt("maxCycles", 0));
            return new JsonObject();
        });
        server.register("record.stop", (p, call) -> ok("stopped", session(p).stop()));
        server.register("record.registers", (p, call) -> session(p).registers(optInteger(p, "cycle")));
        server.register("record.memory", (p, call) -> session(p).memory());
        server.register("record.instruction", (p, call) -> session(p).instruction(optInteger(p, "cycle")));
        server.register("record.fieldPaths", (p, call) -> {
            RecordSession s = session(p);
            Doc d = files.get(p.str("fileId"));
            return s.fieldPaths(d.circuit(p.str("circuitId")), optInteger(p, "cycle"));
        });
        server.register("record.markPc", (p, call) -> {
            RecordSession s = session(p);
            Doc d = files.get(p.str("fileId"));
            Circuit c = d.circuit(p.str("circuitId"));
            return s.markPc(c, d.component(c, p.str("componentId")), p.optBool("on", true));
        });
        server.register("record.markRegisterFile", (p, call) -> {
            RecordSession s = session(p);
            Doc d = files.get(p.str("fileId"));
            return s.markRegisterFile(d.circuit(p.str("circuitId")), p.optBool("on", true));
        });
        server.register("record.registerMapping", (p, call) -> session(p).registerMapping());
        server.register("record.setRegisterMapping", (p, call) -> {
            RecordSession s = session(p);
            Doc d = files.get(p.str("fileId"));
            Circuit rf = d.circuit(p.str("circuitId"));
            JsonElement m = p.raw().get("map");
            if (m == null || !m.isJsonObject()) {
                throw RpcError.params("map must be an object {number: [x, y] | null}");
            }
            Map<Integer, Location> chosen = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : m.getAsJsonObject().entrySet()) {
                int n;
                try {
                    n = Integer.parseInt(e.getKey());
                } catch (NumberFormatException ex) {
                    throw RpcError.params("map keys are register numbers");
                }
                JsonElement v = e.getValue();
                if (v.isJsonNull()) {
                    chosen.put(n, null);
                } else if (v.isJsonArray() && v.getAsJsonArray().size() == 2) {
                    chosen.put(n, Location.create(v.getAsJsonArray().get(0).getAsInt(),
                            v.getAsJsonArray().get(1).getAsInt()));
                } else {
                    throw RpcError.params("map values are [x, y] or null");
                }
            }
            return s.setRegisterMapping(rf, chosen);
        });
    }
}
