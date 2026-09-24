/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** #67: 앱 환경설정 저장소와 설정 폴더. */
class SettingsTest {
    @TempDir
    Path tmp;

    @Test
    void valuesSurviveSaveAndReload() throws Exception {
        File f = tmp.resolve("sub/settings.properties").toFile();
        Settings s = new Settings(f);
        s.set("toolbar.style", "icons+text");
        s.set("label.density", 2);
        s.set("zoom.fitOnOpen", true);
        s.setList("recent", Arrays.asList("/a/한글 폴더/x.circ", "C:\\b\\y.circ"));
        s.save();

        Settings t = new Settings(f);
        assertEquals("icons+text", t.getString("toolbar.style", null));
        assertEquals(2, t.getInt("label.density", 0));
        assertTrue(t.getBoolean("zoom.fitOnOpen", false));
        assertEquals(Arrays.asList("/a/한글 폴더/x.circ", "C:\\b\\y.circ"), t.getList("recent"));
        assertEquals("def", t.getString("missing", "def"));
        assertEquals(7, t.getInt("toolbar.style", 7), "not a number falls back to the default");
    }

    @Test
    void shorterListRemovesOldEntries() throws Exception {
        File f = tmp.resolve("settings.properties").toFile();
        Settings s = new Settings(f);
        s.setList("tabs", Arrays.asList("a", "b", "c"));
        s.setList("tabs", Collections.singletonList("z"));
        s.save();
        assertEquals(Collections.singletonList("z"), new Settings(f).getList("tabs"));
        s.set("tabs.0", (String) null);
        assertEquals(Collections.emptyList(), s.getList("tabs"));
    }

    @Test
    void savedFileIsSortedAndLeavesNoTempFile() throws Exception {
        File f = tmp.resolve("settings.properties").toFile();
        Settings s = new Settings(f);
        s.set("b", "2");
        s.set("a", "1");
        s.save();
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.ISO_8859_1);
        assertTrue(text.indexOf("a=1") < text.indexOf("b=2"), text);
        try (java.util.stream.Stream<Path> files = Files.list(tmp)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void brokenFileStartsFromDefaults() throws Exception {
        File f = tmp.resolve("settings.properties").toFile();
        Files.write(f.toPath(), "a=\\u12".getBytes(StandardCharsets.ISO_8859_1)); // 깨진 이스케이프
        Settings s = new Settings(f);
        assertEquals("d", s.getString("a", "d"));
    }

    @Test
    void configFolderPerOs() {
        assertEquals(new File("C:\\Users\\s\\AppData\\Roaming", "HallymCircuitStudio"),
                AppDirs.config("Windows 11", "C:\\Users\\s", "C:\\Users\\s\\AppData\\Roaming", null));
        assertEquals(new File("/Users/s/Library/Application Support/HallymCircuitStudio"),
                AppDirs.config("Mac OS X", "/Users/s", null, null));
        assertEquals(new File("/home/s/.config/hallym-circuit-studio"),
                AppDirs.config("Linux", "/home/s", null, ""));
        assertEquals(new File("/x/hallym-circuit-studio"), AppDirs.config("Linux", "/home/s", null, "/x"));
    }

    @Test
    void configFolderOverride() {
        String old = System.getProperty("hcs.configDir");
        try {
            System.setProperty("hcs.configDir", tmp.toString());
            assertEquals(tmp.toFile(), AppDirs.config());
        } finally {
            if (old == null) System.clearProperty("hcs.configDir");
            else System.setProperty("hcs.configDir", old);
        }
    }

    @Test
    void messagesInKoreanAndEnglish() {
        String ko = Messages.get(Locale.KOREAN, "extSaveError", "E");
        String en = Messages.get(Locale.ENGLISH, "extSaveError", "E");
        assertTrue(ko.startsWith("회로는 저장했지만") && ko.endsWith(": E"), ko);
        assertTrue(en.startsWith("The circuit was saved") && en.endsWith(": E"), en);
        assertEquals("noSuchKey", Messages.get(Locale.ENGLISH, "noSuchKey"));
        assertFalse(ko.equals(en));
    }
}
