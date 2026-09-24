/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.splitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 스플리터 편집기의 모델(#105, PLAN.md 11.11, D-023). 묶인 쪽 폭과 팔 목록(위에서 아래 순서, 팔마다 비트 집합과
 * 이름)을 가진다. 저장은 늘 원조 2.7.1 표준 속성(fanout, incoming, bitN)으로 하고, 팔 이름만 따로 둔다. GUI 없이
 * 테스트한다.
 */
public final class SplitterSpec {
    /** 팔 하나: 비트 번호들(큰 것부터)과 이름. */
    public static final class Arm {
        final List<Integer> bits;
        String name;

        Arm(List<Integer> bits, String name) {
            List<Integer> sorted = new ArrayList<>(new TreeSet<>(bits));
            Collections.reverse(sorted);
            this.bits = Collections.unmodifiableList(sorted);
            this.name = name == null ? "" : name.trim();
        }

        public List<Integer> bits() {
            return bits;
        }

        public String name() {
            return name;
        }

        public int width() {
            return bits.size();
        }

        public int msb() {
            return bits.isEmpty() ? -1 : bits.get(0);
        }

        /** 범위 표기: {@code [31:26]}, 한 비트면 {@code [31]}, 떨어져 있으면 {@code [7,3:0]}. */
        public String range() {
            return "[" + ranges(bits) + "]";
        }

        /** 팔 라벨: {@code [31:26] op}. */
        public String label() {
            return name.isEmpty() ? range() : range() + " " + name;
        }
    }

    public static final class ParseException extends Exception {
        private static final long serialVersionUID = 1L;

        ParseException(String message) {
            super(message);
        }
    }

    /** 미리 정한 나누기. 이름과 (MSB 쪽부터) 범위. */
    public enum Preset {
        MIPS_R("31:26 op, 25:21 rs, 20:16 rt, 15:11 rd, 10:6 shamt, 5:0 funct"),
        MIPS_I("31:26 op, 25:21 rs, 20:16 rt, 15:0 imm"),
        MIPS_J("31:26 op, 25:0 addr"),
        BYTES("31:24 b3, 23:16 b2, 15:8 b1, 7:0 b0"),
        HALVES("31:16 hi, 15:0 lo"),
        SIGN("31 sign, 30:0 rest");

        final String text;

        Preset(String text) {
            this.text = text;
        }

        public SplitterSpec spec(boolean msbOnTop) {
            try {
                return parse(text, 32, msbOnTop);
            } catch (ParseException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private final int width;
    private final List<Arm> arms;

    public SplitterSpec(int width, List<Arm> arms) {
        this.width = width;
        this.arms = Collections.unmodifiableList(new ArrayList<>(arms));
    }

    public int width() {
        return width;
    }

    /** 팔 목록, 위(0번 팔)부터. */
    public List<Arm> arms() {
        return arms;
    }

    public static Arm arm(String name, List<Integer> bits) {
        return new Arm(bits, name);
    }

    private static final Pattern REPEAT = Pattern.compile("\\s*(\\d+)\\s*[xX×]\\s*(\\d+)\\s*");
    private static final Pattern PART = Pattern.compile("\\s*(\\d+)\\s*(?::\\s*(\\d+))?\\s*([A-Za-z_][\\w.]*)?\\s*");

    /**
     * 범위 입력을 읽는다. {@code 31:26, 25:21, 20:16, 15:0}(범위 뒤에 이름을 붙여도 된다: {@code 31:26 op}),
     * {@code 4x8}(8비트 팔 4개), {@code 32x1}. width가 0 이하면 가장 큰 비트 + 1(반복 표기는 곱). 팔 순서는
     * msbOnTop이면 위 팔이 큰 비트, 아니면 위 팔이 작은 비트다.
     */
    public static SplitterSpec parse(String text, int width, boolean msbOnTop) throws ParseException {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            throw new ParseException("empty");
        }
        List<Arm> arms = new ArrayList<>();
        Matcher rep = REPEAT.matcher(t);
        if (rep.matches()) {
            int n = Integer.parseInt(rep.group(1));
            int each = Integer.parseInt(rep.group(2));
            if (n <= 0 || each <= 0 || n * each > 32) {
                throw new ParseException("bad repeat: " + t);
            }
            for (int i = 0; i < n; i++) {
                List<Integer> bits = new ArrayList<>();
                for (int b = i * each; b < (i + 1) * each; b++) {
                    bits.add(b);
                }
                arms.add(new Arm(bits, ""));
            }
            if (width <= 0) {
                width = n * each;
            }
        } else {
            for (String part : t.split(",")) {
                Matcher m = PART.matcher(part);
                if (!m.matches()) {
                    throw new ParseException("cannot read: " + part.trim());
                }
                int a = Integer.parseInt(m.group(1));
                int b = m.group(2) == null ? a : Integer.parseInt(m.group(2));
                List<Integer> bits = new ArrayList<>();
                for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
                    bits.add(i);
                }
                arms.add(new Arm(bits, m.group(3)));
            }
            if (width <= 0) {
                int max = 0;
                for (Arm arm : arms) {
                    max = Math.max(max, arm.msb());
                }
                width = max + 1;
            }
        }
        SplitterSpec spec = new SplitterSpec(width, arms).ordered(msbOnTop);
        String err = spec.overlapOrOutOfRange();
        if (err != null) {
            throw new ParseException(err);
        }
        return spec;
    }

