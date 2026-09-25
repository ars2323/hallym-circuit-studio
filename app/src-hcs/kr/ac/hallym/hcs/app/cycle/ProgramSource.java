/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 불러온 .s의 주소 → 원래 줄(C-02 사이클 표 머리, PLAN.md 5.1: 학생이 쓴 줄 그대로, 라벨·주석 포함). hcs-asm을
 * 다시 돌려 주소와 줄 번호, 라벨을 얻는다(기계어는 QtSpim 그대로, D-010). 파일이 바뀌지 않았으면 다시 돌리지 않는다.
 * .s가 없거나 어셈블할 수 없으면 비어 있다(머리는 디스어셈블로).
 */
public final class ProgramSource {
    public static final ProgramSource EMPTY = new ProgramSource(null, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyList());

    private static final Map<String, ProgramSource> CACHE = new HashMap<>();

    private final File file;
    private final Map<Integer, Integer> lineByAddr;
    private final Map<Integer, String> labelByAddr;
    private final Map<String, Integer> addrByLabel = new TreeMap<>();
    private final List<String> lines;

    ProgramSource(File file, Map<Integer, Integer> lineByAddr, Map<Integer, String> labelByAddr, List<String> lines) {
        this.file = file;
        this.lineByAddr = lineByAddr;
        this.labelByAddr = labelByAddr;
        this.lines = lines;
        for (Map.Entry<Integer, String> e : labelByAddr.entrySet()) {
            addrByLabel.put(e.getValue(), e.getKey());
        }
    }

    public File file() {
        return file;
    }

    public boolean isEmpty() {
        return lineByAddr.isEmpty();
    }

    /** addr 명령어의 원래 줄(앞뒤 공백 정리, 탭은 공백 하나). 없으면 null. */
    public String line(int addr) {
        Integer n = lineByAddr.get(addr);
        if (n == null || n < 1 || n > lines.size()) {
            return null;
        }
        return lines.get(n - 1).trim().replaceAll("\\s+", " ");
    }

    /** addr 명령어의 줄 번호(1부터). 없으면 -1. */
    public int lineNumber(int addr) {
        Integer n = lineByAddr.get(addr);
        return n == null ? -1 : n;
    }

    /** 주소의 라벨. 없으면 null. */
    public String label(int addr) {
        return labelByAddr.get(addr);
    }

    public Map<Integer, String> labels() {
        return Collections.unmodifiableMap(labelByAddr);
    }

    /** 라벨 이름 → 주소(이름 순). */
    public Map<String, Integer> addresses() {
        return Collections.unmodifiableMap(addrByLabel);
    }

    /** source(.s)를 읽는다. 같은 파일·수정 시각이면 앞 결과를 쓴다. 실패하면 EMPTY. */
    public static synchronized ProgramSource of(File source) {
        if (source == null || !source.isFile()) {
            return EMPTY;
        }
        String key = source.getAbsolutePath() + "@" + source.lastModified();
        ProgramSource p = CACHE.get(key);
        if (p == null) {
            p = load(source);
            CACHE.put(key, p);
        }
        return p;
    }

    static ProgramSource load(File source) {
        File exe = kr.ac.hallym.hcs.app.BundledLibraries.hcsAsm();
        if (exe == null) {
            return EMPTY;
        }
        try {
            Process proc = new ProcessBuilder(exe.getPath(), source.getPath()).redirectErrorStream(false).start();
            proc.getOutputStream().close();
            byte[] out = proc.getInputStream().readAllBytes();
            if (!proc.waitFor(30, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return EMPTY;
            }
            List<String> lines = new ArrayList<>(Files.readAllLines(source.toPath(), StandardCharsets.UTF_8));
            return parse(source, new String(out, StandardCharsets.UTF_8), lines);
        } catch (IOException e) {
            return EMPTY;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EMPTY;
        }
    }

    /** hcs-asm의 JSON에서 text 칸의 주소·줄 번호와 labels를 읽는다. */
    static ProgramSource parse(File source, String json, List<String> lines) {
        Map<Integer, Integer> byAddr = new HashMap<>();
        int t = json.indexOf("\"text\"");
        if (t >= 0) {
            int end = json.indexOf(']', t);
            Matcher m = Pattern.compile("\"addr\":\\s*\"0x([0-9a-fA-F]+)\"[^}]*?\"line\":\\s*(\\d+)")
                    .matcher(json.substring(t, end < 0 ? json.length() : end));
            while (m.find()) {
                byAddr.put((int) Long.parseLong(m.group(1), 16), Integer.parseInt(m.group(2)));
            }
        }
        Map<Integer, String> labels = new HashMap<>();
        int l = json.indexOf("\"labels\"");
        if (l >= 0) {
            int end = json.indexOf('}', l);
            Matcher m = Pattern.compile("\"([^\"]+)\":\\s*\"0x([0-9a-fA-F]+)\"")
                    .matcher(json.substring(l + 8, end < 0 ? json.length() : end));
            while (m.find()) {
                labels.putIfAbsent((int) Long.parseLong(m.group(2), 16), m.group(1));
            }
        }
        if (byAddr.isEmpty()) {
            return EMPTY;
        }
        return new ProgramSource(source, byAddr, labels, lines);
    }
}
