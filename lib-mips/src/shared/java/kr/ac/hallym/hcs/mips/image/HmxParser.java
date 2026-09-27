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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 실행 이미지 파일(.hmx 1판)을 읽는다(Z-01). 글 → 결과(이미지, 또는 줄 번호가 붙은 오류 목록). 오류가 하나라도 있으면
 * 이미지는 없고, 불러오는 쪽은 아무것도 바꾸지 않는다. 형식과 이 도구의 해석은 docs/hmx.md.
 *
 * <p>1판의 줄: {@code HALLYM-EXEC 1}(첫 줄), {@code source}, {@code source-sha256}, {@code produced-by},
 * {@code assembled}, {@code endian}, {@code entry}, {@code reg <이름> <값>}, {@code symbol <이름> <주소>},
 * {@code .text <주소> words <개수>}와 워드 줄, {@code .data <주소> bytes <개수>}와 바이트 줄, 구간 안의
 * {@code zero <개수>}. {@code #}로 시작하는 줄과 빈 줄은 읽지 않는다. 1판에서 새 키는 새 판이므로 모르는 키는 오류다.
 */
public final class HmxParser {
    /** 읽은 결과. {@link #image}와 {@link #errors} 중 하나만 있다. */
    public static final class Result {
        public final ExecutableImage image;
        public final List<HmxError> errors;

        Result(ExecutableImage image, List<HmxError> errors) {
            this.image = image;
            this.errors = Collections.unmodifiableList(new ArrayList<HmxError>(errors));
        }

        public boolean ok() {
            return image != null;
        }
    }

    private static final String AGAIN_KO = " Hallym MIPS에서 다시 내보내세요.";
    private static final String AGAIN_EN = " Export it again from Hallym MIPS.";

    private final List<HmxError> errors = new ArrayList<HmxError>();
    private final ExecutableImage.Builder builder = new ExecutableImage.Builder();
    /** 키 → 처음 나온 줄(겹침 검사). reg·symbol은 "reg $sp"처럼 이름까지 붙인다. */
    private final Map<String, Integer> seen = new HashMap<String, Integer>();
    private final List<ExecutableImage.Segment> segmentHeads = new ArrayList<ExecutableImage.Segment>();

    // 읽는 중인 구간
    private ExecutableImage.Kind kind;
    private long segStart;
    private long declared;
    private int segLine;
    private int[] units = new int[0];
    private int filled;
    /** 한 구간의 단위 개수 상한(16M). 메모리 부품 한계(기본 1MB)보다 훨씬 크다. */
    static final long MAX_UNITS = 1L << 24;
    private boolean sawEntry;
    private List<ExecutableImage.ZeroRun> zeros;
    /** 바로 앞 줄에서 구간이 다 찼다(그 뒤 데이터 줄은 "개수보다 많음"). */
    private ExecutableImage.Segment justClosed;

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
        if (!firstLine(lines[0].trim())) {
            return new Result(null, errors);
        }
        for (int i = 1; i < lines.length; i += 1) {
            line(i + 1, lines[i].trim());
        }
        if (kind != null) {
            truncated();
        }
        if (!sawEntry) {
            error(0, Msg.of("There is no entry line. An executable image states its entry address." + AGAIN_EN,
                    "entry 줄이 없습니다. 실행 이미지에는 시작 주소 줄이 있어야 합니다." + AGAIN_KO));
        }
        overlaps();
        if (!errors.isEmpty()) {
            return new Result(null, errors);
        }
        return new Result(builder.build(), errors);
    }

    /** 첫 줄: HALLYM-EXEC 1. 읽기를 계속할 수 있으면 true. */
    private boolean firstLine(String first) {
        String[] t = first.split("\\s+");
        if (!t[0].equals(HmxFormat.MAGIC) || t.length != 2) {
            error(1, Msg.of("The first line is not the " + HmxFormat.MAGIC + " header, so this is not an executable"
                    + " image. Choose a .hmx file exported from Hallym MIPS.",
                    "첫 줄이 " + HmxFormat.MAGIC + " 머리 줄이 아니라서 실행 이미지 파일이 아닙니다."
                            + " Hallym MIPS에서 내보낸 .hmx 파일을 고르세요."));
            return false;
        }
        if (!t[1].matches("\\d{1,6}")) {
            error(1, Msg.of("The format version is not a number: " + t[1] + "." + AGAIN_EN,
                    "판 번호가 숫자가 아닙니다: " + t[1] + "." + AGAIN_KO));
            return false;
        }
        int version = Integer.parseInt(t[1]);
        if (version != HmxFormat.VERSION) {
            error(1, Msg.of("This file is executable image version " + version + ". This tool reads version "
                    + HmxFormat.VERSION + " only. Update Hallym Circuit Studio to a newer version.",
                    "이 파일은 실행 이미지 " + version + "판입니다. 이 도구는 " + HmxFormat.VERSION
                            + "판만 읽습니다. Hallym Circuit Studio를 새 판으로 바꾸세요."));
            return false;
        }
        return true;
    }

    private void line(int n, String s) {
        if (s.isEmpty() || s.startsWith("#")) {
            return;
        }
        String[] t = s.split("\\s+");
        if (kind != null) {
            if (isKeyWord(t[0])) {
                truncated(); // 구간이 덜 찼는데 다음 키가 왔다
            } else {
                unitLine(n, t);
                return;
            }
        }
        ExecutableImage.Segment closed = justClosed;
        justClosed = null;
        String key = t[0];
        if (key.equals(ExecutableImage.Kind.TEXT.directive) || key.equals(ExecutableImage.Kind.DATA.directive)) {
            segmentHead(n, t);
        } else if (key.equals("reg")) {
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
        } else if (key.equals("zero")) {
            error(n, Msg.of("A zero line can only be inside a segment (.text, .data)." + AGAIN_EN,
                    "zero 줄은 구간(.text, .data) 안에만 올 수 있습니다." + AGAIN_KO));
        } else if (closed != null && looksLikeUnits(closed.kind, t)) {
            error(n, Msg.of("The " + closed.kind.directive + " segment on line " + closed.line + " declares "
                    + ExecutableImage.count(closed.count, closed.kind.unit) + ", but there are more." + AGAIN_EN,
                    closed.line + "번째 줄의 " + closed.kind.directive + " 구간은 " + unitsKo(closed.kind, closed.count)
                            + "라고 적혀 있지만 그보다 많습니다." + AGAIN_KO));
        } else {
            error(n, Msg.of("Unknown key: " + key + ". Version " + HmxFormat.VERSION + " has no such key, so the file"
                    + " may be a newer version. Update Hallym Circuit Studio to a newer version.",
                    "이 도구가 모르는 키입니다: " + key + ". 실행 이미지 " + HmxFormat.VERSION
                            + "판에는 없는 키라서 새 판 파일일 수 있습니다. Hallym Circuit Studio를 새 판으로 바꾸세요."));
        }
    }

    private static boolean isKeyWord(String t) {
        return t.startsWith(".") || t.matches("[A-Za-z][A-Za-z0-9-]*") && !t.equals("zero") && !isHex(t);
    }

    private static boolean isHex(String t) {
        return t.matches("[0-9a-fA-F]+");
    }

    private static boolean looksLikeUnits(ExecutableImage.Kind k, String[] t) {
        for (String x : t) {
            if (!isHex(x)) {
                return x.equals("zero");
            }
        }
        return true;
    }

    /** "워드 14개" / "바이트 12개". */
    private static String unitsKo(ExecutableImage.Kind k, long n) {
        return (k == ExecutableImage.Kind.TEXT ? "워드 " : "바이트 ") + n + "개";
    }

    // ---- 구간 ----

    private void segmentHead(int n, String[] t) {
        ExecutableImage.Kind k = t[0].equals(ExecutableImage.Kind.TEXT.directive) ? ExecutableImage.Kind.TEXT
                : ExecutableImage.Kind.DATA;
        if (t.length != 4 || !t[2].equals(k.units) || !t[3].matches("\\d{1,10}")) {
            error(n, Msg.of("The " + k.directive + " line must be: " + k.directive + " <address> " + k.units
                    + " <count>." + AGAIN_EN,
                    k.directive + " 줄의 형식이 틀렸습니다. 형식: " + k.directive + " <주소> " + k.units + " <개수>."
                            + AGAIN_KO));
            skip(k, n);
            return;
        }
        Long start = address(n, k.directive, t[1]);
        long count = Long.parseLong(t[3]);
        long bad = -1;
        if (start == null) {
            bad = 0;
        } else if (k == ExecutableImage.Kind.TEXT && (start & 3) != 0) {
            error(n, Msg.of("The .text address is not on a word boundary (a multiple of 4): "
                    + ExecutableImage.hex(start) + "." + AGAIN_EN,
                    ".text 시작 주소가 4바이트 워드 경계에 있지 않습니다: " + ExecutableImage.hex(start) + "." + AGAIN_KO));
            bad = 0;
        } else if (count > MAX_UNITS) {
            error(n, Msg.of("The " + k.directive + " segment is too large: " + ExecutableImage.count(count, k.unit)
                    + " (at most " + MAX_UNITS + ")." + AGAIN_EN,
                    k.directive + " 구간이 너무 큽니다: " + unitsKo(k, count) + "(최대 " + MAX_UNITS + "개)." + AGAIN_KO));
            skip(k, n);
            return;
        } else if (start + count * k.unitBytes > 0x100000000L) {
            error(n, Msg.of("The " + k.directive + " segment goes past address 0xffffffff." + AGAIN_EN,
                    k.directive + " 구간이 주소 0xffffffff 너머까지 이어집니다." + AGAIN_KO));
            bad = 0;
        }
        kind = k;
        segStart = start == null ? 0 : start;
        declared = count;
        segLine = bad < 0 ? n : -n; // 음수: 머리가 틀려 이미지에 넣지 않는다
        units = new int[(int) Math.min(count, 4096)];
        filled = 0;
        zeros = new ArrayList<ExecutableImage.ZeroRun>();
        if (count == 0) {
            close();
        }
    }

    /** 머리가 틀린 구간: 뒤따르는 워드·바이트 줄을 키로 읽지 않게 다음 키까지 넘긴다. */
    private void skip(ExecutableImage.Kind k, int n) {
        kind = k;
        segStart = 0;
        declared = Long.MAX_VALUE;
        segLine = -n;
        units = new int[0];
        filled = 0;
        zeros = new ArrayList<ExecutableImage.ZeroRun>();
    }

    private void unitLine(int n, String[] t) {
        if (t[0].equals("zero")) {
            if (t.length != 2 || !t[1].matches("\\d{1,10}") || Long.parseLong(t[1]) == 0) {
                error(n, Msg.of("The zero line must be: zero <count> (a positive number)." + AGAIN_EN,
                        "zero 줄의 형식이 틀렸습니다. 형식: zero <개수>(1 이상)." + AGAIN_KO));
                return;
            }
            long c = Long.parseLong(t[1]);
            if (filled + c > declared) {
                more(n);
                return;
            }
            zeros.add(new ExecutableImage.ZeroRun(segStart + (long) filled * kind.unitBytes, c, n));
            for (long i = 0; i < c; i += 1) {
                put(0);
            }
            if (filled == declared) {
                close();
            }
            return;
        }
        if (kind == ExecutableImage.Kind.TEXT) {
            if (t.length != 1 || !t[0].matches("[0-9a-fA-F]{8}")) {
                error(n, Msg.of("A .text line must hold one word of 8 hexadecimal digits: " + join(t) + "." + AGAIN_EN,
                        ".text 줄에는 16진수 8자리 워드가 하나씩 있어야 합니다: " + join(t) + "." + AGAIN_KO));
                return;
            }
        } else {
            for (String b : t) {
                if (!b.matches("[0-9a-fA-F]{2}")) {
                    error(n, Msg.of("A .data byte must be 2 hexadecimal digits: " + b + "." + AGAIN_EN,
                            ".data 바이트는 16진수 2자리여야 합니다: " + b + "." + AGAIN_KO));
                    return;
                }
            }
        }
        if (filled + t.length > declared) {
            more(n);
            return;
        }
        for (String u : t) {
            put((int) Long.parseLong(u, 16));
        }
        if (filled == declared) {
            close();
        }
    }

    private void put(int unit) {
        if (declared == Long.MAX_VALUE) {
            filled += 1; // 머리가 틀린 구간: 줄만 넘긴다
            return;
        }
        if (filled == units.length) {
            units = java.util.Arrays.copyOf(units, Math.max(16, units.length * 2));
        }
        units[filled++] = unit;
    }

    private void more(int n) {
        error(n, Msg.of("The " + kind.directive + " segment on line " + Math.abs(segLine) + " declares "
                + ExecutableImage.count(declared, kind.unit) + ", but there are more." + AGAIN_EN,
                Math.abs(segLine) + "번째 줄의 " + kind.directive + " 구간은 " + unitsKo(kind, declared)
                        + "라고 적혀 있지만 그보다 많습니다." + AGAIN_KO));
        segLine = -Math.abs(segLine);
    }

    /** 구간이 개수만큼 차지 않았다(파일 잘림 또는 다음 키). */
    private void truncated() {
        if (declared != Long.MAX_VALUE) {
            error(Math.abs(segLine), Msg.of("The " + kind.directive + " line declares "
                    + ExecutableImage.count(declared, kind.unit) + ", but there " + (filled == 1 ? "is " : "are ")
                    + filled + ". The file may be cut off." + AGAIN_EN,
                    kind.directive + " 줄에는 " + unitsKo(kind, declared) + "라고 적혀 있지만 실제로는 " + filled
                            + "개입니다. 파일이 잘렸을 수 있습니다." + AGAIN_KO));
        }
        segLine = -Math.abs(segLine);
        close();
    }

    private void close() {
        ExecutableImage.Segment head = new ExecutableImage.Segment(kind, segStart, filled, Math.abs(segLine), zeros);
        if (segLine > 0 && filled == declared) {
            builder.segment(kind, segStart, units, filled, segLine, zeros);
            segmentHeads.add(head);
            justClosed = head;
        }
        kind = null;
    }

    private void overlaps() {
        for (int i = 0; i < segmentHeads.size(); i += 1) {
            for (int j = 0; j < i; j += 1) {
                ExecutableImage.Segment a = segmentHeads.get(j);
                ExecutableImage.Segment b = segmentHeads.get(i);
                if (a.overlaps(b)) {
                    error(b.line, Msg.of("This segment overlaps the segment on line " + a.line + ": " + b + " and "
                            + a + "." + AGAIN_EN,
                            "이 구간이 " + a.line + "번째 줄의 구간과 겹칩니다: " + b + ", " + a + "." + AGAIN_KO));
                }
            }
        }
    }

    // ---- 키 ----

    /** 값 하나짜리 키: 값이 있고 처음 나왔으면 true. */
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

    /** {@code 0x}로 시작하는 32비트 16진수. 아니면 오류를 남기고 null. */
    private Long address(int n, String what, String s) {
        if (s.matches("0[xX][0-9a-fA-F]{1,8}")) {
            return Long.parseLong(s.substring(2), 16);
        }
        error(n, Msg.of("The " + what + " value is not a 32-bit hexadecimal number starting with 0x: " + s + "."
                + AGAIN_EN,
                what + " 값을 읽을 수 없습니다: " + s + ". 값은 0x 뒤에 16진수 1~8자리를 적은 모양입니다." + AGAIN_KO));
        return null;
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
