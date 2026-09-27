/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

/**
 * 실행 이미지를 읽지 못한 이유 하나. 문장은 "줄 번호 → 무엇이 틀렸는지 → 무엇을 할지" 순서다(D-126).
 * 줄 번호가 없으면(예: entry 줄이 아예 없음) {@link #line}은 0이다.
 */
public final class HmxError {
    /** 1부터. 0: 특정 줄이 아님. */
    public final int line;
    /** 줄 번호 머리가 없는 문장. */
    public final Msg what;

    HmxError(int line, Msg what) {
        this.line = line;
        this.what = what;
    }

    /** 줄 번호를 붙인 문장. 예: {@code 12번째 줄: …}, {@code Line 12: …}. */
    public String text(boolean korean) {
        if (line <= 0) {
            return what.get(korean);
        }
        return (korean ? line + "번째 줄: " : "Line " + line + ": ") + what.get(korean);
    }

    @Override
    public String toString() {
        return text(false);
    }
}
