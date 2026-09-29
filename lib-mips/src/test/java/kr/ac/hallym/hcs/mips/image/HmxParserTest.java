/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 실행 이미지(.hmx 1판) 파서와 원본 대조(Z-01, Z-06, D-138). 기준은 Hallym MIPS 명세 docs/hmx-format.md(v2.6.0, v2.4.0과 같음), 명세가
 * 말하지 않은 곳은 docs/hmx.md. 시험 파일은 tests/hmx. Hallym MIPS가 낸 골든은 {@code HallymMipsGoldenTest}.
 */
class HmxParserTest {
    static final Path HMX = Path.of(System.getProperty("hcs.testsDir", "../tests"), "hmx");

    @TempDir
    Path tmp;

    static HmxParser.Result read(String name) throws Exception {
        return HmxParser.read(HMX.resolve(name).toFile());
    }

    static ExecutableImage ok(String name) throws Exception {
        HmxParser.Result r = read(name);
        assertEquals(List.of(), r.errors, name);
        assertTrue(r.ok());
        return r.image;
    }

    /** 오류 문장들(한국어, 줄 번호 포함). */
    static List<String> errorsKo(HmxParser.Result r) {
        assertFalse(r.ok());
        assertNull(r.image, "no image when there is an error");
        List<String> out = new ArrayList<>();
        for (HmxError e : r.errors) {
            out.add(e.text(true));
        }
        return out;
    }

    // ---- 읽히는 파일 ----

    @Test
    void readsTheSpecExample() throws Exception {
        ExecutableImage img = ok("example.hmx");
        assertEquals("example.s", img.source());
        assertEquals("254029244d04e3f07f12c64464d7ca66902ee0a4b9492b39e9e5d8a94eb1c4e4", img.sourceSha256());
        assertEquals("Hallym MIPS 2.2.0", img.producedBy());
        assertEquals("2026-09-27T13:15+09:00", img.assembled());
        assertEquals(ExecutableImage.Endian.LITTLE, img.endian());
        assertEquals(0x00400024L, (long) img.entry());
        assertEquals(0x7ffff000L, (long) img.reg("$sp"));
        assertEquals(0x7ffff000L, (long) img.reg("$29"), "numeric register name");
        assertEquals(0x10008000L, (long) img.reg("$gp"));
        assertEquals(List.of("$sp", "$gp"), new ArrayList<>(img.regs().keySet()));
        assertEquals(List.of("main"), img.symbolsAt(0x00400024L));
        assertEquals(0x10010000L, (long) img.symbols().get("str"));
        // 시작 코드를 포함해 파일 주소 그대로
        assertEquals(14, img.textWords().size());
        assertEquals(0x8fa40000, (int) img.textWords().get(0x00400000L));
        assertEquals(0x3c041001, (int) img.textWords().get(0x00400024L));
        assertEquals(12, img.dataBytes().size());
        assertEquals(Map.of(0x10010000L, 0x6c6c6548, 0x10010004L, 0x494d206f, 0x10010008L, 0x000a5350),
                img.dataWords(), "little endian words, same as SPIM");
        // 요약 글(Z-01의 예)
        assertEquals("14 words (0x00400000\u20130x00400034)",
                ExecutableImage.describe(img.segments(ExecutableImage.Kind.TEXT)));
        assertEquals("12 bytes = 3 words (0x10010000\u20130x1001000b)",
                ExecutableImage.describe(img.segments(ExecutableImage.Kind.DATA)));
        assertEquals(17, img.segments().get(0).line, ".text header line");
    }

    @Test
    void dataLengthNotAMultipleOfFourIsPaddedWithZeros() throws Exception {
        ExecutableImage img = ok("data-odd.hmx");
        // 0x10010000: 00 00 41 42 | 43 44 45 46 | 47 00 00 00 (구간이 주지 않은 바이트는 0)
        assertEquals(Map.of(0x10010000L, 0x42410000, 0x10010004L, 0x46454443, 0x10010008L, 0x00000047),
                img.dataWords());
        assertEquals(7, img.dataBytes().size(), "byte addresses stay as they are");
        assertFalse(img.dataBytes().containsKey(0x10010001L));
        assertFalse(img.dataBytes().containsKey(0x10010009L));
        assertEquals(0x41, (int) img.dataBytes().get(0x10010002L));
        assertEquals(0x47, (int) img.dataBytes().get(0x10010008L));
        assertEquals("7 bytes = 3 words (0x10010002\u20130x10010008)",
                ExecutableImage.describe(img.segments(ExecutableImage.Kind.DATA)));
    }

