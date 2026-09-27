/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.disasm;

import java.util.Map;
import java.util.TreeMap;

/**
 * 명령어 워드 하나를 SPIM의 Text 세그먼트 목록과 같은 글자로 보인다(Z-04, D-127). 기계어 워드 다음, {@code ;} 원래
 * 줄 주석 앞까지다. 예: {@code lw $4, 0($29)}, {@code jal 0x00400024 [main]}, {@code bne $8, $9, -4
 * [loop-0x00400030]}, {@code nop}, SPIM이 모르는 워드는 {@code <unknown instruction 0>}.
 *
 * <p>MIPS32 명세의 opcode 표를 보고 직접 썼다. SPIM의 소스와 표는 옮기지 않는다(CLAUDE.md 규칙 2.5). 내는 글은 SPIM이
 * 내는 글과 글자까지 같게 만든다(예: 모르는 워드의 {@code <unknown instruction 0>}). 같은지는 SPIM이 실제로 낸 출력
 * (tests/disasm/ 골든, {@code hcs-asm -disasm})과 한 줄씩 대조해 확인한다. 명세와 SPIM의 출력이 다른 곳(예: SPECIAL3
 * 전체가 {@code ext}, 단정도 funct 8이 {@code swxc1})은 SPIM의 출력을 따르고 그 자리에 적어 둔다.
 *
 * <p>SPIM은 같은 워드라도 어셈블한 줄과 {@code .word}로 둔 워드를 몇몇 명령에서 다르게 보인다(부동소수점 비교·조건
 * 이동, {@code movt}, {@code bc1fl} 등). 이 클래스는 학생이 QtSpim에서 보는 쪽, 곧 어셈블한 목록을 따른다.
 *
 * <p>표시 전용이다. 인코딩하지 않고 워드는 QtSpim이 만든 그대로다(D-010). 두 트랙 공용(D-125): Java 8, 의존성 없음.
 */
public final class Disassembler {
    private Disassembler() {
    }

    /** SPIM이 해석하지 못하는 워드의 글. */
    public static final String UNKNOWN = "<unknown instruction 0>";

    /** 피연산자 모양. SPIM 목록의 쉼표·공백 그대로다. */
    private enum Form {
        NO_OPERANDS, // name
        RD_RS_RT, // $rd, $rs, $rt
        RD_RT_SA, // $rd, $rt, sa (워드 전체가 0이면 nop)
        RD_RT_RS, // $rd, $rt, $rs
        RS, // $rs
        RD, // $rd
        RD_RS, // $rd, $rs
        RS_RT, // $rs, $rt
        RT_RD, // $rt, $rd
        RT_FRD, // $rt, $f<rd>
        RD_RS_CC, // $rd, $rs, cc
        RT_RS_IMM, // $rt, $rs, imm
        RS_IMM, // $rs, imm
        RT_IMM, // $rt, imm
        RT_OFFSET_BASE, // $rt, imm($rs)
        FRT_OFFSET_BASE, // $f<rt>, imm($rs)
        RS_RT_DISP, // $rs, $rt, disp   (disp: 바이트 변위, disp() 참고)
        RS_DISP, // $rs disp         (SPIM은 쉼표를 찍지 않는다)
        CC_DISP, // <name><cc> disp   (조건 코드가 이름에 붙는다)
        TARGET28, // 0x<index × 4>    (위 4비트 없이 28비트)
        FD_FS, // $fd, $fs
        FD_FS_FT, // $fd, $fs, $ft
        CC_FS_FT, // $fs, $ft  또는 cc, $fs, $ft
        FD_FT, // $fd, $ft   (.ps 비교: SPIM이 어셈블하지 않아 워드 해석만 있다)
        FD_FS_CC, // $fd, $fs, cc
        F0_FD_CC // $f0, $fd, cc (.ps 조건 이동: 워드 해석만 있다)
    }

    private static final class Op {
        final String name;
        final Form form;

        Op(String name, Form form) {
            this.name = name;
            this.form = form;
        }
    }

