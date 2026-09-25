/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.demo;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.splitter.SplitterEdits;
import kr.ac.hallym.hcs.app.splitter.SplitterSpec;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 스크린샷용 데모 회로 tests/circ/demo-datapath.circ의 생성기(검토 반영 1, 5번). 학생이 그린 것 같은 single-cycle MIPS
 * 일부다: PC → +4 가산기, PC → 명령어 메모리 → R형 스플리터 → 레지스터 파일 서브회로 → ALU 서브회로 → 데이터 메모리 →
 * MemtoReg 멀티플렉서 → 레지스터 파일. 부품 사이는 선으로 잇고(PLAN 부록 A.4: 다른 넷의 선은 같은 직선에서 겹치지
 * 않고, 가로·세로가 끝점 없이만 교차한다), 터널은 제어선(RegWrite, MemtoReg, MemWrite, MemRead, ALUOp)과 clk만
 * 쓴다. 테스트 편의로 PC가 0x10이 되면 1이 되는 halt 출력이 PC 아래에 있다(원조 -tty가 멈추게).
 * 우리 정확성 테스트용 참조 회로(tests/mips/ref-mips.circ)와는 따로다.
 */
public final class DemoDatapath {
    /** 명령어 메모리 내용(주소 0부터): add $3,$1,$2 / sub / and / or. */
    static final int[] PROGRAM = {0x00221820, 0x00221822, 0x00221824, 0x00221825};

    final LogisimFile file;
    final Library mips;
    Component pc;
    Component adder;
    Component imem;
    Component splitter;
    Component regfile;
    Component alu;
    Component dmem;
    Component mux;
    Component halt;
    Circuit regfileCircuit;
    Circuit aluCircuit;

    private DemoDatapath(LogisimFile file, Library mips) {
        this.file = file;
        this.mips = mips;
    }

    /** file의 main에 데모 회로를 만든다(서브회로 regfile·alu를 더한다). */
    public static DemoDatapath build(LogisimFile file, Library mips) throws Exception {
        DemoDatapath d = new DemoDatapath(file, mips);
        d.regfileCircuit = d.regfile();
        d.aluCircuit = d.alu();
        // 서브회로 상자는 Auto Appearance(검토 2차 C): 포트 이름과 회로 이름이 상자 안에 보인다
        for (Circuit sub : new Circuit[] {d.regfileCircuit, d.aluCircuit}) {
            kr.ac.hallym.hcs.app.appear.AutoAppearance
                    .action(sub, kr.ac.hallym.hcs.app.appear.AutoAppearance.build(sub)).doIt(null);
        }
        d.main();
        return d;
    }

    // ---- 서브회로: 레지스터 파일(레지스터 $1~$3, $0은 0) ----

    private Circuit regfile() {
        Circuit c = new Circuit("regfile");
        file.addCircuit(c);
        CircuitBuilder b = new CircuitBuilder(file, c);
        b.input("RR1", 5, 100, 100);
        b.input("RR2", 5, 100, 150);
        b.input("WR", 5, 100, 200);
        b.input("WD", 32, 100, 250);
        b.input("RegWrite", 1, 100, 300);
        b.input("clk", 1, 100, 350);
        b.output("RD1", 32, 900, 100);
        b.output("RD2", 32, 900, 150);
        b.constant("r0", 32, 0, 300, 450);
        // 낮은 두 비트로 레지스터를 고른다
        String[] sel = {"RR1", "RR2", "WR"};
        for (int i = 0; i < sel.length; i++) {
            Component s = b.add("Wiring", "Splitter", 250, 120 + 60 * i, "fanout", "2", "incoming", "5", "bit0", "0",
                    "bit1", "0", "bit2", "1", "bit3", "1", "bit4", "1");
            b.tunnel(s, 0, sel[i]);
            b.tunnel(s, 1, sel[i] + "lo");
        }
        Component dec = b.add("Plexers", "Decoder", 400, 600, "select", "2", "enable", "false");
        b.tunnel(dec, 4, "WRlo");
        for (int r = 1; r <= 3; r++) {
            Component and = b.add("Gates", "AND Gate", 520, 500 + 60 * r, "inputs", "2", "size", "30");
            b.tunnel(dec, r, "sel" + r);
            b.tunnel(and, 1, "sel" + r);
            b.tunnel(and, 2, "RegWrite");
            b.tunnel(and, 0, "we" + r);
            Component reg = b.add("Memory", "Register", 700, 300 + 70 * r, "width", "32", "label", "$" + r);
            b.tunnel(reg, 1, "WD");
            b.tunnel(reg, 2, "clk");
            b.tunnel(reg, 4, "we" + r);
            b.tunnel(reg, 0, "r" + r);
        }
        String[][] read = {{"RR1lo", "RD1"}, {"RR2lo", "RD2"}};
        for (int i = 0; i < read.length; i++) {
            Component m = b.add("Plexers", "Multiplexer", 850, 100 + 150 * i + 400, "select", "2", "width", "32",
                    "enable", "false");
            for (int r = 0; r < 4; r++) {
                b.tunnel(m, r, "r" + r);
            }
            b.tunnel(m, 4, read[i][0]);
            b.tunnel(m, m.getEnds().size() - 1, read[i][1]);
        }
        b.commit();
        return c;
    }

