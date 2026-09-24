/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tabs;

import java.io.File;
import java.io.IOException;
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
    }

    public interface Listener {
        void tabsChanged();
    }

    private final List<Tab<K>> tabs = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private K active;

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
            if (indexOf(key) < 0 || Objects.equals(active, key)) {
                return;
            }
            active = key;
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
