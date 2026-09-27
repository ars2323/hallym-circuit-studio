/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 시험용 실행 이미지(.hmx)의 자리(Z-06). tests/asm, tests/mips, tests/record의 .s를 vendor/spim이 있을 때
 * {@code hcs-asm -exception}(Hallym MIPS 기본 배치)으로 어셈블해 .hmx 1판으로 적고 굳혀 두었다(D-126, D-141). hcs-asm과
 * vendor/spim은 지웠으므로 다시 만들지 않는다. 만든 파일은 둘째 줄 주석({@link #MARK})으로 표시한다. 배포하지 않는다.
 */
final class HmxFiles {
    static final String MARK = "# hcs-asm -exception으로 만들어 굳힌 시험 이미지(D-126, D-141): 기계어는 SPIM 9.1.24 그대로";

    private HmxFiles() {
    }

    /** 굳힌 .hmx가 있는 폴더와 원본 .s 폴더 쌍: tests/hmx/asm ← tests/asm, tests/hmx/mips ← tests/mips, … */
    static final String[] FOLDERS = {"asm", "mips", "record"};

    /** 원본 .s들. */
    static List<Path> sources(Path tests) throws Exception {
        List<Path> out = new ArrayList<>();
        for (String dir : FOLDERS) {
            try (var list = Files.list(tests.resolve(dir))) {
                list.filter(f -> f.toString().endsWith(".s")).sorted().forEach(out::add);
            }
        }
        return out;
    }

    /** 원본 .s에 대응하는 굳힌 .hmx 경로. */
    static Path hmxFor(Path tests, Path source) {
        String dir = source.getParent().getFileName().toString();
        String name = source.getFileName().toString().replaceAll("\\.s$", ".hmx");
        return tests.resolve("hmx").resolve(dir).resolve(name);
    }
}