    // ---- 서브회로: ALU(ALUOp 0 더하기, 1 빼기, 2 AND, 3 OR) ----

    private Circuit alu() {
        Circuit c = new Circuit("alu");
        file.addCircuit(c);
        CircuitBuilder b = new CircuitBuilder(file, c);
        b.input("A", 32, 100, 100);
        b.input("B", 32, 100, 150);
        b.input("ALUOp", 2, 100, 200);
        b.output("Result", 32, 900, 100);
        b.output("Zero", 1, 900, 150);
        Component add = b.add("Arithmetic", "Adder", 400, 300, "width", "32");
        Component sub = b.add("Arithmetic", "Subtractor", 400, 400, "width", "32");
        Component and = b.add("Gates", "AND Gate", 400, 500, "width", "32", "inputs", "2", "size", "30");
        Component or = b.add("Gates", "OR Gate", 400, 600, "width", "32", "inputs", "2", "size", "30");
        for (Component x : new Component[] {add, sub}) {
            b.tunnel(x, 0, "A");
            b.tunnel(x, 1, "B");
        }
        for (Component x : new Component[] {and, or}) {
            b.tunnel(x, 1, "A");
            b.tunnel(x, 2, "B");
        }
        b.tunnel(add, 2, "sum");
        b.tunnel(sub, 2, "diff");
        b.tunnel(and, 0, "andv");
        b.tunnel(or, 0, "orv");
        Component m = b.add("Plexers", "Multiplexer", 650, 450, "select", "2", "width", "32", "enable", "false");
        String[] in = {"sum", "diff", "andv", "orv"};
        for (int i = 0; i < 4; i++) {
            b.tunnel(m, i, in[i]);
        }
        b.tunnel(m, 4, "ALUOp");
        b.tunnel(m, m.getEnds().size() - 1, "Result");
        Component zero = b.add("Wiring", "Constant", 700, 620, "width", "32", "value", "0x0");
        Component cmp = b.add("Arithmetic", "Comparator", 800, 610, "width", "32");
        b.tunnel(cmp, 0, "Result");
        b.wire(zero.getEnds().get(0).getLocation(), cmp.getEnds().get(1).getLocation().translate(-40, 0));
        b.wire(cmp.getEnds().get(1).getLocation().translate(-40, 0), cmp.getEnds().get(1).getLocation());
        b.tunnel(cmp, 3, "Zero");
        b.commit();
        return c;
    }

    // ---- main: 데이터패스 ----

    /** 서브회로 부품의 포트 이름 → 위치(부품을 loc에 놓았을 때). */
    static Map<String, Location> ports(Component inst) {
        Map<String, Location> ret = new LinkedHashMap<>();
        for (int i = 0; i < inst.getEnds().size(); i++) {
            ret.put(Kinds.portName(inst, i), inst.getEnds().get(i).getLocation());
        }
        return ret;
    }

    /** 서브회로를 port 이름의 포트가 at에 오도록 놓을 자리. */
    static Location placeSo(Circuit sub, String port, Location at) {
        Component probe = sub.getSubcircuitFactory().createComponent(Location.create(0, 0),
                sub.getSubcircuitFactory().createAttributeSet());
        Location p = ports(probe).get(port);
        return Location.create(at.getX() - p.getX(), at.getY() - p.getY());
    }

    private static Location at(int x, int y) {
        return Location.create(x, y);
    }

