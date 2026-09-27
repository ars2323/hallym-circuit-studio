/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

import java.util.Locale;

/**
 * 옛 .circ에 남은 .s 경로(D-141). .s 불러오기와 hcs-asm은 없어졌고, Load Program은 Hallym MIPS가 내보낸 실행
 * 이미지(.hmx)만 받는다. 메모리 부품의 {@code source} 속성에 .s(.asm) 경로가 남은 옛 파일은 전과 똑같이 열리고, 고치지
 * 않으면 전과 같은 바이트로 저장된다(속성은 읽기만 한다). 학생이 .hmx를 고르면 불러오기가 그 속성을 새 경로로 바꾼다.
 *
 * <p>트랙 A 우클릭 메뉴(lib-mips)와 v2 엔진의 사실({@code mips.facts}의 {@code assemblySource})이 같은 규칙과 같은
 * 문장을 쓴다.
 */
public final class AssemblySource {
    /** 사실과 할 일(사용자 결정 D-141의 문장, 이름 뒤 조사 규칙 D-126에 맞춘 것). */
    public static final Msg FACT = Msg.of(
            "This file points to a .s file. Load the file exported with Export executable image (.hmx) in Hallym MIPS.",
            "이 파일은 .s 파일을 가리킵니다. Hallym MIPS에서 Export executable image (.hmx) 단추로 내보낸 파일을 불러오세요.");

    private AssemblySource() {
    }

    /** 경로(또는 파일 이름)가 어셈블리 원본(.s, .asm)을 가리키는가. 대소문자와 앞뒤 공백은 가리지 않는다. */
    public static boolean isAssembly(String path) {
        if (path == null) {
            return false;
        }
        String n = path.trim().toLowerCase(Locale.ROOT);
        return n.endsWith(".s") || n.endsWith(".asm");
    }

    /** 경로의 마지막 이름('/'와 '\' 모두 구분자로 본다). 예: {@code lab/sum.s} → {@code sum.s}. */
    public static String fileName(String path) {
        String p = path == null ? "" : path.trim();
        int cut = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
        return cut < 0 ? p : p.substring(cut + 1);
    }

    /** 같은 이름의 실행 이미지 이름. 예: {@code lab/sum.s} → {@code sum.hmx}. */
    public static String imageName(String path) {
        String name = fileName(path);
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name) + ".hmx";
    }
}