    @Test
    void zeroRunsAreWordsInTextAndBytesInData() throws Exception {
        ExecutableImage img = ok("zero-runs.hmx");
        assertEquals(Map.of(0x00400000L, 0x24020001, 0x00400004L, 0, 0x00400008L, 0, 0x0040000cL, 0,
                0x00400010L, 0x0000000c), img.textWords());
        ExecutableImage.Segment data = img.segments(ExecutableImage.Kind.DATA).get(0);
        assertEquals(16, data.count);
        assertEquals(2, data.zeroRuns().size(), "two zero runs");
        assertEquals(0x10010001L, data.zeroRuns().get(0).start);
        assertEquals(6, data.zeroRuns().get(0).count);
        assertEquals(0x10010009L, data.zeroRuns().get(1).start);
        assertEquals(7, data.zeroRuns().get(1).count);
        assertEquals(16, img.dataBytes().size(), "zero bytes are defined bytes");
        // 바이트: 01 | 0×6 | 02 03 | 0×7 → 01 00 00 00 / 00 00 00 02 / 03 00 00 00 / 00 00 00 00
        assertEquals(Map.of(0x10010000L, 0x00000001, 0x10010004L, 0x02000000, 0x10010008L, 0x00000003,
                0x1001000cL, 0), img.dataWords());
        assertEquals(1, img.segments(ExecutableImage.Kind.TEXT).get(0).zeroRuns().size());
        ExecutableImage.ZeroRun textZeros = img.segments(ExecutableImage.Kind.TEXT).get(0).zeroRuns().get(0);
        assertEquals(0x00400004L, textZeros.start, "a .text zero run starts after the words before it");
        assertEquals(3, textZeros.count);
        assertEquals(7, textZeros.line);
    }

    @Test
    void commentsAndBlankLinesAreIgnoredAnywhere() throws Exception {
        ExecutableImage img = ok("comments.hmx");
        assertEquals(Map.of(0x00400024L, 0x3c041001, 0x00400028L, 0x0000000c), img.textWords());
        assertTrue(img.dataWords().isEmpty(), "a program without data has no .data");
        // CRLF와 BOM도 읽는다(Windows에서 저장한 파일)
        String crlf = "\uFEFF" + new String(Files.readAllBytes(HMX.resolve("comments.hmx")), "UTF-8")
                .replace("\n", "\r\n");
        HmxParser.Result r = HmxParser.parse(crlf);
        assertEquals(List.of(), r.errors);
        assertTrue(r.image.sameProgram(img));
    }

    @Test
    void bigEndianPutsTheLowAddressInTheHighByte() throws Exception {
        ExecutableImage img = ok("big-endian.hmx");
        assertEquals(ExecutableImage.Endian.BIG, img.endian());
        assertEquals(Map.of(0x10010000L, 0x12345678, 0x10010004L, 0x9a000000), img.dataWords());
    }

    // ---- 읽지 않는 파일: 줄 번호 → 무엇이 틀렸나 → 무엇을 할까 ----

    @Test
    void truncatedFileNamesTheSegmentLineAndCounts() throws Exception {
        List<String> e = errorsKo(read("truncated.hmx"));
        assertEquals(List.of("8번째 줄: .text 줄에는 워드 14개라고 적혀 있지만 실제로는 13개입니다. 파일이 잘렸을 수 있습니다."
                + " Hallym MIPS에서 다시 내보내세요."), e);
        HmxParser.Result r = read("truncated.hmx");
        assertEquals("Line 8: The .text line declares 14 words, but there are 13. The file may be cut off."
                + " Export it again from Hallym MIPS.", r.errors.get(0).text(false));
    }

    @Test
    void version2IsNotRead() throws Exception {
        HmxParser.Result r = read("version2.hmx");
        assertEquals(List.of("1번째 줄: 이 파일은 실행 이미지 2판입니다. 이 도구는 1판만 읽습니다. Hallym Circuit Studio를 새 판으로"
                + " 바꾸세요."), errorsKo(r));
        assertEquals(1, r.errors.size(), "the rest of a version 2 file is not read");
    }

    /** 명세 "What a reader must do" 4: 모르는 필드는 읽지 않는다(새 필드는 판을 올리지 않는다). */
    @Test
    void unknownFieldsAreIgnored() throws Exception {
        HmxParser.Result r = read("unknown-key.hmx");
        assertEquals(List.of(), r.errors);
        assertEquals(Map.of(5, "checksum"), r.ignoredFields);
        assertEquals(Map.of(0x00400024L, 0x0000000c), r.image.textWords());
        // 여러 모양: 값 없음, 값 여럿, 하이픈, 알려진 필드 사이
        HmxParser.Result many = HmxParser.parse("HALLYM-EXEC 1\nendian little\nline-numbers\nentry 0x00400000\n"
                + "text-panel a b c\nregs2 $sp 0x7fffeffc\n.text 0x00400000 words 1\n0000000c\n");
        assertEquals(List.of(), many.errors);
        assertEquals(Map.of(3, "line-numbers", 5, "text-panel", 6, "regs2"), many.ignoredFields);
        assertEquals(List.of(), new ArrayList<>(many.image.regs().keySet()));
    }

