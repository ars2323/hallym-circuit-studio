/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.ext.CircExtension;
import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.model.Names;

/**
 * "Mark as PC"(V-08, D-103): 상태 표시줄 PC로 쓸 레지스터를 학생이 직접 정한다. PLAN.md 7.0 네임스페이스에
 * {@code <item kind="pc" at="(x,y)"/>}로 회로마다 하나 저장한다(원조는 무시하고 연다). 부품이 사라지면 표시도 지운다.
 */
public final class PcMark {
    static final String KIND = "pc";

    public static final CircExtensions.Pruner PRUNER = (file, ext) -> {
        for (Circuit c : file.getCircuits()) {
            for (CircExtension.Item item : new ArrayList<>(ext.items(c.getName()))) {
                if (item.kind().equals(KIND) && at(c, item.get("at")) == null) {
                    ext.remove(c.getName(), item);
                }
            }
        }
    };

    private PcMark() {
    }

    static Component at(Circuit c, String loc) {
        if (loc == null) {
            return null;
        }
        Location l;
        try {
            l = Location.parse(loc);
        } catch (RuntimeException e) {
            return null;
        }
        Component other = null;
        for (Component x : c.getNonWires()) {
            if (x.getLocation().equals(l) && !x.getEnds().isEmpty()) {
                if (markable(x)) {
                    return x; // 같은 자리에 터널·핀이 겹쳐 있어도 표시한 것은 레지스터·카운터다(X-04)
                }
                if (other == null) {
                    other = x;
                }
            }
        }
        return other;
    }

    /** 이 회로에서 PC로 표시한 부품. 없으면 null. */
    public static Component marked(LogisimFile file, Circuit circuit) {
        if (file == null || circuit == null) {
            return null;
        }
        for (CircExtension.Item item : CircExtensions.of(file).items(circuit.getName())) {
            if (item.kind().equals(KIND)) {
                return at(circuit, item.get("at"));
            }
        }
        return null;
    }

    /** 표시할 수 있는 부품인가: 레지스터·카운터. */
    public static boolean markable(Component c) {
        String f = c.getFactory().getName();
        return f.equals("Register") || f.equals("Counter");
    }

    static void set(LogisimFile file, Circuit circuit, Component c) {
        CircExtension ext = CircExtensions.of(file);
        for (CircExtension.Item item : new ArrayList<>(ext.items(circuit.getName()))) {
            if (item.kind().equals(KIND)) {
                ext.remove(circuit.getName(), item);
            }
        }
        if (c != null) {
            Map<String, String> attrs = new LinkedHashMap<>();
            attrs.put("at", c.getLocation().toString());
            ext.add(circuit.getName(), new CircExtension.Item(KIND, attrs));
        }
    }

    /** 표시(on) 또는 해제 동작. 되돌리기 한 번. */
    public static Action action(LogisimFile file, Circuit circuit, Component c, boolean on) {
        Component before = marked(file, circuit);
        return new Action() {
            @Override
            public String getName() {
                return Messages.get(on ? "pc.markAction" : "pc.unmarkAction", Names.name(circuit, c));
            }

            @Override
            public void doIt(Project proj) {
                set(file, circuit, on ? c : null);
                proj.repaintCanvas(); // 상태 표시줄은 프로젝트 이벤트로 새로 그린다
            }

            @Override
            public void undo(Project proj) {
                set(file, circuit, before);
                proj.repaintCanvas();
            }
        };
    }
}
