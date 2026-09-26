/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 라벨 칩 배치(#79, PLAN.md 11.12). 칩은 원래 라벨 자리(anchor) 가운데에 두고, 다른 칩이나 부품과 겹치면 가까운 빈
 * 방향(위·아래·오른쪽·왼쪽, 그다음 대각선)으로 한 걸음씩 옮긴다. 멀리 옮긴 칩은 원래 자리와 지시선으로 잇는다.
 * 순서와 결과는 입력만으로 정해진다. 좌표는 회로 좌표(px)이고 GUI 없이 테스트한다.
 */
public final class LabelLayout {
    /** 배치 요청: 원래 라벨 자리와 칩 크기. 우선순위가 작을수록 먼저(좋은 자리를) 잡는다. */
    public static final class Req {
        final Object key;
        final Rectangle anchor;
        final int width;
        final int height;
        final int priority;
        /** 버스 칩: 이 선에 지시선으로 매인다(지시선이 다른 선·부품을 가로지르는 자리는 쓰지 않는다, C-08 검토). */
        final java.awt.geom.Line2D tether;

        public Req(Object key, Rectangle anchor, int width, int height, int priority) {
            this(key, anchor, width, height, priority, null);
        }

        public Req(Object key, Rectangle anchor, int width, int height, int priority, java.awt.geom.Line2D tether) {
            this.key = key;
            this.anchor = new Rectangle(anchor);
            this.width = width;
            this.height = height;
            this.priority = priority;
            this.tether = tether;
        }
    }

    /** 배치 결과. */
    public static final class Placed {
        public final Object key;
        public final Rectangle rect;
        /** 원래 자리 가운데(지시선의 시작). */
        public final Point anchor;
        /** 멀리 옮겨 지시선을 그린다. */
        public final boolean leader;
        /** 빈 자리를 못 찾아 원래 자리에 겹쳐 둔다. */
        public final boolean overlapped;

        Placed(Object key, Rectangle rect, Point anchor, boolean leader, boolean overlapped) {
            this.key = key;
            this.rect = rect;
            this.anchor = anchor;
            this.leader = leader;
            this.overlapped = overlapped;
        }
    }

    /** 옮기는 방향: 위, 아래, 오른쪽, 왼쪽, 그다음 대각선. */
    private static final int[][] DIRS = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}, {1, -1}, {-1, -1}, {1, 1}, {-1, 1}};
    /** 칩 사이 여백. */
    static final int GAP = 2;

    private LabelLayout() {
    }

    /**
     * 배치. obstacles는 부품 몸체(칩이 덮으면 안 되는 곳), step은 한 걸음(px), maxSteps는 최대 걸음 수. 빈 자리가
     * 없으면 원래 자리에 두고 overlapped로 표시한다.
     */
    public static List<Placed> layout(List<Req> reqs, List<Rectangle> obstacles, int step, int maxSteps) {
        List<Req> order = new ArrayList<>(reqs);
        order.sort(Comparator.comparingInt((Req r) -> r.priority).thenComparingInt(r -> r.anchor.y)
                .thenComparingInt(r -> r.anchor.x));
        List<Rectangle> taken = new ArrayList<>();
        List<Placed> ret = new ArrayList<>();
        for (Req r : order) {
            Point c = new Point(r.anchor.x + r.anchor.width / 2, r.anchor.y + r.anchor.height / 2);
            Rectangle home = new Rectangle(c.x - r.width / 2, c.y - r.height / 2, r.width, r.height);
            Rectangle found = null;
            int foundStep = 0;
            if (free(home, taken, obstacles) && clearLeader(r.tether, home, obstacles)) {
                found = home;
            }
            for (int s = 1; found == null && s <= maxSteps; s++) {
                for (int[] d : DIRS) {
                    Rectangle cand = new Rectangle(home);
                    cand.translate(d[0] * s * step, d[1] * s * step);
                    if (free(cand, taken, obstacles) && clearLeader(r.tether, cand, obstacles)) {
                        found = cand;
                        foundStep = s;
                        break;
                    }
                }
            }
            boolean overlapped = found == null;
            if (overlapped) {
                found = home;
            }
            taken.add(found);
            boolean leader = foundStep * step > Math.max(r.height, 12);
            ret.add(new Placed(r.key, found, c, leader, overlapped));
        }
        return ret;
    }

    /** 선 tether 위 가장 가까운 점에서 칩 r까지의 지시선이 다른 선·부품(장애물)을 가로지르지 않는가. */
    static boolean clearLeader(java.awt.geom.Line2D tether, Rectangle r, List<Rectangle> obstacles) {
        if (tether == null) {
            return true;
        }
        double cx = r.getCenterX();
        double cy = r.getCenterY();
        double x0 = Math.min(tether.getX1(), tether.getX2());
        double x1 = Math.max(tether.getX1(), tether.getX2());
        double y0 = Math.min(tether.getY1(), tether.getY2());
        double y1 = Math.max(tether.getY1(), tether.getY2());
        double sx = Math.max(x0, Math.min(cx, x1));
        double sy = Math.max(y0, Math.min(cy, y1));
        double ex = Math.max(r.x, Math.min(sx, r.x + r.width));
        double ey = Math.max(r.y, Math.min(sy, r.y + r.height));
        java.awt.geom.Line2D lead = new java.awt.geom.Line2D.Double(sx, sy, ex, ey);
        for (Rectangle o : obstacles) {
            if (o.contains(sx, sy) || o.intersects(r)) {
                continue; // 제 선(지시선의 시작)과, 칩이 이미 덮는 것은 따로 본다
            }
            if (o.intersectsLine(lead)) {
                return false;
            }
        }
        return true;
    }

    static boolean free(Rectangle r, List<Rectangle> taken, List<Rectangle> obstacles) {
        Rectangle grown = new Rectangle(r.x - GAP, r.y - GAP, r.width + 2 * GAP, r.height + 2 * GAP);
        for (Rectangle t : taken) {
            if (grown.intersects(t)) {
                return false;
            }
        }
        for (Rectangle o : obstacles) {
            if (r.intersects(o)) {
                return false;
            }
        }
        return true;
    }
}