    /** 명세 "What a reader must do" 3: endian·entry·.text가 없거나, 모르는 구간, 같은 종류 두 번째 구간은 오류다. */
    @Test
    void requiredLinesAndSections() {
        List<String> none = errorsKo(HmxParser.parse("HALLYM-EXEC 1\n# 머리 줄만\n"));
        assertEquals(List.of(
                "endian 줄이 없습니다. 실행 이미지에는 바이트 순서 줄이 있어야 합니다: endian little 또는 endian big."
                        + " Hallym MIPS에서 다시 내보내세요.",
                "entry 줄이 없습니다. 실행 이미지에는 시작 주소 줄이 있어야 합니다. Hallym MIPS에서 다시 내보내세요.",
                ".text 구간이 없습니다. 실행 이미지에는 프로그램의 명령어 워드 구간이 있어야 합니다. Hallym MIPS에서 다시"
                        + " 내보내세요."), none);
        HmxParser.Result noText = HmxParser.parse("HALLYM-EXEC 1\nendian little\nentry 0x10010000\n"
                + ".data 0x10010000 bytes 1\n01\n");
        assertEquals("There is no .text section. An executable image holds the program's instruction words."
                + " Export it again from Hallym MIPS.", noText.errors.get(0).text(false));
        String head = "HALLYM-EXEC 1\nendian little\nentry 0x00400000\n";
        List<String> unknown = errorsKo(HmxParser.parse(head + ".text 0x00400000 words 1\n0000000c\n"
                + ".rdata 0x10000000 bytes 1\n01\nzz\n"));
        assertEquals(List.of("6번째 줄: 이 도구가 모르는 구간입니다: .rdata. 실행 이미지 1판의 구간은 .text, .data 둘뿐입니다."
                + " Hallym MIPS에서 다시 내보내세요."), unknown, "the lines of an unknown section are skipped");
        List<String> second = errorsKo(HmxParser.parse(head + ".text 0x00400000 words 1\n0000000c\n"
                + ".text 0x00400100 words 1\n0000000c\n"));
        assertEquals(List.of("6번째 줄: 4번째 줄에 이미 .text 구간이 있습니다. 구간은 종류마다 하나만 있을 수 있습니다."
                + " Hallym MIPS에서 다시 내보내세요."), second);
        List<String> twoData = errorsKo(HmxParser.parse(head + ".text 0x00400000 words 1\n0000000c\n"
                + ".data 0x10010000 bytes 1\n01\n.data 0x10010100 bytes 1\n02\n"));
        assertEquals(1, twoData.size(), twoData.toString());
        assertTrue(twoData.get(0).startsWith("8번째 줄: 6번째 줄에 이미 .data 구간이 있습니다."), twoData.toString());
        // 필드는 첫 구간 앞에만
        List<String> late = errorsKo(HmxParser.parse("HALLYM-EXEC 1\nendian little\n.text 0x00400000 words 1\n"
                + "0000000c\nentry 0x00400000\n"));
        assertEquals(List.of("5번째 줄: entry 줄은 첫 구간(.text, .data)보다 앞에 있어야 합니다. Hallym MIPS에서 다시 내보내세요.",
                "entry 줄이 없습니다. 실행 이미지에는 시작 주소 줄이 있어야 합니다. Hallym MIPS에서 다시 내보내세요."), late);
        // 구간 밖의 워드·바이트 줄
        List<String> outside = errorsKo(HmxParser.parse(head + "8fa40000 27a50004\n.text 0x00400000 words 1\n"
                + "0000000c\n"));
        assertEquals(List.of("4번째 줄: 워드·바이트 줄과 zero 줄은 구간(.text, .data) 안에만 올 수 있습니다: 8fa40000 27a50004."
                + " Hallym MIPS에서 다시 내보내세요."), outside);
    }

    /** 명세 "Lines": 한 줄에 워드·바이트 여럿, 앞뒤 공백·탭, CRLF, 16진수 대소문자, 머리 줄 앞의 주석. */
    @Test
    void linesFollowTheSpec() {
        String text = "# 머리 줄 앞 주석\n\n  HALLYM-EXEC\t1  \n\tendian little\nentry\t0x0040000C\n"
                + "symbol main 0X0040000C\n.text 0x00400000 words 5\n 8FA40000 27a50004\t24A60004 \nzero 1\n0000000C\n"
                + ".data 0x10010000 bytes 5\nAB cd\n  Ef 01 02  \n";
        for (String variant : new String[] {text, text.replace("\n", "\r\n")}) {
            HmxParser.Result r = HmxParser.parse(variant);
            assertEquals(List.of(), r.errors);
            ExecutableImage img = r.image;
            assertEquals(0x0040000cL, (long) img.entry());
            assertEquals(0x0040000cL, (long) img.symbols().get("main"));
            assertEquals(Map.of(0x00400000L, 0x8fa40000, 0x00400004L, 0x27a50004, 0x00400008L, 0x24a60004,
                    0x0040000cL, 0, 0x00400010L, 0x0000000c), img.textWords());
            assertEquals(Map.of(0x10010000L, 0x01efcdab, 0x10010004L, 0x02), img.dataWords());
        }
        // zero 0은 칸이 없는 0 구간이다(명세의 <count>는 10진수 정수)
        HmxParser.Result z = HmxParser.parse("HALLYM-EXEC 1\nendian little\nentry 0x00400000\n"
                + ".text 0x00400000 words 1\nzero 0\n0000000c\n");
        assertEquals(List.of(), z.errors);
        assertTrue(z.image.segments().get(0).zeroRuns().isEmpty());
        // 빈 구간
        HmxParser.Result empty = HmxParser.parse("HALLYM-EXEC 1\nendian little\nentry 0x00400000\n"
                + ".text 0x00400000 words 0\n.data 0x10010000 bytes 0\n");
        assertEquals(List.of(), empty.errors);
        assertEquals(0, empty.image.textWords().size());
        assertEquals(2, empty.image.segments().size());
    }

