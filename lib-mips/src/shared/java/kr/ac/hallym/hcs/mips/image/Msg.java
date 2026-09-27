/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

/**
 * 영어·한국어 두 벌의 설명 문장. 공용 코드는 Logisim의 언어 설정을 모르므로 두 벌을 함께 넘기고, 쓰는 쪽이 고른다
 * (트랙 A는 lib-mips {@code Text.of(en, ko)}와 같은 규칙, D-049). 문장 안의 이름(키, 레지스터, 파일 이름)은 영어
 * 그대로 두고, 그 바로 뒤에 조사를 붙이지 않는다(D-126).
 */
public final class Msg {
    public final String en;
    public final String ko;

    private Msg(String en, String ko) {
        this.en = en;
        this.ko = ko;
    }

    public static Msg of(String en, String ko) {
        return new Msg(en, ko);
    }

    public String get(boolean korean) {
        return korean ? ko : en;
    }

    @Override
    public String toString() {
        return en;
    }
}