    private static final Op[] PRIMARY = new Op[64];
    private static final Op[] SPECIAL = new Op[64];
    private static final Op[] REGIMM = new Op[32];
    private static final Op[] SPECIAL2 = new Op[64];
    private static final Op[] COP1_S = new Op[64];
    private static final Op[] COP1_D = new Op[64];
    private static final Op[] COP1_PS1 = new Op[64]; // fmt 19: SPIM이 add.ps·sub.ps·abs.ps·c.*.ps를 둔 자리
    private static final Op[] COP1_PS2 = new Op[64]; // fmt 22: 명세의 PS
    private static final String[] COND = {"f", "un", "eq", "ueq", "olt", "ult", "ole", "ule", "sf", "ngle", "seq",
        "ngl", "lt", "nge", "le", "ngt"};

    private static void put(Op[] table, int index, String name, Form form) {
        table[index] = new Op(name, form);
    }

    static {
        put(PRIMARY, 2, "j", Form.TARGET28);
        put(PRIMARY, 3, "jal", Form.TARGET28);
        put(PRIMARY, 4, "beq", Form.RS_RT_DISP);
        put(PRIMARY, 5, "bne", Form.RS_RT_DISP);
        put(PRIMARY, 6, "blez", Form.RS_DISP);
        put(PRIMARY, 7, "bgtz", Form.RS_DISP);
        String[] alu = {"addi", "addiu", "slti", "sltiu", "andi", "ori", "xori"};
        for (int i = 0; i < alu.length; i++) {
            put(PRIMARY, 8 + i, alu[i], Form.RT_RS_IMM);
        }
        put(PRIMARY, 15, "lui", Form.RT_IMM);
        put(PRIMARY, 20, "beql", Form.RS_RT_DISP);
        put(PRIMARY, 21, "bnel", Form.RS_RT_DISP);
        put(PRIMARY, 22, "blezl", Form.RS_DISP);
        put(PRIMARY, 23, "bgtzl", Form.RS_DISP);
        String[] mem = {"lb", "lh", "lwl", "lw", "lbu", "lhu", "lwr", null, "sb", "sh", "swl", "sw", null, null, "swr"};
        for (int i = 0; i < mem.length; i++) {
            if (mem[i] != null) {
                put(PRIMARY, 32 + i, mem[i], Form.RT_OFFSET_BASE);
            }
        }
        put(PRIMARY, 47, "cache", Form.RT_RS_IMM); // SPIM: $rt, $rs, imm
        put(PRIMARY, 48, "ll", Form.RT_OFFSET_BASE);
        put(PRIMARY, 49, "lwc1", Form.FRT_OFFSET_BASE);
        put(PRIMARY, 50, "lwc2", Form.RT_OFFSET_BASE);
        put(PRIMARY, 51, "pref", Form.RT_RS_IMM); // SPIM: $rt, $rs, imm
        put(PRIMARY, 53, "ldc1", Form.FRT_OFFSET_BASE);
        put(PRIMARY, 54, "ldc2", Form.RT_OFFSET_BASE);
        put(PRIMARY, 56, "sc", Form.RT_OFFSET_BASE);
        put(PRIMARY, 57, "swc1", Form.FRT_OFFSET_BASE);
        put(PRIMARY, 58, "swc2", Form.RT_OFFSET_BASE);
        put(PRIMARY, 61, "sdc1", Form.FRT_OFFSET_BASE);
        put(PRIMARY, 62, "sdc2", Form.RT_OFFSET_BASE);

        put(SPECIAL, 0, "sll", Form.RD_RT_SA);
        put(SPECIAL, 2, "srl", Form.RD_RT_SA);
        put(SPECIAL, 3, "sra", Form.RD_RT_SA);
        put(SPECIAL, 4, "sllv", Form.RD_RT_RS);
        put(SPECIAL, 6, "srlv", Form.RD_RT_RS);
        put(SPECIAL, 7, "srav", Form.RD_RT_RS);
        put(SPECIAL, 8, "jr", Form.RS);
        put(SPECIAL, 9, "jalr", Form.RD_RS);
        put(SPECIAL, 10, "movz", Form.RD_RS_RT);
        put(SPECIAL, 11, "movn", Form.RD_RS_RT);
        put(SPECIAL, 12, "syscall", Form.NO_OPERANDS);
        put(SPECIAL, 13, "break", Form.NO_OPERANDS);
        put(SPECIAL, 15, "sync", Form.NO_OPERANDS);
        put(SPECIAL, 16, "mfhi", Form.RD);
        put(SPECIAL, 17, "mthi", Form.RS);
        put(SPECIAL, 18, "mflo", Form.RD);
        put(SPECIAL, 19, "mtlo", Form.RS);
        String[] muldiv = {"mult", "multu", "div", "divu"};
        for (int i = 0; i < muldiv.length; i++) {
            put(SPECIAL, 24 + i, muldiv[i], Form.RS_RT);
        }
        String[] r3 = {"add", "addu", "sub", "subu", "and", "or", "xor", "nor", null, null, "slt", "sltu"};
        for (int i = 0; i < r3.length; i++) {
            if (r3[i] != null) {
                put(SPECIAL, 32 + i, r3[i], Form.RD_RS_RT);
            }
        }
        String[] traps = {"tge", "tgeu", "tlt", "tltu", "teq", null, "tne"};
        for (int i = 0; i < traps.length; i++) {
            if (traps[i] != null) {
                put(SPECIAL, 48 + i, traps[i], Form.RS_RT);
            }
        }

        String[] regimm = {"bltz", "bgez", "bltzl", "bgezl", null, null, null, null, "tgei", "tgeiu", "tlti", "tltiu",
            "teqi", null, "tnei", null, "bltzal", "bgezal", "bltzall", "bgezall"};
        for (int i = 0; i < regimm.length; i++) {
            if (regimm[i] != null) {
                put(REGIMM, i, regimm[i], i >= 8 && i < 16 ? Form.RS_IMM : Form.RS_DISP);
            }
        }
        put(REGIMM, 20, "synci", Form.RT_RS_IMM); // SPIM: $rt, $rs, imm

        put(SPECIAL2, 0, "madd", Form.RS_RT);
        put(SPECIAL2, 1, "maddu", Form.RS_RT);
        put(SPECIAL2, 2, "mul", Form.RD_RS_RT);
        put(SPECIAL2, 4, "msub", Form.RS_RT);
        put(SPECIAL2, 5, "msubu", Form.RS_RT);
        put(SPECIAL2, 32, "clz", Form.RD_RS_RT); // SPIM: $rd, $rs, $rt
        put(SPECIAL2, 33, "clo", Form.RD_RS_RT);
        put(SPECIAL2, 63, "sdbbp", Form.NO_OPERANDS);

        // 단정도(S)·배정도(D) 공통
        Op[][] sd = {COP1_S, COP1_D};
        String[] fmt = {"s", "d"};
        for (int k = 0; k < 2; k++) {
            String f = "." + fmt[k];
            put(sd[k], 0, "add" + f, Form.FD_FS_FT);
            put(sd[k], 1, "sub" + f, Form.FD_FS_FT);
            put(sd[k], 2, "mul" + f, Form.FD_FS_FT);
            put(sd[k], 3, "div" + f, Form.FD_FS_FT);
            String[] unary = {"sqrt", "abs", "mov", "neg"};
            for (int i = 0; i < unary.length; i++) {
                put(sd[k], 4 + i, unary[i] + f, Form.FD_FS);
            }
            String[] conv = {"round.l", "trunc.l", "ceil.l", "floor.l", "round.w", "trunc.w", "ceil.w", "floor.w"};
            for (int i = 0; i < conv.length; i++) {
                put(sd[k], 8 + i, conv[i] + f, Form.FD_FS);
            }
            put(sd[k], 17, "movf" + f, Form.FD_FS_CC); // tf 비트가 1이면 movt(cop1)
            put(sd[k], 18, "movz" + f, Form.FD_FS_CC);
            put(sd[k], 19, "movn" + f, Form.FD_FS_CC);
            put(sd[k], 21, "recip" + f, Form.FD_FS);
            put(sd[k], 22, "rsqrt" + f, Form.FD_FS);
            for (int c = 0; c < 16; c++) {
                put(sd[k], 48 + c, "c." + COND[c] + f, Form.CC_FS_FT);
            }
        }
        // SPIM은 단정도 funct 8·9·13을 인덱스 저장 명령 이름으로 보인다. 13은 SPIM이 trunc.w.s로 어셈블하므로 그 이름이다.
        put(COP1_S, 8, "swxc1", Form.FD_FS_FT);
        put(COP1_S, 9, "sdxc1", Form.FD_FS_FT);
        put(COP1_S, 13, "trunc.w.s", Form.FD_FS);
        put(COP1_S, 33, "cvt.d.s", Form.FD_FS);
        put(COP1_S, 36, "cvt.w.s", Form.FD_FS);
        put(COP1_S, 37, "cvt.l.s", Form.FD_FS);
        put(COP1_S, 38, "cvt.ps.s", Form.FD_FS);
        put(COP1_D, 32, "cvt.s.d", Form.FD_FS);
        put(COP1_D, 33, "cvt.d.w", Form.FD_FS); // SPIM: fmt D 자리
        put(COP1_D, 36, "cvt.w.d", Form.FD_FS);
        put(COP1_D, 37, "cvt.l.d", Form.FD_FS);

        put(COP1_PS1, 0, "add.ps", Form.FD_FS_FT);
        put(COP1_PS1, 1, "sub.ps", Form.FD_FS_FT);
        put(COP1_PS1, 5, "abs.ps", Form.FD_FS);
        for (int c = 0; c < 16; c++) {
            put(COP1_PS1, 48 + c, "c." + COND[c] + ".ps", Form.FD_FT);
        }
        put(COP1_PS2, 2, "mul.ps", Form.FD_FS_FT);
        put(COP1_PS2, 6, "mov.ps", Form.FD_FS);
        put(COP1_PS2, 7, "neg.ps", Form.FD_FS);
        put(COP1_PS2, 17, "movf.ps", Form.F0_FD_CC);
        put(COP1_PS2, 18, "movz.ps", Form.F0_FD_CC);
        put(COP1_PS2, 19, "movn.ps", Form.F0_FD_CC);
        put(COP1_PS2, 32, "cvt.s.pu", Form.FD_FS);
        put(COP1_PS2, 36, "cvt.s.pl", Form.FD_FS);
        String[] pair = {"pll", "plu", "pul", "puu"};
        for (int i = 0; i < pair.length; i++) {
            put(COP1_PS2, 44 + i, pair[i] + ".ps", Form.FD_FS_FT);
        }
    }

