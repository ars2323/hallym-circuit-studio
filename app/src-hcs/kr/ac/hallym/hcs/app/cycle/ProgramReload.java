/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;

/**
 * .s 자동 재로드(C-09, PLAN.md 5장·6.8). 우클릭 ".s 불러오기"로 불러온 메모리(Instruction Memory·Data Memory)는
 * 속성 source에 .s 경로를 기억한다. 파일을 연 때, 리셋할 때, .s 파일이 바뀌었을 때 그 .s를 hcs-asm으로 다시 어셈블해
 * 초기 내용이 달라졌으면 바꾼다(되돌리기 한 번, 그 뒤 리셋). 어셈블 오류면 옛 내용을 두고 한 줄로 알린다. 기계어는
 * QtSpim 그대로이고 도구는 해석하지 않는다(D-010).
 */
public final class ProgramReload {
    static final String CONTENTS = "contents";
    static final String SOURCE = "source";

    /** 결과. */
    public static final class Result {
        /** 다시 불러온 .s 파일들. */
        public final List<File> reloaded = new ArrayList<>();
        /** 어셈블 오류: 파일 → 첫 오류 글. */
        public final Map<File, String> errors = new LinkedHashMap<>();

        public boolean changed() {
            return !reloaded.isEmpty();
        }
    }

    /** 프로젝트마다 마지막으로 본 .s 수정 시각. */
    private static final Map<Project, Map<File, Long>> SEEN = new WeakHashMap<>();

    private ProgramReload() {
    }

    /** 이 파일의 메모리 중 source가 있는 것들(.s 파일 → 부품들). */
    static Map<File, List<Component>> targets(LogisimFile file) {
        Map<File, List<Component>> out = new LinkedHashMap<>();
        File circ = file.getLoader().getMainFile();
        for (Circuit c : file.getCircuits()) {
            for (Component x : c.getNonWires()) {
                String f = x.getFactory().getName();
                if (!f.equals("Instruction Memory") && !f.equals("Data Memory")) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Attribute<Object> a = (Attribute<Object>) x.getAttributeSet().getAttribute(SOURCE);
                Object v = a == null ? null : x.getAttributeSet().getValue(a);
                if (v == null || v.toString().isEmpty()) {
                    continue;
                }
                File s = new File(v.toString());
                if (!s.isAbsolute() && circ != null && circ.getParentFile() != null) {
                    s = new File(circ.getParentFile(), v.toString());
                }
                try {
                    s = s.getCanonicalFile();
                } catch (IOException e) {
                    s = s.getAbsoluteFile();
                }
                out.computeIfAbsent(s, k -> new ArrayList<>()).add(x);
            }
        }
        return out;
    }

    /**
     * 수정 시각이 바뀐 .s만 다시 본다(force면 모두). 바뀐 내용을 한 동작으로 둔다. 파일을 처음 보는 경우(연 때)는
     * force와 같다.
     */
    public static Result check(Project proj, boolean force) {
        Result r = new Result();
        LogisimFile file = proj.getLogisimFile();
        if (file == null) {
            return r;
        }
        Map<File, Long> seen;
        synchronized (SEEN) {
            seen = SEEN.computeIfAbsent(proj, k -> new HashMap<>());
        }
        Map<File, List<Component>> all = targets(file);
        CircuitMutationSet changes = new CircuitMutationSet(file);
        for (Map.Entry<File, List<Component>> e : all.entrySet()) {
            File s = e.getKey();
            long mtime = s.lastModified();
            Long before = seen.get(s);
            if (!force && before != null && before == mtime) {
                continue;
            }
            seen.put(s, mtime);
            if (!s.isFile()) {
                continue;
            }
            String json;
            try {
                json = assemble(s);
            } catch (IOException ex) {
                r.errors.put(s, ex.getMessage());
                continue;
            }
            String error = firstError(json);
            if (error != null) {
                r.errors.put(s, error);
                continue;
            }
            boolean any = false;
            for (Component x : e.getValue()) {
                String section = x.getFactory().getName().equals("Instruction Memory") ? "text" : "data";
                @SuppressWarnings("unchecked")
                Attribute<Object> a = (Attribute<Object>) x.getAttributeSet().getAttribute(CONTENTS);
                if (a == null) {
                    continue;
                }
                Object want = a.parse(words(json, section));
                Object now = x.getAttributeSet().getValue(a);
                if (!a.toStandardString(want).equals(a.toStandardString(now))) {
                    changes.set(x, a, want);
                    any = true;
                }
            }
            if (any) {
                r.reloaded.add(s);
            }
        }
        if (changes.isEmpty()) {
            return r;
        }
        String names = String.join(", ", r.reloaded.stream().map(File::getName).toArray(String[]::new));
        proj.doAction(changes.toAction(Messages.get("reload.action", names)));
        return r;
    }

