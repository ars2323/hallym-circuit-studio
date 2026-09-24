/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.MouseEvent;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.GraphicsUtil;

/**
 * 다중 진법 Probe(PLAN.md 5.1). 입력 하나의 값을 16진수·10진수·2진수로 함께 보인다. 주 진법(굵게, 맨 위)은
 * {@code radix} 속성으로 정하고, 시뮬레이션 중에는 찌르기 도구로 클릭할 때마다 바뀐다(저장하지 않음).
 * 10진수는 {@code signed} 속성으로 부호 있음·없음을 고르고, 2진수는 4비트씩 끊는다.
 */
final class RadixProbe extends InstanceFactory {
    static final int HEX = 0;
    static final int DEC = 1;
    static final int BIN = 2;

    static final AttributeOption[] RADIXES = {
        new AttributeOption("hex", "hex", Text.of("Hexadecimal", "16진수")),
        new AttributeOption("dec", "dec", Text.of("Decimal", "10진수")),
        new AttributeOption("bin", "bin", Text.of("Binary", "2진수")),
    };
    static final Attribute<AttributeOption> RADIX =
            Attributes.forOption("radix", Text.of("Primary Radix", "주 진법"), RADIXES);
    static final Attribute<Boolean> SIGNED =
            Attributes.forBoolean("signed", Text.of("Signed Decimal", "부호 있는 10진수"));

    private static final Font PRIMARY_FONT = new Font("Monospaced", Font.BOLD, 11);
    private static final Font SECONDARY_FONT = new Font("Monospaced", Font.PLAIN, 10);
    private static final Color SECONDARY_COLOR = new Color(0x5A6472); // Hallym MIPS text-2
    private static final int HEIGHT = 50;

    /** 시뮬레이션 중 찌르기로 바꾼 주 진법. 없으면 속성을 따른다. */
    static final class State implements InstanceData, Cloneable {
        int primary = -1;

        @Override
        public State clone() {
            try {
                return (State) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    public static final class Poker extends InstancePoker {
        @Override
        public void mousePressed(InstanceState state, MouseEvent e) {
            State st = (State) state.getData();
            if (st == null) {
                st = new State();
                state.setData(st);
            }
            st.primary = (primary(state, st) + 1) % RADIXES.length;
            state.fireInvalidated();
        }
    }

    RadixProbe() {
        super("Radix Probe", Text.of("Radix Probe", "다중 진법 Probe"));
        setAttributes(new Attribute<?>[] {StdAttr.WIDTH, RADIX, SIGNED, StdAttr.LABEL, StdAttr.LABEL_FONT},
                new Object[] {BitWidth.create(32), RADIXES[HEX], Boolean.TRUE, "", StdAttr.DEFAULT_LABEL_FONT});
        setPorts(new Port[] {new Port(0, 0, Port.INPUT, StdAttr.WIDTH)});
        setInstancePoker(Poker.class);
    }

    /** 가장 긴 줄(2진수)이 들어가는 폭. 입력은 왼쪽 가운데. */
    @Override
    public Bounds getOffsetBounds(AttributeSet attrs) {
        int bits = attrs.getValue(StdAttr.WIDTH).getWidth();
        int chars = Math.max(bits + (bits - 1) / 4, 10);
        int width = ((chars * 6 + 20) + 9) / 10 * 10;
        return Bounds.create(0, -HEIGHT / 2, width, HEIGHT);
    }

    @Override
    protected void configureNewInstance(Instance instance) {
        instance.addAttributeListener();
        placeLabel(instance);
    }

    @Override
    protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
        if (attr == StdAttr.WIDTH) {
            instance.recomputeBounds();
            placeLabel(instance);
        }
    }

    private static void placeLabel(Instance instance) {
        Bounds b = instance.getBounds();
        instance.setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT,
                b.getX() + b.getWidth() / 2, b.getY() - 3, GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);
    }

    @Override
    public void propagate(InstanceState state) {
        // 출력이 없다. 그리기만 한다.
    }

