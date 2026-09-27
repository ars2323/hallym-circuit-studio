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
import com.cburch.logisim.data.Location;
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
 * <p>속성 이름은 .circ에 저장되므로 바꾸지 않는다: {@code base}, {@code size}, {@code top}, {@code stacktop},
 * {@code stacksize}, {@code contents}, {@code source}, {@code label}.
 *
 * <p>영역은 둘까지다(D-140). 데이터 영역은 {@code base}부터 위로 [base, base+size), 스택 영역은 맨 위 워드부터 아래로
 * 자란다: 옛 Stack 부품은 [top+4−size, top+4), 합친 Data Memory는 {@code stacksize}가 0이 아니면
 * [stacktop+4−stacksize, stacktop+4).
 */
abstract class MemoryFactory extends InstanceFactory {
    static final BitWidth W32 = BitWidth.create(32);
    static final int DELAY = 10; // 원조 RAM·ROM과 같다

    static final Attribute<Integer> BASE =
            Attributes.forHexInteger("base", Text.name("Start Address"));
    static final Attribute<Integer> SIZE =
            Attributes.forHexInteger("size", Text.name("Limit (bytes)"));
    /** Stack의 맨 위 워드 주소. Stack은 여기서 아래로 자란다. */
    static final Attribute<Integer> TOP =
            Attributes.forHexInteger("top", Text.name("Top Word Address"));
    /** 합친 Data Memory(D-140)의 스택 영역 맨 위 워드 주소. 스택 영역은 여기서 아래로 자란다. */
    static final Attribute<Integer> STACK_TOP =
            Attributes.forHexInteger("stacktop", Text.name("Stack Top Word Address"));
    /** 합친 Data Memory의 스택 영역 한계(바이트). 0이면 스택 영역이 없다(옛 Data Memory). */
    static final Attribute<Integer> STACK_SIZE =
            Attributes.forHexInteger("stacksize", Text.name("Stack Limit (bytes)"));
    static final WordImageAttribute CONTENTS =
            new WordImageAttribute("contents", Text.name("Initial Contents"));
    static final Attribute<String> SOURCE =
            Attributes.forString("source", Text.name("Program"));

