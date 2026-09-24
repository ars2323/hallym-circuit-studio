/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.ext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * .circ에 넣는 추가 정보(PLAN.md 7.0, D-024). 사용자가 직접 지정한 정보(터널 색, 신호 그룹, 영역 메모)만
 * 담는다. 회로 이름마다 항목 목록을 두고, 항목은 종류와 속성(문자열)만 가진다. 모르는 종류·속성도 그대로
 * 두었다가 다시 저장한다(새 버전이 만든 파일을 옛 버전이 열고 저장해도 잃지 않게).
 */
public final class CircExtension {
    /** 한 항목. 예: 종류 "tunnel", 속성 label=PC, color=#1f77b4. */
    public static final class Item {
        private final String kind;
        private final Map<String, String> attrs;

        public Item(String kind, Map<String, String> attrs) {
            this.kind = checkName(Objects.requireNonNull(kind));
            for (String name : attrs.keySet()) {
                checkName(name);
            }
            this.attrs = Collections.unmodifiableMap(new LinkedHashMap<>(attrs));
        }

        public String kind() {
            return kind;
        }

        public Map<String, String> attrs() {
            return attrs;
        }

        public String get(String name) {
            return attrs.get(name);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Item && ((Item) o).kind.equals(kind) && ((Item) o).attrs.equals(attrs);
        }

        @Override
        public int hashCode() {
            return kind.hashCode() * 31 + attrs.hashCode();
        }

        @Override
        public String toString() {
            return kind + attrs;
        }
    }

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");

    /** 종류·속성 이름은 XML 이름으로 그대로 쓰이므로 글자·숫자·_.-만 받는다. */
    static String checkName(String name) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("bad extension name: " + name);
        }
        return name;
    }

    private final Map<String, List<Item>> byCircuit = new TreeMap<>();

    public synchronized boolean isEmpty() {
        return byCircuit.isEmpty();
    }

    /** 확장 정보가 있는 회로 이름(이름 순). */
    public synchronized List<String> circuits() {
        return new ArrayList<>(byCircuit.keySet());
    }

    public synchronized List<Item> items(String circuit) {
        List<Item> items = byCircuit.get(circuit);
        return items == null ? Collections.<Item>emptyList() : new ArrayList<>(items);
    }

    public synchronized void add(String circuit, Item item) {
        byCircuit.computeIfAbsent(circuit, k -> new ArrayList<>()).add(item);
    }

    public synchronized void remove(String circuit, Item item) {
        List<Item> items = byCircuit.get(circuit);
        if (items != null && items.remove(item) && items.isEmpty()) {
            byCircuit.remove(circuit);
        }
    }

    /** 회로 이름이 바뀌면 그 회로의 항목을 옮긴다. */
    public synchronized void renameCircuit(String oldName, String newName) {
        List<Item> items = byCircuit.remove(oldName);
        if (items != null) {
            byCircuit.computeIfAbsent(newName, k -> new ArrayList<>()).addAll(items);
        }
    }

    public synchronized void removeCircuit(String circuit) {
        byCircuit.remove(circuit);
    }

    @Override
    public synchronized boolean equals(Object o) {
        if (!(o instanceof CircExtension)) {
            return false;
        }
        CircExtension other = (CircExtension) o;
        synchronized (other) {
            return byCircuit.equals(other.byCircuit);
        }
    }

    @Override
    public synchronized int hashCode() {
        return byCircuit.hashCode();
    }

    @Override
    public synchronized String toString() {
        return byCircuit.toString();
    }
}
