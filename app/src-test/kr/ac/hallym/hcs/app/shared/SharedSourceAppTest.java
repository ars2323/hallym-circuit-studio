/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;

import kr.ac.hallym.hcs.mips.image.HmxFormat;

/**
 * D-125: 포크 앱은 공용 소스를 직접 컴파일해 쓴다. 번들 라이브러리의 클래스 로더(원조 ZipClassLoader)는 부모를 먼저 찾으므로,
 * 포크 안에서는 hcs-mips.jar 코드도 포크 jar의 이 사본을 쓴다(같은 소스라 결과가 같다).
 */
class SharedSourceAppTest {
    @Test
    void theAppCompilesTheSharedSourceIntoItsJar() throws Exception {
        assertEquals("HALLYM-EXEC 1", HmxFormat.header());
        try (ZipFile z = new ZipFile(new File(System.getProperty("hcs.forkJar")))) {
            assertNotNull(z.getEntry("kr/ac/hallym/hcs/mips/image/HmxFormat.class"), "the fork jar has the shared class");
        }
    }
}
