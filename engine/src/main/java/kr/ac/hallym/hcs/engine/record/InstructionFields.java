/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import kr.ac.hallym.hcs.mips.disasm.Disassembler;

/**
 * 명령어 워드를 필드로 나눈다(Instruction 패널, N-14, D-144). 형식 규칙과 필드 자리는 MIPS32 명세 그대로이고, 필드
 * 이름은 Hallym MIPS Inspector와 같다(opcode, rs, rt, rd, shamt, funct, immediate, target, 부동소수점의 fmt·ft·fs·fd·
 * cc·nd·tf, CP0의 CO·code·sel): 화면이 이 이름으로 색을 고르므로 두 프로그램이 같은 명령에 같은 이름과 같은 색을
 * 보인다. 명령어 이름·글은 lib-mips 디스어셈블러(D-127)의 것이다. 워드는 QtSpim이 만든 그대로이고 도구는 인코딩하지
 * 않는다(D-010). 판단하지 않고 보이기만 한다.
 */
public final class InstructionFields {
    private InstructionFields() {
    }

    /** 레지스터 이름(번호 순). */
    public static final String[] REG = {"$zero", "$at", "$v0", "$v1", "$a0", "$a1", "$a2", "$a3", "$t0", "$t1", "$t2",
        "$t3", "$t4", "$t5", "$t6", "$t7", "$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7", "$t8", "$t9", "$k0",
        "$k1", "$gp", "$sp", "$fp", "$ra"};

    /** 필드 하나: 이름, 가장 높은 비트, 가장 낮은 비트, 부호 없는 값. */
    public static final class Field {
        public final String name;
        public final int hi;
        public final int lo;
        public final int value;

        Field(String name, int hi, int lo, int word) {
            this.name = name;
            this.hi = hi;
            this.lo = lo;
            int w = hi - lo + 1;
            this.value = w >= 32 ? word : (word >>> lo) & ((1 << w) - 1);
        }

        public int width() {
            return hi - lo + 1;
        }

        /** 비트 글(높은 비트부터, 폭만큼). */
        public String bits() {
            StringBuilder sb = new StringBuilder();
            for (int i = width() - 1; i >= 0; i--) {
                sb.append((value >>> i & 1) == 0 ? '0' : '1');
            }
            return sb.toString();
        }
    }

    /** 형식: R, I, J, CP0, FR, FI(Hallym MIPS Inspector와 같은 이름). */
    public static String format(int word) {
        switch (word >>> 26) {
            case 0x00:
            case 0x1c:
                return "R";
            case 0x02:
            case 0x03:
                return "J";
            case 0x10:
                return "CP0";
            case 0x11:
                return ((word >>> 21) & 31) == 8 ? "FI" : "FR";
            default:
                return "I";
        }
    }

    /** 형식에 맞는 필드들(높은 비트부터, 32비트를 빈틈없이 덮는다). */
    public static List<Field> fields(int word) {
        List<Field> f = new ArrayList<>();
        f.add(new Field("opcode", 31, 26, word));
        switch (format(word)) {
            case "R":
                f.add(new Field("rs", 25, 21, word));
                f.add(new Field("rt", 20, 16, word));
                f.add(new Field("rd", 15, 11, word));
                f.add(new Field("shamt", 10, 6, word));
                f.add(new Field("funct", 5, 0, word));
                break;
            case "J":
                f.add(new Field("target", 25, 0, word));
                break;
            case "CP0":
                if ((word & 0x02000000) != 0) {
                    f.add(new Field("CO", 25, 25, word));
                    f.add(new Field("code", 24, 6, word));
                    f.add(new Field("funct", 5, 0, word));
                } else {
                    f.add(new Field("rs", 25, 21, word));
                    f.add(new Field("rt", 20, 16, word));
                    f.add(new Field("rd", 15, 11, word));
                    f.add(new Field("0", 10, 3, word));
                    f.add(new Field("sel", 2, 0, word));
                }
                break;
            case "FR":
                f.add(new Field("fmt", 25, 21, word));
                f.add(new Field("ft", 20, 16, word));
                f.add(new Field("fs", 15, 11, word));
                f.add(new Field("fd", 10, 6, word));
                f.add(new Field("funct", 5, 0, word));
                break;
            case "FI":
                f.add(new Field("fmt", 25, 21, word));
                f.add(new Field("cc", 20, 18, word));
                f.add(new Field("nd", 17, 17, word));
                f.add(new Field("tf", 16, 16, word));
                f.add(new Field("immediate", 15, 0, word));
                break;
            default:
                f.add(new Field("rs", 25, 21, word));
                f.add(new Field("rt", 20, 16, word));
                f.add(new Field("immediate", 15, 0, word));
                break;
        }
        return Collections.unmodifiableList(f);
    }

    /** 분기(목적지 = 분기 주소 + imm×4, D-010·D-127)인가: 디스어셈블러가 읽은 이름으로 본다. */
    static boolean isBranch(String mnemonic) {
        if (mnemonic == null) {
            return false;
        }
        switch (mnemonic) {
            case "beq":
            case "bne":
            case "blez":
            case "bgtz":
            case "bltz":
            case "bgez":
            case "bltzal":
            case "bgezal":
            case "beql":
            case "bnel":
            case "blezl":
            case "bgtzl":
            case "bltzl":
            case "bgezl":
            case "bltzall":
            case "bgezall":
                return true;
            default:
                return mnemonic.startsWith("bc1") || mnemonic.startsWith("bc2");
        }
    }

    /** 필드 값 글: immediate는 부호 있는 10진, 그 밖은 부호 없는 10진. */
    public static String value(Field f) {
        return f.name.equals("immediate") ? Integer.toString((short) f.value) : Integer.toString(f.value);
    }

    /**
     * 필드의 뜻(사실만): 레지스터 필드는 이름($t0, $f2), shamt는 자리 수, immediate는 16진(분기면 목적지 주소), target은
     * 점프 주소, opcode·funct는 디스어셈블러가 읽은 명령어 이름. symbols(주소 → 이름)가 있으면 주소 뒤에 [이름].
     */
    public static String meaning(Field f, int word, int pc, Map<Integer, String> symbols) {
        String fmt = format(word);
        String name = Disassembler.mnemonic(word);
        switch (f.name) {
            case "rs":
            case "rt":
            case "rd":
                return fmt.equals("CP0") ? "$" + f.value : REG[f.value & 31];
            case "ft":
            case "fs":
            case "fd":
                return "$f" + f.value;
            case "shamt":
                return Integer.toString(f.value);
            case "immediate":
                if (isBranch(name)) {
                    return address(pc + ((short) f.value << 2), symbols);
                }
                return String.format("0x%04x", f.value);
            case "target":
                return address((pc & 0xf0000000) | (f.value << 2), symbols);
            case "opcode": {
                int op = word >>> 26;
                if (op == 0) {
                    return "R-type";
                }
                if (op == 0x1c) {
                    return "SPECIAL2";
                }
                if (op >= 0x10 && op <= 0x13) {
                    return "COP" + (op - 0x10);
                }
                return name == null ? "" : name;
            }
            case "funct":
                return name == null ? "" : name;
            case "fmt":
                return f.value == 16 ? "single" : f.value == 17 ? "double" : f.value == 20 ? "word"
                        : Integer.toString(f.value);
            default:
                return Integer.toString(f.value);
        }
    }

    static String address(int a, Map<Integer, String> symbols) {
        String s = String.format("0x%08x", a);
        String label = symbols == null ? null : symbols.get(a);
        return label == null ? s : s + " [" + label + "]";
    }
}