    private static final Op SPECIAL3_EXT = new Op("ext", Form.FD_FS); // SPIM: SPECIAL3 전체
    private static final Op COP2_OP = new Op("cop2", Form.TARGET28);
    private static final Op CVT_S_W = new Op("cvt.s.w", Form.FD_FS);

    private static Op decode(int word) {
        int op = word >>> 26;
        int rs = (word >>> 21) & 31;
        int rt = (word >>> 16) & 31;
        int fn = word & 63;
        switch (op) {
        case 0:
            if (fn == 1) {
                return new Op((rt & 1) == 0 ? "movf" : "movt", Form.RD_RS_CC);
            }
            return SPECIAL[fn];
        case 1:
            return REGIMM[rt];
        case 16:
            return cop0(rs, fn & 31);
        case 17:
            return cop1(rs, rt, fn);
        case 18:
            return cop2(rs, rt);
        case 28:
            return SPECIAL2[fn];
        case 31:
            return SPECIAL3_EXT;
        default:
            return PRIMARY[op];
        }
    }

    /** COP0: rs와 funct의 아래 5비트로 가른다. */
    private static Op cop0(int rs, int fn5) {
        if (rs == 16) {
            switch (fn5) {
            case 1: return new Op("tlbr", Form.NO_OPERANDS);
            case 2: return new Op("tlbwi", Form.NO_OPERANDS);
            case 6: return new Op("tlbwr", Form.NO_OPERANDS);
            case 8: return new Op("tlbp", Form.NO_OPERANDS);
            case 16: return new Op("rfe", Form.NO_OPERANDS);
            case 24: return new Op("eret", Form.NO_OPERANDS);
            case 31: return new Op("deret", Form.NO_OPERANDS);
            default: return null;
            }
        }
        if (fn5 != 0) {
            return null;
        }
        switch (rs) {
        case 0: return new Op("mfc0", Form.RT_RD);
        case 2: return new Op("cfc0", Form.RT_FRD);
        case 4: return new Op("mtc0", Form.RT_RD);
        case 6: return new Op("ctc0", Form.RT_FRD);
        case 10: return new Op("rdpgpr", Form.RT_RD);
        case 14: return new Op("wrpgpr", Form.RT_RD);
        default: return null;
        }
    }

