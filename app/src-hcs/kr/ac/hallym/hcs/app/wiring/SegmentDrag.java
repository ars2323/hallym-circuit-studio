/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.wiring;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.proj.Action;

import kr.ac.hallym.hcs.app.Messages;

/**
 * 가운데 선분 평행 이동(PLAN.md 11.9, #81). 선 하나만 골라 그 선과 수직으로 끌면, 양쪽 끝에 붙은 수직 다리가 늘거나 줄어
 * 꺾임이 따라온다. 끝마다 붙은 것이 다리 하나뿐이고(부품 포트·다른 선 없음) 다리가 선과 수직일 때만 쓴다. 그 밖에는
 * null을 돌려주고 원조 이동에 맡긴다.
 */
public final class SegmentDrag {
    private final Circuit circuit;
    private final Wire moved;
    private final Wire[] legs;
    private final Wire newWire;
    private final List<Wire> newLegs = new ArrayList<>();

    private SegmentDrag(Circuit circuit, Wire moved, Wire[] legs, Wire newWire) {
        this.circuit = circuit;
        this.moved = moved;
        this.legs = legs;
        this.newWire = newWire;
    }

    /** 계획. 쓸 수 없으면 null. */
    public static SegmentDrag plan(Circuit circuit, Selection sel, int dx, int dy) {
        if (sel.getComponents().size() != 1 || !(sel.getComponents().iterator().next() instanceof Wire)) {
            return null;
        }
        Wire w = (Wire) sel.getComponents().iterator().next();
        if (w.getLength() == 0 || (w.isVertical() ? dy != 0 || dx == 0 : dx != 0 || dy == 0)) {
            return null; // 선과 수직으로만
        }
        Wire[] legs = new Wire[2];
        Location[] ends = {w.getEnd0(), w.getEnd1()};
        for (int i = 0; i < 2; i++) {
            Location p = ends[i];
            for (Component c : circuit.getNonWires()) {
                for (int e = 0; e < c.getEnds().size(); e++) {
                    if (c.getEnd(e).getLocation().equals(p)) {
                        return null; // 끝이 포트에 닿아 있다
                    }
                }
            }
            Wire leg = null;
            for (Wire o : circuit.getWires()) {
                if (o == w || o.equals(w) || !o.contains(p)) {
                    continue;
                }
                if (leg != null || !o.endsAt(p) || o.isVertical() == w.isVertical()) {
                    return null; // 다리가 둘 이상이거나, T로 닿거나, 같은 방향
                }
                leg = o;
            }
            if (leg == null) {
                return null;
            }
            legs[i] = leg;
        }
        Wire nw = Wire.create(ends[0].translate(dx, dy), ends[1].translate(dx, dy));
        SegmentDrag s = new SegmentDrag(circuit, w, legs, nw);
        for (int i = 0; i < 2; i++) {
            Location far = legs[i].getOtherEnd(ends[i]);
            Location to = ends[i].translate(dx, dy);
            if (!far.equals(to)) {
                s.newLegs.add(Wire.create(far, to));
            }
        }
        return s;
    }

    /** 되돌릴 수 있는 한 동작: 선분과 두 다리를 바꾼다. */
    Action action() {
        CircuitMutation m = new CircuitMutation(circuit);
        m.remove(moved);
        m.remove(legs[0]);
        if (legs[1] != legs[0]) {
            m.remove(legs[1]);
        }
        m.add(newWire);
        for (Wire l : newLegs) {
            m.add(l);
        }
        return m.toAction(() -> Messages.get("move.segmentAction"));
    }

    /** 새로 그은 선(규칙 검사 대상). */
    List<Wire> added() {
        List<Wire> ret = new ArrayList<>(newLegs);
        ret.add(newWire);
        return ret;
    }

    /** 옮긴 뒤 새 선분을 고른다. */
    void select(Selection sel) {
        sel.add(newWire);
    }
}