    static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 11);
    static final Font BODY_FONT = new Font("Monospaced", Font.PLAIN, 10);
    static final Color STATUS_COLOR = new Color(0xC0392B); // Hallym MIPS error 토큰

    private final Text title;

    /**
     * @param start 위로 자라는 메모리는 시작 주소({@code base}), 아래로 자라는 Stack은 맨 위 워드({@code top})
     */
    MemoryFactory(String name, Text displayName, Text title, boolean growsDown, int start, int defaultSize) {
        this(name, displayName, title,
                new Attribute<?>[] {growsDown ? TOP : BASE, SIZE, CONTENTS, SOURCE, StdAttr.LABEL, StdAttr.LABEL_FONT},
                new Object[] {start, defaultSize, WordImage.EMPTY, "", "", StdAttr.DEFAULT_LABEL_FONT});
    }

    /** 속성과 새로 놓을 때의 값을 직접 준다(합친 Data Memory, D-140). */
    MemoryFactory(String name, Text displayName, Text title, Attribute<?>[] attrs, Object[] defaults) {
        super(name, displayName);
        this.title = title;
        setAttributes(attrs, defaults);
    }

    /** 부품 몸체 맨 위의 제목(부품 이름, 늘 영어, D-049). */
    Text title() {
        return title;
    }

    /**
     * 데이터 영역 [base, base+size)(위로 자람). 부호 없는 32비트이고 2^32를 넘으면 자른다. base 속성이 없으면(옛 Stack
     * 부품) null.
     */
    static long[] dataRegion(AttributeSet attrs) {
        if (!attrs.containsAttribute(BASE)) {
            return null;
        }
        long size = attrs.getValue(SIZE) & 0xffffffffL;
        long low = attrs.getValue(BASE) & 0xffffffffL;
        return new long[] {low, Math.min(low + size, 0x100000000L)};
    }

    /**
     * 스택 영역(아래로 자람). 옛 Stack 부품은 [top+4−size, top+4), 합친 Data Memory는 stacksize가 0이 아니면
     * [stacktop+4−stacksize, stacktop+4). 스택 영역이 없으면 null. 0 아래로 가면 자른다.
     */
    static long[] stackRegion(AttributeSet attrs) {
        if (attrs.containsAttribute(TOP)) {
            return down(attrs.getValue(TOP), attrs.getValue(SIZE));
        }
        if (attrs.containsAttribute(STACK_SIZE)) {
            int size = attrs.getValue(STACK_SIZE);
            return size == 0 ? null : down(attrs.getValue(STACK_TOP), size);
        }
        return null;
    }

    private static long[] down(int top, int size) {
        long high = (top & 0xffffffffL) + 4;
        return new long[] {Math.max(0, high - (size & 0xffffffffL)), high};
    }

    /**
     * 주 영역 {낮은 주소, 높은 주소(제외)}: 데이터 영역이 있으면 그것(Instruction Memory, Data Memory), 없으면 스택
     * 영역(옛 Stack). 실행 이미지의 구간을 담는지 볼 때 쓴다.
     */
    static long[] region(AttributeSet attrs) {
        long[] data = dataRegion(attrs);
        return data != null ? data : stackRegion(attrs);
    }

    /** 이 부품의 모든 영역(데이터, 스택 순). */
    static long[][] regions(AttributeSet attrs) {
        long[] data = dataRegion(attrs);
        long[] stack = stackRegion(attrs);
        if (data != null && stack != null) {
            return new long[][] {data, stack};
        }
        return data != null ? new long[][] {data} : stack != null ? new long[][] {stack} : new long[0][];
    }

    static boolean contains(long[] region, int addr) {
        long a = addr & 0xffffffffL;
        return region != null && a >= region[0] && a < region[1];
    }

    static boolean contains(long[][] regions, int addr) {
        for (long[] r : regions) {
            if (contains(r, addr)) {
                return true;
            }
        }
        return false;
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
        GraphicsUtil.drawCenteredText(g, title.get(), cx, b.getY() + 10); // 부품 이름은 늘 영어(D-049)
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

    /** 포트 이름을 테두리에서 이만큼 안쪽에 적는다(포트에 붙은 터널 글자와 겹치지 않게). */
    static final int PORT_INSET = 14;
    static final Font PORT_FONT = new Font("SansSerif", Font.PLAIN, 9);

    /**
     * 포트와 그 이름. 원조 {@code drawPort(i, label, dir)}는 이름을 포트 바로 옆에 붙이는데, 포트에 이어 붙인 터널
     * 글자와 겹친다. 여기서는 포트가 놓인 변에서 {@link #PORT_INSET}만큼 안쪽에 적는다.
     */
    static void drawPortInside(InstancePainter painter, int index, String label) {
        painter.drawPort(index);
        Location p = painter.getInstance().getPortLocation(index);
        Bounds b = painter.getBounds();
        Graphics g = painter.getGraphics();
        Font old = g.getFont();
        Color oldColor = g.getColor();
        g.setFont(PORT_FONT);
        g.setColor(Color.DARK_GRAY);
        if (p.getX() <= b.getX()) {
            GraphicsUtil.drawText(g, label, b.getX() + PORT_INSET, p.getY(), GraphicsUtil.H_LEFT,
                    GraphicsUtil.V_CENTER);
        } else if (p.getX() >= b.getX() + b.getWidth()) {
            GraphicsUtil.drawText(g, label, b.getX() + b.getWidth() - PORT_INSET, p.getY(), GraphicsUtil.H_RIGHT,
                    GraphicsUtil.V_CENTER);
        } else if (p.getY() >= b.getY() + b.getHeight()) {
            GraphicsUtil.drawText(g, label, p.getX(), b.getY() + b.getHeight() - PORT_INSET + 4,
                    GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);
        } else {
            GraphicsUtil.drawText(g, label, p.getX(), b.getY() + PORT_INSET, GraphicsUtil.H_CENTER,
                    GraphicsUtil.V_TOP);
        }
        g.setFont(old);
        g.setColor(oldColor);
    }

    static String region(InstancePainter painter) {
        return range(region(painter.getAttributeSet()));
    }

    /** 영역 글자: {@code 10000000-100fffff}. */
    static String range(long[] r) {
        return WordImage.hex(r[0]) + "-" + WordImage.hex(r[1] - 1);
    }
}
