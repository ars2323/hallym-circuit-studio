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
import java.util.List;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitTransaction;
import com.cburch.logisim.circuit.CircuitTransactionResult;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.StringGetter;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Netlist;

/**
 * 새 선·부품을 자동으로 두는 편집의 검사기 하나(W-05). 핀·상수·프로브·터널 붙이기, 게이트 바꾸기, 선을 터널로,
 * 빠른 프로브, 비트 나누기·뽑기·합치기, 스플리터 고치기가 모두 이것을 거친다. 따라오는 선(옮기기)은
 * {@link SafeMove}가 같은 규칙({@link WireRules})으로, 복제는 {@link SafeDuplicate}가 {@link #touches}로 본다.
 * <ol>
 * <li>새 선은 부록 A.4 출력 규칙을 지킨다({@link WireRules#violations}).</li>
 * <li>군더더기(고리, 막다른 끝, 쪼개진 일직선)를 새로 남기지 않는다({@link WireRules#clutter}).</li>
 * <li>남아 있는 옛 부품끼리의 넷은 그대로다: 두 넷이 합쳐지거나 한 넷이 갈라지지 않는다.</li>
 * <li>새 부품의 포트는 기능이 뜻한 점(allowed)에서만 옛 선·포트에 닿는다.</li>
 * </ol>
 * 검사는 편집을 실제 회로에 한 번 해 보고 곧바로 되돌려서 한다(부품 객체를 다른 회로에 넣으면 원조가 서브회로 사용
 * 관계를 옮겨 적기 때문에 복사 회로를 쓰지 않는다). 파일의 dirty 표시와 되돌리기 목록은 건드리지 않는다. 통과하면
 * 원조 {@link CircuitMutation#toAction} 그대로 돌려주므로 되돌리기 한 번에 전부 취소된다.
 */
public final class WireGuard {
    private WireGuard() {
    }