    @Test
    void duplicateKeysNameTheFirstLine() throws Exception {
        assertEquals("Line 5: The entry key is already on line 3. A key can appear only once. Export it again from"
                + " Hallym MIPS.", read("duplicate-key.hmx").errors.get(0).toString());
        List<String> e = errorsKo(read("duplicate-key.hmx"));
        assertEquals(List.of(
                "5번째 줄: entry 키가 3번째 줄에 이미 있습니다. 키는 한 번만 적을 수 있습니다. Hallym MIPS에서 다시 내보내세요.",
                "6번째 줄: symbol main 키가 4번째 줄에 이미 있습니다. 키는 한 번만 적을 수 있습니다. Hallym MIPS에서 다시 내보내세요."),
                e);
    }

    @Test
    void overlappingSections() throws Exception {
        List<String> e = errorsKo(read("overlap.hmx"));
        assertEquals(List.of("8번째 줄: 이 구간이 5번째 줄의 구간과 겹칩니다: .data 0x00400004\u20130x00400007,"
                + " .text 0x00400000\u20130x00400004. Hallym MIPS에서 다시 내보내세요."), e);
    }

    @Test
    void unalignedText() throws Exception {
        List<String> e = errorsKo(read("unaligned-text.hmx"));
        assertEquals(List.of("5번째 줄: .text 시작 주소가 4바이트 워드 경계에 있지 않습니다: 0x00400002."
                + " Hallym MIPS에서 다시 내보내세요."), e);
    }

    @Test
    void missingEntry() throws Exception {
        List<String> e = errorsKo(read("missing-entry.hmx"));
        assertEquals(List.of("entry 줄이 없습니다. 실행 이미지에는 시작 주소 줄이 있어야 합니다. Hallym MIPS에서 다시 내보내세요."), e);
    }