    /** 회로마다 모은 속성 바꾸기(동작 하나로). */
    static final class CircuitMutationSet {
        private final LogisimFile file;
        private final Map<Circuit, CircuitMutation> byCircuit = new LinkedHashMap<>();

        CircuitMutationSet(LogisimFile file) {
            this.file = file;
        }

        void set(Component x, Attribute<Object> a, Object v) {
            for (Circuit c : file.getCircuits()) {
                if (c.getNonWires().contains(x)) {
                    byCircuit.computeIfAbsent(c, CircuitMutation::new).set(x, a, v);
                    return;
                }
            }
        }

        boolean isEmpty() {
            return byCircuit.isEmpty();
        }

        com.cburch.logisim.proj.Action toAction(String name) {
            com.cburch.logisim.proj.Action act = null;
            for (CircuitMutation m : byCircuit.values()) {
                com.cburch.logisim.proj.Action one = m.toAction(() -> name);
                act = act == null ? one : act.append(one);
            }
            return act;
        }
    }

    static String assemble(File source) throws IOException {
        File exe = kr.ac.hallym.hcs.app.BundledLibraries.hcsAsm();
        if (exe == null) {
            throw new IOException(Messages.get("reload.noAssembler"));
        }
        Process p = new ProcessBuilder(exe.getPath(), source.getPath()).start();
        p.getOutputStream().close();
        byte[] out = p.getInputStream().readAllBytes();
        try {
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("hcs-asm timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        String json = new String(out, StandardCharsets.UTF_8);
        if (!json.contains("\"text\"")) {
            byte[] err = p.getErrorStream().readAllBytes();
            throw new IOException(new String(err, StandardCharsets.UTF_8).trim());
        }
        return json;
    }

    /** hcs-asm JSON의 첫 어셈블 오류("줄 12: …"). 없으면 null. */
    static String firstError(String json) {
        int i = json.indexOf("\"errors\"");
        if (i < 0) {
            return null;
        }
        int end = json.indexOf(']', i);
        Matcher m = Pattern.compile("\"line\":\\s*(\\d+)[^}]*?\"message\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(json.substring(i, end < 0 ? json.length() : end));
        if (m.find()) {
            return Messages.get("reload.errorLine", m.group(1), m.group(2).replace("\\\"", "\""));
        }
        return null;
    }

    /** hcs-asm JSON의 text 또는 data 칸 워드를 hcs-words 형식으로(lib-mips WordImage가 읽는다). */
    static String words(String json, String section) {
        int start = json.indexOf("\"" + section + "\"");
        if (start < 0) {
            return "";
        }
        int end = json.indexOf(']', start);
        Matcher m = Pattern.compile("\"addr\":\\s*\"0x([0-9a-fA-F]+)\",\\s*\"word\":\\s*\"0x([0-9a-fA-F]+)\"")
                .matcher(json.substring(start, end < 0 ? json.length() : end));
        TreeMap<Long, String> w = new TreeMap<>();
        while (m.find()) {
            w.put(Long.parseLong(m.group(1), 16), m.group(2).toLowerCase());
        }
        if (w.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("hcs-words 1\n");
        for (Map.Entry<Long, String> e : w.entrySet()) {
            sb.append(String.format("%08x", e.getKey())).append(' ').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    /** 테스트: 본 시각을 잊는다. */
    static void forget(Project proj) {
        synchronized (SEEN) {
            SEEN.remove(proj);
        }
    }
}
