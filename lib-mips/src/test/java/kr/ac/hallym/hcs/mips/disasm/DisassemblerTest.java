/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.disasm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;

/** Z-04: 손으로 확인한 워드들(글은 SPIM·QtSpim이 보인 그대로, tests/disasm·tests/asm/qtspim에서 가져왔다). */
class DisassemblerTest {
    static Map<Integer, String> symbols(Object... addrName) {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (int i = 0; i < addrName.length; i += 2) {
            m.put((Integer) addrName[i], (String) addrName[i + 1]);
        }
        return m;
    }

    @Test
    void integerInstructions() {
        assertEquals("lw $4, 0($29)", Disassembler.text(0x8fa40000, 0x00400000));
        assertEquals("addiu $5, $29, 4", Disassembler.text(0x27a50004, 0x00400004));
        assertEquals("sll $2, $4, 2", Disassembler.text(0x00041080, 0x0040000c));
        assertEquals("nop", Disassembler.text(0, 0x00400018));
        assertEquals("sll $0, $0, 1", Disassembler.text(0x00000040, 0)); // ssnop: 워드가 0일 때만 nop
        assertEquals("ori $2, $0, 10", Disassembler.text(0x3402000a, 0));
        assertEquals("syscall", Disassembler.text(0x0000000c, 0));
        assertEquals("break", Disassembler.text(0x0000014d, 0)); // 코드는 보이지 않는다
        assertEquals("lui $1, -1", Disassembler.text(0x3c01ffff, 0)); // 즉값은 부호 있는 10진수
        assertEquals("ori $1, $1, -1", Disassembler.text(0x3421ffff, 0));
        assertEquals("jalr $31, $25", Disassembler.text(0x0320f809, 0));
        assertEquals("mul $8, $9, $10", Disassembler.text(0x712a4002, 0));
        assertEquals("mfc0 $9, $9", Disassembler.text(0x40094800, 0));
        assertEquals("eret", Disassembler.text(0x42000018, 0));
        assertEquals("movt $3, $2, 6", Disassembler.text(0x00591801, 0));
    }

    @Test
    void jumpsAndBranchesWithSymbols() {
        Map<Integer, String> s = symbols(0x00400024, "main", 0x0040002c, "loop", 0x0040006c, "done");
        assertEquals("jal 0x00400024 [main]", Disassembler.text(0x0c100009, 0x00400014, s));
        assertEquals("jal 0x00400024", Disassembler.text(0x0c100009, 0x00400014));
        assertEquals("jal 0x00400024", Disassembler.text(0x0c100009, 0x00400014, symbols()));
        assertEquals("bne $8, $9, -4 [loop-0x00400030]", Disassembler.text(0x1509ffff, 0x00400030, s));
        assertEquals("bgez $0 4 [done-0x00400068]", Disassembler.text(0x04010001, 0x00400068, s)); // 쉼표 없음
        assertEquals("beq $0, $0, 0", Disassembler.text(0x10000000, 0x00400030, s)); // 목적지에 라벨 없음
        // 목적지는 분기 주소 + imm×4(D-010). SPIM이 보이는 변위는 비트 15 규칙을 따른다(0x7fff → -4).
        assertEquals("bne $0, $0, -4 [far-0x00400000]",
                Disassembler.text(0x14007fff, 0x00400000, symbols(0x0041fffc, "far")));
        assertEquals("bne $0, $0, -131072", Disassembler.text(0x14008000, 0x00400000));
        assertEquals("bc2f2 93184", Disassembler.text(0x49085b00, 0)); // 0x5b00×4: 비트 15가 0이라 그대로
        // 점프 목적지의 위 4비트는 PC+4에서 온다(MIPS32). 글에는 SPIM처럼 아래 28비트만 보인다.
        Map<Integer, String> kernel = symbols(0x80000258, "l17a");
        assertEquals("j 0x00000258", Disassembler.text(0x08000096, 0x004006fc, kernel));
        assertEquals("j 0x00000258 [l17a]", Disassembler.text(0x08000096, 0x80000100, kernel));
        assertEquals("cop2 0x08296c00", Disassembler.text(0x4a0a5b00, 0, symbols(0x08296c00, "x"))); // 점프 아님
        // 256MB 구역의 마지막 워드: PC+4의 위 4비트가 다음 구역이다
        Map<Integer, String> next = symbols(0x10000040, "far", 0x00000040, "near");
        assertEquals("jal 0x00000040 [far]", Disassembler.text(0x0c000010, 0x0ffffffc, next));
        assertEquals("jal 0x00000040 [near]", Disassembler.text(0x0c000010, 0x0ffffff8, next));
    }

