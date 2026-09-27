/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** 가까운 이름의 규칙(D-143): 확신할 때만, 후보 하나, 같은 폭. */
class NearNamesTest {
    @Test
    void closeNamesAreNear() {
        assertTrue(NearNames.near("RegWirte", "RegWrite"), "two letters swapped");
        assertTrue(NearNames.near("RegDest", "RegDst"), "one letter more");
        assertTrue(NearNames.near("MemtoReg", "MemToReg"), "case only");
        assertTrue(NearNames.near("rs", "RS"), "case only, even short");
        assertTrue(NearNames.near("ALUSrc", "ALUSrcA"));
        assertTrue(NearNames.near("MemWrite", "MemWrit"));
        assertTrue(NearNames.near("MemtoReg", "MemtoRg"), "one letter missing");
        assertTrue(NearNames.near("InstrMem", "InstMemm"), "8 letters or more: two edits");
    }

    @Test
    void differentSignalsAreNotNear() {
        assertFalse(NearNames.near("RegWrite", "RegWrite"), "the same name is not another name");
        assertFalse(NearNames.near("rs", "rt"), "short names differ by one letter all the time");
        assertFalse(NearNames.near("pc", "pc4"));
        assertFalse(NearNames.near("ALUOp0", "ALUOp1"), "a numbered family");
        assertFalse(NearNames.near("r10", "r11"));
        assertFalse(NearNames.near("PCSrc", "PCSrc2"), "only a digit more");
        assertFalse(NearNames.near("Zero", "Zer0x"), "short: two edits");
        assertFalse(NearNames.near("MemRead", "MemWrite"));
        assertFalse(NearNames.near("Branch", "Brunch1"), "6 letters: two edits");
        assertFalse(NearNames.near("RegWrite", "RgWrte"), "under 8 letters: two edits");
        assertFalse(NearNames.near("InstrMem", "InsMemmm"), "8 letters: three edits");
    }

    @Test
    void distanceCountsASwapAsOne() {
        assertEquals(1, NearNames.distance("regwirte", "regwrite"));
        assertEquals(1, NearNames.distance("regdest", "regdst"));
        assertEquals(0, NearNames.distance("a", "a"));
        assertEquals(3, NearNames.distance("", "abc"));
        assertEquals(1, NearNames.distance("ab", "ba"), "one swap");
    }

    @Test
    void aTunnelGetsTheOnlyNearNameOfItsWidth() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null));
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component recv = b.add("Wiring", "Tunnel", 100, 100, "width", "1", "label", "RegWirte");
        b.add("Wiring", "Tunnel", 100, 200, "width", "1", "label", "RegWrite");
        b.add("Wiring", "Tunnel", 100, 300, "width", "1", "label", "RegWrite");
        b.add("Wiring", "Tunnel", 100, 400, "width", "32", "label", "RegWrit");
        b.add("Wiring", "Tunnel", 100, 500, "width", "5", "label", "x");
        Component other = b.add("Wiring", "Tunnel", 300, 100, "width", "4", "label", "sum");
        Component none = b.add("Wiring", "Tunnel", 300, 200, "width", "1");
        b.commit();
        assertEquals("RegWrite", NearNames.forTunnel(f.getMainCircuit(), recv), "RegWrit is 32 bits");
        assertNull(NearNames.forTunnel(f.getMainCircuit(), other));
        assertNull(NearNames.forTunnel(f.getMainCircuit(), none), "no label");
        b.add("Wiring", "Tunnel", 100, 600, "width", "1", "label", "RegWirt");
        b.commit();
        assertNull(NearNames.forTunnel(f.getMainCircuit(), recv), "two near names of its width: not sure");
    }
}
