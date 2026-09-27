/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
/**
 * 실행 이미지(executable image): Hallym MIPS가 내보내는 .hmx를 읽는다(Z-01, Z-02). .s 불러오기와 hcs-asm은 없어졌다(D-141, {@link kr.ac.hallym.hcs.mips.image.AssemblySource}).
 * 두 트랙 공용(D-125): Java 8, 외부 의존성 없음, GUI 없음. lib-mips jar와 포크 앱에 같은 소스를 함께 컴파일한다.
 */
package kr.ac.hallym.hcs.mips.image;
