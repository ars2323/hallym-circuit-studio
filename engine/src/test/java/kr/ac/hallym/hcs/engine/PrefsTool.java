/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import java.util.prefs.Preferences;

/**
 * 테스트용 하위 프로세스: JDK 기본 환경설정(디스크)으로 원조 Logisim 설정 노드를 쓰거나 읽는다. 엔진이 이 값을
 * 읽지 않는지 보려고 쓴다. {@code write <tickFrequency> [<사용자 템플릿 .circ>]} 또는 {@code read}.
 */
public final class PrefsTool {
    /** 원조 AppPreferences의 노드(Preferences.userNodeForPackage(com.cburch.logisim.Main)). */
    static final String NODE = "com/cburch/logisim";

    private PrefsTool() {
    }

    public static void main(String[] args) throws Exception {
        Preferences p = Preferences.userRoot().node(NODE);
        if (args[0].equals("write")) {
            p.putDouble("tickFrequency", Double.parseDouble(args[1]));
            if (args.length > 2) {
                // File > New from a template of the student's (Preferences > Template > Custom)
                p.putInt("templateType", 2);
                p.put("templateFile", args[2]);
            }
            p.flush();
        } else {
            System.out.println(p.getDouble("tickFrequency", -1) + " " + p.getInt("templateType", -1));
        }
    }
}
