/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    @SuppressWarnings("unchecked")
    void parsesValues() {
        Map<String, Object> m = (Map<String, Object>) Json.parse(
                "{\"a\": [1, -2, 3.5, true, false, null], \"s\": \"x\\ny\\t\\\"\\u00e9\\\\\", \"e\": {}, \"l\": []}");
        assertEquals(List.of(1L, -2L, 3.5, true, false), ((List<Object>) m.get("a")).subList(0, 5));
        assertNull(((List<Object>) m.get("a")).get(5));
        assertEquals("x\ny\t\"é\\", m.get("s"));
        assertEquals(Map.of(), m.get("e"));
        assertEquals(List.of(), m.get("l"));
        assertEquals(List.of("s", "e", "l"), List.copyOf(m.keySet()).subList(1, 4)); // 순서 유지
    }

    @Test
    void passesUtf8TextThrough() {
        assertEquals("한글 # 주석", Json.parse("\"한글 # 주석\""));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": 1"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1, 2] x"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"open"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{a: 1}"));
    }
}
