/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.util.Map;
import java.util.function.IntFunction;

/**
 * 명령어 워드를 읽는 글(C-02 사이클 표 머리, C-07 필드 색). .s가 없을 때 디스어셈블로 쓴다. MIPS32 명세의 opcode·
 * funct 표를 보고 직접 썼다(SPIM 코드는 쓰지 않는다, CLAUDE.md 규칙 2.5). 기계어는 QtSpim 그대로이고 도구는 해석만
 * 한다(D-010). 분기·점프 목적지는 워드의 필드로 계산한 주소를 보일 뿐 학생 데이터패스의 계산과 비교하지 않는다.
 */
public final class MipsText {
    private MipsText() {
    }

    public static final String[] REG = {"$zero", "$at", "$v0", "$v1", "$a0", "$a1", "$a2", "$a3", "$t0", "$t1", "$t2",
        "$t3", "$t4", "$t5", "$t6", "$t7", "$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7", "$t8", "$t9",
        "$k0", "$k1", "$gp", "$sp", "$fp", "$ra"};

    /** 명령어 형식. */
    public enum Format {
        R, I, J
    }

    /** 필드 하나: 이름, 가장 높은 비트, 가장 낮은 비트. */
    public static final class Field {
        public final String name;
        public final int hi;
        public final int lo;

        Field(String name, int hi, int lo) {
            this.name = name;
            this.hi = hi;
            this.lo = lo;
        }

        public int of(int word) {
            int w = hi - lo + 1;
            return (word >>> lo) & (w == 32 ? -1 : (1 << w) - 1);
        }
    }

    public static final Field OP = new Field("op", 31, 26);
    public static final Field RS = new Field("rs", 25, 21);
    public static final Field RT = new Field("rt", 20, 16);
    public static final Field RD = new Field("rd", 15, 11);
    public static final Field SHAMT = new Field("shamt", 10, 6);
    public static final Field FUNCT = new Field("funct", 5, 0);
    public static final Field IMM = new Field("imm", 15, 0);
    public static final Field ADDR = new Field("addr", 25, 0);

    public static Format format(int word) {
        int op = OP.of(word);
        if (op == 0 || op == 0x1c) {
            return Format.R;
        }
        return op == 2 || op == 3 ? Format.J : Format.I;
    }

    /** 형식에 맞는 필드들(높은 비트부터). */
    public static Field[] fields(int word) {
        switch (format(word)) {
        case R:
            return new Field[] {OP, RS, RT, RD, SHAMT, FUNCT};
        case J:
            return new Field[] {OP, ADDR};
        default:
            return new Field[] {OP, RS, RT, IMM};
        }
    }

    private static String reg(int n) {
        return REG[n & 31];
    }

    private static String hex(long v) {
        return String.format("0x%08x", v & 0xffffffffL);
    }

    /** 명령어 이름. 모르는 워드는 null. */
    public static String mnemonic(int word) {
        String s = disassemble(word, 0, null);
        if (s.startsWith(".word")) {
            return null;
        }
        int sp = s.indexOf(' ');
        return sp < 0 ? s : s.substring(0, sp);
    }