    @Test
    void otherFormatErrorsHaveLineNumbers() {
        String head = "HALLYM-EXEC 1\nendian little\nentry 0x00400000\n";
        // 개수보다 많음(구간마다 한 번)
        List<String> more = errorsKo(HmxParser.parse(head + ".text 0x00400000 words 1\n0000000c\n0000000c\n0000000c\n"));
        assertEquals(List.of("6번째 줄: 4번째 줄의 .text 구간은 워드 1개라고 적혀 있지만 그보다 많습니다. Hallym MIPS에서 다시 내보내세요."),
                more);
        List<String> moreBytes = errorsKo(HmxParser.parse(head + ".text 0x00400000 words 0\n"
                + ".data 0x10010000 bytes 2\n01 02 03\n"));
        assertEquals(List.of("6번째 줄: 5번째 줄의 .data 구간은 바이트 2개라고 적혀 있지만 그보다 많습니다. Hallym MIPS에서 다시"
                + " 내보내세요."), moreBytes);
        List<String> moreZero = errorsKo(HmxParser.parse(head + ".text 0x00400000 words 2\n0000000c\nzero 5\n"));
        assertTrue(moreZero.get(0).startsWith("6번째 줄: 4번째 줄의 .text 구간은 워드 2개라고"), moreZero.toString());
        // 워드 형식: 16진수 정확히 8자리, 0x 없이
        for (String w : new String[] {"8fa4000", "0x8fa40000", "8fa400000", "8fa4000g"}) {
            List<String> word = errorsKo(HmxParser.parse(head + ".text 0x00400000 words 1\n" + w + "\n"));
            assertEquals("5번째 줄: .text 워드는 16진수 8자리여야 합니다: " + w + ". Hallym MIPS에서 다시 내보내세요.", word.get(0));
        }
        // 바이트 형식: 16진수 정확히 2자리
        for (String b : new String[] {"4g", "1", "123", "0x1"}) {
            List<String> bytes = errorsKo(HmxParser.parse(head + ".text 0x00400000 words 0\n.data 0x10010000 bytes 1\n"
                    + b + "\n"));
            assertEquals("6번째 줄: .data 바이트는 16진수 2자리여야 합니다: " + b + ". Hallym MIPS에서 다시 내보내세요.",
                    bytes.get(0));
        }
        // 주소 형식: 0x 뒤에 16진수 정확히 8자리
        for (String a : new String[] {"400024", "0x400024", "0x004000240", "0x0040002g"}) {
            List<String> addr = errorsKo(HmxParser.parse("HALLYM-EXEC 1\nendian little\nentry " + a + "\n"
                    + ".text 0x00400000 words 0\n"));
            assertEquals("3번째 줄: entry 값을 읽을 수 없습니다: " + a + ". 값은 0x 뒤에 16진수 8자리를 적은 모양입니다."
                    + " Hallym MIPS에서 다시 내보내세요.", addr.get(0));
        }
        String text = ".text 0x00400000 words 0\n";
        // 레지스터 이름, endian 값, zero 위치
        assertTrue(errorsKo(HmxParser.parse(head + "reg $xx 0x00000000\n" + text)).get(0)
                .startsWith("4번째 줄: 레지스터 이름을 읽을 수 없습니다: $xx."));
        assertTrue(errorsKo(HmxParser.parse("HALLYM-EXEC 1\nendian middle\nentry 0x00400000\n" + text)).get(0)
                .startsWith("2번째 줄: endian 값으로 읽을 수 없습니다: middle."));
        assertTrue(errorsKo(HmxParser.parse(head + "zero 4\n" + text)).get(0)
                .startsWith("4번째 줄: 워드·바이트 줄과 zero 줄은 구간(.text, .data) 안에만 올 수 있습니다: zero 4."));
        assertTrue(errorsKo(HmxParser.parse(head + ".text 0x00400000 words 1\nzero x\n")).get(0)
                .startsWith("5번째 줄: zero 줄의 형식이 틀렸습니다. 형식: zero <개수>."));
        assertTrue(errorsKo(HmxParser.parse(head + ".text 0x00400000 words 1\nzero 1 2\n")).get(0)
                .startsWith("5번째 줄: zero 줄의 형식이 틀렸습니다."));
        // 구간 머리 형식: 뒤따르는 워드 줄은 읽지 않는다(오류 하나)
        List<String> bad = errorsKo(HmxParser.parse(head + ".text 0x00400000 bytes 1\n0000000c\n"));
        assertEquals(1, bad.size(), bad.toString());
        assertTrue(bad.get(0).startsWith("4번째 줄: .text 줄의 형식이 틀렸습니다. 형식: .text <주소> words <개수>."));
        assertEquals(1, errorsKo(HmxParser.parse(head + ".text 0x00400000 words\n")).size());
        assertEquals(1, errorsKo(HmxParser.parse(head + ".text 0x00400000 words x\n")).size());
        assertEquals(1, errorsKo(HmxParser.parse(head + ".data 0x10010000 words 1\n.text 0x00400000 words 0\n")).size());
        // 다음 구간이 오기 전에 구간이 덜 참
        List<String> early = errorsKo(HmxParser.parse(head + ".text 0x00400000 words 2\n0000000c\n"
                + ".data 0x10010000 bytes 1\n01\n"));
        assertEquals(List.of("4번째 줄: .text 줄에는 워드 2개라고 적혀 있지만 실제로는 1개입니다. 파일이 잘렸을 수 있습니다."
                + " Hallym MIPS에서 다시 내보내세요."), early);
        assertEquals("Line 4: The .text line declares 2 words, but there is 1. The file may be cut off. Export it"
                + " again from Hallym MIPS.", HmxParser.parse(head + ".text 0x00400000 words 2\n0000000c\n").errors
                .get(0).text(false));
        // 첫 줄이 머리가 아님(주석·빈 줄 다음 첫 줄), 빈 파일
        List<String> notHmx = errorsKo(HmxParser.parse("{\"text\": []}\n"));
        assertEquals(List.of("1번째 줄: 첫 줄이 HALLYM-EXEC 머리 줄이 아니라서 실행 이미지 파일이 아닙니다."
                + " Hallym MIPS에서 내보낸 .hmx 파일을 고르세요."), notHmx);
        assertEquals(1, errorsKo(HmxParser.parse("# c\n\nHALLYM-EXEC 1 x\nendian little\n")).size());
        assertTrue(errorsKo(HmxParser.parse("# c\n\nHALLYM-EXEC 1 x\n")).get(0).startsWith("3번째 줄: 첫 줄이"));
        assertTrue(errorsKo(HmxParser.parse("HALLYM-EXE 1\n")).get(0).startsWith("1번째 줄: 첫 줄이"));
        assertEquals(List.of("첫 줄이 HALLYM-EXEC 머리 줄이 아니라서 실행 이미지 파일이 아닙니다. Hallym MIPS에서 내보낸 .hmx 파일을"
                + " 고르세요."), errorsKo(HmxParser.parse("")));
        assertEquals(1, errorsKo(HmxParser.parse(null)).size());
        assertEquals(List.of("1번째 줄: 판 번호가 숫자가 아닙니다: one. Hallym MIPS에서 다시 내보내세요."),
                errorsKo(HmxParser.parse("HALLYM-EXEC one\n")), "nothing after a bad header is read");
        assertTrue(errorsKo(HmxParser.parse("HALLYM-EXEC 0\n")).get(0).startsWith("1번째 줄: 이 파일은 실행 이미지 0판입니다."));
        // 32비트 너머, 너무 큰 구간
        assertTrue(errorsKo(HmxParser.parse(head + ".text 0x00400000 words 0\n.data 0xfffffffe bytes 3\n01 02 03\n"))
                .get(0).startsWith("5번째 줄: .data 구간이 주소 0xffffffff 너머까지 이어집니다."));
        assertEquals(List.of(), HmxParser.parse(head + ".text 0x00400000 words 0\n.data 0xfffffffe bytes 2\n01 02\n")
                .errors, "up to 0xffffffff is fine");
        assertTrue(errorsKo(HmxParser.parse(head + ".text 0x00400000 words 16777217\n")).get(0)
                .startsWith("4번째 줄: .text 구간이 너무 큽니다: 워드 16777217개(최대 16777216개)."));
        assertTrue(errorsKo(HmxParser.parse(head + ".text 0x00400000 words 16777216\n")).get(0)
                .startsWith("4번째 줄: .text 줄에는 워드 16777216개라고 적혀 있지만 실제로는 0개입니다."), "16M is allowed");
        // 값 모양
        assertTrue(errorsKo(HmxParser.parse(head + "entry\n" + text)).get(0).startsWith("4번째 줄: entry 줄에 값이 없습니다."));
        assertTrue(errorsKo(HmxParser.parse(head + "source\n" + text)).get(0).startsWith("4번째 줄: source 줄에 값이 없습니다."));
        assertTrue(errorsKo(HmxParser.parse(head + "source a.s\nsource b.s\n" + text)).get(0)
                .startsWith("5번째 줄: source 키가 4번째 줄에 이미 있습니다."));
        assertTrue(errorsKo(HmxParser.parse("HALLYM-EXEC 1\nendian little big\nentry 0x00400000\n" + text)).get(0)
                .startsWith("2번째 줄: endian 줄에 값이 둘 이상입니다: endian little big."));
        assertTrue(errorsKo(HmxParser.parse(head + "source-sha256 12\n" + text)).get(0)
                .startsWith("4번째 줄: source-sha256 값이 16진수 64자리가 아닙니다: 12."));
        assertTrue(errorsKo(HmxParser.parse(head + "reg $sp\n" + text)).get(0)
                .startsWith("4번째 줄: reg 줄의 형식이 틀렸습니다."));
        assertTrue(errorsKo(HmxParser.parse(head + "reg $sp 0x7fffeffc\nreg $29 0x7fffeffc\n" + text)).get(0)
                .startsWith("5번째 줄: reg $sp 키가 4번째 줄에 이미 있습니다."));
        assertTrue(errorsKo(HmxParser.parse(head + "reg $sp 12\n" + text)).get(0)
                .startsWith("4번째 줄: reg $sp 값을 읽을 수 없습니다: 12."));
        assertTrue(errorsKo(HmxParser.parse(head + "symbol x\n" + text)).get(0)
                .startsWith("4번째 줄: symbol 줄의 형식이 틀렸습니다."));
        assertTrue(errorsKo(HmxParser.parse(head + "symbol x 12\n" + text)).get(0)
                .startsWith("4번째 줄: symbol x 값을 읽을 수 없습니다: 12."));
        // source 값은 줄의 나머지 전부(공백 포함), source-sha256은 소문자로
        HmxParser.Result named = HmxParser.parse(head + "source  my lab 04.s \nsource-sha256 "
                + "ABCDEF0123456789abcdef0123456789ABCDEF0123456789abcdef0123456789\nproduced-by Hallym MIPS 2.4.0\n"
                + "assembled 2026-09-27T19:05+09:00\n" + text);
        assertEquals(List.of(), named.errors);
        assertEquals("my lab 04.s", named.image.source());
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", named.image.sourceSha256());
        assertEquals("Hallym MIPS 2.4.0", named.image.producedBy());
        assertEquals("2026-09-27T19:05+09:00", named.image.assembled());
        // 오류가 난 줄의 값은 쓰지 않는다: 틀린 값·두 번째 값 뒤에 오류가 더 붙지 않는다
        assertEquals(1, errorsKo(HmxParser.parse(head + "reg $sp 12\nreg $sp 0x7fffeffc\n" + text)).size());
        assertEquals(1, errorsKo(HmxParser.parse(head + "symbol x 12\nsymbol x 0x00000010\n" + text)).size());
        assertEquals(1, errorsKo(HmxParser.parse("HALLYM-EXEC 1\nendian little\nendian middle\nentry 0x00400000\n"
                + text)).size());
        assertEquals(1, errorsKo(HmxParser.parse("HALLYM-EXEC 1\nendian little\nentry 1 2\n"
                + "entry 0x00400000\n" + text)).size());
        assertEquals(1, errorsKo(HmxParser.parse(head + ".text 0x00400000 words 0\n.data 12 bytes 1\n01\n")).size());
        // .text 워드 수로 32비트 끝을 잰다
        assertTrue(errorsKo(HmxParser.parse(head + ".text 0xfffffffc words 2\n00000000 00000000\n")).get(0)
                .startsWith("4번째 줄: .text 구간이 주소 0xffffffff 너머까지 이어집니다."));
        assertEquals(List.of(), HmxParser.parse(head + ".text 0xfffffffc words 1\n0000000c\n").errors);
        // 구간 끝이 다른 구간 시작과 맞닿으면 겹침이 아니다(두 순서), 빈 구간은 어디에 있어도 겹치지 않는다
        for (String adjacent : new String[] {".text 0x00400000 words 2\n0 0\n.data 0x00400008 bytes 1\n01\n",
            ".data 0x003ffffc bytes 4\n01 02 03 04\n.text 0x00400000 words 1\n0000000c\n",
            ".text 0x00400000 words 2\n00000000 0000000c\n.data 0x00400004 bytes 0\n",
            ".data 0x10010000 bytes 8\nzero 8\n.text 0x10010004 words 0\n",
            ".text 0x10010004 words 0\n.data 0x10010000 bytes 8\nzero 8\n",
            ".text 0x00400000 words 1\n0000000c\n.data 0x003ffffc bytes 4\n01 02 03 04\n"}) {
            String body = head + adjacent.replace("\n0 0\n", "\n00000000 0000000c\n");
            assertEquals(List.of(), HmxParser.parse(body).errors, adjacent);
        }
        assertEquals(1, errorsKo(HmxParser.parse(head + ".text 0x00400004 words 1\n0000000c\n"
                + ".data 0x00400000 bytes 5\nzero 5\n")).size(), "one byte of overlap");
        // 구간이 4096칸보다 크면 칸 배열을 늘린다
        HmxParser.Result big = HmxParser.parse(head + ".text 0x00400000 words 5000\nzero 4999\n0000000c\n");
        assertEquals(List.of(), big.errors);
        assertEquals(0x0000000c, (int) big.image.textWords().get(0x00400000L + 4999 * 4));
        assertEquals(5000, big.image.textWords().size());
        // 오류는 줄 번호 순(줄이 없는 오류는 뒤). 모자란 구간의 오류(머리 줄)는 나중에 알게 되지만 앞에 온다
        List<Integer> order = new ArrayList<>();
        for (HmxError e : HmxParser.parse(head + ".text 0x00400000 words 2\n0000000c\nentry 0x00400000\n").errors) {
            order.add(e.line);
        }
        assertEquals(List.of(4, 6), order);
        assertEquals("Line 6: The entry line must come before the first section (.text, .data). Export it again"
                + " from Hallym MIPS.", HmxParser.parse(head + ".text 0x00400000 words 2\n0000000c\nentry 0x00400000\n")
                .errors.get(1).toString());
        // 오류는 줄 번호 순(줄이 없는 오류는 뒤)
        HmxParser.Result sorted = HmxParser.parse("HALLYM-EXEC 1\n.text 0x00400000 words 2\n0000000c\n"
                + ".data 0x10010000 bytes 1\n0g\n");
        List<Integer> lines = new ArrayList<>();
        for (HmxError e : sorted.errors) {
            lines.add(e.line);
        }
        assertEquals(List.of(2, 5, 0, 0), lines);
    }

