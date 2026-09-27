/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;

import kr.ac.hallym.hcs.mips.disasm.Disassembler;
import kr.ac.hallym.hcs.mips.image.ExecutableImage;
import kr.ac.hallym.hcs.mips.image.HmxParser;
import kr.ac.hallym.hcs.mips.image.SourceCheck;
import kr.ac.hallym.hcs.mips.image.StartFacts;

/**
 * Hallym MIPS v2.4.0이 낸 실행 이미지 골든(tests/hmx/hallym-mips-v2.4.0, 명세 hmx-format.md의 "Test files" 일곱 쌍)을
 * 읽고, 불러오고, 돌린다(Z-06, D-138).
 *
 * <ol>
 * <li>파서가 일곱 파일을 모두 읽고, 명세 표의 설명(entry, 구간, zero 줄, 처리기 없음)과 맞는다.
 * <li>불러오기(ProgramLoader)가 Instruction Memory·Data Memory에 넣은 것을 부품 출력으로 되읽으면(헤드리스, 주소를 세는
 * 회로) 워드마다·바이트마다 이미지와 같고, 이미지가 주지 않은 주소는 0이다.
 * <li>참조 CPU(ref-mips, PC 시작 = entry)가 각 이미지를 exit까지 돌린 레지스터와 Console 글이 SPIM 오라클 파일
 * ({@code <이름>.regs}, vendor SPIM 9.1.24로 만든 것)과 같다. 비교하는 레지스터는 프로그램 자신의 워드가 쓰는 것이다(시작
 * 코드와 SPIM의 실행 스택이 정한 값은 회로가 돌리지 않는다). ref-mips에 없는 명령이 쓰는 레지스터는 비교하지 않고 빌드
 * 로그에 "NOT compared"로 남긴다(건너뛴 검사는 통과가 아니다).
 * </ol>
 */
class HallymMipsGoldenTest {
    static final Path DIR = AssemblerIntegrationTest.TESTS.resolve("hmx/hallym-mips-v2.4.0");
    static final List<String> CASES = List.of("branches", "data", "main-later", "pseudo", "no-data", "space-gap",
            "no-handler");
    /** 참조 CPU(RefMips)가 해석하는 명령. */
    static final List<String> REF_MIPS = List.of("add", "addu", "sub", "subu", "and", "or", "xor", "nor", "slt",
            "sltu", "sll", "srl", "sra", "sllv", "srlv", "srav", "jr", "syscall", "mul", "addi", "addiu", "slti", "sltiu",
            "andi", "ori", "xori", "lui", "lw", "sw", "beq", "bne", "bgez", "bltz", "j", "jal", "nop");
    static final int MAX_CYCLES = 5000;

    @TempDir
    Path tmp;

    static ExecutableImage image(String name) throws Exception {
        HmxParser.Result r = HmxParser.read(DIR.resolve(name + ".hmx").toFile());
        assertEquals(List.of(), r.errors, name);
        return r.image;
    }

    // ---- (a) 읽기 ----

