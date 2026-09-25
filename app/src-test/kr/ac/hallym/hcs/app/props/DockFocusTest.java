/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.props;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** ui-reviewer(#242): 속성 패널을 접거나 펴도 초점이 부품 검색 칸으로 넘어가지 않고 캔버스에 남는다. */
class DockFocusTest {
    @TempDir
    Path tmp;

    @Test
    void focusGoesBackToTheCanvasNotToTheSearchField() throws Exception {
        Project proj = new Project(CircuitBuilder.newFile(new Loader(null), tmp.toFile()));
        proj.getSimulator().setIsRunning(false);
        Canvas canvas = new Canvas(proj);
        JPanel center = new JPanel();
        JPanel inner = new JPanel();
        inner.add(canvas);
        center.add(inner);
        JPanel panel = new JPanel();
        JButton inPanel = new JButton("x");
        panel.add(inPanel);
        JPanel strip = new JPanel();
        JButton collapse = new JButton("<");
        strip.add(collapse);
        JTextField search = new JTextField();

        assertSame(canvas, AttrDock.focusAfter(canvas, center, panel, strip), "canvas keeps focus");
        assertSame(canvas, AttrDock.focusAfter(collapse, center, panel, strip), "the collapse button hands it back");
        assertSame(canvas, AttrDock.focusAfter(inPanel, center, panel, strip), "a row of the panel being hidden");
        assertSame(canvas, AttrDock.focusAfter(null, center, panel, strip), "no focus owner");
        assertNull(AttrDock.focusAfter(search, center, panel, strip), "a field the student chose stays focused");
    }
}
