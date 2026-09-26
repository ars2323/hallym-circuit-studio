/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.StdAttr;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.InstancePaths;
import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.model.OriginTrace;
import kr.ac.hallym.hcs.app.model.Trace;
import kr.ac.hallym.hcs.app.record.Recording;

/**
 * 동적 진단(PLAN.md 4.3·4.4, D-01·D-03): 기록 엔진의 스텝 하나를 보고 새로 생긴 문제를 원인 한 곳으로 말한다.
 * <ul>
 * <li><b>E 발생(D-01):</b> 넷에 E가 새로 생기면 E·X 출처 추적으로 처음 생긴 곳을 찾는다. 같은 원인은 한 번만.</li>
 * <li><b>X 쓰기(D-03):</b> 상태 부품(Register, 플립플롭, Data Memory, Stack)의 클럭 에지에 쓰기 허용 입력이 정해지지
 * 않았거나, 쓰기가 허용됐는데 쓸 값·주소가 정해지지 않았으면, 에지 직전 스텝의 기록값으로 그 입력의 출처를 찾는다.
 * 원조 Register·플립플롭은 X를 담지 않고 옛 값을 지키므로(쓰기가 조용히 빠진다) 이 순간이 원인을 말할 곳이다.</li>
 * </ul>
 * 연결되지 않은 허용 입력(en)은 원조처럼 "늘 허용"이라 문제로 보지 않는다. 기록값을 읽기만 한다. GUI 없이 테스트한다.
 */
public final class DynamicCheck {
    private final Circuit top;
    private final Recording rec;
    private final OriginTrace trace;
    private final Trace nets = new Trace();

    public DynamicCheck(Circuit top, Recording rec) {
        this.top = top;
        this.rec = rec;
        this.trace = new OriginTrace(top, rec.originValues());
    }

    /** 사이클 뷰의 열(그 스텝을 보이는 열): 스텝 2c-1, 2c가 열 c다(D-074). */
    public static int cycleOf(int step) {
        return step <= 0 ? 0 : (step + 1) / 2;
    }

    /** 스텝 step의 새 진단. seen은 이미 말한 원인 열쇠 → 처음 말한 스텝(더해 간다). */
    public List<Diagnostic> step(int step, Map<String, Integer> seen) {
        List<Diagnostic> out = new ArrayList<>();
        errors(step, seen, out);
        if (step > rec.first()) {
            writes(step, seen, out);
        }
        return out;
    }

    // ---- E 발생 ----

    private void errors(int step, Map<String, Integer> seen, List<Diagnostic> out) {
        List<Object[]> errors = new ArrayList<>(rec.newErrors(step));
        // 같은 원인의 E가 여러 넷에 있으면 늘 같은 넷을 말한다(V-02): 바깥 회로부터, 왼쪽 위부터
        errors.sort((a, b) -> {
            int d = ((List<?>) a[0]).size() - ((List<?>) b[0]).size();
            return d != 0 ? d : ((Location) a[1]).compareTo((Location) b[1]);
        });
        for (Object[] e : errors) {
            @SuppressWarnings("unchecked")
            List<Component> path = (List<Component>) e[0];
            Location at = (Location) e[1];
            Circuit c = circuitOf(path);
            if (c == null) {
                continue;
            }
            Trace.Node en = trace.node(path, c, at);
            OriginTrace.Origin o = trace.find(en, step);
            // 원인 열쇠는 종류와 상관없이 하나: 같은 원인이 E도 만들고 X 쓰기도 만들면 먼저 본 한 번만 말한다
            if (o == null || seen.putIfAbsent(key(o), step) != null) {
                continue;
            }
            out.add(new Diagnostic(Diagnostic.Kind.E_APPEARED, o.node.circuit, o.node.instances, o.step,
                    components(o), wires(o), location(o), cycleOf(o.step), OriginText.netLabel(top, en),
                    Messages.get("diag.causePrefix", cause(o)), OriginText.errorLabel(o, en))
                    .appearedAt(path, c, at));
        }
    }

    // ---- X 쓰기 ----

    /** 쓰기 포트: 허용 입력(없으면 -1)과 쓸 값 입력들. */
    static final class WritePorts {
        final int enable;
        final boolean enableFloatingWrites; // 허용 입력이 떠 있으면 원조는 쓰기로 본다(Register·플립플롭)
        final int[] data;

        WritePorts(int enable, boolean enableFloatingWrites, int... data) {
            this.enable = enable;
            this.enableFloatingWrites = enableFloatingWrites;
            this.data = data;
        }
    }

    /** 부품 종류별 쓰기 포트(등록표 Kinds의 포트 이름). 쓰기를 보지 않는 종류는 null. */
    static WritePorts writePorts(Component c) {
        String f = Kinds.of(c).factory();
        int n = c.getEnds().size();
        switch (f) {
        case "Register":
            return new WritePorts(4, true, 1);
        case "D Flip-Flop":
        case "T Flip-Flop":
            return new WritePorts(n - 1, true, 0);
        case "J-K Flip-Flop":
        case "S-R Flip-Flop":
            return new WritePorts(n - 1, true, 0, 1);
        case "Data Memory":
        case "Stack":
            return new WritePorts(2, false, 1, 0); // MemWrite; WriteData, Addr
        default:
            return null;
        }
    }

