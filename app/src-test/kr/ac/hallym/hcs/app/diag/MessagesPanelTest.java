/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #27: Messages 탭 모델, 클릭 이동, 편집 뒤 다시 돌기. */
class MessagesPanelTest {
    @TempDir
    Path tmp;

    /** 서브회로 datapath 안에 클럭 없는 PC. */
    LogisimFile broken() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit dp = new Circuit("datapath");
        f.addCircuit(dp);
        CircuitBuilder b = new CircuitBuilder(f, dp);
        Component r = b.add("Memory", "Register", 300, 200, "width", "8", "label", "PC");
        b.input("d", 8, 100, 100);
        b.output("q", 8, 500, 100);
        b.tunnel(r, 1, "d");
        b.tunnel(r, 0, "q");
        b.commit();
        return f;
    }

    @Test
    void listsDiagnosticsAndGoesToTheCause() throws Exception {
        Project proj = new Project(broken());
        MessagesPanel panel = new MessagesPanel(proj);
        assertEquals(1, panel.rows().size());
        Diagnostic d = panel.rows().get(0);
        assertEquals(Diagnostic.Kind.CLOCK_UNCONNECTED, d.kind);
        assertTrue(MessagesPanel.row(d).contains("datapath › PC"), MessagesPanel.row(d));
        assertEquals(MessagesPanel.statusText(1), panel.statusLabel().getText());

        Diagnostics diags = Diagnostics.of(proj);
        diags.go(d);
        assertSame(d.circuit, proj.getCurrentCircuit(), "opens the circuit of the cause");
        // 선택은 창의 캔버스에 있다(헤드리스 프로젝트에는 없다). 고를 부품은 원인 부품이다
        assertEquals("PC", kr.ac.hallym.hcs.app.model.Names.label(d.components.get(0)));
        assertSame(d, diags.focused(), "the canvas draws it strongly");
        assertEquals(1, diags.in(d.circuit).size());
        assertEquals(0, diags.in(proj.getLogisimFile().getMainCircuit()).size());
    }

    @Test
    void editsTriggerARunAndFixesClearTheList() throws Exception {
        Project proj = new Project(broken());
        Diagnostics diags = Diagnostics.of(proj);
        MessagesPanel panel = new MessagesPanel(proj);
        Circuit dp = proj.getLogisimFile().getCircuit("datapath");
        Component reg = diags.list().get(0).components.get(0);
        // 클럭을 붙이는 편집(되돌릴 수 있는 동작)
        CircuitBuilder b = new CircuitBuilder(proj.getLogisimFile(), dp);
        Component clk = b.add("Wiring", "Clock", 100, 400);
        CircuitMutation m = new CircuitMutation(dp);
        m.add(clk);
        m.add(com.cburch.logisim.circuit.Wire.create(clk.getEnd(0).getLocation(),
                com.cburch.logisim.data.Location.create(reg.getEnd(2).getLocation().getX(),
                        clk.getEnd(0).getLocation().getY())));
        m.add(com.cburch.logisim.circuit.Wire.create(com.cburch.logisim.data.Location.create(
                reg.getEnd(2).getLocation().getX(), clk.getEnd(0).getLocation().getY()), reg.getEnd(2).getLocation()));
        for (int i = 0; i < 3; i++) {
            System.gc(); // 원조 Project는 리스너를 약하게 잡는다: GC 뒤에도 편집을 들어야 한다
        }
        proj.doAction(m.toAction(null));
        assertTrue(diags.pending(), "runs again once editing stops");
        diags.refresh(); // 타이머 대신 바로
        assertEquals(0, panel.rows().size(), diags.list().toString());
        assertEquals(MessagesPanel.statusText(0), panel.statusLabel().getText());
    }

    /** 탭 이름과 개수는 이름이라 어느 언어에서도 영어다(D-049). 1이면 단수. */
    @Test
    void namesAreEnglishWithSingularForOne() {
        assertEquals("No messages", Messages.get("messages.count", 0));
        assertEquals("1 message", Messages.get("messages.count", 1));
        assertEquals("3 messages", Messages.get("messages.count", 3));
        assertEquals("Messages", Messages.get("messages.tab"));
        assertFalse(Messages.get("messages.none").startsWith("messages."), "empty-state sentence exists");
    }
}