    /**
     * 검사를 통과하면 편집 동작을, 아니면 null을 돌려주고 상태 표시줄에 한 줄로 알린다. allowed는 새 부품의 포트가
     * 옛 선·포트에 닿아도 되는 점(붙일 포트, 나눌 선 위의 점 등).
     */
    public static Action guarded(Project proj, Circuit circuit, CircuitMutation m, Collection<Location> allowed,
            StringGetter name) {
        List<String> problems = problems(proj, circuit, m, allowed);
        if (!problems.isEmpty()) {
            kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, Messages.get("edit.wireRules"));
            return null;
        }
        return m.toAction(name);
    }

    /** guarded 뒤 바로 실행. 실행했으면 true. */
    public static boolean run(Project proj, Circuit circuit, CircuitMutation m, Collection<Location> allowed,
            StringGetter name) {
        Action a = guarded(proj, circuit, m, allowed, name);
        if (a == null) {
            return false;
        }
        proj.doAction(a);
        return true;
    }

    /** 어긴 규칙(사람이 읽는 설명, 테스트용). 비었으면 통과. 회로는 원래대로 돌려 둔다. */
    public static List<String> problems(Project proj, Circuit circuit, CircuitMutation m,
            Collection<Location> allowed) {
        Selection sel = proj == null ? null : proj.getSelection();
        List<Component> selBefore = sel == null ? null : new ArrayList<>(sel.getComponents());
        Set<Component> compsBefore = identity(circuit.getNonWires());
        Set<Wire> wiresBefore = new HashSet<>(circuit.getWires());
        List<Wire> oldWires = new ArrayList<>(circuit.getWires());
        WireRules.Before nets = new WireRules.Before(circuit);
        List<String> clutterBefore = WireRules.clutter(circuit);

        CircuitTransactionResult result = m.execute();
        CircuitTransaction reverse = result.getReverseTransaction();
        List<String> out = new ArrayList<>();
        try {
            List<Component> removed = new ArrayList<>();
            for (Component c : compsBefore) {
                if (!circuit.contains(c)) {
                    removed.add(c);
                }
            }
            List<Component> added = new ArrayList<>();
            for (Component c : circuit.getNonWires()) {
                if (!compsBefore.contains(c)) {
                    added.add(c);
                }
            }
            List<Wire> newWires = new ArrayList<>();
            for (Wire w : circuit.getWires()) {
                if (!wiresBefore.contains(w) && !insideOld(w, oldWires)) {
                    newWires.add(w);
                }
            }
            // 1, 2
            out.addAll(WireRules.violations(circuit, newWires, nets));
            List<String> clutter = WireRules.clutter(circuit);
            clutter.removeAll(clutterBefore);
            out.addAll(clutter);
            // 3: 옛 넷(지운 부품은 빼고)이 그대로인가. 뜻한 점(allowed)의 넷은 다른 넷과 이어져도 된다(같은
            // 이름 터널을 붙여 그 신호에 잇는 것은 학생이 이름으로 한 연결이다).
            List<Component> exclude = new ArrayList<>(removed);
            exclude.addAll(added);
            List<Set<Netlist.PortRef>> netsAfter = nets(circuit, exclude, allowed);
            List<Wire> kept = new ArrayList<>();
            List<Wire> nowWires = new ArrayList<>(circuit.getWires());
            for (Wire w : oldWires) {
                if (covered(w, nowWires)) {
                    kept.add(w);
                }
            }
            reverse.execute();
            reverse = null;
            List<Set<Netlist.PortRef>> netsBefore = nets(circuit, removed, allowed);
            Set<Set<Netlist.PortRef>> before = new HashSet<>(netsBefore.subList(1, netsBefore.size()));
            Set<Set<Netlist.PortRef>> after = new HashSet<>();
            for (Set<Netlist.PortRef> n : netsAfter.subList(1, netsAfter.size())) {
                if (java.util.Collections.disjoint(n, netsBefore.get(0))) {
                    after.add(n);
                }
            }
            // 뜻한 점의 넷에 합쳐진 옛 넷은 비교에서 뺀다
            for (Set<Netlist.PortRef> n : netsAfter) {
                if (n == netsAfter.get(0) || !java.util.Collections.disjoint(n, netsBefore.get(0))) {
                    before.removeIf(b -> n.containsAll(b));
                }
            }
            if (!before.equals(after)) {
                out.add("changes other connections");
            }
            // 4: 새 부품 포트가 뜻하지 않은 곳에서 옛 넷에 닿는가
            out.addAll(portTouches(added, compsBefore, removed, kept, allowed));
        } finally {
            if (reverse != null) {
                reverse.execute();
            }
            restore(sel, selBefore, circuit);
        }
        return out;
    }

    /**
     * 넷별 포트 집합(exclude의 포트는 뺀다). 맨 앞 원소는 뜻한 점(allowed)에 닿는 넷들의 포트를 모은 것이고, 나머지는
     * 그 밖의 넷이다(포트가 없는 넷은 뺀다).
     */
    static List<Set<Netlist.PortRef>> nets(Circuit circuit, Collection<? extends Component> exclude,
            Collection<Location> allowed) {
        Set<Component> ex = identity(exclude);
        Set<Netlist.PortRef> atAllowed = new HashSet<>();
        List<Set<Netlist.PortRef>> ret = new ArrayList<>();
        ret.add(atAllowed);
        for (Netlist.Net n : Netlist.of(circuit).nets()) {
            Set<Netlist.PortRef> ports = new HashSet<>();
            boolean touches = false;
            for (Netlist.PortRef p : n.ports()) {
                if (!ex.contains(p.component)) {
                    ports.add(p);
                }
                touches |= allowed != null && allowed.contains(p.location());
            }
            for (Wire w : n.wires()) {
                for (Location a : allowed == null ? Collections.<Location>emptyList() : allowed) {
                    touches |= w.contains(a);
                }
            }
            if (touches) {
                atAllowed.addAll(ports);
            } else if (!ports.isEmpty()) {
                ret.add(ports);
            }
        }
        return ret;
    }

    /** 부품 c의 모든 포트 자리(allowed에 넣기 좋게). */
    public static List<Location> ends(Component c) {
        List<Location> ret = new ArrayList<>();
        for (int i = 0; i < c.getEnds().size(); i++) {
            ret.add(c.getEnd(i).getLocation());
        }
        return ret;
    }

    /** 옛 선 w가 지금 선들로 전부 덮여 있는가(선 고치기가 나눈 조각도 덮은 것으로 본다). */
    static boolean covered(Wire w, List<Wire> now) {
        int n = w.getLength() / 10;
        int dx = Integer.signum(w.getEnd1().getX() - w.getEnd0().getX()) * 10;
        int dy = Integer.signum(w.getEnd1().getY() - w.getEnd0().getY()) * 10;
        for (int i = 0; i < n; i++) {
            Location a = w.getEnd0().translate(dx * i, dy * i);
            Location z = a.translate(dx, dy);
            boolean on = false;
            for (Wire o : now) {
                if (o.isVertical() == w.isVertical() && o.contains(a) && o.contains(z)) {
                    on = true;
                    break;
                }
            }
            if (!on) {
                return false;
            }
        }
        return true;
    }

    /** 옛 선 하나의 한 조각(선 고치기가 옛 선을 나눈 것)인가. */
    static boolean insideOld(Wire w, List<Wire> old) {
        for (Wire o : old) {
            if (o.contains(w.getEnd0()) && o.contains(w.getEnd1()) && o.isVertical() == w.isVertical()) {
                return true;
            }
        }
        return false;
    }

    static List<String> portTouches(List<Component> added, Set<Component> compsBefore, List<Component> removed,
            List<Wire> oldWires, Collection<Location> allowed) {
        Set<Component> gone = identity(removed);
        Set<Location> oldPorts = new HashSet<>();
        for (Component c : compsBefore) {
            if (!gone.contains(c)) {
                for (int i = 0; i < c.getEnds().size(); i++) {
                    oldPorts.add(c.getEnd(i).getLocation());
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (Component c : added) {
            for (int i = 0; i < c.getEnds().size(); i++) {
                Location p = c.getEnd(i).getLocation();
                if (allowed != null && allowed.contains(p)) {
                    continue;
                }
                if (oldPorts.contains(p) || onAny(p, oldWires)) {
                    out.add("a port of " + c.getFactory().getName() + " touches another net at " + p);
                }
            }
        }
        return out;
    }

    /**
     * 복제·붙여넣기처럼 떠 있는 부품을 (dx, dy)만큼 옮겨 내려놓으면 옛 선·포트에 닿거나 다른 부품 몸체와 겹치는가.
     * 떠 있는 선은 끝점과 옛 포트·선 끝, 같은 직선 겹침까지 본다.
     */
    public static boolean touches(Circuit circuit, Collection<Component> floating, int dx, int dy) {
        List<Wire> oldWires = new ArrayList<>(circuit.getWires());
        Set<Location> oldPoints = new HashSet<>();
        for (Component c : circuit.getNonWires()) {
            for (int i = 0; i < c.getEnds().size(); i++) {
                oldPoints.add(c.getEnd(i).getLocation());
            }
        }
        for (Wire w : oldWires) {
            oldPoints.add(w.getEnd0());
            oldPoints.add(w.getEnd1());
        }
        for (Component c : floating) {
            if (c instanceof Wire) {
                Wire w = Wire.create(((Wire) c).getEnd0().translate(dx, dy), ((Wire) c).getEnd1().translate(dx, dy));
                for (Location p : new Location[] {w.getEnd0(), w.getEnd1()}) {
                    if (oldPoints.contains(p) || onAny(p, oldWires)) {
                        return true;
                    }
                }
                for (Location p : oldPoints) {
                    if (w.contains(p)) {
                        return true;
                    }
                }
                for (Wire o : oldWires) {
                    if (WireRules.overlap(w, o) > 0) {
                        return true;
                    }
                }
                continue;
            }
            for (int i = 0; i < c.getEnds().size(); i++) {
                Location p = c.getEnd(i).getLocation().translate(dx, dy);
                if (oldPoints.contains(p) || onAny(p, oldWires)) {
                    return true;
                }
            }
            com.cburch.logisim.data.Bounds b = c.getBounds().translate(dx, dy);
            for (Component o : circuit.getNonWires()) {
                com.cburch.logisim.data.Bounds ob = o.getBounds();
                if (b.getX() < ob.getX() + ob.getWidth() && ob.getX() < b.getX() + b.getWidth()
                        && b.getY() < ob.getY() + ob.getHeight() && ob.getY() < b.getY() + b.getHeight()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean onAny(Location p, List<Wire> wires) {
        for (Wire w : wires) {
            if (w.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static Set<Component> identity(Collection<? extends Component> cs) {
        Set<Component> s = Collections.newSetFromMap(new IdentityHashMap<>());
        s.addAll(cs);
        return s;
    }

    /** 시험 실행이 선택에서 뺀 부품을 되돌린다(원조 선택은 지운 부품을 빼고, 되살린 것은 다시 넣지 않는다). */
    private static void restore(Selection sel, List<Component> before, Circuit circuit) {
        if (sel == null || before == null) {
            return;
        }
        List<Component> missing = new ArrayList<>();
        for (Component c : before) {
            if (!sel.contains(c) && circuit.contains(c)) {
                missing.add(c);
            }
        }
        if (!missing.isEmpty()) {
            sel.addAll(missing);
        }
    }
}
