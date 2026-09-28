/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.regress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.cburch.logisim.circuit.Simulator;

/** 원조 전파가 잡아 찍은 예외 보기(D-143): CI에서 실제로 찍힌 스택으로 원조 안의 것만 봐주는지 본다. */
class LogisimRaceTest {
    /** 36364477170 첫 시도: 원조 전파가 잡아 찍고 시뮬레이션을 끈 예외. */
    static final String PROPAGATOR = "java.lang.ArrayIndexOutOfBoundsException: Index 49 out of bounds for length 49\n"
            + "\tat java.base/java.util.HashMap.keysToArray(HashMap.java:958)\n"
            + "\tat com.cburch.logisim.util.SmallSet.toArray(SmallSet.java:120)\n"
            + "\tat com.cburch.logisim.circuit.CircuitState.processDirtyComponents(CircuitState.java:333)\n"
            + "\tat com.cburch.logisim.circuit.Propagator.propagate(Propagator.java:187)\n"
            + "\tat com.cburch.logisim.circuit.Simulator$PropagationManager.run(Simulator.java:114)\n";
    /** 전파 안에서 우리 부품이 던진 예외(가짜): 봐주면 안 된다. */
    static final String OURS_IN_PROPAGATOR = "java.lang.IllegalStateException: ours\n"
            + "\tat kr.ac.hallym.hcs.mips.DataMemory.propagate(DataMemory.java:10)\n"
            + "\tat com.cburch.logisim.circuit.Propagator.propagate(Propagator.java:187)\n"
            + "\tat com.cburch.logisim.circuit.Simulator$PropagationManager.run(Simulator.java:114)\n";
    /** 전파 밖의 로그와 예외(엔진 편집 실패 등): 원조 전파가 잡은 것이 아니다. */
    static final String OTHER = "[hcs-engine] error: edit.delete failed: java.util.ConcurrentModificationException\n"
            + "java.util.ConcurrentModificationException\n"
            + "\tat com.cburch.logisim.util.SmallSet$ArrayIterator.next(SmallSet.java:32)\n"
            + "\tat com.cburch.logisim.circuit.CircuitState$MyCircuitListener.circuitChanged(CircuitState.java:62)\n"
            + "\tat kr.ac.hallym.hcs.engine.edit.Intents.delete(Intents.java:320)\n";

    @Test
    void onlyTracesThroughThePropagationThreadAreCollected() {
        LogisimRace race = LogisimRace.watch();
        try {
            System.err.print(OTHER);
            assertTrue(race.propagatorTraces().isEmpty());
            System.err.print(PROPAGATOR);
            System.err.print(OTHER);
            assertEquals(1, race.propagatorTraces().size());
            assertEquals(PROPAGATOR, race.propagatorTraces().get(0));
        } finally {
            race.close();
        }
    }

    @Test
    void aStoppedSimulationNeedsANewLogisimOnlyPropagatorException() {
        Simulator sim = new Simulator();
        LogisimRace race = LogisimRace.watch();
        try {
            assertFalse(race.stoppedByLogisim(sim), "running");
            sim.setIsRunning(false);
            assertThrows(AssertionError.class, () -> race.stoppedByLogisim(sim), "no exception printed");
            System.err.print(PROPAGATOR);
            assertTrue(race.stoppedByLogisim(sim));
            assertThrows(AssertionError.class, () -> race.stoppedByLogisim(sim), "the same exception again");
            System.err.print(OURS_IN_PROPAGATOR);
            assertThrows(AssertionError.class, () -> race.stoppedByLogisim(sim), "our code in the propagator");
        } finally {
            race.close();
            sim.shutDown();
        }
    }
}
