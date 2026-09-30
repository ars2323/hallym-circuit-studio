/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.app.cycle.RegisterFile;
import kr.ac.hallym.hcs.app.splitter.SplitterEdits;
import kr.ac.hallym.hcs.app.splitter.SplitterSpec;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 컴퓨터구조 튜토리얼의 예제 tests/tutorial/tutorial-mips.circ 생성기(N-18, D-161). 작은 단일 사이클 데이터패스다:
 * main에 PC, Instruction Memory, 명령어 필드 Splitter, 서브회로 control·regfile·alu, Data Memory(데이터+스택 한 부품,
 * D-140), Console. 튜토리얼 프로그램(tests/tutorial/tutorial.hmx, Hallym MIPS가 내보낸 이미지)이 쓰는 명령만 한다:
 * add addu sub subu and or slt(R형), syscall, addi addiu ori lui lw sw beq bne. 분기 목적지는 PC 기준(QtSpim 기계어,
 * D-010). 학생 과제의 정답 회로가 아니라 튜토리얼이 둘러보고 한 사이클씩 뛰어 볼 예제다.
 *
 * <p>PC 시작 = entry(M-01, D-126): 1비트 Register {@code started}가 첫 클럭 전에는 0이라 멀티플렉서가 entry 상수
 * (0x00400000: 예외 처리기 없이 어셈블한 tutorial.hmx의 main, 시작 코드 없음)를 PC로 내고, 첫 클럭부터는 {@code PC reg}의 값을 낸다. 그 출력 터널
 * 이름이 {@code PC}다. Console이 exit를 알리면 PC가 멈춘다(PC reg의 enable).
 *
 * <p>일부러 틀린 곳 하나: regfile 인스턴스의 RegWrite 입력 터널 이름이 {@code RegWirte}다(C4·C5). 그래서 처음에는
 * Messages에 "혹시 RegWrite?" 한 줄이 있고, 고치면 0건이다.
 */
public final class TutorialMips {
    /** tutorial.hmx의 entry: 예외 처리기 없이 어셈블해 main이 .text 첫 워드(시작 코드 없음, N-18 compat 검토). */
    static final long ENTRY = 0x00400000L;
    /** 튜토리얼이 고치게 하는 오타(C4·C5). */
    public static final String TYPO = "RegWirte";

    // control 출력 비트(ROM 한 개: 주소 = {op, funct})
    static final int REG_DST = 0;
    static final int ALU_SRC = 1;
    static final int MEM_TO_REG = 2; // 2비트: 0 ALU, 1 메모리, 2 lui
    static final int REG_WRITE = 4;
    static final int MEM_READ = 5;
    static final int MEM_WRITE = 6;
    static final int BEQ = 7;
    static final int BNE = 8;
    static final int ZERO_EXT = 9;
    static final int ALU_CTL = 10;   // 3비트: 0 add, 1 sub, 2 and, 3 or, 4 slt
    static final int SYSCALL = 13;
    static final int CTRL_WIDTH = 14;

    static final String[] REG_NAMES = {"$zero", "$at", "$v0", "$v1", "$a0", "$a1", "$a2", "$a3", "$t0", "$t1", "$t2",
        "$t3", "$t4", "$t5", "$t6", "$t7", "$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7", "$t8", "$t9", "$k0",
        "$k1", "$gp", "$sp", "$fp", "$ra"};

    private final LogisimFile file;
    private final Library mips;

    public Circuit control;
    public Circuit regfile;
    public Circuit alu;
    public Component pcReg;
    public Component pcMux;
    public Component imem;
    public Component dmem;
    public Component console;
    public Component regfileInst;
    public Component aluInst;
    public Component controlInst;
    public Component typo;
    /** regfile 안의 레지스터 $1~$31(0번은 상수). */
    public final Component[] regs = new Component[32];

    private TutorialMips(LogisimFile file, Library mips) {
        this.file = file;
        this.mips = mips;
    }

    public static TutorialMips build(LogisimFile file, Library mips) throws Exception {
        TutorialMips t = new TutorialMips(file, mips);
        t.control = t.control();
        t.regfile = t.regfile();
        t.alu = t.alu();
        for (Circuit sub : new Circuit[] {t.control, t.regfile, t.alu}) {
            kr.ac.hallym.hcs.app.appear.AutoAppearance
                    .action(sub, kr.ac.hallym.hcs.app.appear.AutoAppearance.build(sub)).doIt(null);
        }
        t.main();
        // Registers 패널이 $0~$31로 보이게 regfile을 레지스터 파일로 표시한다(v1 Mark as Register File, hcs:ext)
        RegisterFile.markAction(file, t.regfile, true).doIt(null);
        return t;
    }

