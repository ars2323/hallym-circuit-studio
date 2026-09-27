/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import kr.ac.hallym.hcs.mips.disasm.Disassembler;
import kr.ac.hallym.hcs.mips.image.AssemblySource;
import kr.ac.hallym.hcs.mips.image.HmxParser;

/**
 * 불러온 프로그램의 주소 → 원래 줄과 라벨(C-02 사이클 표 머리, PLAN.md 5.1). 불러오는 것은 실행 이미지(.hmx)이고
 * (D-141) .hmx에는 워드마다 원래 줄 번호가 없어(hmx-feedback.md) 줄은 비어 있고 라벨만 이미지의 기호에서 얻는다. 머리는
 * 라벨을 붙인 디스어셈블이다. .s 경로(옛 파일)나 읽을 수 없는 이미지면 비어 있다. 파일이 바뀌지 않았으면 다시 읽지 않는다.
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

    /** source(.hmx)를 읽는다. 같은 파일·수정 시각이면 앞 결과를 쓴다. 실패하면 EMPTY. */
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
        if (AssemblySource.isAssembly(source.getName())) {
            return EMPTY; // 옛 파일의 .s 경로: 읽지 않는다(D-141)
        }
        HmxParser.Result r;
        try {
            r = HmxParser.read(source);
        } catch (IOException e) {
            return EMPTY;
        }
        if (!r.ok() || r.image.symbols().isEmpty()) {
            return EMPTY; // 읽을 수 없는 이미지, 기호 없는 이미지
        }
        return new ProgramSource(source, Collections.<Integer, Integer>emptyMap(),
                new HashMap<>(Disassembler.byAddress(r.image.symbols())), Collections.<String>emptyList());
    }
}
