/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.record.RecordingTestSupport;

/**
 * C-07 필드 경로: demo-datapath의 스플리터 팔(op, rs, rt, rd, shamt, funct)에서 나가는 선. rs는 regfile의 RR1에,
 * rt는 RR2에 닿고, 팔에서 스플리터를 거슬러 합친 버스(Instruction Memory 출력)로 퍼지지 않으며, 레지스터 파일을 지나
 * 읽은 값(RD1, RD2)으로도 번지지 않는다(그것은 필드가 아니라 레지스터 값이다). 형식에 없는 필드는 칠하지 않는다.
 */
class FieldPathsTest {
    @TempDir
    Path tmp;

    static boolean reaches(Set<Wire> wires, Location at) {
        for (Wire w : wires) {
            if (w.getEnd0().equals(at) || w.getEnd1().equals(at)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void namedArmsColorTheirPaths() throws Exception {
        LogisimFile file = RecordingTestSupport.openCirc(tmp, "demo-datapath.circ");
        CircExtensions.afterOpen(file, file.getLoader().getMainFile());
        Circuit main = file.getMainCircuit();
        Component regfile = null;
        Component imem = null;
        for (Component c : main.getNonWires()) {
            if (c.getFactory() instanceof SubcircuitFactory && c.getFactory().getName().equals("regfile")) {
                regfile = c;
            }
            if (c.getFactory().getName().equals("Instruction Memory")) {
                imem = c;
            }
        }
        Location rr1 = null;
        Location rr2 = null;
        for (int i = 0; i < regfile.getEnds().size(); i++) {
            String n = Kinds.portName(regfile, i);
            if (n.equals("RR1")) {
                rr1 = regfile.getEnd(i).getLocation();
            } else if (n.equals("RR2")) {
                rr2 = regfile.getEnd(i).getLocation();
            }
        }
        int add = 0x00221824; // and $v1, $at, $v0: R 형식
        Map<String, Set<Wire>> paths = FieldPaths.of(file, main, FieldPaths.fieldsOf(add));
        assertEquals(Arrays.asList("op", "rs", "rt", "rd", "shamt", "funct"), FieldPaths.fieldsOf(add));
        assertTrue(paths.containsKey("rs") && reaches(paths.get("rs"), rr1), "rs reaches RR1: " + paths.keySet());
        assertTrue(reaches(paths.get("rt"), rr2), "rt reaches RR2");
        assertFalse(reaches(paths.get("rs"), rr2), "rs does not reach RR2");
        for (int i = 0; i < regfile.getEnds().size(); i++) {
            String n = Kinds.portName(regfile, i);
            if (n.equals("RD1") || n.equals("RD2")) {
                Location rd = regfile.getEnd(i).getLocation();
                for (Map.Entry<String, Set<Wire>> e : paths.entrySet()) {
                    assertFalse(reaches(e.getValue(), rd), e.getKey() + " stops at the register file, not " + n);
                }
            }
        }
        Location instr = imem.getEnd(1).getLocation();
        for (Map.Entry<String, Set<Wire>> e : paths.entrySet()) {
            assertFalse(reaches(e.getValue(), instr), e.getKey() + " stays off the combined instruction bus");
        }
        // I 형식에는 rd가 없다
        Map<String, Set<Wire>> itype = FieldPaths.of(file, main, FieldPaths.fieldsOf(0x8e0a0000));
        assertFalse(itype.containsKey("rd"));
        assertTrue(itype.containsKey("rs"));
        assertEquals("rs", FieldPaths.fieldName("RS"));
        assertEquals("imm", FieldPaths.fieldName("immediate"));
        assertEquals(null, FieldPaths.fieldName("ALUOp"));
    }
}
