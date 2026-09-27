/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kr.ac.hallym.hcs.mips.image.ExecutableImage;
import kr.ac.hallym.hcs.mips.image.SourceCheck;

/**
 * 시험용 실행 이미지(.hmx) 만들기(Z-06). Hallym MIPS의 내보내기가 아직 없으므로 tests/asm, tests/mips의 .s를
 * {@code hcs-asm -exception}(Hallym MIPS 기본 배치)으로 어셈블한 이미지를 .hmx 1판으로 적는다. 만든 파일은 첫 주석으로
 * 표시하고, Hallym MIPS 내보내기가 나오면 그 결과로 바꾼다(#373). 배포하지 않는다.
 */
final class HmxFiles {
    static final String MARK = "# hcs-asm -exception으로 생성: Hallym MIPS 내보내기가 나오면 교체";
    /** .data 안에서 이만큼 이상 이어지는 0 바이트는 {@code zero} 줄로 적는다. */
    static final int ZERO_BYTES = 16;
    /** .text 안에서 이만큼 이상 이어지는 0 워드는 {@code zero} 줄로 적는다. */
    static final int ZERO_WORDS = 4;

    private HmxFiles() {
    }

    /** 만든 .hmx가 있는 폴더와 원본 .s 폴더 쌍: tests/hmx/asm ← tests/asm, tests/hmx/mips ← tests/mips. */
    static final String[] FOLDERS = {"asm", "mips"};

    /** 원본 .s들(어셈블 오류가 나는 시험 입력은 hcs-asm이 판단한 뒤 부르는 쪽이 뺀다). */
    static List<Path> sources(Path tests) throws Exception {
        List<Path> out = new ArrayList<>();
        for (String dir : FOLDERS) {
            try (var list = Files.list(tests.resolve(dir))) {
                list.filter(f -> f.toString().endsWith(".s")).sorted().forEach(out::add);
            }
        }
        return out;
    }

    /** 원본 .s에 대응하는 만든 .hmx 경로. */
    static Path hmxFor(Path tests, Path source) {
        String dir = source.getParent().getFileName().toString();
        String name = source.getFileName().toString().replaceAll("\\.s$", ".hmx");
        return tests.resolve("hmx").resolve(dir).resolve(name);
    }

    /** image를 .hmx 1판 글로. source는 .hmx 폴더 기준 원본 경로. */
    static String write(ExecutableImage img, String source, File sourceFile) throws Exception {
        StringBuilder sb = new StringBuilder("HALLYM-EXEC 1\n").append(MARK).append('\n');
        key(sb, "source", source);
        key(sb, "source-sha256", SourceCheck.sha256(sourceFile));
        key(sb, "produced-by", img.producedBy());
        key(sb, "endian", img.endian().word);
        sb.append('\n');
        key(sb, "entry", ExecutableImage.hex(img.entry()));
        for (Map.Entry<String, Long> r : img.regs().entrySet()) {
            key(sb, "reg " + r.getKey(), ExecutableImage.hex(r.getValue()));
        }
        if (!img.symbols().isEmpty()) {
            sb.append('\n');
        }
        for (Map.Entry<String, Long> s : img.symbols().entrySet()) {
            key(sb, "symbol " + s.getKey(), ExecutableImage.hex(s.getValue()));
        }
        for (ExecutableImage.Segment seg : img.segments()) {
            sb.append('\n').append(seg.kind.directive).append(' ').append(ExecutableImage.hex(seg.start)).append(' ')
                    .append(seg.kind.units).append(' ').append(seg.count).append('\n');
            if (seg.kind == ExecutableImage.Kind.TEXT) {
                text(sb, img, seg);
            } else {
                data(sb, img, seg);
            }
        }
        return sb.toString();
    }

    private static void key(StringBuilder sb, String key, String value) {
        sb.append(key);
        for (int i = key.length(); i < 14; i += 1) {
            sb.append(' ');
        }
        sb.append(key.length() >= 14 ? " " : "").append(value).append('\n');
    }

    private static void text(StringBuilder sb, ExecutableImage img, ExecutableImage.Segment seg) {
        long a = seg.start;
        while (a < seg.end()) {
            long run = 0;
            while (a + 4 * run < seg.end() && img.textWords().get(a + 4 * run) == 0) {
                run += 1;
            }
            if (run >= ZERO_WORDS) {
                sb.append("zero ").append(run).append('\n');
                a += 4 * run;
                continue;
            }
            sb.append(String.format("%08x", img.textWords().get(a))).append('\n');
            a += 4;
        }
    }

    private static void data(StringBuilder sb, ExecutableImage img, ExecutableImage.Segment seg) {
        long a = seg.start;
        int inLine = 0;
        while (a < seg.end()) {
            long run = 0;
            while (a + run < seg.end() && img.dataBytes().get(a + run) == 0) {
                run += 1;
            }
            if (run >= ZERO_BYTES) {
                if (inLine > 0) {
                    sb.append('\n');
                    inLine = 0;
                }
                sb.append("zero ").append(run).append('\n');
                a += run;
                continue;
            }
            sb.append(inLine == 0 ? "" : " ").append(String.format("%02x", img.dataBytes().get(a)));
            inLine += 1;
            if (inLine == 16) {
                sb.append('\n');
                inLine = 0;
            }
            a += 1;
        }
        if (inLine > 0) {
            sb.append('\n');
        }
    }
}
