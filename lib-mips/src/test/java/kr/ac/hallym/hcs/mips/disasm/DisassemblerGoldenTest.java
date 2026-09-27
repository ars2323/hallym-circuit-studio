/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.disasm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Z-04, D-127: 디스어셈블러를 SPIM이 낸 목록(tests/disasm/*.txt, {@code hcs-asm -disasm}이 SPIM 자신의 출력 함수로
 * 만든 골든)과 한 줄씩 대조한다. 네이티브 도구 없이 돈다. 골든이 SPIM과 어긋나지 않았는지는 CI가
 * tools/gen-disasm-golden.sh로 다시 만들어 본다.
 *
 * <p>대조 규칙(D-127):
 * <ul>
 * <li>기본: 글자 하나까지 같다.
 * <li>{@code .word}로 둔 골든(keys, fields)에서 SPIM의 워드 해석이 어셈블 목록과 다른 명령({@link #decoderQuirk})은
 * 어셈블 목록을 따른다. 그 목록은 quirks.txt가 글자까지 확인한다.
 * <li>프로그램 골든에서 분기·점프가 아닌 명령 뒤의 {@code [식]}은 원래 줄의 식(la의 라벨 등)이라 워드만으로는 알 수 없다. 그
 * 앞까지 같아야 한다.
 * </ul>
 */
class DisassemblerGoldenTest {
    static final Path DIR = Paths.get(System.getProperty("hcs.testsDir", "../tests"), "disasm");
    static final Pattern LINE = Pattern.compile("([0-9a-f]{8}) ([0-9a-f]{8}) (.*)");
    static final Pattern LABEL = Pattern.compile("label ([0-9a-f]{8}) (\\S+)");

    static final class Golden {
        final String name;
        final Map<String, Long> labels = new LinkedHashMap<>();
        final List<int[]> words = new ArrayList<>(); // {addr, word}
        final List<String> texts = new ArrayList<>();

        Golden(Path p) throws IOException {
            name = p.getFileName().toString();
            for (String l : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                Matcher m = LABEL.matcher(l);
                if (m.matches()) {
                    labels.put(m.group(2), Long.parseLong(m.group(1), 16));
                    continue;
                }
                m = LINE.matcher(l);
                if (m.matches()) {
                    words.add(new int[] {(int) Long.parseLong(m.group(1), 16), (int) Long.parseLong(m.group(2), 16)});
                    texts.add(m.group(3));
                } else {
                    assertTrue(l.startsWith("#"), name + ": " + l);
                }
            }
        }

        /** .word로만 된 골든: SPIM의 워드 해석 그대로다. */
        boolean ofWords() {
            return name.equals("keys.txt") || name.equals("fields.txt");
        }
    }

    static List<Golden> goldens() throws IOException {
        List<Golden> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(DIR)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".txt")).sorted().collect(Collectors.toList())) {
                out.add(new Golden(p));
            }
        }
        return out;
    }

    /**
     * SPIM이 {@code .word}로 둔 워드를 어셈블한 줄과 다르게 보이는 명령(quirks.s로 어셈블해 따로 확인). MIPS32 필드로 적었다.
     */
    static boolean decoderQuirk(int w) {
        int op = w >>> 26;
        int rs = (w >>> 21) & 31;
        int rt = (w >>> 16) & 31;
        int fn = w & 63;
        if (op == 0 && fn == 1) {
            return (rt & 1) != 0; // movt: SPIM의 워드 해석은 늘 movf
        }
        if (op == 17 && rs == 8) {
            return (rt & 2) != 0; // bc1fl, bc1tl: likely 비트를 잃는다
        }
        if (op == 18 && rs == 8) {
            return (rt & 3) != 0; // bc2t, bc2fl, bc2tl: 늘 bc2f
        }
        if (op == 17 && (rs == 16 || rs == 17)) {
            return rs == 16 && fn == 13 // trunc.w.s: suxc1로 보인다
                    || fn >= 0x11 && fn <= 0x13 // movf/movt/movz/movn.fmt: 필드 자리가 다르다
                    || fn >= 0x30; // c.cond.fmt: 필드 자리가 다르고 조건 코드를 잃는다
        }
        return false;
    }

    /**
     * 분기·점프의 괄호가 목적지가 아니라 원래 줄을 따르는 줄(파일 주소 → 까닭). 워드만으로는 알 수 없어 괄호 앞까지만 같다.
     */
    static final Map<String, String> SOURCE_BRACKET_JUMPS = Map.of(
            "spim-tt.core.txt 004006fc", "j l17a: 목적지가 위 4비트 다른 커널 라벨이라 SPIM도 오류로 알린다",
            "spim-tt.core.txt 00403b28", "bgtu $0 0 fail을 편 beq: 원래 줄에 없는 수 변위인데 목적지에 라벨 l135가 있다");

    static boolean controlTransfer(int w) {
        String m = Disassembler.mnemonic(w);
        return m != null && (m.equals("j") || m.equals("jal") || m.startsWith("b") && !m.equals("break"));
    }

    @Test
    void everyGoldenLineMatchesSpim() throws IOException {
        List<Golden> gs = goldens();
        assertTrue(gs.size() >= 18, "goldens in " + DIR.toAbsolutePath() + ": " + gs.size());
        int exact = 0;
        int quirk = 0;
        int sourceExpr = 0;
        int sourceJump = 0;
        List<String> failures = new ArrayList<>();
        for (Golden g : gs) {
            Map<Integer, String> symbols = Disassembler.byAddress(g.labels);
            for (int i = 0; i < g.words.size(); i++) {
                int addr = g.words.get(i)[0];
                int word = g.words.get(i)[1];
                String spim = g.texts.get(i);
                String ours = Disassembler.text(word, addr, symbols);
                if (ours.equals(spim)) {
                    exact++;
                } else if (g.ofWords() && decoderQuirk(word)) {
                    quirk++;
                } else if (!g.ofWords() && !controlTransfer(word) && spim.startsWith(ours + " [")
                        && spim.endsWith("]")) {
                    sourceExpr++;
                } else if (SOURCE_BRACKET_JUMPS.containsKey(g.name + String.format(" %08x", addr))
                        && withoutBracket(spim).equals(withoutBracket(ours))) {
                    sourceJump++;
                } else if (failures.size() < 40) {
                    failures.add(String.format("%s %08x %08x: spim \"%s\", ours \"%s\"", g.name, addr, word, spim, ours));
                }
            }
        }
        System.out.printf("disasm goldens: %d files, %d lines compared: %d identical, %d .word lines where SPIM's"
                + " word decoder differs from its assembler listing (checked in quirks.txt), %d up to a source"
                + " expression bracket, %d jumps whose bracket follows the source%n", gs.size(),
                exact + quirk + sourceExpr + sourceJump, exact, quirk, sourceExpr, sourceJump);
        assertEquals(List.of(), failures);
        assertEquals(SOURCE_BRACKET_JUMPS.size(), sourceJump);
        assertTrue(exact > 11000, "identical " + exact);
    }

    static String withoutBracket(String s) {
        int b = s.indexOf(" [");
        return b < 0 ? s : s.substring(0, b);
    }

    /** quirks.txt(어셈블한 줄)는 예외 없이 같고, 대조 규칙의 예외 목록을 모두 담는다. */
    @Test
    void quirksAreCheckedAgainstTheAssembledListing() throws IOException {
        Golden g = new Golden(DIR.resolve("quirks.txt"));
        Map<Integer, String> symbols = Disassembler.byAddress(g.labels);
        int quirks = 0;
        for (int i = 0; i < g.words.size(); i++) {
            int[] aw = g.words.get(i);
            assertEquals(g.texts.get(i), Disassembler.text(aw[1], aw[0], symbols), String.format("%08x", aw[1]));
            if (decoderQuirk(aw[1])) {
                quirks++;
            }
        }
        assertTrue(quirks > 150, "quirk words " + quirks);
        // .word 골든에서 어셈블 목록을 따른 워드는 모두 이 규칙에 든다(위 시험). 이름마다 quirks.txt에 있다.
        for (String name : new String[] {"movt", "bc1fl", "bc1tl", "bc2t", "bc2fl", "bc2tl", "trunc.w.s", "movf.s",
            "movt.d", "movz.s", "movn.d", "c.eq.s", "c.ngt.d"}) {
            assertTrue(g.texts.stream().anyMatch(t -> t.startsWith(name + " ") || t.matches(name + "\\d .*")),
                    name + " in quirks.txt");
        }
    }

    /**
     * 골든을 뽑는 방식(hcs-asm -disasm)이 QtSpim 창의 글과 같다: tests/asm/qtspim/의 QtSpim Save Log File 출력과 한 줄씩
     * 대조한다.
     */
    @Test
    void goldenTextIsWhatQtSpimShows() throws IOException {
        int compared = 0;
        String[][] pairs = {{"helloworld.text.txt", "spim-helloworld.txt"}, {"tt.core.text.txt", "spim-tt.core.txt"}};
        for (String[] p : pairs) {
            Map<Integer, String> qt = new TreeMap<>();
            Pattern row = Pattern.compile("\\[([0-9a-f]{8})\\] ([0-9a-f]{8})  (.*)");
            for (String l : Files.readAllLines(DIR.resolveSibling("asm").resolve("qtspim").resolve(p[0]),
                    StandardCharsets.UTF_8)) {
                if (l.startsWith("Kernel Text Segment")) {
                    break;
                }
                Matcher m = row.matcher(l);
                if (m.matches()) {
                    String t = m.group(3);
                    int semi = t.indexOf(';');
                    qt.put((int) Long.parseLong(m.group(1), 16), (semi < 0 ? t : t.substring(0, semi)).trim());
                }
            }
            Golden g = new Golden(DIR.resolve(p[1]));
            assertEquals(qt.size(), g.words.size(), p[1]);
            for (int i = 0; i < g.words.size(); i++) {
                assertEquals(qt.get(g.words.get(i)[0]), g.texts.get(i), p[1] + String.format(" %08x", g.words.get(i)[0]));
                compared++;
            }
        }
        System.out.printf("disasm goldens: %d lines equal to QtSpim's own window text%n", compared);
        assertTrue(compared > 4700, "compared " + compared);
    }
}
