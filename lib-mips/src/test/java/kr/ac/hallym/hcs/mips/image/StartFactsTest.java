/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Z-03(D-138): 실행 이미지 시작 사실. 요약 머리 줄(entry, reg), 진입 루틴의 jr $ra, 상태 표시줄의 PC ≠ entry.
 */
class StartFactsTest {
    static final Path HMX = Paths.get(System.getProperty("hcs.testsDir", "../tests"), "hmx");

    static final int NOP = 0;
    static final int ADDI = 0x20080001; // addi $t0, $zero, 1
    static final int SYSCALL = 0x0000000c;
    static final int JR_RA = 0x03e00008;

    /** jal의 워드(목적지 target). */
    static int jal(long target) {
        return 0x0c000000 | (int) ((target & 0x0fffffffL) >>> 2);
    }

    static List<String> en(List<Msg> ms) {
        List<String> out = new ArrayList<>();
        for (Msg m : ms) {
            out.add(m.en);
        }
        return out;
    }

    static ExecutableImage read(String name) throws Exception {
        HmxParser.Result r = HmxParser.read(HMX.resolve(name).toFile());
        assertEquals(List.of(), r.errors, name);
        return r.image;
    }

    // ---- 요약 머리 줄 ----

    @Test
    void entryLineNamesTheSymbolAtTheEntry() {
        ExecutableImage img = new ExecutableImage.Builder().entry(0x00400024L).symbol("str", 0x10010000L)
                .symbol("main", 0x00400024L).text(0x00400000L, NOP).build();
        Msg m = StartFacts.entryLine(img);
        assertEquals("entry 0x00400024 (main)", m.en);
        assertEquals(m.en, m.ko, "a name line is English in both languages (D-049)");
    }

    @Test
    void entryLineWithoutSymbol() {
        ExecutableImage img = new ExecutableImage.Builder().entry(0x00400024L).symbol("loop", 0x00400028L).build();
        assertEquals("entry 0x00400024", StartFacts.entryLine(img).en);
    }

    @Test
    void severalSymbolsAtTheEntryInFileOrder() {
        ExecutableImage img = new ExecutableImage.Builder().entry(0x00400024L).symbol("start", 0x00400024L)
                .symbol("other", 0x00400000L).symbol("main", 0x00400024L).symbol("_go", 0x00400024L).build();
        assertEquals("entry 0x00400024 (start, main, _go)", StartFacts.entryLine(img).en);
    }

    @Test
    void noEntry() {
        ExecutableImage img = new ExecutableImage.Builder().reg("$sp", 0x7ffff000L).symbol("x", 0x00400000L)
                .text(0x00400000L, JR_RA).build();
        assertEquals("no entry", StartFacts.entryLine(img).en);
        assertEquals("no entry", StartFacts.entryLine(img).ko);
        assertEquals(List.of("no entry", "reg $sp 0x7ffff000"), en(StartFacts.summary(img)));
        assertNull(StartFacts.entryRoutine(img));
        assertEquals(List.of(), StartFacts.jrRaInEntryRoutine(img));
        assertNull(StartFacts.jrRaFact(img));
        assertEquals(List.of(), StartFacts.facts(img));
        assertNull(StartFacts.pcFact(img, 0x00400000L));
    }

    @Test
    void regLinesKeepFileOrderAndStandardNames() {
        ExecutableImage img = new ExecutableImage.Builder().entry(0x00400000L).reg("$gp", 0x10008000L)
                .reg("$29", 0x7ffff000L).reg("$s8", 0xfffffffcL).reg("$ra", 0x00400018L).build();
        assertEquals(List.of("entry 0x00400000", "reg $gp 0x10008000", "reg $sp 0x7ffff000", "reg $fp 0xfffffffc",
                "reg $ra 0x00400018"), en(StartFacts.summary(img)));
        for (Msg m : StartFacts.summary(img)) {
            assertEquals(m.en, m.ko);
        }
        assertEquals(List.of(), StartFacts.regLines(new ExecutableImage.Builder().entry(0L).build()));
    }

