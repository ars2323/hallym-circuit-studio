/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.palette;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

import kr.ac.hallym.hcs.app.model.Kinds;

/**
 * 부품 검색과 명령 팔레트의 모델(#76, PLAN.md 11.6). 질의를 부품 이름·한글 별칭·약어로 찾고, 뒤에 붙은 숫자는
 * 속성으로 읽는다(게이트는 입력 수, 그 밖은 폭: {@code and 3}, {@code mux 32}, {@code reg 32}). 순서는 이름이
 * 정확히 같은 것, 앞이 같은 것, 포함하는 것이고 즐겨찾기·최근·이 프로젝트 서브회로가 앞선다. 명령(리셋, 클럭
 * 한 번 등)도 같은 목록에 있다. GUI 없이 테스트한다.
 */
public final class Palette {
    public enum Kind { COMPONENT, SUBCIRCUIT, COMMAND }

    /** 목록 한 줄. */
    public static final class Item {
        public final Kind kind;
        public final String name;
        public final Library library;
        public final Circuit circuit;
        public final String command;
        public final Map<String, String> attrs;
        final int score;

        Item(Kind kind, String name, Library library, Circuit circuit, String command, Map<String, String> attrs,
                int score) {
            this.kind = kind;
            this.name = name;
            this.library = library;
            this.circuit = circuit;
            this.command = command;
            this.attrs = Collections.unmodifiableMap(attrs);
            this.score = score;
        }

        @Override
        public String toString() {
            return kind + " " + name + attrs;
        }
    }

    /** 부품 저장 이름 → 별칭(소문자). 한글 이름과 흔한 약어. */
    static final Map<String, List<String>> ALIASES = new HashMap<>();
    /** 명령 id → 별칭. */
    static final Map<String, List<String>> COMMANDS = new LinkedHashMap<>();

    private static void alias(String factory, String... names) {
        ALIASES.put(factory, Arrays.asList(names));
    }

    static {
        alias("AND Gate", "and", "앤드", "논리곱", "and게이트");
        alias("OR Gate", "or", "오어", "논리합", "or게이트");
        alias("NOT Gate", "not", "낫", "인버터", "inv", "부정");
        alias("NAND Gate", "nand", "낸드");
        alias("NOR Gate", "nor", "노어");
        alias("XOR Gate", "xor", "엑스오어", "배타적");
        alias("XNOR Gate", "xnor", "엑스노어");
        alias("Buffer", "buf", "버퍼");
        alias("Multiplexer", "mux", "먹스", "멀티플렉서", "선택기");
        alias("Demultiplexer", "demux", "디먹스", "디멀티플렉서");
        alias("Decoder", "dec", "디코더");
        alias("Priority Encoder", "enc", "인코더", "우선순위");
        alias("Register", "reg", "레지스터");
        alias("Counter", "ctr", "cnt", "카운터");
        alias("RAM", "ram", "램", "메모리");
        alias("ROM", "rom", "롬");
        alias("D Flip-Flop", "dff", "d플립플롭", "플립플롭");
        alias("Adder", "add", "adder", "가산기", "덧셈");
        alias("Subtractor", "sub", "감산기", "뺄셈");
        alias("Multiplier", "mul", "곱셈기", "곱셈");
        alias("Divider", "div", "나눗셈기", "나눗셈");
        alias("Comparator", "cmp", "비교기");
        alias("Shifter", "shift", "sll", "시프터");
        alias("Negator", "neg", "부호반전");
        alias("Splitter", "split", "스플리터", "분배기");
        alias("Tunnel", "tunnel", "터널");
        alias("Pin", "pin", "핀", "입력", "출력", "in", "out");
        alias("Probe", "probe", "프로브");
        alias("Constant", "const", "상수");
        alias("Clock", "clk", "clock", "클럭");
        alias("Bit Extender", "ext", "sext", "zext", "확장", "부호확장");
        alias("Pull Resistor", "pull", "풀");
        alias("Instruction Memory", "imem", "명령어메모리", "instruction");
        alias("Data Memory", "dmem", "데이터메모리");
        alias("Stack", "stack", "스택");
        alias("Console", "console", "콘솔", "syscall");
        alias("Radix Probe", "rprobe", "진법", "다중진법");
        COMMANDS.put("reset", Arrays.asList("reset", "리셋", "초기화"));
        COMMANDS.put("tick", Arrays.asList("tick", "클럭 한 번", "클럭", "사이클"));
        COMMANDS.put("step", Arrays.asList("step", "한 단계", "전파"));
        COMMANDS.put("run", Arrays.asList("run", "시뮬레이션 켜기", "시뮬레이션"));
        COMMANDS.put("loadS", Arrays.asList(".s", "s 불러오기", "프로그램", "load"));
        COMMANDS.put("fit", Arrays.asList("fit", "화면 맞춤", "맞춤"));
        COMMANDS.put("find", Arrays.asList("find", "찾기"));
        COMMANDS.put("keys", Arrays.asList("keys", "단축키", "?"));
    }

    private Palette() {
    }

