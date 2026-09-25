/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.circuit.WireSet;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Location;

/**
 * 한 회로의 넷(#71, PLAN.md 11.0 "연결 탐색 엔진은 하나다"). 선은 원조의 연결 계산({@link Circuit#getWireSet})으로
 * 묶고, 그 선의 끝점이나 선 위(부록 A.4 "포트 위를 지나감")에 닿은 포트를 넷에 넣는다. 선 없이 같은 점에 닿은
 * 포트끼리도 한 넷이다. 같은 이름의 터널은 한 넷으로 합친다. 스플리터는 넷을 합치지 않고 비트 대응
 * ({@link BitLink})으로 잇는다. 서브회로 경계는 {@link Hierarchy}가 잇는다. GUI 없이 회로 모델만 본다.
 */
public final class Netlist {
    /** 부품의 포트 하나. */
    public static final class PortRef {
        public final Component component;
        public final int end;

        public PortRef(Component component, int end) {
            this.component = component;
            this.end = end;
        }

        public EndData data() {
            return component.getEnds().get(end);
        }

        public Location location() {
            return data().getLocation();
        }

        public int width() {
            return data().getWidth().getWidth();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PortRef && ((PortRef) o).component == component && ((PortRef) o).end == end;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(component) * 31 + end;
        }

        @Override
        public String toString() {
            return component.getFactory().getName() + "@" + component.getLocation() + "#" + end;
        }
    }

    /** 넷 하나. */
    public static final class Net {
        final int id;
        final List<PortRef> ports = new ArrayList<>();
        final List<Wire> wires = new ArrayList<>();

        Net(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public List<PortRef> ports() {
            return Collections.unmodifiableList(ports);
        }

        public List<Wire> wires() {
            return Collections.unmodifiableList(wires);
        }

        /** 넷의 폭: 포트 폭 중 가장 큰 것(폭 불일치 진단은 따로). 포트가 없으면 0. */
        public int width() {
            int w = 0;
            for (PortRef p : ports) {
                w = Math.max(w, p.width());
            }
            return w;
        }

        /** 이 넷에 값을 내는 포트(출력 전용). 터널·스플리터·양방향 포트는 뺀다. */
        public List<PortRef> drivers() {
            List<PortRef> ret = new ArrayList<>();
            for (PortRef p : ports) {
                if (p.data().isOutput() && !p.data().isInput()) {
                    ret.add(p);
                }
            }
            return ret;
        }

        /** 이 넷의 값을 읽는 포트(입력 전용). */
        public List<PortRef> readers() {
            List<PortRef> ret = new ArrayList<>();
            for (PortRef p : ports) {
                if (p.data().isInput() && !p.data().isOutput()) {
                    ret.add(p);
                }
            }
            return ret;
        }

        @Override
        public String toString() {
            return "net" + id + ports;
        }
    }

    /** 스플리터 비트 대응: 묶인 쪽 넷의 bit 번째 비트 = 팔 쪽 넷의 armBit 번째 비트. */
    public static final class BitLink {
        public final Component splitter;
        public final Net combined;
        public final int bit;
        public final Net arm;
        public final int armIndex;
        public final int armBit;

        BitLink(Component splitter, Net combined, int bit, Net arm, int armIndex, int armBit) {
            this.splitter = splitter;
            this.combined = combined;
            this.bit = bit;
            this.arm = arm;
            this.armIndex = armIndex;
            this.armBit = armBit;
        }
    }

    private final Circuit circuit;
    private final List<Net> nets = new ArrayList<>();
    private final Map<PortRef, Net> byPort = new HashMap<>();
    private final Map<Wire, Net> byWire = new IdentityHashMap<>();
    private final List<BitLink> links = new ArrayList<>();

    private Netlist(Circuit circuit) {
        this.circuit = circuit;
    }

    public Circuit circuit() {
        return circuit;
    }

    public List<Net> nets() {
        return Collections.unmodifiableList(nets);
    }

    public List<BitLink> bitLinks() {
        return Collections.unmodifiableList(links);
    }

    /** 포트가 속한 넷. 아무것에도 닿지 않은 포트도 혼자 넷 하나다. */
    public Net netOf(Component c, int end) {
        return byPort.get(new PortRef(c, end));
    }

    public Net netOf(Wire w) {
        return byWire.get(w);
    }

    /** 회로의 넷을 계산한다. */
    public static Netlist of(Circuit circuit) {
        Netlist n = new Netlist(circuit);
        n.build();
        return n;
    }

