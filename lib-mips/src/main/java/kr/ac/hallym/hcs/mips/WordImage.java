/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * 메모리 부품의 초기 내용(실행 이미지의 .text 또는 .data). 바뀌지 않는 값이다.
 *
 * <p>.circ에는 다음 텍스트로 저장한다. 첫 줄은 형식 이름, 그다음 줄마다 시작 주소와 이어지는 워드들이다.
 * <pre>
 * hcs-words 1
 * 00400000 3c041001 34020004 0000000c
 * 10010000 000a6968
 * </pre>
 *
 * <p>Stack은 실행 이미지의 {@code reg $sp}를 깊이 기준으로 기억한다(D-126). 새 속성 이름을 더하지 않으려고 이 형식에
 * <b>주소만 있는 줄</b> 하나로 적는다(워드를 적지 않으므로 메모리 내용은 그대로다). 옛 lib-mips의 {@link #parse}도
 * 이 줄을 오류 없이 건너뛴다.
 * <pre>
 * hcs-words 1
 * 7ffff000
 * </pre>
 */
final class WordImage {
    static final String HEADER = "hcs-words 1";
    static final WordImage EMPTY = new WordImage(new TreeMap<Long, Integer>());
    private static final int WORDS_PER_LINE = 8;

    /** 주소(부호 없는 32비트) → 워드. */
    private final TreeMap<Long, Integer> words;
    /** Stack 깊이 기준으로 기억한 $sp 시작 값(주소만 있는 줄). 없으면 null. */
    private final Long initialSp;
    private final SparseMemory memory = new SparseMemory();

    private WordImage(TreeMap<Long, Integer> words) {
        this(words, null);
    }

    private WordImage(TreeMap<Long, Integer> words, Long initialSp) {
        this.words = words;
        this.initialSp = initialSp;
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

    /** 실행 이미지의 {@code reg $sp}(Stack 깊이 기준). 없으면 null. */
    Long initialSp() {
        return initialSp;
    }

    /** 같은 워드에 깊이 기준만 바꾼 것. sp가 null이면 기준을 지운다. */
    WordImage withInitialSp(Long sp) {
        Long v = sp == null ? null : sp & 0xffffffffL;
        if (v == null ? initialSp == null : v.equals(initialSp)) {
            return this;
        }
        return new WordImage(words, v);
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

    /** 주소 → 워드(읽기 전용). v2 엔진의 디스어셈블·요약·다시 불러오기 비교가 쓴다(D-147). */
    SortedMap<Long, Integer> words() {
        return Collections.unmodifiableSortedMap(words);
    }

    Long firstAddress() {
        return words.isEmpty() ? null : words.firstKey();
    }

    Long lastAddress() {
        return words.isEmpty() ? null : words.lastKey();
    }

    String format() {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        if (initialSp != null) {
            sb.append(hex(initialSp)).append('\n'); // 주소만 있는 줄: Stack 깊이 기준
        }
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
        Long sp = null;
        for (int i = 1; i < lines.length; i += 1) {
            StringTokenizer tok = new StringTokenizer(lines[i]);
            if (!tok.hasMoreTokens()) {
                continue;
            }
            long addr = Long.parseLong(tok.nextToken(), 16);
            if (!tok.hasMoreTokens()) {
                sp = addr & 0xffffffffL; // 주소만 있는 줄
                continue;
            }
            while (tok.hasMoreTokens()) {
                words.put(addr & 0xfffffffcL, (int) Long.parseLong(tok.nextToken(), 16));
                addr += 4;
            }
        }
        return new WordImage(words, sp);
    }

    static String hex(long v) {
        String s = Long.toHexString(v & 0xffffffffL);
        return "00000000".substring(s.length()) + s;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WordImage)) {
            return false;
        }
        WordImage w = (WordImage) o;
        return w.words.equals(words) && (initialSp == null ? w.initialSp == null : initialSp.equals(w.initialSp));
    }

    @Override
    public int hashCode() {
        return words.hashCode() * 31 + (initialSp == null ? 0 : initialSp.hashCode());
    }
}
