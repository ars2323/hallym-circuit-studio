/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.util.Locale;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;

import kr.ac.hallym.hcs.app.model.Names;

/**
 * 여기까지 실행(Run Until, C-04, PLAN.md 5.2)의 조건. 사이클이 끝날 때마다(짝수 스텝) 기록을 보고 멈출지 정한다.
 * 조건은 사실만 본다: PC 값, 명령어 종류, 표의 줄 값 바뀜, E·X 발생, halt·exit. 무한 반복에 대비해 최대 사이클
 * 수를 둔다. GUI 없이 테스트한다.
 */
public final class RunUntil {
    /** 조건 종류. */
    public enum Kind {
        /** PC가 이 값이 된다(그 명령어를 실행하기 전 사이클). */
        PC,
        /** 다음 명령어가 이 이름이다(예: beq). */
        INSTRUCTION,
        /** 표의 이 줄 값이 바뀐다. */
        ROW_CHANGES,
        /** 넷 하나라도 E가 되거나 정해져 있다가 X가 된다. */
        ERROR_OR_X,
        /** halt 출력 핀이 1이 되거나 Console이 exit 한다. */
        HALT
    }

    public static final int DEFAULT_MAX_CYCLES = 10_000;

    public final Kind kind;
    public final int pc;
    public final String mnemonic;
    public final CycleModel.Signal row;
    public final int maxCycles;

    private RunUntil(Kind kind, int pc, String mnemonic, CycleModel.Signal row, int maxCycles) {
        this.kind = kind;
        this.pc = pc;
        this.mnemonic = mnemonic == null ? null : mnemonic.trim().toLowerCase(Locale.ROOT);
        this.row = row;
        this.maxCycles = Math.max(1, maxCycles);
    }

    public static RunUntil pc(int pc, int max) {
        return new RunUntil(Kind.PC, pc, null, null, max);
    }

    public static RunUntil instruction(String mnemonic, int max) {
        return new RunUntil(Kind.INSTRUCTION, 0, mnemonic, null, max);
    }

    public static RunUntil rowChanges(CycleModel.Signal row, int max) {
        return new RunUntil(Kind.ROW_CHANGES, 0, null, row, max);
    }

    public static RunUntil errorOrX(int max) {
        return new RunUntil(Kind.ERROR_OR_X, 0, null, null, max);
    }

    public static RunUntil halt(int max) {
        return new RunUntil(Kind.HALT, 0, null, null, max);
    }

    /** 결과: 멈춘 이유. */
    public enum Result {
        /** 아직 멈추지 않는다. */
        RUNNING,
        /** 조건을 만났다. */
        MET,
        /** 최대 사이클 수에 닿았다. */
        LIMIT
    }

    /**
     * start 열에서 시작해 cycle 열까지 돌았을 때 멈출지. 조건은 start 다음 열부터 본다(지금 이미 조건인 상태에서
     * 누르면 한 번은 나아간다).
     */
    public Result check(CycleModel m, int start, int cycle) {
        if (cycle <= start) {
            return Result.RUNNING;
        }
        if (met(m, cycle)) {
            return Result.MET;
        }
        return cycle - start >= maxCycles ? Result.LIMIT : Result.RUNNING;
    }

    boolean met(CycleModel m, int cycle) {
        switch (kind) {
        case PC: {
            Value v = m.pc(cycle);
            return v != null && v.isFullyDefined() && v.toIntValue() == pc;
        }
        case INSTRUCTION: {
            Value v = m.instruction(cycle);
            return v != null && v.isFullyDefined() && mnemonic.equals(MipsText.mnemonic(v.toIntValue()));
        }
        case ROW_CHANGES:
            return m.changed(row, cycle);
        case ERROR_OR_X: {
            int to = CycleModel.stepOf(cycle);
            return !m.recording().problemSteps(to - 1, to).isEmpty();
        }
        case HALT:
            return halted(m, cycle);
        default:
            return false;
        }
    }

    /** halt 출력 핀이 1이거나 Console의 Exit가 1(서브회로 안이어도). */
    static boolean halted(CycleModel m, int cycle) {
        Circuit root = m.recording().circuit();
        int step = CycleModel.stepOf(cycle);
        for (java.util.List<Component> path : m.recording().paths()) {
            Circuit c = path.isEmpty() ? root
                    : ((com.cburch.logisim.circuit.SubcircuitFactory) path.get(path.size() - 1).getFactory())
                            .getSubcircuit();
            for (Component x : c.getNonWires()) {
                String f = x.getFactory().getName();
                Location at = null;
                if (f.equals("Pin") && "halt".equalsIgnoreCase(Names.label(x)) && !x.getEnds().isEmpty()) {
                    at = x.getEnd(0).getLocation();
                } else if (f.equals("Console") && x.getEnds().size() >= 5) {
                    at = x.getEnd(4).getLocation(); // Exit(lib-mips Console 포트 순서: Syscall, V0, A0, clk, Exit)
                }
                if (at != null && m.recording().value(path, at, step) == Value.TRUE) {
                    return true;
                }
            }
        }
        return false;
    }

    /** PC 글(0x00400034, 400034, 라벨)을 주소로. 읽을 수 없으면 null. */
    public static Integer parsePc(String text, ProgramSource src) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (src != null && src.addresses().containsKey(t)) {
            return src.addresses().get(t);
        }
        if (t.startsWith("0x") || t.startsWith("0X")) {
            t = t.substring(2);
        }
        try {
            return (int) Long.parseLong(t, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
