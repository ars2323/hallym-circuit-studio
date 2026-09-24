/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;

/** 다중 진법 Probe(PLAN.md 5.1). */
class RadixProbeTest {
    @TempDir
    Path tmp;

    static Value v(int width, int value) {
        return Value.createKnown(BitWidth.create(width), value);
    }

    @Test
    void formatsThreeRadixes() {
        assertEquals("0x0000002a", RadixProbe.hex(v(32, 42)));
        assertEquals("0000 0000 0000 0000 0000 0000 0010 1010", RadixProbe.bin(v(32, 42)));
        assertEquals("-1", RadixProbe.dec(v(32, -1), true));
        assertEquals("4294967295", RadixProbe.dec(v(32, -1), false));
        assertEquals("-2147483648", RadixProbe.dec(v(32, 0x80000000), true));
        assertEquals("0x15", RadixProbe.hex(v(5, 21)));
        assertEquals("1 0101", RadixProbe.bin(v(5, 21)));
        assertEquals("-11", RadixProbe.dec(v(5, 21), true));
        assertEquals("21", RadixProbe.dec(v(5, 21), false));
        assertEquals("0x1", RadixProbe.hex(v(1, 1)));
        assertEquals("1", RadixProbe.dec(v(1, 1), true)); // 1비트는 부호로 보지 않는다
    }

    @Test
    void undefinedAndErrorBitsShowPerDigit() {
        Value[] bits = new Value[8]; // Value.create는 0번 비트부터
        for (int i = 0; i < 8; i += 1) {
            bits[i] = i < 4 ? Value.TRUE : Value.UNKNOWN;
        }
        Value partial = Value.create(bits);
        assertEquals("0xxf", RadixProbe.hex(partial));
        assertEquals("xxxx 1111", RadixProbe.bin(partial));
        assertEquals("x", RadixProbe.dec(partial, true));
        bits[7] = Value.ERROR;
        assertEquals("0xEf", RadixProbe.hex(Value.create(bits)));
        assertEquals("E", RadixProbe.dec(Value.createError(BitWidth.create(8)), true));
    }

    @Test
    void primaryRadixComesFirst() {
        assertArrayEquals(new String[] {"0x0000002a", "42", "0000 0000 0000 0000 0000 0000 0010 1010"},
                RadixProbe.lines(v(32, 42), RadixProbe.HEX, true));
        assertArrayEquals(new String[] {"42", "0x0000002a", "0000 0000 0000 0000 0000 0000 0010 1010"},
                RadixProbe.lines(v(32, 42), RadixProbe.DEC, true));
        assertArrayEquals(new String[] {"0000 0000 0000 0000 0000 0000 0010 1010", "0x0000002a", "42"},
                RadixProbe.lines(v(32, 42), RadixProbe.BIN, true));
    }

    @Test
    void widthSetsTheSize() {
        RadixProbe f = new RadixProbe();
        com.cburch.logisim.data.AttributeSet as = f.createAttributeSet();
        assertEquals(260, f.getOffsetBounds(as).getWidth()); // 32비트: 2진수 39자
        as.setValue(StdAttr.WIDTH, BitWidth.create(8));
        assertEquals(80, f.getOffsetBounds(as).getWidth());
    }

    @Test
    void pokingCyclesThePrimaryRadix() throws Exception {
        InProcessSim sim = new InProcessSim();
        Component probe = sim.b.add(sim.mips, "Radix Probe", 400, 100, "radix", "dec");
        sim.start();
        InstanceState state = sim.state(probe);
        RadixProbe.Poker poker = new RadixProbe.Poker();
        assertEquals(RadixProbe.DEC, RadixProbe.primary(state, (RadixProbe.State) state.getData()));
        int[] expected = {RadixProbe.BIN, RadixProbe.HEX, RadixProbe.DEC};
        for (int want : expected) {
            poker.mousePressed(state, null);
            assertEquals(want, RadixProbe.primary(state, (RadixProbe.State) state.getData()));
        }
    }

    @Test
    void loadsAndRunsInTheOriginalLogisim() throws Exception {
        OriginalLogisim o = new OriginalLogisim(tmp);
        o.b.constant("x", 32, 42, 100, 100);
        Component probe = o.b.add(o.mips, "Radix Probe", 300, 100, "radix", "bin", "signed", "false");
        o.b.tunnel(probe, 0, "x");
        o.b.constant("halt", 1, 1, 100, 300);
        o.b.output("halt", 1, 600, 300);
        // 출력 핀이 halt뿐이라 값 열이 없다. run()이 종료 코드 0(부품 로드와 halt)을 확인한다.
        assertEquals(0, o.run("probe").size());
    }
}
