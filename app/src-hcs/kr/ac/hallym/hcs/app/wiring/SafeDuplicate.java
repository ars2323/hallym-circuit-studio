/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.wiring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.proj.Project;

/**
 * 복제(W-05). 원조 복제는 겹치지 않는 가장 가까운 자리에 사본을 띄워 두는데, 그 자리에서 사본의 포트가 옛 선에
 * 닿거나 사본 선이 옛 포트에 닿으면 내려놓는 순간 뜻하지 않은 연결이 생긴다. 그러면 닿지 않는 가장 가까운 자리로 더
 * 옮겨 내려놓는다. 옮기기는 원조가 복제에 이어 붙이므로 되돌리기 한 번에 함께 취소된다. 학생이 사본을 끌어 선 위에
 * 두는 것은 학생의 배치이므로 막지 않는다.
 */
public final class SafeDuplicate {
    /** 찾아볼 자리 수(격자 10px, 가까운 것부터). */
    static final int TRIES = 24 * 24;

    private SafeDuplicate() {
    }

    public static void run(Project proj, Selection sel) {
        proj.doAction(SelectionActions.duplicate(sel));
        Circuit circuit = proj.getCurrentCircuit();
        List<Component> floating = new ArrayList<>(sel.getFloatingComponents());
        if (circuit == null || floating.isEmpty() || !WireGuard.touches(circuit, floating, 0, 0)) {
            return;
        }
        int[] off = freeOffset(circuit, floating);
        if (off != null) {
            proj.doAction(SelectionActions.translate(sel, off[0], off[1], null));
        }
    }

    /** 닿지 않는 가장 가까운 옮김(원조 복제와 같은 네모 나선 순서). 없으면 null. */
    public static int[] freeOffset(Circuit circuit, Collection<Component> floating) {
        Bounds bds = Bounds.EMPTY_BOUNDS;
        for (Component c : floating) {
            bds = bds.add(c.getBounds());
        }
        for (int index = 1; index < TRIES; index++) {
            int[] d = spiral(index);
            int dx = d[0] * 10;
            int dy = d[1] * 10;
            if (bds.getX() + dx >= 0 && bds.getY() + dy >= 0 && !WireGuard.touches(circuit, floating, dx, dy)) {
                return new int[] {dx, dy};
            }
        }
        return null;
    }

    /** 원조 SelectionBase.copyComponents의 나선: 0,0에서 점점 큰 네모의 가장자리를 돈다(격자 단위). */
    static int[] spiral(int index) {
        int side = 1;
        while (side * side <= index) {
            side += 2;
        }
        int offs = index - (side - 2) * (side - 2);
        int dx = side / 2;
        int dy = side / 2;
        if (offs < side - 1) {
            dx -= offs;
        } else if (offs < 2 * (side - 1)) {
            offs -= side - 1;
            dx = -dx;
            dy -= offs;
        } else if (offs < 3 * (side - 1)) {
            offs -= 2 * (side - 1);
            dx = -dx + offs;
            dy = -dy;
        } else {
            offs -= 3 * (side - 1);
            dy = -dy + offs;
        }
        return new int[] {dx, dy};
    }
}
