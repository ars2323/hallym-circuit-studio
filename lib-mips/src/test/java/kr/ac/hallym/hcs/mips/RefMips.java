/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 우리 테스트용 참조 single-cycle MIPS 회로(#16). 원조 2.7.1 부품과 MIPS 라이브러리로 만든다. 학생 과제의 정답이
 * 아니다. MIPS 부품과 hcs-asm이 SPIM 실행과 같은 결과를 내는지 확인하는 데 쓴다.
 *
 * <p>기계어는 QtSpim 그대로이므로(D-010) 분기 가산기는 PC 기준(PC + imm×4)이다. 연결은 모두 라벨 터널이고, 부품은
 * 서로 포트가 겹치지 않게 넓은 격자에 놓는다. 레지스터 파일은 main 회로에 펼쳐 두어 테스트가 값을 바로 읽는다.
 *
 * <p>명령어: add addu sub subu and or xor nor slt sltu sll srl sra sllv srlv srav jr syscall, mul(SPECIAL2),
 * addi addiu slti sltiu andi ori xori lui lw sw beq bne bgez bltz, j jal. SPIM 의사 명령어(li, la, move, blt,
 * bge, b 등)는 이것들로 펼쳐진다.
 */
final class RefMips {
    // 제어 ROM 출력 비트
    static final int REG_DST = 0;    // 2비트: 0 rt, 1 rd, 2 $ra
    static final int ALU_SRC = 2;
    static final int MEM_TO_REG = 3; // 2비트: 0 ALU, 1 메모리, 2 PC+4, 3 lui
    static final int REG_WRITE = 5;
    static final int MEM_READ = 6;
    static final int MEM_WRITE = 7;
    static final int BEQ = 8;
    static final int BNE = 9;
    static final int JUMP = 10;
    static final int ALU_OP = 11;    // 3비트: 0 add, 1 sub, 2 funct, 3 and, 4 or, 5 xor, 6 slt, 7 sltu
    static final int ZERO_EXT = 14;
    static final int SPECIAL2 = 15;
    static final int REGIMM = 16;
    static final int CTRL_WIDTH = 17;

    // ALU 제어 ROM 출력
    static final int F_ADD = 0, F_SUB = 1, F_AND = 2, F_OR = 3, F_XOR = 4, F_NOR = 5, F_SLT = 6, F_SLTU = 7,
            F_SLL = 8, F_SRL = 9, F_SRA = 10, F_MUL = 11;
    static final int SHIFT_VAR = 4, JR = 5, SYSCALL = 6, NO_WRITE = 7;

    private final CircuitBuilder b;
    private final Library mips;
    private int cell;

    /** 레지스터 1~31(0번은 상수), Console, 메모리. 테스트가 읽는다. */
    final Component[] regs = new Component[32];
    Component imem;
    Component dmem;
    Component stack;
    Component console;

    private RefMips(CircuitBuilder b, Library mips) {
        this.b = b;
        this.mips = mips;
    }

    static RefMips build(CircuitBuilder b, Library mips) {
        RefMips m = new RefMips(b, mips);
        m.build();
        return m;
    }

    private int[] next() {
        int x = 400 + (cell % 12) * 600;
        int y = 400 + (cell / 12) * 800;
        cell += 1;
        return new int[] {x, y};
    }

    private Component add(String lib, String name, String... attrs) {
        int[] p = next();
        return b.add(lib, name, p[0], p[1], attrs);
    }

    private Component addMips(String name) {
        int[] p = next();
        return b.add(mips, name, p[0], p[1]);
    }

    private void t(Component c, int port, String label) {
        b.tunnel(c, port, label);
    }

    private void konst(String label, int width, long value) {
        int[] p = next();
        b.constant(label, width, (int) value, p[0], p[1]);
    }

    /** combined(폭 incoming)를 나누거나 모은다. groups[i]는 비트 i가 속한 팔, labels는 팔마다 터널. */
    private void split(String combined, int incoming, int[] groups, String... labels) {
        List<String> attrs = new ArrayList<String>();
        attrs.add("incoming");
        attrs.add(Integer.toString(incoming));
        attrs.add("fanout");
        attrs.add(Integer.toString(labels.length));
        for (int i = 0; i < incoming; i += 1) {
            attrs.add("bit" + i);
            attrs.add(Integer.toString(groups[i]));
        }
        Component s = add("Wiring", "Splitter", attrs.toArray(new String[0]));
        t(s, 0, combined);
        for (int i = 0; i < labels.length; i += 1) {
            t(s, 1 + i, labels[i]);
        }
    }

    /** 비트 범위로 나누기: bounds는 각 팔의 비트 수(아래 비트부터). */
    private void fields(String combined, int incoming, int[] widths, String... labels) {
        int[] groups = new int[incoming];
        int bit = 0;
        for (int g = 0; g < widths.length; g += 1) {
            for (int i = 0; i < widths[g]; i += 1) {
                groups[bit++] = g;
            }
        }
        split(combined, incoming, groups, labels);
    }

    private void mux(String out, String sel, int selBits, int width, String... inputs) {
        Component m = add("Plexers", "Multiplexer", "select", Integer.toString(selBits),
                "width", Integer.toString(width), "enable", "false");
        int n = 1 << selBits;
        for (int i = 0; i < n; i += 1) {
            t(m, i, inputs[i]);
        }
        t(m, n, sel);
        t(m, n + 1, out);
    }

    private void gate(String type, String out, int width, String... ins) {
        Component g = add("Gates", type, "width", Integer.toString(width), "inputs", Integer.toString(ins.length));
        t(g, 0, out);
        for (int i = 0; i < ins.length; i += 1) {
            t(g, 1 + i, ins[i]);
        }
    }

    private void not(String out, String in, int width) {
        Component g = add("Gates", "NOT Gate", "width", Integer.toString(width));
        t(g, 0, out);
        t(g, 1, in);
    }

    private void arith(String name, String out, String a, String bIn, String... attrs) {
        Component c = add("Arithmetic", name, attrs.length == 0 ? new String[] {"width", "32"} : attrs);
        t(c, 0, a);
        t(c, 1, bIn);
        t(c, 2, out);
    }

    /** Comparator: port 2 gt, 3 eq, 4 lt. */
    private void compare(String a, String bIn, String mode, int port, String out) {
        Component c = add("Arithmetic", "Comparator", "width", "32", "mode", mode);
        t(c, 0, a);
        t(c, 1, bIn);
        t(c, port, out);
    }

    private void extend(String out, String in, int from, int to, String type) {
        Component c = add("Wiring", "Bit Extender", "in_width", Integer.toString(from),
                "out_width", Integer.toString(to), "type", type);
        t(c, 0, out);
        t(c, 1, in);
    }

    private void shift(String out, String data, String dist, String kind) {
        Component c = add("Arithmetic", "Shifter", "width", "32", "shift", kind);
        t(c, 0, data);
        t(c, 1, dist);
        t(c, 2, out);
    }

    private void rom(String out, String addr, int addrBits, int dataBits, long[] values) {
        StringBuilder sb = new StringBuilder("addr/data: " + addrBits + " " + dataBits + "\n");
        for (int i = 0; i < values.length; i += 1) {
            sb.append(Long.toHexString(values[i])).append(i % 16 == 15 ? "\n" : " ");
        }
        Component r = add("Memory", "ROM", "addrWidth", Integer.toString(addrBits),
                "dataWidth", Integer.toString(dataBits), "contents", sb.toString());
        t(r, 0, out);
        t(r, 1, addr);
        t(r, 2, "one");
    }

    static long ctrl(int regDst, int aluSrc, int memToReg, int regWrite, int memRead, int memWrite,
            int beq, int bne, int jump, int aluOp, int zeroExt, int special2, int regimm) {
        return (long) regDst << REG_DST | (long) aluSrc << ALU_SRC | (long) memToReg << MEM_TO_REG
                | (long) regWrite << REG_WRITE | (long) memRead << MEM_READ | (long) memWrite << MEM_WRITE
                | (long) beq << BEQ | (long) bne << BNE | (long) jump << JUMP | (long) aluOp << ALU_OP
                | (long) zeroExt << ZERO_EXT | (long) special2 << SPECIAL2 | (long) regimm << REGIMM;
    }

    /** opcode → 제어 신호. */
    static long[] controlTable() {
        long[] c = new long[64];
        c[0x00] = ctrl(1, 0, 0, 1, 0, 0, 0, 0, 0, 2, 0, 0, 0);  // R형
        c[0x01] = ctrl(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);  // REGIMM bltz/bgez
        c[0x02] = ctrl(0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0);  // j
        c[0x03] = ctrl(2, 0, 2, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0);  // jal
        c[0x04] = ctrl(0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0);  // beq
        c[0x05] = ctrl(0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0);  // bne
        c[0x08] = ctrl(0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0);  // addi
        c[0x09] = ctrl(0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0);  // addiu
        c[0x0a] = ctrl(0, 1, 0, 1, 0, 0, 0, 0, 0, 6, 0, 0, 0);  // slti
        c[0x0b] = ctrl(0, 1, 0, 1, 0, 0, 0, 0, 0, 7, 0, 0, 0);  // sltiu
        c[0x0c] = ctrl(0, 1, 0, 1, 0, 0, 0, 0, 0, 3, 1, 0, 0);  // andi
        c[0x0d] = ctrl(0, 1, 0, 1, 0, 0, 0, 0, 0, 4, 1, 0, 0);  // ori
        c[0x0e] = ctrl(0, 1, 0, 1, 0, 0, 0, 0, 0, 5, 1, 0, 0);  // xori
        c[0x0f] = ctrl(0, 0, 3, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0);  // lui
        c[0x1c] = ctrl(1, 0, 0, 1, 0, 0, 0, 0, 0, 2, 0, 1, 0);  // SPECIAL2 (mul)
        c[0x23] = ctrl(0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0);  // lw
        c[0x2b] = ctrl(0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0);  // sw
        return c;
    }

    /** {Special2, ALUOp, funct} → {NoWrite, Syscall, JR, ShiftVar, ALU 기능(4비트)}. */
    static long[] aluControlTable() {
        long[] t = new long[1024];
        for (int a = 0; a < 1024; a += 1) {
            int funct = a & 63;
            int op = (a >> 6) & 7;
            boolean special2 = (a >> 9) != 0;
            long v;
            if (special2) {
                v = funct == 2 ? F_MUL : 1L << NO_WRITE;
            } else {
                switch (op) {
                    case 0: v = F_ADD; break;
                    case 1: v = F_SUB; break;
                    case 3: v = F_AND; break;
                    case 4: v = F_OR; break;
                    case 5: v = F_XOR; break;
                    case 6: v = F_SLT; break;
                    case 7: v = F_SLTU; break;
                    default: v = rType(funct);
                }
            }
            t[a] = v;
        }
        return t;
    }

    private static long rType(int funct) {
        switch (funct) {
            case 0x20: case 0x21: return F_ADD;
            case 0x22: case 0x23: return F_SUB;
            case 0x24: return F_AND;
            case 0x25: return F_OR;
            case 0x26: return F_XOR;
            case 0x27: return F_NOR;
            case 0x2a: return F_SLT;
            case 0x2b: return F_SLTU;
            case 0x00: return F_SLL;
            case 0x02: return F_SRL;
            case 0x03: return F_SRA;
            case 0x04: return F_SLL | 1L << SHIFT_VAR;
            case 0x06: return F_SRL | 1L << SHIFT_VAR;
            case 0x07: return F_SRA | 1L << SHIFT_VAR;
            case 0x08: return 1L << JR | 1L << NO_WRITE;
            case 0x0c: return 1L << SYSCALL | 1L << NO_WRITE;
            default: return 1L << NO_WRITE;
        }
    }

    private void build() {
        Component clock = add("Wiring", "Clock");
        t(clock, 0, "clk");
        konst("one", 1, 1);
        konst("zero1", 1, 0);
        konst("zero32", 32, 0);

        // PC: 레지스터는 PC XOR 0x00400000을 담아 리셋 때 PC = 0x00400000이다.
        Component pcReg = add("Memory", "Register", "width", "32");
        t(pcReg, 0, "pcx");
        t(pcReg, 1, "npcx");
        t(pcReg, 2, "clk");
        t(pcReg, 3, "zero1");
        t(pcReg, 4, "one");
        konst("textbase", 32, 0x00400000L);
        gate("XOR Gate", "pc", 32, "pcx", "textbase");
        gate("XOR Gate", "npcx", 32, "npc", "textbase");
        konst("four", 32, 4);
        arith("Adder", "pc4", "pc", "four");

        imem = addMips("Instruction Memory");
        t(imem, InstructionMemory.ADDR, "pc");
        t(imem, InstructionMemory.INSTR, "instr");

        fields("instr", 32, new int[] {6, 5, 5, 5, 5, 6}, "funct", "shamt", "rd", "rt", "rs", "op");
        fields("instr", 32, new int[] {16, 16}, "imm", "instrHi");
        fields("instr", 32, new int[] {26, 6}, "target", "instrOp");

        rom("ctrl", "op", 6, CTRL_WIDTH, controlTable());
        fields("ctrl", CTRL_WIDTH, new int[] {2, 1, 2, 1, 1, 1, 1, 1, 1, 3, 1, 1, 1},
                "RegDst", "ALUSrc", "MemtoReg", "RegWrite", "MemRead", "MemWrite", "Beq", "Bne", "Jump",
                "ALUOp", "ZeroExt", "Special2", "Regimm");
        fields("aluAddr", 10, new int[] {6, 3, 1}, "funct", "ALUOp", "Special2");
        rom("aluctl", "aluAddr", 10, 8, aluControlTable());
        fields("aluctl", 8, new int[] {4, 1, 1, 1, 1}, "ALUFunc", "ShiftVar", "JR", "Syscall", "NoWrite");

        // 즉값
        extend("immS", "imm", 16, 32, "sign");
        extend("immZ", "imm", 16, 32, "zero");
        mux("immExt", "ZeroExt", 1, 32, "immS", "immZ");
        mux("aluB", "ALUSrc", 1, 32, "rtVal", "immExt");

        // ALU
        arith("Adder", "addv", "rsVal", "aluB");
        arith("Subtractor", "subv", "rsVal", "aluB");
        gate("AND Gate", "andv", 32, "rsVal", "aluB");
        gate("OR Gate", "orv", 32, "rsVal", "aluB");
        gate("XOR Gate", "xorv", 32, "rsVal", "aluB");
        gate("NOR Gate", "norv", 32, "rsVal", "aluB");
        compare("rsVal", "aluB", "twosComplement", 4, "sltBit");
        extend("sltv", "sltBit", 1, 32, "zero");
        compare("rsVal", "aluB", "unsigned", 4, "sltuBit");
        extend("sltuv", "sltuBit", 1, 32, "zero");
        fields("rsVal", 32, new int[] {5, 26, 1}, "rs5", "rsMid", "rsSign");
        mux("dist", "ShiftVar", 1, 5, "shamt", "rs5");
        shift("sllv", "rtVal", "dist", "ll");
        shift("srlv", "rtVal", "dist", "lr");
        shift("srav", "rtVal", "dist", "ar");
        arith("Multiplier", "mulv", "rsVal", "rtVal");
        mux("aluResult", "ALUFunc", 4, 32, "addv", "subv", "andv", "orv", "xorv", "norv", "sltv", "sltuv",
                "sllv", "srlv", "srav", "mulv", "zero32", "zero32", "zero32", "zero32");

        // 레지스터 파일
        konst("ra5", 5, 31);
        konst("zero5", 5, 0);
        mux("writeReg", "RegDst", 2, 5, "rt", "rd", "ra5", "zero5");
        konst("zero16", 16, 0);
        fields("luiv", 32, new int[] {16, 16}, "zero16", "imm");
        mux("writeData", "MemtoReg", 2, 32, "aluResult", "memData", "pc4", "luiv");
        not("write", "NoWrite", 1);
        gate("AND Gate", "regWrite", 1, "RegWrite", "write");
        Component dec = add("Plexers", "Decoder", "select", "5", "enable", "false");
        for (int i = 0; i < 32; i += 1) {
            t(dec, i, "dec" + i);
        }
        t(dec, 32, "writeReg");
        konst("r0", 32, 0);
        for (int i = 1; i < 32; i += 1) {
            gate("AND Gate", "we" + i, 1, "dec" + i, "regWrite");
            Component r = add("Memory", "Register", "width", "32", "label", "$" + i);
            t(r, 0, "r" + i);
            t(r, 1, "writeData");
            t(r, 2, "clk");
            t(r, 3, "zero1");
            t(r, 4, "we" + i);
            regs[i] = r;
        }
        String[] regNames = new String[32];
        for (int i = 0; i < 32; i += 1) {
            regNames[i] = "r" + i;
        }
        mux("rsVal", "rs", 5, 32, regNames);
        mux("rtVal", "rt", 5, 32, regNames);

        // 메모리
        dmem = addMips("Data Memory");
        stack = addMips("Stack");
        for (Component m : new Component[] {dmem, stack}) {
            t(m, DataMemory.ADDR, "aluResult");
            t(m, DataMemory.WRITE_DATA, "rtVal");
            t(m, DataMemory.MEM_WRITE, "MemWrite");
            t(m, DataMemory.MEM_READ, "MemRead");
            t(m, DataMemory.CLK, "clk");
            t(m, DataMemory.READ_DATA, "memData");
        }

        // Console
        console = addMips("Console");
        t(console, Console.SYSCALL, "Syscall");
        t(console, Console.V0, "r2");
        t(console, Console.A0, "r4");
        t(console, Console.CLK, "clk");
        t(console, Console.EXIT, "halt");

        // 다음 PC. 분기 목적지는 PC 기준(QtSpim 기계어, D-010).
        compare("rsVal", "rtVal", "twosComplement", 3, "eq");
        not("neq", "eq", 1);
        gate("AND Gate", "tBeq", 1, "Beq", "eq");
        gate("AND Gate", "tBne", 1, "Bne", "neq");
        fields("rt", 5, new int[] {1, 4}, "rtBit0", "rtHi");
        gate("XOR Gate", "regimmCond", 1, "rtBit0", "rsSign");
        gate("AND Gate", "tRegimm", 1, "Regimm", "regimmCond");
        gate("OR Gate", "take", 1, "tBeq", "tBne", "tRegimm");
        konst("two", 5, 2);
        shift("off", "immS", "two", "ll");
        arith("Adder", "btarget", "pc", "off");
        fields("pc", 32, new int[] {28, 4}, "pcLo", "pcTop");
        konst("zero2", 2, 0);
        fields("jtarget", 32, new int[] {2, 26, 4}, "zero2", "target", "pcTop");
        mux("n1", "take", 1, 32, "pc4", "btarget");
        mux("n2", "Jump", 1, 32, "n1", "jtarget");
        mux("npc", "JR", 1, 32, "n2", "rsVal");

        int[] p = next();
        b.output("pc", 32, p[0], p[1]);
        p = next();
        b.output("halt", 1, p[0], p[1]);
        b.commit();
    }
}
