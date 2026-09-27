/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

/** 실행 이미지 파일(.hmx)의 머리 규칙(docs/hmx.md). */
public final class HmxFormat {
    /** 첫 줄의 첫 낱말. */
    public static final String MAGIC = "HALLYM-EXEC";
    /** 이 도구가 읽는 판. 첫 줄의 판이 이와 다르면 읽지 않는다. */
    public static final int VERSION = 1;
    /** 파일 이름 끝. */
    public static final String EXTENSION = ".hmx";

    private HmxFormat() {
    }

    /** 첫 줄로 쓸 글자. 예: {@code HALLYM-EXEC 1}. */
    public static String header() {
        return MAGIC + " " + VERSION;
    }
}