    @Test
    void summaryOfTheSpecExample() throws Exception {
        assertEquals(List.of("entry 0x00400024 (main)", "reg $sp 0x7ffff000", "reg $gp 0x10008000"),
                en(StartFacts.summary(read("example.hmx"))));
    }

    // ---- 진입 루틴과 jr $ra ----

    /** 시작 코드 9워드(jal main 포함) + main. */
    static ExecutableImage.Builder withStartup(int... main) {
        int[] words = new int[9 + main.length];
        words[5] = jal(0x00400024L); // 0x00400014: jal main
        words[8] = SYSCALL;
        System.arraycopy(main, 0, words, 9, main.length);
        return new ExecutableImage.Builder().entry(0x00400024L).symbol("main", 0x00400024L).text(0x00400000L, words);
    }

    @Test
    void jrRaInTheEntryRoutineIsAFact() {
        ExecutableImage img = withStartup(ADDI, ADDI, JR_RA).build(); // jr $ra at 0x0040002c
        assertArrayEquals(new long[] {0x00400024L, 0x00400030L}, StartFacts.entryRoutine(img),
                "the startup code's jal main does not cut the routine");
        assertEquals(List.of(0x0040002cL), StartFacts.jrRaInEntryRoutine(img));
        Msg m = StartFacts.jrRaFact(img);
        assertEquals("jr $ra in the entry routine: 0x0040002c. The startup code does not run in the circuit, so $ra"
                + " is whatever the circuit gives.", m.en);
        assertEquals("진입 루틴의 jr $ra: 0x0040002c. 회로에서는 시작 코드가 돌지 않으므로 $ra 값은 회로가 주는 값입니다.", m.ko);
        assertEquals(1, StartFacts.facts(img).size());
        assertEquals(m.en, StartFacts.facts(img).get(0).en);
    }

    @Test
    void severalJrRaAddresses() {
        ExecutableImage img = withStartup(JR_RA, ADDI, JR_RA).build();
        assertEquals(List.of(0x00400024L, 0x0040002cL), StartFacts.jrRaInEntryRoutine(img));
        assertEquals("jr $ra in the entry routine: 0x00400024, 0x0040002c. The startup code does not run in the"
                + " circuit, so $ra is whatever the circuit gives.", StartFacts.jrRaFact(img).en);
        assertEquals("진입 루틴의 jr $ra: 0x00400024, 0x0040002c. 회로에서는 시작 코드가 돌지 않으므로 $ra 값은 회로가 주는"
                + " 값입니다.", StartFacts.jrRaFact(img).ko);
    }

    @Test
    void noJrRa() {
        // jr $t0(0x01000008), jalr $ra(0x03e0f809), 비슷한 워드는 jr $ra가 아니다
        ExecutableImage img = withStartup(ADDI, 0x01000008, 0x03e0f809, JR_RA + 1, JR_RA | 0x10000).build();
        assertEquals(List.of(), StartFacts.jrRaInEntryRoutine(img));
        assertNull(StartFacts.jrRaFact(img));
        assertEquals(List.of(), StartFacts.facts(img));
    }

    /** 루틴 안의 라벨(loop)에서 자르지 않는다: 반복문 뒤의 jr $ra도 진입 루틴이다. */
    @Test
    void labelsInsideTheRoutineDoNotEndIt() {
        ExecutableImage img = withStartup(ADDI, ADDI, 0x1500fffe /* bne $t0,$0,loop */, JR_RA)
                .symbol("loop", 0x00400028L).symbol("done", 0x00400030L).build();
        assertEquals(List.of(0x00400030L), StartFacts.jrRaInEntryRoutine(img));
    }

