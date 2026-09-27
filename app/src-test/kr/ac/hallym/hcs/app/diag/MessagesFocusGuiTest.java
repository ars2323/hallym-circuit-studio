/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JList;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.gui.GuiTestSupport;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 메시지를 누르면(원인으로 가고 목록을 새로 고침) 키보드 초점이 Messages 목록에 남는다(v1.0.3 최종 세트 검토: 14b에서
 * 초점이 왼쪽 위 부품 검색 칸으로 옮겨 가 있었다. 그 뒤 친 글자가 검색 칸으로 갔다).
 */
@Tag("gui")
class MessagesFocusGuiTest {
    @TempDir
    Path tmp;

    @Test
    void clickingAMessageKeepsTheFocusInTheList() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit main = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, main);
        Component r = b.add("Memory", "Register", 300, 200, "width", "8", "label", "PC"); // 클럭 없는 레지스터
        b.tunnel(r, 1, "d");
        b.tunnel(r, 0, "q");
        b.commit();
        Project proj = new Project(file);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Frame f = new Frame(proj);
            proj.setFrame(f);
            f.setVisible(true);
            fr.set(f);
        });
        Frame frame = fr.get();
        try {
            Diagnostics diags = Diagnostics.of(proj);
            long end = System.currentTimeMillis() + 5000;
            while (diags.list().isEmpty() && System.currentTimeMillis() < end) {
                Thread.sleep(50);
            }
            AtomicReference<JList<Diagnostic>> listRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                MessagesPanel mp = MessagesPanel.of(frame);
                mp.open();
                listRef.set(mp.listForTest());
            });
            JList<Diagnostic> list = listRef.get();
            SwingUtilities.invokeAndWait(() -> {
                frame.toFront();
                list.requestFocusInWindow();
            });
            end = System.currentTimeMillis() + 5000;
            while (!list.isFocusOwner() && System.currentTimeMillis() < end) {
                Thread.sleep(50);
            }
            assumeTrue(list.isFocusOwner(), "window focus is available on this display");
            // 누른 것과 같은 일: 원인으로 가기, 그 뒤 편집이 멈춰 다시 도는 진단
            SwingUtilities.invokeAndWait(() -> {
                diags.go(diags.list().get(0));
                diags.refresh();
            });
            Thread.sleep(800);
            SwingUtilities.invokeAndWait(() -> {
            });
            java.awt.Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            assertTrue(list.isFocusOwner(), "the focus stays in the Messages list, not " + owner);
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
