/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.gui;

import java.nio.file.Files;

import javax.swing.SwingUtilities;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * GUI 테스트 공용. 원조는 마지막 프로젝트 창이 닫히면 {@code ProjectActions.doQuit()}으로 {@code System.exit(0)}을
 * 부른다. 테스트가 창을 닫으면 JVM이 조용히 끝나 뒤 테스트가 실행되지 않은 채 빌드가 통과한다(P-07에서 발견). 그래서
 * 테스트 JVM마다 보이지 않는 창 하나를 열어 둔다(한 번 보였다 숨긴 창은 열린 프로젝트로 남는다).
 */
public final class GuiTestSupport {
    private static Frame anchor;

    private GuiTestSupport() {
    }

    public static synchronized void keepAlive() throws Exception {
        if (anchor != null) {
            return;
        }
        Project p = new Project(CircuitBuilder.newFile(new Loader(null),
                Files.createTempDirectory("hcs-gui-anchor").toFile()));
        p.getSimulator().setIsRunning(false);
        SwingUtilities.invokeAndWait(() -> {
            anchor = new Frame(p);
            anchor.setVisible(true);
            anchor.setVisible(false);
        });
    }
}
