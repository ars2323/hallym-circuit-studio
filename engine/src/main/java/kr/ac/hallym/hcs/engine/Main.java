/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;

import kr.ac.hallym.hcs.engine.prefs.MemoryPreferencesFactory;
import kr.ac.hallym.hcs.engine.rpc.Server;

/**
 * 엔진 프로세스의 시작점: {@code java -jar hcs-engine.jar}(docs/engine-api.md 2절). 화면(Electron main)의 자식
 * 프로세스로 돌고, stdin·stdout으로 JSON-RPC를 주고받는다. stdin이 닫히면 끝난다.
 *
 * <p>시작 순서가 중요하다: (1) 메모리 전용 환경설정(원조 설정을 읽지도 쓰지도 않는다), (2) headless, (3) 번들
 * hcs-mips.jar 위치, (4) stdout을 규약 전용으로 떼어 내고 {@code System.out}은 stderr로 돌린다(Logisim 코드의
 * 출력이 규약을 깨지 않게).
 */
public final class Main {
    /** 번들 MIPS 라이브러리 위치를 주는 시스템 속성({@code BundledLibraries}와 같은 이름). */
    static final String MIPS_PROPERTY = "hcs.bundledMips";

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        MemoryPreferencesFactory.install();
        System.setProperty("java.awt.headless", "true");
        locateMipsJar();
        OutputStream protocol = new FileOutputStream(FileDescriptor.out);
        PrintStream log = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        System.setOut(log);
        if (!MemoryPreferencesFactory.isActive()) {
            log.println("[hcs-engine] error: in-memory preferences are not active; refusing to start");
            System.exit(3);
        }
        Server server = new Server(System.in, protocol, log);
        new Engine(server);
        String reason = server.serve();
        log.println("[hcs-engine] info: exit (" + reason + ")");
        log.flush();
        System.exit(0);
    }

    /**
     * hcs-mips.jar 찾기: 시스템 속성 {@code hcs.bundledMips}가 있으면 그대로, 없으면 hcs-engine.jar 옆, 그다음
     * 옆의 {@code lib/}(v1 배치와 같다).
     */
    static void locateMipsJar() {
        String prop = System.getProperty(MIPS_PROPERTY);
        if (prop != null && !prop.isEmpty()) {
            return;
        }
        File home = jarHome();
        if (home == null) {
            return;
        }
        for (File f : new File[] {new File(home, "hcs-mips.jar"), new File(new File(home, "lib"), "hcs-mips.jar")}) {
            if (f.canRead()) {
                System.setProperty(MIPS_PROPERTY, f.getAbsolutePath());
                return;
            }
        }
    }

    /** hcs-engine.jar가 있는 폴더(클래스 폴더에서 돌면 null). */
    static File jarHome() {
        CodeSource src = Main.class.getProtectionDomain().getCodeSource();
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