    private static Location at(int x, int y) {
        return Location.create(x, y);
    }

    // ---- 제어: opcode·funct → 제어 신호 ----

    static long ctrl(int regDst, int aluSrc, int memToReg, int regWrite, int memRead, int memWrite, int beq, int bne,
            int zeroExt, int aluCtl, int syscall) {
        return (long) regDst << REG_DST | (long) aluSrc << ALU_SRC | (long) memToReg << MEM_TO_REG
                | (long) regWrite << REG_WRITE | (long) memRead << MEM_READ | (long) memWrite << MEM_WRITE
                | (long) beq << BEQ | (long) bne << BNE | (long) zeroExt << ZERO_EXT | (long) aluCtl << ALU_CTL
                | (long) syscall << SYSCALL;
    }

    /** 주소 = op × 64 + funct. 모르는 명령은 0(아무것도 쓰지 않음). */
    static long[] controlTable() {
        long[] t = new long[4096];
        for (int funct = 0; funct < 64; funct++) {
            int r;
            switch (funct) {
                case 0x20: case 0x21: r = 0; break; // add addu
                case 0x22: case 0x23: r = 1; break; // sub subu
                case 0x24: r = 2; break;
                case 0x25: r = 3; break;
                case 0x2a: r = 4; break;             // slt
                default: r = -1;
            }
            if (r >= 0) {
                t[funct] = ctrl(1, 0, 0, 1, 0, 0, 0, 0, 0, r, 0);
            } else if (funct == 0x0c) {
                t[funct] = ctrl(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
            }
            t[0x04 * 64 + funct] = ctrl(0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0); // beq
            t[0x05 * 64 + funct] = ctrl(0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0); // bne
            t[0x08 * 64 + funct] = ctrl(0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0); // addi
            t[0x09 * 64 + funct] = ctrl(0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0); // addiu
            t[0x0d * 64 + funct] = ctrl(0, 1, 0, 1, 0, 0, 0, 0, 1, 3, 0); // ori
            t[0x0f * 64 + funct] = ctrl(0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0); // lui
            t[0x23 * 64 + funct] = ctrl(0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0); // lw
            t[0x2b * 64 + funct] = ctrl(0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0); // sw
        }
        return t;
    }

    private Circuit control() {
        Circuit c = new Circuit("control");
        file.addCircuit(c);
        CircuitBuilder b = new CircuitBuilder(file, c);
        b.input("op", 6, 100, 100);
        b.input("funct", 6, 100, 160);
        // 주소 {op, funct}: 아래 6비트 funct, 위 6비트 op
        Component join = b.add("Wiring", "Splitter", 300, 300, splitAttrs(12, new int[] {6, 6}));
        b.tunnelOutward(join, 0, "addr");
        b.tunnelOutward(join, 1, "funct");
        b.tunnelOutward(join, 2, "op");
        StringBuilder sb = new StringBuilder("addr/data: 12 " + CTRL_WIDTH + "\n");
        long[] table = controlTable();
        for (int i = 0; i < table.length; i++) {
            sb.append(Long.toHexString(table[i])).append(i % 16 == 15 ? "\n" : " ");
        }
        Component rom = b.add("Memory", "ROM", 560, 300, "addrWidth", "12", "dataWidth", Integer.toString(CTRL_WIDTH),
                "contents", sb.toString());
        b.tunnelOutward(rom, 1, "addr");
        b.tunnelOutward(rom, 0, "ctrl");
        b.constant("one", 1, 1, 540, 420);
        b.tunnelOutward(rom, 2, "one");
        String[] names = {"RegDst", "ALUSrc", "MemtoReg", "RegWrite", "MemRead", "MemWrite", "Beq", "Bne", "ZeroExt",
            "ALUCtl", "Syscall"};
        int[] widths = {1, 1, 2, 1, 1, 1, 1, 1, 1, 3, 1};
        Component split = b.add("Wiring", "Splitter", 760, 300, splitAttrs(CTRL_WIDTH, widths));
        b.tunnelOutward(split, 0, "ctrl");
        for (int i = 0; i < names.length; i++) {
            b.tunnelOutward(split, 1 + i, names[i]);
            b.outputOutward(names[i], widths[i], 1100, 100 + 60 * i);
        }
        b.commit();
        return c;
    }

    // ---- 레지스터 파일: $0 = 0, $1~$31 ----

    private Circuit regfile() {
        Circuit c = new Circuit("regfile");
        file.addCircuit(c);
        CircuitBuilder b = new CircuitBuilder(file, c);
        b.input("RR1", 5, 100, 100);
        b.input("RR2", 5, 100, 160);
        b.input("WR", 5, 100, 220);
        b.input("WD", 32, 100, 300);
        b.input("RegWrite", 1, 100, 380);
        b.input("clk", 1, 100, 440);
        b.outputOutward("RD1", 32, 2000, 100);
        b.outputOutward("RD2", 32, 2000, 200);
        b.outputOutward("v0", 32, 2000, 300);
        b.outputOutward("a0", 32, 2000, 400);
        b.constant("r0", 32, 0, 340, 560);
        // 쓸 레지스터: WR을 푸는 디코더(enable = RegWrite). 꺼졌을 때 출력은 0: 떠 있으면 Register가 쓰기로 본다
        Component dec = b.add("Plexers", "Decoder", 380, 700, "select", "5", "enable", "true", "disabled", "0");
        for (int i = 1; i < 32; i++) {
            b.tunnelOutward(dec, i, "we" + i);
        }
        b.tunnelOutward(dec, 32, "WR");
        b.tunnelOutward(dec, 33, "RegWrite");
        for (int i = 1; i < 32; i++) {
            int col = (i - 1) / 8;
            int row = (i - 1) % 8;
            Component r = b.add("Memory", "Register", 760 + 260 * col, 140 + 110 * row, "width", "32",
                    "label", REG_NAMES[i]);
            b.tunnelOutward(r, 0, "r" + i);
            b.tunnelOutward(r, 1, "WD");
            b.tunnelOutward(r, 2, "clk");
            b.tunnelOutward(r, 4, "we" + i);
            regs[i] = r;
        }
        String[] regNets = new String[32];
        for (int i = 0; i < 32; i++) {
            regNets[i] = "r" + i;
        }
        String[][] read = {{"RR1", "RD1"}, {"RR2", "RD2"}};
        for (int k = 0; k < read.length; k++) {
            Component m = b.add("Plexers", "Multiplexer", 1860, 400 + 420 * k, "select", "5", "width", "32",
                    "enable", "false");
            for (int i = 0; i < 32; i++) {
                b.tunnelOutward(m, i, regNets[i]);
            }
            b.tunnelOutward(m, 32, read[k][0]);
            b.tunnelOutward(m, 33, read[k][1]);
        }
        b.add("Wiring", "Tunnel", 1960, 300, "width", "32", "label", "r2", "facing", "east");
        b.add("Wiring", "Tunnel", 1960, 400, "width", "32", "label", "r4", "facing", "east");
        b.wire(at(1960, 300), at(2000, 300));
        b.wire(at(1960, 400), at(2000, 400));
        b.commit();
        return c;
    }

    // ---- ALU: 0 add, 1 sub, 2 and, 3 or, 4 slt ----

    private Circuit alu() {
        Circuit c = new Circuit("alu");
        file.addCircuit(c);
        CircuitBuilder b = new CircuitBuilder(file, c);
        b.input("A", 32, 100, 100);
        b.input("B", 32, 100, 200);
        b.input("ALUCtl", 3, 100, 260);
        b.outputOutward("Result", 32, 1100, 100);
        b.outputOutward("Zero", 1, 1100, 160);
        Component add = b.add("Arithmetic", "Adder", 400, 320, "width", "32");
        Component sub = b.add("Arithmetic", "Subtractor", 400, 420, "width", "32");
        Component and = b.add("Gates", "AND Gate", 400, 520, "width", "32", "inputs", "2", "size", "30");
        Component or = b.add("Gates", "OR Gate", 400, 620, "width", "32", "inputs", "2", "size", "30");
        Component lt = b.add("Arithmetic", "Comparator", 400, 720, "width", "32", "mode", "twosComplement");
        for (Component x : new Component[] {add, sub, lt}) {
            b.tunnelOutward(x, 0, "A");
            b.tunnelOutward(x, 1, "B");
        }
        for (Component x : new Component[] {and, or}) {
            b.tunnelOutward(x, 1, "A");
            b.tunnelOutward(x, 2, "B");
        }
        b.tunnelOutward(add, 2, "sum");
        b.tunnelOutward(sub, 2, "diff");
        b.tunnelOutward(and, 0, "andv");
        b.tunnelOutward(or, 0, "orv");
        b.tunnelOutward(lt, 4, "ltBit");
        Component ext = b.add("Wiring", "Bit Extender", 560, 800, "in_width", "1", "out_width", "32", "type", "zero");
        b.tunnelOutward(ext, 0, "sltv");
        b.tunnelOutward(ext, 1, "ltBit");
        b.constant("zero32", 32, 0, 620, 900);
        Component m = b.add("Plexers", "Multiplexer", 800, 500, "select", "3", "width", "32", "enable", "false");
        String[] in = {"sum", "diff", "andv", "orv", "sltv", "zero32", "zero32", "zero32"};
        for (int i = 0; i < 8; i++) {
            b.tunnelOutward(m, i, in[i]);
        }
        b.tunnelOutward(m, 8, "ALUCtl");
        b.tunnelOutward(m, 9, "Result");
        Component zero = b.add("Wiring", "Constant", 820, 700, "width", "32", "value", "0x0");
        Component eq = b.add("Arithmetic", "Comparator", 960, 690, "width", "32");
        b.tunnelOutward(eq, 0, "Result");
        Location eqB = CircuitBuilder.port(eq, 1);
        b.wire(zero.getLocation(), at(eqB.getX() - 40, zero.getLocation().getY()));
        b.wire(at(eqB.getX() - 40, zero.getLocation().getY()), at(eqB.getX() - 40, eqB.getY()));
        b.wire(at(eqB.getX() - 40, eqB.getY()), eqB);
        b.tunnelOutward(eq, 3, "Zero");
        b.commit();
        return c;
    }

    // ---- main ----

    private void main() throws Exception {
        Circuit c = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, c);

        // 클럭, exit → running(PC가 멈춘다)
        Component clock = b.add("Wiring", "Clock", 80, 100);
        b.tunnelOutward(clock, 0, "clk");
        Component stop = b.add("Gates", "NOT Gate", 180, 100);
        b.tunnelOutward(stop, 1, "exit");
        b.tunnelOutward(stop, 0, "running");

        // PC: 첫 클럭 전에는 entry, 그 뒤로는 PC reg
        pcReg = b.add("Memory", "Register", 240, 200, "width", "32", "label", "PC reg");
        spread(b, pcReg, new int[] {1, 4}, new String[] {"npc", "running"}, "east", 0);
        b.tunnelOutward(pcReg, 2, "clk");
        Component started = b.add("Memory", "Register", 240, 300, "width", "1", "label", "started");
        b.add("Wiring", "Constant", 170, 300, "width", "1", "value", "0x1");
        b.wire(at(170, 300), CircuitBuilder.port(started, 1));
        b.tunnelOutward(started, 0, "started");
        b.tunnelOutward(started, 2, "clk");
        pcMux = b.add("Plexers", "Multiplexer", 340, 210, "select", "1", "width", "32", "enable", "false");
        Location m0 = CircuitBuilder.port(pcMux, 0);
        Location m1 = CircuitBuilder.port(pcMux, 1);
        b.add("Wiring", "Constant", m0.getX() - 10, 130, "width", "32", "value", "0x" + Long.toHexString(ENTRY));
        TutorialLogic.path(b, at(m0.getX() - 10, 130), at(m0.getX() - 10, m0.getY()), m0);
        Location q = pcReg.getLocation();
        TutorialLogic.path(b, q, at(q.getX() + 20, q.getY()), at(q.getX() + 20, m1.getY()), m1);
        b.tunnelOutward(pcMux, 2, "started");

        // PC → Instruction Memory(선), PC 터널, +4
        imem = b.add(mips, "Instruction Memory", 620, 210);
        Location pcOut = pcMux.getLocation();
        Location addr = CircuitBuilder.port(imem, 0);
        TutorialLogic.path(b, pcOut, at(390, pcOut.getY()), addr);
        b.add("Wiring", "Tunnel", 390, pcOut.getY(), "width", "32", "label", "PC", "facing", "north");
        Component plus4 = b.add("Arithmetic", "Adder", 500, 320, "width", "32");
        Location four = CircuitBuilder.port(plus4, 1);
        b.add("Wiring", "Constant", four.getX() - 20, four.getY(), "width", "32", "value", "0x4");
        b.wire(at(four.getX() - 20, four.getY()), four);
        b.tunnelOutward(plus4, 0, "PC");
        b.tunnelOutward(plus4, 2, "pc4");

        // 명령어 필드
        Location instr = CircuitBuilder.port(imem, 1);
        SplitterSpec spec = SplitterSpec.parse("31:26 op, 25:21 rs, 20:16 rt, 15:11 rd, 10:6 shamt, 5:0 funct", 32,
                true);
        List<String> attrs = new ArrayList<>();
        for (Map.Entry<String, String> e : spec.toStandardAttrs().entrySet()) {
            attrs.add(e.getKey());
            attrs.add(e.getValue());
        }
        Component fields = b.add("Wiring", "Splitter", instr.getX() + 60, instr.getY(), attrs.toArray(new String[0]));
        Location bend = at(instr.getX() + 30, instr.getY());
        TutorialLogic.path(b, instr, bend, fields.getLocation());
        b.wire(bend, at(bend.getX(), bend.getY() + 70));
        b.add("Wiring", "Tunnel", bend.getX(), bend.getY() + 70, "width", "32", "label", "instr", "facing", "north");
        spread(b, fields, new int[] {1, 2, 3, 4, 5, 6}, new String[] {"op", "rs", "rt", "rd", "shamt", "funct"}, "west", 2);
        Component immSplit = b.add("Wiring", "Splitter", 760, 330, splitAttrs(32, new int[] {16, 16}));
        b.tunnelOutward(immSplit, 0, "instr");
        b.tunnelOutward(immSplit, 1, "imm");

        // control(오른쪽 위 포트가 부품 자리): 서쪽 op·funct, 동쪽 제어 신호
        controlInst = b.addSubcircuit(control, 1000, 110);
        // regfile: 서쪽 RR1·RR2·WR·WD·RegWrite·clk, 동쪽 RD1·RD2·v0·a0
        regfileInst = b.addSubcircuit(regfile, 1000, 480);
        // alu: 서쪽 A·B·ALUCtl, 동쪽 Result·Zero
        aluInst = b.addSubcircuit(alu, 1320, 480);
        b.commit();
        b = new CircuitBuilder(file, c);
        Map<String, Location> cp = DemoDatapath.ports(controlInst);
        for (Map.Entry<String, Location> e : cp.entrySet()) {
            tunnelAt(b, e.getValue(), e.getKey(), portWidth(controlInst, e.getKey()),
                    e.getKey().equals("op") || e.getKey().equals("funct") ? "east" : "west");
        }
        Map<String, Location> rp = DemoDatapath.ports(regfileInst);
        for (Map.Entry<String, Location> e : rp.entrySet()) {
            boolean in = !e.getKey().startsWith("RD") && !e.getKey().equals("v0") && !e.getKey().equals("a0");
            String name = e.getKey().equals("RR1") ? "rs" : e.getKey().equals("RR2") ? "rt"
                    : e.getKey().equals("RegWrite") ? TYPO : e.getKey().equals("RD1") ? "rsVal"
                    : e.getKey().equals("RD2") ? "rtVal" : e.getKey().equals("WD") ? "writeData" : e.getKey();
            Component tun = tunnelAt(b, e.getValue(), name, portWidth(regfileInst, e.getKey()), in ? "east" : "west");
            if (e.getKey().equals("RegWrite")) {
                typo = tun;
            }
        }
        Map<String, Location> ap = DemoDatapath.ports(aluInst);
        for (Map.Entry<String, Location> e : ap.entrySet()) {
            String k = e.getKey();
            if (k.equals("Zero")) {
                continue; // 이 데이터패스는 Zero 대신 비교기로 분기한다
            }
            String name = k.equals("A") ? "rsVal" : k.equals("B") ? "aluB" : k.equals("Result") ? "aluResult" : k;
            tunnelAt(b, e.getValue(), name, portWidth(aluInst, k), k.equals("Result") ? "west" : "east");
        }

        // 쓸 레지스터(RegDst), 즉값(ZeroExt), ALU B(ALUSrc)
        mux(b, 820, 560, 1, 5, "WR", "RegDst", "rt", "rd");
        Component extS = b.add("Wiring", "Bit Extender", 820, 660, "in_width", "16", "out_width", "32",
                "type", "sign");
        b.tunnelOutward(extS, 0, "immS");
        b.tunnelOutward(extS, 1, "imm");
        Component extZ = b.add("Wiring", "Bit Extender", 820, 740, "in_width", "16", "out_width", "32",
                "type", "zero");
        b.tunnelOutward(extZ, 0, "immZ");
        b.tunnelOutward(extZ, 1, "imm");
        mux(b, 960, 700, 1, 32, "immExt", "ZeroExt", "immS", "immZ");
        mux(b, 1160, 620, 1, 32, "aluB", "ALUSrc", "rtVal", "immExt");

        // Data Memory(데이터+스택, D-140): 주소 = ALU 결과
        dmem = b.add(mips, "Data Memory", 1700, 520);
        b.tunnelOutward(dmem, 0, "aluResult");
        b.tunnelOutward(dmem, 1, "rtVal");
        b.tunnelOutward(dmem, 2, "MemWrite");
        b.tunnelOutward(dmem, 3, "MemRead");
        b.tunnelOutward(dmem, 4, "clk");
        b.tunnelOutward(dmem, 5, "memData");

        // 레지스터에 쓸 값: ALU / 메모리 / lui
        Component lui = b.add("Wiring", "Splitter", 1560, 720, splitAttrs(32, new int[] {16, 16}));
        b.tunnelOutward(lui, 0, "luiv");
        spread(b, lui, new int[] {1, 2}, new String[] {"zero16", "imm"}, "west", 0);
        b.constant("zero16", 16, 0, 1500, 800);
        Component wd = b.add("Plexers", "Multiplexer", 1860, 700, "select", "2", "width", "32", "enable", "false");
        spread(b, wd, new int[] {0, 1, 2}, new String[] {"aluResult", "memData", "luiv"}, "east", 2);
        Location in3 = CircuitBuilder.port(wd, 3);
        b.add("Wiring", "Constant", in3.getX() - 60, in3.getY() + 30, "width", "32", "value", "0x0");
        TutorialLogic.path(b, at(in3.getX() - 60, in3.getY() + 30), at(in3.getX() - 20, in3.getY() + 30),
                at(in3.getX() - 20, in3.getY()), in3);
        b.tunnelOutward(wd, 4, "MemtoReg");
        b.tunnelOutward(wd, 5, "writeData");

        // Console: syscall, $v0, $a0. exit이면 PC가 멈춘다.
        console = b.add(mips, "Console", 1840, 290);
        b.tunnelOutward(console, 0, "Syscall");
        b.tunnelOutward(console, 1, "v0");
        b.tunnelOutward(console, 2, "a0");
        b.tunnelOutward(console, 3, "clk");
        b.tunnelOutward(console, 4, "exit");

        // 다음 PC: 분기 목적지 = PC + (immS << 2) (QtSpim 기계어, D-010)
        Component eq = b.add("Arithmetic", "Comparator", 300, 460, "width", "32");
        b.tunnelOutward(eq, 0, "rsVal");
        b.tunnelOutward(eq, 1, "rtVal");
        b.tunnelOutward(eq, 3, "eq");
        Component ne = b.add("Gates", "NOT Gate", 300, 540);
        b.tunnelOutward(ne, 0, "ne");
        b.tunnelOutward(ne, 1, "eq");
        Component tb = b.add("Gates", "AND Gate", 460, 460, "inputs", "2", "size", "30");
        b.tunnelOutward(tb, 0, "takeEq");
        b.tunnelOutward(tb, 1, "Beq");
        b.tunnelOutward(tb, 2, "eq");
        Component tn = b.add("Gates", "AND Gate", 460, 540, "inputs", "2", "size", "30");
        b.tunnelOutward(tn, 0, "takeNe");
        b.tunnelOutward(tn, 1, "Bne");
        b.tunnelOutward(tn, 2, "ne");
        Component take = b.add("Gates", "OR Gate", 620, 500, "inputs", "2", "size", "30");
        b.tunnelOutward(take, 0, "take");
        b.tunnelOutward(take, 1, "takeEq");
        b.tunnelOutward(take, 2, "takeNe");
        Component sh = b.add("Arithmetic", "Shifter", 300, 640, "width", "32");
        b.tunnelOutward(sh, 0, "immS");
        Location dist = CircuitBuilder.port(sh, 1);
        b.add("Wiring", "Constant", dist.getX() - 30, dist.getY(), "width", "5", "value", "0x2");
        b.wire(at(dist.getX() - 30, dist.getY()), dist);
        b.tunnelOutward(sh, 2, "offset");
        Component bt = b.add("Arithmetic", "Adder", 460, 660, "width", "32");
        b.tunnelOutward(bt, 0, "PC");
        b.tunnelOutward(bt, 1, "offset");
        b.tunnelOutward(bt, 2, "target");
        mux(b, 640, 640, 1, 32, "npc", "take", "pc4", "target");
        b.commit();

        SplitterEdits.setNames(file, c, fields.getLocation(), spec);
    }