    /**
     * 문구 규칙(D-126): 한국어 문장은 줄 번호 → 무엇이 틀렸나 → 무엇을 할까 순서이고, "~하면 됩니다"를 쓰지 않으며, 파일
     * 이름·레지스터·키·자리 표시 바로 뒤에 조사를 붙이지 않는다. 조사가 붙는 앞 낱말은 한글이어야 한다.
     */
    @Test
    void koreanWordingFollowsTheParticleRule() throws Exception {
        List<String> all = new ArrayList<>();
        for (File f : HMX.toFile().listFiles((d, n) -> n.endsWith(".hmx"))) {
            HmxParser.Result r = HmxParser.read(f);
            for (HmxError e : r.errors) {
                all.add(e.text(true));
            }
        }
        String head = "HALLYM-EXEC 1\nentry 0x00400000\n";
        for (String bad : Arrays.asList(".text 0x00400000 words 1\n0000000c\n0000000c\n", "reg $xx 0x0\n",
                "endian middle\n", "zero 4\n", ".text 0x00400000 bytes 1\n", "entry\n", "source-sha256 12\n",
                "reg $sp\n", "symbol x\n", "zero\n", ".text 0x00400000 words 2\n0000000c\nzero 5\n", "x y z\n",
                ".rdata 0x0 bytes 1\n", ".text 0x00400000 words 0\n.text 0x00400000 words 0\n",
                ".text 0x00400000 words 0\nentry 0x00400000\n", "ab cd\n", ".text 0x00400000 words 1\nzero x\n",
                ".text 0x00400000 words 1\n0x1\n", ".data 0x10010000 bytes 1\n123\n", "endian a b\n",
                ".text 0x00400002 words 1\n", ".text 0x00400000 words 16777217\n", ".data 0xffffffff bytes 2\n",
                ".text 0x00400000 words 1\n.data 0x00400000 bytes 1\n01\n", "source\n", "symbol x 1\n")) {
            for (HmxError e : HmxParser.parse(head + bad).errors) {
                all.add(e.text(true));
            }
        }
        all.add(HmxParser.parse("HALLYM-EXEC x\n").errors.get(0).text(true));
        for (SourceCheck.Status s : SourceCheck.Status.values()) {
            all.add(message(s));
        }
        assertTrue(all.size() > 20, all.toString());
        // 조사(을/를/이/가/은/는/에/의/와/과/로/으로/에서/도/만) 바로 앞이 영문·숫자·기호면 안 된다(예외: 판·개 같은 한글 단위 뒤는 괜찮다)
        Pattern particle = Pattern.compile("[A-Za-z0-9._$>)\\]](을|를|이|가|은|는|에|의|와|과|로|으로|에서|도|만)(\\s|[.,])");
        for (String s : all) {
            String body = s.replaceAll("^\\d+번째 줄: ", "").replace("Hallym MIPS에서", "").replace("Hallym Circuit Studio를", "");
            assertFalse(particle.matcher(body).find(), "particle after a name: " + s);
            assertFalse(s.contains("하면 됩니다"), s);
        }
    }