    /** 꺾은선: 점들을 차례로 잇는다(모두 가로 또는 세로). */
    private static void path(CircuitBuilder b, Location... pts) {
        for (int i = 0; i + 1 < pts.length; i++) {
            if (!pts[i].equals(pts[i + 1])) {
                b.wire(pts[i], pts[i + 1]);
            }
        }
    }

    private void main() throws Exception {
        Circuit c = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, c);

        // PC와 +4
        pc = b.add("Memory", "Register", 300, 200, "width", "32", "label", "PC");
        adder = b.add("Arithmetic", "Adder", 200, 120, "width", "32");
        b.add("Wiring", "Constant", 160, 130, "width", "32", "value", "0x4");
        path(b, at(200, 120), at(230, 120), at(230, 200), at(270, 200)); // npc → PC.D
        path(b, at(300, 200), at(360, 200)); // PC → 명령어 메모리
        path(b, at(330, 200), at(330, 80), at(140, 80), at(140, 110), at(160, 110)); // PC → +4
        b.tunnelOutward(pc, 2, "clk");

        // 명령어 메모리 → 스플리터(R형)
        imem = b.add(mips, "Instruction Memory", 560, 200, "base", "0x0",
                "contents", program());
        SplitterSpec spec = SplitterSpec.parse("31:26 op, 25:21 rs, 20:16 rt, 15:11 rd, 10:6 shamt, 5:0 funct", 32,
                true);
        List<String> attrs = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : spec.toStandardAttrs().entrySet()) {
            attrs.add(e.getKey());
            attrs.add(e.getValue());
        }
        splitter = b.add("Wiring", "Splitter", 620, 200, attrs.toArray(new String[0]));
        path(b, at(560, 200), at(620, 200));

        // 레지스터 파일: 서쪽 포트 x=760, RR1이 y=240
        Location rfLoc = placeSo(regfileCircuit, "RR1", at(760, 240));
        regfile = b.addSubcircuit(regfileCircuit, rfLoc.getX(), rfLoc.getY());
        b.commit();
        b = new CircuitBuilder(file, c);
        Map<String, Location> rf = ports(regfile);
        Location rs = splitter.getEnds().get(2).getLocation();
        Location rt = splitter.getEnds().get(3).getLocation();
        Location rd = splitter.getEnds().get(4).getLocation();
        path(b, rs, at(710, rs.getY()), at(710, rf.get("RR1").getY()), rf.get("RR1"));
        path(b, rt, at(700, rt.getY()), at(700, rf.get("RR2").getY()), rf.get("RR2"));
        path(b, rd, at(690, rd.getY()), at(690, rf.get("WR").getY()), rf.get("WR"));
        // 제어선: 짧은 선 끝에 터널(두 터널이 겹치지 않게 엇갈린다)
        Location rw = rf.get("RegWrite");
        Location rclk = rf.get("clk");
        path(b, at(715, rw.getY()), rw);
        path(b, at(745, rclk.getY()), rclk);
        b.add("Wiring", "Tunnel", 715, rw.getY(), "label", "RegWrite", "facing", "east");
        b.add("Wiring", "Tunnel", 745, rclk.getY(), "label", "clk", "facing", "east");

        // ALU: 서쪽 포트 x=1000, A가 RD1과 같은 높이
        Location aluLoc = placeSo(aluCircuit, "A", at(1000, rf.get("RD1").getY()));
        alu = b.addSubcircuit(aluCircuit, aluLoc.getX(), aluLoc.getY());
        b.commit();
        b = new CircuitBuilder(file, c);
        Map<String, Location> al = ports(alu);
        path(b, rf.get("RD1"), al.get("A"));
        Location rd2 = rf.get("RD2");
        path(b, rd2, at(940, rd2.getY()), at(940, al.get("B").getY()), al.get("B"));
        // ALUOp는 서쪽 변 맨 아래 포트다. 사용자 모양은 포트 표시가 상자 밖으로 나와 경계로 변을 가를 수 없어 직접 둔다
        Location aluOp = al.get("ALUOp");
        b.add("Wiring", "Tunnel", aluOp.getX(), aluOp.getY(), "width", "2", "label", "ALUOp", "facing", "east");
        Component zero = b.add("Wiring", "Pin", al.get("Zero").getX() + 60, al.get("Zero").getY(), "facing", "west",
                "output", "true", "label", "Zero");
        path(b, al.get("Zero"), zero.getLocation());

        // 데이터 메모리: Addr = ALU 결과, WriteData = RD2
        Location res = al.get("Result");
        Location dmAddrAt = at(res.getX() + 100, res.getY() + 160);
        dmem = b.add(mips, "Data Memory", dmAddrAt.getX() + 240, dmAddrAt.getY() + 40, "base", "0x0");
        Location dAddr = dmem.getEnds().get(0).getLocation();
        Location dWrite = dmem.getEnds().get(1).getLocation();
        path(b, res, at(res.getX() + 40, res.getY()), at(res.getX() + 40, dAddr.getY()), dAddr);
        path(b, at(940, al.get("B").getY()), at(940, dWrite.getY()), dWrite);
        b.tunnelOutward(dmem, 2, "MemWrite");
        b.tunnelOutward(dmem, 3, "MemRead");
        b.tunnelOutward(dmem, 4, "clk");

        // MemtoReg 멀티플렉서 → 레지스터 파일 WD(아래로 돌아온다)
        Location dRead = dmem.getEnds().get(5).getLocation();
        mux = b.add("Plexers", "Multiplexer", dRead.getX() + 100, dRead.getY() - 10, "width", "32",
                "enable", "false");
        Location in0 = mux.getEnds().get(0).getLocation();
        Location in1 = mux.getEnds().get(1).getLocation();
        path(b, dRead, at(dRead.getX() + 40, dRead.getY()), at(dRead.getX() + 40, in1.getY()), in1);
        path(b, at(res.getX() + 40, res.getY()), at(res.getX() + 40, res.getY() - 30),
                at(in0.getX() - 20, res.getY() - 30), at(in0.getX() - 20, in0.getY()), in0);
        b.tunnelOutward(mux, 2, "MemtoReg");
        Location out = mux.getEnds().get(3).getLocation();
        int bottom = dmem.getBounds().getY() + dmem.getBounds().getHeight() + 60;
        Location wd = rf.get("WD");
        path(b, out, at(out.getX() + 30, out.getY()), at(out.getX() + 30, bottom), at(640, bottom),
                at(640, wd.getY()), wd);

        // 제어 입력(교재 제어 유닛 대신 핀), 클럭
        String[][] ctl = {{"RegWrite", "1"}, {"MemtoReg", "1"}, {"MemWrite", "1"}, {"MemRead", "1"}, {"ALUOp", "2"}};
        for (int i = 0; i < ctl.length; i++) {
            Component pin = b.add("Wiring", "Pin", 100, 420 + 50 * i, "width", ctl[i][1], "tristate", "false",
                    "label", ctl[i][0], "labelloc", "north");
            b.tunnel(pin, 0, ctl[i][0]); // 원조 핀은 떠 있지 않게(값 0에서 시작)
        }
        Component clock = b.add("Wiring", "Clock", 100, 700);
        b.tunnel(clock, 0, "clk");

        // 테스트용 halt(PC 아래): PC가 0x10이면 1
        Component cmp = b.add("Arithmetic", "Comparator", 360, 460, "width", "32");
        b.tunnel(cmp, 0, "pc");
        b.add("Wiring", "Constant", 320, 470, "width", "32", "value", "0x10");
        halt = b.add("Wiring", "Pin", 420, 460, "facing", "west", "output", "true", "label", "halt");
        path(b, cmp.getEnds().get(3).getLocation(), halt.getLocation());
        b.add("Wiring", "Tunnel", 140, 80, "width", "32", "label", "pc", "facing", "east");
        b.commit();

        SplitterEdits.setNames(file, c, splitter.getLocation(), spec);
    }

    static int index(Component inst, String port) {
        for (int i = 0; i < inst.getEnds().size(); i++) {
            if (Kinds.portName(inst, i).equals(port)) {
                return i;
            }
        }
        throw new IllegalArgumentException(port);
    }

    static String program() {
        StringBuilder sb = new StringBuilder("hcs-words 1\n0");
        for (int w : PROGRAM) {
            sb.append(' ').append(String.format("%08x", w));
        }
        return sb.append('\n').toString();
    }

    /** 이 회로의 제어·연결 이름(테스트에서 넷을 확인할 때). */
    static final List<String> CONTROL = Arrays.asList("RegWrite", "MemtoReg", "MemWrite", "MemRead", "ALUOp", "clk");
}
