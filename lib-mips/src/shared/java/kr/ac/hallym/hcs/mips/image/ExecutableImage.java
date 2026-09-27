/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 실행 이미지(executable image) 하나: 주소마다 놓일 워드와 바이트, 시작 주소(entry), 레지스터 시작 값, 기호, 머리 정보.
 * 바뀌지 않는 값이다. Hallym MIPS의 .hmx({@link HmxParser})와 전환용 .s 경로(lib-mips)가 같은 모델을 만들고, 불러오기는
 * 이 모델 하나만 본다(Z-01, D-126). 주소와 값은 부호 없는 32비트를 {@code long}에 담는다.
 *
 * <p>해석(docs/hmx.md): {@code .data} 바이트는 {@link #endian()}에 따라 워드로 묶고, 구간이 워드 경계에서 시작하거나
 * 끝나지 않으면 빈 바이트를 0으로 채운다. 바이트 주소는 그대로다.
 */
public final class ExecutableImage {
    /** 바이트 순서. SPIM(QtSpim)과 Hallym MIPS는 리틀 엔디언이다. */
    public enum Endian {
        LITTLE("little"), BIG("big");

        public final String word;

        Endian(String word) {
            this.word = word;
        }
    }

    /** 구간 종류. {@code .text}는 워드 단위, {@code .data}는 바이트 단위로 센다. */
    public enum Kind {
        TEXT(".text", "words", "word", 4), DATA(".data", "bytes", "byte", 1);

        public final String directive;
        /** 머리 줄의 단위 낱말. */
        public final String units;
        public final String unit;
        /** 한 단위의 바이트 수. */
        public final int unitBytes;

        Kind(String directive, String units, String unit, int unitBytes) {
            this.directive = directive;
            this.units = units;
            this.unit = unit;
            this.unitBytes = unitBytes;
        }
    }

    /** 0이 이어지는 칸({@code zero <개수>} 줄). 단위는 구간과 같다. */
    public static final class ZeroRun {
        public final long start;
        public final long count;
        public final int line;

        ZeroRun(long start, long count, int line) {
            this.start = start;
            this.count = count;
            this.line = line;
        }
    }

    /** 이어진 구간 하나({@code .text <주소> words <개수>} 또는 {@code .data <주소> bytes <개수>}). */
    public static final class Segment {
        public final Kind kind;
        /** 첫 바이트 주소. */
        public final long start;
        /** 단위 개수(.text는 워드, .data는 바이트). 0 이상. */
        public final long count;
        /** .hmx의 머리 줄 번호. 파일에서 오지 않았으면 0. */
        public final int line;
        private final List<ZeroRun> zeros;

        Segment(Kind kind, long start, long count, int line, List<ZeroRun> zeros) {
            this.kind = kind;
            this.start = start;
            this.count = count;
            this.line = line;
            this.zeros = Collections.unmodifiableList(new ArrayList<ZeroRun>(zeros));
        }

        public long bytes() {
            return count * kind.unitBytes;
        }

        /** 끝 바이트 주소 + 1. */
        public long end() {
            return start + bytes();
        }

        /** 마지막 단위의 주소(.text는 마지막 워드, .data는 마지막 바이트). 빈 구간이면 start. */
        public long last() {
            return count == 0 ? start : end() - kind.unitBytes;
        }

        public List<ZeroRun> zeroRuns() {
            return zeros;
        }

        public boolean overlaps(Segment o) {
            return bytes() > 0 && o.bytes() > 0 && start < o.end() && o.start < end();
        }

        /** 예: {@code 0x00400000–0x00400034}. */
        public String range() {
            return hex(start) + "–" + hex(last());
        }

        /** 예: {@code .text 0x00400000–0x00400034}. */
        @Override
        public String toString() {
            return kind.directive + " " + range();
        }
    }

    // ---- 머리 키 이름(1판) ----
    public static final String SOURCE = "source";
    public static final String SOURCE_SHA256 = "source-sha256";
    public static final String PRODUCED_BY = "produced-by";
    public static final String ASSEMBLED = "assembled";

    private static final String[] REG_NAMES = {"$zero", "$at", "$v0", "$v1", "$a0", "$a1", "$a2", "$a3",
        "$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7", "$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7",
        "$t8", "$t9", "$k0", "$k1", "$gp", "$sp", "$fp", "$ra"};

    private final Map<String, String> header;
    private final Endian endian;
    private final Long entry;
    private final Map<String, Long> regs;
    private final Map<String, Long> symbols;
    private final List<Segment> segments;
    private final SortedMap<Long, Integer> textWords;
    private final SortedMap<Long, Integer> dataBytes;
    private final SortedMap<Long, Integer> dataWords;

    private ExecutableImage(Builder b) {
        this.header = Collections.unmodifiableMap(new LinkedHashMap<String, String>(b.header));
        this.endian = b.endian;
        this.entry = b.entry;
        this.regs = Collections.unmodifiableMap(new LinkedHashMap<String, Long>(b.regs));
        this.symbols = Collections.unmodifiableMap(new LinkedHashMap<String, Long>(b.symbols));
        this.segments = Collections.unmodifiableList(new ArrayList<Segment>(b.segments));
        this.textWords = Collections.unmodifiableSortedMap(new TreeMap<Long, Integer>(b.textWords));
        this.dataBytes = Collections.unmodifiableSortedMap(new TreeMap<Long, Integer>(b.dataBytes));
        TreeMap<Long, Integer> words = new TreeMap<Long, Integer>();
        for (Map.Entry<Long, Integer> e : b.dataBytes.entrySet()) {
            long addr = e.getKey();
            long word = addr & ~3L;
            int i = (int) (addr & 3);
            int shift = endian == Endian.LITTLE ? 8 * i : 8 * (3 - i);
            Integer old = words.get(word);
            words.put(word, (old == null ? 0 : old) | (e.getValue() & 0xff) << shift);
        }
        this.dataWords = Collections.unmodifiableSortedMap(words);
    }

    // ---- 머리 정보 ----

    /** 머리 키 값(source, source-sha256, produced-by, assembled). 없으면 null. */
    public String header(String key) {
        return header.get(key);
    }

    public Map<String, String> header() {
        return header;
    }

    /** 원본 .s 경로(.hmx 폴더 기준 상대 경로 또는 이름). 없으면 null. */
    public String source() {
        return header.get(SOURCE);
    }

    /** 원본 .s의 SHA-256(소문자 16진수 64자). 없으면 null. */
    public String sourceSha256() {
        return header.get(SOURCE_SHA256);
    }

    public String producedBy() {
        return header.get(PRODUCED_BY);
    }

    public String assembled() {
        return header.get(ASSEMBLED);
    }

    public Endian endian() {
        return endian;
    }

    /** 시작 주소. .hmx는 늘 적는다. 전환용 .s 경로에서 main이 없으면 null. */
    public Long entry() {
        return entry;
    }

    /** 레지스터 시작 값(표준 이름 → 값, 파일 순서). 예: {@code $sp → 0x7ffff000}. */
    public Map<String, Long> regs() {
        return regs;
    }

    /** 레지스터 시작 값. name은 {@code $sp}, {@code $29} 모두 된다. 없으면 null. */
    public Long reg(String name) {
        String canonical = canonicalRegister(name);
        return canonical == null ? null : regs.get(canonical);
    }

    /** 기호(이름 → 주소, 파일 순서). 디스어셈블 표시가 쓴다(예: {@code jal 0x00400024 [main]}). */
    public Map<String, Long> symbols() {
        return symbols;
    }

    /** addr에 있는 기호 이름들(파일 순서). */
    public List<String> symbolsAt(long addr) {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Long> e : symbols.entrySet()) {
            if (e.getValue() == (addr & 0xffffffffL)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    // ---- 내용 ----

    public List<Segment> segments() {
        return segments;
    }

    public List<Segment> segments(Kind kind) {
        List<Segment> out = new ArrayList<Segment>();
        for (Segment s : segments) {
            if (s.kind == kind) {
                out.add(s);
            }
        }
        return out;
    }

    /** .text 워드(주소 → 워드). */
    public SortedMap<Long, Integer> textWords() {
        return textWords;
    }

    /** .data 바이트(주소 → 0~255). */
    public SortedMap<Long, Integer> dataBytes() {
        return dataBytes;
    }

    /** .data를 {@link #endian()}에 따라 워드로 묶은 것(워드 주소 → 워드). 빈 바이트는 0이다. */
    public SortedMap<Long, Integer> dataWords() {
        return dataWords;
    }

    /** 구간들에 놓이는 워드(.text는 그 워드, .data는 그 바이트를 담은 워드). */
    public SortedMap<Long, Integer> words(List<Segment> list) {
        TreeMap<Long, Integer> out = new TreeMap<Long, Integer>();
        for (Segment s : list) {
            if (s.count == 0) {
                continue;
            }
            SortedMap<Long, Integer> from = s.kind == Kind.TEXT ? textWords : dataWords;
            out.putAll(from.subMap(s.start & ~3L, s.end()));
        }
        return out;
    }

    /** 같은 프로그램인가: 워드, entry, 레지스터 시작 값, 기호가 같다(머리 정보와 줄 번호는 보지 않는다). */
    public boolean sameProgram(ExecutableImage o) {
        return textWords.equals(o.textWords) && dataWords.equals(o.dataWords)
                && (entry == null ? o.entry == null : entry.equals(o.entry))
                && regs.equals(o.regs) && symbols.equals(o.symbols);
    }

    // ---- 요약 글(이름·숫자뿐이라 영어, D-049) ----

    /** 예: {@code 14 words (0x00400000–0x00400034)}. 구간이 여럿이면 범위를 쉼표로 잇는다. */
    public static String describe(List<Segment> list) {
        if (list.isEmpty()) {
            return "0 words";
        }
        long units = 0;
        StringBuilder ranges = new StringBuilder();
        java.util.TreeSet<Long> words = new java.util.TreeSet<Long>();
        Kind kind = list.get(0).kind;
        for (Segment s : list) {
            units += s.count;
            if (ranges.length() > 0) {
                ranges.append(", ");
            }
            ranges.append(s.range());
            for (long a = s.start & ~3L; a < s.end(); a += 4) {
                words.add(a);
            }
        }
        String amount = count(units, kind.unit);
        if (kind == Kind.DATA) {
            amount += " = " + count(words.size(), "word");
        }
        return amount + " (" + ranges + ")";
    }

    /** 예: {@code 1 word}, {@code 14 words}. */
    public static String count(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s");
    }

    /** {@code 0x0040a0b0}(8자리, 소문자). */
    public static String hex(long v) {
        String s = Long.toHexString(v & 0xffffffffL);
        return "0x" + "00000000".substring(s.length()) + s;
    }

    /** {@code $sp}, {@code $29}, {@code $s8} → 표준 이름({@code $sp}, {@code $sp}, {@code $fp}). 모르면 null. */
    public static String canonicalRegister(String name) {
        if (name == null || !name.startsWith("$")) {
            return null;
        }
        String n = name.substring(1);
        if (n.matches("\\d{1,2}")) {
            int i = Integer.parseInt(n);
            return i < 32 ? REG_NAMES[i] : null;
        }
        if (n.equals("s8")) {
            return "$fp";
        }
        for (String r : REG_NAMES) {
            if (r.substring(1).equals(n)) {
                return r;
            }
        }
        return null;
    }

    /** 이미지를 만든다. 파서와 전환용 .s 경로가 쓴다. 구간 겹침 검사는 파서 몫이다. */
    public static final class Builder {
        private final Map<String, String> header = new LinkedHashMap<String, String>();
        private Endian endian = Endian.LITTLE;
        private Long entry;
        private final Map<String, Long> regs = new LinkedHashMap<String, Long>();
        private final Map<String, Long> symbols = new LinkedHashMap<String, Long>();
        private final List<Segment> segments = new ArrayList<Segment>();
        private final TreeMap<Long, Integer> textWords = new TreeMap<Long, Integer>();
        private final TreeMap<Long, Integer> dataBytes = new TreeMap<Long, Integer>();

        public Builder header(String key, String value) {
            header.put(key, value);
            return this;
        }

        public Builder endian(Endian e) {
            endian = e;
            return this;
        }

        public Builder entry(Long addr) {
            entry = addr == null ? null : addr & 0xffffffffL;
            return this;
        }

        /** name은 {@link #canonicalRegister}로 바꿔 둔다. 모르는 이름이면 IllegalArgumentException. */
        public Builder reg(String name, long value) {
            String c = canonicalRegister(name);
            if (c == null) {
                throw new IllegalArgumentException("register " + name);
            }
            regs.put(c, value & 0xffffffffL);
            return this;
        }

        public Builder symbol(String name, long addr) {
            symbols.put(name, addr & 0xffffffffL);
            return this;
        }

        /** .text 구간 하나(워드 주소 정렬은 부르는 쪽이 지킨다). */
        public Builder text(long start, int... words) {
            return segment(Kind.TEXT, start, words.clone(), words.length, 0, Collections.<ZeroRun>emptyList());
        }

        /** .data 구간 하나(바이트 0~255). */
        public Builder data(long start, int... bytes) {
            return segment(Kind.DATA, start, bytes.clone(), bytes.length, 0, Collections.<ZeroRun>emptyList());
        }

        /** units의 앞 count개(.text는 워드, .data는 바이트). */
        Builder segment(Kind kind, long start, int[] units, int count, int line, List<ZeroRun> zeros) {
            segments.add(new Segment(kind, start & 0xffffffffL, count, line, zeros));
            for (int i = 0; i < count; i += 1) {
                long addr = (start & 0xffffffffL) + (long) i * kind.unitBytes;
                if (kind == Kind.TEXT) {
                    textWords.put(addr, units[i]);
                } else {
                    dataBytes.put(addr, units[i] & 0xff);
                }
            }
            return this;
        }

        public ExecutableImage build() {
            return new ExecutableImage(this);
        }
    }
}