    /** 팔 순서를 방향에 맞춘다(위 팔이 큰 비트 / 작은 비트). */
    public SplitterSpec ordered(boolean msbOnTop) {
        List<Arm> sorted = new ArrayList<>(arms);
        sorted.sort((p, q) -> msbOnTop ? q.msb() - p.msb() : p.msb() - q.msb());
        return new SplitterSpec(width, sorted);
    }

    /** 범위를 넘거나 두 팔에 같은 비트가 있으면 설명, 아니면 null. */
    public String overlapOrOutOfRange() {
        boolean[] used = new boolean[Math.max(width, 0)];
        for (Arm arm : arms) {
            for (int b : arm.bits) {
                if (b < 0 || b >= width) {
                    return "bit " + b + " is outside 0.." + (width - 1);
                }
                if (used[b]) {
                    return "bit " + b + " is in two arms";
                }
                used[b] = true;
            }
        }
        if (arms.isEmpty()) {
            return "no arms";
        }
        if (width < 1 || width > 32) {
            return "width must be 1..32";
        }
        return null;
    }

    /** 어느 팔에도 가지 않는 비트(큰 것부터). */
    public List<Integer> unassigned() {
        boolean[] used = new boolean[width];
        for (Arm arm : arms) {
            for (int b : arm.bits) {
                used[b] = true;
            }
        }
        List<Integer> ret = new ArrayList<>();
        for (int b = width - 1; b >= 0; b--) {
            if (!used[b]) {
                ret.add(b);
            }
        }
        return ret;
    }

    /** 비트마다 팔 번호(없으면 -1). */
    public int[] armOfBit() {
        int[] ret = new int[width];
        java.util.Arrays.fill(ret, -1);
        for (int i = 0; i < arms.size(); i++) {
            for (int b : arms.get(i).bits) {
                ret[b] = i;
            }
        }
        return ret;
    }

    /**
     * 원조 2.7.1 표준 속성(.circ에 적히는 저장 문자열). 순서가 중요하다: fanout과 incoming을 바꾸면 원조가 bitN을
     * 기본 배정으로 되돌리므로 bitN은 그 뒤에 둔다.
     */
    public Map<String, String> toStandardAttrs() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("fanout", Integer.toString(arms.size()));
        m.put("incoming", Integer.toString(width));
        int[] arm = armOfBit();
        for (int b = 0; b < width; b++) {
            m.put("bit" + b, arm[b] < 0 ? "none" : Integer.toString(arm[b]));
        }
        return m;
    }

