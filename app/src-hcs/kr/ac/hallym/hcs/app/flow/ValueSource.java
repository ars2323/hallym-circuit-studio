/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.flow;

import java.util.List;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;

/**
 * 신호 값 읽기(P-07 Active Path Only). 지금 시뮬레이션 상태에서 읽거나, 3단계 사이클 뷰가 고른 사이클의 기록값에서
 * 읽는다(엔진을 다시 돌리지 않는다). 값은 읽기만 한다.
 */
public interface ValueSource {
    /** 서브회로 경로 instances 안의 점 at의 값. 모르면 null. */
    Value value(List<Component> instances, Location at);

    /** 맨 위 회로 상태에서 읽는다(서브회로는 인스턴스의 하위 상태). */
    static ValueSource of(CircuitState top) {
        return (instances, at) -> {
            CircuitState s = top;
            for (Component inst : instances) {
                if (s == null || !(inst.getFactory() instanceof SubcircuitFactory)) {
                    return null;
                }
                Instance i = Instance.getInstanceFor(inst);
                if (i == null) {
                    return null;
                }
                s = ((SubcircuitFactory) inst.getFactory()).getSubstate(s, i);
            }
            return s == null ? null : s.getValue(at);
        };
    }
}
