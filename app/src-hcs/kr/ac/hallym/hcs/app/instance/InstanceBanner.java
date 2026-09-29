/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.instance;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.InstancePaths;

/**
 * 서브회로 포트 변경 뒤 선 되살리기(P-02, PLAN.md 11.10): v1 인스턴스 안내 띠의 GUI 없는 부분. 핀을 바꾼 뒤 끊긴
 * 인스턴스 연결 가운데 옛 선 끝이 그대로 있으면 새 포트 자리까지 선을 이어 되살린다(새 선은
 * {@link kr.ac.hallym.hcs.app.wiring.WireGuard}를 거친다). 엔진의 {@code PortWatch}가 쓴다. 띠(Swing)는 화면 코드와
 * 함께 지웠다(N-27, D-163, 옛 코드는 태그 {@code swing-final}).
 */
public final class InstanceBanner {
    private InstanceBanner() {
    }

    /**
     * 끊긴 곳 가운데 옛 선 끝이 옛 포트 자리에 그대로 있고 새 포트 자리가 있으면, 새 자리에서 옛 자리까지 선을 잇는다
     * (한 줄이면 곧게, 아니면 가로 먼저 ㄱ자). 부모 회로마다 한 번의 변경이고 {@link kr.ac.hallym.hcs.app.wiring.WireGuard}
     * 검사를 통과할 때만 남긴다. 되살린 수.
     */
    public static int reconnect(Project proj, List<InstancePaths.Broken> broken) {
        java.util.Map<Circuit, List<InstancePaths.Broken>> byParent = new java.util.LinkedHashMap<>();
        for (InstancePaths.Broken x : broken) {
            // 새 자리가 비어 있고(다른 것에 닿지 않음) 옛 자리에 선 끝이 남은 경우만 잇는다
            if (x.now != null && InstancePaths.wireEndsAt(x.before.parent, x.before.at)
                    && !InstancePaths.touchesAnything(x.before.parent, x.before.instance, x.now)) {
                byParent.computeIfAbsent(x.before.parent, k -> new ArrayList<>()).add(x);
            }
        }
        int kept = 0;
        for (java.util.Map.Entry<Circuit, List<InstancePaths.Broken>> e : byParent.entrySet()) {
            Circuit parent = e.getKey();
            CircuitMutation m = new CircuitMutation(parent);
            List<Location> allowed = new ArrayList<>();
            for (InstancePaths.Broken x : e.getValue()) {
                Location from = x.now;
                Location to = x.before.at;
                if (from.getX() == to.getX() || from.getY() == to.getY()) {
                    m.add(Wire.create(from, to));
                } else {
                    Location bend = Location.create(to.getX(), from.getY());
                    m.add(Wire.create(from, bend));
                    m.add(Wire.create(bend, to));
                }
                allowed.add(from);
                allowed.add(to);
            }
            if (kr.ac.hallym.hcs.app.wiring.WireGuard.run(proj, parent, m, allowed,
                    () -> Messages.get("instance.reconnectAction"))) {
                kept += e.getValue().size();
            }
        }
        return kept;
    }
}
