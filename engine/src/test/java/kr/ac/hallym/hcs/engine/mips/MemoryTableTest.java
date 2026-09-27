/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** v2 Memory 패널의 표(D-140): 한 표에 데이터 구간과 스택 구간, 0 구간은 한 줄, 라벨과 $sp 표시. */
class MemoryTableTest {
    /** 맵으로 된 부품(희소 저장처럼 값을 가진 페이지만 알린다). */
    static final class Fake implements MemoryTable.Part {
        final String name;
        final long[] data;
        final long[] stack;
        final TreeMap<Long, Integer> words = new TreeMap<>();
        final Set<Long> undefined = new HashSet<>();
        long lowest = -1;
        long base;
        int reads;

        Fake(String name, long[] data, long[] stack, long base) {
            this.name = name;
            this.data = data;
            this.stack = stack;
            this.base = base;
        }

        Fake put(long addr, int... ws) {
            for (int w : ws) {
                words.put(addr, w);
                addr += 4;
            }
            return this;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public long[] dataRegion() {
            return data;
        }

        @Override
        public long[] stackRegion() {
            return stack;
        }

        @Override
        public int word(long addr) {
            reads++;
            return words.getOrDefault(addr & ~3L, 0);
        }

        @Override
        public boolean defined(long addr) {
            return !undefined.contains(addr & ~3L);
        }

        @Override
        public long[] pages() {
            TreeSet<Long> p = new TreeSet<>();
            for (long a : words.keySet()) {
                p.add(a & ~4095L);
            }
            for (long a : undefined) {
                p.add(a & ~4095L);
            }
            return p.stream().mapToLong(Long::longValue).toArray();
        }

        @Override
        public long lowestAccess() {
            return lowest;
        }

        @Override
        public long depthBase() {
            return base;
        }
    }

    static final long[] DATA = {0x10000000L, 0x10100000L};
    static final long[] STACK = {0x7FFC0000L, 0x80000000L};

    static List<String> text(List<MemoryTable.Row> rows) {
        List<String> out = new ArrayList<>();
        for (MemoryTable.Row r : rows) {
            out.add(r.toString());
        }
        return out;
    }

    static Map<Long, List<String>> labels(Object... kv) {
        Map<Long, List<String>> out = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            out.put((Long) kv[i], List.of((String) kv[i + 1]));
        }
        return out;
    }

    static Map<String, Long> sp(long v) {
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("$sp", v);
        return out;
    }

    /** 합친 Data Memory 하나: 데이터 구간(0x10010000부터, 라벨)과 스택 구간(위에서 아래로, $sp 표시)이 한 표에 온다. */
    @Test
    void oneMergedPartGivesTheDataAndTheStackSection() {
        Fake dm = new Fake("Data Memory", DATA, STACK, 0x7FFFEFFCL);
        dm.put(0x10010000L, 0x3d202136, 0x20); // "6! = "
        dm.put(0x7FFFEFF4L, 6, 0x0040002c); // push: $a0, $ra
        dm.lowest = 0x7FFFEFF4L;
        List<MemoryTable.Row> rows = MemoryTable.build(List.of(dm), labels(0x10010000L, "msg"), sp(0x7FFFEFF4L));
        assertEquals(List.of(
                "[DATA Data Memory 10010000-100fffff]",
                "10010000 3d202136 00000020 00000000 00000000 msg@10010000",
                "10010010..100fffff 0 x245756",
                "[STACK Data Memory 7fffeff0-7fffffff]",
                "7ffff000..7fffffff 0 x1024",
                "7fffeff0 00000000 00000006 0040002c 00000000 $sp->7fffeff4"), text(rows));
        MemoryTable.Row stack = rows.get(3);
        assertEquals(0x7FFFEFFCL, stack.base);
        assertEquals(8, stack.depth, "depth = base − $sp");
        assertEquals(8, stack.peak);
        // 1MB 데이터 영역과 스택을 워드마다 읽지 않는다: 값이 있는 페이지 둘만 읽는다
        assertTrue(dm.reads < 2100, "untouched pages are skipped, not read word by word: " + dm.reads);
    }

