/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import java.awt.Color;
import java.nio.charset.StandardCharsets;

/**
 * 터널 자동 색(#79, PLAN.md 11.12, 검토 반영 1). 저장하지 않는다. 색만으로 뜻을 전하지 않도록 터널 이름 글자는
 * 그대로 보인다.
 * <ul>
 * <li>팔레트: 색각 이상에서도 서로 구별되는 Okabe–Ito 6색과 Paul Tol "muted" 6색. 흰 바탕에서 옅어지는 노랑과
 * 모래색은 뺐다.</li>
 * <li>배정({@link #assign}): 이름마다 해시 색을 먼저 고르되, 가까이 있는(같은 화면에 함께 보일) 다른 이름이 이미 그
 * 색이면 다음 색으로 넘어간다. 같은 이름은 늘 같은 색이다.</li>
 * </ul>
 */
public final class TunnelColors {
    public static final Color[] PALETTE = {
        new Color(0xE69F00), // 주황
        new Color(0x56B4E9), // 하늘
        new Color(0x009E73), // 청록
        new Color(0x0072B2), // 파랑
        new Color(0xD55E00), // 주홍
        new Color(0xCC79A7), // 분홍
        new Color(0x332288), // 남색
        new Color(0x117733), // 초록
        new Color(0x999933), // 올리브
        new Color(0x882255), // 자주
        new Color(0x44AA99), // 옥색
        new Color(0xAA4499), // 보라
    };

    /** 이 거리(회로 좌표) 안에 터널이 있는 두 이름은 다른 색을 받는다. 200% 화면 한 장 안팎. */
    public static final int NEAR = 400;

    private TunnelColors() {
    }

    /** 이름의 팔레트 번호. 이름을 다듬지 않는다(원조 터널이 이름 그대로 연결하므로). */
    public static int index(String name) {
        // FNV-1a 32비트: JVM·실행과 무관하게 같다
        int h = 0x811C9DC5;
        for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xFF;
            h *= 0x01000193;
        }
        return Math.floorMod(h, PALETTE.length);
    }

    public static Color of(String name) {
        return PALETTE[index(name)];
    }

    /**
     * 한 회로의 이름별 색 번호. tunnels는 이름 → 그 이름 터널들의 위치, fixed는 직접 지정한 색(이름 → 팔레트 번호,
     * 팔레트 밖 색이면 -1). 이름 순으로 정하고, 해시 번호부터 시작해 가까운 이름이 쓴 번호를 건너뛴다. 모두 쓰였으면
     * 해시 번호. 결과는 입력만으로 정해진다.
     */
    public static java.util.Map<String, Integer> assign(java.util.Map<String, java.util.List<java.awt.Point>> tunnels,
            java.util.Map<String, Integer> fixed) {
        java.util.Map<String, Integer> ret = new java.util.TreeMap<>();
        java.util.List<String> names = new java.util.ArrayList<>(new java.util.TreeSet<>(tunnels.keySet()));
        for (String n : names) {
            Integer f = fixed.get(n);
            if (f != null) {
                ret.put(n, f);
            }
        }
        for (String n : names) {
            if (ret.containsKey(n)) {
                continue;
            }
            java.util.Set<Integer> used = new java.util.HashSet<>();
            for (java.util.Map.Entry<String, Integer> e : ret.entrySet()) {
                if (near(tunnels.get(n), tunnels.get(e.getKey()))) {
                    used.add(e.getValue());
                }
            }
            int start = index(n);
            int pick = start;
            for (int k = 0; k < PALETTE.length; k++) {
                int c = (start + k) % PALETTE.length;
                if (!used.contains(c)) {
                    pick = c;
                    break;
                }
            }
            ret.put(n, pick);
        }
        return ret;
    }

    static boolean near(java.util.List<java.awt.Point> a, java.util.List<java.awt.Point> b) {
        if (a == null || b == null) {
            return false;
        }
        for (java.awt.Point p : a) {
            for (java.awt.Point q : b) {
                if (Math.abs(p.x - q.x) <= NEAR && Math.abs(p.y - q.y) <= NEAR) {
                    return true;
                }
            }
        }
        return false;
    }
}