    private void writes(int step, Map<String, Integer> seen, List<Diagnostic> out) {
        int before = step - 1;
        for (List<Component> path : rec.paths()) {
            Circuit c = circuitOf(path);
            if (c == null) {
                continue;
            }
            Netlist nl = nets.netlist(c);
            for (Component x : c.getNonWires()) {
                WritePorts wp = writePorts(x);
                if (wp == null || !edge(path, x, before, step)) {
                    continue;
                }
                Value en = wp.enable < 0 ? Value.TRUE : value(path, x, wp.enable, before);
                boolean enConnected = wp.enable >= 0 && connected(nl, x, wp.enable);
                if (enConnected && undefined(en)) {
                    report(Diagnostic.Kind.X_WRITE_CONTROL, path, c, x, wp.enable, step, seen, out);
                    continue;
                }
                boolean writes = en == Value.TRUE || !enConnected && wp.enableFloatingWrites;
                if (!writes) {
                    continue;
                }
                for (int d : wp.data) {
                    if (connected(nl, x, d) && undefined(value(path, x, d, before))) {
                        report(Diagnostic.Kind.X_WRITE_DATA, path, c, x, d, step, seen, out);
                        break;
                    }
                }
            }
        }
    }

    private void report(Diagnostic.Kind kind, List<Component> path, Circuit c, Component x, int port, int step,
            Map<String, Integer> seen, List<Diagnostic> out) {
        int before = step - 1;
        Trace.Node n = trace.node(path, c, x.getEnd(port).getLocation());
        OriginTrace.Origin o = trace.find(n, before);
        String k = o != null ? key(o) : kind + "|" + System.identityHashCode(x) + ":" + port + ":" + path;
        if (seen.putIfAbsent(k, step) != null) {
            return;
        }
        String where = Names.path(InstancePaths.describe(top, path), Names.name(c, x));
        String portName = Kinds.portName(x, port);
        String because = o == null ? "" : Messages.get("diag.causePrefix", cause(o));
        if (o == null) {
            out.add(new Diagnostic(kind, c, path, before, Collections.singletonList(x),
                    Collections.<Wire>emptyList(), x.getEnd(port).getLocation(), cycleOf(before), where, portName,
                    because).appearedAt(path, c, x.getEnd(port).getLocation()));
            return;
        }
        // 누르면 원인으로 간다: 원인 부품·선을 강조하고, 쓰려던 부품이 같은 인스턴스에 있으면 함께
        List<Component> comps = components(o);
        if (o.node.circuit == c && o.node.instances.equals(path) && !comps.contains(x)) {
            comps.add(x);
        }
        out.add(new Diagnostic(kind, o.node.circuit, o.node.instances, before, comps, wires(o), location(o),
                cycleOf(before), where, portName, because).appearedAt(path, c, x.getEnd(port).getLocation()));
    }

    /** before → step 사이에 이 부품의 클럭이 트리거 방향으로 바뀌었는가(상승이 기본, 하강 설정이면 하강). */
    private boolean edge(List<Component> path, Component x, int before, int step) {
        int clk = -1;
        for (int i = 0; i < x.getEnds().size(); i++) {
            if (Kinds.portName(x, i).equals("clk")) {
                clk = i;
            }
        }
        if (clk < 0) {
            return false;
        }
        Value a = value(path, x, clk, before);
        Value b = value(path, x, clk, step);
        Object trig = x.getAttributeSet().containsAttribute(StdAttr.TRIGGER)
                ? x.getAttributeSet().getValue(StdAttr.TRIGGER) : StdAttr.TRIG_RISING;
        if (trig == StdAttr.TRIG_FALLING) {
            return a == Value.TRUE && b == Value.FALSE;
        }
        if (trig == StdAttr.TRIG_RISING) {
            return a == Value.FALSE && b == Value.TRUE;
        }
        return false; // 레벨 트리거: 에지가 없다
    }

    private Value value(List<Component> path, Component x, int end, int step) {
        return rec.value(path, x.getEnd(end).getLocation(), step);
    }

    /** 포트가 무엇엔가 이어져 있는가(선이나 다른 포트). */
    static boolean connected(Netlist nl, Component x, int end) {
        Netlist.Net n = nl.netOf(x, end);
        return n != null && (!n.wires().isEmpty() || n.ports().size() > 1);
    }

    static boolean undefined(Value v) {
        return v != null && !v.isFullyDefined();
    }

    private Circuit circuitOf(List<Component> path) {
        return path.isEmpty() ? top : ((SubcircuitFactory) path.get(path.size() - 1).getFactory()).getSubcircuit();
    }

    private String cause(OriginTrace.Origin o) {
        return OriginText.cause(top, o);
    }

    private static String key(OriginTrace.Origin o) {
        return OriginText.key(o);
    }

    private static List<Component> components(OriginTrace.Origin o) {
        return OriginText.components(o);
    }

    private static List<Wire> wires(OriginTrace.Origin o) {
        return OriginText.wires(o);
    }

    private static Location location(OriginTrace.Origin o) {
        return OriginText.location(o);
    }
}
