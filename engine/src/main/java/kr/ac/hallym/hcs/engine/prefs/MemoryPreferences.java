/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.prefs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;

/**
 * 메모리에만 있는 환경설정 노드(D-134). 디스크·레지스트리를 읽지도 쓰지도 않는다. 값은 엔진이 끝나면 사라진다.
 * 잠금은 {@link AbstractPreferences}가 노드마다 한다.
 */
final class MemoryPreferences extends AbstractPreferences {
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, MemoryPreferences> children = new LinkedHashMap<>();

    MemoryPreferences(MemoryPreferences parent, String name) {
        super(parent, name);
    }

    @Override
    protected void putSpi(String key, String value) {
        values.put(key, value);
    }

    @Override
    protected String getSpi(String key) {
        return values.get(key);
    }

    @Override
    protected void removeSpi(String key) {
        values.remove(key);
    }

    @Override
    protected void removeNodeSpi() {
        values.clear();
        children.clear();
        if (parent() instanceof MemoryPreferences) {
            ((MemoryPreferences) parent()).children.remove(name());
        }
    }

    @Override
    protected String[] keysSpi() {
        return values.keySet().toArray(new String[0]);
    }

    @Override
    protected String[] childrenNamesSpi() {
        return children.keySet().toArray(new String[0]);
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
        return children.computeIfAbsent(name, n -> new MemoryPreferences(this, n));
    }

    @Override
    protected void syncSpi() {
        // 저장소가 없다
    }

    @Override
    protected void flushSpi() {
        // 저장소가 없다
    }
}
