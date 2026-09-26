/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.memo;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.ext.CircExtension;
import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.labels.TunnelColors;

/**
 * 영역 메모(E-08, PLAN.md 11.14): IF/ID/EX… 같은 영역을 감싸는 색 상자와 메모 글. 학생이 직접 둔 정보라 .circ 확장
 * 정보(PLAN.md 7.0)에 회로마다 {@code memo x= y= w= h= color= text=}로 저장하고 원조 2.7.1은 건너뛴다. 부품·선과는
 * 무관한 그림이라 시뮬레이션·저장 형식에 영향이 없다. 색은 터널 색 팔레트({@link TunnelColors#PALETTE})의 번호다.
 */
public final class AreaMemos {
    static final String KIND = "memo";
    /** 기본 상자 크기(회로 좌표). */
    public static final int DEFAULT_W = 200;
    public static final int DEFAULT_H = 120;
    /** 고른 부품 둘레 여백. */
    public static final int MARGIN = 20;

    /** 메모 하나(값 객체). */
    public static final class Memo {
        public final Bounds bounds;
        public final int color;
        public final String text;

        public Memo(Bounds bounds, int color, String text) {
            this.bounds = bounds;
            this.color = Math.floorMod(color, TunnelColors.PALETTE.length);
            this.text = text == null ? "" : text;
        }

        public Color color() {
            return TunnelColors.PALETTE[color];
        }

        public boolean contains(Location p) {
            return bounds.contains(p);
        }

        CircExtension.Item toItem() {
            Map<String, String> a = new LinkedHashMap<>();
            a.put("x", Integer.toString(bounds.getX()));
            a.put("y", Integer.toString(bounds.getY()));
            a.put("w", Integer.toString(bounds.getWidth()));
            a.put("h", Integer.toString(bounds.getHeight()));
            a.put("color", Integer.toString(color));
            a.put("text", text);
            return new CircExtension.Item(KIND, a);
        }

        static Memo of(CircExtension.Item item) {
            try {
                return new Memo(Bounds.create(Integer.parseInt(item.get("x")), Integer.parseInt(item.get("y")),
                        Integer.parseInt(item.get("w")), Integer.parseInt(item.get("h"))),
                        Integer.parseInt(item.get("color")), item.get("text"));
            } catch (RuntimeException e) {
                return null;
            }
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Memo && ((Memo) o).bounds.equals(bounds) && ((Memo) o).color == color
                    && ((Memo) o).text.equals(text);
        }

        @Override
        public int hashCode() {
            return bounds.hashCode() * 31 + color * 7 + text.hashCode();
        }

        @Override
        public String toString() {
            return "Memo[" + bounds + " " + color + " " + text + "]";
        }
    }

    private AreaMemos() {
    }

    /** 회로의 메모(파일 차례). */
    public static List<Memo> of(LogisimFile file, Circuit circuit) {
        List<Memo> out = new ArrayList<>();
        if (file == null || circuit == null) {
            return out;
        }
        for (CircExtension.Item item : CircExtensions.of(file).items(circuit.getName())) {
            if (item.kind().equals(KIND)) {
                Memo m = Memo.of(item);
                if (m != null) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    /** 점 p를 감싸는 메모 중 가장 작은 것(없으면 null). 겹칠 때 안쪽 것을 고른다. */
    public static Memo at(LogisimFile file, Circuit circuit, Location p) {
        Memo best = null;
        for (Memo m : of(file, circuit)) {
            if (m.contains(p) && (best == null
                    || m.bounds.getWidth() * m.bounds.getHeight() < best.bounds.getWidth() * best.bounds.getHeight())) {
                best = m;
            }
        }
        return best;
    }

    /** 고른 부품을 여백을 두고 감싸는 상자(격자에 맞춤). 비었으면 점 p에 기본 크기. */
    public static Bounds around(Collection<Component> comps, Location p) {
        Bounds b = Bounds.EMPTY_BOUNDS;
        for (Component c : comps) {
            b = b.add(c.getBounds());
        }
        if (b.getWidth() <= 0 || b.getHeight() <= 0) {
            return Bounds.create(snap(p.getX() - DEFAULT_W / 2), snap(p.getY() - DEFAULT_H / 2), DEFAULT_W, DEFAULT_H);
        }
        b = b.expand(MARGIN);
        int x = snap(b.getX());
        int y = snap(b.getY());
        int x1 = snapUp(b.getX() + b.getWidth());
        int y1 = snapUp(b.getY() + b.getHeight());
        return Bounds.create(x, y, x1 - x, y1 - y);
    }

    static int snap(int v) {
        return Math.floorDiv(v, 10) * 10;
    }

    static int snapUp(int v) {
        return -Math.floorDiv(-v, 10) * 10;
    }

    /** before를 after로 바꾼다(자리 유지). before가 null이면 끝에 더하고, after가 null이면 지운다. */
    static void replace(LogisimFile file, Circuit circuit, Memo before, Memo after) {
        CircExtension ext = CircExtensions.of(file);
        List<CircExtension.Item> memos = new ArrayList<>();
        for (CircExtension.Item item : ext.items(circuit.getName())) {
            if (item.kind().equals(KIND)) {
                memos.add(item);
            }
        }
        List<Memo> order = new ArrayList<>();
        boolean replaced = false;
        for (CircExtension.Item item : memos) {
            Memo m = Memo.of(item);
            if (!replaced && before != null && before.equals(m)) {
                replaced = true;
                if (after != null) {
                    order.add(after);
                }
            } else if (m != null) {
                order.add(m);
            }
        }
        if (!replaced && after != null) {
            order.add(after);
        }
        for (CircExtension.Item item : memos) {
            ext.remove(circuit.getName(), item);
        }
        for (Memo m : order) {
            ext.add(circuit.getName(), m.toItem());
        }
    }

    /** 더하기(before=null), 고치기, 지우기(after=null)를 한 동작으로(되돌리기 한 번). */
    public static Action action(LogisimFile file, Circuit circuit, Memo before, Memo after) {
        return new Action() {
            @Override
            public String getName() {
                return Messages.get(before == null ? "memo.addAction" : after == null ? "memo.deleteAction"
                        : "memo.editAction");
            }

            @Override
            public void doIt(Project proj) {
                replace(file, circuit, before, after);
                proj.repaintCanvas();
            }

            @Override
            public void undo(Project proj) {
                replace(file, circuit, after, before);
                proj.repaintCanvas();
            }
        };
    }
}
