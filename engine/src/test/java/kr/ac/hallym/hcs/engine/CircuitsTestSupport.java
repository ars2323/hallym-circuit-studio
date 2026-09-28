/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.engine.edit.CircuitService;

/** 테스트 도우미: 엔진 스레드에서 읽은 파일 구조(file.changed와 같은 모양). */
final class CircuitsTestSupport {
    private CircuitsTestSupport() {
    }

    static JsonObject fileJson(InProcess e, String fileId) {
        try {
            return e.onEngine(() -> CircuitService.fileJson(e.engine.files().get(fileId)));
        } catch (Exception x) {
            throw new AssertionError(x);
        }
    }
}
