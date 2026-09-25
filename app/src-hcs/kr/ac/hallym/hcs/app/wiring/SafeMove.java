/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.wiring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.move.MoveResult;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Netlist;

/**
 * 따라오는 배선의 안전한 이동(PLAN.md 11.9, #81). 원조 2.7.1의 "옮길 때 연결 유지"(MoveGesture가 선을 늘리고 꺾는다)나
 * 가운데 선분 평행 이동({@link SegmentDrag})을 먼저 시험해 보고, 다음을 지킬 때만 남긴다.
 * <ul>
 * <li>옮긴 부품의 포트를 뺀 넷리스트(넷별 포트 집합)가 그대로다: 다른 넷이 합쳐지거나 나뉘지 않는다.</li>
 * <li>새로 그은 선이 부록 A.4 출력 규칙을 지킨다({@link WireRules}).</li>
 * </ul>
 * 어기면 되돌리고, 선을 잇지 않는 원조 이동(선택만 옮김)을 같은 기준으로 시험한다. 그것도 어기면 옮기지 않는다.
 * 어느 쪽이든 상태 표시줄에 한 줄로 알린다. 남긴 이동은 되돌리기 한 번으로 선 변경과 함께 취소된다.
 */
public final class SafeMove {
    /** 결과. */
    public enum Outcome { MOVED, MOVED_WITHOUT_WIRES, REFUSED }

    private SafeMove() {
    }