    /**
     * pc에 있는 word의 읽는 글. labels(주소 → 이름, null 가능)가 있으면 분기·점프 목적지에 이름을 붙인다.
     */
    public static String disassemble(int word, int pc, IntFunction<String> labels) {
        int op = OP.of(word);
        int rs = RS.of(word);
        int rt = RT.of(word);
        int rd = RD.of(word);
        int sh = SHAMT.of(word);
        int fn = FUNCT.of(word);
        short imm = (short) IMM.of(word);
        int uimm = IMM.of(word);
        if (word == 0) {
            return "nop";
        }
        switch (op) {
        case 0:
            switch (fn) {
            case 0x00:
                return "sll " + reg(rd) + ", " + reg(rt) + ", " + sh;
            case 0x02:
                return "srl " + reg(rd) + ", " + reg(rt) + ", " + sh;
            case 0x03:
                return "sra " + reg(rd) + ", " + reg(rt) + ", " + sh;
            case 0x04:
                return "sllv " + reg(rd) + ", " + reg(rt) + ", " + reg(rs);
            case 0x06:
                return "srlv " + reg(rd) + ", " + reg(rt) + ", " + reg(rs);
            case 0x07:
                return "srav " + reg(rd) + ", " + reg(rt) + ", " + reg(rs);
            case 0x08:
                return "jr " + reg(rs);
            case 0x09:
                return "jalr " + reg(rd) + ", " + reg(rs);
            case 0x0a:
                return "movz " + reg(rd) + ", " + reg(rs) + ", " + reg(rt);
            case 0x0b:
                return "movn " + reg(rd) + ", " + reg(rs) + ", " + reg(rt);
            case 0x0c:
                return "syscall";
            case 0x0d:
                return "break";
            case 0x10:
                return "mfhi " + reg(rd);
            case 0x11:
                return "mthi " + reg(rs);
            case 0x12:
                return "mflo " + reg(rd);
            case 0x13:
                return "mtlo " + reg(rs);
            case 0x18:
                return "mult " + reg(rs) + ", " + reg(rt);
            case 0x19:
                return "multu " + reg(rs) + ", " + reg(rt);
            case 0x1a:
                return "div " + reg(rs) + ", " + reg(rt);
            case 0x1b:
                return "divu " + reg(rs) + ", " + reg(rt);
            case 0x20:
                return r3("add", rd, rs, rt);
            case 0x21:
                return r3("addu", rd, rs, rt);
            case 0x22:
                return r3("sub", rd, rs, rt);
            case 0x23:
                return r3("subu", rd, rs, rt);
            case 0x24:
                return r3("and", rd, rs, rt);
            case 0x25:
                return r3("or", rd, rs, rt);
            case 0x26:
                return r3("xor", rd, rs, rt);
            case 0x27:
                return r3("nor", rd, rs, rt);
            case 0x2a:
                return r3("slt", rd, rs, rt);
            case 0x2b:
                return r3("sltu", rd, rs, rt);
            default:
                return word(word);
            }
        case 0x1c:
            if (fn == 0x02) {
                return r3("mul", rd, rs, rt);
            }
            return word(word);
        case 0x01: {
            String name = rt == 0 ? "bltz" : rt == 1 ? "bgez" : rt == 0x10 ? "bltzal" : rt == 0x11 ? "bgezal" : null;
            return name == null ? word(word) : name + " " + reg(rs) + ", " + target(pc + 4 + (imm << 2), labels);
        }
        case 0x02:
            return "j " + target(((pc + 4) & 0xf0000000) | (ADDR.of(word) << 2), labels);
        case 0x03:
            return "jal " + target(((pc + 4) & 0xf0000000) | (ADDR.of(word) << 2), labels);
        case 0x04:
            return "beq " + reg(rs) + ", " + reg(rt) + ", " + target(pc + 4 + (imm << 2), labels);
        case 0x05:
            return "bne " + reg(rs) + ", " + reg(rt) + ", " + target(pc + 4 + (imm << 2), labels);
        case 0x06:
            return "blez " + reg(rs) + ", " + target(pc + 4 + (imm << 2), labels);
        case 0x07:
            return "bgtz " + reg(rs) + ", " + target(pc + 4 + (imm << 2), labels);
        case 0x08:
            return i3("addi", rt, rs, imm);
        case 0x09:
            return i3("addiu", rt, rs, imm);
        case 0x0a:
            return i3("slti", rt, rs, imm);
        case 0x0b:
            return i3("sltiu", rt, rs, imm);
        case 0x0c:
            return i3("andi", rt, rs, uimm);
        case 0x0d:
            return i3("ori", rt, rs, uimm);
        case 0x0e:
            return i3("xori", rt, rs, uimm);
        case 0x0f:
            return "lui " + reg(rt) + ", " + String.format("0x%04x", uimm);
        case 0x20:
            return mem("lb", rt, imm, rs);
        case 0x21:
            return mem("lh", rt, imm, rs);
        case 0x23:
            return mem("lw", rt, imm, rs);
        case 0x24:
            return mem("lbu", rt, imm, rs);
        case 0x25:
            return mem("lhu", rt, imm, rs);
        case 0x28:
            return mem("sb", rt, imm, rs);
        case 0x29:
            return mem("sh", rt, imm, rs);
        case 0x2b:
            return mem("sw", rt, imm, rs);
        default:
            return word(word);
        }
    }

    private static String r3(String name, int rd, int rs, int rt) {
        return name + " " + reg(rd) + ", " + reg(rs) + ", " + reg(rt);
    }

    private static String i3(String name, int rt, int rs, int imm) {
        return name + " " + reg(rt) + ", " + reg(rs) + ", " + imm;
    }

    private static String mem(String name, int rt, int off, int rs) {
        return name + " " + reg(rt) + ", " + off + "(" + reg(rs) + ")";
    }

    private static String word(int word) {
        return ".word " + hex(word);
    }

    private static String target(long addr, IntFunction<String> labels) {
        String name = labels == null ? null : labels.apply((int) addr);
        return name != null ? name : hex(addr);
    }

    /** 이름표(주소 → 이름)를 IntFunction으로. */
    public static IntFunction<String> labels(Map<Integer, String> byAddr) {
        return byAddr == null ? null : byAddr::get;
    }
}