    /** 스택은 높은 주소가 위다. $sp 아래(팝한 자리)도 가장 낮게 접근한 곳까지 보인다. $sp 줄은 0이어도 줄이지 않는다. */
    @Test
    void stackRowsGoFromTheTopDownToThePeakAndKeepTheSpLine() {
        Fake dm = new Fake("DMem", DATA, STACK, 0x7FFFEFFCL);
        dm.put(0x7FFFEFE0L, 1, 2, 3, 4); // 가장 깊었던 프레임(팝한 뒤)
        dm.lowest = 0x7FFFEFE0L;
        List<MemoryTable.Row> rows = MemoryTable.build(List.of(dm), Map.of(), sp(0x7FFFEFF0L));
        List<String> t = text(rows);
        int s = t.indexOf("[STACK DMem 7fffefe0-7fffffff]");
        assertEquals(List.of("[STACK DMem 7fffefe0-7fffffff]",
                "7ffff000..7fffffff 0 x1024",
                "7fffeff0 00000000 00000000 00000000 00000000 $sp->7fffeff0",
                "7fffefe0 00000001 00000002 00000003 00000004"), t.subList(s, t.size()));
        assertEquals(12, rows.get(s).depth);
        assertEquals(28, rows.get(s).peak);
        for (int i = s + 2; i < rows.size(); i++) {
            assertTrue(rows.get(i).addr < rows.get(i - 1).addr, "high addresses on top");
        }
    }

    /** 0 구간은 한 줄이고, 그 안의 라벨은 그 줄에 붙는다. 값이 있는 줄 사이의 0 구간도 한 줄이다. */
    @Test
    void zeroRunsAreOneRowAndCarryTheirLabels() {
        Fake dm = new Fake("Data Memory", DATA, null, 0);
        dm.put(0x10010000L, 7);
        dm.put(0x10010100L, 9);
        List<MemoryTable.Row> rows = MemoryTable.build(List.of(dm),
                labels(0x10010000L, "a", 0x10010010L, "arr", 0x10010100L, "b"), Map.of());
        assertEquals(List.of(
                "[DATA Data Memory 10010000-100fffff]",
                "10010000 00000007 00000000 00000000 00000000 a@10010000",
                "10010010..100100ff 0 x60 arr@10010010",
                "10010100 00000009 00000000 00000000 00000000 b@10010100",
                "10010110..100fffff 0 x245692"), text(rows));
        assertEquals(60, rows.get(2).count());
    }

    /** 옛 구조(스택 영역이 없는 Data Memory + 옛 Stack): 두 부품이 한 구간씩 한 표에 낸다. */
    @Test
    void oldStructureGivesOneSectionPerPart() {
        Fake dm = new Fake("Data Memory", new long[] {0x10010000L, 0x10110000L}, null, 0);
        dm.put(0x10010000L, 1);
        Fake st = new Fake("Stack", null, new long[] {0x7FF00000L, 0x80000000L}, 0x7FFFEFFCL);
        st.put(0x7FFFEFF8L, 5);
        st.lowest = 0x7FFFEFF8L;
        List<String> t = text(MemoryTable.build(List.of(dm, st), Map.of(), sp(0x7FFFEFF8L)));
        assertEquals(List.of(
                "[DATA Data Memory 10010000-1010ffff]",
                "10010000 00000001 00000000 00000000 00000000",
                "10010010..1010ffff 0 x262140",
                "[STACK Stack 7fffeff0-7fffffff]",
                "7ffff000..7fffffff 0 x1024",
                "7fffeff0 00000000 00000000 00000005 00000000 $sp->7fffeff8"), t);
    }

