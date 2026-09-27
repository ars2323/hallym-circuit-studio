/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Canvas;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.List;

import org.junit.jupiter.api.Test;

import kr.ac.hallym.hcs.app.theme.Theme;

/** Z-17 keep-all: 한국어 문장은 어절 사이에서만 줄이 바뀐다. 여러 폭에서 어절 중간 줄바꿈이 0건인지 본다. */
class WrapTest {
    static final String[] SENTENCES = {
        "검색 칸(Ctrl+K)에 and를 치고 AND Gate를 고르세요. 고르면 다음 단계로 넘어갑니다.",
        "원본 파일 lab04.s: 내보낸 뒤 바뀜. Hallym MIPS에서 다시 내보내세요.",
        "12번째 줄: .text words 14인데 워드가 13개입니다. 파일이 잘렸을 수 있습니다.",
        "예제는 내려가고 튜토리얼을 시작하기 전의 화면으로 돌아갑니다.",
        "Hallym University 로고와 캐릭터 하람·하리는 Hallym University 소유이며 상업적으로 쓸 수 없습니다.",
    };

    static FontMetrics metrics(Font f) {
        return new Canvas().getFontMetrics(f);
    }

    @Test
    void koreanBreaksOnlyBetweenWordsAtManyWidths() {
        Theme.registerFonts();
        FontMetrics fm = metrics(Theme.uiFont(400, 13));
        int checked = 0;
        for (String s : SENTENCES) {
            for (int w = 60; w <= 640; w += 7) {
                List<String> lines = Wrap.lines(s, fm, w);
                assertTrue(Wrap.breaksOnlyBetweenWords(s, lines, fm, w), w + "px: " + lines);
                for (String l : lines) {
                    // 한 어절이 폭보다 길 때만 넘친다
                    assertTrue(fm.stringWidth(l) <= w || !l.contains(" "), w + "px line too wide: " + l);
                }
                assertEquals(s.replace(" ", ""), String.join("", lines).replace(" ", ""), "no characters lost");
                checked++;
            }
        }
        assertTrue(checked > 300, "widths × sentences checked: " + checked);
    }

    @Test
    void newlinesAlwaysBreakAndLongWordsSplitOnlyWhenTheyMust() {
        FontMetrics fm = metrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        assertEquals(List.of("가나", "다라"), Wrap.lines("가나\n다라", fm, 500));
        List<String> lines = Wrap.lines("0x0040002400400024004000240040", fm, 60);
        assertTrue(lines.size() > 1, "a word wider than the line is split: " + lines);
        assertEquals("0x0040002400400024004000240040", String.join("", lines));
    }
}
