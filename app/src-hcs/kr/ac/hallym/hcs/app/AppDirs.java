/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app;

import java.io.File;

/**
 * 앱 설정 폴더(OS별). 설정 파일, 자동 저장(#70)이 여기에 들어간다. .circ 옆에는 아무 파일도 만들지 않는다.
 * 시스템 속성 {@code hcs.configDir}가 있으면 그 폴더를 쓴다(테스트, 휴대용 실행).
 */
public final class AppDirs {
    static final String NAME = "HallymCircuitStudio";

    private AppDirs() {
    }

    public static File config() {
        String override = System.getProperty("hcs.configDir");
        if (override != null && !override.isEmpty()) {
            return new File(override);
        }
        return config(System.getProperty("os.name", ""), System.getProperty("user.home", "."),
                System.getenv("APPDATA"), System.getenv("XDG_CONFIG_HOME"));
    }

    static File config(String os, String home, String appData, String xdgConfig) {
        String lower = os.toLowerCase();
        if (lower.startsWith("windows")) {
            File base = appData != null && !appData.isEmpty()
                    ? new File(appData) : new File(home, "AppData" + File.separator + "Roaming");
            return new File(base, NAME);
        }
        if (lower.startsWith("mac")) {
            return new File(home, "Library/Application Support/" + NAME);
        }
        File base = xdgConfig != null && !xdgConfig.isEmpty() ? new File(xdgConfig) : new File(home, ".config");
        return new File(base, "hallym-circuit-studio");
    }
}