    private static Op cop1(int rs, int rt, int fn) {
        switch (rs) {
        case 8:
            return new Op(branchName("bc1", rt), Form.CC_DISP);
        case 16:
        case 17:
            if (fn == 17 && (rt & 1) != 0) { // tf 비트: movt.fmt
                return new Op(rs == 16 ? "movt.s" : "movt.d", Form.FD_FS_CC);
            }
            return (rs == 16 ? COP1_S : COP1_D)[fn];
        case 19:
            return COP1_PS1[fn];
        case 20:
            return fn == 32 ? CVT_S_W : null;
        case 22:
            return COP1_PS2[fn];
        default:
            break;
        }
        if (fn != 0) {
            return null;
        }
        switch (rs) {
        case 0: return new Op("mfc1", Form.RT_FRD);
        case 2: return new Op("cfc1", Form.RT_FRD);
        case 3: return new Op("mfhc1", Form.RT_FRD);
        case 4: return new Op("mtc1", Form.RT_FRD);
        case 6: return new Op("ctc1", Form.RT_FRD);
        case 7: return new Op("mthc1", Form.RT_FRD);
        default: return null;
        }
    }

    /** COP2: rs만 본다. */
    private static Op cop2(int rs, int rt) {
        switch (rs) {
        case 0: return new Op("mfc2", Form.RT_RD);
        case 2: return new Op("cfc2", Form.RT_FRD);
        case 3: return new Op("mfhc2", Form.RT_RD);
        case 4: return new Op("mtc2", Form.RT_RD);
        case 6: return new Op("ctc2", Form.RT_FRD);
        case 7: return new Op("mthc2", Form.RT_RD);
        case 8: return new Op(branchName("bc2", rt), Form.CC_DISP);
        case 16: return COP2_OP;
        default: return null;
        }
    }

