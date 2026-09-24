/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

/**
 * 32비트 명령어 워드의 이름(니모닉). MIPS32 명세의 opcode·funct 표를 보고 직접 썼다. SPIM의 명령어 표는
 * 옮기지 않는다(CLAUDE.md 규칙 2.5). 정수 명령어와 syscall·CP0 일부만 다루고, 모르는 워드는 null이다.
 * 결과는 테스트에서 원본 spim의 디스어셈블과 대조한다.
 */
final class Disassembler {
    private Disassembler() {
    }

    private static final String[] SPECIAL = new String[64];
    private static final String[] REGIMM = new String[32];
    private static final String[] PRIMARY = new String[64];
    private static final String[] SPECIAL2 = new String[64];

    static {
        String[][] special = {
            {"0", "sll"}, {"2", "srl"}, {"3", "sra"}, {"4", "sllv"}, {"6", "srlv"}, {"7", "srav"},
            {"8", "jr"}, {"9", "jalr"}, {"10", "movz"}, {"11", "movn"}, {"12", "syscall"}, {"13", "break"},
            {"15", "sync"}, {"16", "mfhi"}, {"17", "mthi"}, {"18", "mflo"}, {"19", "mtlo"},
            {"24", "mult"}, {"25", "multu"}, {"26", "div"}, {"27", "divu"},
            {"32", "add"}, {"33", "addu"}, {"34", "sub"}, {"35", "subu"},
            {"36", "and"}, {"37", "or"}, {"38", "xor"}, {"39", "nor"}, {"42", "slt"}, {"43", "sltu"},
            {"48", "tge"}, {"49", "tgeu"}, {"50", "tlt"}, {"51", "tltu"}, {"52", "teq"}, {"54", "tne"},
        };
        for (String[] e : special) {
            SPECIAL[Integer.parseInt(e[0])] = e[1];
        }
        String[][] regimm = {
            {"0", "bltz"}, {"1", "bgez"}, {"2", "bltzl"}, {"3", "bgezl"},
            {"8", "tgei"}, {"9", "tgeiu"}, {"10", "tlti"}, {"11", "tltiu"}, {"12", "teqi"}, {"14", "tnei"},
            {"16", "bltzal"}, {"17", "bgezal"}, {"18", "bltzall"}, {"19", "bgezall"},
        };
        for (String[] e : regimm) {
            REGIMM[Integer.parseInt(e[0])] = e[1];
        }
        String[][] primary = {
            {"2", "j"}, {"3", "jal"}, {"4", "beq"}, {"5", "bne"}, {"6", "blez"}, {"7", "bgtz"},
            {"8", "addi"}, {"9", "addiu"}, {"10", "slti"}, {"11", "sltiu"},
            {"12", "andi"}, {"13", "ori"}, {"14", "xori"}, {"15", "lui"},
            {"20", "beql"}, {"21", "bnel"}, {"22", "blezl"}, {"23", "bgtzl"},
            {"32", "lb"}, {"33", "lh"}, {"34", "lwl"}, {"35", "lw"}, {"36", "lbu"}, {"37", "lhu"}, {"38", "lwr"},
            {"40", "sb"}, {"41", "sh"}, {"42", "swl"}, {"43", "sw"}, {"46", "swr"},
            {"48", "ll"}, {"49", "lwc1"}, {"53", "ldc1"}, {"56", "sc"}, {"57", "swc1"}, {"61", "sdc1"},
        };
        for (String[] e : primary) {
            PRIMARY[Integer.parseInt(e[0])] = e[1];
        }
        String[][] special2 = {
            {"0", "madd"}, {"1", "maddu"}, {"2", "mul"}, {"4", "msub"}, {"5", "msubu"},
            {"32", "clz"}, {"33", "clo"},
        };
        for (String[] e : special2) {
            SPECIAL2[Integer.parseInt(e[0])] = e[1];
        }
    }

    /** 워드의 명령어 이름. 모두 0인 워드는 {@code nop}(= {@code sll $0, $0, 0}). */
    static String mnemonic(int word) {
        if (word == 0) {
            return "nop";
        }
        int op = word >>> 26;
        int rs = (word >>> 21) & 31;
        int rt = (word >>> 16) & 31;
        int funct = word & 63;
        switch (op) {
            case 0:
                if (funct == 1) {
                    return (rt & 1) == 0 ? "movf" : "movt"; // MOVCI: FP 조건 코드로 이동
                }
                return SPECIAL[funct];
            case 1:
                return REGIMM[rt];
            case 16: // COP0
            case 18: // COP2
                return coprocessor(op - 16, rs, funct);
            case 28:
                return SPECIAL2[funct];
            default:
                return PRIMARY[op];
        }
    }

    /** COPz의 레지스터 이동과 COP0의 TLB·eret. */
    private static String coprocessor(int z, int rs, int funct) {
        switch (rs) {
            case 0: return "mfc" + z;
            case 2: return "cfc" + z;
            case 4: return "mtc" + z;
            case 6: return "ctc" + z;
            default: break;
        }
        if (z == 0 && rs == 16) {
            switch (funct) {
                case 1: return "tlbr";
                case 2: return "tlbwi";
                case 6: return "tlbwr";
                case 8: return "tlbp";
                case 24: return "eret";
                default: return null;
            }
        }
        return null;
    }
}
