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
        Theme.install();
        assertTrue(UIManager.getLookAndFeel() instanceof FlatLightLaf);
        assertEquals(Tokens.BLUE, new Color(UIManager.getColor("Component.accentColor").getRGB()));
        assertEquals(Tokens.BLUE_TINT_2, new Color(UIManager.getColor("Table.selectionBackground").getRGB()));
        assertEquals(Tokens.NAVY, new Color(UIManager.getColor("Table.selectionForeground").getRGB()));
        assertEquals(Tokens.NAVY, new Color(UIManager.getColor("ToolTip.background").getRGB()));
        assertEquals(Tokens.BLUE, new Color(UIManager.getColor("Button.default.background").getRGB()));
        assertEquals(Tokens.SCROLLBAR, UIManager.getInt("ScrollBar.width"));
        // Z-12b: Hallym MIPS app.css 치수(arc는 지름)
        assertEquals(2 * Tokens.RADIUS, UIManager.getInt("Button.arc"));
        assertEquals(2 * Tokens.RADIUS, UIManager.getInt("Component.arc"));
        assertEquals(Tokens.ROW, UIManager.getInt("Table.rowHeight"));
        assertEquals(Tokens.ROW, UIManager.getInt("Tree.rowHeight"));
        assertEquals(Tokens.HEAD, UIManager.getInt("TabbedPane.tabHeight"));
        assertEquals(Tokens.TAB_UNDERLINE, UIManager.getInt("TabbedPane.tabSelectionHeight"));
        assertEquals(Tokens.NAVY, new Color(UIManager.getColor("TabbedPane.selectedForeground").getRGB()));
        assertEquals(Tokens.TEXT_MUTED, new Color(UIManager.getColor("TabbedPane.foreground").getRGB()));
        Font font = UIManager.getFont("defaultFont");
        assertEquals("Pretendard", font.getFamily());
        assertEquals(13, font.getSize());
        assertEquals("Pretendard", UIManager.getFont("Label.font").getFamily());
        // .btn 높이 28px(한 줄 글자 단추)
        javax.swing.JButton b = new javax.swing.JButton("Run");
        assertEquals(Tokens.BUTTON_HEIGHT, b.getPreferredSize().height, "button height");
    }

    /** Z-12b: FlatLightLaf.properties의 색 변수는 Tokens의 같은 이름 값과 같다(값을 두 곳에서 따로 고치지 않게). */
    @Test
    void propertiesVariablesEqualTokens() throws Exception {
        java.util.Properties p = new java.util.Properties();
        try (InputStream in = Theme.class.getResourceAsStream("FlatLightLaf.properties")) {
            assertNotNull(in, "FlatLightLaf.properties is bundled");
            p.load(in);
        }
        String[][] vars = {{"@navy", "NAVY"}, {"@blue", "BLUE"}, {"@gray", "GRAY"}, {"@white", "WHITE"},
            {"@window", "WINDOW"}, {"@border", "BORDER"}, {"@hover", "HOVER"}, {"@text", "TEXT"}, {"@text2", "TEXT_2"},
            {"@muted", "TEXT_MUTED"}, {"@blueTint", "BLUE_TINT"}, {"@blueTint2", "BLUE_TINT_2"}, {"@scroll", "SCROLL"},
            {"@scrollHover", "SCROLL_HOVER"}};
        for (String[] v : vars) {
            Color c = (Color) Tokens.class.getField(v[1]).get(null);
            assertEquals(Tokens.hex(c).toLowerCase(), p.getProperty(v[0]), v[0] + " = Tokens." + v[1]);
        }
    }

    /** Z-12b, O-07: D2Coding이 번들되고 등록되며, 그 글꼴에서 0과 O의 모양이 다르다. */
    @Test
    void d2codingRegistersAndSeparatesZeroFromO() throws Exception {
        assertNotNull(Theme.class.getResourceAsStream(Theme.FONT_DIR + Theme.D2CODING));
        assertNotNull(Theme.class.getResourceAsStream(Theme.FONT_DIR + "LICENSE-D2Coding.txt"), "OFL text ships with the font");
        Font f = Theme.codeFont(13);
        assertEquals(Tokens.CODE_FONT, f.getFamily());
        java.awt.image.BufferedImage zero = glyph(f, '0');
        java.awt.image.BufferedImage oh = glyph(f, 'O');
        int diff = 0;
        for (int y = 0; y < zero.getHeight(); y++) {
            for (int x = 0; x < zero.getWidth(); x++) {
                if ((zero.getRGB(x, y) & 0xFF) != (oh.getRGB(x, y) & 0xFF)) {
                    diff++;
                }
            }
        }
        assertTrue(diff >= 8, "0 and O differ in D2Coding (" + diff + " pixels)");
        assertEquals(f.getStringBounds("0", new java.awt.font.FontRenderContext(null, true, true)).getWidth(),
                f.getStringBounds("O", new java.awt.font.FontRenderContext(null, true, true)).getWidth(), 0.01, "fixed width");
    }

    static java.awt.image.BufferedImage glyph(Font f, char c) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(40, 40, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 40, 40);
        g.setColor(Color.BLACK);
        g.setFont(f.deriveFont(26f));
        g.drawString(String.valueOf(c), 6, 30);
        g.dispose();
        return img;
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