    /** jal로 부르는 루틴(entry 뒤의 첫 jal 목적지)부터는 진입 루틴이 아니다. */
    @Test
    void jrRaInACalledRoutineIsOutside() {
        // main: jal f; syscall; jr $ra(0x2c) ; f(0x30): addi; jr $ra(0x34); g(0x38): jr $ra
        ExecutableImage img = withStartup(jal(0x00400030L), SYSCALL, JR_RA, ADDI, JR_RA, JR_RA, jal(0x00400038L))
                .symbol("f", 0x00400030L).build();
        assertArrayEquals(new long[] {0x00400024L, 0x00400030L}, StartFacts.entryRoutine(img));
        assertEquals(List.of(0x0040002cL), StartFacts.jrRaInEntryRoutine(img));
        assertEquals(List.of(0x00400024L, 0x00400030L, 0x00400038L), StartFacts.callTargets(img));
        // 부르는 루틴만 있고 main에는 jr $ra가 없다(tests/asm/jumps.s 모양)
        ExecutableImage only = withStartup(jal(0x0040002cL), SYSCALL, JR_RA).build();
        assertEquals(List.of(), StartFacts.jrRaInEntryRoutine(only));
        assertNull(StartFacts.jrRaFact(only));
    }

    /** entry 앞의 jr $ra(main보다 먼저 둔 함수, 시작 코드)는 진입 루틴이 아니다. */
    @Test
    void jrRaBeforeTheEntryIsOutside() {
        ExecutableImage img = new ExecutableImage.Builder().entry(0x00400028L).symbol("helper", 0x00400024L)
                .symbol("main", 0x00400028L).text(0x00400024L, JR_RA, jal(0x00400024L), SYSCALL).build();
        assertArrayEquals(new long[] {0x00400028L, 0x00400030L}, StartFacts.entryRoutine(img));
        assertEquals(List.of(), StartFacts.jrRaInEntryRoutine(img));
    }

    /** 루틴은 entry를 담는 .text 구간 끝에서 끝난다(다른 구간의 jr $ra는 밖). */
    @Test
    void theRoutineEndsAtTheSegmentEnd() {
        ExecutableImage img = new ExecutableImage.Builder().entry(0x00400000L).text(0x00400000L, ADDI, JR_RA)
                .text(0x00400100L, JR_RA).build();
        assertArrayEquals(new long[] {0x00400000L, 0x00400008L}, StartFacts.entryRoutine(img));
        assertEquals(List.of(0x00400004L), StartFacts.jrRaInEntryRoutine(img));
        // 뒤 구간의 jal 목적지는 앞 구간을 자르지 않는다
        ExecutableImage far = new ExecutableImage.Builder().entry(0x00400000L).text(0x00400000L, ADDI, JR_RA)
                .text(0x00400100L, jal(0x00400200L)).build();
        assertArrayEquals(new long[] {0x00400000L, 0x00400008L}, StartFacts.entryRoutine(far));
        // entry가 둘째 구간에 있으면 그 구간
        ExecutableImage second = new ExecutableImage.Builder().entry(0x00400104L).text(0x00400000L, JR_RA)
                .text(0x00400100L, JR_RA, ADDI, JR_RA).build();
        assertArrayEquals(new long[] {0x00400104L, 0x0040010cL}, StartFacts.entryRoutine(second));
        assertEquals(List.of(0x00400108L), StartFacts.jrRaInEntryRoutine(second));
    }

    @Test
    void entryOutsideEveryTextSegmentHasNoRoutine() {
        // 구간 바로 뒤(끝 = entry), 앞, .data 안
        for (long entry : new long[] {0x00400008L, 0x003ffffcL, 0x10010000L}) {
            ExecutableImage img = new ExecutableImage.Builder().entry(entry).text(0x00400000L, ADDI, JR_RA)
                    .data(0x10010000L, 8, 0, 0xe0, 3).build();
            assertNull(StartFacts.entryRoutine(img), Long.toHexString(entry));
            assertNull(StartFacts.jrRaFact(img));
        }
        // entry가 구간의 마지막 워드면 그 한 워드
        ExecutableImage last = new ExecutableImage.Builder().entry(0x00400004L).text(0x00400000L, ADDI, JR_RA).build();
        assertArrayEquals(new long[] {0x00400004L, 0x00400008L}, StartFacts.entryRoutine(last));
        assertEquals(List.of(0x00400004L), StartFacts.jrRaInEntryRoutine(last));
    }

