/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;

import kr.ac.hallym.hcs.app.flow.ActivePath;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.model.RefKey;

/**
 * 활성 경로의 계산(C-08, V-04, D-079, D-099): MUX마다 선택 값이 정해졌으면 실제로 고른 데이터 입력까지 오는 가지의
 * 선분들. 그 넷을 내는 포트에서 그 입력 포트까지의 가장 짧은 선 경로만이고({@link Netlist#branch}), 내는 포트를
 * 모르면(스플리터·터널만 있는 넷) 넷 전체다. 선택이 정해지지 않은 MUX는 없다. 값은 회로 상태에서 읽기만 한다.
 * GUI가 없다: Swing 덧그림({@link ActivePathOverlay})과 v2 엔진(flow.activePath, N-15, D-151)이 함께 쓴다.
 */
public final class ActiveBranches {
    /** MUX 하나가 고른 가지. */
    public static final class Branch {
        public final Component mux;
        /** 고른 데이터 입력 번호(= 선택 값). */
        public final int input;
        /** 선분들(끝점 둘). */
        public final List<Location[]> segments;

        Branch(Component mux, int input, List<Location[]> segments) {
            this.mux = mux;
            this.input = input;
            this.segments = Collections.unmodifiableList(segments);
        }
    }

    /** 회로마다 회로 모양 서명과 넷 목록(부품·선이 그대로면 다시 쓴다). */
    private static final Map<Circuit, Object[]> NETS = new WeakHashMap<>();

    private ActiveBranches() {
    }

    /** 회로 circ의 MUX들이 상태 state에서 고른 가지(부품 차례). */
    public static List<Branch> of(Circuit circ, CircuitState state) {
        List<Branch> out = new ArrayList<>();
        if (circ == null || state == null) {
            return out;
        }
        Netlist nl = null;
        for (Component c : circ.getNonWires()) {
            if (!c.getFactory().getName().equals("Multiplexer")) {
                continue;
            }
            int n = c.getEnds().size();
            int k = ActivePath.dataCount(c, n); // 포트: 데이터 0..k-1, 선택 k, (enable), 출력 마지막
            if (k >= n) {
                continue;
            }
            Value sel = state.getValue(c.getEnd(k).getLocation());
            if (sel == null || !sel.isFullyDefined()) {
                continue;
            }
            int i = sel.toIntValue();
            if (i < 0 || i >= k) {
                continue;
            }
            if (nl == null) {
                nl = netlist(circ);
            }
            Netlist.Net net = nl.netOf(c, i);
            if (net == null) {
                continue;
            }
            Location to = c.getEnd(i).getLocation();
            Location from = null;
            if (!net.drivers().isEmpty()) {
                from = net.drivers().get(0).location();
            } else {
                for (Netlist.PortRef p : net.ports()) {
                    if (p.component != c && !p.data().isInput()) {
                        from = p.location();
                        break;
                    }
                }
            }
            List<Location[]> branch = from == null ? Collections.<Location[]>emptyList()
                    : Netlist.branch(net, from, to);
            List<Location[]> segs = new ArrayList<>();
            if (branch.isEmpty()) {
                for (Wire w : net.wires()) {
                    segs.add(new Location[] {w.getEnd0(), w.getEnd1()});
                }
            } else {
                segs.addAll(branch);
            }
            out.add(new Branch(c, i, segs));
        }
        return out;
    }

    /** 모든 MUX의 가지 선분을 한 목록으로. */
    public static List<Location[]> segments(Circuit circ, CircuitState state) {
        List<Location[]> out = new ArrayList<>();
        for (Branch b : of(circ, state)) {
            out.addAll(b.segments);
        }
        return out;
    }

    /** 부품·선이 그대로면 지난 넷 목록. 서명은 부품·선의 정체를 비교한다(D-129: identity hash 합이 아니라). */
    static Netlist netlist(Circuit circ) {
        RefKey sig = RefKey.shape(circ);
        synchronized (NETS) {
            Object[] hit = NETS.get(circ);
            if (hit != null && hit[0].equals(sig)) {
                return (Netlist) hit[1];
            }
            Netlist nl = Netlist.of(circ);
            NETS.put(circ, new Object[] {sig, nl});
            return nl;
        }
    }
}
