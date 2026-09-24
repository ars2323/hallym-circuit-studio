/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.cburch.logisim.proj.Project;

/**
 * 한 프로젝트(시뮬레이션)에 있는 Data Memory·Stack의 지금 상태. Console의 print_string이 문자열을 바이트
 * 단위로 읽고, 메모리 부품이 "어느 영역에도 없는 주소", "영역 겹침"을 알아낼 때 쓴다. 원조 2.7.1은 다른 부품의
 * 상태를 얻는 공개 API가 없어서, 메모리 부품이 전파될 때마다 스스로 등록한다. 부품(Instance)마다 가장 최근
 * 상태 하나만 둔다. 시뮬레이션을 리셋하면 새 상태가 옛 상태를 밀어낸다.
 */
final class MemoryRegistry {
    interface View {
        boolean contains(int addr);

        /** 영역 {낮은 주소, 높은 주소(제외)}. */
        long[] region();

        /** Stack이면 true. 한계 아래 주소를 "스택 사용량 초과"로 본다. */
        boolean growsDown();

        boolean isDefined(int addr);

        int readByte(int addr);
    }

    private static final Map<Object, Map<Object, View>> BY_PROJECT = new WeakHashMap<Object, Map<Object, View>>();
    private static final Object NO_PROJECT = new Object();

    private MemoryRegistry() {
    }

    private static Object key(Project project) {
        return project == null ? NO_PROJECT : project;
    }

    /** owner(부품 인스턴스)의 지금 상태를 알린다. */
    static synchronized void touch(Project project, Object owner, View view) {
        Map<Object, View> views = BY_PROJECT.get(key(project));
        if (views == null) {
            views = new WeakHashMap<Object, View>();
            BY_PROJECT.put(key(project), views);
        }
        views.put(owner, view);
    }

    static synchronized List<View> all(Project project) {
        Map<Object, View> views = BY_PROJECT.get(key(project));
        return views == null ? new ArrayList<View>() : new ArrayList<View>(views.values());
    }

    /** addr를 담은 메모리. 영역이 겹치면 아무거나 하나(겹침은 따로 알린다). */
    static synchronized View find(Project project, int addr) {
        for (View v : all(project)) {
            if (v.contains(addr)) {
                return v;
            }
        }
        return null;
    }
}
