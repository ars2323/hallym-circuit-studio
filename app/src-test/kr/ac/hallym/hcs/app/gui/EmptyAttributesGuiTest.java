/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.gui.generic.AttrTable;
import com.cburch.logisim.gui.generic.AttrTableModel;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** Y-05: 아무것도 고르지 않은 새 창의 Attributes 칸은 비어 있지 않고 현재 회로의 속성(Circuit: main)을 보인다. */
@Tag("gui")
class EmptyAttributesGuiTest {
    @TempDir
    Path tmp;

    @Test
    void aFreshWindowShowsTheCircuitAttributes() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
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
            Thread.sleep(800);
            String[] got = new String[2];
            SwingUtilities.invokeAndWait(() -> {
                AttrTable t = AttrPanelGuiTest.table(frame.getContentPane());
                AttrTableModel m = t == null ? null : t.getAttrTableModel();
                got[0] = m == null ? null : m.getTitle();
                got[1] = m == null ? "0" : Integer.toString(m.getRowCount());
            });
            assertNotNull(got[0], "the attribute pane has a model with a title");
            assertEquals(true, got[0].contains("main"), "the circuit's own attributes (Circuit: main): " + got[0]);
            assertEquals(true, Integer.parseInt(got[1]) > 0, "the circuit attributes are listed");
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
