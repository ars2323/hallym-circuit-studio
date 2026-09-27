/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.doc;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.file.Loader;

/**
 * 원조 {@link Loader} 그대로, 오류 창만 없는 것(headless). 원조는 불러오기·저장 중의 오류를 대화상자로 보이고
 * 계속하는데({@link Loader#showError}), 엔진은 그 글을 모아 두었다가 응답에 싣는다.
 */
public final class EngineLoader extends Loader {
    private final List<String> errors = new ArrayList<>();

    public EngineLoader() {
        super(null);
        OwnTools.unshare(getBuiltin()); // 도구 기본값을 다른 파일과 나눠 쓰지 않는다(D-149)
    }

    @Override
    public void showError(String description) {
        synchronized (errors) {
            errors.add(description);
        }
    }

    /** 모인 오류 글을 꺼내고 비운다. */
    public List<String> drainErrors() {
        synchronized (errors) {
            List<String> ret = new ArrayList<>(errors);
            errors.clear();
            return ret;
        }
    }
}
