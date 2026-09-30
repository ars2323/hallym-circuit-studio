/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.SimulatorListener;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.std.wiring.Pin;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.engine.doc.Doc;

/**
 * V-16(D-173): the original {@link Simulator} keeps its listeners in a plain ArrayList that its own thread copies on
 * every propagationCompleted. A listener added on the engine thread while that copy is made can come out as null, and
 * the NullPointerException ends the simulator thread (CI run 36584750323: {@code "l" is null} at
 * Simulator.firePropagationCompleted, then file.open answered with an empty recording after the 5 s wait). The engine
 * therefore adds every simulator listener while the simulator has not run yet: no propagation before attaching, and
 * nothing added after file.open answers. The race itself cannot be forced; these tests pin the order that removes it.
 */
class SimulatorListenersTest {
    @TempDir
    Path tmp;

    InProcess e;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
    }

    @AfterEach
    void stop() {
        e.close();
    }

    @Test
    void aFileIsNotPropagatedBeforeTheEngineHasAttachedItsListeners() throws Exception {
        File f = Fixtures.counter(tmp);
        // Only the document, as file.open makes it before Engine.attach adds the listeners
        Doc d = e.onEngine(() -> e.engine.files().open(f, false, new ArrayList<>()));
        try {
            Thread.sleep(500); // the simulator thread would have propagated by now if anything had asked it to
            Value q = e.onEngine(() -> {
                Component pin = null;
                for (Component c : d.file().getMainCircuit().getNonWires()) {
                    if (c.getFactory() instanceof Pin) {
                        pin = c;
                    }
                }
                assertNotNull(pin);
                return d.project().getCircuitState().getValue(pin.getLocation());
            });
            assertFalse(q.isFullyDefined(), "the circuit was propagated before its listeners were attached: q = " + q);
        } finally {
            e.onEngine(() -> {
                e.engine.files().close(d);
                return null;
            });
        }
    }

    @Test
    void noSimulatorListenerIsAddedAfterFileOpenAnswers() throws Exception {
        JsonObject r = e.client.callObject("file.open", params("path", Fixtures.counter(tmp).getPath()));
        String fileId = r.get("fileId").getAsString();
        String main = r.get("main").getAsString();
        List<SimulatorListener> atOpen = listeners(fileId);
        // An edit (its selection used to make the hidden Canvas, which adds the original TickCounter and its
        // project listener, while the clock could be running), a Poke and cycles
        e.client.callObject("edit.select", params("fileId", fileId, "circuitId", main, "at", new int[] {-50, -50}));
        e.client.call("sim.cycles", params("fileId", fileId, "n", 2));
        e.client.callObject("edit.select", params("fileId", fileId, "circuitId", main, "rect",
                new int[] {0, 0, 1000, 1000}));
        e.client.call("record.state", params("fileId", fileId));
        assertEquals(atOpen, listeners(fileId), "a simulator listener was added while the simulator could be running");
        assertEquals(1, atOpen.stream().filter(l -> l.getClass().getName().endsWith("TickCounter")).count(),
                "the hidden Canvas is made when the file opens");
    }

    /** The simulator's listener list, read on the engine thread (the only thread that changes it). */
    private List<SimulatorListener> listeners(String fileId) throws Exception {
        return e.onEngine(() -> {
            Simulator sim = e.engine.files().get(fileId).project().getSimulator();
            Field f = Simulator.class.getDeclaredField("listeners");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<SimulatorListener> list = (List<SimulatorListener>) f.get(sim);
            return new ArrayList<>(list);
        });
    }
}
