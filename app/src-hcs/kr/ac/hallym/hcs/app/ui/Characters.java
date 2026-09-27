/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.ui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/**
 * 하람·하리 캐릭터(Hallym University 소유, 원본 PNG 그대로). Hallym MIPS와 같은 자세 이름으로 부르고, 높이만 정해
 * 비율 그대로 줄인다(76px보다 작게는 쓰지 않는다, Hallym MIPS {@code dom.ts character}). 오류·진단 메시지에는 쓰지 않는다
 * (CLAUDE.md 8절): 시작 카드, 빈 상태, 묻는 대화상자, 튜토리얼 카드, About에만.
 */
public final class Characters {
    /** Hallym MIPS 자세 이름 → 우리 파일(assets/hallym/character). */
    static final Map<String, String> FILES = new HashMap<>();

    static {
        FILES.put("hello", "haram-hari-greeting.png");
        FILES.put("pair", "haram-hari.png");
        FILES.put("haram", "haram.png");
        FILES.put("hari", "hari.png");
        FILES.put("ok", "haram-hari-ok.png");
        FILES.put("guide", "haram-hari-guide.png");
        FILES.put("curious", "haram-hari-curious.png");
        FILES.put("congrats", "haram-hari-congrats.png");
        FILES.put("best", "haram-hari-best.png");
        FILES.put("go", "haram-hari-go.png");
        FILES.put("teach", "haram-hari-education.png");
        FILES.put("thanks", "haram-hari-thanks.png");
        FILES.put("sign", "haram-hari-sign.png");
    }

    static final String DIR = "/kr/ac/hallym/hcs/app/character/";
    public static final int MIN_HEIGHT = 76;
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    private Characters() {
    }

    /** 원본 그림. 없는 자세면 IllegalArgumentException. */
    public static synchronized BufferedImage image(String pose) {
        BufferedImage img = CACHE.get(pose);
        if (img != null) {
            return img;
        }
        String file = FILES.get(pose);
        if (file == null) {
            throw new IllegalArgumentException("no character pose " + pose);
        }
        try (InputStream in = Characters.class.getResourceAsStream(DIR + file)) {
            if (in == null) {
                throw new IllegalArgumentException("character not bundled: " + file);
            }
            img = ImageIO.read(in);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        CACHE.put(pose, img);
        return img;
    }

    /** 높이 height(px, 76 이상)로 비율 그대로 줄인 그림. */
    public static ImageIcon icon(String pose, int height) {
        BufferedImage img = image(pose);
        int h = Math.max(MIN_HEIGHT, height);
        int w = Math.round(img.getWidth() * (h / (float) img.getHeight()));
        return new ImageIcon(img.getScaledInstance(w, h, Image.SCALE_SMOOTH));
    }
}
