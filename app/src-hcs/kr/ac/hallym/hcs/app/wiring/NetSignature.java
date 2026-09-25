/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.wiring;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;

import kr.ac.hallym.hcs.app.model.Netlist;

/**
 * 넷리스트의 모양: 넷마다 이어진 포트 집합(#81). 옮긴 부품의 포트는 빼고 본다(옮긴 부품의 연결은 바뀌어도 되지만, 그
 * 밖의 넷은 그대로여야 한다). 선은 포트가 아니므로 모양에 들어가지 않는다.
 */
public final class NetSignature {
    private NetSignature() {
    }

    public static Set<Set<Netlist.PortRef>> of(Circuit circuit, Collection<? extends Component> exclude) {
        Set<Component> ex = Collections.newSetFromMap(new IdentityHashMap<>());
        ex.addAll(exclude);
        Set<Set<Netlist.PortRef>> ret = new HashSet<>();
        for (Netlist.Net n : Netlist.of(circuit).nets()) {
            Set<Netlist.PortRef> ports = new HashSet<>();
            for (Netlist.PortRef p : n.ports()) {
                if (!ex.contains(p.component)) {
                    ports.add(p);
                }
            }
            if (!ports.isEmpty()) {
                ret.add(ports);
            }
        }
        return ret;
    }

    /**
     * 옮긴 부품의 연결까지 본 모양: 옮긴 부품의 포트는 옮기기 전 자리(지금 자리 − (dx, dy))로 적는다. 옮기기 전
     * (dx = dy = 0)과 옮긴 뒤가 같으면 옮긴 부품의 연결도 그대로다.
     */
    public static Set<Set<Object>> full(Circuit circuit, Collection<? extends Component> moved, int dx, int dy) {
        Set<Component> mv = Collections.newSetFromMap(new IdentityHashMap<>());
        mv.addAll(moved);
        Set<Set<Object>> ret = new HashSet<>();
        for (Netlist.Net n : Netlist.of(circuit).nets()) {
            Set<Object> ports = new HashSet<>();
            for (Netlist.PortRef p : n.ports()) {
                if (mv.contains(p.component)) {
                    ports.add(p.component.getFactory().getName() + p.component.getLocation().translate(-dx, -dy)
                            + "#" + p.end);
                } else {
                    ports.add(p);
                }
            }
            ret.add(ports);
        }
        return ret;
    }
}
