/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.sim;

import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;

/**
 * 시뮬레이션이 꺼져 있을 때의 틱 보호(D-091). 원조 {@code Simulator.tick()}은 꺼진 시뮬레이터에 틱을 요청하면 그
 * 요청이 소비되지 않아 전파 스레드가 멈추지 않고 돌며(원조 PropagationManager의 동작) 전파 완료 알림을 끝없이 낸다.
 * 발진으로 시뮬레이션이 꺼진 뒤 1 Cycle을 누르면 그렇게 된다. 우리 코드는 틱 전에 늘 여기를 거친다: 꺼져 있으면 틱하지
 * 않고 상태 표시줄에 한 줄 알린다(위 띠의 Turn On과 같은 말).
 */
public final class TickGuard {
    private TickGuard() {
    }

    public static boolean canTick(Simulator sim) {
        return sim != null && sim.isRunning();
    }

    /** 켜져 있으면 틱하고 true. 꺼져 있으면 알리고 false. */
    public static boolean tick(Project proj) {
        Simulator sim = proj == null ? null : proj.getSimulator();
        if (!canTick(sim)) {
            if (proj != null) {
                SimControls.notice(proj, Messages.get("bar.simOff"));
            }
            return false;
        }
        sim.tick();
        return true;
    }
}