    /** 0x10010000 아래($gp 구역)에 값이 있으면 그 줄부터, 0x10010000이 영역 밖이면 영역 처음부터. 정해지지 않은 칸은 x. */
    @Test
    void dataSectionStartsAtUserDataUnlessSomethingLivesBelow() {
        Fake gp = new Fake("Data Memory", DATA, null, 0);
        gp.put(0x10008004L, 42);
        gp.put(0x10010000L, 1);
        List<String> t = text(MemoryTable.build(List.of(gp), Map.of(), Map.of("$gp", 0x10008000L)));
        assertEquals("[DATA Data Memory 10008000-100fffff]", t.get(0));
        assertEquals("10008000 00000000 0000002a 00000000 00000000 $gp->10008000", t.get(1));
        assertEquals("10008010..1000ffff 0 x8188", t.get(2));

        Fake zero = new Fake("DMem", new long[] {0, 0x100000L}, null, 0);
        zero.put(0, 0x8c080000);
        zero.undefined.add(4L);
        List<String> z = text(MemoryTable.build(List.of(zero), Map.of(), Map.of()));
        assertEquals("[DATA DMem 00000000-000fffff]", z.get(0));
        assertEquals("00000000 8c080000 xxxxxxxx 00000000 00000000", z.get(1));
    }

    /** $sp가 스택 영역 밖(아직 두지 않은 0 등)이면 표시도 깊이도 없고, 깊이 기준까지만 보인다. */
    @Test
    void anSpOutsideTheStackRegionIsNotMarked() {
        Fake dm = new Fake("Data Memory", DATA, STACK, 0x7FFFEFFCL);
        List<MemoryTable.Row> rows = MemoryTable.build(List.of(dm), Map.of(), sp(0));
        List<String> t = text(rows);
        assertEquals(List.of("[DATA Data Memory 10010000-100fffff]", "10010000..100fffff 0 x245760",
                "[STACK Data Memory 7fffeff0-7fffffff]", "7fffeff0..7fffffff 0 x1028"), t);
        assertEquals(-1, rows.get(2).depth);
        assertEquals(0, rows.get(2).peak);
    }

    /** 화면에 보낼 모양: 주소는 글자, 구간 밖 칸은 null, 0 구간은 count, 스택 머리는 base·depth·peak. */
    @Test
    void jsonShape() {
        Fake dm = new Fake("Data Memory", DATA, STACK, 0x7FFFEFFCL);
        dm.put(0x10010000L, 0x3d202136);
        dm.put(0x7FFFEFF8L, 3);
        dm.lowest = 0x7FFFEFF8L;
        JsonArray j = MemoryTable.json(MemoryTable.build(List.of(dm), labels(0x10010000L, "msg"),
                sp(0x7FFFEFF8L)));
        JsonObject head = j.get(0).getAsJsonObject();
        assertEquals("section", head.get("kind").getAsString());
        assertEquals("data", head.get("section").getAsString());
        assertEquals("0x10010000", head.get("addr").getAsString());
        assertEquals("0x100fffff", head.get("end").getAsString());
        JsonObject first = j.get(1).getAsJsonObject();
        assertEquals("words", first.get("kind").getAsString());
        assertEquals("3d202136", first.getAsJsonArray("words").get(0).getAsString());
        assertEquals("msg", first.getAsJsonArray("labels").get(0).getAsJsonObject().getAsJsonArray("names").get(0)
                .getAsString());
        assertEquals("zeros", j.get(2).getAsJsonObject().get("kind").getAsString());
        assertEquals(245756, j.get(2).getAsJsonObject().get("count").getAsLong());
        JsonObject stackHead = j.get(3).getAsJsonObject();
        assertEquals("stack", stackHead.get("section").getAsString());
        assertEquals("0x7fffeffc", stackHead.get("base").getAsString());
        assertEquals(4, stackHead.get("depth").getAsLong());
        assertEquals(4, stackHead.get("peak").getAsLong());
        JsonObject spRow = j.get(j.size() - 1).getAsJsonObject();
        assertEquals("0x7fffeff8", spRow.getAsJsonObject("pointers").get("$sp").getAsString());
    }
}
