/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** D-141: 옛 .circ의 .s 경로를 알아보는 규칙과 사실 문장(트랙 A 메뉴와 v2 엔진 mips.facts가 같은 것을 쓴다). */
class AssemblySourceTest {
    @Test
    void recognisesAssemblyPaths() {
        for (String p : new String[] {"sum.s", "lab/sum.s", "C:\\lab\\SUM.S", " prog.asm ", "x.ASM", "../../mips/a.s"}) {
            assertTrue(AssemblySource.isAssembly(p), p);
        }
        for (String p : new String[] {null, "", "sum.hmx", "sum.s.hmx", "prog.sasm", "a.s/b.hmx", "lab.s/", "notes.txt"}) {
            assertFalse(AssemblySource.isAssembly(p), String.valueOf(p));
        }
    }

    @Test
    void namesTheFileAndItsImage() {
        assertEquals("sum.s", AssemblySource.fileName("lab/sum.s"));
        assertEquals("SUM.S", AssemblySource.fileName("C:\\lab\\SUM.S"));
        assertEquals("prog.asm", AssemblySource.fileName(" prog.asm "));
        assertEquals("", AssemblySource.fileName(null));
        assertEquals("sum.hmx", AssemblySource.imageName("../mips/sum.s"));
        assertEquals("LAB04.hmx", AssemblySource.imageName("C:\\lab\\LAB04.ASM"));
        assertEquals("noext.hmx", AssemblySource.imageName("noext"));
    }

    /** 사용자 문장(D-141) 그대로의 뜻: 사실(이 파일은 .s를 가리킨다)과 할 일(Export executable image (.hmx)로 내보낸 파일). */
    @Test
    void factSaysWhatAndWhatToDo() {
        assertEquals("이 파일은 .s 파일을 가리킵니다. Hallym MIPS에서 Export executable image (.hmx) 단추로 내보낸 파일을 불러오세요.",
                AssemblySource.FACT.ko);
        assertEquals("This file points to a .s file. Load the file exported with Export executable image (.hmx) in"
                + " Hallym MIPS.", AssemblySource.FACT.en);
        // 한국어 문장 규칙(D-126, HmxParserTest와 같은 예외 "Hallym MIPS에서"): 이름·기호 바로 뒤에 조사가 없고,
        // "~하면 됩니다"가 없다. 영어 문장에는 한글이 없다
        Pattern particle = Pattern.compile("[A-Za-z0-9._$>)\\]](을|를|이|가|은|는|에|의|와|과|로|으로|에서|도|만)(\\s|[.,])");
        String body = AssemblySource.FACT.ko.replace("Hallym MIPS에서", "");
        assertFalse(particle.matcher(body).find(), AssemblySource.FACT.ko);
        assertTrue(particle.matcher("이 파일은 .s를 가리킵니다.").find(), "the rule catches \".s를\"");
        assertFalse(AssemblySource.FACT.ko.contains("하면 됩니다"));
        assertFalse(AssemblySource.FACT.en.matches(".*[\\uAC00-\\uD7AF].*"));
    }
}
