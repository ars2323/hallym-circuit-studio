/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.LocaleManager;

import kr.ac.hallym.hcs.regress.CircEquivalence;
import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;
import kr.ac.hallym.hcs.regress.Engine;

/**
 * #23, 규칙 2.3: UI 언어가 한국어여도 저장 결과는 영어일 때와 같다. 기본 라이브러리의 모든 부품을 기본 속성으로
 * 놓은 새 파일과 tests/circ 회로를 두 언어로 저장해 비교한다.
 */
class LocaleSaveCompatTest {
    static final File DIR = new File(System.getProperty("hcs.circDir"));

    @TempDir
    Path tmp;

    String everyComponent(Locale locale, String name) throws Exception {
        Locale old = LocaleManager.getLocale();
        try {
            LocaleManager.setLocale(locale);
            Path dir = Files.createDirectories(tmp.resolve(name));
            LogisimFile file = CircuitBuilder.newFile(new Loader(null), dir.toFile());
            CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
            int i = 0;
            for (Library lib : file.getLoader().getBuiltin().getLibraries()) {
                for (Tool t : lib.getTools()) {
                    if (t instanceof AddTool) {
                        b.add(lib, t.getName(), 100 + (i % 10) * 200, 100 + (i / 10) * 200);
                        i++;
                    }
                }
            }
            assertTrue(i >= 50, "all builtin components placed: " + i);
            b.commit();
            File out = dir.resolve(name + ".circ").toFile();
            CircuitBuilder.save(file, out);
            return new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        } finally {
            LocaleManager.setLocale(old);
        }
    }

    @Test
    void everyBuiltinComponentSavesTheSameInKorean() throws Exception {
        String en = everyComponent(Locale.ENGLISH, "en");
        String ko = everyComponent(new Locale("ko"), "ko");
        assertEquals(CircNormalizer.normalize(en), CircNormalizer.normalize(ko));
        for (char c : ko.toCharArray()) {
            assertTrue(c < 128, "no localized text in the saved file: " + c);
        }
    }

    @Test
    void existingCircuitsSaveTheSameInKorean() throws Exception {
        Locale old = LocaleManager.getLocale();
        try {
            LocaleManager.setLocale(new Locale("ko"));
            List<String> names = Engine.circuits(DIR);
            for (String name : names) {
                File original = new File(DIR, name + ".circ");
                LogisimFile file = new Loader(null).openLogisimFile(original);
                File saved = tmp.resolve(name + "-ko.circ").toFile();
                CircuitBuilder.save(file, saved);
                assertEquals(CircNormalizer.normalize(new String(Files.readAllBytes(original.toPath()),
                        StandardCharsets.UTF_8)), CircNormalizer.normalize(new String(Files.readAllBytes(
                        saved.toPath()), StandardCharsets.UTF_8)), name);
                assertEquals(Collections.<String>emptyList(), CircEquivalence.compare(original, saved), name);
            }
        } finally {
            LocaleManager.setLocale(old);
        }
    }
}
