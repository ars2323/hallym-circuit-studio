/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Iterator;
import java.util.Map;

import com.cburch.logisim.comp.Component;

/**
 * 원조 라벨 글자만 빼는 Graphics(#79, D-040). 원조 {@code Circuit.draw}는 선을 그릴 Graphics를 하나 만든 뒤 부품마다
 * (가려진 부품은 건너뛰고) {@code getNonWires()} 순서대로 {@code create()}를 한 번씩 부른다. 맨 위 Graphics는 그
 * 순서를 따라 자식마다 그 부품을 붙이고, 자식(과 그 자식)은 <b>그 부품 자신의</b> 라벨 글자·기준선 좌표와 정확히
 * 같은 {@code drawString}만 뺀다. 그래서 같은 글자의 다른 라벨·터널·글자 부품이 같은 자리에 있어도 사라지지
 * 않는다. 순서가 어긋나면 라벨이 원조대로 남을 뿐 다른 글자를 빼지 않는다. 엔진 코드는 그대로다.
 */
final class FilterGraphics extends DelegatingGraphics {
    /** 맨 위일 때: 원조가 create()를 부를 순서(첫째는 선, null). 자식이면 null. */
    private final Iterator<Component> order;
    /** 맨 위일 때: 부품 → 그 부품 라벨의 열쇠. */
    private final Map<Component, String> keys;
    /** 자식일 때: 이 부품 라벨의 열쇠(없으면 null). */
    private final String skip;

    /** 맨 위 Graphics. order는 원조가 create()를 부를 순서다. */
    FilterGraphics(Graphics2D g, Iterator<Component> order, Map<Component, String> keys) {
        super(g);
        this.order = order;
        this.keys = keys;
        this.skip = null;
    }

    private FilterGraphics(Graphics2D g, String skip) {
        super(g);
        this.order = null;
        this.keys = null;
        this.skip = skip;
    }

    /** 뺄 호출의 열쇠: 글자와 기준선 좌표. */
    static String key(String text, int x, int y) {
        return text + '\u0000' + x + ',' + y;
    }

    @Override
    public void drawString(String str, int x, int y) {
        if (skip == null || !skip.equals(key(str, x, y))) {
            g.drawString(str, x, y);
        }
    }

    @Override
    public Graphics create() {
        Graphics2D child = (Graphics2D) g.create();
        if (order == null) {
            return new FilterGraphics(child, skip);
        }
        Component c = order.hasNext() ? order.next() : null;
        return new FilterGraphics(child, c == null ? null : keys.get(c));
    }
}