    @Test
    void floatingPointAsQtSpimListsIt() {
        assertEquals("c.eq.d $f2, $f4", Disassembler.text(0x46241032, 0));
        assertEquals("c.un.d 1, $f2, $f4", Disassembler.text(0x46241131, 0));
        assertEquals("movf.d $f4, $f2, 0", Disassembler.text(0x46201111, 0));
        assertEquals("movt.s $f4, $f2, 7", Disassembler.text(0x461d1111, 0));
        assertEquals("movn.d $f2, $f0, 0", Disassembler.text(0x46200093, 0));
        assertEquals("trunc.w.s $f0, $f2", Disassembler.text(0x4600100d, 0));
        assertEquals("bc1fl5 0 [main-0x00400000]", Disassembler.text(0x45160000, 0x00400000,
                symbols(0x00400000, "main")));
        assertEquals("bc2t1 0", Disassembler.text(0x49050000, 0));
        assertEquals("lwc1 $f1, -28548($1)", Disassembler.text(0xc421907c, 0));
        assertEquals("cvt.d.w $f0, $f2", Disassembler.text(0x46201021, 0));
    }

    /**
     * SPIM 표에 같은 키가 둘인 워드: SPIM의 글은 C 라이브러리 qsort가 같은 키를 어떤 순서로 두느냐에 달려 골든에서 뺐다
     * (D-127, tools/disasm-golden.py tie_dependent). 디스어셈블러는 Linux(glibc) SPIM의 글을 따른다. 어셈블할 수 있는
     * trunc.w.s·floor.w.s는 어셈블한 목록 그대로다(quirks.txt).
     */
    @Test
    void wordsWhoseSpimTextDependsOnQsortKeepTheLinuxText() {
        assertEquals("swxc1 $f0, $f0, $f0", Disassembler.text(0x46000008, 0)); // 다른 순서면 round.l.s
        assertEquals("sdxc1 $f1, $f1, $f1", Disassembler.text(0x46010849, 0)); // 다른 순서면 trunc.l.s
        assertEquals("trunc.w.s $f6, $f5", Disassembler.text(0x4604298d, 0)); // Linux .word 목록은 suxc1
        assertEquals("floor.w.s $f31, $f31", Disassembler.text(0x461fffcf, 0)); // 다른 순서면 prefx
        assertEquals(Disassembler.UNKNOWN, Disassembler.text(0x4c0a5b00, 0)); // 다른 순서면 lwxc1
    }

    @Test
    void unknownWordsAndMnemonics() {
        assertEquals(Disassembler.UNKNOWN, Disassembler.text(0xffffffff, 0));
        assertEquals("<unknown instruction 0>", Disassembler.text(0x4c000000, 0)); // COP1X: SPIM이 모른다
        assertNull(Disassembler.mnemonic(0xffffffff));
        assertEquals("nop", Disassembler.mnemonic(0));
        assertEquals("sll", Disassembler.mnemonic(0x00041080));
        assertEquals("bc1fl", Disassembler.mnemonic(0x45160000));
        assertEquals("movt", Disassembler.mnemonic(0x00591801));
        assertEquals("c.eq.d", Disassembler.mnemonic(0x46241032));
        assertEquals("ext", Disassembler.mnemonic(0x7d2a5b04)); // SPIM: SPECIAL3 전체
    }

    @Test
    void symbolTableByAddress() {
        Map<String, Long> labels = new LinkedHashMap<>();
        labels.put("zeta", 0x00400024L);
        labels.put("main", 0x00400024L);
        labels.put("loop", 0x0040002cL);
        Map<Integer, String> s = Disassembler.byAddress(labels);
        assertEquals("main", s.get(0x00400024)); // 한 주소에 여럿이면 사전순 앞
        assertEquals("loop", s.get(0x0040002c));
        assertEquals(0, Disassembler.byAddress(null).size());
    }

    /** D-125: 공용 소스라 트랙 A jar에 Java 8 바이트코드로 들어간다. */
    @Test
    void inTheTrackAJarAsJava8() throws Exception {
        File jar = new File(System.getProperty("hcs.mipsJar", "build/libs/hcs-mips.jar"));
        try (ZipFile z = new ZipFile(jar)) {
            ZipEntry e = z.getEntry("kr/ac/hallym/hcs/mips/disasm/Disassembler.class");
            assertNotNull(e, "Disassembler is in " + jar);
            try (InputStream in = z.getInputStream(e); DataInputStream d = new DataInputStream(in)) {
                assertEquals(0xCAFEBABE, d.readInt());
                d.readUnsignedShort();
                assertEquals(52, d.readUnsignedShort(), "class file major version 52 = Java 8");
            }
        }
    }
}
