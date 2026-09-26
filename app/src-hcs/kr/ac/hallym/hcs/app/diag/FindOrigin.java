/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.gui.main.SelectionActions;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.InstancePaths;
import kr.ac.hallym.hcs.app.model.OriginTrace;
import kr.ac.hallym.hcs.app.record.Recorder;

/**
 * 선 우클릭 "Find E/X Origin"(D-01, PLAN.md 4.3): 보이는 상태(사이클 뷰에서 고른 사이클이면 그 사이클)에서 그 선의
 * E·X가 처음 생긴 곳을 찾아 그 서브회로 인스턴스로 들어가 원인을 고르고, 상태 표시줄에 원인 문장을 한 줄로 보인다.
 */
public final class FindOrigin {
    private FindOrigin() {
    }

    /** 이 선의 값이 정해지지 않았는가(메뉴를 켤지). */
    public static boolean undefined(Project proj, Wire w) {
        CircuitState s = proj.getCircuitState();
        Value v = s == null ? null : s.getValue(w.getEnd0());
        return v != null && v.getWidth() > 0 && !v.isFullyDefined();
    }

    /** 찾는다. 찾은 원인(없으면 null). */
    public static OriginTrace.Origin find(Project proj, Circuit circuit, Wire w) {
        CircuitState cur = proj.getCircuitState();
        if (cur == null) {
            return null;
        }
        CircuitState root = cur;
        while (root.getParentState() != null) {
            root = root.getParentState();
        }
        List<Component> path = Recorder.pathOf(cur);
        OriginTrace t = new OriginTrace(root.getCircuit(), OriginTrace.live(root));
        return t.find(t.node(path, circuit, w.getEnd0()), 0);
    }

    /** 메뉴 동작: 찾고, 원인으로 가서 고르고, 알린다. */
    public static void run(Project proj, Circuit circuit, Wire w) {
        OriginTrace.Origin o = find(proj, circuit, w);
        if (o == null) {
            kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, Messages.get("origin.none"));
            return;
        }
        CircuitState root = proj.getCircuitState();
        while (root.getParentState() != null) {
            root = root.getParentState();
        }
        CircuitState at = InstancePaths.stateFor(root, o.node.instances);
        if (at != null && proj.getCircuitState() != at) {
            proj.setCircuitState(at);
        }
        List<Component> comps = OriginText.components(o);
        kr.ac.hallym.hcs.app.props.QuickBar.markQuiet(proj, comps);
        if (proj.getSelection() != null) {
            proj.doAction(SelectionActions.dropAll(proj.getSelection()));
            proj.getSelection().addAll(comps);
            proj.getSelection().addAll(OriginText.wires(o));
        }
        kr.ac.hallym.hcs.app.sim.SimControls.notice(proj,
                Messages.get("origin.found", OriginText.cause(root.getCircuit(), o)));
    }
}
