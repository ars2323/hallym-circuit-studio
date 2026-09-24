/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * 메모리 부품의 초기 내용(.s를 어셈블한 .text 또는 .data). 바뀌지 않는 값이다.
 *
 * <p>.circ에는 다음 텍스트로 저장한다. 첫 줄은 형식 이름, 그다음 줄마다 시작 주소와 이어지는 워드들이다.
 * <pre>
 * hcs-words 1
 * 00400000 3c041001 34020004 0000000c
 * 10010000 000a6968
 * </pre>
 */
final class WordImage {
    static final String HEADER = "hcs-words 1";
    static final WordImage EMPTY = new WordImage(new TreeMap<Long, Integer>());
    private static final int WORDS_PER_LINE = 8;

    /** 주소(부호 없는 32비트) → 워드. */
    private final TreeMap<Long, Integer> words;
    private final SparseMemory memory = new SparseMemory();

    private WordImage(TreeMap<Long, Integer> words) {
        this.words = words;
        for (Map.Entry<Long, Integer> e : words.entrySet()) {
            memory.write((int) (long) e.getKey(), e.getValue());
        }
    }

    static WordImage of(Map<Long, Integer> words) {
        TreeMap<Long, Integer> copy = new TreeMap<Long, Integer>();
        for (Map.Entry<Long, Integer> e : words.entrySet()) {
            copy.put(e.getKey() & 0xfffffffcL, e.getValue());
        }
        return new WordImage(copy);
    }

    int size() {
        return words.size();
    }

    boolean isEmpty() {
        return words.isEmpty();
    }

    /** 초기 내용을 담은 새 메모리. 실행 중 쓰기는 이 복사본에 한다. */
    SparseMemory newMemory() {
        return memory.copy();
    }

    /** 읽기 전용 부품(Instruction Memory)이 직접 읽는다. */
    int read(int addr) {
        return memory.read(addr);
    }

    Long firstAddress() {
        return words.isEmpty() ? null : words.firstKey();
    }

    Long lastAddress() {
        return words.isEmpty() ? null : words.lastKey();
    }

    String format() {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        long next = -1;
        int inLine = 0;
        for (Map.Entry<Long, Integer> e : words.entrySet()) {
            long addr = e.getKey();
            if (addr != next || inLine == WORDS_PER_LINE) {
                if (next != -1) {
                    sb.append('\n');
                }
                sb.append(hex(addr));
                inLine = 0;
            }
            sb.append(' ').append(hex(e.getValue() & 0xffffffffL));
            next = addr + 4;
            inLine += 1;
        }
        if (next != -1) {
            sb.append('\n');
        }
        return sb.toString();
    }

    /** {@link #format()}의 역. 형식이 틀리면 IllegalArgumentException. */
    static WordImage parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return EMPTY;
        }
        String[] lines = trimmed.split("\r?\n");
        if (!lines[0].trim().equals(HEADER)) {
            throw new IllegalArgumentException("expected '" + HEADER + "'");
        }
        TreeMap<Long, Integer> words = new TreeMap<Long, Integer>();
        for (int i = 1; i < lines.length; i += 1) {
            StringTokenizer tok = new StringTokenizer(lines[i]);
            if (!tok.hasMoreTokens()) {
                continue;
            }
            long addr = Long.parseLong(tok.nextToken(), 16);
            while (tok.hasMoreTokens()) {
                words.put(addr & 0xfffffffcL, (int) Long.parseLong(tok.nextToken(), 16));
                addr += 4;
            }
        }
        return new WordImage(words);
    }

    static String hex(long v) {
        String s = Long.toHexString(v & 0xffffffffL);
        return "00000000".substring(s.length()) + s;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WordImage && ((WordImage) o).words.equals(words);
    }

    @Override
    public int hashCode() {
        return words.hashCode();
    }
}