    /** bc1f·bc1t·bc1fl·bc1tl: rt의 비트 0이 참/거짓, 비트 1이 likely. */
    private static String branchName(String prefix, int rt) {
        return prefix + ((rt & 1) == 0 ? "f" : "t") + ((rt & 2) == 0 ? "" : "l");
    }

    /** 명령어 이름(SPIM 목록의 이름, 조건 코드는 빼고). SPIM이 해석하지 못하는 워드는 null. */
    public static String mnemonic(int word) {
        Op o = decode(word);
        if (o == null) {
            return null;
        }
        return o.form == Form.RD_RT_SA && word == 0 ? "nop" : o.name;
    }

    /** 이름표 없이 {@link #text(int, int, Map)}. */
    public static String text(int word, int address) {
        return text(word, address, null);
    }

    /**
     * address에 있는 word의 글. symbols(주소 → 이름, null 가능)에 분기·점프 목적지가 있으면 SPIM처럼 {@code [이름]}(점프),
     * {@code [이름-0x<분기 주소>]}(분기)를 붙인다. 분기 목적지는 QtSpim 기본 설정(지연 분기 끔)의 인코딩대로 분기 주소 +
     * imm×4다(D-010).
     */
    public static String text(int word, int address, Map<Integer, String> symbols) {
        Op o = decode(word);
        if (o == null) {
            return UNKNOWN;
        }
        int rs = (word >>> 21) & 31;
        int rt = (word >>> 16) & 31;
        int rd = (word >>> 11) & 31;
        int sa = (word >>> 6) & 31;
        int imm = (short) word;
        StringBuilder sb = new StringBuilder(o.name);
        String target = null;
        switch (o.form) {
        case NO_OPERANDS:
            break;
        case RD_RS_RT:
            sb.append(" $").append(rd).append(", $").append(rs).append(", $").append(rt);
            break;
        case RD_RT_SA:
            if (word == 0) {
                return "nop";
            }
            sb.append(" $").append(rd).append(", $").append(rt).append(", ").append(sa);
            break;
        case RD_RT_RS:
            sb.append(" $").append(rd).append(", $").append(rt).append(", $").append(rs);
            break;
        case RS:
            sb.append(" $").append(rs);
            break;
        case RD:
            sb.append(" $").append(rd);
            break;
        case RD_RS:
            sb.append(" $").append(rd).append(", $").append(rs);
            break;
        case RS_RT:
            sb.append(" $").append(rs).append(", $").append(rt);
            break;
        case RT_RD:
            sb.append(" $").append(rt).append(", $").append(rd);
            break;
        case RT_FRD:
            sb.append(" $").append(rt).append(", $f").append(rd);
            break;
        case RD_RS_CC:
            sb.append(" $").append(rd).append(", $").append(rs).append(", ").append(rt >> 2);
            break;
        case RT_RS_IMM:
            sb.append(" $").append(rt).append(", $").append(rs).append(", ").append(imm);
            break;
        case RS_IMM:
            sb.append(" $").append(rs).append(", ").append(imm);
            break;
        case RT_IMM:
            sb.append(" $").append(rt).append(", ").append(imm);
            break;
        case RT_OFFSET_BASE:
            sb.append(" $").append(rt).append(", ").append(imm).append("($").append(rs).append(')');
            break;
        case FRT_OFFSET_BASE:
            sb.append(" $f").append(rt).append(", ").append(imm).append("($").append(rs).append(')');
            break;
        case RS_RT_DISP:
            sb.append(" $").append(rs).append(", $").append(rt).append(", ").append(disp(imm));
            target = branchBracket(symbols, address, imm);
            break;
        case RS_DISP:
            sb.append(" $").append(rs).append(' ').append(disp(imm));
            target = branchBracket(symbols, address, imm);
            break;
        case CC_DISP:
            sb.append(rt >> 2).append(' ').append(disp(imm));
            target = branchBracket(symbols, address, imm);
            break;
        case TARGET28: {
            int index = word & 0x03ffffff;
            sb.append(' ').append(hex(index << 2));
            if (o != COP2_OP && symbols != null) {
                target = symbols.get(((address + 4) & 0xf0000000) | (index << 2));
            }
            break;
        }
        case FD_FS:
            sb.append(" $f").append(sa).append(", $f").append(rd);
            break;
        case FD_FS_FT:
            sb.append(" $f").append(sa).append(", $f").append(rd).append(", $f").append(rt);
            break;
        case CC_FS_FT:
            sb.append(' ');
            if (sa != 0) {
                sb.append(sa >> 2).append(", ");
            }
            sb.append("$f").append(rd).append(", $f").append(rt);
            break;
        case FD_FT:
            sb.append(" $f").append(sa).append(", $f").append(rt);
            break;
        case FD_FS_CC:
            sb.append(" $f").append(sa).append(", $f").append(rd).append(", ").append(rt >> 2);
            break;
        case F0_FD_CC:
            sb.append(" $f0, $f").append(sa).append(", ").append(rt >> 2);
            break;
        default:
            throw new AssertionError(o.form);
        }
        if (target != null) {
            sb.append(" [").append(target).append(']');
        }
        return sb.toString();
    }