    private String message(SourceCheck.Status s) throws Exception {
        switch (s) {
            case SAME:
                return SourceCheck.check(HMX.resolve("example.hmx").toFile(), ok("example.hmx")).message().ko;
            case CHANGED:
                return SourceCheck.check(HMX.resolve("source-changed.hmx").toFile(), ok("source-changed.hmx")).message().ko;
            case NOT_FOUND:
                return SourceCheck.check(HMX.resolve("source-missing.hmx").toFile(), ok("source-missing.hmx")).message().ko;
            default:
                return SourceCheck.check(HMX.resolve("comments.hmx").toFile(), ok("comments.hmx")).message().ko;
        }
    }

    // ---- 원본 .s 대조 ----

    @Test
    void sourceHashMatchMismatchMissing() throws Exception {
        SourceCheck same = SourceCheck.check(HMX.resolve("example.hmx").toFile(), ok("example.hmx"));
        assertEquals(SourceCheck.Status.SAME, same.status);
        assertEquals("example.s", same.file.getName());
        assertFalse(same.warns());

        SourceCheck changed = SourceCheck.check(HMX.resolve("source-changed.hmx").toFile(), ok("source-changed.hmx"));
        assertEquals(SourceCheck.Status.CHANGED, changed.status);
        assertTrue(changed.warns());
        assertEquals("원본 파일 example.s: 내보낸 뒤 바뀜. Hallym MIPS에서 다시 내보내세요.", changed.message().ko);
        assertEquals("Source file example.s: changed after export. Export it again from Hallym MIPS.",
                changed.message().en);

        SourceCheck missing = SourceCheck.check(HMX.resolve("source-missing.hmx").toFile(), ok("source-missing.hmx"));
        assertEquals(SourceCheck.Status.NOT_FOUND, missing.status);
        assertEquals("원본 .s 파일을 찾지 못해 비교하지 않았습니다.", missing.message().ko);

        SourceCheck noHash = SourceCheck.check(HMX.resolve("comments.hmx").toFile(), ok("comments.hmx"));
        assertEquals(SourceCheck.Status.NO_HASH, noHash.status);
    }