    /**
     * 한 줄로 10 단위마다 선 포트들(위에서 아래 차례)의 터널을 20 단위 간격으로 벌려 놓는다(N-18, UI 검토): 터널 칩의
     * 높이(16~18)가 10보다 커 바로 붙이면 옆 칩을 덮는다. 포트 k는 곧게 나가고, 그 위의 포트는 위로, 아래의 포트는
     * 아래로 10씩 더 벌어진다. 바깥쪽 포트일수록 부품 가까이에서 꺾어 선끼리 엇갈리지 않는다. facing은 터널이 보는
     * 쪽이다(east = 몸체가 왼쪽으로, 부품의 왼쪽 포트).
     */
    private static void spread(CircuitBuilder b, Component c, int[] ports, String[] labels, String facing, int k) {
        int n = ports.length;
        int dx = facing.equals("west") ? 1 : -1;
        Location first = CircuitBuilder.port(c, ports[0]);
        int x0 = first.getX();
        int reach = 10 * (Math.max(k, n - 1 - k) + 1);
        int x = x0 + dx * reach;
        for (int i = 0; i < n; i++) {
            Location p = CircuitBuilder.port(c, ports[i]);
            if (p.getX() != x0 || p.getY() != first.getY() + 10 * i) {
                throw new IllegalArgumentException("ports not in one line 10 apart: " + p);
            }
            int y = p.getY() + 10 * (i - k);
            if (i == k) {
                b.wire(p, at(x, y));
            } else {
                int xb = x0 + dx * (i < k ? 10 * (i + 1) : 10 * (n - i));
                b.wire(p, at(xb, p.getY()));
                b.wire(at(xb, p.getY()), at(xb, y));
                b.wire(at(xb, y), at(x, y));
            }
            tunnelAt(b, at(x, y), labels[i], CircuitBuilder.width(c, ports[i]), facing);
        }
    }

