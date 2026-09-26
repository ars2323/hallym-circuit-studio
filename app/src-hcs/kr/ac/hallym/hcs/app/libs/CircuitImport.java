/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.libs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cburch.draw.model.CanvasObject;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.circuit.appear.AppearancePort;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

import kr.ac.hallym.hcs.app.Messages;

/**
 * 다른 .circ에서 서브회로 가져오기(P-05, PLAN.md 11.10). 고른 회로와 그 안에서 쓰는 서브회로를 딸려서 지금 파일에
 * 복사한다(라이브러리로 가리키는 P-03과 달리 파일 안에 사본이 생긴다). 이름이 겹치면 {@code 이름-2}처럼 번호를 붙인다.
 * 부품은 지금 파일의 라이브러리에서 같은 이름의 부품으로 다시 만들고, 없는 라이브러리의 부품은 건너뛰고 알린다. 사용자
 * 모양(appearance)도 포트를 새 핀에 맞춰 복사한다. 한 동작(되돌리기 한 번). GUI 없이 테스트한다.
 */
public final class CircuitImport {
    /** 가져오기 계획: 차례(딸린 것 먼저), 새 이름, 건너뛸 부품. */
    public static final class Plan {
        public final List<Circuit> order = new ArrayList<>();
        public final Map<Circuit, String> names = new LinkedHashMap<>();
        /** 건너뛸 부품: "회로 › 부품 이름". */
        public final List<String> skipped = new ArrayList<>();

        public boolean renamed(Circuit c) {
            return !c.getName().equals(names.get(c));
        }
    }

    private CircuitImport() {
    }

    /** 고른 회로와 딸린 서브회로의 계획. */
    public static Plan plan(LogisimFile target, LogisimFile source, Collection<Circuit> chosen) {
        Plan p = new Plan();
        Set<Circuit> seen = new LinkedHashSet<>();
        for (Circuit c : chosen) {
            visit(c, source, seen, p.order);
        }
        Set<String> used = new LinkedHashSet<>();
        for (Circuit c : target.getCircuits()) {
            used.add(c.getName());
        }
        for (Circuit c : p.order) {
            String name = c.getName();
            int n = 2;
            while (used.contains(name)) {
                name = c.getName() + "-" + n++;
            }
            used.add(name);
            p.names.put(c, name);
        }
        for (Circuit c : p.order) {
            for (Component x : c.getNonWires()) {
                if (!(x.getFactory() instanceof SubcircuitFactory) && factory(target, x.getFactory()) == null) {
                    p.skipped.add(c.getName() + " › " + kr.ac.hallym.hcs.app.model.Names.name(c, x));
                }
            }
        }
        return p;
    }

    private static void visit(Circuit c, LogisimFile source, Set<Circuit> seen, List<Circuit> order) {
        if (!seen.add(c)) {
            return;
        }
        for (Component x : c.getNonWires()) {
            if (x.getFactory() instanceof SubcircuitFactory) {
                Circuit sub = ((SubcircuitFactory) x.getFactory()).getSubcircuit();
                if (source.getCircuits().contains(sub)) {
                    visit(sub, source, seen, order); // 딸린 것 먼저
                }
            }
        }
        order.add(c);
    }

    /** 지금 파일의 라이브러리에서 같은 이름의 부품(없으면 null). */
    static ComponentFactory factory(LogisimFile target, ComponentFactory f) {
        return find(target, f.getName(), new HashMap<>());
    }

