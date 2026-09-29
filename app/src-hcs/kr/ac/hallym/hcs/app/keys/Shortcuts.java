/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.keys;

import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.std.wiring.Pin;

/**
 * v1 단축키 코드(#78)의 GUI 없는 부분: R·Shift+R 회전 변경과 입력 핀 값 넣기(값 글 해석, 원조 Pin.setValue). 엔진의
 * {@code edit.rotate}·{@code sim.pinValue}가 쓴다. 키·마우스 처리와 단축키 표 창은 Swing 화면과 함께 지웠다(N-27,
 * D-163, 옛 코드는 태그 {@code swing-final}).
 */
public final class Shortcuts {
    private Shortcuts() {
    }

    /** 회전 변경(GUI 없이 테스트). */
    public static com.cburch.logisim.circuit.CircuitMutation rotation(Circuit circuit, List<Component> comps,
            boolean clockwise) {
        com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(circuit);
        for (Component c : comps) {
            Direction d = c.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.FACING);
            if (d != null) {
                m.set(c, com.cburch.logisim.instance.StdAttr.FACING, next(d, clockwise));
            }
        }
        return m;
    }

    /** 다음 방향(시계 방향이면 오른쪽→아래→왼쪽→위). */
    public static Direction next(Direction d, boolean clockwise) {
        Direction[] cw = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
        for (int i = 0; i < 4; i++) {
            if (cw[i] == d) {
                return cw[(i + (clockwise ? 1 : 3)) % 4];
            }
        }
        return d;
    }

    /**
     * 입력 핀에 값을 넣는다. 원조 조작 도구가 핀을 누를 때와 같은 경로(Pin.setValue)라 시뮬레이션 상태만 바뀌고
     * .circ에는 남지 않는다.
     */
    public static void setPinValue(CircuitState state, Component pin, long v) {
        int width = pin.getEnds().get(0).getWidth().getWidth();
        InstanceState is = state.getInstanceState(pin);
        Pin.FACTORY.setValue(is, Value.createKnown(BitWidth.create(width), (int) v));
    }

    /** 지금 입력 핀이 내는 값(테스트용). */
    static Value pinValue(CircuitState state, Component pin) {
        return Pin.FACTORY.getValue(state.getInstanceState(pin));
    }

    /** 값 해석: 0x·0b 접두사, 10진(음수는 2의 보수). 폭을 넘으면 null. */
    public static Long parseValue(String text, int width) {
        String t = text.trim().replace("_", "").replace(" ", "");
        if (t.isEmpty()) {
            return null;
        }
        long v;
        try {
            if (t.startsWith("0x") || t.startsWith("0X")) {
                v = Long.parseLong(t.substring(2), 16);
            } else if (t.startsWith("0b") || t.startsWith("0B")) {
                v = Long.parseLong(t.substring(2), 2);
            } else {
                v = Long.parseLong(t);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        long mask = width >= 64 ? -1L : (1L << width) - 1;
        if (v < 0) {
            if (-v > (1L << (width - 1))) {
                return null;
            }
            return v & mask;
        }
        return v > mask ? null : v;
    }
}
