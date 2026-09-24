/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.smoke;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;

/** 32비트 입력 A에 1을 더해 Y로 내는 최소 부품. A가 정의되지 않으면 Y는 E다. */
public class Incrementer extends InstanceFactory {
    static final BitWidth WIDTH = BitWidth.create(32);

    public Incrementer() {
        super("Incrementer");
        setOffsetBounds(Bounds.create(-30, -15, 30, 30));
        setPorts(new Port[] {
            new Port(-30, 0, Port.INPUT, WIDTH),
            new Port(0, 0, Port.OUTPUT, WIDTH),
        });
    }

    @Override
    public void propagate(InstanceState state) {
        Value a = state.getPort(0);
        Value y = a.isFullyDefined()
                ? Value.createKnown(WIDTH, a.toIntValue() + 1)
                : Value.createError(WIDTH);
        state.setPort(1, y, 1);
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        painter.drawBounds();
        painter.drawLabel();
        painter.drawPorts();
    }
}
