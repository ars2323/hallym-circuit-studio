/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.diag;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.instance.StdAttr;

import kr.ac.hallym.hcs.app.model.Names;

/**
 * 가까운 이름(D-143, v2 새 규칙): 짝 없는 터널의 이름과 가까운 다른 터널 이름이 <b>하나뿐일 때만</b> 짚는다("혹시
 * RegWrite?"). 확신할 때만 말한다(v2 지시 7절): 후보가 둘이면 어느 쪽인지 모르므로 말하지 않는다.
 * <ul>
 * <li>후보: 같은 회로의 다른 터널 이름(터널은 회로 안에서만 이어진다). 같은 비트 폭의 터널이 적어도 하나 있어야 한다.</li>
 * <li>가깝다: 대소문자만 다르거나, 두 이름이 모두 4글자 이상이고 숫자만 다른 것이 아니며(ALUOp0·ALUOp1, r1·r2는
 * 번호로 가른 다른 신호다) 편집 거리(넣기·빼기·바꾸기·붙은 두 글자 맞바꾸기, 대소문자 무시)가 짧은 이름 7글자까지 1,
 * 8글자부터 2 이하.</li>
 * <li>3글자 이하(rs·rt·rd, pc)는 대소문자만 다를 때만 가깝다: 한 글자 차이가 흔한 다른 신호다.</li>
 * </ul>
 * v1은 편집 거리 2 안의 이름을 모두 늘어놓았다(폭을 보지 않았다). 그 문구는 v1 화면에 그대로 남는다.
 */
public final class NearNames {
    private NearNames() {
    }

    /** 짝 없는 터널 tunnel(회로 c 안)의 가까운 이름 하나. 없거나 둘 이상이면 null. */
    public static String forTunnel(Circuit c, Component tunnel) {
        String label = Names.label(tunnel);
        if (label == null) {
            return null;
        }
        int width = width(tunnel);
        Map<String, Set<Integer>> widths = new LinkedHashMap<>();
        for (Component x : c.getNonWires()) {
            if (x != tunnel && x.getFactory().getName().equals("Tunnel")) {
                String l = Names.label(x);
                if (l != null && !l.equals(label)) {
                    widths.computeIfAbsent(l, k -> new LinkedHashSet<>()).add(width(x));
                }
            }
        }
        String found = null;
        for (Map.Entry<String, Set<Integer>> e : widths.entrySet()) {
            if (e.getValue().contains(width) && near(label, e.getKey())) {
                if (found != null) {
                    return null; // 둘 이상: 확신할 수 없다
                }
                found = e.getKey();
            }
        }
        return found;
    }

    static int width(Component c) {
        Object w = c.getAttributeSet().getValue(StdAttr.WIDTH);
        return w instanceof BitWidth ? ((BitWidth) w).getWidth() : 1;
    }

    /** 두 이름이 가까운가(위 규칙). 같은 이름은 가깝지 않다(다른 이름만 짚는다). */
    public static boolean near(String a, String b) {
        if (a.equals(b)) {
            return false;
        }
        String x = a.toLowerCase(java.util.Locale.ROOT);
        String y = b.toLowerCase(java.util.Locale.ROOT);
        if (x.equals(y)) {
            return true;
        }
        int shorter = Math.min(x.length(), y.length());
        if (shorter < 4) {
            return false;
        }
        if (withoutDigits(x).equals(withoutDigits(y))) {
            return false; // 번호만 다르다: 다른 신호
        }
        return distance(x, y) <= (shorter >= 8 ? 2 : 1);
    }

    static String withoutDigits(String s) {
        return s.replaceAll("[0-9]", "");
    }

    /** 편집 거리(optimal string alignment): 넣기·빼기·바꾸기와 붙은 두 글자 맞바꾸기가 한 번씩. */
    static int distance(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    v = Math.min(v, d[i - 2][j - 2] + 1);
                }
                d[i][j] = v;
            }
        }
        return d[n][m];
    }
}
