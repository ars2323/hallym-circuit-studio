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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 디스어셈블러의 글이 원본 spim 명령줄의 디스어셈블({@code spim -noexception -dump}의 text.asm)과 같다(D-127). hcs-asm
 * 골든(tests/disasm)과 다른 두 번째 SPIM 출력이다. vendor/spim이 있을 때 뽑아 tests/spim-oracle/dump/에 굳혔다(D-141).
 * 원래 줄의 식({@code [m3]} 등)은 워드만으로 알 수 없어 그 앞까지 비교한다.
 */
class SpimDumpListingTest {
    static final Path DIR = Paths.get(System.getProperty("hcs.testsDir", "../tests"), "spim-oracle", "dump");

    @Test
    void disassemblyMatchesTheOriginalSpimDump() throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(DIR)) {
            files = s.filter(f -> f.toString().endsWith(".txt")).sorted().collect(Collectors.toList());
        }
        assertTrue(files.size() >= 10, "listings in " + DIR.toAbsolutePath() + ": " + files.size());
        int compared = 0;
        int floating = 0;
        List<String> failures = new ArrayList<>();
        for (Path f : files) {
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            assertTrue(lines.get(0).startsWith("# spim -noexception -dump -file "), f + ": " + lines.get(0));
            for (String l : lines) {
                if (l.startsWith("#")) {
                    continue;
                }
                String[] t = l.split(" ", 3);
                int addr = (int) Long.parseLong(t[0], 16);
                int word = (int) Long.parseLong(t[1], 16);
                String spim = t[2];
                int bracket = spim.indexOf(" [");
                String want = bracket < 0 ? spim : spim.substring(0, bracket);
                String ours = Disassembler.text(word, addr);
                if (!want.equals(ours) && failures.size() < 40) {
                    failures.add(f.getFileName() + " " + l + ": ours \"" + ours + "\"");
                }
                compared += 1;
                if ((word >>> 26) == 0x11) {
                    floating += 1;
                }
            }
        }
        assertEquals(List.of(), failures);
        assertTrue(compared > 5000, "compared " + compared);
        assertTrue(floating > 100, "floating " + floating); // tt.core.s의 부동소수점 명령도 같다
    }
}
