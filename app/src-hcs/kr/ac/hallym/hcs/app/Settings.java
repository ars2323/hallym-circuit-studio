/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;

/**
 * 앱 환경설정(PLAN.md 11.0). 툴바 모양, 라벨 밀도, 단축키, 최근 파일, 열린 탭처럼 사람마다 다른 설정을 둔다.
 * .circ에는 넣지 않는다. 설정 폴더의 {@code settings.properties} 한 파일에 키=값으로 저장한다.
 * 목록 값은 {@code key.0}, {@code key.1}, ... 으로 나눠 저장한다.
 */
public final class Settings {
    static final String FILE_NAME = "settings.properties";

    private static Settings instance;

    private final File file;
    private final Properties props = new Properties();

    Settings(File file) {
        this.file = file;
        if (file.isFile()) {
            try (InputStream in = Files.newInputStream(file.toPath())) {
                props.load(in);
            } catch (IOException | IllegalArgumentException e) {
                props.clear(); // 깨진 설정은 버리고 기본값으로 시작한다
            }
        }
    }

    /** 설정 폴더의 설정. 처음 부를 때 읽는다. */
    public static synchronized Settings get() {
        if (instance == null) {
            instance = new Settings(new File(AppDirs.config(), FILE_NAME));
        }
        return instance;
    }

    public synchronized String getString(String key, String def) {
        String v = props.getProperty(key);
        return v == null ? def : v;
    }

    public synchronized int getInt(String key, int def) {
        String v = props.getProperty(key);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public synchronized boolean getBoolean(String key, boolean def) {
        String v = props.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    public synchronized List<String> getList(String key) {
        List<String> ret = new ArrayList<>();
        for (int i = 0; ; i++) {
            String v = props.getProperty(key + "." + i);
            if (v == null) {
                return ret;
            }
            ret.add(v);
        }
    }

    public synchronized void set(String key, String value) {
        if (value == null) {
            props.remove(key);
        } else {
            props.setProperty(key, value);
        }
    }

    public void set(String key, int value) {
        set(key, Integer.toString(value));
    }

    public void set(String key, boolean value) {
        set(key, Boolean.toString(value));
    }

    public synchronized void setList(String key, List<String> values) {
        for (int i = 0; props.remove(key + "." + i) != null; i++) {
            // 옛 목록을 지운다
        }
        for (int i = 0; i < values.size(); i++) {
            props.setProperty(key + "." + i, values.get(i));
        }
    }

    /** 설정 파일에 쓴다. 임시 파일에 쓴 뒤 바꿔 끼워, 쓰다가 꺼져도 옛 설정이 남는다. */
    public synchronized void save() throws IOException {
        File dir = file.getAbsoluteFile().getParentFile();
        Files.createDirectories(dir.toPath());
        File tmp = File.createTempFile("settings", ".tmp", dir);
        try {
            try (OutputStream out = Files.newOutputStream(tmp.toPath())) {
                sorted().store(out, "Hallym Circuit Studio");
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
    }

    /** 키 순서로 쓰는 Properties. 파일을 사람이 읽고 비교하기 쉽게 한다. */
    private Properties sorted() {
        final TreeMap<Object, Object> map = new TreeMap<>(props);
        Properties p = new Properties() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.util.Set<java.util.Map.Entry<Object, Object>> entrySet() {
                return Collections.unmodifiableSet(map.entrySet());
            }
        };
        p.putAll(map);
        return p;
    }
}
