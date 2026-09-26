/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.props;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.gui.GuiTestSupport;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * v1.0.2 최종 세트 검토(14d): 메시지 클릭 같은 조용한 선택 뒤 배율을 바꿔도 옛 부품의 빠른 속성 창이 다시 뜨지 않는다
 * (체크리스트 6).
 */
@Tag("gui")
class QuickBarQuietGuiTest {
    @TempDir
    Path tmp;

    static void settle() throws Exception {
        for (int i = 0; i < 4; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(80);
        }
    }

    @Test
    void aQuietSelectionKeepsTheBarHiddenAcrossAZoomChange() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component pin = b.input("in", 1, 100, 100);
        Component reg = b.add("Memory", "Register", 300, 200, "width", "8");
        b.commit();
        Project proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> fr.set(new Frame(proj)));
        Frame frame = fr.get();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame.setVisible(true);
                proj.setTool(file.getLoader().getBuiltin().getLibrary("Base").getTool("Edit Tool"));
            });
            settle();
            QuickBar q = QuickBar.of(frame);
            assertNotNull(q, "the quick attribute bar");
            SwingUtilities.invokeAndWait(() -> proj.getSelection().add(pin));
            settle();
            assertTrue(q.isBarVisible(), "a pin picked by hand shows its quick attributes");
            // 메시지 클릭처럼: 조용한 선택으로 레지스터를 고른다
            SwingUtilities.invokeAndWait(() -> {
                proj.doAction(com.cburch.logisim.gui.main.SelectionActions.dropAll(proj.getSelection()));
                QuickBar.markQuiet(proj, java.util.List.of(reg));
                proj.getSelection().add(reg);
            });
            settle();
            assertFalse(q.isBarVisible(), "a quiet selection shows no bar");
            // 배율을 바꾸면 place()가 돈다: 옛 핀 단추가 레지스터 위에 다시 뜨면 안 된다
            SwingUtilities.invokeAndWait(() -> frame.getCanvas().getHcsZoom().zoomTo(1.5));
            settle();
            SwingUtilities.invokeAndWait(() -> frame.getCanvas().getHcsZoom().fitCircuit());
            settle();
            assertFalse(q.isBarVisible(), "still no bar after zoom changes");
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
