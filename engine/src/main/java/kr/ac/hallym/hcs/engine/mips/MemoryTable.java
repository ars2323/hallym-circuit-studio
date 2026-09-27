/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.mips;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * v2 Memory 패널의 표(D-140, 화면은 N-14). Hallym MIPS Data 탭처럼 한 표에 데이터 구간과 스택 구간을 함께 둔다. 회로
 * 상태를 읽기만 하고 판단하지 않는다(값을 보일 뿐이다).
 *
 * <ul>
 *   <li><b>줄:</b> 16바이트 정렬 주소에 워드 네 칸(+0, +4, +8, +C). 네 워드 이상 0이 이어지면(줄 처음부터) 몇 줄이든
 *       한 줄로 줄인다(Hallym MIPS memory-rows.ts와 같은 규칙). 포인터($sp 등)가 가리키는 줄은 줄이지 않는다.</li>
 *   <li><b>데이터 구간:</b> 데이터 영역에서 사용자 .data 시작 {@link #USER_DATA}(0x10010000)부터 영역 끝까지, 낮은
 *       주소부터. 그 아래(예: $gp 구역)에 값이 있으면 그 줄부터. 0x10010000이 영역 밖이면 영역 처음부터. 줄마다 그 안의
 *       라벨(실행 이미지의 기호)을 단다.</li>
 *   <li><b>스택 구간:</b> 스택 영역 맨 위에서 아래로(높은 주소가 위, PLAN.md 6.2). 가장 낮은 줄은 지금 $sp, 가장 낮게
 *       접근한 곳(최고 수위), 깊이 기준 가운데 가장 낮은 곳이다. $sp가 가리키는 줄에 표시를 단다. 스택 내용은 실행
 *       이미지에 없으므로(Hallym MIPS가 인자를 둔 자리도) 쓰지 않은 칸은 0이다.</li>
 *   <li>합친 Data Memory(데이터+스택)는 두 구간을 모두 내고, 옛 구조(스택 영역이 없는 Data Memory + 옛 Stack)는 두
 *       부품이 한 구간씩 낸다.</li>
 * </ul>
 */
public final class MemoryTable {
    /** 사용자 .data가 시작하는 곳: SPIM data.cpp data_begins_at_point의 DATA_BOT + 64K. */
    public static final long USER_DATA = 0x10010000L;
    static final long LINE = 16;
    static final long PAGE = 4096;
    /** 한 구간에서 만들 줄 수의 한계(잘못 둔 $sp 등으로 표가 끝없이 길어지지 않게). */
    public static final int MAX_ROWS = 4096;

    private MemoryTable() {
    }

    /** 부품 하나의 메모리(읽기만). 주소는 부호 없는 32비트. */
    public interface Part {
        /** 표에 보일 부품 이름(라벨 또는 부품 이름, 서브회로 안이면 경로). */
        String name();

        /** 데이터 영역 {낮은 주소, 높은 주소(제외)}. 없으면(옛 Stack) null. */
        long[] dataRegion();

        /** 스택 영역. 없으면(스택 영역이 없는 옛 Data Memory) null. */
        long[] stackRegion();

        int word(long addr);

        boolean defined(long addr);

        /** 값을 가진 4KB 페이지 시작 주소들(오름차순). 그 밖은 모두 0이다(희소 저장). */
        long[] pages();

        /** 클럭 에지에 접근한 스택 영역의 가장 낮은 워드 주소. 없으면 -1. */
        long lowestAccess();

        /** 스택 깊이를 재는 기준 주소(실행 이미지의 $sp, SPIM 시작 $sp 또는 영역 맨 위). */
        long depthBase();
    }

    public enum Section { DATA, STACK }

    public enum Kind { SECTION, WORDS, ZERO_RUN }

    /** 표의 한 줄. */
    public static final class Row {
        public final Kind kind;
        public final Section section;
        public final String part;
        /** SECTION: 구간 시작. WORDS: 16바이트 줄 주소. ZERO_RUN: 첫 워드 주소. */
        public final long addr;
        /** 끝(제외). SECTION: 구간 끝. WORDS: addr + 16. ZERO_RUN: 마지막 워드 + 4. */
        public final long end;
        /** WORDS: 칸 네 개(+0, +4, +8, +C)의 값. 보이지 않는 칸(구간 밖)은 {@code present[i] == false}. */
        public final int[] words;
        public final boolean[] defined;
        public final boolean[] present;
        /** 줄 안의 라벨: 주소 → 이름들. */
        public final SortedMap<Long, List<String>> labels;
        /** 줄을 가리키는 포인터: 이름 → 주소($sp 등). */
        public final Map<String, Long> pointers;
        /** SECTION(스택): 깊이 기준, 지금 깊이(기준 − $sp, 모르면 -1), 최고 수위(바이트). 그 밖은 -1·-1·0. */
        public final long base;
        public final long depth;
        public final long peak;

        Row(Kind kind, Section section, String part, long addr, long end, int[] words, boolean[] defined,
                boolean[] present, SortedMap<Long, List<String>> labels, Map<String, Long> pointers, long base,
                long depth, long peak) {
            this.kind = kind;
            this.section = section;
            this.part = part;
            this.addr = addr;
            this.end = end;
            this.words = words;
            this.defined = defined;
            this.present = present;
            this.labels = Collections.unmodifiableSortedMap(labels);
            this.pointers = Collections.unmodifiableMap(pointers);
            this.base = base;
            this.depth = depth;
            this.peak = peak;
        }

        /** 0 구간 줄의 워드 수. */
        public long count() {
            return kind == Kind.ZERO_RUN ? (end - addr) / 4 : 0;
        }

        @Override
        public String toString() {
            String a = hex(addr);
            switch (kind) {
                case SECTION:
                    return "[" + section + " " + part + " " + a + "-" + hex(end - 1) + "]";
                case ZERO_RUN:
                    return a + ".." + hex(end - 1) + " 0 x" + count() + tags();
                default:
                    StringBuilder sb = new StringBuilder(a);
                    for (int i = 0; i < 4; i += 1) {
                        sb.append(' ').append(!present[i] ? "--------" : defined[i] ? hex(words[i] & 0xffffffffL)
                                : "xxxxxxxx");
                    }
                    return sb + tags();
            }
        }

        private String tags() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Long, List<String>> e : labels.entrySet()) {
                sb.append(' ').append(String.join(",", e.getValue())).append('@').append(hex(e.getKey()));
            }
            for (Map.Entry<String, Long> e : pointers.entrySet()) {
                sb.append(' ').append(e.getKey()).append("->").append(hex(e.getValue()));
            }
            return sb.toString();
        }
    }

    static String hex(long v) {
        return String.format("%08x", v & 0xffffffffL);
    }

    /**
     * 표를 만든다. 데이터 구간들(부품 순서)을 먼저, 스택 구간들을 뒤에 둔다. labels: 주소 → 이름들(실행 이미지의 기호),
     * pointers: 이름 → 주소({@code $sp}; 모르면 빼면 된다).
     */
    public static List<Row> build(List<? extends Part> parts, Map<Long, List<String>> labels,
            Map<String, Long> pointers) {
        List<Row> out = new ArrayList<>();
        for (Part p : parts) {
            if (p.dataRegion() != null) {
                data(p, labels, pointers, out);
            }
        }
        for (Part p : parts) {
            if (p.stackRegion() != null) {
                stack(p, labels, pointers, out);
            }
        }
        return out;
    }

    // ---- 데이터 구간 ----

    private static void data(Part p, Map<Long, List<String>> labels, Map<String, Long> pointers, List<Row> out) {
        long lo = p.dataRegion()[0];
        long hi = p.dataRegion()[1];
        long from = USER_DATA >= lo && USER_DATA < hi ? USER_DATA : lo;
        long first = firstValue(p, lo, from);
        if (first >= 0) {
            from = Math.max(lo, first & ~(LINE - 1));
        }
        out.add(section(Section.DATA, p, from, hi, -1, -1, 0));
        int n = 0;
        long a = from;
        if (a % LINE != 0) { // 줄 중간에서 시작하는 구간: 짧은 줄 하나
            long stop = Math.min((a | (LINE - 1)) + 1, hi);
            out.add(words(Section.DATA, p, a & ~(LINE - 1), a, stop, labels, pointers));
            a = stop;
        }
        while (a < hi && n < MAX_ROWS) {
            long run = zeroRun(p, a, hi, pointers, true);
            if (run >= LINE) {
                out.add(zero(Section.DATA, p, a, a + run, labels, pointers));
                a += run;
            } else {
                long stop = Math.min(a + LINE, hi);
                out.add(words(Section.DATA, p, a, a, stop, labels, pointers));
                a = stop;
            }
            n += 1;
        }
    }

    /** [lo, before)에서 값(0 아님 또는 정해지지 않음)을 가진 첫 워드 주소. 없으면 -1. */
    private static long firstValue(Part p, long lo, long before) {
        for (long page : p.pages()) {
            if (page + PAGE <= lo || page >= before) {
                continue;
            }
            for (long a = Math.max(page, lo); a < Math.min(page + PAGE, before); a += 4) {
                if (!zero(p, a)) {
                    return a;
                }
            }
        }
        return -1;
    }

    // ---- 스택 구간 ----

    private static void stack(Part p, Map<Long, List<String>> labels, Map<String, Long> pointers, List<Row> out) {
        long lo = p.stackRegion()[0];
        long hi = p.stackRegion()[1];
        long base = p.depthBase();
        long lowest = p.lowestAccess();
        Long sp = pointers.get("$sp");
        boolean spInside = sp != null && sp >= lo && sp < hi;
        long bottom = Math.min(hi, Math.max(lo, base));
        if (lowest >= lo && lowest < bottom) {
            bottom = lowest;
        }
        if (spInside && sp < bottom) {
            bottom = sp;
        }
        bottom = Math.max(lo, bottom & ~(LINE - 1));
        long depth = spInside && sp <= base ? base - sp : -1;
        long peak = lowest < 0 ? 0 : base - lowest;
        out.add(section(Section.STACK, p, bottom, hi, base, depth, peak));
        // 맨 위 줄부터 아래로. 0 줄이 이어지면 한 줄로 줄인다
        long line = (hi - 1) & ~(LINE - 1);
        int n = 0;
        long[] pages = p.pages();
        while (line >= bottom && n < MAX_ROWS) {
            long runLow = line;
            while (runLow >= bottom) {
                long page = runLow & ~(PAGE - 1);
                if ((runLow & (PAGE - 1)) == PAGE - LINE && page >= bottom && Arrays.binarySearch(pages, page) < 0
                        && !pointedIn(page, page + PAGE, pointers)) {
                    runLow = page - LINE; // 쓴 적 없는 페이지: 모두 0
                } else if (zeroRun(p, runLow, runLow + LINE, pointers, false) >= LINE) {
                    runLow -= LINE;
                } else {
                    break;
                }
            }
            if (runLow < line) { // [runLow + 16, line + 16)이 모두 0
                out.add(zero(Section.STACK, p, runLow + LINE, line + LINE, labels, pointers));
                line = runLow;
            } else {
                out.add(words(Section.STACK, p, line, line, Math.min(line + LINE, hi), labels, pointers));
                line -= LINE;
            }
            n += 1;
        }
    }

    // ---- 공통 ----

    private static boolean zero(Part p, long a) {
        return p.word(a) == 0 && p.defined(a);
    }

    /**
     * a(줄 처음)부터 [a, limit)에서 0인 워드가 이어지는 길이(바이트, 줄 단위로 자름). 포인터가 가리키는 줄에서 멈춘다.
     * byLines가 false면 한 줄만 본다. 값을 가진 페이지가 없는 곳은 워드마다 읽지 않고 건너뛴다(희소 저장).
     */
    private static long zeroRun(Part p, long a, long limit, Map<String, Long> pointers, boolean byLines) {
        long[] pages = p.pages();
        long x = a;
        while (x < limit) {
            if (x % LINE == 0 && pointed(x, pointers)) {
                break;
            }
            long page = x & ~(PAGE - 1);
            if (x % PAGE == 0 && Arrays.binarySearch(pages, page) < 0 && x + PAGE <= limit
                    && !pointedIn(x, x + PAGE, pointers)) {
                x += PAGE; // 쓴 적 없는 페이지: 모두 0
                continue;
            }
            if (!zero(p, x)) {
                break;
            }
            x += 4;
            if (!byLines && x - a >= LINE) {
                break;
            }
        }
        return (x - a) & ~(LINE - 1);
    }

    private static boolean pointed(long line, Map<String, Long> pointers) {
        return pointedIn(line, line + LINE, pointers);
    }

    private static boolean pointedIn(long from, long to, Map<String, Long> pointers) {
        for (Long v : pointers.values()) {
            if (v != null && v >= from && v < to) {
                return true;
            }
        }
        return false;
    }

    private static Row section(Section s, Part p, long from, long to, long base, long depth, long peak) {
        return new Row(Kind.SECTION, s, p.name(), from, to, null, null, null, new TreeMap<Long, List<String>>(),
                new LinkedHashMap<String, Long>(), base, depth, peak);
    }

    private static Row words(Section s, Part p, long line, long from, long to, Map<Long, List<String>> labels,
            Map<String, Long> pointers) {
        int[] words = new int[4];
        boolean[] defined = new boolean[4];
        boolean[] present = new boolean[4];
        for (int i = 0; i < 4; i += 1) {
            long a = line + 4L * i;
            present[i] = a >= from && a < to;
            if (present[i]) {
                words[i] = p.word(a);
                defined[i] = p.defined(a);
            }
        }
        return new Row(Kind.WORDS, s, p.name(), line, line + LINE, words, defined, present, labelsIn(labels, from, to),
                pointersIn(pointers, from, to), -1, -1, 0);
    }

    private static Row zero(Section s, Part p, long from, long to, Map<Long, List<String>> labels,
            Map<String, Long> pointers) {
        return new Row(Kind.ZERO_RUN, s, p.name(), from, to, null, null, null, labelsIn(labels, from, to),
                pointersIn(pointers, from, to), -1, -1, 0);
    }

    private static SortedMap<Long, List<String>> labelsIn(Map<Long, List<String>> labels, long from, long to) {
        SortedMap<Long, List<String>> out = new TreeMap<>();
        for (Map.Entry<Long, List<String>> e : labels.entrySet()) {
            if (e.getKey() >= from && e.getKey() < to) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static Map<String, Long> pointersIn(Map<String, Long> pointers, long from, long to) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : pointers.entrySet()) {
            if (e.getValue() != null && e.getValue() >= from && e.getValue() < to) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /**
     * 화면에 보낼 모양(N-14가 규약에 싣는다). 줄마다 {@code {kind:"section"|"words"|"zeros", section:"data"|"stack",
     * part, addr, end, ...}}. words: {@code words:["0000002a", …]}(칸 네 개, 구간 밖 null, 정해지지 않은 칸은
     * "xxxxxxxx"), zeros: {@code count}, 공통: {@code labels:[{addr, names}]}, {@code pointers:{"$sp": addr}},
     * 스택 section: {@code base, depth, peak}. 주소는 {@code "0x10010000"} 꼴 글자다.
     */
    public static JsonArray json(List<Row> rows) {
        JsonArray out = new JsonArray();
        for (Row r : rows) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", r.kind == Kind.SECTION ? "section" : r.kind == Kind.WORDS ? "words" : "zeros");
            o.addProperty("section", r.section == Section.DATA ? "data" : "stack");
            o.addProperty("part", r.part);
            o.addProperty("addr", "0x" + hex(r.addr));
            o.addProperty("end", "0x" + hex(r.end - 1));
            if (r.kind == Kind.WORDS) {
                JsonArray w = new JsonArray();
                for (int i = 0; i < 4; i += 1) {
                    if (!r.present[i]) {
                        w.add(com.google.gson.JsonNull.INSTANCE);
                    } else {
                        w.add(r.defined[i] ? hex(r.words[i] & 0xffffffffL) : "xxxxxxxx");
                    }
                }
                o.add("words", w);
            } else if (r.kind == Kind.ZERO_RUN) {
                o.addProperty("count", r.count());
            } else if (r.section == Section.STACK) {
                o.addProperty("base", "0x" + hex(r.base));
                o.addProperty("depth", r.depth);
                o.addProperty("peak", r.peak);
            }
            if (!r.labels.isEmpty()) {
                JsonArray ls = new JsonArray();
                for (Map.Entry<Long, List<String>> e : r.labels.entrySet()) {
                    JsonObject l = new JsonObject();
                    l.addProperty("addr", "0x" + hex(e.getKey()));
                    JsonArray names = new JsonArray();
                    e.getValue().forEach(names::add);
                    l.add("names", names);
                    ls.add(l);
                }
                o.add("labels", ls);
            }
            if (!r.pointers.isEmpty()) {
                JsonObject ps = new JsonObject();
                for (Map.Entry<String, Long> e : r.pointers.entrySet()) {
                    ps.addProperty(e.getKey(), "0x" + hex(e.getValue()));
                }
                o.add("pointers", ps);
            }
            out.add(o);
        }
        return out;
    }
}
