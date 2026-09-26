/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.libs;

import java.io.File;
import java.io.IOException;

import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.sim.SimControls;

/**
 * 저장 뒤 상태 표시줄 알림(V-01, D-096): 파일이 Hallym MIPS를 쓰는데 .circ 옆에 hcs-mips.jar가 없으면 원조 2.7.1이
 * 열지 못하므로 한 번 알리고 [Copy hcs-mips.jar Here] 단추를 보인다. 누르기 전에는 복사하지 않는다.
 */
public final class JarBesideNotice {
    private JarBesideNotice() {
    }

    public static void afterSave(Project proj, File saved) {
        if (!MipsShadow.needsJarNotice(proj, saved)) {
            return;
        }
        SimControls.noticeWithButton(proj, Messages.get("mips.jarMissing"), Messages.get("mips.copyHere"),
                () -> copy(saved));
    }

    static String copy(File saved) {
        try {
            File dest = MipsShadow.copyJarBeside(saved);
            return Messages.get("mips.copied", dest.getName());
        } catch (IOException e) {
            return Messages.get("mips.copyFailed", e.getMessage());
        }
    }
}
