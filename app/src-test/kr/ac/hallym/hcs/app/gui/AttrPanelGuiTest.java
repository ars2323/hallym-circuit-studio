/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.generic.AttrTable;
import com.cburch.logisim.gui.generic.AttrTableModel;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.diag.Diagnostic;
import kr.ac.hallym.hcs.app.diag.Diagnostics;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * GUI 스모크 테스트(PLAN.md 11.16, 화면 필요: {@code xvfb-run -a ./gradlew :app:guiTest}). 2c 검토 반영 2: 프로그램이
 * 고른 선택(Messages에서 누름) 뒤에도 오른쪽 Attributes 패널은 고른 부품의 속성을 보인다. 전에는 제목만 "Selection:
 * Register"이고 줄은 회로 속성(Circuit Name …)이 빈 값으로 남았다.
 */
@Tag("gui")
class AttrPanelGuiTest {
    @TempDir
    Path tmp;

    static AttrTable table(Container root) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof AttrTable) {
                return (AttrTable) c;
            }
            if (c instanceof Container) {
                AttrTable t = table((Container) c);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    static List<String> labels(AttrTableModel m) {
        List<String> ret = new ArrayList<>();
        for (int i = 0; i < m.getRowCount(); i++) {
            ret.add(m.getRow(i).getLabel() + "=" + m.getRow(i).getValue());
        }
        return ret;
    }

    @Test
    void attributePanelFollowsASelectionMadeFromMessages() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component reg = b.add("Memory", "Register", 300, 200, "width", "8", "label", "PC");
        b.input("d", 8, 100, 100);
        b.output("q", 8, 500, 100);
        b.tunnel(reg, 1, "d");
        b.tunnel(reg, 0, "q");
        b.commit();
        Circuit other = new Circuit("other");
        f.addCircuit(other);
        Project proj = new Project(f);
        AtomicReference<Frame> frame = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> frame.set(new Frame(proj)));
        try {
            com.cburch.logisim.tools.Library base = f.getLoader().getBuiltin().getLibrary("Base");
            SwingUtilities.invokeAndWait(() -> {
                proj.setCurrentCircuit(other);
                proj.setCurrentCircuit(f.getMainCircuit());
                proj.setTool(base.getTool("Edit Tool")); // 빈 선택: 선택 모델의 줄이 회로 속성(Circuit Name …)이 된다
            });
            // 표가 잠시 다른 것을 보인다(스크린샷 장면에서는 비어 있었다). 선택 모델은 이때 속성 목록을 듣지 않는다
            SwingUtilities.invokeAndWait(() -> frame.get().viewComponentAttributes(f.getMainCircuit(), null));
            SwingUtilities.invokeAndWait(() -> frame.get().setVisible(true));
            // 스크린샷 장면처럼 편집 동작 하나(터널 이름 바꾸기) 뒤에 Messages를 누른다
            SwingUtilities.invokeAndWait(() -> {
                Component t = null;
                for (Component c : f.getMainCircuit().getNonWires()) {
                    if (c.getFactory().getName().equals("Tunnel")) {
                        t = c;
                    }
                }
                com.cburch.logisim.circuit.CircuitMutation m =
                        new com.cburch.logisim.circuit.CircuitMutation(f.getMainCircuit());
                m.set(t, com.cburch.logisim.instance.StdAttr.LABEL, "renamed");
                proj.doAction(m.toAction(null));
            });
            SwingUtilities.invokeAndWait(() -> Diagnostics.of(proj).refresh());
            Diagnostics diags = Diagnostics.of(proj);
            Diagnostic d = diags.list().get(0);
            assertEquals(Diagnostic.Kind.CLOCK_UNCONNECTED, d.kind);
            SwingUtilities.invokeAndWait(() -> diags.go(d));
            SwingUtilities.invokeAndWait(() -> { }); // 선택 뒤 이어지는 이벤트까지
            AtomicReference<List<String>> rows = new AtomicReference<>();
            AtomicReference<String> title = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                AttrTableModel m = table(frame.get().getContentPane()).getAttrTableModel();
                rows.set(labels(m));
                title.set(m.getTitle());
            });
            assertTrue(proj.getSelection().getComponents().contains(reg), "the cause is selected");
            assertTrue(title.get().contains("Register"), title.get());
            assertTrue(rows.get().stream().anyMatch(r -> r.startsWith("Data Bits=8")), rows.get().toString());
            assertTrue(rows.get().stream().anyMatch(r -> r.startsWith("Label=PC")), rows.get().toString());
            assertFalse(rows.get().stream().anyMatch(r -> r.startsWith("Circuit Name")), rows.get().toString());
        } finally {
            SwingUtilities.invokeAndWait(() -> frame.get().dispose());
        }
    }
}
