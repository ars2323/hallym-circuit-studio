/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.edit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;

/**
 * 배치 편집(E-01·E-02, PLAN.md 11.8): N개 복제(개수·간격·방향·라벨 자동 번호 R0…R31), 정렬(왼쪽·가운데·오른쪽·위·
 * 가운데·아래), 같은 간격. 결과는 원조 {@link CircuitMutation} 하나라 되돌리기 한 번에 취소된다. 실제로 적용하기 전에
 * 새 선·부품 검사기(WireGuard, W-05)를 거친다: 새 자리의 포트가 다른 선·포트에 닿으면 적용하지 않는다.
 * <p>
 * 정렬·같은 간격은 포트가 아무것에도 이어지지 않은 부품만 옮긴다. 부품마다 옮기는 거리가 달라 따라오는 배선
 * (SafeMove, 한 가지 거리)을 한 동작으로 보장할 수 없어서다. 배선 전에 정렬하는 것이 보통의 순서다(D-084).
 */
public final class Arrange {
    /** 복제 방향. */
    public enum Dir {
        RIGHT(1, 0), DOWN(0, 1), LEFT(-1, 0), UP(0, -1);

        final int dx;
        final int dy;

        Dir(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    /** 정렬 기준. */
    public enum Align {
        LEFT, CENTER_X, RIGHT, TOP, CENTER_Y, BOTTOM
    }

    static final int GRID = 10;
    public static final int MAX_COPIES = 64;

    private Arrange() {
    }

    static Bounds bounds(List<Component> comps) {
        Bounds b = Bounds.EMPTY_BOUNDS;
        for (Component c : comps) {
            b = b.add(c.getBounds());
        }
        return b;
    }

    static int snap(int v) {
        return Math.round(v / (float) GRID) * GRID;
    }

    /** 기본 간격: 묶음의 그 방향 크기 + 한 칸, 격자 배수. */
    public static int defaultSpacing(List<Component> comps, Dir dir) {
        Bounds b = bounds(comps);
        int size = dir.dx != 0 ? b.getWidth() : b.getHeight();
        return (int) Math.ceil((size + GRID) / (double) GRID) * GRID;
    }

    private static final Pattern NUMBERED = Pattern.compile("^(.*?)(\\d+)$");

    /**
     * k번째 사본(1부터)의 라벨. 끝이 숫자면 그 숫자에 k를 더한다(R0 → R1, R07 → R08처럼 자릿수 유지). 숫자가 없으면
     * 이름 뒤에 k. 비어 있으면 빈 채로.
     */
    public static String nextLabel(String label, int k) {
        if (label == null || label.isEmpty()) {
            return label;
        }
        Matcher m = NUMBERED.matcher(label);
        if (m.matches()) {
            String digits = m.group(2);
            long n = Long.parseLong(digits) + k;
            String s = Long.toString(n);
            while (s.length() < digits.length()) {
                s = "0" + s;
            }
            return m.group(1) + s;
        }
        return label + k;
    }

    /** 부품 c를 옮긴 새 부품(속성 복제). label이 null이 아니면 라벨을 바꾼다. */
    @SuppressWarnings("unchecked")
    static Component moved(Component c, int dx, int dy, String label) {
        AttributeSet as = (AttributeSet) c.getAttributeSet().clone();
        if (label != null && as.containsAttribute(StdAttr.LABEL)) {
            as.setValue(StdAttr.LABEL, label);
        }
        return c.getFactory().createComponent(c.getLocation().translate(dx, dy), as);
    }

    static String label(Component c) {
        Attribute<String> a = StdAttr.LABEL;
        return c.getAttributeSet().containsAttribute(a) ? c.getAttributeSet().getValue(a) : null;
    }

    /**
     * N개 복제: 부품 묶음의 사본 count개를 dir 방향으로 spacing씩 떨어뜨려 더한다. number면 라벨 끝 숫자를 이어서
     * 매긴다. 더한 사본들과 편집을 돌려준다(적용 전).
     */
    public static CircuitMutation copies(Circuit circuit, List<Component> comps, int count, Dir dir, int spacing,
            boolean number, List<Component> out) {
        CircuitMutation m = new CircuitMutation(circuit);
        int n = Math.max(1, Math.min(MAX_COPIES, count));
        int step = Math.max(GRID, snap(spacing));
        for (int k = 1; k <= n; k++) {
            for (Component c : comps) {
                String l = label(c);
                Component copy = moved(c, dir.dx * step * k, dir.dy * step * k, number ? nextLabel(l, k) : null);
                m.add(copy);
                out.add(copy);
            }
        }
        return m;
    }

    /** 포트가 무엇엔가 이어져 있는가(다른 부품의 포트나 선 끝이 같은 자리). */
    public static boolean connected(Circuit circuit, Component c) {
        for (int i = 0; i < c.getEnds().size(); i++) {
            Location at = c.getEnd(i).getLocation();
            for (Component o : circuit.getComponents(at)) {
                if (o != c) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 이어진 부품(정렬·같은 간격에서 옮기지 않는다). */
    public static List<Component> connectedOnes(Circuit circuit, List<Component> comps) {
        List<Component> ret = new ArrayList<>();
        for (Component c : comps) {
            if (connected(circuit, c)) {
                ret.add(c);
            }
        }
        return ret;
    }

    /** 정렬 편집. 옮길 것이 없으면 null. out에 새 부품(옮긴 것)을 넣는다. */
    public static CircuitMutation align(Circuit circuit, List<Component> comps, Align a, List<Component> out) {
        Bounds all = bounds(comps);
        CircuitMutation m = new CircuitMutation(circuit);
        boolean any = false;
        for (Component c : comps) {
            Bounds b = c.getBounds();
            int dx = 0;
            int dy = 0;
            switch (a) {
            case LEFT:
                dx = all.getX() - b.getX();
                break;
            case RIGHT:
                dx = all.getX() + all.getWidth() - (b.getX() + b.getWidth());
                break;
            case CENTER_X:
                dx = all.getX() + all.getWidth() / 2 - (b.getX() + b.getWidth() / 2);
                break;
            case TOP:
                dy = all.getY() - b.getY();
                break;
            case BOTTOM:
                dy = all.getY() + all.getHeight() - (b.getY() + b.getHeight());
                break;
            default:
                dy = all.getY() + all.getHeight() / 2 - (b.getY() + b.getHeight() / 2);
                break;
            }
            dx = snap(dx);
            dy = snap(dy);
            if (dx == 0 && dy == 0) {
                out.add(c);
                continue;
            }
            m.remove(c);
            Component moved = moved(c, dx, dy, null);
            m.add(moved);
            out.add(moved);
            any = true;
        }
        return any ? m : null;
    }

    /**
     * 같은 간격: 가로(또는 세로)로 늘어선 순서를 지키고 양 끝 부품은 두고, 사이 간격을 같게. 셋 미만이면 null.
     */
    public static CircuitMutation distribute(Circuit circuit, List<Component> comps, boolean horizontal,
            List<Component> out) {
        if (comps.size() < 3) {
            return null;
        }
        List<Component> sorted = new ArrayList<>(comps);
        sorted.sort(Comparator.comparingInt((Component c) -> horizontal ? c.getBounds().getX()
                : c.getBounds().getY()));
        Bounds first = sorted.get(0).getBounds();
        Bounds last = sorted.get(sorted.size() - 1).getBounds();
        int start = horizontal ? first.getX() : first.getY();
        int end = horizontal ? last.getX() + last.getWidth() : last.getY() + last.getHeight();
        int sum = 0;
        for (Component c : sorted) {
            sum += horizontal ? c.getBounds().getWidth() : c.getBounds().getHeight();
        }
        double gap = (end - start - sum) / (double) (sorted.size() - 1);
        CircuitMutation m = new CircuitMutation(circuit);
        boolean any = false;
        double at = start;
        for (int i = 0; i < sorted.size(); i++) {
            Component c = sorted.get(i);
            Bounds b = c.getBounds();
            int size = horizontal ? b.getWidth() : b.getHeight();
            int now = horizontal ? b.getX() : b.getY();
            int d = i == 0 || i == sorted.size() - 1 ? 0 : snap((int) Math.round(at) - now);
            at += size + gap;
            if (d == 0) {
                out.add(c);
                continue;
            }
            m.remove(c);
            Component moved = moved(c, horizontal ? d : 0, horizontal ? 0 : d, null);
            m.add(moved);
            out.add(moved);
            any = true;
        }
        return any ? m : null;
    }
}
