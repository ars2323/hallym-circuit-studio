/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.cburch.logisim.proj.Project;

/**
 * 한 프로젝트(시뮬레이션)에 있는 Data Memory·Stack의 상태 목록. Console의 print_string이 문자열을
 * 메모리에서 바이트 단위로 직접 읽을 때 쓴다(PLAN.md 6.9). 원조 2.7.1은 다른 부품의 상태를 얻는
 * 공개 API가 없어서, 메모리 부품이 전파될 때마다 스스로 등록한다.
 */
final class MemoryRegistry {
    interface View {
        boolean contains(int addr);

        boolean isDefined(int addr);

        int readByte(int addr);
    }

    private static final class Entry {
        final WeakReference<View> view;
        long touched;

        Entry(View view, long touched) {
            this.view = new WeakReference<View>(view);
            this.touched = touched;
        }
    }

    private static final Map<Object, List<Entry>> BY_PROJECT = new WeakHashMap<Object, List<Entry>>();
    private static final Object NO_PROJECT = new Object();
    private static long clock;

    private MemoryRegistry() {
    }

    private static Object key(Project project) {
        return project == null ? NO_PROJECT : project;
    }

    static synchronized void touch(Project project, View view) {
        clock += 1;
        List<Entry> entries = BY_PROJECT.get(key(project));
        if (entries == null) {
            entries = new ArrayList<Entry>();
            BY_PROJECT.put(key(project), entries);
        }
        for (Iterator<Entry> it = entries.iterator(); it.hasNext();) {
            Entry e = it.next();
            View v = e.view.get();
            if (v == null) {
                it.remove();
            } else if (v == view) {
                e.touched = clock;
                return;
            }
        }
        entries.add(new Entry(view, clock));
    }

    /** addr를 담은 메모리 중 가장 최근에 전파된 것. 리셋 전의 옛 상태보다 지금 상태가 이긴다. */
    static synchronized View find(Project project, int addr) {
        List<Entry> entries = BY_PROJECT.get(key(project));
        View best = null;
        long bestTouched = -1;
        if (entries != null) {
            for (Entry e : entries) {
                View v = e.view.get();
                if (v != null && e.touched > bestTouched && v.contains(addr)) {
                    best = v;
                    bestTouched = e.touched;
                }
            }
        }
        return best;
    }
}
