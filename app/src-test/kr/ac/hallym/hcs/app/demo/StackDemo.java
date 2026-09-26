/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.demo;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 스크린샷용 작은 회로 tests/circ/stack-demo.circ의 생성기(C-06 검토, 체크리스트 10). 학생이 스택 동작을 확인하려고 그린
 * 것 같은 회로: 사이클마다 카운터가 1씩 오르고, $sp = 0x7FFFEFF8 − 4 × count를 계산해 레지스터 "$sp"에 넣고 같은
 * 주소의 Stack 칸에 count를 쓴다. 그래서 스택이 SPIM처럼 시작 $sp(0x7FFFEFFC) 아래로 4바이트씩 깊어진다. 자동 배치한
 * ref-mips와 달리 부품을 왼쪽에서 오른쪽으로 흐름대로 놓고 터널 이름을 붙였다.
 */
public final class StackDemo {
    public Component counter;
    public Component sp;
    public Component stack;

    private StackDemo() {
    }

    public static StackDemo build(LogisimFile file, Library mips) throws Exception {
        StackDemo d = new StackDemo();
        Circuit c = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, c);
        Component clock = b.add("Wiring", "Clock", 160, 480);
        b.tunnelOutward(clock, 0, "clk");

        // count → count × 4 → 0x7FFFEFF8 − count × 4
        d.counter = b.add("Memory", "Counter", 300, 200, "width", "32", "label", "count");
        b.tunnelOutward(d.counter, 0, "count");
        b.tunnelOutward(d.counter, 2, "clk");
        Component shift = b.add("Arithmetic", "Shifter", 520, 210, "width", "32");
        b.tunnelOutward(shift, 0, "count");
        // 상수는 짧은 선으로 곧장(이웃 포트의 터널과 겹치지 않게)
        com.cburch.logisim.data.Location in1 = CircuitBuilder.port(shift, 1);
        b.add("Wiring", "Constant", in1.getX() - 60, in1.getY(), "width", "5", "value", "0x2");
        b.wire(com.cburch.logisim.data.Location.create(in1.getX() - 60, in1.getY()), in1);
        b.tunnelOutward(shift, 2, "offset");
        Component sub = b.add("Arithmetic", "Subtractor", 760, 200, "width", "32");
        com.cburch.logisim.data.Location in0 = CircuitBuilder.port(sub, 0);
        b.add("Wiring", "Constant", in0.getX() - 100, in0.getY(), "width", "32", "value", "0x7fffeff8");
        b.wire(com.cburch.logisim.data.Location.create(in0.getX() - 100, in0.getY()), in0);
        b.tunnelOutward(sub, 1, "offset");
        b.tunnelOutward(sub, 2, "next");

        // $sp 레지스터(레지스터 패널의 $29)와 Stack
        d.sp = b.add("Memory", "Register", 1000, 200, "width", "32", "label", "$sp");
        b.tunnelOutward(d.sp, 1, "next");
        b.tunnelOutward(d.sp, 2, "clk");
        b.tunnelOutward(d.sp, 0, "sp");
        b.constant("one", 1, 1, 560, 560);
        d.stack = b.add(mips, "Stack", 1160, 460);
        b.tunnelOutward(d.stack, 0, "next");
        b.tunnelOutward(d.stack, 1, "count");
        b.tunnelOutward(d.stack, 2, "one");
        b.tunnelOutward(d.stack, 3, "one");
        b.tunnelOutward(d.stack, 4, "clk");
        b.commit();
        return d;
    }
}
