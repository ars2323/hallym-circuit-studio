/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.mips;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.tools.SetAttributeAction;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.cycle.ConsoleText;
import kr.ac.hallym.hcs.app.sim.StatusModel;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.doc.Files;
import kr.ac.hallym.hcs.engine.rpc.Params;
import kr.ac.hallym.hcs.engine.rpc.RpcError;
import kr.ac.hallym.hcs.engine.rpc.Server;
import kr.ac.hallym.hcs.engine.sim.SimSession;
import kr.ac.hallym.hcs.mips.disasm.Disassembler;
import kr.ac.hallym.hcs.mips.image.AssemblySource;
import kr.ac.hallym.hcs.mips.image.ExecutableImage;
import kr.ac.hallym.hcs.mips.image.HmxParser;
import kr.ac.hallym.hcs.mips.image.LoadReport;
import kr.ac.hallym.hcs.mips.image.Msg;
import kr.ac.hallym.hcs.mips.image.StartFacts;

/**
 * MIPS 프로그램(N-16, D-147, docs/engine-api.md "mips"): 실행 이미지 불러오기({@code mips.load}), 다시 불러오기
 * ({@code mips.reload}와 자동: .hmx가 바뀜·Reset·파일을 연 뒤 처음 볼 때, PLAN.md 6.8), 디스어셈블({@code mips.disasm}),
 * Console 출력({@code mips.console}), 상태 표시줄의 프로그램 사실({@code mips.facts}의 {@code program}과 사이클 0의
 * PC ≠ entry).
 *
 * <p>읽기와 넣을 곳 정하기는 트랙 A와 같은 lib-mips 코드가 한다({@link LibMips}, 전부 아니면 전무). 여기는 그 결과를
 * 원조 {@link SetAttributeAction} 한 번(되돌리기 한 단계)으로 넣고, 시뮬레이션을 처음으로 돌리고, 화면에 알린다. 다시
 * 불러오다 실패하면 올라가 있던 프로그램과 시뮬레이션을 그대로 두고 실패와 마지막으로 불러온 시각을 알린다. 학생의
 * 레지스터에는 아무것도 쓰지 않고, 동작하는 회로를 판단하지 않는다(PC ≠ entry는 사실이다, 진단이 아님).
 *
 * <p>감시는 앱이 도는 동안만이다(실습실 규칙: 디스크에 아무것도 남기지 않는다).
 */
public final class Programs {
    /** .hmx 감시 간격(ms). */
    public static final long WATCH_MS = 1000;
    /** 프로그램 사실을 다시 보는 간격(화면 프레임 수, 약 100ms). */
    static final int FACTS_FRAMES = 6;
    /** mips.disasm 한 번에 내는 줄 수의 기본값과 한도. */
    static final int DISASM_DEFAULT = 1024;
    static final int DISASM_MAX = 4096;

    private final Server server;
    private final Files files;
    private final Function<Doc, SimSession> sims;
    private final Consumer<Doc> publish;
    private final Map<String, State> states = new LinkedHashMap<>();

    /** 파일의 .hmx 상태(수정 시각·크기, 없음). */
    static final class Stamp {
        final boolean exists;
        final long modified;
        final long length;

        Stamp(File f) {
            exists = f.isFile();
            modified = exists ? f.lastModified() : 0;
            length = exists ? f.length() : 0;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Stamp && ((Stamp) o).exists == exists && ((Stamp) o).modified == modified
                    && ((Stamp) o).length == length;
        }

        @Override
        public int hashCode() {
            return Objects.hash(exists, modified, length);
        }
    }

    /** 다시 불러오지 못함: 그때 시각, 파일 이름, source 속성 글, 까닭(open·changed·reset·manual), 문제들. */
    static final class Failure {
        final long at;
        final String file;
        final String source;
        final String reason;
        final List<LoadReport.Problem> problems;