    /** jal 목적지의 위 4비트는 jal 다음 주소(PC+4)에서 온다. */
    @Test
    void callTargetsUseTheUpperBitsOfTheNextAddress() {
        ExecutableImage img = new ExecutableImage.Builder().entry(0x0ffffff8L)
                .text(0x0ffffff8L, NOP, jal(0x00000040L)).build(); // 0x0ffffffc의 jal: PC+4 = 0x10000000
        assertEquals(List.of(0x10000040L), StartFacts.callTargets(img));
        ExecutableImage low = new ExecutableImage.Builder().entry(0x00400000L)
                .text(0x00400000L, jal(0x0ffffffcL), 0x0fffffff /* jal, index 전부 1 */).build();
        assertEquals(List.of(0x0ffffffcL), StartFacts.callTargets(low));
        // 다른 opcode(j = 2, beq = 4)는 부르기가 아니다
        ExecutableImage other = new ExecutableImage.Builder().entry(0x00400000L)
                .text(0x00400000L, 0x08100010, 0x10000010, 0xcc100010).build();
        assertEquals(List.of(), StartFacts.callTargets(other));
    }

    @Test
    void regRaIsShownButNotSet() {
        ExecutableImage img = withStartup(JR_RA).reg("$ra", 0x00400018L).build();
        Msg m = StartFacts.jrRaFact(img);
        assertEquals("jr $ra in the entry routine: 0x00400024. The startup code does not run in the circuit, so $ra"
                + " is whatever the circuit gives. The file gives reg $ra 0x00400018 (the tool does not set"
                + " registers).", m.en);
        assertEquals("진입 루틴의 jr $ra: 0x00400024. 회로에서는 시작 코드가 돌지 않으므로 $ra 값은 회로가 주는 값입니다."
                + " 파일의 시작 값: reg $ra 0x00400018(도구는 레지스터 값을 넣지 않습니다).", m.ko);
        // 다른 레지스터 시작 값은 이 줄에 넣지 않는다
        ExecutableImage sp = withStartup(JR_RA).reg("$sp", 0x7ffff000L).build();
        assertFalse(StartFacts.jrRaFact(sp).en.contains("reg"), StartFacts.jrRaFact(sp).en);
    }

    /** 실제 파일: 부르는 루틴의 jr $ra(jumps), entry 앞의 함수(main-not-first), jr $ra 없음(example). */
    @Test
    void generatedImages() throws Exception {
        assertNull(StartFacts.jrRaFact(read("asm/jumps.hmx")));
        assertNull(StartFacts.jrRaFact(read("asm/main-not-first.hmx")));
        assertNull(StartFacts.jrRaFact(read("example.hmx")));
        assertNull(StartFacts.jrRaFact(read("mips/factorial.hmx")), "fact's jr $ra is in a called routine");
        assertEquals("entry 0x00400028 (main)", StartFacts.entryLine(read("asm/main-not-first.hmx")).en);
    }

    // ---- 예외 처리기 없이 어셈블한 이미지 ----

