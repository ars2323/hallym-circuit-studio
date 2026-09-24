/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import java.awt.Color;
import java.nio.charset.StandardCharsets;

/**
 * 터널 자동 색(#79, PLAN.md 11.12). 이름의 해시로 팔레트에서 고르므로 같은 이름은 어느 회로·어느 실행에서나 같은
 * 색이다. 저장하지 않는다. 팔레트는 색각 이상에서도 구별되는 Okabe–Ito 색(노랑은 흰 바탕에서 옅어 뺌)이다.
 * 색만으로 뜻을 전하지 않도록 터널 이름 글자는 그대로 보인다.
 */
public final class TunnelColors {
    public static final Color[] PALETTE = {
        new Color(0xE69F00), // 주황
        new Color(0x56B4E9), // 하늘
        new Color(0x009E73), // 청록
        new Color(0x0072B2), // 파랑
        new Color(0xD55E00), // 주홍
        new Color(0xCC79A7), // 자주
        new Color(0x7F7F7F), // 회색
    };

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
}
