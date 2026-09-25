/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * C-02 디스어셈블: 손으로 확인한 워드들, 그리고 tests/asm의 hcs-asm 기대 출력(QtSpim 기계어)에서 원래 줄이 실제
 * 명령어인 워드마다 이름과 피연산자가 원래 줄과 같다.
 */
class MipsTextTest {
    @Test
    void knownWords() {
        Map<Integer, String> labels = new HashMap<>();
        labels.put(0x0040000c, "inner");
        assertEquals("add $t3, $t3, $t2", MipsText.disassemble(0x016a5820, 0x00400014, null));
        assertEquals("lw $t2, 0($s0)", MipsText.disassemble(0x8e0a0000, 0x00400010, null));
        assertEquals("sw $ra, 4($sp)", MipsText.disassemble(0xafbf0004, 0, null));
        assertEquals("addi $s0, $s0, 4", MipsText.disassemble(0x22100004, 0, null));
        assertEquals("addi $sp, $sp, -8", MipsText.disassemble(0x23bdfff8, 0, null));
        assertEquals("bne $t4, $zero, 0x0040000c", MipsText.disassemble(0x1580fffa, 0x00400024, null));
        assertEquals("bne $t4, $zero, inner", MipsText.disassemble(0x1580fffa, 0x00400024, MipsText.labels(labels)));
        assertEquals("j 0x00400004", MipsText.disassemble(0x08100001, 0x0040002c, null));
        assertEquals("lui $s0, 0x1001", MipsText.disassemble(0x3c101001, 0, null));
        assertEquals("ori $t0, $zero, 0", MipsText.disassemble(0x34080000, 0, null));
        assertEquals("slti $t4, $t1, 100", MipsText.disassemble(0x292c0064, 0, null));
        assertEquals("syscall", MipsText.disassemble(0x0000000c, 0, null));
        assertEquals("jr $ra", MipsText.disassemble(0x03e00008, 0, null));
        assertEquals("nop", MipsText.disassemble(0, 0, null));
        assertEquals("sll $t0, $t1, 2", MipsText.disassemble(0x00094080, 0, null));
        assertEquals("mul $v0, $a0, $v0", MipsText.disassemble(0x70821002, 0, null));
        assertEquals(".word 0xffffffff", MipsText.disassemble(0xffffffff, 0, null));
        assertEquals("beq", MipsText.mnemonic(0x11000003));
        assertEquals(null, MipsText.mnemonic(0xffffffff));
    }

    @Test
    void fieldsFollowTheFormat() {
        int add = 0x016a5820; // add $t3, $t3, $t2
        assertEquals(MipsText.Format.R, MipsText.format(add));
        assertEquals(Arrays.asList("op", "rs", "rt", "rd", "shamt", "funct"), names(MipsText.fields(add)));
        assertEquals(11, MipsText.RS.of(add));
        assertEquals(10, MipsText.RT.of(add));
        assertEquals(11, MipsText.RD.of(add));
        assertEquals(0x20, MipsText.FUNCT.of(add));
        int lw = 0x8e0a0000;
        assertEquals(MipsText.Format.I, MipsText.format(lw));
        assertEquals(Arrays.asList("op", "rs", "rt", "imm"), names(MipsText.fields(lw)));
        int j = 0x08100001;
        assertEquals(MipsText.Format.J, MipsText.format(j));
        assertEquals(Arrays.asList("op", "addr"), names(MipsText.fields(j)));
        assertEquals(0x100001, MipsText.ADDR.of(j));
    }

    static List<String> names(MipsText.Field[] fs) {
        List<String> out = new ArrayList<>();
        for (MipsText.Field f : fs) {
            out.add(f.name);
        }
        return out;
    }

    /** 원래 줄이 실제 명령어(의사 명령어가 아닌)이면 디스어셈블이 같은 이름·피연산자다. */
    @Test
    void matchesTheSourceOfRealInstructions() throws Exception {
        Set<String> pseudo = new HashSet<>(Arrays.asList("li", "la", "move", "blt", "bge", "bgt", "ble", "b",
                "neg", "not", "abs", "beqz", "bnez", "rem", "div", "subi", "seq", "sne", "sge", "sgt", "sle"));
        File dir = new File(System.getProperty("hcs.testsDir"), "asm");
        Pattern entry = Pattern.compile("\"addr\":\\s*\"0x([0-9a-f]+)\",\\s*\"word\":\\s*\"0x([0-9a-f]+)\",\\s*"
                + "\"line\":\\s*(\\d+),\\s*\"source\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        int checked = 0;
        int branches = 0;
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        assertTrue(files != null && files.length > 3);
        for (File f : files) {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (!json.contains("\"errors\": []")) {
                continue; // 어셈블 오류 예제
            }
            Map<Integer, String> labels = new HashMap<>();
            int li = json.indexOf("\"labels\"");
            if (li >= 0) {
                Matcher lm = Pattern.compile("\"([^\"]+)\":\\s*\"0x([0-9a-f]+)\"")
                        .matcher(json.substring(li + 8, json.indexOf('}', li)));
                while (lm.find()) {
                    labels.putIfAbsent((int) Long.parseLong(lm.group(2), 16), lm.group(1));
                }
            }
            Matcher m = entry.matcher(json);
            List<String[]> rows = new ArrayList<>();
            while (m.find()) {
                rows.add(new String[] {m.group(1), m.group(2), m.group(3), m.group(4)});
            }
            for (int i = 0; i < rows.size(); i++) {
                String[] r = rows.get(i);
                boolean multi = i + 1 < rows.size() && rows.get(i + 1)[2].equals(r[2])
                        || i > 0 && rows.get(i - 1)[2].equals(r[2]);
                String src = r[3].replaceAll("#.*", "").trim();
                String op = src.split("\\s+")[0];
                if (multi || src.isEmpty() || pseudo.contains(op)) {
                    continue;
                }
                String want = src.replaceAll("\\s+", " ").replaceAll(",\\s*", ", ");
                String got = MipsText.disassemble((int) Long.parseLong(r[1], 16), (int) Long.parseLong(r[0], 16), null);
                String gotOp = got.split(" ")[0];
                assertEquals(op, gotOp, f.getName() + ": " + src + " -> " + got);
                // 피연산자: 레지스터·즉값이 그대로인 줄만(라벨·16진 즉값·레지스터 번호 표기는 이름만 본다)
                if (!want.matches(".*(0x|\\$[0-9]|[A-Za-z_]\\w*$).*") && !op.startsWith("b") && !op.startsWith("j")) {
                    assertEquals(want, got, f.getName());
                }
                // 분기·점프: 라벨을 붙이면 원래 줄의 라벨이 나온다(QtSpim 인코딩, D-010)
                if ((op.startsWith("b") || op.equals("j") || op.equals("jal")) && want.matches(".*[, ][A-Za-z_]\\w*$")
                        && !want.matches(".*\\$[0-9].*")) {
                    String withLabels = MipsText.disassemble((int) Long.parseLong(r[1], 16),
                            (int) Long.parseLong(r[0], 16), MipsText.labels(labels));
                    assertEquals(want, withLabels, f.getName() + " branch target");
                    branches++;
                }
                checked++;
            }
        }
        assertTrue(checked > 20, "checked " + checked);
        assertTrue(branches >= 4, "branches with labels " + branches);
    }
}
