/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.io.InputStream;
import java.util.Map;

import javax.swing.UIManager;

import org.junit.jupiter.api.Test;

import com.formdev.flatlaf.FlatLightLaf;

/** #21: FlatLaf + 디자인 토큰 + Pretendard. */
class ThemeTest {
    /** 글자와 그 바탕 쌍이 WCAG AA(4.5:1)를 넘는다(Hallym MIPS tokens.md 1.4와 같은 쌍). */
    @Test
    void textPairsMeetAa() {
        Object[][] pairs = {
            {Tokens.TEXT, Tokens.WHITE}, {Tokens.TEXT, Tokens.WINDOW}, {Tokens.TEXT_2, Tokens.WHITE},
            {Tokens.TEXT_2, Tokens.WINDOW}, {Tokens.TEXT_MUTED, Tokens.WHITE}, {Tokens.NAVY, Tokens.BLUE_TINT_2},
            {Tokens.NAVY, Tokens.BLUE_TINT}, {Tokens.BLUE, Tokens.WHITE}, {Tokens.WHITE, Tokens.BLUE},
            {Tokens.WHITE, Tokens.NAVY}, {Tokens.TEAL_TEXT, Tokens.TEAL_TINT}, {Tokens.AMBER_TEXT, Tokens.AMBER_TINT},
            {Tokens.ERROR_TEXT, Tokens.ERROR_TINT}, {Tokens.ERROR, Tokens.WHITE}, {Tokens.NAVY, Tokens.WHITE},
        };
        for (Object[] p : pairs) {
            double c = Tokens.contrast((Color) p[0], (Color) p[1]);
            assertTrue(c >= 4.5, Tokens.hex((Color) p[0]) + " on " + Tokens.hex((Color) p[1]) + " = " + c);
        }
        assertTrue(Tokens.contrast(Tokens.TEAL, Tokens.WHITE) < 3, "teal is never text (2.91)");
    }

    @Test
    void valuesMatchHallymMips() {
        assertEquals("#00205B", Tokens.hex(Tokens.NAVY));
        assertEquals("#0055A5", Tokens.hex(Tokens.BLUE));
        assertEquals("#00A9A5", Tokens.hex(Tokens.TEAL));
        assertEquals("#5A6472", Tokens.hex(Tokens.TEXT_2)); // tokens.md의 옛 값 #5B6B7B가 아니라 코드 값
        assertEquals("#65707E", Tokens.hex(Tokens.TEXT_MUTED));
        assertEquals("#00736F", Tokens.hex(Tokens.TEAL_TEXT));
    }

    @Test
    void pretendardIsBundledAndRegisters() throws Exception {
        for (String name : Theme.PRETENDARD) {
            try (InputStream in = Theme.class.getResourceAsStream(Theme.FONT_DIR + name)) {
                assertNotNull(in, name);
            }
        }
        assertNotNull(Theme.class.getResourceAsStream(Theme.FONT_DIR + "LICENSE.txt"), "OFL text ships with the font");
        assertEquals("Pretendard", Theme.registerFonts());
        Font f = new Font("Pretendard", Font.PLAIN, 13);
        assertEquals("Pretendard", f.getFamily());
        assertTrue(f.canDisplayUpTo("회로 저장 Circuit") < 0, "Hangul and Latin glyphs");
    }

    @Test
    void flatLafGetsTokens() throws Exception {
        Map<String, String> d = Theme.defaults();
        assertEquals("#0055A5", d.get("@accentColor"));
        assertEquals("#D3E2F3", d.get("@selectionBackground"));
        assertEquals("#00205B", d.get("@selectionForeground"));
        Theme.install();
        assertTrue(UIManager.getLookAndFeel() instanceof FlatLightLaf);
        assertEquals(Tokens.BLUE, new Color(UIManager.getColor("Component.accentColor").getRGB()));
        assertEquals(Tokens.BLUE_TINT_2, new Color(UIManager.getColor("Table.selectionBackground").getRGB()));
        assertEquals(Tokens.NAVY, new Color(UIManager.getColor("Table.selectionForeground").getRGB()));
        assertEquals(Tokens.NAVY, new Color(UIManager.getColor("ToolTip.background").getRGB()));
        assertEquals(Tokens.BLUE, new Color(UIManager.getColor("Button.default.background").getRGB()));
        assertEquals(12, UIManager.getInt("ScrollBar.width"));
        Font font = UIManager.getFont("defaultFont");
        assertEquals("Pretendard", font.getFamily());
        assertEquals(13, font.getSize());
        assertEquals("Pretendard", UIManager.getFont("Label.font").getFamily());
    }

    /** D-049: Swing이 그리는 버튼 이름도 한국어 OS에서 영어다(스크린샷 13의 "확인"). */
    @Test
    void swingButtonNamesAreEnglish() {
        java.util.Locale os = java.util.Locale.getDefault();
        java.util.Locale before = javax.swing.JComponent.getDefaultLocale();
        try {
            java.util.Locale.setDefault(java.util.Locale.KOREA);
            javax.swing.JComponent.setDefaultLocale(java.util.Locale.KOREA);
            Theme.swingNamesInEnglish();
            java.util.Locale l = new javax.swing.JOptionPane().getLocale();
            org.junit.jupiter.api.Assertions.assertEquals("OK", javax.swing.UIManager.getString("OptionPane.okButtonText", l));
            org.junit.jupiter.api.Assertions.assertEquals("Cancel",
                    javax.swing.UIManager.getString("OptionPane.cancelButtonText", l));
        } finally {
            java.util.Locale.setDefault(os);
            javax.swing.JComponent.setDefaultLocale(before);
        }
    }
}
