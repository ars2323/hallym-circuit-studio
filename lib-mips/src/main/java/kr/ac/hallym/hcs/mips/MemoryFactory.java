/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.GraphicsUtil;

/**
 * MIPS 메모리 부품의 공통 부분(PLAN.md 6.2). 32비트 byte 주소를 그대로 받고, 워드 단위로만 접근하며,
 * 자기 영역 밖의 주소에서는 출력을 구동하지 않는다.
 *
 * <p>속성 이름은 .circ에 저장되므로 바꾸지 않는다: {@code base}, {@code size}, {@code contents},
 * {@code source}, {@code label}.
 */
abstract class MemoryFactory extends InstanceFactory {
    static final BitWidth W32 = BitWidth.create(32);
    static final int DELAY = 10; // 원조 RAM·ROM과 같다

    static final Attribute<Integer> BASE =
            Attributes.forHexInteger("base", Text.of("Start Address", "시작 주소"));
    static final Attribute<Integer> SIZE =
            Attributes.forHexInteger("size", Text.of("Limit (bytes)", "한계(바이트)"));
    /** Stack의 맨 위 워드 주소. Stack은 여기서 아래로 자란다. */
    static final Attribute<Integer> TOP =
            Attributes.forHexInteger("top", Text.of("Top Word Address", "맨 위 워드 주소"));
    static final WordImageAttribute CONTENTS =
            new WordImageAttribute("contents", Text.of("Initial Contents", "초기 내용"));
    static final Attribute<String> SOURCE =
            Attributes.forString("source", Text.of("Program (.s)", "프로그램(.s)"));

    static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 11);
    static final Font BODY_FONT = new Font("Monospaced", Font.PLAIN, 10);
    static final Color STATUS_COLOR = new Color(0xC0392B); // Hallym MIPS error 토큰

    private final Text title;

    /**
     * @param start 위로 자라는 메모리는 시작 주소({@code base}), 아래로 자라는 Stack은 맨 위 워드({@code top})
     */
    MemoryFactory(String name, Text displayName, Text title, boolean growsDown, int start, int defaultSize) {
        super(name, displayName);
        this.title = title;
        setAttributes(
                new Attribute<?>[] {growsDown ? TOP : BASE, SIZE, CONTENTS, SOURCE, StdAttr.LABEL, StdAttr.LABEL_FONT},
                new Object[] {start, defaultSize, WordImage.EMPTY, "", "", StdAttr.DEFAULT_LABEL_FONT});
    }

    /**
     * 영역 {낮은 주소, 높은 주소(제외)}. 부호 없는 32비트. 위로 자라면 [base, base+size), 아래로 자라면
     * [top+4−size, top+4). 2^32를 넘거나 0 아래로 가면 자른다.
     */
    static long[] region(AttributeSet attrs) {
        long size = attrs.getValue(SIZE) & 0xffffffffL;
        if (attrs.containsAttribute(TOP)) {
            long high = (attrs.getValue(TOP) & 0xffffffffL) + 4;
            return new long[] {Math.max(0, high - size), high};
        }
        long low = attrs.getValue(BASE) & 0xffffffffL;
        return new long[] {low, Math.min(low + size, 0x100000000L)};
    }

    static boolean contains(long[] region, int addr) {
        long a = addr & 0xffffffffL;
        return a >= region[0] && a < region[1];
    }

    static boolean contains(InstanceState state, int addr) {
        return contains(region(state.getAttributeSet()), addr);
    }

    static Value word(int value) {
        return Value.createKnown(W32, value);
    }

    static Value floating() {
        return Value.createUnknown(W32);
    }

    @Override
    protected void configureNewInstance(Instance instance) {
        Bounds b = instance.getBounds();
        instance.setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT,
                b.getX() + b.getWidth() / 2, b.getY() - 3, GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);
    }

    @Override
    public void paintIcon(InstancePainter painter) {
        Graphics g = painter.getGraphics();
        g.setColor(Color.BLACK);
        g.drawRect(2, 2, 16, 16);
        g.setFont(new Font("SansSerif", Font.BOLD, 8));
        GraphicsUtil.drawCenteredText(g, iconText(), 10, 9);
    }

    abstract String iconText();

    /** 부품 안에 보일 줄들(현재 주소 근처). 상태를 모르면 영역만 보인다. */
    abstract String[] bodyLines(InstancePainter painter);

    /** 부품 아래쪽에 빨갛게 보일 상태(떠 있는 제어 입력, 정렬 안 된 주소). 없으면 null. */
    String status(InstancePainter painter) {
        return null;
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        Graphics g = painter.getGraphics();
        Bounds b = painter.getBounds();
        int cx = b.getX() + b.getWidth() / 2;
        painter.drawBounds();
        painter.drawLabel();
        g.setColor(Color.BLACK);
        g.setFont(TITLE_FONT);
        GraphicsUtil.drawCenteredText(g, title.get(), cx, b.getY() + 10);
        g.setFont(BODY_FONT);
        String[] lines = bodyLines(painter);
        for (int i = 0; i < lines.length; i += 1) {
            GraphicsUtil.drawText(g, lines[i], cx, b.getY() + 30 + 12 * i,
                    GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);
        }
        String status = status(painter);
        if (status != null) {
            g.setColor(STATUS_COLOR);
            GraphicsUtil.drawText(g, status, cx, b.getY() + 32 + 12 * lines.length,
                    GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);
            g.setColor(Color.BLACK);
        }
        drawPorts(painter);
    }

    abstract void drawPorts(InstancePainter painter);

    static String region(InstancePainter painter) {
        long[] r = region(painter.getAttributeSet());
        return WordImage.hex(r[0]) + "-" + WordImage.hex(r[1] - 1);
    }
}
