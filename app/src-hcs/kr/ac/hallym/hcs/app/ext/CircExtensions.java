/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.ext;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;

import com.cburch.logisim.file.LogisimFile;

/**
 * 열린 파일({@link LogisimFile})마다 확장 정보를 둔다. 엔진의 LogisimFile은 바꾸지 않고 옆에 붙여 둔다.
 * ProjectActions가 파일을 연 뒤 {@link #afterOpen}, 저장한 뒤 {@link #afterSave}를 부른다.
 */
public final class CircExtensions {
    private static final Map<LogisimFile, CircExtension> BY_FILE = new WeakHashMap<>();

    /** 저장하기 전에 더 이상 가리키는 것이 없는 항목을 지우는 쪽(예: 지운 스플리터의 팔 이름). */
    public interface Pruner {
        void prune(LogisimFile file, CircExtension ext);
    }

    private static final java.util.List<Pruner> PRUNERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addPruner(Pruner p) {
        if (!PRUNERS.contains(p)) {
            PRUNERS.add(p);
        }
    }

    private CircExtensions() {
    }

    /** file의 확장 정보. 없으면 빈 것을 만들어 붙인다. */
    public static synchronized CircExtension of(LogisimFile file) {
        return BY_FILE.computeIfAbsent(file, f -> new CircExtension());
    }

    /** source에서 file을 연 직후: 원조 로더가 건너뛴 확장 요소를 읽어 붙인다. */
    public static void afterOpen(LogisimFile file, File source) throws IOException {
        CircExtension ext = CircExtensionIO.read(source);
        synchronized (CircExtensions.class) {
            BY_FILE.put(file, ext);
        }
    }

    /** file을 dest에 원조 방식으로 저장한 직후: 확장 정보가 있으면 파일 끝에 넣는다. */
    public static void afterSave(LogisimFile file, File dest) throws IOException {
        CircExtension ext;
        synchronized (CircExtensions.class) {
            ext = BY_FILE.get(file);
        }
        if (ext != null) {
            for (Pruner p : PRUNERS) {
                p.prune(file, ext);
            }
        }
        CircExtensionIO.writeInto(dest, ext == null ? new CircExtension() : ext);
    }
}