        Failure(long at, String file, String source, String reason, List<LoadReport.Problem> problems) {
            this.at = at;
            this.file = file;
            this.source = source;
            this.reason = reason;
            this.problems = problems;
        }
    }

    /** 열린 파일 하나의 프로그램 상태. 모두 엔진 스레드에서만 만진다. */
    static final class State {
        final Doc doc;
        /**
         * 파일을 열 때 메모리 부품의 source가 가리키던 .hmx(속성 글). 감시가 처음 볼 때 저장된 내용과 파일을 대조한다.
         * 연 뒤에 편집으로 생긴 source는 대조하지 않고 그때부터 바뀜만 본다.
         */
        final java.util.Set<String> opened = new java.util.HashSet<>();
        /** source 속성 글 → 마지막으로 본 파일 상태. */
        final Map<String, Stamp> stamps = new HashMap<>();
        /** 이번 실행에서 마지막으로 불러오거나 다시 불러온 시각(ms). 파일에 저장된 채로 열었으면 null. */
        Long loadedAt;
        /** 서 있는 다시 불러오기 실패(띠). 없으면 null. */
        Failure failure;
        /** .hmx 절대 경로 → 이번 실행에서 마지막으로 넣은 이미지. */
        final Map<String, ExecutableImage> images = new HashMap<>();
        /** .hmx 절대 경로 → 디스크에서 읽은 이미지(그 파일 상태일 때, 읽지 못했으면 null). */
        final Map<String, Object[]> parsed = new HashMap<>();
        JsonObject factsSent;
        int frames;
        /** Console 이름 → 보낸 글. */
        final Map<String, String> consoleText = new LinkedHashMap<>();
        final Map<String, Boolean> consoleExited = new HashMap<>();

        State(Doc doc) {
            this.doc = doc;
        }
    }

    public Programs(Server server, Files files, Function<Doc, SimSession> sims, Consumer<Doc> publish) {
        this.server = server;
        this.files = files;
        this.sims = sims;
        this.publish = publish;
        register();
        server.executor().scheduleWithFixedDelay(this::watchAll, WATCH_MS, WATCH_MS, TimeUnit.MILLISECONDS);
    }

    // ---- 파일 ----

    public void attach(Doc d) {
        State s = new State(d);
        s.opened.addAll(sources(d.file()));
        states.put(d.id(), s);
    }

    public void detach(Doc d) {
        states.remove(d.id());
    }

    public void closeAll() {
        states.clear();
    }

    State state(Doc d) {
        return states.computeIfAbsent(d.id(), k -> new State(d));
    }

    static File circFile(Doc d) {
        return d.file().getLoader() == null ? null : d.file().getLoader().getMainFile();
    }

    /** source 속성 글(상대 경로면 .circ 폴더 기준)의 파일. 트랙 A의 ProgramLoader.resolveSource와 같다. */
    static File resolve(File circ, String source) {
        File f = new File(source);
        if (!f.isAbsolute() && circ != null && circ.getAbsoluteFile().getParentFile() != null) {
            f = new File(circ.getAbsoluteFile().getParentFile(), source);
        }
        return f.getAbsoluteFile();
    }

    /** MIPS 메모리 부품(Instruction Memory, Data Memory)의 종류: text, data. 아니면 null(옛 Stack은 프로그램을 담지 않는다). */
    static String kind(Component c) {
        if (!c.getFactory().getClass().getName().startsWith("kr.ac.hallym.hcs.mips.")) {
            return null;
        }
        String n = c.getFactory().getName();
        return n.equals("Instruction Memory") ? "text" : n.equals("Data Memory") ? "data" : null;
    }