    @TestFactory
    Stream<DynamicTest> everyImageIsRead() {
        return CASES.stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            HmxParser.Result r = HmxParser.read(DIR.resolve(name + ".hmx").toFile());
            assertEquals(List.of(), r.errors);
            assertEquals(Map.of(), r.ignoredFields, "Hallym MIPS 2.4.0 writes only version 1 fields");
            ExecutableImage img = r.image;
            assertEquals(name + ".s", img.source());
            assertEquals("Hallym MIPS 2.4.0", img.producedBy());
            assertEquals(ExecutableImage.Endian.LITTLE, img.endian());
            assertEquals(0x7fffffe4L, (long) img.reg("$sp"));
            assertEquals(0x10008000L, (long) img.reg("$gp"));
            assertEquals(List.of("$sp", "$gp"), new ArrayList<>(img.regs().keySet()));
            assertEquals(0x00400000L, img.segments(ExecutableImage.Kind.TEXT).get(0).start);
            SourceCheck check = SourceCheck.check(DIR.resolve(name + ".hmx").toFile(), img);
            assertEquals(SourceCheck.Status.SAME, check.status, "the .s beside it is the assembled source");
            // 처리기를 불러온 이미지는 시작 코드 9워드로 시작하고 entry가 main이다(명세 "An example")
            boolean handler = !name.equals("no-handler");
            assertEquals(!handler, StartFacts.withoutExceptionHandler(img));
            if (handler) {
                assertEquals(img.symbols().get("main"), img.entry());
                assertEquals(0x8fa40000, (int) img.textWords().get(0x00400000L));
                assertEquals(0x0c000000 | (int) (img.entry() >>> 2 & 0x03ffffff),
                        (int) img.textWords().get(0x00400014L), "jal main");
                assertNull(StartFacts.noHandlerFact(img));
            }
        }));
    }

    /** 명세 "Test files" 표의 설명. */
    @Test
    void imagesShowWhatTheSpecSays() throws Exception {
        // branches: beq, j, jal, jr
        List<String> ops = ProgramLoader.usedInstructions(image("branches"));
        assertTrue(ops.containsAll(List.of("beq", "j", "jal", "jr")), ops.toString());
        // data: la → lui+ori, lw 라벨 → lui+lw, .data 28바이트(msg 7바이트 + 채움 1바이트, 워드들)
        ExecutableImage data = image("data");
        assertEquals(List.of(0x3c011001, 0x34300008, 0x3c011001, 0x8c310018),
                new ArrayList<>(data.textWords().subMap(0x00400024L, 0x00400034L).values()));
        assertEquals(28, data.dataBytes().size());
        assertEquals(3, (int) data.dataWords().get(0x10010008L));
        assertEquals(-1, (int) data.dataWords().get(0x10010014L));
        assertEquals(4, (int) data.dataWords().get(0x10010018L));
        assertEquals(0x206d7573, (int) data.dataWords().get(0x10010000L), "\"sum \", little endian");
        // main-later: entry는 시작 코드 다음 첫 워드가 아니다
        ExecutableImage later = image("main-later");
        assertEquals(0x0040002cL, (long) later.entry());
        assertEquals(0x00400024L, (long) later.symbols().get("square"));
        // pseudo: 의사 명령어가 펼쳐진 워드
        ExecutableImage pseudo = image("pseudo");
        assertEquals(34, pseudo.textWords().size());
        assertTrue(ProgramLoader.usedInstructions(pseudo).containsAll(List.of("mul", "div", "mfhi", "slt", "nor")));
        // no-data: .data 구간 없음
        assertTrue(image("no-data").segments(ExecutableImage.Kind.DATA).isEmpty());
        // space-gap: zero 줄, 마지막 .space까지
        ExecutableImage gap = image("space-gap");
        ExecutableImage.Segment d = gap.segments(ExecutableImage.Kind.DATA).get(0);
        assertEquals(4168, d.count);
        assertEquals(2, d.zeroRuns().size());
        assertEquals(0x10010004L, d.zeroRuns().get(0).start);
        assertEquals(4096, d.zeroRuns().get(0).count);
        assertEquals(0x10011008L, d.zeroRuns().get(1).start);
        assertEquals(64, d.zeroRuns().get(1).count);
        assertEquals(0x22222222, (int) gap.dataWords().get(0x10011004L));
        assertEquals(0, (int) gap.dataWords().get(0x10011044L), "the tail .space is in the image, as zeros");
        assertEquals(1042, gap.dataWords().size());
        // no-handler: 시작 코드 없음, entry = __start
        ExecutableImage nh = image("no-handler");
        assertEquals(0x00400000L, (long) nh.entry());
        assertEquals(List.of("__start"), nh.symbolsAt(0x00400000L));
        assertEquals(3, nh.textWords().size());
        assertEquals("Assembled without the exception handler: no start-up code, entry = the program's own __start.",
                StartFacts.noHandlerFact(nh).en);
        assertEquals("entry 0x00400000 (__start)", StartFacts.entryLine(nh).en);
        // 골든에는 진입 루틴의 jr $ra가 없다(branches·main-later의 jr $ra는 jal로 부르는 함수 안)
        for (String name : CASES) {
            assertNull(StartFacts.jrRaFact(image(name)), name);
        }
        ExecutableImage branches = image("branches");
        assertEquals(StartFacts.JR_RA, (int) branches.textWords().get(0x00400058L), "double: jr $ra");
        assertEquals(List.of(0x00400024L, 0x00400054L), Arrays.asList(StartFacts.entryRoutine(branches)[0],
                StartFacts.entryRoutine(branches)[1]), "main ends where jal double goes");
    }

    // ---- (b) 불러오기와 되읽기 ----

    /**
     * 주소를 세는 회로: 사이클마다 4씩 늘어 Instruction Memory·Data Memory를 차례로 읽는다. Data Memory는 데이터와 스택
     * 영역을 함께 맡는 새 부품 하나다(D-140).
     */
    static final class Reader {
        final InProcessSim sim;
        final Component imem;
        final Component dmem;

        Reader(long textStart, long dataStart) throws Exception {
            sim = new InProcessSim();
            imem = sim.b.add(sim.mips, "Instruction Memory", 1200, 200);
            dmem = sim.b.add(sim.mips, "Data Memory", 1200, 600);
            Component clock = sim.b.add("Wiring", "Clock", 100, 100);
            sim.b.tunnel(clock, 0, "clk");
            sim.b.constant("one", 1, 1, 100, 200);
            sim.b.constant("zero1", 1, 0, 100, 260);
            sim.b.constant("zero32", 32, 0, 100, 320);
            sim.b.constant("four", 32, 4, 100, 380);
            sim.b.constant("tbase", 32, (int) textStart, 100, 440);
            sim.b.constant("dbase", 32, (int) dataStart, 100, 500);
            Component ctr = sim.b.add("Memory", "Register", 400, 100, "width", "32");
            sim.b.tunnelOutward(ctr, 0, "ctr");
            sim.b.tunnelOutward(ctr, 1, "next");
            sim.b.tunnelOutward(ctr, 2, "clk");
            sim.b.tunnelOutward(ctr, 3, "zero1");
            sim.b.tunnelOutward(ctr, 4, "one");
            adder(400, 400, "ctr", "four", "next");
            adder(400, 700, "ctr", "tbase", "taddr");
            adder(400, 1000, "ctr", "dbase", "daddr");
            sim.b.tunnelOutward(imem, InstructionMemory.ADDR, "taddr");
            sim.b.tunnelOutward(dmem, DataMemory.ADDR, "daddr");
            sim.b.tunnelOutward(dmem, DataMemory.WRITE_DATA, "zero32");
            sim.b.tunnelOutward(dmem, DataMemory.MEM_WRITE, "zero1");
            sim.b.tunnelOutward(dmem, DataMemory.MEM_READ, "one");
            sim.b.tunnelOutward(dmem, DataMemory.CLK, "clk");
            sim.b.commit();
        }

        private void adder(int x, int y, String a, String b, String out) {
            Component c = sim.b.add("Arithmetic", "Adder", x, y, "width", "32");
            sim.b.tunnelOutward(c, 0, a);
            sim.b.tunnelOutward(c, 1, b);
            sim.b.tunnelOutward(c, 2, out);
        }
    }

    static String hex(Value v) {
        return v.isFullyDefined() ? String.format("%08x", v.toIntValue()) : v.toString();
    }

    @TestFactory
    Stream<DynamicTest> loaderPutsEveryWordAndByte() {
        return CASES.stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            ProgramLoader.Loaded l = ProgramLoader.read(DIR.resolve(name + ".hmx").toFile());
            assertEquals(List.of(), l.errors);
            ExecutableImage img = l.image;
            long textStart = 0x00400000L;
            long dataStart = 0x10010000L;
            Reader r = new Reader(textStart, dataStart);
            ProgramLoader.Plan plan = ProgramLoader.plan(l, r.sim.file.getCircuits(), null, null, name + ".hmx");
            assertEquals(List.of(), plan.errors);
            assertEquals(List.of(), plan.warnings, "source check: same");
            AssemblerIntegrationTest.apply(plan);
            r.sim.start();
            // 이미지 끝 다음 두 워드까지: 이미지가 주지 않은 메모리는 0이다(명세 "What a reader must do" 5)
            int textWords = img.textWords().size();
            int dataWords = img.dataWords().size();
            int n = Math.max(textWords, dataWords) + 2;
            int bytes = 0;
            for (int i = 0; i < n; i += 1) {
                long ta = textStart + 4L * i;
                String got = hex(r.sim.port(r.imem, InstructionMemory.INSTR));
                Integer want = img.textWords().get(ta);
                assertEquals(String.format("%08x", want == null ? 0 : want), got, String.format("text %08x", ta));
                if (i >= textWords) {
                    assertNull(want);
                }
                long da = dataStart + 4L * i;
                String dword = hex(r.sim.port(r.dmem, DataMemory.READ_DATA));
                Integer dwant = img.dataWords().get(da);
                assertEquals(String.format("%08x", dwant == null ? 0 : dwant), dword, String.format("data %08x", da));
                DataMemory.State st = (DataMemory.State) r.sim.data(r.dmem);
                for (long a = da; a < da + 4; a += 1) {
                    Integer b = img.dataBytes().get(a);
                    if (b != null) {
                        assertEquals((int) b, st.readByte((int) a), String.format("byte %08x", a));
                        bytes += 1;
                    } else {
                        assertEquals(0, st.readByte((int) a), String.format("byte %08x", a));
                    }
                }
                r.sim.cycle();
            }
            assertEquals(img.dataBytes().size(), bytes, "every byte read back");
            // reg $sp: Data Memory 스택 영역의 깊이 기준(파일 값, D-140), 레지스터에는 넣지 않는다. 스택 내용은 파일에 없어 0
            assertEquals(img.reg("$sp"), r.dmem.getAttributeSet().getValue(MemoryFactory.CONTENTS).initialSp());
            DataMemory.State dst = (DataMemory.State) r.sim.data(r.dmem);
            assertNull(dst.problem, "one Data Memory, no overlapping part");
            if (img.reg("$sp") != null) {
                for (long a = img.reg("$sp"); a < 0x80000000L; a += 4) {
                    assertEquals(0, dst.readWord((int) a), String.format("stack %08x", a));
                }
            }
            assertEquals(name + ".hmx", r.imem.getAttributeSet().getValue(MemoryFactory.SOURCE));
        }));
    }

    // ---- (c) 끝 레지스터: SPIM 오라클 파일 ----

    /** {@code <이름>.regs}: 레지스터 이름 → 값. 주석(#)과 console 줄은 따로. */
    static final class Oracle {
        final Map<String, Integer> regs = new LinkedHashMap<>();
        String console;
        final List<String> header = new ArrayList<>();

        static Oracle read(Path p) throws Exception {
            Oracle o = new Oracle();
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                if (line.startsWith("#")) {
                    o.header.add(line);
                } else if (line.startsWith("console ")) {
                    o.console = unescape(line.substring("console ".length()));
                } else if (!line.isBlank()) {
                    String[] t = line.split("\\s+");
                    assertEquals(2, t.length, line);
                    o.regs.put(t[0], (int) Long.parseLong(t[1], 16));
                }
            }
            return o;
        }
    }

    static final String[] NAMES = {"$zero", "$at", "$v0", "$v1", "$a0", "$a1", "$a2", "$a3", "$t0", "$t1", "$t2",
        "$t3", "$t4", "$t5", "$t6", "$t7", "$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7", "$t8", "$t9",
        "$k0", "$k1", "$gp", "$sp", "$fp", "$ra"};

    /** 따옴표 안의 글: \n, \\, \" 만 바꾼다. */
    static String escape(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + "\"";
    }

    static String unescape(String q) {
        assertTrue(q.startsWith("\"") && q.endsWith("\""), q);
        StringBuilder sb = new StringBuilder();
        String s = q.substring(1, q.length() - 1);
        for (int i = 0; i < s.length(); i += 1) {
            char c = s.charAt(i);
            if (c == '\\') {
                char e = s.charAt(++i);
                sb.append(e == 'n' ? '\n' : e);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 원본 SPIM(vendor/spim-9.1.24를 빌드한 오라클)으로 끝까지 돌린 레지스터를 오라클 파일 글로. 처리기를 불러온 이미지는
     * {@code -exception}, 아니면 {@code -noexception}. 빈 환경에서 {@code load}·{@code run}(프로그램 인자 없음).
     */
    static String spimOracle(String name, boolean handler) throws Exception {
        Path cmd = Files.createTempFile("hcs-regs", ".txt");
        try {
            Files.writeString(cmd, "load \"" + name + ".s\"\nrun\nprint_all_regs hex\n");
            ProcessBuilder pb = new ProcessBuilder(AssemblerIntegrationTest.ORACLE.getPath(),
                    handler ? "-exception" : "-noexception").directory(DIR.toFile()).redirectInput(cmd.toFile())
                    .redirectErrorStream(true);
            pb.environment().clear();
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(p.waitFor(60, TimeUnit.SECONDS));
            String[] parts = out.split("\\(spim\\) ", -1);
            String console = parts[2];
            Map<Integer, String> regs = new java.util.TreeMap<>();
            Matcher m = Pattern.compile("R(\\d+)\\s+\\(\\w+\\) = ([0-9a-f]{8})").matcher(out);
            while (m.find()) {
                regs.put(Integer.parseInt(m.group(1)), m.group(2));
            }
            assertEquals(32, regs.size(), out);
            Matcher hilo = Pattern.compile("HI\\s+= ([0-9a-f]{8})\\s+LO\\s+= ([0-9a-f]{8})").matcher(out);
            assertTrue(hilo.find(), out);
            StringBuilder sb = new StringBuilder();
            sb.append("# Hallym MIPS v2.4.0 golden ").append(name).append(".s: registers and console output at exit.\n");
            sb.append("# Oracle: SPIM 9.1.24 (vendor/spim-9.1.24; its CPU/ is byte-identical to Hallym MIPS v2.4.0's CPU/).\n");
            sb.append("# Made by: spim ").append(handler ? "-exception" : "-noexception")
                    .append(" in an empty environment with the commands load \"").append(name)
                    .append(".s\", run, print_all_regs hex (no program arguments).\n");
            sb.append("# $sp and the start-up code's $a0-$a2 come from the run stack: with no arguments $sp is 0x7ffffff0;"
                    + " Hallym MIPS passes the file name, so its reg $sp is 0x7fffffe4.\n");
            sb.append("# Regenerate while vendor/spim exists: ./gradlew :lib-mips:test --tests"
                    + " kr.ac.hallym.hcs.mips.HallymMipsGoldenTest -Phcs.update=true (D-138).\n");
            for (Map.Entry<Integer, String> e : regs.entrySet()) {
                sb.append(NAMES[e.getKey()]).append(' ').append(e.getValue()).append('\n');
            }
            sb.append("hi ").append(hilo.group(1)).append('\n');
            sb.append("lo ").append(hilo.group(2)).append('\n');
            sb.append("console ").append(escape(console)).append('\n');
            return sb.toString();
        } finally {
            Files.deleteIfExists(cmd);
        }
    }

    /** 오라클 파일이 지금의 vendor SPIM 결과와 같다(SPIM이 있는 동안). -Phcs.update=true면 다시 쓴다. */
    @TestFactory
    Stream<DynamicTest> oracleFilesAreSpimOutput() {
        return CASES.stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            Path file = DIR.resolve(name + ".regs");
            Assumptions.assumeTrue(AssemblerIntegrationTest.ORACLE.canExecute(),
                    "vendor SPIM not built (make -C native/hcs-asm oracle): the .regs files stay the oracle");
            String fresh = spimOracle(name, !StartFacts.withoutExceptionHandler(image(name)));
            if (Boolean.getBoolean("hcs.update")) {
                Files.writeString(file, fresh);
            }
            assertTrue(Files.exists(file), "make it with -Phcs.update=true: " + file);
            assertEquals(fresh, Files.readString(file));
        }));
    }

    /** 이미지의 명령 이름(시작 코드 제외: 처리기를 불러온 이미지는 앞 9워드가 시작 코드다, 명세 "An example"). */
    static TreeSet<String> programOps(ExecutableImage img) {
        TreeSet<String> out = new TreeSet<>();
        for (int w : programWords(img)) {
            String m = Disassembler.mnemonic(w);
            out.add(m == null ? "?" : m);
        }
        return out;
    }

    static List<Integer> programWords(ExecutableImage img) {
        long from = StartFacts.withoutExceptionHandler(img) ? 0x00400000L : 0x00400024L;
        return new ArrayList<>(img.textWords().tailMap(from).values());
    }

    /** 워드가 값을 쓰는 레지스터(목적지 필드). 없으면 0($zero). */
    static int dest(int word) {
        int op = word >>> 26;
        int rt = (word >>> 16) & 31;
        int rd = (word >>> 11) & 31;
        int funct = word & 63;
        if (op == 0) {
            return funct == 8 || funct == 12 || funct == 13 || funct >= 0x18 && funct <= 0x1b ? 0 : rd;
        } else if (op == 0x1c) {
            return rd;
        } else if (op == 3) {
            return 31;
        } else if (op >= 8 && op <= 15 || op >= 0x20 && op <= 0x26) {
            return rt;
        }
        return 0;
    }

    /** 프로그램 자신의 워드가 쓰는 레지스터. onlyUnsupported면 ref-mips에 없는 명령이 쓰는 레지스터만. */
    static TreeSet<Integer> written(ExecutableImage img, boolean onlyUnsupported) {
        TreeSet<Integer> out = new TreeSet<>();
        for (int word : programWords(img)) {
            String m = Disassembler.mnemonic(word);
            if (!onlyUnsupported || !REF_MIPS.contains(m)) {
                out.add(dest(word));
            }
        }
        out.remove(0);
        return out;
    }

    @TestFactory
    Stream<DynamicTest> refMipsEndsWithTheOracleRegisters() {
        return CASES.stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            ExecutableImage img = image(name);
            Oracle oracle = Oracle.read(DIR.resolve(name + ".regs"));
            assertEquals(34, oracle.regs.size(), "32 registers, hi, lo");
            assertEquals(0x10008000, (int) oracle.regs.get("$gp"), "the same $gp as the image's reg $gp");
            TreeSet<String> missing = programOps(img);
            missing.removeAll(REF_MIPS);
            // ref-mips에 없는 명령이 쓰는 레지스터는 비교하지 않는다. 그 밖의 레지스터·Console이 같으면 제어 흐름도 같았다.
            TreeSet<Integer> skipped = written(img, true);
            TreeSet<Integer> regs = written(img, false);
            regs.removeAll(skipped);
            if (regs.isEmpty()) {
                String why = "hmx goldens: " + name + ": registers NOT compared (skipped, not a pass): ref-mips has no "
                        + String.join(", ", missing);
                System.out.println(why);
                Assumptions.abort(why);
            }
            Result run = runRefMips(img);
            List<String> compared = new ArrayList<>();
            for (int i : regs) {
                assertEquals(String.format("%08x", oracle.regs.get(NAMES[i])), String.format("%08x", run.regs[i]),
                        name + " " + NAMES[i]);
                compared.add(NAMES[i]);
            }
            assertEquals(oracle.console, run.console, name + " console");
            List<String> notCompared = new ArrayList<>();
            for (int i : skipped) {
                notCompared.add(NAMES[i]);
            }
            System.out.println("hmx goldens: " + name + ": ref-mips (PC start " + ExecutableImage.hex(img.entry())
                    + ") ends with the oracle's " + String.join(" ", compared) + " and console output"
                    + (missing.isEmpty() ? "" : "; not in ref-mips: " + String.join(", ", missing)
                            + (notCompared.isEmpty() ? " (they write no register)"
                                    : ", so " + String.join(" ", notCompared) + " NOT compared (not a pass)")));
        }));
    }

    static final class Result {
        final int[] regs = new int[32];
        String console;
    }

    /** ref-mips(PC 시작 = 이미지의 entry)를 exit까지. 이미지는 불러오기와 같은 길로 넣는다. */
    static Result runRefMips(ExecutableImage img) throws Exception {
        InProcessSim sim = new InProcessSim();
        RefMips cpu = RefMips.build(sim.b, sim.mips, img.entry());
        sim.b.commit();
        RefMipsTest.load(sim.file.getMainCircuit(), cpu, img);
        sim.start();
        assertEquals((long) img.entry(), sim.port(cpu.imem, InstructionMemory.ADDR).toIntValue() & 0xffffffffL,
                "PC starts at the entry");
        Console.State console = null;
        for (int n = 0; n < MAX_CYCLES; n += 1) {
            sim.cycle();
            console = (Console.State) sim.data(cpu.console);
            if (console != null && console.exited) {
                break;
            }
        }
        assertNotNull(console);
        assertTrue(console.exited, "exit within " + MAX_CYCLES + " cycles");
        Result r = new Result();
        r.console = console.text();
        for (int i = 1; i < 32; i += 1) {
            r.regs[i] = sim.port(cpu.regs[i], 0).toIntValue();
        }
        return r;
    }

    /** 오라클 파일 모양: 머리 주석이 만든 방법을 말하고, 레지스터 32개와 hi·lo, console 줄이 있다. */
    @Test
    void oracleFilesSayHowTheyWereMade() throws Exception {
        for (String name : CASES) {
            Oracle o = Oracle.read(DIR.resolve(name + ".regs"));
            String head = String.join("\n", o.header);
            assertTrue(head.contains("SPIM 9.1.24") && head.contains(name.equals("no-handler") ? "-noexception"
                    : "-exception") && head.contains("empty environment"), head);
            assertEquals(Arrays.asList(NAMES), new ArrayList<>(o.regs.keySet()).subList(0, 32));
            assertNotNull(o.console, name);
            assertFalse(head.contains(File.separator + "home" + File.separator), "no local paths");
        }
        assertEquals("sum = 14", Oracle.read(DIR.resolve("data.regs")).console);
        assertEquals("a\\\"b\nc", unescape(escape("a\\\"b\nc")));
    }
}
