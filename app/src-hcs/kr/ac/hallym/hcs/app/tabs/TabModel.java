/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tabs;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 파일 탭 목록(PLAN.md 11.1, #68). 열린 파일마다 탭 하나, 순서, 활성 탭, 저장 안 된 변경 표시를 가진다.
 * GUI와 Logisim 프로젝트를 모르는 순수 모델이라 GUI 없이 테스트한다. 키(key)는 탭을 가진 쪽이 정한다
 * (앱에서는 Project).
 *
 * @param <K> 탭 키
 */
public final class TabModel<K> {
    /** 탭 하나. file은 아직 저장하지 않은 새 파일이면 null. */
    public static final class Tab<K> {
        final K key;
        File file;
        String title;
        boolean dirty;
        /** 이 파일이 쓰는 라이브러리 파일이 저장되어 새 버전을 불러왔다(P-03). 탭을 보면 지운다. */
        boolean updated;

        Tab(K key, File file, String title) {
            this.key = key;
            this.file = file;
            this.title = title;
        }

        public K key() {
            return key;
        }

        public File file() {
            return file;
        }

        public String title() {
            return title;
        }

        public boolean dirty() {
            return dirty;
        }

        public boolean updated() {
            return updated;
        }
    }

    /**
     * 같은 제목의 탭을 가르는 덧말(V-05): 제목이 겹치는 탭마다, 다른 탭들과 구분되는 가장 짧은 상위 폴더 경로 꼬리
     * ({@code hw3}, 바로 위 폴더 이름이 겹치면 {@code tests/circ}). 저장한 적 없는 탭과 겹치지 않는 탭은 빠진다. 결과는
     * 열쇠 → 덧말.
     */
    public static <K> Map<K, String> distinguishers(List<Tab<K>> tabs) {
        Map<K, String> out = new LinkedHashMap<>();
        Map<String, List<Tab<K>>> byTitle = new LinkedHashMap<>();
        for (Tab<K> t : tabs) {
            if (t.file != null) {
                byTitle.computeIfAbsent(t.title, k -> new ArrayList<>()).add(t);
            }
        }
        for (List<Tab<K>> group : byTitle.values()) {
            if (group.size() < 2) {
                continue;
            }
            Map<K, List<String>> dirs = new LinkedHashMap<>();
            for (Tab<K> t : group) {
                List<String> parts = new ArrayList<>();
                for (File d = t.file.getAbsoluteFile().getParentFile(); d != null; d = d.getParentFile()) {
                    parts.add(d.getName().isEmpty() ? d.getPath() : d.getName()); // 가까운 폴더부터
                }
                dirs.put(t.key, parts);
            }
            for (Tab<K> t : group) {
                List<String> mine = dirs.get(t.key);
                int n = 1;
                for (; n < mine.size(); n++) {
                    boolean unique = true;
                    for (Tab<K> o : group) {
                        if (o != t && tail(dirs.get(o.key), n).equals(tail(mine, n))) {
                            unique = false;
                        }
                    }
                    if (unique) {
                        break;
                    }
                }
                out.put(t.key, tail(mine, Math.min(n, mine.size())));
            }
        }
        return out;
    }

    /** 가까운 폴더 n개를 경로 순서로: {@code tests/circ}. */
    private static String tail(List<String> parts, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = Math.min(n, parts.size()) - 1; i >= 0; i--) {
            sb.append(parts.get(i));
            if (i > 0) {
                sb.append('/');
            }
        }
        return sb.toString();
    }

    public interface Listener {
        void tabsChanged();
    }

    private final List<Tab<K>> tabs = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private K active;
    /** 창을 분리한 탭(P-06): 겹쳐 두는 무리에서 빠져 제 창으로 보인다. */
    private final java.util.Set<K> detached = new java.util.LinkedHashSet<>();

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void fire() {
        for (Listener l : listeners) {
            l.tabsChanged();
        }
    }

    public synchronized List<Tab<K>> tabs() {
        return Collections.unmodifiableList(new ArrayList<>(tabs));
    }

    public synchronized K active() {
        return active;
    }

    public synchronized int size() {
        return tabs.size();
    }

    /** 같은 파일을 보여 주는 탭의 키. 없으면 null. 경로는 정규 경로로 비교한다. */
    public synchronized K find(File file) {
        if (file == null) {
            return null;
        }
        File c = canonical(file);
        for (Tab<K> t : tabs) {
            if (t.file != null && canonical(t.file).equals(c)) {
                return t.key;
            }
        }
        return null;
    }

    /** 새 탭을 활성 탭 바로 뒤에 넣고 활성으로 한다. 이미 있는 키면 활성으로만 한다. */
    public void add(K key, File file, String title) {
        synchronized (this) {
            if (indexOf(key) < 0) {
                int at = active == null ? tabs.size() : indexOf(active) + 1;
                tabs.add(at, new Tab<>(key, file, title));
            }
            active = key;
        }
        fire();
    }

    public void remove(K key) {
        synchronized (this) {
            detached.remove(key);
            int i = indexOf(key);
            if (i < 0) {
                return;
            }
            tabs.remove(i);
            if (Objects.equals(active, key)) {
                // 닫은 탭의 오른쪽, 없으면 왼쪽 탭으로
                active = tabs.isEmpty() ? null : tabs.get(Math.min(i, tabs.size() - 1)).key;
            }
        }
        fire();
    }

    public void activate(K key) {
        synchronized (this) {
            int i = indexOf(key);
            if (i < 0 || Objects.equals(active, key)) {
                return;
            }
            active = key;
            tabs.get(i).updated = false; // 본 탭은 "Updated"를 지운다
        }
        fire();
    }

    /** 라이브러리가 새로 불러와졌다는 표시(P-03). 지금 보는 탭이면 켜지 않는다. */
    public void markUpdated(K key) {
        synchronized (this) {
            int i = indexOf(key);
            if (i < 0 || Objects.equals(active, key) || tabs.get(i).updated) {
                return;
            }
            tabs.get(i).updated = true;
        }
        fire();
    }

    /** 파일 이름이나 저장 상태가 바뀌었을 때. */
    public void update(K key, File file, String title, boolean dirty) {
        synchronized (this) {
            int i = indexOf(key);
            if (i < 0) {
                return;
            }
            Tab<K> t = tabs.get(i);
            if (Objects.equals(t.file, file) && Objects.equals(t.title, title) && t.dirty == dirty) {
                return;
            }
            t.file = file;
            t.title = title;
            t.dirty = dirty;
        }
        fire();
    }

    /** 탭을 from에서 to 자리로 옮긴다. */
    public void move(int from, int to) {
        synchronized (this) {
            if (from == to || from < 0 || from >= tabs.size() || to < 0 || to >= tabs.size()) {
                return;
            }
            tabs.add(to, tabs.remove(from));
        }
        fire();
    }

    /** 탭을 제 창으로 분리한다(P-06). */
    public void detach(K key) {
        synchronized (this) {
            if (indexOf(key) < 0 || !detached.add(key)) {
                return;
            }
        }
        fire();
    }

    /** 분리한 창을 다시 겹치는 무리로 넣는다. */
    public void attach(K key) {
        synchronized (this) {
            if (!detached.remove(key)) {
                return;
            }
        }
        fire();
    }

    /** 이 탭이 있는가. */
    public synchronized boolean has(K key) {
        return indexOf(key) >= 0;
    }

    public synchronized boolean isDetached(K key) {
        return detached.contains(key);
    }

    /** 분리한 탭 가운데 저장된 파일의 경로(복원용). */
    public synchronized List<String> detachedFiles() {
        List<String> ret = new ArrayList<>();
        for (Tab<K> t : tabs) {
            if (t.file != null && detached.contains(t.key)) {
                ret.add(t.file.getAbsolutePath());
            }
        }
        return ret;
    }

    /** 다시 실행할 때 열 파일(탭 순서, 저장된 파일만). */
    public synchronized List<String> restoreList() {
        List<String> ret = new ArrayList<>();
        for (Tab<K> t : tabs) {
            if (t.file != null) {
                ret.add(t.file.getAbsolutePath());
            }
        }
        return ret;
    }

    /** 복원 목록에서 활성 탭의 위치. 활성 탭이 저장 안 된 새 파일이면 -1. */
    public synchronized int restoreActive() {
        int n = 0;
        for (Tab<K> t : tabs) {
            if (t.file != null) {
                if (Objects.equals(t.key, active)) {
                    return n;
                }
                n++;
            }
        }
        return -1;
    }

    private int indexOf(K key) {
        for (int i = 0; i < tabs.size(); i++) {
            if (Objects.equals(tabs.get(i).key, key)) {
                return i;
            }
        }
        return -1;
    }

    static File canonical(File f) {
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return f.getAbsoluteFile();
        }
    }
}
