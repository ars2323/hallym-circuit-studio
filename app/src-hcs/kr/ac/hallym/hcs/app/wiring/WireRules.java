/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.wiring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;

/**
 * 자동으로 늘리거나 꺾은 선이 PLAN.md 부록 A.4 출력 규칙을 지키는지(#81).
 * <ul>
 * <li>교차점에 끝점을 두지 않는다: 새 선의 끝점이 가로선과 세로선이 함께 지나는 점(둘 다 그 점을 끝으로 하지 않음)에
 * 있으면 안 된다.</li>
 * <li>분기는 T자: 새 선 끝이 다른 선 한가운데에 닿는 것은 된다.</li>
 * <li>목적 포트가 아닌 포트 위를 지나지 않는다: 포트는 선의 끝에만 온다. 선 한가운데에 포트가 있으면 안 된다.</li>
 * <li>다른 선과 같은 직선에서 겹치지 않는다(길이가 있는 겹침).</li>
 * </ul>
 */
public final class WireRules {
    private WireRules() {
    }

    /** 새 선 added가 어기는 규칙(사람이 읽는 설명). 비었으면 지킨다. */
    public static List<String> violations(Circuit circuit, Collection<Wire> added) {
        List<String> out = new ArrayList<>();
        List<Wire> all = new ArrayList<>(circuit.getWires());
        List<Wire> news = new ArrayList<>();
        for (Wire w : added) {
            if (w.getLength() > 0) {
                news.add(w);
            }
        }
        for (Wire w : news) {
            for (Location p : new Location[] {w.getEnd0(), w.getEnd1()}) {
                boolean horizontal = false;
                boolean vertical = false;
                for (Wire o : all) {
                    if (o != w && !o.equals(w) && o.contains(p) && !o.endsAt(p)) {
                        horizontal |= !o.isVertical();
                        vertical |= o.isVertical();
                    }
                }
                if (horizontal && vertical) {
                    out.add("endpoint on a crossing at " + p);
                }
            }
            for (Component c : circuit.getNonWires()) {
                for (int i = 0; i < c.getEnds().size(); i++) {
                    Location q = c.getEnd(i).getLocation();
                    if (w.contains(q) && !w.endsAt(q)) {
                        out.add("passes over a port of " + c.getFactory().getName() + " at " + q);
                    }
                }
            }
            for (Wire o : all) {
                if (o != w && !o.equals(w) && overlap(w, o) > 0) {
                    out.add("overlaps another wire on the same line: " + w + " / " + o);
                }
            }
        }
        return out;
    }

    /** 같은 직선 위 두 선이 겹치는 길이(다른 직선이거나 방향이 다르면 0). */
    static int overlap(Wire a, Wire b) {
        if (a.isVertical() != b.isVertical() || a.getLength() == 0 || b.getLength() == 0) {
            return 0;
        }
        if (a.isVertical()) {
            if (a.getEnd0().getX() != b.getEnd0().getX()) {
                return 0;
            }
            int lo = Math.max(Math.min(a.getEnd0().getY(), a.getEnd1().getY()),
                    Math.min(b.getEnd0().getY(), b.getEnd1().getY()));
            int hi = Math.min(Math.max(a.getEnd0().getY(), a.getEnd1().getY()),
                    Math.max(b.getEnd0().getY(), b.getEnd1().getY()));
            return hi - lo;
        }
        if (a.getEnd0().getY() != b.getEnd0().getY()) {
            return 0;
        }
        int lo = Math.max(Math.min(a.getEnd0().getX(), a.getEnd1().getX()),
                Math.min(b.getEnd0().getX(), b.getEnd1().getX()));
        int hi = Math.min(Math.max(a.getEnd0().getX(), a.getEnd1().getX()),
                Math.max(b.getEnd0().getX(), b.getEnd1().getX()));
        return hi - lo;
    }
}
