/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import kr.ac.hallym.hcs.mips.image.HmxFormat;

/**
 * D-125: 포크(이제 엔진이 쓰는 app 모듈)는 공용 소스를 직접 컴파일해 쓴다. 번들 라이브러리의 클래스 로더(원조
 * ZipClassLoader)는 부모를 먼저 찾으므로, 엔진 안에서는 hcs-mips.jar 코드도 이 사본을 쓴다(같은 소스라 결과가 같다).
 * 엔진 jar에 들어 있는지는 엔진의 SubprocessTest가 본다.
 */
class SharedSourceAppTest {
    @Test
    void theAppCompilesTheSharedSourceIntoItsJar() throws Exception {
        assertEquals("HALLYM-EXEC 1", HmxFormat.header());
        String from = HmxFormat.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        assertTrue(from.replace('\\', '/').contains("/app/build/classes/java/main"),
                "the shared class is compiled into the app module: " + from);
    }
}