    /** 포트 at에 터널 하나(몸체는 facing의 반대쪽으로: east = 왼쪽으로 뻗는다). */
    private static Component tunnelAt(CircuitBuilder b, Location at, String label, int width, String facing) {
        return b.add("Wiring", "Tunnel", at.getX(), at.getY(), "width", Integer.toString(width), "label", label,
                "facing", facing);
    }

    private static int portWidth(Component inst, String port) {
        return CircuitBuilder.width(inst, DemoDatapath.index(inst, port));
    }

    /** 멀티플렉서(select 비트, 폭): 입력 터널들, 선택, 출력. */
    private static void mux(CircuitBuilder b, int x, int y, int select, int width, String out, String sel,
            String... inputs) {
        Component m = b.add("Plexers", "Multiplexer", x, y, "select", Integer.toString(select),
                "width", Integer.toString(width), "enable", "false");
        for (int i = 0; i < inputs.length; i++) {
            b.tunnelOutward(m, i, inputs[i]);
        }
        b.tunnelOutward(m, inputs.length, sel);
        b.tunnelOutward(m, inputs.length + 1, out);
    }

    /** 비트 수 widths(아래 비트부터)로 나누는 Splitter 속성. */
    static String[] splitAttrs(int incoming, int[] widths) {
        List<String> a = new ArrayList<>();
        a.add("incoming");
        a.add(Integer.toString(incoming));
        a.add("fanout");
        a.add(Integer.toString(widths.length));
        int bit = 0;
        for (int g = 0; g < widths.length; g++) {
            for (int i = 0; i < widths[g]; i++) {
                a.add("bit" + bit++);
                a.add(Integer.toString(g));
            }
        }
        return a.toArray(new String[0]);
    }
}