    /** 기호에 __start가 있으면 처리기 없이 어셈블한 이미지다(명세: 처리기의 라벨은 기호에 넣지 않는다). */
    @Test
    void noExceptionHandler() {
        ExecutableImage nh = new ExecutableImage.Builder().entry(0x00400000L).symbol("__start", 0x00400000L)
                .text(0x00400000L, ADDI, JR_RA, SYSCALL).build();
        assertTrue(StartFacts.withoutExceptionHandler(nh));
        Msg m = StartFacts.noHandlerFact(nh);
        assertEquals("Assembled without the exception handler: no start-up code, entry = the program's own __start.",
                m.en);
        assertEquals("예외 처리기 없이 어셈블한 이미지: 시작 코드 없음, 진입점 = 프로그램의 __start.", m.ko);
        assertEquals(List.of(0x00400004L), StartFacts.jrRaInEntryRoutine(nh), "the data is still there");
        assertNull(StartFacts.jrRaFact(nh), "no start-up code, so no jal main and no $ra fact");
        assertEquals(List.of(m.en), en(StartFacts.facts(nh)));
        // __start와 main이 둘 다 있고 entry가 main
        ExecutableImage both = new ExecutableImage.Builder().entry(0x00400008L).symbol("__start", 0x00400000L)
                .symbol("main", 0x00400008L).text(0x00400000L, jal(0x00400008L), NOP, JR_RA).build();
        assertEquals("Assembled without the exception handler: no start-up code, the program's own __start ="
                + " 0x00400000.", StartFacts.noHandlerFact(both).en);
        assertEquals("예외 처리기 없이 어셈블한 이미지: 시작 코드 없음, 프로그램의 __start = 0x00400000.",
                StartFacts.noHandlerFact(both).ko);
        assertNull(StartFacts.jrRaFact(both));
        // entry가 없어도 사실 줄은 __start 주소를 말한다
        ExecutableImage noEntry = new ExecutableImage.Builder().symbol("__start", 0x00400000L).build();
        assertEquals("Assembled without the exception handler: no start-up code, the program's own __start ="
                + " 0x00400000.", StartFacts.noHandlerFact(noEntry).en);
        // 처리기를 불러온 이미지: 사실 줄이 없다
        ExecutableImage handler = withStartup(JR_RA).build();
        assertFalse(StartFacts.withoutExceptionHandler(handler));
        assertNull(StartFacts.noHandlerFact(handler));
        assertEquals(1, StartFacts.facts(handler).size());
    }

    // ---- 상태 표시줄 ----

    @Test
    void pcFact() {
        ExecutableImage img = new ExecutableImage.Builder().entry(0x00400024L).build();
        assertNull(StartFacts.pcFact(img, 0x00400024L), "PC = entry: nothing to say");
        assertNull(StartFacts.pcFact(img, 0x00400024L | 0xffffffff00000000L), "32 bits");
        Msg m = StartFacts.pcFact(img, 0x00400000L);
        assertEquals("PC 0x00400000 · executable image entry 0x00400024", m.en);
        assertEquals("PC 0x00400000 · 실행 이미지 진입점 0x00400024", m.ko);
        assertEquals("PC 0xfffffffc · executable image entry 0x00400024", StartFacts.pcFact(img, -4L).en);
        assertEquals("PC 0x00400028 · executable image entry 0x00400024", StartFacts.pcFact(img, 0x00400028L).en);
        assertNull(StartFacts.pcFact(new ExecutableImage.Builder().build(), 0x00400000L), "no entry");
    }

    /** 한국어 문장: 이름·주소 바로 뒤에 조사가 없다(D-126, HmxParserTest와 같은 규칙). */
    @Test
    void koreanWordingFollowsTheParticleRule() {
        Pattern particle = Pattern.compile("[A-Za-z0-9._$>)\\]](을|를|이|가|은|는|에|의|와|과|로|으로|에서|도|만)(\\s|[.,])");
        List<Msg> all = new ArrayList<>();
        all.add(StartFacts.jrRaFact(withStartup(JR_RA, JR_RA).reg("$ra", 0x00400018L).build()));
        all.add(StartFacts.pcFact(new ExecutableImage.Builder().entry(0x00400024L).build(), 0));
        all.addAll(StartFacts.summary(withStartup(JR_RA).reg("$sp", 0x7ffff000L).build()));
        for (Msg m : all) {
            assertFalse(particle.matcher(m.ko).find(), m.ko);
            assertFalse(m.ko.contains("하면 됩니다"), m.ko);
            assertFalse(m.en.matches(".*[\\uAC00-\\uD7AF].*"), m.en);
        }
    }
}