    /**
     * SPIM이 보이는 분기 변위: imm×4에서 비트 15가 1이면 위 16비트를 1로 채운다. 그래서 0x7fff는 -4, 0x2000 이상 양수 중 일부는
     * 음수로 보인다(SPIM 출력 그대로). 목적지 주소는 이와 상관없이 분기 주소 + imm×4다.
     */
    static int disp(int imm) {
        int d = imm << 2;
        return (d & 0x8000) != 0 ? d | 0xffff0000 : d;
    }

    private static String branchBracket(Map<Integer, String> symbols, int address, int imm) {
        if (symbols == null) {
            return null;
        }
        String name = symbols.get(address + imm * 4);
        return name == null ? null : name + "-" + hex(address);
    }

    private static String hex(int v) {
        String h = Integer.toHexString(v);
        StringBuilder sb = new StringBuilder("0x");
        for (int i = h.length(); i < 8; i++) {
            sb.append('0');
        }
        return sb.append(h).toString();
    }

    /**
     * 이름 → 주소 표(hcs-asm의 labels)를 주소 → 이름으로. 한 주소에 이름이 여럿이면 사전순으로 앞선 이름을 쓴다(SPIM은
     * 원래 줄에 쓴 이름을 보이지만 워드만으로는 알 수 없다).
     */
    public static Map<Integer, String> byAddress(Map<String, ? extends Number> labels) {
        Map<Integer, String> out = new TreeMap<Integer, String>();
        if (labels == null) {
            return out;
        }
        for (Map.Entry<String, ? extends Number> e : labels.entrySet()) {
            Integer a = e.getValue().intValue();
            String had = out.get(a);
            if (had == null || e.getKey().compareTo(had) < 0) {
                out.put(a, e.getKey());
            }
        }
        return out;
    }
}
