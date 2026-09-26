/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.record.Recording;
import kr.ac.hallym.hcs.app.record.RecordingTestSupport;

/**
 * C-05·C-06(C-10 "factorial.s로 $sp 이동·깊이·복귀"): ref-mips가 재귀 팩토리얼을 도는 기록에서 사이클마다 $sp(표시가
 * 없어 라벨 $29로 찾는다), 스택 깊이, Stack 패널의 $sp 화살표와 최고 수위, Data 패널의 .data 라벨을 읽는다. fact(6)은
 * 호출마다 8바이트를 쌓으므로 가장 깊을 때 7단(56바이트)이고, 모두 돌아오면 0이다.
 */
class MachineStateTest {
    @TempDir
    Path tmp;

    @Test
    void factorialStackDepthFollowsTheCalls() throws Exception {
        LogisimFile file = RecordingTestSupport.openRefMips(tmp);
        Path s = RecordingTestSupport.program("mips/factorial.s");
        RecordingTestSupport.load(file, s);
        Circuit main = file.getMainCircuit();
        CycleModel.Cpu cpu = CycleModel.findCpu(main);
        File circ = file.getLoader().getMainFile();
        Files.copy(s, new File(circ.getParentFile(), "factorial.s").toPath());
        @SuppressWarnings("unchecked")
        Attribute<Object> src = (Attribute<Object>) cpu.imem.getAttributeSet().getAttribute("source");
        CircuitMutation m = new CircuitMutation(main);
        m.set(cpu.imem, src, "factorial.s");
        m.execute();

        Project proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
        CircuitState root = new CircuitState(proj, main);
        root.getPropagator().propagate();
        Recording r = new Recording(main);
        r.restart(root, 0);
        CycleModel model = new CycleModel(main, r, cpu, ProgramSource.of(CycleModel.sourceFile(cpu, circ)));
        MachineState ms = new MachineState(model, file);
        assertFalse(ms.hasRegisterFile(), "nothing marked: registers are listed by path");

        long maxDepth = 0;
        int deepest = -1;
        // exit(Console Exit = 1)까지. 끝난 뒤에도 돌리면 CPU가 syscall 10 다음 코드를 계속 실행한다
        for (int step = 1; step <= 2000; step++) {
            Propagator p = root.getPropagator();
            p.tick();
            p.propagate();
            r.capture(root, step, false);
            if (step % 2 == 0 && RunUntil.halted(model, step / 2)) {
                break;
            }
            if (step % 2 == 0) {
                long d = MachineState.depth(ms.sp(step / 2));
                if (d > maxDepth) {
                    maxDepth = d;
                    deepest = step / 2;
                }
            }
        }
        int last = model.lastCycle();
        assertEquals(56, maxDepth, "fact(6)..fact(0): seven frames of 8 bytes");
        assertEquals(0, MachineState.depth(ms.sp(last)), "all calls returned");

        // $29는 라벨로 찾았다(ref-mips 레지스터 라벨 $1~$31)
        List<MachineState.Reg> regs = ms.registers(last);
        assertTrue(regs.stream().anyMatch(x -> x.number == 29), "$29 found by its label");

        // 가장 깊을 때 Stack 패널을 그 사이클 상태로 본다(기록에서 다시 만든 상태 = 캔버스가 보이는 상태)
        CircuitState then = r.reconstruct(CycleModel.stepOf(deepest));
        MachineState.Memory stack = null;
        MachineState.Memory data = null;
        for (MachineState.Memory mem : ms.memories(then, deepest)) {
            if (mem.stack) {
                stack = mem;
            } else {
                data = mem;
            }
        }
        assertNotNull(stack);
        assertEquals(56, stack.depth);
        // 최고 수위는 실제로 읽거나 쓴 곳까지: $sp를 막 내린 사이클에는 새 칸에 아직 쓰지 않았다
        assertEquals(48, stack.peak);
        assertEquals(14, stack.words.size(), "rows from the top down to $sp");
        assertTrue(stack.words.get(stack.words.size() - 1).sp, "the $sp arrow on the lowest word");
        assertTrue(stack.words.get(0).addr > stack.words.get(1).addr, "high addresses on top");
        Value sp = ms.sp(deepest);
        assertEquals(sp.toIntValue() & 0xffffffffL, stack.words.get(stack.words.size() - 1).addr);

        // 끝난 뒤: 깊이 0, 최고 수위는 그대로 56
        MachineState.Memory stackEnd = null;
        for (MachineState.Memory mem : ms.memories(root, last)) {
            if (mem.stack) {
                stackEnd = mem;
            }
        }
        assertEquals(0, stackEnd.depth);
        assertEquals(56, stackEnd.peak);

        // Data: .data의 msg("6! = ")가 라벨과 함께, 낮은 주소부터
        assertNotNull(data);
        MachineState.Word first = data.words.get(0);
        assertEquals(0x10010000L, first.addr);
        assertEquals("msg", first.label);
        assertEquals(0x3d202136, first.value);
    }
}
