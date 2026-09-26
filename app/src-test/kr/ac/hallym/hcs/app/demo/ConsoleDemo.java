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
 * 스크린샷용 작은 회로 tests/circ/console-demo.circ의 생성기(C-09, 체크리스트 10). 학생이 Console 부품을 시험하려고
 * 그린 것 같은 회로: 사이클마다 카운터가 오르고, A0 = 'A' + count, V0 = 11(print_char)로 글자를 하나씩 찍는다.
 * count가 6이면 V0 = 10(exit)이라 "ABCDEF"를 찍고 끝난다. 부품은 흐름대로 왼쪽에서 오른쪽에 두고 이름 붙인 터널로 잇는다.
 */
public final class ConsoleDemo {
    public Component console;

    private ConsoleDemo() {
    }

    public static ConsoleDemo build(LogisimFile file, Library mips) throws Exception {
        ConsoleDemo d = new ConsoleDemo();
        Circuit c = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, c);
        Component clock = b.add("Wiring", "Clock", 160, 480);
        b.tunnelOutward(clock, 0, "clk");

        Component counter = b.add("Memory", "Counter", 300, 200, "width", "32", "label", "count");
        b.tunnelOutward(counter, 0, "count");
        b.tunnelOutward(counter, 2, "clk");
        // A0 = 'A' + count
        b.constant("letterA", 32, 0x41, 420, 100);
        Component add = b.add("Arithmetic", "Adder", 560, 200, "width", "32");
        b.tunnelOutward(add, 0, "letterA");
        b.tunnelOutward(add, 1, "count");
        b.tunnelOutward(add, 2, "A0");
        // V0 = count == 6 ? 10 : 11
        b.constant("six", 32, 6, 420, 330);
        Component cmp = b.add("Arithmetic", "Comparator", 560, 360, "width", "32");
        b.tunnelOutward(cmp, 0, "count");
        b.tunnelOutward(cmp, 1, "six");
        b.tunnelOutward(cmp, 3, "done");
        b.constant("printChar", 32, 11, 660, 440);
        b.constant("exit", 32, 10, 660, 500);
        Component mux = b.add("Plexers", "Multiplexer", 820, 470, "width", "32", "enable", "false");
        b.tunnelOutward(mux, 0, "printChar");
        b.tunnelOutward(mux, 1, "exit");
        b.tunnelOutward(mux, 2, "done");
        b.tunnelOutward(mux, 3, "V0");

        b.constant("go", 1, 1, 900, 180);
        d.console = b.add(mips, "Console", 1300, 320, "label", "out");
        b.tunnelOutward(d.console, 0, "go");
        b.tunnelOutward(d.console, 1, "V0");
        b.tunnelOutward(d.console, 2, "A0");
        b.tunnelOutward(d.console, 3, "clk");
        b.commit();
        return d;
    }
}
