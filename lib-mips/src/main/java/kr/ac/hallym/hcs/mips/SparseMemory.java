/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.util.HashMap;
import java.util.Map;

/**
 * 32비트 byte 주소 공간의 워드 메모리. 쓴 4KB 페이지만 메모리를 차지한다(PLAN.md 6.2 "희소 저장").
 * 워드마다 정의 여부를 따로 기억한다. 정의되지 않은 값(X)을 쓴 칸은 읽을 때도 정의되지 않는다.
 * 한 번도 쓰지 않은 칸은 SPIM처럼 0이다. 초기 내용이나 쓰기로 값을 가진 칸은 따로 표시해 둔다(몸체의 "data N words",
 * D-140).
 */
final class SparseMemory {
    static final int PAGE_WORDS = 1024; // 4KB

    private static final class Page {
        final int[] words = new int[PAGE_WORDS];
        final boolean[] undefined = new boolean[PAGE_WORDS];
        /** 초기 내용이나 쓰기로 값을 가진 칸. */
        final boolean[] held = new boolean[PAGE_WORDS];

        Page copy() {
            Page p = new Page();
            System.arraycopy(words, 0, p.words, 0, PAGE_WORDS);
            System.arraycopy(undefined, 0, p.undefined, 0, PAGE_WORDS);
            System.arraycopy(held, 0, p.held, 0, PAGE_WORDS);
            return p;
        }
    }

    private final Map<Integer, Page> pages = new HashMap<Integer, Page>();

    SparseMemory copy() {
        SparseMemory m = new SparseMemory();
        for (Map.Entry<Integer, Page> e : pages.entrySet()) {
            m.pages.put(e.getKey(), e.getValue().copy());
        }
        return m;
    }

    private static int pageOf(int addr) {
        return addr >>> 12;
    }

    private static int slotOf(int addr) {
        return (addr >>> 2) & (PAGE_WORDS - 1);
    }

    /** addr가 가리키는 워드(하위 2비트 무시). */
    int read(int addr) {
        Page p = pages.get(pageOf(addr));
        return p == null ? 0 : p.words[slotOf(addr)];
    }

    boolean isDefined(int addr) {
        Page p = pages.get(pageOf(addr));
        return p == null || !p.undefined[slotOf(addr)];
    }

    void write(int addr, int word) {
        Page p = page(addr);
        p.words[slotOf(addr)] = word;
        p.undefined[slotOf(addr)] = false;
        p.held[slotOf(addr)] = true;
    }

    void writeUndefined(int addr) {
        Page p = page(addr);
        p.words[slotOf(addr)] = 0;
        p.undefined[slotOf(addr)] = true;
        p.held[slotOf(addr)] = true;
    }

    /** [low, high) 안에서 초기 내용이나 쓰기로 값을 가진 워드 수. */
    long heldWords(long low, long high) {
        long n = 0;
        for (Map.Entry<Integer, Page> e : pages.entrySet()) {
            long start = (e.getKey() & 0xfffffL) << 12;
            if (start + 4096 <= low || start >= high) {
                continue;
            }
            boolean[] held = e.getValue().held;
            for (int i = 0; i < PAGE_WORDS; i += 1) {
                long a = start + 4L * i;
                if (held[i] && a >= low && a < high) {
                    n += 1;
                }
            }
        }
        return n;
    }

    /** addr의 바이트. 리틀 엔디언(SPIM과 같음). */
    int readByte(int addr) {
        return (read(addr) >>> (8 * (addr & 3))) & 0xff;
    }

    private Page page(int addr) {
        Integer key = pageOf(addr);
        Page p = pages.get(key);
        if (p == null) {
            p = new Page();
            pages.put(key, p);
        }
        return p;
    }

    /** 쓴 적 있는 4KB 페이지의 시작 주소들(오름차순). */
    long[] pageAddresses() {
        long[] out = new long[pages.size()];
        int i = 0;
        for (Integer k : pages.keySet()) {
            out[i++] = (k & 0xfffffL) << 12;
        }
        java.util.Arrays.sort(out);
        return out;
    }

    int pageCount() {
        return pages.size();
    }
}
