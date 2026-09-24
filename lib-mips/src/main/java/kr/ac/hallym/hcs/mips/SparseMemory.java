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
 * 한 번도 쓰지 않은 칸은 SPIM처럼 0이다.
 */
final class SparseMemory {
    static final int PAGE_WORDS = 1024; // 4KB

    private static final class Page {
        final int[] words = new int[PAGE_WORDS];
        final boolean[] undefined = new boolean[PAGE_WORDS];

        Page copy() {
            Page p = new Page();
            System.arraycopy(words, 0, p.words, 0, PAGE_WORDS);
            System.arraycopy(undefined, 0, p.undefined, 0, PAGE_WORDS);
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
    }

    void writeUndefined(int addr) {
        Page p = page(addr);
        p.words[slotOf(addr)] = 0;
        p.undefined[slotOf(addr)] = true;
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

    int pageCount() {
        return pages.size();
    }
}