    /** source가 없으면 .hmx 옆의 같은 이름 .s, source가 하위 폴더를 가리키면 그 경로, 없으면 .hmx 옆의 그 이름. */
    @Test
    void sourceIsFoundByRelativePathOrBesideTheImage() throws Exception {
        byte[] s = Files.readAllBytes(HMX.resolve("example.s"));
        String sha = SourceCheck.sha256(HMX.resolve("example.s").toFile());
        String body = "HALLYM-EXEC 1\nsource-sha256 " + sha + "\nendian little\nentry 0x00400024\n"
                + ".text 0x00400024 words 1\n0000000c\n";
        Path dir = Files.createDirectories(tmp.resolve("lab"));
        // (1) source 상대 경로
        Files.createDirectories(dir.resolve("src"));
        Files.write(dir.resolve("src/lab04.s"), s);
        Path a = Files.writeString(dir.resolve("a.hmx"), body.replace("HALLYM-EXEC 1\n", "HALLYM-EXEC 1\nsource src/lab04.s\n"));
        assertEquals(SourceCheck.Status.SAME, SourceCheck.check(a.toFile(), HmxParser.read(a.toFile()).image).status);
        // (2) source 이름이 .hmx 옆에
        Path b = Files.writeString(dir.resolve("b.hmx"), body.replace("HALLYM-EXEC 1\n", "HALLYM-EXEC 1\nsource C:/Users/x/lab05.s\n"));
        Files.write(dir.resolve("lab05.s"), s);
        assertEquals(SourceCheck.Status.SAME, SourceCheck.check(b.toFile(), HmxParser.read(b.toFile()).image).status);
        // (3) source가 없으면 .hmx와 같은 이름의 .s
        Path c = Files.writeString(dir.resolve("lab06.hmx"), body);
        assertEquals(SourceCheck.Status.NOT_FOUND, SourceCheck.check(c.toFile(), HmxParser.read(c.toFile()).image).status);
        Files.write(dir.resolve("lab06.s"), s);
        SourceCheck byName = SourceCheck.check(c.toFile(), HmxParser.read(c.toFile()).image);
        assertEquals(SourceCheck.Status.SAME, byName.status);
        assertEquals("lab06.s", byName.name, "without source, the name is the file found");
        assertEquals("원본 파일 lab06.s: 내보낸 때와 같음.", byName.message().ko);
        // 이름이 점으로 시작하는 .hmx: 확장자로 보지 않는다
        Path dot = Files.writeString(dir.resolve(".hmx"), body);
        Files.write(dir.resolve(".hmx.s"), s);
        assertEquals(SourceCheck.Status.SAME, SourceCheck.check(dot.toFile(), HmxParser.read(dot.toFile()).image).status);
        // 읽을 수 없는 원본은 찾지 못한 것과 같다
        Path locked = Files.writeString(dir.resolve("lab07.hmx"), body);
        File lockedSource = Files.write(dir.resolve("lab07.s"), s).toFile();
        if (lockedSource.setReadable(false, false) && !lockedSource.canRead()) {
            assertEquals(SourceCheck.Status.NOT_FOUND, SourceCheck.check(locked.toFile(),
                    HmxParser.read(locked.toFile()).image).status);
            lockedSource.setReadable(true, false);
        }
        Files.write(dir.resolve("lab06.s"), (new String(s, "UTF-8") + "# edited\n").getBytes("UTF-8"));
        assertEquals(SourceCheck.Status.CHANGED, SourceCheck.check(c.toFile(), HmxParser.read(c.toFile()).image).status);
    }
}
