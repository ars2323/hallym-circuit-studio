/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.cburch.logisim.data.Location;

/**
 * D-129: identity hash는 고유하지 않다. RefKey는 해시가 같은 서로 다른 객체도 가른다. 보통 JVM에서는 identity hash가
 * 같은 두 객체를 만들어 찾고, {@code testConstantIdentityHash}(-XX:hashCode=2)에서는 모든 객체가 그렇다.
 */
class RefKeyTest {
    /** identityHashCode가 같은 서로 다른 두 객체(보통 JVM: 생일 문제로 수만 개 안에 나온다). */
    static Object[] collidingPair() {
        Map<Integer, Object> seen = new HashMap<>();
        for (int i = 0; i < 5_000_000; i++) {
            Object o = new Object();
            Object before = seen.putIfAbsent(System.identityHashCode(o), o);
            if (before != null) {
                return new Object[] {before, o};
            }
        }
        throw new AssertionError("no identity hash collision in 5M objects");
    }

    @Test
    void objectsWithTheSameIdentityHashAreDifferentKeys() {
        Object[] p = collidingPair();
        assertEquals(System.identityHashCode(p[0]), System.identityHashCode(p[1]));
        RefKey a = RefKey.builder().ref(p[0]).value(3).build();
        RefKey b = RefKey.builder().ref(p[1]).value(3).build();
        assertEquals(a.hashCode(), b.hashCode(), "same hash: only equals can tell them apart");
        assertNotEquals(a, b);
        assertEquals(a, RefKey.builder().ref(p[0]).value(3).build());
        Set<RefKey> set = new HashSet<>(List.of(a, b));
        assertEquals(2, set.size());
    }

    @Test
    void pathsCompareElementByElementAndByLength() {
        Object[] p = collidingPair();
        Object x = new Object();
        RefKey ab = RefKey.builder().refs(List.of(p[0], x)).ref(x).build();
        assertNotEquals(ab, RefKey.builder().refs(List.of(p[1], x)).ref(x).build(), "same-hash element");
        // [a, x], x 와 [a], x, x 는 다르다(길이가 부분에 들어간다)
        assertNotEquals(ab, RefKey.builder().refs(List.of(p[0])).ref(x).ref(x).build());
        assertEquals(ab, RefKey.builder().refs(new ArrayList<>(List.of(p[0], x))).ref(x).build());
    }

    @Test
    void valuesCompareWithEqualsAndRefsWithIdentity() {
        String s1 = new String("RegWrite");
        String s2 = new String("RegWrite");
        assertEquals(RefKey.builder().value(s1).build(), RefKey.builder().value(s2).build(), "equal strings");
        assertNotEquals(RefKey.builder().ref(s1).build(), RefKey.builder().ref(s2).build(), "different objects");
        assertEquals(RefKey.builder().value(Location.create(10, 20)).build(),
                RefKey.builder().value(Location.create(10, 20)).build());
        // 값과 객체 부분은 서로 같지 않다
        assertNotEquals(RefKey.builder().value(s1).build(), RefKey.builder().ref(s1).build());
    }

    @Test
    void refSetIgnoresOrderButNotIdentity() {
        Object[] p = collidingPair();
        Object x = new Object();
        assertEquals(RefKey.builder().refSet(List.of(p[0], x)).build(), RefKey.builder().refSet(List.of(x, p[0])).build());
        assertNotEquals(RefKey.builder().refSet(List.of(p[0], x)).build(),
                RefKey.builder().refSet(List.of(p[1], x)).build());
        // equals가 같은 서로 다른 객체도 정체로 보는 모음에서는 다르다
        String s1 = new String("pc");
        String s2 = new String("pc");
        assertNotEquals(RefKey.builder().refSet(List.of(s1)).build(), RefKey.builder().refSet(List.of(s2)).build());
        assertEquals(RefKey.builder().refSet(List.of(s1)).build(), RefKey.builder().refSet(List.of(s1)).build());
        // 복사해 두므로 나중에 원래 모음이 바뀌어도 서명은 그대로다
        List<Object> live = new ArrayList<>(List.of(x));
        RefKey before = RefKey.builder().refSet(live).build();
        live.add(p[0]);
        assertNotEquals(before, RefKey.builder().refSet(live).build());
        assertTrue(before.equals(RefKey.builder().refSet(List.of(x)).build()));
    }
}
