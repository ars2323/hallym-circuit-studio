/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.wiring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.ReplacementMap;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.move.MoveResult;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Netlist;

/**
 * 따라오는 배선의 안전한 이동(PLAN.md 11.9, #81, D-055). 다음 후보를 차례로 따져 보고, 기준을 지키는 첫 후보만 실제
 * 회로에 한 동작으로 적용한다.
 * <ol>
 * <li>가운데 선분 평행 이동({@link SegmentDrag})</li>
 * <li>원조 연결 유지 이동(MoveGesture의 길 찾기). 길을 못 찾고 남긴 점과 포트끼리 바로 닿던 점은 곧은 선(ㄱ자)으로</li>
 * <li>옛 자리에서 곧은 선(ㄱ자)으로만 잇기. 원조 길 찾기는 시간 제한이 있어 결과가 그때그때 다르므로 늘 같은 방법을 둔다</li>
 * <li>선 없이 옮기기(원조 동작)</li>
 * </ol>
 * 기준: 1~3은 넷리스트(넷별 포트 집합)가 그대로이고(옮긴 부품은 옮기기 전 자리로 적어 비교), 새 선이 부록 A.4 규칙
 * ({@link WireRules})을 지킨다. 4는 옮긴 부품의 포트를 뺀 넷리스트가 그대로다. 어느 것도 못 지키면 옮기지 않는다.
 * <p>
 * 후보는 실제 회로가 아니라 복사한 회로(scratch)에서 따진다. 실제 회로는 고른 후보를 적용할 때 한 번만 바뀐다(시험하고
 * 되돌리는 변경 이벤트가 시뮬레이션 상태·선택·자동 저장에 흘러가지 않는다). 남긴 동작은 되돌리기 한 번에 선 변경과
 * 함께 취소된다.
 */
public final class SafeMove {
    /** 결과. */
    public enum Outcome { MOVED, MOVED_WITHOUT_WIRES, REFUSED }

    private SafeMove() {
    }

    /**
     * 선택을 (dx, dy)만큼 옮긴다. result는 원조 연결 유지 계산 결과(없으면 선을 잇지 않는 이동). 옮긴 뒤에는 빠른 속성
     * 창을 띄우지 않는다(S-04: 끌기 직후 창이 칩을 가렸다. 다음에 누르면 다시 뜬다).
     */
    public static Outcome move(Project proj, Selection sel, int dx, int dy, MoveResult result) {
        Outcome o = moveInner(proj, sel, dx, dy, result);
        kr.ac.hallym.hcs.app.props.QuickBar.markQuiet(proj, sel.getComponents());
        return o;
    }

