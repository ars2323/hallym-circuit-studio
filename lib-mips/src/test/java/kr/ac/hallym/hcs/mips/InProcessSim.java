/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 원조 2.7.1 엔진을 테스트 JVM 안에서 돌린다(TtyInterface와 같은 방식). 부품 내부 상태(Console의 출력 글자 등)를
 * 확인할 때 쓴다. MIPS 부품은 테스트 클래스패스의 {@link MipsLibrary}다.
 */
final class InProcessSim {
    final LogisimFile file;
    final Library mips = new MipsLibrary();
    final CircuitBuilder b;
    private CircuitState state;
    private Propagator prop;

    InProcessSim() throws Exception {
        file = CircuitBuilder.newFile(new Loader(null));
        file.addLibrary(mips);
        b = new CircuitBuilder(file, file.getMainCircuit());
    }

    void start() {
        b.commit();
        state = new CircuitState(new Project(file), file.getMainCircuit());
        prop = state.getPropagator();
        prop.propagate();
    }

    /** 클럭 한 주기: 상승 에지, 하강 에지. */
    void cycle() {
        for (int i = 0; i < 2; i += 1) {
            prop.tick();
            prop.propagate();
        }
    }

    Value port(Component c, int index) {
        return state.getValue(CircuitBuilder.port(c, index));
    }

    InstanceData data(Component c) {
        return state.getInstanceState(c).getData();
    }

    InstanceState state(Component c) {
        return state.getInstanceState(c);
    }
}
