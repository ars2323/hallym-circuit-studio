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

/** 원조 경합 가려내기(D-143): CI에서 실제로 찍힌 스택으로 원조 안의 것만 봐주는지 본다. */
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
    /** 36368172877: 엔진 스레드의 지우기가 원조 CircuitState 청취자 안에서 끝났다. */
    static final String DELETE = "[hcs-engine] error: edit.delete failed: java.util.ConcurrentModificationException\n"
            + "java.util.ConcurrentModificationException\n"
            + "\tat com.cburch.logisim.util.SmallSet$ArrayIterator.next(SmallSet.java:32)\n"
            + "\tat java.base/java.util.AbstractCollection.remove(AbstractCollection.java:299)\n"
            + "\tat com.cburch.logisim.circuit.CircuitState$MyCircuitListener.circuitChanged(CircuitState.java:62)\n"
            + "\tat com.cburch.logisim.circuit.Circuit.fireEvent(Circuit.java:175)\n"
            + "\tat com.cburch.logisim.circuit.Circuit.mutatorRemove(Circuit.java:402)\n"
            + "\tat com.cburch.logisim.circuit.CircuitTransaction.execute(CircuitTransaction.java:25)\n"
            + "\tat com.cburch.logisim.proj.Project.doAction(Project.java:389)\n"
            + "\tat kr.ac.hallym.hcs.engine.edit.Intents.delete(Intents.java:320)\n"
            + "\tat kr.ac.hallym.hcs.engine.rpc.Server.handleLine(Server.java:217)\n";
    /** 우리 청취자가 던진 예외(가짜): 원조 청취자가 아니므로 봐주면 안 된다. */
    static final String OURS_IN_EDIT = "[hcs-engine] error: edit.move failed: java.lang.IllegalStateException: ours\n"
            + "java.lang.IllegalStateException: ours\n"
            + "\tat app//kr.ac.hallym.hcs.app.record.Recorder$2.circuitChanged(Recorder.java:81)\n"
            + "\tat app//com.cburch.logisim.circuit.Circuit.fireEvent(Circuit.java:175)\n"
            + "\tat app//com.cburch.logisim.proj.Project.doAction(Project.java:389)\n"
            + "\tat app//kr.ac.hallym.hcs.engine.edit.Intents.move(Intents.java:200)\n";

    @Test
    void anEditThatEndedInLogisimsCircuitStateListenerIsTheRace() {
        LogisimRace race = LogisimRace.watch();
        try {
            System.err.print(DELETE);
            // 다른 스레드가 머리와 스택 사이에 찍어도 머리로 찾는다
            System.err.print("[hcs-engine] error: edit.redo failed: java.lang.NullPointerException\n");
            System.err.print(PROPAGATOR);
            System.err.print("java.lang.NullPointerException\n"
                    + "\tat com.cburch.logisim.util.SmallSet.add(SmallSet.java:179)\n"
                    + "\tat com.cburch.logisim.circuit.CircuitState.markPointAsDirty(CircuitState.java:297)\n"
                    + "\tat com.cburch.logisim.circuit.Propagator.checkComponentEnds(Propagator.java:368)\n"
                    + "\tat com.cburch.logisim.circuit.CircuitState$MyCircuitListener.circuitChanged(CircuitState.java:61)\n"
                    + "\tat kr.ac.hallym.hcs.app.edit.RedoStack$Redone.doIt(RedoStack.java:77)\n");
            System.err.print(OURS_IN_EDIT);
            assertTrue(race.editFailedInLogisimListener("edit.delete"));
            assertTrue(race.editFailedInLogisimListener("edit.redo"));
            assertFalse(race.editFailedInLogisimListener("edit.move"), "our listener is not Logisim's race");
            assertFalse(race.editFailedInLogisimListener("edit.setAttr"), "nothing logged");
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
            assertEquals(1, race.propagatorTraces().size());
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
