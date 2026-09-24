/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app;

import java.io.File;
import java.net.URISyntaxException;
import java.security.CodeSource;

/**
 * 포크에 번들된 라이브러리 jar(D-007). .circ의 {@code jar#<경로>#<클래스>}에서 경로의 jar를 읽을 수 없고 클래스가
 * 번들된 라이브러리면, 파일 선택 창 대신 번들 jar로 연결한다. 경로의 jar를 읽을 수 있으면 원조처럼 그것을 쓴다.
 *
 * <p>번들 위치: 시스템 속성 {@code hcs.bundledMips}, 없으면 포크 jar 옆 {@code lib/hcs-mips.jar}. hcs-asm도 같은
 * {@code lib/}에 둔다(lib-mips가 jar 옆에서 찾는다).
 */
public final class BundledLibraries {
    public static final String MIPS_CLASS = "kr.ac.hallym.hcs.mips.MipsLibrary";
    static final String MIPS_JAR = "hcs-mips.jar";

    private BundledLibraries() {
    }

    /**
     * requested(.circ가 가리키는 jar)를 대신할 번들 jar. requested를 읽을 수 있거나, 번들에 없는 클래스거나,
     * 번들 jar가 없으면 null(원조 동작).
     */
    public static File substitute(File requested, String className) {
        if (requested.canRead() || !MIPS_CLASS.equals(className)) {
            return null;
        }
        File bundled = mipsJar();
        return bundled != null && bundled.canRead() ? bundled : null;
    }

    static File mipsJar() {
        String prop = System.getProperty("hcs.bundledMips");
        if (prop != null && !prop.isEmpty()) {
            return new File(prop);
        }
        File home = appHome();
        return home == null ? null : new File(new File(home, "lib"), MIPS_JAR);
    }

    /** 포크 jar가 있는 폴더. 클래스 폴더에서 돌 때(개발 중)는 null. */
    static File appHome() {
        CodeSource src = BundledLibraries.class.getProtectionDomain().getCodeSource();
        if (src == null) {
            return null;
        }
        try {
            File f = new File(src.getLocation().toURI());
            return f.isFile() ? f.getParentFile() : null;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }
}