    private void build() {
        List<Component> comps = new ArrayList<>(circuit.getNonWires());
        List<Wire> allWires = new ArrayList<>(circuit.getWires());
        // 1. 원조 연결 계산으로 선 묶음
        for (Wire w : allWires) {
            if (byWire.containsKey(w)) {
                continue;
            }
            WireSet ws = circuit.getWireSet(w);
            Net net = new Net(nets.size());
            for (Wire o : allWires) {
                if (ws.containsWire(o)) {
                    net.wires.add(o);
                    byWire.put(o, net);
                }
            }
            nets.add(net);
        }
        // 2. 포트: 선 묶음의 끝점이나 선 위에 있으면 그 넷, 아니면 같은 점의 포트끼리
        Map<Location, Net> byPoint = new LinkedHashMap<>();
        for (Component c : comps) {
            for (int i = 0; i < c.getEnds().size(); i++) {
                PortRef p = new PortRef(c, i);
                Location at = p.location();
                Net net = wiredNet(at);
                if (net == null) {
                    net = byPoint.get(at);
                    if (net == null) {
                        net = new Net(nets.size());
                        nets.add(net);
                        byPoint.put(at, net);
                    }
                }
                net.ports.add(p);
                byPort.put(p, net);
            }
        }
        // 3. 같은 이름의 터널은 한 넷
        Map<String, Net> tunnels = new HashMap<>();
        for (Component c : comps) {
            if (Kinds.of(c).factory().equals("Tunnel")) {
                String label = Names.label(c);
                if (label == null) {
                    continue;
                }
                Net here = byPort.get(new PortRef(c, 0));
                Net first = tunnels.get(label);
                if (first == null) {
                    tunnels.put(label, here);
                } else if (first != here) {
                    merge(first, here);
                }
            }
        }
        // 빈 넷(합쳐진 것) 정리, 번호 다시 매기기
        List<Net> kept = new ArrayList<>();
        for (Net net : nets) {
            if (!net.ports.isEmpty() || !net.wires.isEmpty()) {
                kept.add(net);
            }
        }
        nets.clear();
        for (Net net : kept) {
            Net renum = new Net(nets.size());
            renum.ports.addAll(net.ports);
            renum.wires.addAll(net.wires);
            nets.add(renum);
            for (PortRef p : renum.ports) {
                byPort.put(p, renum);
            }
            for (Wire w : renum.wires) {
                byWire.put(w, renum);
            }
        }
        // 4. 스플리터 비트 대응
        for (Component c : comps) {
            if (Kinds.of(c).factory().equals("Splitter")) {
                linkSplitter(c);
            }
        }
    }

    /** 점 at에 닿은 선 묶음(끝점 또는 선 위). */
    private Net wiredNet(Location at) {
        for (Map.Entry<Wire, Net> e : byWire.entrySet()) {
            Wire w = e.getKey();
            if (w.getEnd0().equals(at) || w.getEnd1().equals(at) || w.contains(at)) {
                return e.getValue();
            }
        }
        return null;
    }

    private void merge(Net into, Net from) {
        for (PortRef p : from.ports) {
            into.ports.add(p);
            byPort.put(p, into);
        }
        for (Wire w : from.wires) {
            into.wires.add(w);
            byWire.put(w, into);
        }
        from.ports.clear();
        from.wires.clear();
    }

    @SuppressWarnings("unchecked")
    private void linkSplitter(Component s) {
        int width = s.getEnds().get(0).getWidth().getWidth();
        int[] armBits = new int[s.getEnds().size()];
        Net combined = byPort.get(new PortRef(s, 0));
        for (int b = 0; b < width; b++) {
            Attribute<Object> a = (Attribute<Object>) s.getAttributeSet().getAttribute("bit" + b);
            Object v = a == null ? null : s.getAttributeSet().getValue(a);
            int end = v instanceof Integer ? (Integer) v : 0;
            if (end <= 0 || end >= s.getEnds().size()) {
                continue; // 어느 팔에도 가지 않는 비트
            }
            Net arm = byPort.get(new PortRef(s, end));
            links.add(new BitLink(s, combined, b, arm, end - 1, armBits[end]++));
        }
    }

    /** 스플리터 한 개의 비트 배정: 묶인 쪽 비트마다 팔 번호(0부터, 없으면 -1). */
    public static int[] splitterArms(Component s) {
        int width = s.getEnds().get(0).getWidth().getWidth();
        int[] ret = new int[width];
        for (int b = 0; b < width; b++) {
            @SuppressWarnings("unchecked")
            Attribute<Object> a = (Attribute<Object>) s.getAttributeSet().getAttribute("bit" + b);
            Object v = a == null ? null : s.getAttributeSet().getValue(a);
            ret[b] = v instanceof Integer ? (Integer) v - 1 : -1;
        }
        return ret;
    }
}
