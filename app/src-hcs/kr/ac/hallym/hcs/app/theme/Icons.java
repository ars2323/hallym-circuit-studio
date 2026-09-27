/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.theme;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.Icon;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * 아이콘(Z-12b): Hallym MIPS와 같은 Lucide SVG(ISC)를 같은 이름으로 번들하고 FlatSVGIcon으로 그린다. 새로 그리지 않는다.
 * 선 색({@code currentColor})은 부르는 쪽이 정한다(기본 navy). 도구 모음·시작 카드·빈 상태·대화상자·패널 머리 단추가 모두
 * 이것을 쓴다.
 */
public final class Icons {
    static final String DIR = "kr/ac/hallym/hcs/app/icons/lucide/";

    /** 도구 모음 이름 → Lucide 이름(도구 모음 항목과 Hallym MIPS 같은 자리의 아이콘). */
    public static final Map<String, String> TOOLBAR;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("new", "file-plus"); // Hallym MIPS 새 파일
        m.put("open", "folder-open"); // Hallym MIPS 열기
        m.put("save", "save"); // Hallym MIPS 저장
        m.put("undo", "undo-2");
        m.put("redo", "redo-2");
        m.put("select", "mouse-pointer-2");
        m.put("poke", "hand");
        m.put("wire", "spline");
        m.put("text", "type");
        m.put("pin", "square-dot");
        m.put("tunnel", "tag");
        m.put("probe", "crosshair");
        m.put("run", "play"); // Hallym MIPS Run
        m.put("pause", "pause"); // Hallym MIPS 멈춤
        m.put("cycle", "step-forward"); // Hallym MIPS Step
        m.put("cycles", "fast-forward");
        m.put("reset", "rotate-ccw"); // Hallym MIPS Reinitialize
        m.put("program", "file-down");
        m.put("flow", "waypoints");
        TOOLBAR = Collections.unmodifiableMap(m);
    }

    private Icons() {
    }

    /** Lucide 이름의 아이콘, 한 변 size px, 선 색 navy. */
    public static Icon lucide(String name, int size) {
        return lucide(name, size, Tokens.NAVY);
    }

    /** Lucide 이름의 아이콘, 한 변 size px, 선 색 color. 없는 이름이면 IllegalArgumentException. */
    public static FlatSVGIcon lucide(String name, int size, Color color) {
        FlatSVGIcon icon = new FlatSVGIcon(DIR + name + ".svg", size, size, Icons.class.getClassLoader());
        if (!icon.hasFound()) {
            throw new IllegalArgumentException("no Lucide icon " + name);
        }
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
        return icon;
    }

    /** 도구 모음 이름(new, run …)의 아이콘. */
    public static Icon toolbar(String name, int size) {
        String lucide = TOOLBAR.get(name);
        if (lucide == null) {
            throw new IllegalArgumentException("no toolbar icon " + name);
        }
        return lucide(lucide, size);
    }
}
