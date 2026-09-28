/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.edit;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.instance.InstanceBanner;
import kr.ac.hallym.hcs.app.model.InstancePaths;
import kr.ac.hallym.hcs.engine.doc.Doc;

/**
 * 서브회로 포트 변경 영향(v1 P-02 {@code InstanceBanner.projectChanged}, D-064): 지금 회로가 주 회로가 아니고
 * 그 인스턴스가 있을 때, 한 동작의 앞뒤로 모든 인스턴스 포트의 연결을 비교한다. 끊긴 곳(핀을 지움, 포트가 옮겨져
 * 떨어지거나 옆 선에 붙음)은 옛 자리에 선 끝이 남아 있고 새 자리가 비었으면 v1처럼 선을 이어 되살린다(부모 회로마다
 * 한 동작, 검사기 WireGuard를 거침, 따로 되돌릴 수 있는 한 단계 "Keep Instance Connections"). 결과는 화면에
 * {@code model.portImpact}로 알린다.
 *
 * <p>v1은 동작이 끝난 뒤 Swing 사건 줄에서 했다. 엔진은 의도 하나를 마친 뒤 같은 스레드에서 한다({@link #settle}).
 */
public final class PortWatch implements ProjectListener {
    private final Doc d;
    private List<InstancePaths.PortUse> before;
    private Circuit beforeCircuit;
    private final List<Object[]> pending = new ArrayList<>();

    public PortWatch(Doc d) {
        this.d = d;
        d.project().addProjectListener(this);
    }

    @Override
    public void projectChanged(ProjectEvent e) {
        int a = e.getAction();
        if (a == ProjectEvent.ACTION_START) {
            Circuit cur = d.project().getCurrentCircuit();
            LogisimFile file = d.file();
            if (cur != null && cur != file.getMainCircuit() && hasInstances(file, cur)) {
                before = InstancePaths.snapshot(file, cur);
                beforeCircuit = cur;
            } else {
                before = null;
            }
        } else if (a == ProjectEvent.ACTION_COMPLETE && before != null) {
            pending.add(new Object[] {beforeCircuit, before});
            before = null;
        }
    }

    static boolean hasInstances(LogisimFile file, Circuit sub) {
        for (Circuit c : file.getCircuits()) {
            for (Component x : c.getNonWires()) {
                if (x.getFactory() instanceof SubcircuitFactory
                        && ((SubcircuitFactory) x.getFactory()).getSubcircuit() == sub) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 의도가 끝난 뒤: 끊긴 연결을 세고 되살린다. 알릴 것({fileId, circuitId, name, broken, kept})들. */
    @SuppressWarnings("unchecked")
    public List<JsonObject> settle() {
        List<JsonObject> out = new ArrayList<>();
        for (int round = 0; round < 4 && !pending.isEmpty(); round++) {
            List<Object[]> now = new ArrayList<>(pending);
            pending.clear();
            for (Object[] p : now) {
                Circuit sub = (Circuit) p[0];
                if (!d.file().contains(sub)) {
                    continue;
                }
                List<InstancePaths.Broken> broken = InstancePaths.broken((List<InstancePaths.PortUse>) p[1],
                        InstancePaths.snapshot(d.file(), sub));
                if (broken.isEmpty()) {
                    continue;
                }
                int kept = InstanceBanner.reconnect(d.project(), broken);
                JsonObject o = new JsonObject();
                o.addProperty("fileId", d.id());
                o.addProperty("circuitId", d.ids().of(sub));
                o.addProperty("name", sub.getName());
                o.addProperty("broken", broken.size());
                o.addProperty("kept", kept);
                out.add(o);
            }
        }
        pending.clear();
        return out;
    }

    public void close() {
        d.project().removeProjectListener(this);
    }
}
