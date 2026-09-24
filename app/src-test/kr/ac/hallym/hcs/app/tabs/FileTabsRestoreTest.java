/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import kr.ac.hallym.hcs.app.Settings;

/**
 * #68: 탭 복원 목록을 앱 환경설정에서 읽어 연다. 없어진 파일과 열다 실패한 파일은 건너뛰고 프로그램을 끝내지
 * 않는다(명령줄로 준 파일과 다르다).
 */
class FileTabsRestoreTest {
    @TempDir
    Path tmp;

    @AfterEach
    void clear() {
        Settings.get().setList(FileTabs.OPEN, Collections.emptyList());
        Settings.get().set(FileTabs.ACTIVE, (String) null);
    }

    File file(String name) throws Exception {
        return Files.createFile(tmp.resolve(name)).toFile();
    }

    @Test
    void missingAndBrokenFilesAreSkipped() throws Exception {
        File ok = file("ok.circ");
        File broken = file("broken.circ");
        File gone = tmp.resolve("gone.circ").toFile();
        Settings.get().setList(FileTabs.OPEN,
                Arrays.asList(ok.getPath(), gone.getPath(), broken.getPath()));
        Settings.get().set(FileTabs.ACTIVE, 2);
        List<File> opened = new ArrayList<>();
        boolean any = FileTabs.get().openRestored(f -> {
            if (f.equals(broken)) {
                throw new Exception("cannot load");
            }
            opened.add(f);
        });
        assertTrue(any);
        assertEquals(Collections.singletonList(ok), opened, "the missing file is not even tried");
    }

    @Test
    void nothingToRestoreMeansNewFile() throws Exception {
        assertFalse(FileTabs.get().openRestored(f -> {
            throw new AssertionError("nothing to open");
        }));
        File broken = file("only.circ");
        Settings.get().setList(FileTabs.OPEN, Collections.singletonList(broken.getPath()));
        assertFalse(FileTabs.get().openRestored(f -> {
            throw new IllegalStateException("bad file");
        }), "all failed: start with a new file instead");
    }
}