    /** 표준 속성(저장 문자열)에서 읽는다. 팔 이름은 names(위 팔부터, 모자라면 빈 이름). */
    public static SplitterSpec fromStandardAttrs(Map<String, String> attrs, List<String> names) {
        int fanout = Integer.parseInt(attrs.get("fanout"));
        int width = Integer.parseInt(attrs.get("incoming"));
        List<List<Integer>> bits = new ArrayList<>();
        for (int i = 0; i < fanout; i++) {
            bits.add(new ArrayList<>());
        }
        for (int b = 0; b < width; b++) {
            String v = attrs.get("bit" + b);
            if (v != null && !v.equals("none")) {
                int a = Integer.parseInt(v);
                if (a >= 0 && a < fanout) {
                    bits.get(a).add(b);
                }
            }
        }
        List<Arm> arms = new ArrayList<>();
        for (int i = 0; i < fanout; i++) {
            arms.add(new Arm(bits.get(i), names != null && i < names.size() ? names.get(i) : ""));
        }
        return new SplitterSpec(width, arms);
    }

    /** 팔 이름(위 팔부터). */
    public List<String> names() {
        List<String> ret = new ArrayList<>();
        for (Arm a : arms) {
            ret.add(a.name);
        }
        return ret;
    }

    public boolean hasNames() {
        for (Arm a : arms) {
            if (!a.name.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public SplitterSpec withNames(List<String> names) {
        List<Arm> ret = new ArrayList<>();
        for (int i = 0; i < arms.size(); i++) {
            ret.add(new Arm(arms.get(i).bits, i < names.size() ? names.get(i) : ""));
        }
        return new SplitterSpec(width, ret);
    }

    /**
     * 여러 선을 고른 순서대로 한 버스로 묶는 스플리터(PLAN.md 11.11). 먼저 고른 선이 위 팔이고 가장 큰 비트다
     * ({@code {PC[31:28], addr, 00}}). 도구는 비트 배치를 정하지 않고 고른 순서와 폭만 쓴다.
     */
    public static SplitterSpec combine(List<Integer> widths, List<String> names) {
        int total = 0;
        for (int w : widths) {
            total += w;
        }
        List<Arm> arms = new ArrayList<>();
        int hi = total - 1;
        for (int i = 0; i < widths.size(); i++) {
            List<Integer> bits = new ArrayList<>();
            for (int b = hi; b > hi - widths.get(i); b--) {
                bits.add(b);
            }
            hi -= widths.get(i);
            arms.add(new Arm(bits, names != null && i < names.size() ? names.get(i) : ""));
        }
        return new SplitterSpec(total, arms);
    }

    /** 선에서 비트 하나를 뽑는 스플리터: 팔 하나에 bit만, 나머지 비트는 어느 팔에도 가지 않는다. */
    public static SplitterSpec extract(int width, int bit) {
        return new SplitterSpec(width, Collections.singletonList(
                new Arm(Collections.singletonList(bit), "")));
    }

    /** 비트 목록(큰 것부터)을 범위 표기로: 31,30,29 → 31:29, 7,3,2,1,0 → 7,3:0. */
    static String ranges(List<Integer> bits) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < bits.size()) {
            int start = bits.get(i);
            int end = start;
            while (i + 1 < bits.size() && bits.get(i + 1) == end - 1) {
                end = bits.get(++i);
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(start == end ? Integer.toString(start) : start + ":" + end);
            i++;
        }
        return sb.toString();
    }

    /** 입력칸에 다시 보여 줄 표기: {@code 31:26 op, 25:21 rs}. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        for (Arm a : arms) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(ranges(a.bits));
            if (!a.name.isEmpty()) {
                sb.append(' ').append(a.name);
            }
        }
        return sb.toString();
    }
}
