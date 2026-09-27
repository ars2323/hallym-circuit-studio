/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 실행 이미지 모델(두 트랙 공용, D-125, D-126)과 문구 두 벌(Msg), 오류 한 줄(HmxError). Z-24의 돌연변이 테스트 대상이다. */
class ExecutableImageTest {
    static ExecutableImage.Builder base() {
        return new ExecutableImage.Builder().header(ExecutableImage.SOURCE, "a.s").endian(ExecutableImage.Endian.LITTLE)
                .entry(0x00400000L).reg("$sp", 0x7fffeffcL).symbol("main", 0x00400000L)
                .text(0x00400000L, 0x20080001, 0x0000000c).data(0x10010000L, 1, 2, 3, 4, 5);
    }

    @Test
    void builderChainsAndKeepsEverything() {
        ExecutableImage img = base().header(ExecutableImage.PRODUCED_BY, "x").build();
        assertEquals("a.s", img.header(ExecutableImage.SOURCE));
        assertEquals("a.s", img.source());
        assertNull(img.header("nothing"));
        assertEquals(Map.of(ExecutableImage.SOURCE, "a.s", ExecutableImage.PRODUCED_BY, "x"), img.header());
        assertEquals("x", img.producedBy());
        assertNull(img.assembled());
        assertNull(img.sourceSha256());
        assertEquals(0x00400000L, (long) img.entry());
        assertEquals(Map.of("$sp", 0x7fffeffcL), img.regs());
        assertEquals(0x7fffeffcL, (long) img.reg("$29"));
        assertNull(img.reg("$gp"));
        assertNull(img.reg("sp"));
        assertEquals(Map.of(0x10010000L, 0x04030201, 0x10010004L, 0x05), img.dataWords());
        assertThrows(IllegalArgumentException.class, () -> new ExecutableImage.Builder().reg("$xx", 0));
        assertNull(new ExecutableImage.Builder().entry(null).build().entry());
        assertEquals(0xfffffffcL, (long) new ExecutableImage.Builder().entry(-4L).build().entry(), "32 bits");
    }

    @Test
    void canonicalRegisterNames() {
        assertNull(ExecutableImage.canonicalRegister(null));
        assertNull(ExecutableImage.canonicalRegister("sp"));
        assertNull(ExecutableImage.canonicalRegister("$32"));
        assertNull(ExecutableImage.canonicalRegister("$xyz"));
        assertNull(ExecutableImage.canonicalRegister("$123"));
        assertEquals("$zero", ExecutableImage.canonicalRegister("$0"));
        assertEquals("$ra", ExecutableImage.canonicalRegister("$31"));
        assertEquals("$ra", ExecutableImage.canonicalRegister("$ra"));
        assertEquals("$fp", ExecutableImage.canonicalRegister("$s8"));
        assertEquals("$fp", ExecutableImage.canonicalRegister("$30"));
        assertEquals("$t0", ExecutableImage.canonicalRegister("$08"));
    }

    @Test
    void describeAndHex() {
        assertEquals("0 words", ExecutableImage.describe(new ArrayList<ExecutableImage.Segment>()));
        ExecutableImage img = base().build();
        assertEquals("2 words (0x00400000–0x00400004)", ExecutableImage.describe(img.segments(ExecutableImage.Kind.TEXT)));
        assertEquals("5 bytes = 2 words (0x10010000–0x10010004)",
                ExecutableImage.describe(img.segments(ExecutableImage.Kind.DATA)));
        assertEquals("0x00000000", ExecutableImage.hex(0));
        assertEquals("0xffffffff", ExecutableImage.hex(-1));
        assertEquals("1 byte", ExecutableImage.count(1, "byte"));
        ExecutableImage.Segment empty = new ExecutableImage.Builder().text(0x00400010L).build().segments().get(0);
        assertEquals(0x00400010L, empty.last(), "an empty segment's last is its start");
        assertEquals(".text 0x00400010–0x00400010", empty.toString());
    }

    @Test
    void wordsOfSegments() {
        ExecutableImage img = base().text(0x00400100L).build(); // 빈 .text 구간 하나 더
        assertEquals(Map.of(0x00400000L, 0x20080001, 0x00400004L, 0x0000000c),
                img.words(img.segments(ExecutableImage.Kind.TEXT)));
        assertEquals(Map.of(0x10010000L, 0x04030201, 0x10010004L, 0x05), img.words(img.segments(ExecutableImage.Kind.DATA)));
        assertEquals(4, img.words(img.segments()).size());
        assertTrue(img.words(new ArrayList<ExecutableImage.Segment>()).isEmpty());
        // 같은 종류의 구간이 여럿이면 준 구간의 워드만
        ExecutableImage two = new ExecutableImage.Builder().data(0x10010000L, 1, 2, 3, 4).data(0x10010011L, 9).build();
        assertEquals(Map.of(0x10010010L, 0x0900), two.words(two.segments().subList(1, 2)));
        ExecutableImage twoText = new ExecutableImage.Builder().text(0x00400000L, 7).text(0x00400010L, 8).build();
        assertEquals(Map.of(0x00400010L, 8), twoText.words(twoText.segments().subList(1, 2)));
        // 워드 경계가 아닌 .data: 그 바이트를 담은 워드
        ExecutableImage odd = new ExecutableImage.Builder().data(0x10010003L, 0xaa, 0xbb).build();
        assertEquals(Map.of(0x10010000L, 0xaa000000, 0x10010004L, 0xbb), odd.words(odd.segments()));
    }

    @Test
    void sameProgram() {
        ExecutableImage a = base().build();
        assertTrue(a.sameProgram(base().header(ExecutableImage.ASSEMBLED, "later").build()), "headers do not count");
        List<ExecutableImage> others = List.of(
                base().text(0x00400100L, 1).build(),
                base().data(0x10020000L, 1).build(),
                base().entry(0x00400004L).build(),
                base().entry(null).build(),
                base().reg("$gp", 0x10008000L).build(),
                base().symbol("loop", 0x00400004L).build());
        for (ExecutableImage o : others) {
            assertFalse(a.sameProgram(o));
            assertFalse(o.sameProgram(a));
        }
        ExecutableImage noEntry = base().entry(null).build();
        assertTrue(noEntry.sameProgram(base().entry(null).build()));
    }

    @Test
    void msgAndError() {
        Msg m = Msg.of("english", "한국어");
        assertEquals("english", m.get(false));
        assertEquals("한국어", m.get(true));
        assertEquals("english", m.toString());
        HmxError line = new HmxError(3, m);
        assertEquals("Line 3: english", line.toString());
        assertEquals("3번째 줄: 한국어", line.text(true));
        HmxError none = new HmxError(0, m);
        assertEquals("english", none.toString());
        assertEquals("한국어", none.text(true));
        assertEquals("HALLYM-EXEC 1", HmxFormat.header());
    }
}
