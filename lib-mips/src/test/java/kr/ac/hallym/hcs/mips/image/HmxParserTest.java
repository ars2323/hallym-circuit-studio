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

/** 실행 이미지(.hmx 1판) 파서와 원본 대조(Z-01, Z-06). 시험 파일은 tests/hmx(docs/hmx.md의 해석). */
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
        // 0x10010000: 41 42 43 44 | 45 46 00 00 ; 0x10010008: 00 61 62 63
        assertEquals(Map.of(0x10010000L, 0x44434241, 0x10010004L, 0x00004645, 0x10010008L, 0x63626100),
                img.dataWords());
        assertEquals(9, img.dataBytes().size(), "byte addresses stay as they are");
        assertFalse(img.dataBytes().containsKey(0x10010006L));
        assertEquals(0x61, (int) img.dataBytes().get(0x10010009L));
        assertEquals("6 bytes = 2 words (0x10010000\u20130x10010005)",
                ExecutableImage.describe(img.segments(ExecutableImage.Kind.DATA).subList(0, 1)));
        assertEquals("9 bytes = 3 words (0x10010000\u20130x10010005, 0x10010009\u20130x1001000b)",
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

    @Test
    void unknownKeyMeansANewVersion() throws Exception {
        List<String> e = errorsKo(read("unknown-key.hmx"));
        assertEquals(1, e.size(), e.toString());
        assertTrue(e.get(0).startsWith("4번째 줄: 이 도구가 모르는 키입니다: checksum."), e.get(0));
    }

    @Test
    void duplicateKeysNameTheFirstLine() throws Exception {
        List<String> e = errorsKo(read("duplicate-key.hmx"));
        assertEquals(List.of(
                "5번째 줄: entry 키가 3번째 줄에 이미 있습니다. 키는 한 번만 적을 수 있습니다. Hallym MIPS에서 다시 내보내세요.",
                "6번째 줄: symbol main 키가 4번째 줄에 이미 있습니다. 키는 한 번만 적을 수 있습니다. Hallym MIPS에서 다시 내보내세요."),
                e);
    }

    @Test
    void overlappingSegments() throws Exception {
        List<String> e = errorsKo(read("overlap.hmx"));
        assertEquals(List.of("7번째 줄: 이 구간이 4번째 줄의 구간과 겹칩니다: .text 0x00400004\u20130x00400004,"
                + " .text 0x00400000\u20130x00400004. Hallym MIPS에서 다시 내보내세요."), e);
    }

    @Test
    void unalignedText() throws Exception {
        List<String> e = errorsKo(read("unaligned-text.hmx"));
        assertEquals(List.of("4번째 줄: .text 시작 주소가 4바이트 워드 경계에 있지 않습니다: 0x00400002."
                + " Hallym MIPS에서 다시 내보내세요."), e);
    }

    @Test
    void missingEntry() throws Exception {
        List<String> e = errorsKo(read("missing-entry.hmx"));
        assertEquals(List.of("entry 줄이 없습니다. 실행 이미지에는 시작 주소 줄이 있어야 합니다. Hallym MIPS에서 다시 내보내세요."), e);
    }

    @Test
    void otherFormatErrorsHaveLineNumbers() {
        String head = "HALLYM-EXEC 1\nentry 0x00400000\n";
        // 개수보다 많음
        List<String> more = errorsKo(HmxParser.parse(head + ".text 0x00400000 words 1\n0000000c\n0000000c\n"));
        assertEquals(List.of("5번째 줄: 3번째 줄의 .text 구간은 워드 1개라고 적혀 있지만 그보다 많습니다. Hallym MIPS에서 다시 내보내세요."),
                more);
        List<String> moreBytes = errorsKo(HmxParser.parse(head + ".data 0x10010000 bytes 2\n01 02 03\n"));
        assertTrue(moreBytes.get(0).startsWith("4번째 줄: 3번째 줄의 .data 구간은 바이트 2개라고"), moreBytes.toString());
        // 워드 형식
        List<String> word = errorsKo(HmxParser.parse(head + ".text 0x00400000 words 1\n8fa4000\n"));
        assertTrue(word.get(0).startsWith("4번째 줄: .text 줄에는 16진수 8자리 워드가 하나씩 있어야 합니다: 8fa4000."), word.toString());
        // 바이트 형식
        List<String> bytes = errorsKo(HmxParser.parse(head + ".data 0x10010000 bytes 1\n4g\n"));
        assertTrue(bytes.get(0).startsWith("4번째 줄: .data 바이트는 16진수 2자리여야 합니다: 4g."), bytes.toString());
        // 주소 형식
        List<String> addr = errorsKo(HmxParser.parse("HALLYM-EXEC 1\nentry 400024\n"));
        assertTrue(addr.get(0).startsWith("2번째 줄: entry 값을 읽을 수 없습니다: 400024. 값은 0x 뒤에 16진수"), addr.toString());
        // 레지스터 이름, endian 값, zero 위치
        assertTrue(errorsKo(HmxParser.parse(head + "reg $xx 0x0\n")).get(0)
                .startsWith("3번째 줄: 레지스터 이름을 읽을 수 없습니다: $xx."));
        assertTrue(errorsKo(HmxParser.parse(head + "endian middle\n")).get(0)
                .startsWith("3번째 줄: endian 값으로 읽을 수 없습니다: middle."));
        assertTrue(errorsKo(HmxParser.parse(head + "zero 4\n")).get(0)
                .startsWith("3번째 줄: zero 줄은 구간(.text, .data) 안에만 올 수 있습니다."));
        // 구간 머리 형식: 뒤따르는 워드 줄은 키로 읽지 않는다(오류 하나)
        List<String> bad = errorsKo(HmxParser.parse(head + ".text 0x00400000 bytes 1\n0000000c\n"));
        assertEquals(1, bad.size(), bad.toString());
        assertTrue(bad.get(0).startsWith("3번째 줄: .text 줄의 형식이 틀렸습니다. 형식: .text <주소> words <개수>."));
        // 다음 키가 오기 전에 구간이 끝나지 않음
        List<String> early = errorsKo(HmxParser.parse(head + ".text 0x00400000 words 2\n0000000c\nsymbol main 0x00400000\n"));
        assertTrue(early.get(0).startsWith("3번째 줄: .text 줄에는 워드 2개라고 적혀 있지만 실제로는 1개입니다."), early.toString());
        // 첫 줄이 머리가 아님
        List<String> notHmx = errorsKo(HmxParser.parse("{\"text\": []}\n"));
        assertEquals(List.of("1번째 줄: 첫 줄이 HALLYM-EXEC 머리 줄이 아니라서 실행 이미지 파일이 아닙니다."
                + " Hallym MIPS에서 내보낸 .hmx 파일을 고르세요."), notHmx);
        // 32비트 너머
        assertTrue(errorsKo(HmxParser.parse(head + ".data 0xfffffffe bytes 3\n01 02 03\n")).get(0)
                .startsWith("3번째 줄: .data 구간이 주소 0xffffffff 너머까지 이어집니다."));
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
                "reg $sp\n", "symbol x\n", "zero\n", ".text 0x00400000 words 2\n0000000c\nzero 5\n", "x y z\n")) {
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
        String body = "HALLYM-EXEC 1\nsource-sha256 " + sha + "\nentry 0x00400024\n";
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
        assertEquals(SourceCheck.Status.SAME, SourceCheck.check(c.toFile(), HmxParser.read(c.toFile()).image).status);
        Files.write(dir.resolve("lab06.s"), (new String(s, "UTF-8") + "# edited\n").getBytes("UTF-8"));
        assertEquals(SourceCheck.Status.CHANGED, SourceCheck.check(c.toFile(), HmxParser.read(c.toFile()).image).status);
    }
}
