/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.gui.GuiTestSupport;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** X-02 GUI: 창 폭 1920·1280·960·640에서 모든 도구 모음 명령이 도구 모음이나 » 메뉴에 있고 잘린 단추가 없다. */
@Tag("gui")
class ToolbarOverflowGuiTest {
    @TempDir
    Path tmp;

    static OverflowToolbar find(Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof OverflowToolbar) {
                return (OverflowToolbar) k;
            }
            if (k instanceof Container) {
                OverflowToolbar t = find((Container) k);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    @Test
    void everyCommandIsSomewhereAndNothingIsClipped() throws Exception {
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
            OverflowToolbar tb = find(frame.getContentPane());
            assertNotNull(tb, "the app toolbar");
            SwingUtilities.invokeAndWait(() -> frame.setBounds(0, 0, 1920, 900));
            Thread.sleep(300);
            List<String> all = new ArrayList<>();
            SwingUtilities.invokeAndWait(() -> {
                frame.validate();
                all.addAll(tb.shownKeys());
                all.addAll(tb.overflowKeys());
            });
            assertEquals(20, all.size(), "all toolbar commands: " + all);
            for (int width : new int[] {1920, 1280, 960, 640}) {
                SwingUtilities.invokeAndWait(() -> frame.setBounds(0, 0, width, 900));
                Thread.sleep(300);
                List<String> problems = new ArrayList<>();
                SwingUtilities.invokeAndWait(() -> {
                    frame.validate();
                    List<String> here = new ArrayList<>(tb.shownKeys());
                    here.addAll(tb.overflowKeys());
                    if (!here.containsAll(all) || here.size() != all.size()) {
                        problems.add(width + ": commands lost " + here);
                    }
                    Rectangle box = new Rectangle(0, 0, tb.getWidth(), tb.getHeight());
                    for (Component c : tb.shownComponents()) {
                        if (!c.isVisible() || !box.contains(c.getBounds())) {
                            problems.add(width + ": clipped " + c.getBounds() + " in " + box);
                        }
                    }
                    if (tb.moreButton().isVisible() && !box.contains(tb.moreButton().getBounds())) {
                        problems.add(width + ": » clipped");
                    }
                    if (width >= 1920 && !tb.overflowKeys().isEmpty()) {
                        problems.add("1920: nothing should overflow " + tb.overflowKeys());
                    }
                    if (width <= 640) {
                        for (String keep : new String[] {"bar.run", "bar.cycle", "bar.reset", "bar.program"}) {
                            if (!tb.shownKeys().contains(keep)) {
                                problems.add("640: " + keep + " should be among the last hidden: " + tb.shownKeys());
                            }
                        }
                    }
                });
                assertTrue(problems.isEmpty(), String.join("\n", problems));
            }
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
