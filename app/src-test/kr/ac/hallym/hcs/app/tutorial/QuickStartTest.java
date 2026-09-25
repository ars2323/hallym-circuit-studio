/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tutorial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.Locale;

import javax.swing.ImageIcon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import kr.ac.hallym.hcs.app.Settings;

/** #23: 첫 실행 안내. 창 자체는 GUI 스모크(Xvfb)로 본다. */
class QuickStartTest {
    @TempDir
    Path tmp;

    static String text(Locale locale, String key) throws Exception {
        java.lang.reflect.Method m = kr.ac.hallym.hcs.app.Messages.class.getDeclaredMethod("get",
                Locale.class, String.class, Object[].class);
        m.setAccessible(true);
        return (String) m.invoke(null, locale, key, new Object[0]);
    }

    @Test
    void everyPageHasAnEnglishTitleAndKoreanAndEnglishBody() throws Exception {
        for (QuickStart.Page p : QuickStart.PAGES) {
            // 제목은 이름(영어 고정), 본문은 설명 문장(D-049)
            String title = text(Locale.KOREAN, p.key + ".title");
            assertNotEquals(p.key + ".title", title, "missing title");
            assertEquals(text(Locale.ENGLISH, p.key + ".title"), title);
            String ko = text(Locale.KOREAN, p.key + ".body");
            String en = text(Locale.ENGLISH, p.key + ".body");
            assertNotEquals(p.key + ".body", ko, "missing Korean text");
            assertNotEquals(p.key + ".body", en, "missing English text");
            assertNotEquals(ko, en);
        }
    }

    @Test
    void charactersAreBundledAndOnlyScaled() {
        int shown = 0;
        for (QuickStart.Page p : QuickStart.PAGES) {
            if (p.image != null) {
                ImageIcon icon = QuickStart.character(p.image);
                assertNotNull(icon, p.image);
                assertEquals(QuickStart.IMAGE_SIZE, icon.getIconWidth());
                assertEquals(QuickStart.IMAGE_SIZE, icon.getIconHeight(), "square originals keep their ratio");
                shown++;
            }
        }
        assertEquals(2, shown, "characters only on the first and last page");
        assertTrue(QuickStart.PAGES.get(0).image != null
                && QuickStart.PAGES.get(QuickStart.PAGES.size() - 1).image != null);
    }

    @Test
    void showsOnlyOnFirstRun() throws Exception {
        File f = tmp.resolve("settings.properties").toFile();
        Constructor<Settings> c = Settings.class.getDeclaredConstructor(File.class);
        c.setAccessible(true);
        Settings s = c.newInstance(f);
        assertTrue(QuickStart.markSeen(s));
        assertFalse(QuickStart.markSeen(s));
        assertFalse(QuickStart.markSeen(c.newInstance(f)), "remembered across runs");
    }
}
