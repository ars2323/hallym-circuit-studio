/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.flow;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * P-07 경로 테스트 회로(tests/circ/flow/). 원조 2.7.1 부품만 쓰고 원조 저장 코드로 저장한다. 일부는 일부러 고리나 떠
 * 있는 선택 입력을 둔다(정적 진단 "정상 회로 0건" 대상 밖이라 하위 폴더에 둔다).
 */
final class FlowCircuits {
    interface Maker {
        void make(LogisimFile f, CircuitBuilder b);
    }

    static final Map<String, Maker> ALL = new LinkedHashMap<>();

    static {
        // A → NOT → AND(B) → OR(C) → Y
        ALL.put("gate-chain", (f, b) -> {
            b.input("A", 1, 100, 100);
            b.input("B", 1, 100, 200);
            b.input("C", 1, 100, 300);
            Component n = b.add("Gates", "NOT Gate", 300, 100, "label", "n1");
            b.tunnel(n, 1, "A");
            b.tunnel(n, 0, "na");
            Component and = b.add("Gates", "AND Gate", 500, 150, "inputs", "2", "label", "g1");
            b.tunnel(and, 1, "na");
            b.tunnel(and, 2, "B");
            b.tunnel(and, 0, "x");
            Component or = b.add("Gates", "OR Gate", 700, 200, "inputs", "2", "label", "g2");
            b.tunnel(or, 1, "x");
            b.tunnel(or, 2, "C");
            b.tunnel(or, 0, "Y");
            b.output("Y", 1, 900, 200);
        });
        // A → NOT → Register R → NOT → Y
        ALL.put("register", (f, b) -> {
            b.input("A", 1, 100, 100);
            b.add("Wiring", "Clock", 100, 300, "label", "clk");
            Component n1 = b.add("Gates", "NOT Gate", 300, 100, "label", "n1");
            b.tunnel(n1, 1, "A");
            b.tunnel(n1, 0, "d");
            Component r = b.add("Memory", "Register", 500, 100, "width", "1", "label", "R");
            b.tunnel(r, 1, "d");
            b.tunnel(r, 0, "q");
            Component n2 = b.add("Gates", "NOT Gate", 700, 100, "label", "n2");
            b.tunnel(n2, 1, "q");
            b.tunnel(n2, 0, "Y");
            b.output("Y", 1, 900, 100);
        });
        // A가 같은 이름 터널 셋(A 옆 하나, NOT 둘)으로 퍼진다
        ALL.put("tunnels", (f, b) -> {
            b.input("A", 1, 100, 200);
            Component n1 = b.add("Gates", "NOT Gate", 400, 100, "label", "t1");
            b.tunnel(n1, 1, "A");
            b.tunnel(n1, 0, "Y1");
            Component n2 = b.add("Gates", "NOT Gate", 400, 300, "label", "t2");
            b.tunnel(n2, 1, "A");
            b.tunnel(n2, 0, "Y2");
            b.output("Y1", 1, 700, 100);
            b.output("Y2", 1, 700, 300);
        });
        // 서브회로 blk: y = NOT a, z = NOT b. main: A → a, B → b
        ALL.put("subcircuit", (f, b) -> {
            Circuit sub = new Circuit("blk");
            f.addCircuit(sub);
            CircuitBuilder sb = new CircuitBuilder(f, sub);
            sb.input("a", 1, 100, 100);
            sb.input("b", 1, 100, 300);
            Component na = sb.add("Gates", "NOT Gate", 300, 100, "label", "ny");
            sb.tunnel(na, 1, "a");
            sb.tunnel(na, 0, "y");
            Component nb = sb.add("Gates", "NOT Gate", 300, 300, "label", "nz");
            sb.tunnel(nb, 1, "b");
            sb.tunnel(nb, 0, "z");
            sb.output("y", 1, 500, 100);
            sb.output("z", 1, 500, 300);
            sb.commit();
            b.input("A", 1, 100, 100);
            b.input("B", 1, 100, 300);
            Component inst = b.addSubcircuit(sub, 400, 200);
            for (int i = 0; i < inst.getEnds().size(); i++) {
                String p = Kinds.portName(inst, i);
                b.tunnel(inst, i, p.equals("a") ? "A" : p.equals("b") ? "B" : p.toUpperCase());
            }
            b.output("Y", 1, 700, 100);
            b.output("Z", 1, 700, 300);
        });
        // Instruction[31:0] → R형 스플리터 → rs·rt를 10비트로 합침 → [4:0]·[9:5]로 다시 나눔
        ALL.put("splitter", (f, b) -> {
            b.input("Instruction", 32, 100, 200);
            String[] bits = new String[64];
            int k = 0;
            // incoming 32, fanout 6: [5:0] funct=5, [10:6]=4, [15:11]=3, [20:16]=2, [25:21]=1, [31:26]=0
            int[] arm = new int[32];
            for (int i = 0; i < 32; i++) {
                arm[i] = i <= 5 ? 5 : i <= 10 ? 4 : i <= 15 ? 3 : i <= 20 ? 2 : i <= 25 ? 1 : 0;
            }
            for (int i = 0; i < 32; i++) {
                bits[k++] = "bit" + i;
                bits[k++] = Integer.toString(arm[i]);
            }
            String[] attrs = new String[4 + 64];
            attrs[0] = "fanout";
            attrs[1] = "6";
            attrs[2] = "incoming";
            attrs[3] = "32";
            System.arraycopy(bits, 0, attrs, 4, 64);
            Component s1 = b.add("Wiring", "Splitter", 300, 200, attrs);
            b.tunnel(s1, 0, "Instruction");
            String[] names = {"op", "rs", "rt", "rd", "shamt", "funct"};
            for (int i = 0; i < 6; i++) {
                b.tunnel(s1, i + 1, names[i]);
            }
            // rs(비트 0~4)와 rt(비트 5~9)를 10비트 rsrt로
            String[] a2 = new String[4 + 20];
            a2[0] = "fanout";
            a2[1] = "2";
            a2[2] = "incoming";
            a2[3] = "10";
            for (int i = 0; i < 10; i++) {
                a2[4 + 2 * i] = "bit" + i;
                a2[5 + 2 * i] = i < 5 ? "0" : "1";
            }
            Component s2 = b.add("Wiring", "Splitter", 600, 300, a2);
            b.tunnel(s2, 0, "rsrt");
            b.tunnel(s2, 1, "rs");
            b.tunnel(s2, 2, "rt");
            // rsrt를 다시 [4:0] lo, [9:5] hi로(여기서는 방향이 반대인 스플리터: 묶인 쪽이 입력)
            Component s3 = b.add("Wiring", "Splitter", 800, 400, a2);
            b.tunnel(s3, 0, "rsrt");
            b.tunnel(s3, 1, "lo");
            b.tunnel(s3, 2, "hi");
            Component nlo = b.add("Gates", "NOT Gate", 1000, 400, "width", "5", "label", "nlo");
            b.tunnel(nlo, 1, "lo");
            b.tunnel(nlo, 0, "LO");
            Component nhi = b.add("Gates", "NOT Gate", 1000, 500, "width", "5", "label", "nhi");
            b.tunnel(nhi, 1, "hi");
            b.tunnel(nhi, 0, "HI");
            b.output("LO", 5, 1200, 400);
            b.output("HI", 5, 1200, 500);
            Component nop = b.add("Gates", "NOT Gate", 1000, 100, "width", "6", "label", "nop");
            b.tunnel(nop, 1, "op");
            b.tunnel(nop, 0, "OP");
            b.output("OP", 6, 1200, 100);
        });
        // A → MUX 0, B → MUX 1, 선택 S(핀) → MUX, 출력 Y
        ALL.put("mux", (f, b) -> {
            b.input("A", 4, 100, 100);
            b.input("B", 4, 100, 200);
            b.input("S", 1, 100, 300);
            Component m = b.add("Plexers", "Multiplexer", 400, 200, "width", "4");
            b.tunnel(m, 0, "A");
            b.tunnel(m, 1, "B");
            b.tunnel(m, 2, "S");
            b.tunnel(m, m.getEnds().size() - 1, "Y"); // 출력은 마지막 포트(enable이 있으면 그 뒤)
            b.output("Y", 4, 700, 200);
        });
        // A → OR → NOT → OR의 다른 입력(조합 고리), NOT 출력 → Y
        ALL.put("loop", (f, b) -> {
            b.input("A", 1, 100, 100);
            Component or = b.add("Gates", "OR Gate", 300, 100, "inputs", "2", "label", "o");
            b.tunnel(or, 1, "A");
            b.tunnel(or, 2, "back");
            b.tunnel(or, 0, "x");
            Component not = b.add("Gates", "NOT Gate", 500, 100, "label", "nx");
            b.tunnel(not, 1, "x");
            b.tunnel(not, 0, "back");
            Component buf = b.add("Gates", "Buffer", 700, 200, "label", "bf");
            b.tunnel(buf, 1, "back");
            b.tunnel(buf, 0, "Y");
            b.output("Y", 1, 900, 200);
        });
    }

    private FlowCircuits() {
    }

    /** name 회로를 dir/name.circ로 만든다. */
    static File make(String name, File dir) throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), dir);
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        ALL.get(name).make(f, b);
        b.commit();
        File out = new File(dir, name + ".circ");
        CircuitBuilder.save(f, out);
        return out;
    }
}
