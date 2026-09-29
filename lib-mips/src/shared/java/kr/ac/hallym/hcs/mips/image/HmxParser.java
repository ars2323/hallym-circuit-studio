/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 실행 이미지 파일(.hmx 1판)을 읽는다(Z-01, D-126, D-138). 글 → 결과(이미지, 또는 줄 번호가 붙은 오류 목록). 오류가 하나라도
 * 있으면 이미지는 없고, 불러오는 쪽은 아무것도 바꾸지 않는다.
 *
 * <p>기준은 Hallym MIPS의 명세 {@code docs/hmx-format.md}(v2.6.0으로 고정, v2.4.0과 바이트까지 같음, docs/hmx.md)다. 무시하는 줄이 아닌 첫 줄이
 * {@code HALLYM-EXEC <판>} 머리 줄이고, 필드(아무 순서), 구간({@code .text}, {@code .data}) 순이다. 읽는 쪽이 할 일(명세
 * "What a reader must do")을 그대로 한다: 1판보다 높은 판은 읽지 않고, 개수를 모두 확인하고, {@code endian}·{@code entry}·
 * {@code .text}가 없거나 모르는 구간, 같은 종류의 두 번째 구간, 틀린 숫자는 오류이고, 빈 줄·{@code #} 줄·모르는 필드는
 * 읽지 않는다. 명세가 말하지 않은 곳의 선택은 docs/hmx.md에 모았다.
 */
public final class HmxParser {
    /** 읽은 결과. {@link #image}와 {@link #errors} 중 하나만 있다. */
    public static final class Result {
        public final ExecutableImage image;
        /** 줄 번호 순(줄이 없는 오류는 맨 뒤). */
        public final List<HmxError> errors;
        /** 읽지 않고 넘긴 모르는 필드(줄 번호 → 첫 낱말). 명세대로 오류가 아니다. */
        public final Map<Integer, String> ignoredFields;

        Result(ExecutableImage image, List<HmxError> errors, Map<Integer, String> ignoredFields) {
            this.image = image;
            List<HmxError> sorted = new ArrayList<HmxError>(errors);
            Collections.sort(sorted, BY_LINE);
            this.errors = Collections.unmodifiableList(sorted);
            this.ignoredFields = Collections.unmodifiableMap(new java.util.TreeMap<Integer, String>(ignoredFields));
        }

        public boolean ok() {
            return image != null;
        }
    }

    /** 줄 번호 순. 줄이 없는 오류(0)는 뒤로. */
    static final Comparator<HmxError> BY_LINE = new Comparator<HmxError>() {
        @Override
        public int compare(HmxError a, HmxError b) {
            return Long.compare(order(a.line), order(b.line));
        }

        private long order(int line) {
            return line <= 0 ? Long.MAX_VALUE : line;
        }
    };

    private static final String AGAIN_KO = " Hallym MIPS에서 다시 내보내세요.";
    private static final String AGAIN_EN = " Export it again from Hallym MIPS.";

    /** 1판의 필드 이름. 이 밖의 필드는 읽지 않는다(명세: 새 필드는 판을 올리지 않는다). */
    private static final String[] FIELDS = {ExecutableImage.SOURCE, ExecutableImage.SOURCE_SHA256,
        ExecutableImage.PRODUCED_BY, ExecutableImage.ASSEMBLED, "endian", "entry", "reg", "symbol"};

    /** 한 구간의 단위 개수 상한(16M). 메모리 부품 한계(기본 1MB)보다 훨씬 크다. */
    static final long MAX_UNITS = 1L << 24;

    private final List<HmxError> errors = new ArrayList<HmxError>();
    private final Map<Integer, String> ignored = new HashMap<Integer, String>();
    private final ExecutableImage.Builder builder = new ExecutableImage.Builder();
    /** 필드 → 처음 나온 줄(두 번 나옴 검사). reg·symbol은 "reg $sp"처럼 이름까지 붙인다. */
    private final Map<String, Integer> seen = new HashMap<String, Integer>();
    /** 구간 종류 → 머리 줄(두 번째 구간 검사). */
    private final Map<ExecutableImage.Kind, Integer> sections =
            new EnumMap<ExecutableImage.Kind, Integer>(ExecutableImage.Kind.class);
    private final List<ExecutableImage.Segment> segmentHeads = new ArrayList<ExecutableImage.Segment>();
    private boolean header;
    private boolean inSections;
    private boolean sawEntry;
    private boolean sawEndian;

    // 읽는 중인 구간. kind가 null이고 skipping이면 받아들이지 않은 구간의 줄을 넘긴다.
    private ExecutableImage.Kind kind;
    private boolean skipping;
    private boolean segOk;
    private long segStart;
    private long declared;
    private int segLine;
    private int[] units = new int[0];
    private int filled;
    private boolean overflow;
    /** 구간 안에 읽지 못한 줄이 있었다(그러면 개수가 모자란다고 따로 알리지 않는다). */
    private boolean malformed;
    private List<ExecutableImage.ZeroRun> zeros;

    private HmxParser() {
    }

    /** 파일을 UTF-8로 읽어 {@link #parse(String)}. 읽지 못하면 IOException. */
    public static Result read(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0;) {
                out.write(buf, 0, n);
            }
            return parse(new String(out.toByteArray(), Charset.forName("UTF-8")));
        } finally {
            in.close();
        }
    }

    public static Result parse(String text) {
        return new HmxParser().run(text == null ? "" : text);
    }

    private Result run(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        String[] lines = text.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i += 1) {
            if (!line(i + 1, lines[i].trim())) {
                return new Result(null, errors, ignored); // 머리 줄이 틀리면 나머지를 읽지 않는다
            }
        }
        if (!header) {
            notAnImage(0);
            return new Result(null, errors, ignored);
        }
        finishSection();
        if (!sawEndian) {
            error(0, Msg.of("There is no endian line. An executable image states its byte order: endian little or"
                    + " endian big." + AGAIN_EN,
                    "endian 줄이 없습니다. 실행 이미지에는 바이트 순서 줄이 있어야 합니다: endian little 또는 endian big."
                            + AGAIN_KO));
        }
        if (!sawEntry) {
            error(0, Msg.of("There is no entry line. An executable image states its entry address." + AGAIN_EN,
                    "entry 줄이 없습니다. 실행 이미지에는 시작 주소 줄이 있어야 합니다." + AGAIN_KO));
        }
        if (!sections.containsKey(ExecutableImage.Kind.TEXT)) {
            error(0, Msg.of("There is no .text section. An executable image holds the program's instruction words."
                    + AGAIN_EN,
                    ".text 구간이 없습니다. 실행 이미지에는 프로그램의 명령어 워드 구간이 있어야 합니다." + AGAIN_KO));
        }
        overlaps();
        if (!errors.isEmpty()) {
            return new Result(null, errors, ignored);
        }
        return new Result(builder.build(), errors, ignored);
    }

    /** 한 줄. 더 읽을 수 없으면(머리 줄이 틀림) false. */
    private boolean line(int n, String s) {
        if (s.isEmpty() || s.startsWith("#")) {
            return true;
        }
        String[] t = s.split("\\s+");
        if (!header) {
            return headerLine(n, t);
        }
        if (t[0].startsWith(".")) {
            finishSection();
            inSections = true;
            sectionHead(n, t);
        } else if (inSections) {
            content(n, t);
        } else {
            field(n, s, t);
        }
        return true;
    }

    /** 머리 줄: HALLYM-EXEC 1. 읽기를 계속할 수 있으면 true. */
    private boolean headerLine(int n, String[] t) {
        if (!t[0].equals(HmxFormat.MAGIC) || t.length != 2) {
            notAnImage(n);
            return false;
        }
        if (!t[1].matches("\\d{1,6}")) {
            error(n, Msg.of("The format version is not a number: " + t[1] + "." + AGAIN_EN,
                    "판 번호가 숫자가 아닙니다: " + t[1] + "." + AGAIN_KO));
            return false;
        }
        int version = Integer.parseInt(t[1]);
        if (version != HmxFormat.VERSION) {
            error(n, Msg.of("This file is executable image version " + version + ". This tool reads version "
                    + HmxFormat.VERSION + " only. Update Hallym Circuit Studio to a newer version.",
                    "이 파일은 실행 이미지 " + version + "판입니다. 이 도구는 " + HmxFormat.VERSION
                            + "판만 읽습니다. Hallym Circuit Studio를 새 판으로 바꾸세요."));
            return false;
        }
        header = true;
        return true;
    }

    private void notAnImage(int n) {
        error(n, Msg.of("The first line is not the " + HmxFormat.MAGIC + " header, so this is not an executable"
                + " image. Choose a .hmx file exported from Hallym MIPS.",
                "첫 줄이 " + HmxFormat.MAGIC + " 머리 줄이 아니라서 실행 이미지 파일이 아닙니다."
                        + " Hallym MIPS에서 내보낸 .hmx 파일을 고르세요."));
    }

    // ---- 필드 ----

    private static boolean isField(String word) {
        for (String f : FIELDS) {
            if (f.equals(word)) {
                return true;
            }
        }
        return false;
    }

    /** 워드·바이트 줄처럼 보이는가: 낱말이 모두 16진수 2자리 또는 8자리. */
    private static boolean looksLikeUnits(String[] t) {
        for (String x : t) {
            if (!x.matches("[0-9a-fA-F]{2}|[0-9a-fA-F]{8}")) {
                return false;
            }
        }
        return true;
    }

    private void field(int n, String s, String[] t) {
        String key = t[0];
        if (key.equals("reg")) {
            reg(n, t);
        } else if (key.equals("symbol")) {
            symbol(n, t);
        } else if (key.equals("entry")) {
            sawEntry = true;
            if (single(n, t, "entry")) {
                Long v = address(n, "entry", t[1]);
                if (v != null) {
                    builder.entry(v);
                }
            }
        } else if (key.equals("endian")) {
            sawEndian = true;
            if (single(n, t, "endian")) {
                if (t[1].equals("little")) {
                    builder.endian(ExecutableImage.Endian.LITTLE);
                } else if (t[1].equals("big")) {
                    builder.endian(ExecutableImage.Endian.BIG);
                } else {
                    error(n, Msg.of("The endian value is neither little nor big: " + t[1] + "." + AGAIN_EN,
                            "endian 값으로 읽을 수 없습니다: " + t[1] + ". 값은 little, big 둘 중 하나입니다." + AGAIN_KO));
                }
            }
        } else if (key.equals(ExecutableImage.SOURCE) || key.equals(ExecutableImage.PRODUCED_BY)
                || key.equals(ExecutableImage.ASSEMBLED)) {
            String value = s.substring(key.length()).trim();
            if (value.isEmpty()) {
                missingValue(n, key);
            } else if (first(n, key)) {
                builder.header(key, value);
            }
        } else if (key.equals(ExecutableImage.SOURCE_SHA256)) {
            if (single(n, t, key)) {
                if (t[1].matches("[0-9a-fA-F]{64}")) {
                    builder.header(key, t[1].toLowerCase());
                } else {
                    error(n, Msg.of("The source-sha256 value is not 64 hexadecimal digits: " + t[1] + "." + AGAIN_EN,
                            "source-sha256 값이 16진수 64자리가 아닙니다: " + t[1] + "." + AGAIN_KO));
                }
            }
        } else if (key.equals("zero") || looksLikeUnits(t)) {
            error(n, Msg.of("Words, bytes and zero lines can only be inside a section (.text, .data): " + join(t) + "."
                    + AGAIN_EN,
                    "워드·바이트 줄과 zero 줄은 구간(.text, .data) 안에만 올 수 있습니다: " + join(t) + "." + AGAIN_KO));
        } else {
            ignored.put(n, key); // 모르는 필드: 읽지 않는다(명세 "What a reader must do" 4)
        }
    }

    /** 값 하나짜리 필드: 값이 있고 처음 나왔으면 true. */
    private boolean single(int n, String[] t, String key) {
        if (t.length < 2) {
            missingValue(n, key);
            return false;
        }
        if (t.length > 2) {
            error(n, Msg.of("The " + key + " line has more than one value: " + join(t) + "." + AGAIN_EN,
                    key + " 줄에 값이 둘 이상입니다: " + join(t) + "." + AGAIN_KO));
            return false;
        }
        return first(n, key);
    }

    private boolean first(int n, String key) {
        Integer before = seen.get(key);
        if (before != null) {
            error(n, Msg.of("The " + key + " key is already on line " + before + ". A key can appear only once."
                    + AGAIN_EN,
                    key + " 키가 " + before + "번째 줄에 이미 있습니다. 키는 한 번만 적을 수 있습니다." + AGAIN_KO));
            return false;
        }
        seen.put(key, n);
        return true;
    }

    private void missingValue(int n, String key) {
        error(n, Msg.of("The " + key + " line has no value." + AGAIN_EN,
                key + " 줄에 값이 없습니다." + AGAIN_KO));
    }

    private void reg(int n, String[] t) {
        if (t.length != 3) {
            error(n, Msg.of("The reg line must be: reg <register> <value>." + AGAIN_EN,
                    "reg 줄의 형식이 틀렸습니다. 형식: reg <레지스터> <값>." + AGAIN_KO));
            return;
        }
        String c = ExecutableImage.canonicalRegister(t[1]);
        if (c == null) {
            error(n, Msg.of("Unknown register name: " + t[1] + "." + AGAIN_EN,
                    "레지스터 이름을 읽을 수 없습니다: " + t[1] + "." + AGAIN_KO));
            return;
        }
        Long v = address(n, "reg " + t[1], t[2]);
        if (v != null && first(n, "reg " + c)) {
            builder.reg(c, v);
        }
    }

    private void symbol(int n, String[] t) {
        if (t.length != 3) {
            error(n, Msg.of("The symbol line must be: symbol <name> <address>." + AGAIN_EN,
                    "symbol 줄의 형식이 틀렸습니다. 형식: symbol <이름> <주소>." + AGAIN_KO));
            return;
        }
        Long v = address(n, "symbol " + t[1], t[2]);
        if (v != null && first(n, "symbol " + t[1])) {
            builder.symbol(t[1], v);
        }
    }

    /** {@code <addr>}: {@code 0x} 뒤에 16진수 정확히 8자리(대소문자 모두). 아니면 오류를 남기고 null. */
    private Long address(int n, String what, String s) {
        if (s.matches("0[xX][0-9a-fA-F]{8}")) {
            return Long.parseLong(s.substring(2), 16);
        }
        error(n, Msg.of("The " + what + " value is not an address of 0x and 8 hexadecimal digits: " + s + "."
                + AGAIN_EN,
                what + " 값을 읽을 수 없습니다: " + s + ". 값은 0x 뒤에 16진수 8자리를 적은 모양입니다." + AGAIN_KO));
        return null;
    }

    // ---- 구간 ----

    private void sectionHead(int n, String[] t) {
        ExecutableImage.Kind k = t[0].equals(ExecutableImage.Kind.TEXT.directive) ? ExecutableImage.Kind.TEXT
                : t[0].equals(ExecutableImage.Kind.DATA.directive) ? ExecutableImage.Kind.DATA : null;
        skipping = true; // 받아들이지 않은 구간의 줄은 넘긴다
        if (k == null) {
            error(n, Msg.of("Unknown section: " + t[0] + ". Version " + HmxFormat.VERSION + " has only the .text and"
                    + " .data sections." + AGAIN_EN,
                    "이 도구가 모르는 구간입니다: " + t[0] + ". 실행 이미지 " + HmxFormat.VERSION
                            + "판의 구간은 .text, .data 둘뿐입니다." + AGAIN_KO));
            return;
        }
        Integer before = sections.get(k);
        if (before != null) {
            error(n, Msg.of("There is already a " + k.directive + " section on line " + before + ". An image has at"
                    + " most one section of each kind." + AGAIN_EN,
                    before + "번째 줄에 이미 " + k.directive + " 구간이 있습니다. 구간은 종류마다 하나만 있을 수 있습니다."
                            + AGAIN_KO));
            return;
        }
        sections.put(k, n);
        if (t.length != 4 || !t[2].equals(k.units) || !t[3].matches("\\d{1,10}")) {
            error(n, Msg.of("The " + k.directive + " line must be: " + k.directive + " <address> " + k.units
                    + " <count>." + AGAIN_EN,
                    k.directive + " 줄의 형식이 틀렸습니다. 형식: " + k.directive + " <주소> " + k.units + " <개수>."
                            + AGAIN_KO));
            return;
        }
        Long start = address(n, k.directive, t[1]);
        long count = Long.parseLong(t[3]);
        boolean ok = start != null;
        if (count > MAX_UNITS) {
            error(n, Msg.of("The " + k.directive + " section is too large: " + ExecutableImage.count(count, k.unit)
                    + " (at most " + MAX_UNITS + ")." + AGAIN_EN,
                    k.directive + " 구간이 너무 큽니다: " + unitsKo(k, count) + "(최대 " + MAX_UNITS + "개)." + AGAIN_KO));
            return;
        }
        if (ok && k == ExecutableImage.Kind.TEXT && (start & 3) != 0) {
            error(n, Msg.of("The .text address is not on a word boundary (a multiple of 4): "
                    + ExecutableImage.hex(start) + "." + AGAIN_EN,
                    ".text 시작 주소가 4바이트 워드 경계에 있지 않습니다: " + ExecutableImage.hex(start) + "." + AGAIN_KO));
            ok = false;
        } else if (ok && start + count * k.unitBytes > 0x100000000L) {
            error(n, Msg.of("The " + k.directive + " section goes past address 0xffffffff." + AGAIN_EN,
                    k.directive + " 구간이 주소 0xffffffff 너머까지 이어집니다." + AGAIN_KO));
            ok = false;
        }
        skipping = false;
        kind = k;
        segOk = ok;
        segStart = start == null ? 0 : start;
        declared = count;
        segLine = n;
        units = new int[(int) Math.min(count, 4096)];
        filled = 0;
        overflow = false;
        malformed = false;
        zeros = new ArrayList<ExecutableImage.ZeroRun>();
    }

    /** 구간 안의 줄: 워드(.text)·바이트(.data) 여럿, 또는 {@code zero <개수>}. */
    private void content(int n, String[] t) {
        if (skipping) {
            return;
        }
        if (isField(t[0])) {
            error(n, Msg.of("The " + t[0] + " line must come before the first section (.text, .data)." + AGAIN_EN,
                    t[0] + " 줄은 첫 구간(.text, .data)보다 앞에 있어야 합니다." + AGAIN_KO));
            return;
        }
        if (t[0].equals("zero")) {
            if (t.length != 2 || !t[1].matches("\\d{1,10}")) {
                error(n, Msg.of("The zero line must be: zero <count>." + AGAIN_EN,
                        "zero 줄의 형식이 틀렸습니다. 형식: zero <개수>." + AGAIN_KO));
                malformed = true;
                return;
            }
            long c = Long.parseLong(t[1]);
            if (filled + c > declared) {
                more(n);
                return;
            }
            if (c > 0) {
                zeros.add(new ExecutableImage.ZeroRun(segStart + (long) filled * kind.unitBytes, c, n));
            }
            for (long i = 0; i < c; i += 1) {
                put(0);
            }
            return;
        }
        String pattern = kind == ExecutableImage.Kind.TEXT ? "[0-9a-fA-F]{8}" : "[0-9a-fA-F]{2}";
        for (String u : t) {
            if (!u.matches(pattern)) {
                if (kind == ExecutableImage.Kind.TEXT) {
                    error(n, Msg.of("A .text word must be 8 hexadecimal digits: " + u + "." + AGAIN_EN,
                            ".text 워드는 16진수 8자리여야 합니다: " + u + "." + AGAIN_KO));
                } else {
                    error(n, Msg.of("A .data byte must be 2 hexadecimal digits: " + u + "." + AGAIN_EN,
                            ".data 바이트는 16진수 2자리여야 합니다: " + u + "." + AGAIN_KO));
                }
                malformed = true;
                return;
            }
        }
        if (filled + t.length > declared) {
            more(n);
            return;
        }
        for (String u : t) {
            put((int) Long.parseLong(u, 16));
        }
    }

    private void put(int unit) {
        if (filled == units.length) {
            units = java.util.Arrays.copyOf(units, Math.max(16, units.length * 2));
        }
        units[filled++] = unit;
    }

    /** 개수보다 많다(구간마다 한 번만 알린다). */
    private void more(int n) {
        if (!overflow) {
            error(n, Msg.of("The " + kind.directive + " section on line " + segLine + " declares "
                    + ExecutableImage.count(declared, kind.unit) + ", but there are more." + AGAIN_EN,
                    segLine + "번째 줄의 " + kind.directive + " 구간은 " + unitsKo(kind, declared)
                            + "라고 적혀 있지만 그보다 많습니다." + AGAIN_KO));
        }
        overflow = true;
    }

    /** 다음 구간 머리나 파일 끝: 개수만큼 찼는지 보고 이미지에 넣는다. */
    private void finishSection() {
        if (kind != null) {
            if (filled < declared && !malformed && !overflow) {
                error(segLine, Msg.of("The " + kind.directive + " line declares "
                        + ExecutableImage.count(declared, kind.unit) + ", but there " + (filled == 1 ? "is " : "are ")
                        + filled + ". The file may be cut off." + AGAIN_EN,
                        kind.directive + " 줄에는 " + unitsKo(kind, declared) + "라고 적혀 있지만 실제로는 " + filled
                                + "개입니다. 파일이 잘렸을 수 있습니다." + AGAIN_KO));
            } else if (segOk && !overflow && !malformed) {
                builder.segment(kind, segStart, units, filled, segLine, zeros);
                segmentHeads.add(new ExecutableImage.Segment(kind, segStart, filled, segLine, zeros));
            }
        }
        kind = null;
        skipping = false;
    }

    /** "워드 14개" / "바이트 12개". */
    private static String unitsKo(ExecutableImage.Kind k, long n) {
        return (k == ExecutableImage.Kind.TEXT ? "워드 " : "바이트 ") + n + "개";
    }

    private void overlaps() {
        for (int i = 0; i < segmentHeads.size(); i += 1) {
            for (int j = 0; j < i; j += 1) {
                ExecutableImage.Segment a = segmentHeads.get(j);
                ExecutableImage.Segment b = segmentHeads.get(i);
                if (a.overlaps(b)) {
                    error(b.line, Msg.of("This section overlaps the section on line " + a.line + ": " + b + " and "
                            + a + "." + AGAIN_EN,
                            "이 구간이 " + a.line + "번째 줄의 구간과 겹칩니다: " + b + ", " + a + "." + AGAIN_KO));
                }
            }
        }
    }

    private void error(int line, Msg what) {
        errors.add(new HmxError(line, what));
    }

    private static String join(String[] t) {
        StringBuilder sb = new StringBuilder();
        for (String s : t) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(s);
        }
        return sb.toString();
    }
}
