/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.ui;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

/**
 * 줄바꿈(Z-17 keep-all): 한국어 문장을 어절 사이(공백)에서만 나눈다. Swing은 한글을 어절 안에서 끊을 수 있어 카드·대화상자·
 * 빈 상태·Messages·상태 표시줄·툴팁·About·요약 창이 모두 이것을 쓴다. 한 어절이 폭보다 길 때만 그 어절 안에서 나눈다
 * (Hallym MIPS CSS {@code word-break: keep-all}과 같은 규칙). 줄바꿈 문자는 늘 줄을 나눈다. GUI 없이 계산만 한다.
 */
public final class Wrap {
    private Wrap() {
    }

    /** text를 width(px) 안에 들도록 나눈 줄들. width가 0 이하면 줄바꿈 문자에서만 나눈다. */
    public static List<String> lines(String text, FontMetrics fm, int width) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String para : text.split("\n", -1)) {
            if (width <= 0) {
                out.add(para);
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ", -1)) {
                if (word.isEmpty() && line.length() == 0) {
                    continue; // 앞 공백
                }
                String tryLine = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(tryLine) <= width) {
                    line.setLength(0);
                    line.append(tryLine);
                    continue;
                }
                if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                // 이 어절만으로도 넘치면 어절 안에서 나눈다(그런 때만)
                String rest = word;
                while (fm.stringWidth(rest) > width && rest.length() > 1) {
                    int cut = fit(rest, fm, width);
                    out.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                line.append(rest);
            }
            out.add(line.toString());
        }
        return out;
    }

    /** 폭 안에 드는 가장 긴 앞부분의 길이(적어도 1). 서로게이트 쌍은 나누지 않는다. */
    static int fit(String s, FontMetrics fm, int width) {
        int n = 1;
        while (n < s.length()) {
            int next = Character.isHighSurrogate(s.charAt(n - 1)) ? n + 1 : n;
            int step = next < s.length() && Character.isHighSurrogate(s.charAt(next)) ? 2 : 1;
            if (fm.stringWidth(s.substring(0, next + step)) > width) {
                return next;
            }
            n = next + step;
        }
        return s.length();
    }

    /** 이 줄들이 keep-all을 지키는가(테스트): 줄 경계가 원문의 공백·줄바꿈 자리이거나, 폭보다 긴 한 어절 안이다. */
    public static boolean breaksOnlyBetweenWords(String text, List<String> lines, FontMetrics fm, int width) {
        int pos = 0;
        for (int i = 0; i < lines.size() - 1; i++) {
            pos = text.indexOf(lines.get(i), pos) + lines.get(i).length();
            if (pos >= text.length()) {
                return true;
            }
            char at = text.charAt(pos);
            if (at == ' ' || at == '\n') {
                continue;
            }
            int start = text.lastIndexOf(' ', pos - 1) + 1;
            int end = text.indexOf(' ', pos);
            String word = text.substring(start, end < 0 ? text.length() : end);
            if (fm.stringWidth(word) <= width) {
                return false; // 폭 안에 드는 어절을 나눴다
            }
        }
        return true;
    }
}
