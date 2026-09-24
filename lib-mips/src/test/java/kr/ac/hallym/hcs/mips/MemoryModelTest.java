/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class MemoryModelTest {
    @Test
    void sparseMemoryUsesPagesOnlyWhereWritten() {
        SparseMemory m = new SparseMemory();
        assertEquals(0, m.read(0x7FFFEFFC));
        m.write(0x00400000, 1);
        m.write(0x7FFFFFFC, 2);
        m.write(0x10010004, 3);
        assertEquals(3, m.pageCount());
        assertEquals(1, m.read(0x00400000));
        assertEquals(1, m.read(0x00400003)); // 하위 2비트는 무시
        assertEquals(2, m.read(0x7FFFFFFC));
        assertEquals(0, m.read(0x10010000));
    }

    @Test
    void undefinedWordsStayUndefinedUntilOverwritten() {
        SparseMemory m = new SparseMemory();
        m.writeUndefined(0x10010000);
        assertFalse(m.isDefined(0x10010000));
        assertTrue(m.isDefined(0x10010004));
        SparseMemory copy = m.copy();
        m.write(0x10010000, 5);
        assertTrue(m.isDefined(0x10010000));
        assertFalse(copy.isDefined(0x10010000)); // 복사본은 따로
    }

    @Test
    void bytesAreLittleEndianLikeSpim() {
        SparseMemory m = new SparseMemory();
        m.write(0x10010000, 0x000a6968); // "hi\n"
        assertEquals('h', m.readByte(0x10010000));
        assertEquals('i', m.readByte(0x10010001));
        assertEquals('\n', m.readByte(0x10010002));
        assertEquals(0, m.readByte(0x10010003));
    }

    @Test
    void wordImageRoundTripsThroughItsCircText() {
        Map<Long, Integer> words = new TreeMap<>();
        for (int i = 0; i < 10; i += 1) {
            words.put(0x00400000L + 4 * i, i * 0x11111111);
        }
        words.put(0x7FFFFFFCL, 0xDEADBEEF);
        WordImage image = WordImage.of(words);
        String text = image.format();
        assertEquals("hcs-words 1\n"
                + "00400000 00000000 11111111 22222222 33333333 44444444 55555555 66666666 77777777\n"
                + "00400020 88888888 99999999\n"
                + "7ffffffc deadbeef\n", text);
        assertEquals(image, WordImage.parse(text));
        assertEquals(0xDEADBEEF, WordImage.parse(text).read(0x7FFFFFFC));
    }

    @Test
    void emptyAndMalformedImages() {
        assertSame(WordImage.EMPTY, WordImage.parse(""));
        assertEquals("hcs-words 1\n", WordImage.EMPTY.format());
        assertEquals(WordImage.EMPTY, WordImage.parse("hcs-words 1\n"));
        assertThrows(IllegalArgumentException.class, () -> WordImage.parse("addr/data: 8 8\n00"));
    }

    @Test
    void imageMemoryIsACopy() {
        Map<Long, Integer> words = new TreeMap<>();
        words.put(0x10010000L, 7);
        WordImage image = WordImage.of(words);
        SparseMemory m = image.newMemory();
        m.write(0x10010000, 9);
        assertEquals(7, image.read(0x10010000));
    }
}
