/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.record;

import java.util.Locale;

import com.cburch.logisim.data.Value;

import kr.ac.hallym.hcs.app.cycle.CycleModel;
import kr.ac.hallym.hcs.app.cycle.RunUntil;
import kr.ac.hallym.hcs.app.record.Recording;
import kr.ac.hallym.hcs.mips.disasm.Disassembler;

/**
 * Run Until의 조건 하나와 그 실행 상태(N-14, v1 C-04·D-075). 조건 판정은 v1 {@link RunUntil}을 그대로 쓰고(PC, 줄 값
 * 바뀜, E·X, halt·exit), 명령어 이름만 lib-mips 디스어셈블러(D-127)의 이름과 비교한다(v1의 옛 디스어셈블러 대신: 사이클
 * 표와 Instruction 패널이 보이는 이름과 같게). 조건은 사실만 본다(규칙 2.6).
 */
final class Until {
    /** 규약 이름: pc, instruction, row, errorOrX, halt. */
    final String kind;
    /** 학생이 적은 값(PC 글·명령어 이름·줄 id). 없으면 null. */
    final String value;
    final int maxCycles;
    final Recording recording;
    final int startCycle;
    private final RunUntil v1;
    private final String mnemonic;
    /** 요청해 둔 마지막 스텝(짝수). 기록이 여기까지 적어야 판정한다. */
    int requestedTo;

    Until(String kind, String value, RunUntil v1, String mnemonic, int maxCycles, Recording recording,
            int startCycle) {
        this.kind = kind;
        this.value = value;
        this.v1 = v1;
        this.mnemonic = mnemonic == null ? null : mnemonic.trim().toLowerCase(Locale.ROOT);
        this.maxCycles = Math.max(1, maxCycles);
        this.recording = recording;
        this.startCycle = startCycle;
    }

    /** start 다음 열부터 본다(이미 조건인 곳에서 누르면 한 번은 나아간다, D-075). */
    RunUntil.Result check(CycleModel m, int cycle) {
        if (cycle <= startCycle) {
            return RunUntil.Result.RUNNING;
        }
        if (mnemonic != null) {
            Value v = m.instruction(cycle);
            if (v != null && v.isFullyDefined() && mnemonic.equals(Disassembler.mnemonic(v.toIntValue()))) {
                return RunUntil.Result.MET;
            }
            return cycle - startCycle >= maxCycles ? RunUntil.Result.LIMIT : RunUntil.Result.RUNNING;
        }
        return v1.check(m, startCycle, cycle);
    }
}