    /** 질의를 이름 부분과 뒤 숫자로 나눈다. 숫자가 없으면 null. */
    static String[] split(String query) {
        String q = query.trim().replaceAll("\\s+", " ");
        int sp = q.lastIndexOf(' ');
        if (sp > 0) {
            String last = q.substring(sp + 1);
            if (last.matches("(0x[0-9a-fA-F]+|\\d+)")) {
                return new String[] {q.substring(0, sp), last};
            }
        }
        return new String[] {q, null};
    }

    /** 뒤 숫자를 속성으로: 게이트는 입력 수, 스플리터는 incoming, 폭 속성이 있으면 폭, 상수는 값도. */
    public static Map<String, String> attributesFor(ComponentFactory f, String arg) {
        Map<String, String> m = new LinkedHashMap<>();
        if (arg == null) {
            return m;
        }
        com.cburch.logisim.data.AttributeSet as = f.createAttributeSet();
        if (as.getAttribute("inputs") != null && Kinds.of(f).category() == Kinds.Category.GATE) {
            m.put("inputs", decimal(arg));
        } else if (f.getName().equals("Splitter")) {
            m.put("incoming", decimal(arg));
        } else if (f.getName().equals("Constant") && arg.startsWith("0x")) {
            m.put("value", arg);
        } else if (as.getAttribute("width") != null) {
            m.put("width", decimal(arg));
        } else if (as.getAttribute("dataWidth") != null) {
            m.put("dataWidth", decimal(arg));
        }
        return m;
    }

    private static String decimal(String arg) {
        return arg.startsWith("0x") ? Long.toString(Long.parseLong(arg.substring(2), 16)) : arg;
    }

    /** 이름 맞춤 점수: 정확 100, 앞이 같음 80, 포함 50, 없음 0. */
    static int match(String q, List<String> names) {
        int best = 0;
        for (String n : names) {
            String a = n.toLowerCase(Locale.ROOT);
            if (a.equals(q)) {
                best = Math.max(best, 100);
            } else if (a.startsWith(q)) {
                best = Math.max(best, 80);
            } else if (a.contains(q)) {
                best = Math.max(best, 50);
            }
        }
        return best;
    }

    /**
     * 찾기. libraries는 파일의 라이브러리(기본·JAR), subcircuits는 이 프로젝트의 회로, recent·favorites는 부품
     * 저장 이름 목록(앱 환경설정).
     */
    public static List<Item> search(String query, List<Library> libraries, List<Circuit> subcircuits,
            List<String> recent, List<String> favorites) {
        String[] parts = split(query);
        String q = parts[0].toLowerCase(Locale.ROOT).replace(" ", "");
        List<Item> ret = new ArrayList<>();
        if (q.isEmpty()) {
            return ret;
        }
        for (Library lib : libraries) {
            for (Tool t : lib.getTools()) {
                if (!(t instanceof AddTool)) {
                    continue;
                }
                ComponentFactory f = ((AddTool) t).getFactory();
                List<String> names = new ArrayList<>();
                names.add(f.getName().replace(" ", ""));
                names.add(f.getDisplayName().replace(" ", ""));
                names.addAll(ALIASES.getOrDefault(f.getName(), Collections.<String>emptyList()));
                int s = match(q, names);
                if (s == 0) {
                    continue;
                }
                s += boost(f.getName(), recent, favorites);
                ret.add(new Item(Kind.COMPONENT, f.getName(), lib, null, null, attributesFor(f, parts[1]), s));
            }
        }
        for (Circuit c : subcircuits) {
            int s = match(q, Collections.singletonList(c.getName().replace(" ", "")));
            if (s > 0) {
                ret.add(new Item(Kind.SUBCIRCUIT, c.getName(), null, c, null, Collections.<String, String>emptyMap(),
                        s + 15));
            }
        }
        for (Map.Entry<String, List<String>> e : COMMANDS.entrySet()) {
            List<String> names = new ArrayList<>();
            for (String n : e.getValue()) {
                names.add(n.replace(" ", ""));
            }
            int s = match(q, names);
            if (s > 0) {
                ret.add(new Item(Kind.COMMAND, e.getKey(), null, null, e.getKey(),
                        Collections.<String, String>emptyMap(), s - 5));
            }
        }
        ret.sort((a, b) -> b.score != a.score ? b.score - a.score : a.name.compareTo(b.name));
        return ret;
    }

    private static int boost(String factory, List<String> recent, List<String> favorites) {
        int b = 0;
        if (favorites != null && favorites.contains(factory)) {
            b += 30;
        }
        if (recent != null) {
            int i = recent.indexOf(factory);
            if (i >= 0) {
                b += Math.max(5, 20 - 2 * i);
            }
        }
        return b;
    }

    /** 최근 목록 갱신: 맨 앞으로, 최대 8개. */
    public static List<String> touch(List<String> recent, String factory) {
        List<String> ret = new ArrayList<>(recent);
        ret.remove(factory);
        ret.add(0, factory);
        while (ret.size() > 8) {
            ret.remove(ret.size() - 1);
        }
        return ret;
    }
}