    /** 파일의 메모리 부품들이 가리키는 .hmx(속성 글, 회로·부품 순서, 중복 없음). .s 경로(D-141)는 넣지 않는다. */
    static List<String> sources(LogisimFile f) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Circuit c : f.getCircuits()) {
            for (Component x : c.getNonWires()) {
                if (kind(x) != null) {
                    String s = CircuitFacts.source(x);
                    if (s != null && !s.isEmpty() && !AssemblySource.isAssembly(s)) {
                        out.add(s);
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    // ---- 메서드 ----

    private void register() {
        server.register("mips.facts", (p, call) -> facts(state(files.get(p.str("fileId")))));
        server.register("mips.load", (p, call) -> load(p, call));
        server.register("mips.reload", (p, call) -> {
            State s = state(files.get(p.str("fileId")));
            JsonArray results = new JsonArray();
            for (String src : sources(s.doc.file())) {
                s.stamps.put(src, new Stamp(resolve(circFile(s.doc), src)));
                results.add(check(s, src, "manual"));
            }
            JsonObject o = new JsonObject();
            o.addProperty("fileId", s.doc.id());
            o.add("results", results);
            return o;
        });
        server.register("mips.disasm", (p, call) -> disasm(p));
        server.register("mips.console", (p, call) -> {
            State s = state(files.get(p.str("fileId")));
            return console(s, true);
        });
    }

    /** mips.load: 고른 .hmx를 넣는다(트랙 A 메뉴와 같은 길). 고를 것이 남았거나 문제가 있으면 아무것도 바꾸지 않는다. */
    private JsonElement load(Params p, Server.Call call) throws RpcError {
        Doc d = files.get(p.str("fileId"));
        if (d.isReadOnly()) {
            throw RpcError.notEditable("readOnly", "the file is read-only");
        }
        File hmx = new File(p.str("path")).getAbsoluteFile();
        Circuit clickedCircuit = null;
        Component clicked = null;
        String target = p.optStr("target", null);
        if (target != null) {
            clicked = d.ids().component(target);
            if (clicked == null || kind(clicked) == null) {
                throw RpcError.notFound("memory component", target);
            }
            for (Circuit c : d.file().getCircuits()) {
                if (c.contains(clicked)) {
                    clickedCircuit = c;
                }
            }
            if (clickedCircuit == null) {
                throw RpcError.notFound("memory component", target);
            }
        }
        Map<String, Component> picks = new HashMap<>();
        if (p.has("picks") && p.raw().get("picks").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : p.raw().getAsJsonObject("picks").entrySet()) {
                Component c = e.getValue().isJsonPrimitive() ? d.ids().component(e.getValue().getAsString()) : null;
                if (c == null || !e.getKey().equals(kind(c))) {
                    throw RpcError.params("picks." + e.getKey() + " is not a " + e.getKey() + " memory component");
                }
                picks.put(e.getKey(), c);
            }
        }
        final Circuit inCircuit = clickedCircuit;
        final Component chosen = clicked;
        LoadReport r = report(() -> LibMips.load(d.file(), hmx, circFile(d), inCircuit, chosen, picks));
        JsonObject o = new JsonObject();
        o.addProperty("fileId", d.id());
        o.addProperty("file", hmx.getName());
        if (r.choice != null) {
            o.addProperty("loaded", false);
            o.add("choose", ProgramJson.choice(r.choice, d.ids()));
            return o;
        }
        if (!r.ok()) {
            o.addProperty("loaded", false);
            o.add("problems", ProgramJson.problems(r.problems));
            return o;
        }
        State s = state(d);
        apply(d, r, "Load Program");
        long now = System.currentTimeMillis();
        s.loadedAt = now;
        s.failure = null;
        s.images.put(hmx.getPath(), r.image);
        s.stamps.put(r.source, new Stamp(hmx));
        SimSession sim = sims.apply(d);
        boolean resetNow = sim != null && sim.reset();
        call.after(() -> {
            publish.accept(d);
            if (resetNow) {
                sim.sendState(true);
            }
            sendFacts(s, true);
        });
        o.addProperty("loaded", true);
        o.addProperty("source", r.source);
        o.addProperty("loadedAt", now);
        o.add("summary", ProgramJson.summary(r, d.ids()));
        return o;
    }

    private interface Plan {
        LoadReport get() throws LibMips.Missing;
    }

    private static LoadReport report(Plan plan) throws RpcError {
        try {
            return plan.get();
        } catch (LibMips.Missing e) {
            throw new RpcError(RpcError.INTERNAL_ERROR, e.getMessage());
        }
    }

    /** 바꿀 것을 원조 SetAttributeAction(회로마다 하나, 이어 붙여 되돌리기 한 단계)으로 넣는다. */
    private static void apply(Doc d, LoadReport r, String name) {
        Map<Circuit, SetAttributeAction> byCircuit = new LinkedHashMap<>();
        for (LoadReport.Change ch : r.changes) {
            byCircuit.computeIfAbsent((Circuit) ch.circuit, c -> new SetAttributeAction(c, () -> name))
                    .set((Component) ch.component, (Attribute<?>) ch.attribute, ch.value);
        }
        Action action = null;
        for (SetAttributeAction a : byCircuit.values()) {
            action = action == null ? a : action.append(a);
        }
        if (action != null) {
            d.project().doAction(action);
        }
    }

    /** 바꿀 것이 모두 지금 값과 같은가(같은 프로그램을 다시 내보낸 경우). */
    private static boolean unchanged(LoadReport r) {
        for (LoadReport.Change ch : r.changes) {
            @SuppressWarnings("unchecked")
            Attribute<Object> a = (Attribute<Object>) ch.attribute;
            if (!Objects.equals(((Component) ch.component).getAttributeSet().getValue(a), ch.value)) {
                return false;
            }
        }
        return true;
    }

    // ---- 다시 불러오기 ----

    /** 감시: 열린 모든 파일. */
    void watchAll() {
        for (State s : new ArrayList<>(states.values())) {
            try {
                watch(s, "changed");
            } catch (Throwable t) {
                server.log("error", "mips watch: " + t, false);
            }
        }
    }

    /** 감시(테스트): 한 번 곧바로. */
    public void watchNow() {
        watchAll();
    }

    /**
     * 한 파일: 파일을 열 때 있던 .hmx는 처음 볼 때 저장된 내용과 대조하고(다르면 다시 넣는다), 그다음부터는 수정 시각·크기가
     * 바뀐 .hmx만 다시 넣는다. 읽기 전용 파일은 바꾸지 않으므로 보지 않는다.
     */
    void watch(State s, String reason) {
        if (s.doc.isReadOnly()) {
            return;
        }
        File circ = circFile(s.doc);
        for (String src : sources(s.doc.file())) {
            Stamp now = new Stamp(resolve(circ, src));
            Stamp before = s.stamps.put(src, now);
            if (before == null) {
                if (s.opened.remove(src)) {
                    check(s, src, "open"); // 파일을 연 뒤 처음: 저장된 내용이 .hmx와 다르면 다시 넣는다
                }
            } else if (!before.equals(now)) {
                check(s, src, reason);
            }
        }
    }

    /** sim.reset 앞: 바뀐 .hmx가 있으면 먼저 다시 넣는다(PLAN.md 6.8). */
    public void beforeReset(Doc d) {
        State s = states.get(d.id());
        if (s != null) {
            watch(s, "reset");
        }
    }

    /**
     * source(.hmx)를 그 source를 가진 부품에 다시 넣는다. 내용이 같으면 아무것도 하지 않는다. 실패하면 올라가 있던 것과
     * 시뮬레이션을 그대로 두고 실패를 기억해 알린다(띠). 결과: {@code {source, ok, changed, problems?}}.
     */
    JsonObject check(State s, String src, String reason) {
        Doc d = s.doc;
        File circ = circFile(d);
        File hmx = resolve(circ, src);
        JsonObject out = new JsonObject();
        out.addProperty("source", src);
        LoadReport r;
        if (!hmx.isFile()) {
            r = new LoadReport(hmx);
            r.problems.add(new LoadReport.Problem(0, Msg.of(
                    "The executable image file is not there. Export it again from Hallym MIPS, or choose a file with"
                            + " the Load Program… button.",
                    "실행 이미지 파일이 그 자리에 없습니다. Hallym MIPS에서 다시 내보내거나 Load Program… 단추로 파일을"
                            + " 고르세요.")));
        } else {
            try {
                r = LibMips.reload(d.file(), hmx, circ, src);
            } catch (LibMips.Missing e) {
                server.log("warn", "mips reload: " + e.getMessage(), false);
                out.addProperty("ok", false);
                return out;
            }
        }
        if (!r.ok()) {
            s.failure = new Failure(System.currentTimeMillis(), hmx.getName(), src, reason,
                    new ArrayList<>(r.problems));
            out.addProperty("ok", false);
            out.addProperty("changed", false);
            out.add("problems", ProgramJson.problems(r.problems));
            reloaded(s, false, reason, hmx, src, null, r.problems);
            sendFacts(s, false);
            return out;
        }
        s.images.put(hmx.getPath(), r.image);
        boolean same = unchanged(r);
        if (s.failure != null && s.failure.source.equals(src)) {
            s.failure = null; // 파일이 다시 읽힌다
        }
        out.addProperty("ok", true);
        out.addProperty("changed", !same);
        if (same) {
            sendFacts(s, false);
            return out;
        }
        apply(d, r, "Reload " + hmx.getName());
        s.loadedAt = System.currentTimeMillis();
        SimSession sim = sims.apply(d);
        if (sim != null && sim.reset()) {
            sim.sendState(true);
        }
        publish.accept(d);
        reloaded(s, true, reason, hmx, src, r, null);
        sendFacts(s, false);
        return out;
    }

    /** 알림 mips.reloaded: 자동으로 다시 넣었거나 넣지 못했다. */
    private void reloaded(State s, boolean ok, String reason, File hmx, String src, LoadReport r,
            List<LoadReport.Problem> problems) {
        JsonObject o = new JsonObject();
        o.addProperty("fileId", s.doc.id());
        o.addProperty("ok", ok);
        o.addProperty("reason", reason);
        o.addProperty("file", hmx.getName());
        o.addProperty("source", src);
        if (ok) {
            o.addProperty("loadedAt", s.loadedAt);
            o.add("summary", ProgramJson.summary(r, s.doc.ids()));
        } else {
            o.add("problems", ProgramJson.problems(problems));
            o.add("kept", kept(s));
        }
        server.notify("mips.reloaded", o);
    }

    // ---- 이미지(entry·기호) ----

    /**
     * 이 부품 내용과 같은 이미지(entry와 기호를 읽는다): 이번 실행에서 넣은 것, 없으면 디스크의 .hmx(같은 파일 상태면 다시
     * 읽지 않는다). 부품 내용이 그 이미지의 구간 워드와 같을 때만 준다(다시 불러오기에 실패해 파일이 바뀌었으면 null).
     */
    ExecutableImage imageFor(State s, Component c, SortedMap<Long, Integer> words) {
        String src = CircuitFacts.source(c);
        String k = kind(c);
        if (src == null || src.isEmpty() || AssemblySource.isAssembly(src) || k == null || words == null) {
            return null;
        }
        File f = resolve(circFile(s.doc), src);
        ExecutableImage mine = s.images.get(f.getPath());
        if (matches(mine, k, words)) {
            return mine;
        }
        Stamp st = new Stamp(f);
        Object[] cached = s.parsed.get(f.getPath());
        ExecutableImage disk;
        if (cached != null && cached[0].equals(st)) {
            disk = (ExecutableImage) cached[1];
        } else {
            disk = null;
            if (st.exists) {
                try {
                    disk = HmxParser.read(f).image;
                } catch (IOException | RuntimeException e) {
                    disk = null;
                }
            }
            s.parsed.put(f.getPath(), new Object[] {st, disk});
        }
        return matches(disk, k, words) ? disk : null;
    }

    private static boolean matches(ExecutableImage img, String kind, SortedMap<Long, Integer> words) {
        if (img == null) {
            return false;
        }
        ExecutableImage.Kind k = kind.equals("text") ? ExecutableImage.Kind.TEXT : ExecutableImage.Kind.DATA;
        return img.words(img.segments(k)).equals(words);
    }

    // ---- 사실(상태 표시줄) ----

    /**
     * mips.facts: 파일 내용의 사실(따로 된 Stack, .s 경로: {@link CircuitFacts}), 사이클 0에서 PC ≠ entry(사실 줄,
     * {@link StartFacts#pcFact}), 그리고 {@code program}(올린 프로그램, 메모리마다 양, 서 있는 다시 불러오기 실패).
     */
    JsonObject facts(State s) {
        Doc d = s.doc;
        JsonObject o = CircuitFacts.json(d.file(), d.ids());
        o.addProperty("fileId", d.id());
        JsonArray list = o.getAsJsonArray("facts");
        JsonArray memories = new JsonArray();
        String source = null;
        ExecutableImage primary = null;
        Component primaryIm = null;
        for (Circuit c : d.file().getCircuits()) {
            for (Component x : placed(c)) {
                String k = kind(x);
                SortedMap<Long, Integer> words = LibMips.contents(x);
                String src = CircuitFacts.source(x);
                boolean hasSource = src != null && !src.isEmpty();
                if ((words == null || words.isEmpty()) && !hasSource) {
                    continue;
                }
                ExecutableImage img = imageFor(s, x, words);
                JsonObject m = new JsonObject();
                m.addProperty("componentId", d.ids().of(x));
                m.addProperty("circuitId", d.ids().of(c));
                m.addProperty("kind", k);
                m.addProperty("source", hasSource ? src : null);
                m.addProperty("words", words == null ? 0 : words.size());
                String text = ProgramJson.amount(words == null ? Collections.<Long, Integer>emptySortedMap() : words);
                if (k.equals("text") && img != null && img.entry() != null) {
                    m.addProperty("entry", ExecutableImage.hex(img.entry()));
                    text += ", entry " + ExecutableImage.hex(img.entry());
                }
                m.addProperty("text", text);
                memories.add(m);
                if (source == null && hasSource && !AssemblySource.isAssembly(src)) {
                    source = src;
                }
                if (primaryIm == null && k.equals("text")) {
                    primaryIm = x;
                    primary = img;
                }
            }
        }
        if (source != null || s.failure != null) {
            JsonObject prog = new JsonObject();
            String name = source != null ? new File(source.replace('\\', '/')).getName() : s.failure.file;
            prog.addProperty("name", name);
            prog.addProperty("source", source);
            prog.addProperty("entry", primary == null || primary.entry() == null ? null
                    : ExecutableImage.hex(primary.entry()));
            prog.addProperty("loadedAt", s.loadedAt);
            prog.add("failure", s.failure == null ? JsonNull.INSTANCE : failure(s));
            prog.add("memories", memories);
            o.add("program", prog);
        } else {
            o.add("program", JsonNull.INSTANCE);
        }
        JsonObject pc = pcFact(s, primary, primaryIm);
        if (pc != null) {
            list.add(pc);
        }
        return o;
    }

    /**
     * 회로의 MIPS 메모리 부품(Instruction Memory 먼저, 그다음 Data Memory, 같은 종류는 위→아래·왼쪽→오른쪽). 원조의 부품
     * 집합은 순서가 정해져 있지 않다(D-129).
     */
    static List<Component> placed(Circuit c) {
        List<Component> out = new ArrayList<>();
        for (Component x : c.getNonWires()) {
            if (kind(x) != null) {
                out.add(x);
            }
        }
        out.sort(java.util.Comparator.<Component, Boolean>comparing(x -> !"text".equals(kind(x)))
                .thenComparingInt(x -> x.getLocation().getY()).thenComparingInt(x -> x.getLocation().getX()));
        return out;
    }

    /** 사이클 0에서 회로의 PC가 이미지의 entry와 다르면 사실 줄 하나(D-138). 아니면 null. */
    private JsonObject pcFact(State s, ExecutableImage img, Component im) {
        SimSession sim = sims.apply(s.doc);
        if (img == null || img.entry() == null || sim == null || sim.state().get("cycle").getAsLong() != 0) {
            return null;
        }
        String pc = StatusModel.pc(rootState(s.doc));
        if (pc == null) {
            return null;
        }
        long value = Long.parseLong(pc.substring(2), 16);
        Msg m = StartFacts.pcFact(img, value);
        if (m == null) {
            return null;
        }
        JsonObject o = new JsonObject();
        o.addProperty("id", "pcEntry");
        o.addProperty("en", m.en);
        o.addProperty("ko", m.ko);
        JsonArray comps = new JsonArray();
        comps.add(s.doc.ids().of(im));
        o.add("components", comps);
        o.addProperty("pc", ExecutableImage.hex(value));
        o.addProperty("entry", ExecutableImage.hex(img.entry()));
        return o;
    }

    private JsonObject failure(State s) {
        JsonObject f = new JsonObject();
        f.addProperty("at", s.failure.at);
        f.addProperty("file", s.failure.file);
        f.addProperty("source", s.failure.source);
        f.addProperty("reason", s.failure.reason);
        f.add("problems", ProgramJson.problems(s.failure.problems));
        f.add("kept", kept(s));
        return f;
    }

    /** 올라가 있는 것: 이번 실행에서 마지막으로 불러온 시각(ms), 파일에 저장된 채로 열었으면 null. */
    private static JsonObject kept(State s) {
        JsonObject k = new JsonObject();
        k.addProperty("loadedAt", s.loadedAt);
        return k;
    }

    /** 사실이 바뀌었거나 force면 알림 mips.facts를 보낸다. */
    void sendFacts(State s, boolean force) {
        if (!states.containsKey(s.doc.id())) {
            return;
        }
        JsonObject now = facts(s);
        if (force || !now.equals(s.factsSent)) {
            s.factsSent = now;
            server.notify("mips.facts", now);
        }
    }

    static CircuitState rootState(Doc d) {
        CircuitState st = d.project().getCircuitState();
        while (st != null && st.getParentState() != null) {
            st = st.getParentState();
        }
        return st;
    }

    // ---- 디스어셈블 ----

    /** mips.disasm: 메모리 부품의 워드를 SPIM 목록 글로(D-127), 이미지의 기호를 붙여. */
    private JsonObject disasm(Params p) throws RpcError {
        Doc d = files.get(p.str("fileId"));
        String id = p.str("componentId");
        Component c = d.ids().component(id);
        SortedMap<Long, Integer> words = c == null || kind(c) == null ? null : LibMips.contents(c);
        if (words == null) {
            throw RpcError.notFound("memory component", id);
        }
        int count = p.optInt("count", DISASM_DEFAULT);
        if (count < 1 || count > DISASM_MAX) {
            throw RpcError.params("count must be between 1 and " + DISASM_MAX);
        }
        long from = words.isEmpty() ? 0 : words.firstKey();
        if (p.has("from")) {
            from = address(p.raw().get("from"));
        }
        ExecutableImage img = imageFor(state(d), c, words);
        Map<Integer, String> symbols = img == null ? Collections.<Integer, String>emptyMap()
                : Disassembler.byAddress(img.symbols());
        JsonObject o = new JsonObject();
        o.addProperty("fileId", d.id());
        o.addProperty("componentId", id);
        o.addProperty("words", words.size());
        o.addProperty("first", words.isEmpty() ? null : ExecutableImage.hex(words.firstKey()));
        o.addProperty("last", words.isEmpty() ? null : ExecutableImage.hex(words.lastKey()));
        o.addProperty("entry", img == null || img.entry() == null ? null : ExecutableImage.hex(img.entry()));
        o.addProperty("symbols", img != null);
        JsonArray lines = new JsonArray();
        for (Map.Entry<Long, Integer> e : words.tailMap(from).entrySet()) {
            if (lines.size() >= count) {
                break;
            }
            long a = e.getKey();
            int w = e.getValue();
            JsonObject line = new JsonObject();
            line.addProperty("addr", ExecutableImage.hex(a));
            line.addProperty("word", ExecutableImage.hex(w & 0xffffffffL));
            line.addProperty("text", Disassembler.text(w, (int) a, symbols));
            if (img != null) {
                List<String> at = img.symbolsAt(a);
                if (!at.isEmpty()) {
                    JsonArray labels = new JsonArray();
                    at.forEach(labels::add);
                    line.add("labels", labels);
                }
                if (img.entry() != null && img.entry() == a) {
                    line.addProperty("entry", true);
                }
            }
            lines.add(line);
        }
        o.add("lines", lines);
        return o;
    }

    /** 주소 글({@code 0x00400024}, {@code 400024}) 또는 수. */
    static long address(JsonElement e) throws RpcError {
        try {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                return e.getAsLong() & 0xffffffffL;
            }
            String t = e.getAsString().trim().toLowerCase(java.util.Locale.ROOT);
            return Long.parseLong(t.startsWith("0x") ? t.substring(2) : t, 16) & 0xffffffffL;
        } catch (RuntimeException x) {
            throw RpcError.params("from must be an address like 0x00400024");
        }
    }

    // ---- Console ----

    /**
     * Console 출력(v1 C-09): 보고 있는 회로 상태의 맨 위에서 모든 Console 부품의 출력 전체와 exit 여부
     * ({@link ConsoleText}, 서브회로 안은 경로 이름). full이면 {@code text}로 모두, 아니면 앞에 보낸 것에 이어진 부분은
     * {@code append}로. 바뀐 것이 없으면(full이 아니면) null.
     */
    JsonObject console(State s, boolean full) {
        List<ConsoleText.Entry> list = ConsoleText.collect(rootState(s.doc));
        boolean changed = full || list.size() != s.consoleText.size();
        JsonArray out = new JsonArray();
        Map<String, String> texts = new LinkedHashMap<>();
        for (ConsoleText.Entry e : list) {
            String before = s.consoleText.get(e.name);
            JsonObject o = new JsonObject();
            o.addProperty("name", e.name);
            if (full || before == null || !e.text.startsWith(before)) {
                o.addProperty("text", e.text);
                changed |= !e.text.equals(before);
            } else {
                o.addProperty("append", e.text.substring(before.length()));
                changed |= e.text.length() != before.length();
            }
            o.addProperty("exited", e.exited);
            changed |= !Boolean.valueOf(e.exited).equals(s.consoleExited.get(e.name));
            texts.put(e.name, e.text);
            out.add(o);
        }
        if (!changed) {
            return null;
        }
        s.consoleText.clear();
        s.consoleText.putAll(texts);
        s.consoleExited.clear();
        for (ConsoleText.Entry e : list) {
            s.consoleExited.put(e.name, e.exited);
        }
        JsonObject o = new JsonObject();
        o.addProperty("fileId", s.doc.id());
        o.add("consoles", out);
        return o;
    }

    // ---- 프레임 ----

    /** 화면 프레임마다: 바뀐 Console 출력, 그리고 몇 프레임에 한 번 사실. */
    public void frame() {
        for (State s : new ArrayList<>(states.values())) {
            try {
                JsonObject c = console(s, false);
                if (c != null) {
                    server.notify("mips.console", c);
                }
                if (++s.frames % FACTS_FRAMES == 0) {
                    sendFacts(s, false);
                }
            } catch (Throwable t) {
                server.log("error", "mips frame: " + t, false);
            }
        }
    }
}