    private static ComponentFactory find(Library lib, String name, Map<Library, Boolean> done) {
        if (done.put(lib, Boolean.TRUE) != null) {
            return null;
        }
        for (Tool t : lib.getTools()) {
            if (t instanceof AddTool && ((AddTool) t).getFactory().getName().equals(name)
                    && !(((AddTool) t).getFactory() instanceof SubcircuitFactory)) {
                return ((AddTool) t).getFactory();
            }
        }
        for (Library sub : lib.getLibraries()) {
            ComponentFactory r = find(sub, name, done);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    /** 속성을 이름으로 맞춰 복사한다(같은 종류의 부품이라도 다른 파일의 속성 객체일 수 있다). */
    @SuppressWarnings("unchecked")
    static void copyAttrs(AttributeSet from, AttributeSet to, String... skip) {
        List<String> skips = java.util.Arrays.asList(skip);
        for (Attribute<?> a : from.getAttributes()) {
            if (skips.contains(a.getName())) {
                continue;
            }
            Attribute<Object> dst = (Attribute<Object>) to.getAttribute(a.getName());
            if (dst == null || to.isReadOnly(dst)) {
                continue;
            }
            Object v = from.getValue(a);
            if (v == null) {
                continue;
            }
            try {
                to.setValue(dst, dst.parse(((Attribute<Object>) a).toStandardString(v)));
            } catch (RuntimeException e) {
                // 파싱할 수 없는 값은 기본값으로 둔다
            }
        }
    }

    /** 계획대로 회로를 만든다(파일에 더한 새 회로들). */
    static List<Circuit> copy(LogisimFile target, Plan p) {
        Map<Circuit, Circuit> made = new HashMap<>();
        List<Circuit> out = new ArrayList<>();
        for (Circuit src : p.order) {
            Circuit nc = new Circuit(p.names.get(src));
            copyAttrs(src.getStaticAttributes(), nc.getStaticAttributes(), CircuitAttributes.NAME_ATTR.getName());
            nc.getStaticAttributes().setValue(CircuitAttributes.NAME_ATTR, p.names.get(src)); // 이름은 새 이름
            target.addCircuit(nc);
            made.put(src, nc);
            out.add(nc);
            CircuitMutation m = new CircuitMutation(nc);
            for (Component x : src.getNonWires()) {
                ComponentFactory f;
                if (x.getFactory() instanceof SubcircuitFactory) {
                    Circuit sub = ((SubcircuitFactory) x.getFactory()).getSubcircuit();
                    Circuit copied = made.get(sub);
                    f = copied == null ? null : copied.getSubcircuitFactory();
                } else {
                    f = factory(target, x.getFactory());
                }
                if (f == null) {
                    continue;
                }
                AttributeSet as = f.createAttributeSet();
                if (f instanceof SubcircuitFactory) {
                    // 인스턴스 속성만: 회로 이름·라벨 속성은 원조가 회로 자체로 넘기므로(이름을 바꿔 버린다) 뺀다
                    copyAttrs(x.getAttributeSet(), as, CircuitAttributes.NAME_ATTR.getName(),
                            CircuitAttributes.CIRCUIT_LABEL_ATTR.getName(),
                            CircuitAttributes.CIRCUIT_LABEL_FACING_ATTR.getName(),
                            CircuitAttributes.CIRCUIT_LABEL_FONT_ATTR.getName());
                } else {
                    copyAttrs(x.getAttributeSet(), as);
                }
                m.add(f.createComponent(x.getLocation(), as));
            }
            for (Wire w : src.getWires()) {
                m.add(Wire.create(w.getEnd0(), w.getEnd1()));
            }
            m.execute();
            if (!src.getAppearance().isDefaultAppearance()) {
                copyAppearance(src, nc);
            }
        }
        return out;
    }

    /** 사용자 모양 복사: 포트 도형은 같은 자리의 새 핀으로 바꾼다. */
    static void copyAppearance(Circuit src, Circuit dst) {
        Map<Location, Instance> pins = new HashMap<>();
        for (Instance pin : dst.getAppearance().getCircuitPins().getPins()) {
            pins.put(pin.getLocation(), pin);
        }
        List<CanvasObject> shapes = new ArrayList<>();
        for (CanvasObject o : src.getAppearance().getObjectsFromBottom()) {
            if (o instanceof AppearancePort) {
                Instance pin = pins.get(((AppearancePort) o).getPin().getLocation());
                if (pin != null) {
                    shapes.add(new AppearancePort(((AppearancePort) o).getLocation(), pin));
                }
            } else {
                shapes.add(o.clone());
            }
        }
        dst.getAppearance().setDefaultAppearance(false);
        dst.getAppearance().setObjectsForce(shapes);
    }

    /** 되돌릴 수 있는 가져오기. */
    public static Action action(LogisimFile target, Plan p) {
        return new Action() {
            private List<Circuit> added = new ArrayList<>();

            @Override
            public String getName() {
                return Messages.get("import.action");
            }

            @Override
            public void doIt(Project proj) {
                added = copy(target, p);
            }

            @Override
            public void undo(Project proj) {
                for (int i = added.size() - 1; i >= 0; i--) {
                    target.removeCircuit(added.get(i));
                }
                added.clear();
            }
        };
    }
}