    /**
     * 선택을 (dx, dy)만큼 옮긴다. result는 연결 유지 계산 결과(없으면 선을 잇지 않는 이동).
     */
    public static Outcome move(Project proj, Selection sel, int dx, int dy, MoveResult result) {
        Circuit circuit = proj.getCurrentCircuit();
        List<Component> before = new ArrayList<>(sel.getComponents());
        Set<Set<Netlist.PortRef>> sig = NetSignature.of(circuit, before);
        Set<Set<Object>> all = NetSignature.full(circuit, before, 0, 0);
        if (result != null) {
            SegmentDrag seg = SegmentDrag.plan(circuit, sel, dx, dy);
            if (seg != null && tryCommit(proj, seg.action(), circuit, sel, before, sig, all, dx, dy, seg.added())) {
                seg.select(sel);
                return Outcome.MOVED;
            }
            // 원조는 선만 늘리고 꺾는다. 포트끼리 바로 닿아 있던 연결(선 없음)은 곧은 선을 더해 잇는다
            List<Wire> direct = directWires(circuit, before, dx, dy);
            Action follow = SelectionActions.translate(sel, dx, dy, result.getReplacementMap());
            if (!direct.isEmpty()) {
                com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(circuit);
                m.addAll(direct);
                follow = new Both(follow, m.toAction(null));
            }
            List<Wire> added = new ArrayList<>(result.getWiresToAdd());
            added.addAll(direct);
            if (tryCommit(proj, follow, circuit, sel, before, sig, all, dx, dy, added)) {
                return Outcome.MOVED;
            }
        }
        Action plain = SelectionActions.translate(sel, dx, dy, null);
        if (tryCommit(proj, plain, circuit, sel, before, sig, null, dx, dy, Collections.<Wire>emptyList())) {
            if (result != null) {
                kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, Messages.get("move.withoutWires"));
                return Outcome.MOVED_WITHOUT_WIRES;
            }
            return Outcome.MOVED;
        }
        kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, Messages.get("move.refused"));
        return Outcome.REFUSED;
    }

    /** 시험해 보고 기준을 지키면 되돌리기 목록에 한 동작으로 남긴다(다시 실행하지 않는다). */
    static boolean tryCommit(Project proj, Action action, Circuit circuit, Selection sel, List<Component> before,
            Set<Set<Netlist.PortRef>> sig, Set<Set<Object>> all, int dx, int dy, Collection<Wire> added) {
        action.doIt(proj);
        // all이 있으면(선이 따라오는 이동) 옮긴 부품의 연결도 그대로여야 한다
        boolean ok = NetSignature.of(circuit, sel.getComponents()).equals(sig)
                && (all == null || NetSignature.full(circuit, sel.getComponents(), dx, dy).equals(all))
                && WireRules.violations(circuit, added).isEmpty();
        if (!ok) {
            action.undo(proj);
            // 선을 지우고 새로 긋는 시험(선분 이동)은 선택에서 그 선을 뺀다. 되돌린 뒤 원래 선택을 다시 채운다
            for (Component c : before) {
                if (circuit.contains(c) && !sel.getComponents().contains(c)) {
                    sel.add(c);
                }
            }
            return false;
        }
        proj.doAction(new AlreadyDone(action));
        return true;
    }

    /**
     * 옮길 부품의 포트가 선 없이 다른 부품의 포트에 바로 닿아 있던 곳: 옮긴 포트에서 원래 자리까지 곧은 선(가로 또는
     * 세로가 아니면 ㄱ자 두 조각).
     */
    static List<Wire> directWires(Circuit circuit, List<Component> moved, int dx, int dy) {
        java.util.Set<com.cburch.logisim.data.Location> done = new java.util.HashSet<>();
        List<Wire> ret = new ArrayList<>();
        for (Component c : moved) {
            if (c instanceof Wire) {
                continue;
            }
            for (int i = 0; i < c.getEnds().size(); i++) {
                com.cburch.logisim.data.Location at = c.getEnd(i).getLocation();
                if (!done.add(at) || !touchesStayingPort(circuit, moved, at)) {
                    continue;
                }
                com.cburch.logisim.data.Location to = at.translate(dx, dy);
                if (dx == 0 || dy == 0) {
                    ret.add(Wire.create(at, to));
                } else {
                    com.cburch.logisim.data.Location bend = com.cburch.logisim.data.Location.create(to.getX(),
                            at.getY());
                    ret.add(Wire.create(at, bend));
                    ret.add(Wire.create(bend, to));
                }
            }
        }
        return ret;
    }

    /** 점 at에 옮기지 않는 부품의 포트가 있고, 그 점에 끝나는 선은 없다(선이 있으면 원조가 잇는다). */
    static boolean touchesStayingPort(Circuit circuit, List<Component> moved, com.cburch.logisim.data.Location at) {
        for (Wire w : circuit.getWires()) {
            if (w.endsAt(at)) {
                return false;
            }
        }
        for (Component o : circuit.getNonWires()) {
            if (moved.contains(o)) {
                continue;
            }
            for (int i = 0; i < o.getEnds().size(); i++) {
                if (o.getEnd(i).getLocation().equals(at)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 두 동작을 한 동작으로(옮기기 + 바로 닿던 포트를 잇는 선). */
    static final class Both extends Action {
        private final Action a;
        private final Action b;

        Both(Action a, Action b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public String getName() {
            return a.getName();
        }

        @Override
        public void doIt(Project proj) {
            a.doIt(proj);
            b.doIt(proj);
        }

        @Override
        public void undo(Project proj) {
            b.undo(proj);
            a.undo(proj);
        }

        @Override
        public boolean shouldAppendTo(Action other) {
            return a.shouldAppendTo(other);
        }
    }

    /** 이미 한 동작: 처음 doIt은 건너뛰고, 다시 실행·되돌리기·이어 붙이기는 원래 동작에 맡긴다. */
    static final class AlreadyDone extends Action {
        private final Action inner;
        private boolean first = true;

        AlreadyDone(Action inner) {
            this.inner = inner;
        }

        @Override
        public String getName() {
            return inner.getName();
        }

        @Override
        public boolean isModification() {
            return inner.isModification();
        }

        @Override
        public void doIt(Project proj) {
            if (first) {
                first = false;
                return;
            }
            inner.doIt(proj);
        }

        @Override
        public void undo(Project proj) {
            inner.undo(proj);
        }

        @Override
        public boolean shouldAppendTo(Action other) {
            return inner.shouldAppendTo(other);
        }
    }
}