    static int primary(InstanceState state, State st) {
        if (st != null && st.primary >= 0) {
            return st.primary;
        }
        AttributeOption radix = state.getAttributeValue(RADIX);
        for (int i = 0; i < RADIXES.length; i += 1) {
            if (RADIXES[i] == radix) {
                return i;
            }
        }
        return HEX;
    }

    /** 세 진법으로 쓴 값. 주 진법이 첫 줄이고 나머지는 16·10·2진수 순서. */
    static String[] lines(Value v, int primary, boolean signed) {
        String[] all = {hex(v), dec(v, signed), bin(v)};
        String[] out = new String[3];
        out[0] = all[primary];
        int k = 1;
        for (int i = 0; i < 3; i += 1) {
            if (i != primary) {
                out[k++] = all[i];
            }
        }
        return out;
    }

    static String hex(Value v) {
        int w = v.getWidth();
        StringBuilder sb = new StringBuilder("0x");
        for (int nibble = (w + 3) / 4 - 1; nibble >= 0; nibble -= 1) {
            int digit = 0;
            char mark = 0;
            for (int i = Math.min(w - 1, 4 * nibble + 3); i >= 4 * nibble; i -= 1) {
                Value bit = v.get(i);
                if (bit == Value.ERROR) {
                    mark = 'E';
                } else if (bit != Value.TRUE && bit != Value.FALSE && mark == 0) {
                    mark = 'x';
                }
                digit = digit << 1 | (bit == Value.TRUE ? 1 : 0);
            }
            sb.append(mark != 0 ? mark : Character.forDigit(digit, 16));
        }
        return sb.toString();
    }

    static String dec(Value v, boolean signed) {
        if (v.isErrorValue()) {
            return "E";
        }
        if (!v.isFullyDefined()) {
            return "x";
        }
        int w = v.getWidth();
        long raw = v.toIntValue() & (w == 32 ? 0xffffffffL : (1L << w) - 1);
        if (signed && w > 1 && (raw >>> (w - 1)) != 0) {
            raw -= 1L << w;
        }
        return Long.toString(raw);
    }

    static String bin(Value v) {
        StringBuilder sb = new StringBuilder();
        int w = v.getWidth();
        for (int i = w - 1; i >= 0; i -= 1) {
            Value bit = v.get(i);
            sb.append(bit == Value.TRUE ? '1' : bit == Value.FALSE ? '0' : bit == Value.ERROR ? 'E' : 'x');
            if (i > 0 && i % 4 == 0) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        Graphics g = painter.getGraphics();
        Bounds b = painter.getBounds();
        painter.drawBounds();
        painter.drawLabel();
        painter.drawPorts();
        if (!painter.getShowState()) {
            return;
        }
        Value v = painter.getPort(0);
        State st = (State) painter.getData();
        String[] lines = lines(v, primary(painter, st), painter.getAttributeValue(SIGNED));
        int x = b.getX() + 10;
        g.setColor(Color.BLACK);
        g.setFont(PRIMARY_FONT);
        GraphicsUtil.drawText(g, lines[0], x, b.getY() + 16, GraphicsUtil.H_LEFT, GraphicsUtil.V_BASELINE);
        g.setFont(SECONDARY_FONT);
        g.setColor(SECONDARY_COLOR);
        GraphicsUtil.drawText(g, lines[1], x, b.getY() + 30, GraphicsUtil.H_LEFT, GraphicsUtil.V_BASELINE);
        GraphicsUtil.drawText(g, lines[2], x, b.getY() + 43, GraphicsUtil.H_LEFT, GraphicsUtil.V_BASELINE);
        g.setColor(Color.BLACK);
    }

    @Override
    public void paintIcon(InstancePainter painter) {
        Graphics g = painter.getGraphics();
        g.setColor(Color.BLACK);
        g.drawRoundRect(1, 4, 18, 12, 4, 4);
        g.setFont(new Font("SansSerif", Font.BOLD, 7));
        GraphicsUtil.drawCenteredText(g, "0x", 10, 9);
    }
}