    static Outcome moveInner(Project proj, Selection sel, int dx, int dy, MoveResult result) {
        Circuit circuit = proj.getCurrentCircuit();
        if (!sel.getFloatingComponents().isEmpty()) {
            // 붙여 넣어 아직 회로에 놓이지 않은(떠 있는) 것: 원조 이동 그대로(놓을 때 원조가 합친다)
            proj.doAction(SelectionActions.translate(sel, dx, dy, result == null ? null : result.getReplacementMap()));
            return Outcome.MOVED;
        }
        List<Component> before = new ArrayList<>(sel.getComponents());
        Set<Set<Netlist.PortRef>> sig = NetSignature.of(circuit, before);
        Set<Set<Object>> all = NetSignature.full(circuit, before, 0, 0);
        WireRules.Before nets = new WireRules.Before(circuit);
        nets.clutter = WireRules.clutter(circuit);
        if (result != null) {
            SegmentDrag seg = SegmentDrag.plan(circuit, sel, dx, dy);
            if (seg != null) {
                Plan p = new Plan(before, 0, 0);
                p.removed.addAll(seg.removed());
                p.added.addAll(seg.added());
                if (p.check(circuit, sig, all, nets)) {
                    proj.doAction(p.action(sel, circuit));
                    seg.select(sel);
                    return Outcome.MOVED;
                }
            }
            // W-03 고무줄: 포트에 붙은 선을 부품과 함께 끌고 가고, 그 끝의 다리를 늘린다(선 묶음이 나란히 유지된다)
            Plan rubber = rubber(circuit, before, dx, dy);
            if (rubber != null && rubber.check(circuit, sig, all, nets)) {
                proj.doAction(rubber.action(sel, circuit));
                return Outcome.MOVED;
            }
            Set<Location> open = openEnds(circuit, before, result);
            for (boolean horizontalFirst : new boolean[] {true, false}) {
                Plan p = new Plan(before, dx, dy);
                p.repl = result.getReplacementMap();
                p.added.addAll(connectors(open, dx, dy, horizontalFirst));
                if (p.check(circuit, sig, all, nets)) {
                    proj.doAction(p.action(sel, circuit));
                    return Outcome.MOVED;
                }
                if (open.isEmpty() || dx == 0 || dy == 0) {
                    break; // ㄱ자가 없으면 다른 방향도 같다
                }
            }
            Set<Location> connected = connectedEnds(circuit, before);
            for (boolean horizontalFirst : new boolean[] {true, false}) {
                Plan p = new Plan(before, dx, dy);
                p.added.addAll(connectors(connected, dx, dy, horizontalFirst));
                if (p.check(circuit, sig, all, nets)) {
                    proj.doAction(p.action(sel, circuit));
                    return Outcome.MOVED;
                }
                if (dx == 0 || dy == 0) {
                    break;
                }
            }
        }
        Plan plain = new Plan(before, dx, dy);
        if (plain.check(circuit, sig, null, nets)) {
            proj.doAction(plain.action(sel, circuit));
            if (result != null) {
                kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, Messages.get("move.withoutWires"));
                return Outcome.MOVED_WITHOUT_WIRES;
            }
            return Outcome.MOVED;
        }
        kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, Messages.get("move.refused"));
        return Outcome.REFUSED;
    }

    /** 후보 하나: 옮길 것(dx, dy), 원조 길 찾기 결과, 지울 선, 더할 선. */
    static final class Plan {
        final List<Component> moved;
        final int dx;
        final int dy;
        ReplacementMap repl;
        final List<Wire> removed = new ArrayList<>();
        final List<Wire> added = new ArrayList<>();
        /** 정리 단계(W-02)에서 지울 선(모양으로 찾는다). check가 채운다. */
        final List<Wire> cleanup = new ArrayList<>();

        Plan(List<Component> moved, int dx, int dy) {
            this.moved = moved;
            this.dx = dx;
            this.dy = dy;
        }

        /** 복사한 회로에서 결과를 만들어 기준을 따진다. 실제 회로는 건드리지 않는다. */
        boolean check(Circuit real, Set<Set<Netlist.PortRef>> sig, Set<Set<Object>> all, WireRules.Before nets) {
            Circuit scratch = new Circuit("hcs-move-check");
            Map<Component, Component> original = new IdentityHashMap<>(); // 복사본 → 실제
            List<Component> movedCopies = new ArrayList<>();
            Set<Component> movedSet = Collections.newSetFromMap(new IdentityHashMap<>());
            movedSet.addAll(moved);
            CircuitMutation build = new CircuitMutation(scratch);
            Set<Wire> gone = new HashSet<>(removed);
            if (repl != null) {
                gone.addAll(asWires(repl.getRemovals()));
            }
            for (Component c : real.getNonWires()) {
                boolean mv = movedSet.contains(c);
                Location at = mv ? c.getLocation().translate(dx, dy) : c.getLocation();
                Component copy = c.getFactory().createComponent(at, (AttributeSet) c.getAttributeSet().clone());
                original.put(copy, c);
                if (mv) {
                    movedCopies.add(copy);
                }
                build.add(copy);
            }
            for (Wire w : real.getWires()) {
                if (gone.contains(w)) {
                    continue;
                }
                build.add(movedSet.contains(w) ? Wire.create(w.getEnd0().translate(dx, dy),
                        w.getEnd1().translate(dx, dy)) : w);
            }
            List<Wire> news = new ArrayList<>(added);
            if (repl != null) {
                news.addAll(asWires(repl.getAdditions()));
            }
            build.addAll(news);
            try {
                build.execute();
                if (!same(scratch, movedCopies, original, sig, all)) {
                    return false;
                }
                // W-02 정리: 새로 생긴 선을 짧은 것부터 하나씩 빼 보고, 넷이 그대로면 뺀 채로 둔다
                // (같은 넷 안 고리, 막다른 짧은 선, 겹친 조각이 사라진다. 원래 있던 선과 옮긴 선은 건드리지 않는다)
                Set<Wire> kept = new HashSet<>(real.getWires());
                for (Component c : moved) {
                    if (c instanceof Wire) {
                        Wire w = (Wire) c;
                        kept.add(Wire.create(w.getEnd0().translate(dx, dy), w.getEnd1().translate(dx, dy)));
                    }
                }
                cleanup.clear();
                for (Wire w : byLength(scratch.getWires())) {
                    if (kept.contains(w) || !scratch.getWires().contains(w)) {
                        continue;
                    }
                    List<String> deadBefore = deadEnds(scratch);
                    CircuitMutation drop = new CircuitMutation(scratch);
                    drop.remove(w);
                    com.cburch.logisim.circuit.CircuitTransactionResult res = drop.execute();
                    // 넷이 그대로이고 새 막다른 끝이 생기지 않을 때만(옛 선이 매달려 남지 않게)
                    if (same(scratch, movedCopies, original, sig, all) && deadBefore.containsAll(deadEnds(scratch))) {
                        cleanup.add(w);
                    } else {
                        res.getReverseTransaction().execute();
                    }
                }
                List<Wire> remaining = new ArrayList<>();
                for (Wire w : scratch.getWires()) {
                    if (!kept.contains(w)) {
                        remaining.add(w);
                    }
                }
                if (!WireRules.violations(scratch, remaining, nets).isEmpty()) {
                    return false;
                }
                if (all == null) {
                    return true; // 선 없이 옮기기: 옛 선이 일부러 끊긴 채 남는다
                }
                // 체크리스트 7: 정리한 뒤에도 고리·막다른 끝·쪼개진 일직선이 새로 남으면 이 후보는 쓰지 않는다
                List<String> left = WireRules.clutter(scratch);
                left.removeAll(nets.clutter);
                return left.isEmpty();
            } finally {
                // 서브회로 인스턴스 복사본은 서브회로의 사용처 목록에 올라간다. 비워서 지운다
                CircuitMutation clear = new CircuitMutation(scratch);
                clear.removeAll(new ArrayList<>(scratch.getNonWires()));
                clear.removeAll(new ArrayList<>(scratch.getWires()));
                clear.execute();
            }
        }

        boolean same(Circuit scratch, List<Component> movedCopies, Map<Component, Component> original,
                Set<Set<Netlist.PortRef>> sig, Set<Set<Object>> all) {
            return mappedOthers(scratch, movedCopies, original).equals(sig)
                    && (all == null || mappedFull(scratch, movedCopies, original).equals(all));
        }

        static List<String> deadEnds(Circuit c) {
            List<String> ret = new ArrayList<>();
            for (String s : WireRules.clutter(c)) {
                if (s.startsWith("dead end")) {
                    ret.add(s);
                }
            }
            return ret;
        }

        /** 짧은 선부터, 같은 길이면 위치 순(결정적). */
        static List<Wire> byLength(Collection<Wire> wires) {
            List<Wire> ret = new ArrayList<>(wires);
            ret.sort((a, b) -> a.getLength() != b.getLength() ? Integer.compare(a.getLength(), b.getLength())
                    : a.getEnd0().compareTo(b.getEnd0()) != 0 ? a.getEnd0().compareTo(b.getEnd0())
                    : a.getEnd1().compareTo(b.getEnd1()));
            return ret;
        }

        /** 옮긴 것의 포트를 뺀 넷 모양(복사본을 실제 부품으로 바꿔 적는다). */
        Set<Set<Netlist.PortRef>> mappedOthers(Circuit scratch, List<Component> movedCopies,
                Map<Component, Component> original) {
            Set<Set<Netlist.PortRef>> ret = new HashSet<>();
            for (Set<Netlist.PortRef> net : NetSignature.of(scratch, movedCopies)) {
                Set<Netlist.PortRef> m = new HashSet<>();
                for (Netlist.PortRef p : net) {
                    m.add(new Netlist.PortRef(original.get(p.component), p.end));
                }
                ret.add(m);
            }
            return ret;
        }

        /** 옮긴 것까지 본 넷 모양({@link NetSignature#full}과 같은 적기). */
        Set<Set<Object>> mappedFull(Circuit scratch, List<Component> movedCopies, Map<Component, Component> original) {
            Set<Set<Object>> ret = new HashSet<>();
            for (Set<Object> net : NetSignature.full(scratch, movedCopies, dx, dy)) {
                Set<Object> m = new HashSet<>();
                for (Object o : net) {
                    if (o instanceof Netlist.PortRef) {
                        Netlist.PortRef p = (Netlist.PortRef) o;
                        m.add(new Netlist.PortRef(original.get(p.component), p.end));
                    } else {
                        m.add(o);
                    }
                }
                ret.add(m);
            }
            return ret;
        }

        /** 실제 회로에 적용할 한 동작: 원조 옮기기 + 지우고 더할 선 + 정리(W-02). 되돌리기 한 번에 모두 취소된다. */
        Action action(Selection sel, Circuit circuit) {
            List<Action> steps = new ArrayList<>();
            // 옮기기와 선 바꾸기를 한 변경으로(복사한 회로처럼 선 합치기가 한 번에 일어난다). 선택은 원조 선택이
            // 바꿔치기를 따라 새 부품으로 옮긴다
            CircuitMutation m = new CircuitMutation(circuit);
            if (dx != 0 || dy != 0) {
                for (Component c : moved) {
                    Component copy = c instanceof Wire
                            ? Wire.create(((Wire) c).getEnd0().translate(dx, dy), ((Wire) c).getEnd1().translate(dx, dy))
                            : c.getFactory().createComponent(c.getLocation().translate(dx, dy),
                                    (AttributeSet) c.getAttributeSet().clone());
                    m.replace(c, copy);
                }
            }
            if (repl != null) {
                m.replace(repl);
            }
            m.removeAll(removed);
            m.addAll(added);
            steps.add(m.toAction(() -> Messages.get("move.action")));
            if (!cleanup.isEmpty()) {
                steps.add(new RemoveEqual(circuit, cleanup));
            }
            return new Seq(steps);
        }
    }

    /**
     * 고무줄 후보(W-03). 가로·세로 이동만. 옮기는 부품의 포트 P에 끝이 닿은(옮기지 않는) 선 w마다:
     * <ul>
     * <li>w가 이동 방향과 나란하면 w를 늘이거나 줄인다(먼 끝 → P + d).</li>
     * <li>w가 이동 방향과 수직이면 w를 d만큼 옮긴다. w의 먼 끝에 이동 방향으로 뻗은 다리가 하나뿐이면 그 다리를
     * 늘이거나 줄이고, 아니면 먼 끝에서 옮긴 끝까지 곧은 선을 더한다.</li>
     * </ul>
     * 선 없이 옮기지 않는 부품 포트에 바로 닿던 포트는 곧은 선으로 잇는다. 쓸 수 없으면 null.
     */
    static Plan rubber(Circuit circuit, List<Component> moved, int dx, int dy) {
        if ((dx == 0) == (dy == 0)) {
            return null; // 가로 또는 세로 한 방향만
        }
        boolean moveVertical = dx == 0;
        Plan p = new Plan(moved, dx, dy);
        Set<Wire> touched = new HashSet<>();
        for (Location at : portsOf(moved)) {
            List<Wire> attached = new ArrayList<>();
            for (Wire w : circuit.getWires()) {
                if (!moved.contains(w) && w.endsAt(at)) {
                    attached.add(w);
                }
            }
            if (attached.isEmpty()) {
                if (touchesStayingPort(circuit, moved, at)) {
                    p.added.add(Wire.create(at, at.translate(dx, dy)));
                }
                continue;
            }
            for (Wire w : attached) {
                if (!touched.add(w)) {
                    return null; // 두 포트에 걸친 선: 단순하지 않다
                }
                Location far = w.getOtherEnd(at);
                Location to = at.translate(dx, dy);
                p.removed.add(w);
                if (w.isVertical() == moveVertical) { // 나란함
                    if (!far.equals(to)) {
                        p.added.add(Wire.create(far, to));
                    }
                    continue;
                }
                Location farTo = far.translate(dx, dy);
                p.added.add(Wire.create(farTo, to));
                Wire leg = null;
                int others = 0;
                for (Wire o : circuit.getWires()) {
                    if (o != w && !o.equals(w) && o.contains(far)) {
                        others++;
                        if (o.endsAt(far) && o.isVertical() == moveVertical) {
                            leg = o;
                        }
                    }
                }
                boolean portAtFar = false;
                for (Component c : circuit.getNonWires()) {
                    for (int i = 0; i < c.getEnds().size(); i++) {
                        portAtFar |= c.getEnd(i).getLocation().equals(far);
                    }
                }
                if (leg != null && others == 1 && !portAtFar && !touched.contains(leg)) {
                    touched.add(leg);
                    p.removed.add(leg);
                    Location legFar = leg.getOtherEnd(far);
                    if (!legFar.equals(farTo)) {
                        p.added.add(Wire.create(legFar, farTo));
                    }
                } else {
                    p.added.add(Wire.create(far, farTo));
                }
            }
        }
        return p.added.isEmpty() && p.removed.isEmpty() ? null : p;
    }

    static List<Wire> asWires(Collection<? extends Component> comps) {
        List<Wire> ret = new ArrayList<>();
        for (Component c : comps) {
            if (c instanceof Wire) {
                ret.add((Wire) c);
            }
        }
        return ret;
    }

    /**
     * 옮긴 뒤 이어야 할 옛 포트 자리: 원조가 길을 못 찾고 남긴 점(선 끝이 남음)과, 선 없이 옮기지 않는 부품의 포트에 바로
     * 닿아 있던 점.
     */
    static Set<Location> openEnds(Circuit circuit, List<Component> moved, MoveResult result) {
        Set<Location> ret = new LinkedHashSet<>();
        for (Location at : portsOf(moved)) {
            if (result.getUnconnectedLocations().contains(at) || touchesStayingPort(circuit, moved, at)) {
                ret.add(at);
            }
        }
        return ret;
    }

    /** 옮길 부품의 포트 중 옛 자리에 옮기지 않는 선의 끝이나 옮기지 않는 부품의 포트가 닿아 있던 것. */
    static Set<Location> connectedEnds(Circuit circuit, List<Component> moved) {
        Set<Location> ret = new LinkedHashSet<>();
        for (Location at : portsOf(moved)) {
            boolean wired = false;
            for (Wire w : circuit.getWires()) {
                wired |= !moved.contains(w) && w.endsAt(at);
            }
            if (wired || touchesStayingPort(circuit, moved, at)) {
                ret.add(at);
            }
        }
        return ret;
    }

    static Set<Location> portsOf(List<Component> moved) {
        Set<Location> ret = new LinkedHashSet<>();
        for (Component c : moved) {
            if (!(c instanceof Wire)) {
                for (int i = 0; i < c.getEnds().size(); i++) {
                    ret.add(c.getEnd(i).getLocation());
                }
            }
        }
        return ret;
    }

    /** 옛 자리 at에서 옮긴 포트(at + (dx, dy))까지 곧은 선, 가로·세로가 아니면 ㄱ자 두 조각. */
    static List<Wire> connectors(Set<Location> open, int dx, int dy, boolean horizontalFirst) {
        List<Wire> ret = new ArrayList<>();
        for (Location at : open) {
            Location to = at.translate(dx, dy);
            if (dx == 0 || dy == 0) {
                ret.add(Wire.create(at, to));
            } else {
                Location bend = horizontalFirst ? Location.create(to.getX(), at.getY())
                        : Location.create(at.getX(), to.getY());
                ret.add(Wire.create(at, bend));
                ret.add(Wire.create(bend, to));
            }
        }
        return ret;
    }

    /** 점 at에 옮기지 않는 부품의 포트가 있고, 그 점에 끝나는 선은 없다(선이 있으면 원조가 잇는다). */
    static boolean touchesStayingPort(Circuit circuit, List<Component> moved, Location at) {
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

    /** 여러 동작을 한 동작으로(옮기기 + 선 바꾸기 + 정리). 되돌리기 한 번에 거꾸로 모두 취소된다. */
    static final class Seq extends Action {
        private final List<Action> steps;

        Seq(List<Action> steps) {
            this.steps = new ArrayList<>(steps);
        }

        @Override
        public String getName() {
            return steps.isEmpty() ? Messages.get("move.segmentAction") : steps.get(0).getName();
        }

        @Override
        public void doIt(Project proj) {
            for (Action a : steps) {
                a.doIt(proj);
            }
        }

        @Override
        public void undo(Project proj) {
            for (int i = steps.size() - 1; i >= 0; i--) {
                steps.get(i).undo(proj);
            }
        }

        @Override
        public boolean shouldAppendTo(Action other) {
            return !steps.isEmpty() && steps.get(0).shouldAppendTo(other);
        }
    }

    /** 모양이 같은 선을 지운다(정리 단계). 복사한 회로와 실제 회로는 같은 선 합치기를 거쳐 같은 모양이 된다. */
    static final class RemoveEqual extends Action {
        private final Circuit circuit;
        private final List<Wire> wires;
        private com.cburch.logisim.circuit.CircuitTransaction reverse;

        RemoveEqual(Circuit circuit, List<Wire> wires) {
            this.circuit = circuit;
            this.wires = new ArrayList<>(wires);
        }

        @Override
        public String getName() {
            return Messages.get("move.segmentAction");
        }

        @Override
        public void doIt(Project proj) {
            List<Wire> found = new ArrayList<>();
            for (Wire w : circuit.getWires()) {
                if (wires.contains(w)) {
                    found.add(w);
                }
            }
            CircuitMutation m = new CircuitMutation(circuit);
            m.removeAll(found);
            reverse = m.execute().getReverseTransaction();
        }

        @Override
        public void undo(Project proj) {
            if (reverse != null) {
                reverse.execute();
            }
        }
    }
}
