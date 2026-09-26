/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.about;

import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.ImageIcon;

import com.cburch.logisim.circuit.Circuit;

import kr.ac.hallym.hcs.app.model.Names;

/**
 * 앱 정체(E-11·E-12): 이름, 버전, 창 제목, 앱 아이콘, 라이선스·고지 원문. 아이콘과 엠블럼은 학교가 배포한 원본에서
 * 원형 그대로 만든 PNG다(assets/hallym/logo, 다시 그리기·색 변경 없음, CLAUDE.md 8절). Hallym MIPS 선례대로
 * 48px 이상은 원형 엠블럼, 그 아래는 심벌이다.
 */
public final class AppIdentity {
    public static final String NAME = "Hallym Circuit Studio";
    static final String LOGO = "/kr/ac/hallym/hcs/app/logo/";
    static final String ABOUT = "/kr/ac/hallym/hcs/app/about/";
    static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 256};

    private static List<Image> icons;

    private AppIdentity() {
    }

    /** 버전(jar의 Implementation-Version). 개발 중(jar 밖)이면 "dev". */
    public static String version() {
        Package p = AppIdentity.class.getPackage();
        String v = p == null ? null : p.getImplementationVersion();
        return v == null || v.isEmpty() ? "dev" : v;
    }

    /**
     * 주 창 제목: 파일 이름 — 앱 이름. 보고 있는 회로가 맨 위 회로가 아니면 {@code 파일 › 회로}. 원조의
     * "Logisim: main of 파일" 대신.
     */
    public static String title(String fileName, Circuit current, Circuit main) {
        String where = current == null || current == main ? fileName : Names.path(fileName, current.getName());
        return where + " — " + NAME;
    }

    /** 창 아이콘들(작은 것부터). 찾지 못하면 빈 목록(원조 아이콘을 쓴다). */
    public static synchronized List<Image> icons() {
        if (icons == null) {
            List<Image> out = new ArrayList<>();
            for (int size : ICON_SIZES) {
                URL url = AppIdentity.class.getResource(LOGO + "app-" + size + ".png");
                if (url != null) {
                    out.add(new ImageIcon(url).getImage());
                }
            }
            icons = Collections.unmodifiableList(out);
        }
        return icons;
    }

    /** 번들된 글 파일(LICENSE, NOTICE). 없으면 빈 글. */
    public static String text(String name) {
        try (InputStream in = AppIdentity.class.getResourceAsStream(ABOUT + name)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    static URL logo(String file) {
        return AppIdentity.class.getResource(LOGO + file);
    }
}
