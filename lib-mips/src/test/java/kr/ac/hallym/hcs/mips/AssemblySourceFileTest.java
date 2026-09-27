/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.mips.image.AssemblySource;
import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * .s 경로가 남은 옛 .circ(D-141): 원조 2.7.1 + 이 jar에서 전과 똑같이 열리고, 고치지 않으면 같은 바이트로 저장된다.
 * 우클릭 메뉴는 Reload 대신 .hmx를 고르라는 항목이고, 그 경로를 다시 불러오면 사실과 할 일만 돌려준다. 학생이 .hmx를
 * 불러오면 {@code source}가 그 경로로 바뀐다.
 */
class AssemblySourceFileTest {
    static final Path REF = Path.of(System.getProperty("hcs.testsDir"), "mips", "ref-mips.circ");

    @TempDir
    Path tmp;

    static LogisimFile open(Path circ) throws Exception {
        Files.copy(OriginalLogisim.MIPS_JAR, circ.resolveSibling("hcs-mips.jar"), StandardCopyOption.REPLACE_EXISTING);
        return new Loader(null).openLogisimFile(circ.toFile());
    }

    static Component imem(LogisimFile f) {
        return LegacyStackFileTest.byFactory(f.getMainCircuit(), "Instruction Memory");
    }

    /** v1처럼 Instruction Memory·Data Memory가 .s를 가리키는 파일을 원조 저장 코드로 만든다. */
    Path oldFile() throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("lab"));
        Path circ = dir.resolve("cpu.circ");
        Files.copy(REF, circ);
        LogisimFile f = open(circ);
        Circuit main = f.getMainCircuit();
        imem(f).getAttributeSet().setValue(MemoryFactory.SOURCE, "prog/sum.s");
        LegacyStackFileTest.byFactory(main, "Data Memory").getAttributeSet().setValue(MemoryFactory.SOURCE,
                "prog/sum.s");
        CircuitBuilder.save(f, circ.toFile());
        String text = Files.readString(circ);
        assertEquals(2, text.split("<a name=\"source\" val=\"prog/sum.s\"/>", -1).length - 1, text);
        return circ;
    }

    @Test
    void anOldFileOpensAndSavesTheSameBytes() throws Exception {
        Path circ = oldFile();
        String before = Files.readString(circ);
        LogisimFile f = open(circ);
        assertEquals("prog/sum.s", imem(f).getAttributeSet().getValue(MemoryFactory.SOURCE), "the attribute is read");
        Path again = circ.resolveSibling("again.circ");
        CircuitBuilder.save(f, again.toFile());
        String after = Files.readString(again);
        assertEquals(CircNormalizer.normalize(before), CircNormalizer.normalize(after), "saved as before (D-006)");
        assertEquals(before.length(), after.length());
    }

    @Test
    void theMenuShowsTheFactAndAnHmxReplacesTheAttribute() throws Exception {
        Path circ = oldFile();
        LogisimFile f = open(circ);
        Component im = imem(f);
        assertEquals(List.of("Load Program...", "Load .hmx for sum.s..."), LoadSummaryTest.items(im));
        // 옛 경로를 다시 불러오면(파일이 없어도) 이미지 없이 사실과 할 일 한 줄이다
        ProgramLoader.Loaded old = ProgramLoader.read(ProgramLoader.resolveSource(circ.toFile(), "prog/sum.s"));
        assertNull(old.image);
        assertEquals(List.of(AssemblySource.FACT.get(Text.korean())), old.errors);

        // 학생이 Hallym MIPS에서 내보낸 .hmx를 고르면 contents와 source가 바뀐다(메뉴와 같은 길)
        Path hmx = Files.createDirectories(circ.resolveSibling("prog")).resolve("sum.hmx");
        Files.copy(ProgramLoadIntegrationTest.TESTS.resolve("hmx/mips/sum.hmx"), hmx);
        ProgramLoader.Loaded l = ProgramLoader.read(hmx.toFile());
        assertEquals(List.of(), l.errors);
        String source = ProgramLoader.relativeSource(circ.toFile(), hmx.toFile());
        assertEquals("prog/sum.hmx", source);
        ProgramLoader.Plan plan = ProgramLoader.plan(l, f.getCircuits(),
                new ProgramLoader.Target(f.getMainCircuit(), im), null, source);
        ProgramLoadIntegrationTest.apply(plan);
        assertEquals("prog/sum.hmx", im.getAttributeSet().getValue(MemoryFactory.SOURCE));
        assertEquals("prog/sum.hmx", LegacyStackFileTest.byFactory(f.getMainCircuit(), "Data Memory")
                .getAttributeSet().getValue(MemoryFactory.SOURCE));
        assertEquals(List.of("Load Program...", "Reload sum.hmx"), LoadSummaryTest.items(im));
        Path saved = circ.resolveSibling("replaced.circ");
        CircuitBuilder.save(f, saved.toFile());
        String text = Files.readString(saved);
        assertTrue(text.contains("<a name=\"source\" val=\"prog/sum.hmx\"/>"), text);
        assertFalse(text.contains("sum.s\""), "no .s path is left");
    }
}
