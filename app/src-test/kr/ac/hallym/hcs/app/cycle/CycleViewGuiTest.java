/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.gui.GuiTestSupport;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.record.Recorder;
import kr.ac.hallym.hcs.app.record.Recording;
import kr.ac.hallym.hcs.app.record.RecordingTestSupport;

/**
 * C-02·C-03 GUI({@code xvfb-run -a ./gradlew :app:guiTest}): 캔버스 아래 Cycles 탭. ref-mips가 busy-loop.s를 도는
 * 동안 열이 사이클마다 쌓이고, 터널 우클릭 "Add to Cycle View"로 줄이 더해지고, 열을 누르면 회로도(프로젝트 상태)와
 * 상태 표시줄이 그 사이클로 바뀌며, Latest Cycle로 돌아온다.
 */
@Tag("gui")
class CycleViewGuiTest {
    @TempDir
    Path tmp;

    static void waitFor(BooleanSupplier ok, String what) throws Exception {
        long end = System.currentTimeMillis() + 15_000;
        while (true) {
            AtomicReference<Boolean> r = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> r.set(ok.getAsBoolean()));
            if (r.get()) {
                return;
            }
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("timed out: " + what);
            }
            Thread.sleep(20);
        }
    }

    static int pcOf(Project proj, Component pcTunnel) {
        CircuitState s = proj.getCircuitState();
        while (s.getParentState() != null) {
            s = s.getParentState();
        }
        return s.getValue(pcTunnel.getEnd(0).getLocation()).toIntValue();
    }

    @Test
    void cyclesTabRecordsAndGoesBack() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = RecordingTestSupport.openRefMips(tmp);
        RecordingTestSupport.load(file, RecordingTestSupport.program("record/busy-loop.s"));
        Project proj = new Project(file);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Frame f = new Frame(proj);
            proj.setFrame(f);
            f.setVisible(true);
            f.setBounds(0, 0, 1400, 900);
            fr.set(f);
        });
        Frame frame = fr.get();
        try {
            CycleView view = CycleView.of(proj);
            // 탭이 캔버스 아래에 있다
            JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, view.component());
            assertNotNull(tabs);
            assertTrue(tabs.indexOfTab(Messages.get("cycle.tab")) >= 0);

            Recorder.requestReset(proj);
            waitFor(() -> Recorder.of(proj).current() != null && Recorder.of(proj).current().last() == 0, "reset");
            kr.ac.hallym.hcs.app.sim.SimControls.runCycles(proj, 12);
            waitFor(() -> Recorder.of(proj).current().last() == 24, "12 cycles recorded");
            Recording r = Recorder.of(proj).current();

            // 터널 pc를 줄로 더한다(오른쪽 클릭 메뉴와 같은 호출)
            Circuit main = file.getMainCircuit();
            Component pcTunnel = null;
            for (Component c : main.getNonWires()) {
                if (c.getFactory().getName().equals("Tunnel") && "pc".equals(Names.label(c))) {
                    pcTunnel = c;
                }
            }
            Component pcT = pcTunnel;
            SwingUtilities.invokeAndWait(() -> CycleView.addPort(proj, main, pcT.getEnd(0).getLocation()));
            waitFor(() -> tabs.getSelectedComponent() == view.component(), "adding a row shows the Cycles tab");
            assertEquals(1, view.signals().size());
            assertEquals("pc", view.signals().get(0).name);
            CycleModel m = view.model();
            assertEquals(12, m.lastCycle());
            assertEquals(Messages.get("cycle.position", 12, 12), view.positionLabel().getText());
            assertFalse(view.noticeLabel().isVisible());
            int pcNow = pcOf(proj, pcTunnel);

            // 열 5를 누른다: 회로도가 사이클 5의 값으로, 상태 표시줄도 Cycle 5
            int x5 = CycleView.x(m, 5) + CycleView.COL_W / 2;
            SwingUtilities.invokeAndWait(() -> view.body().dispatchEvent(new MouseEvent(view.body(),
                    MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, x5, 5, 1,
                    false, MouseEvent.BUTTON1)));
            waitFor(() -> r.cursor() == 10, "cursor at step 10");
            assertEquals(m.pc(5).toIntValue(), pcOf(proj, pcTunnel), "the canvas shows cycle 5");
            assertTrue(view.noticeLabel().isVisible(), "past-cycle notice");
            assertEquals(Messages.get("cycle.position", 5, 12), view.positionLabel().getText());
            waitFor(() -> statusNow(frame).contains(Messages.get("bar.cycleCount", 5)), "status bar shows Cycle 5");
            assertEquals(24, r.last(), "the later cycles are kept while viewing");

            // 왼쪽 화살표 = 이전 사이클
            SwingUtilities.invokeAndWait(() -> view.step(-1));
            waitFor(() -> r.cursor() == 8, "previous cycle");
            // Latest Cycle: 원래 상태로
            SwingUtilities.invokeAndWait(view::showLatest);
            waitFor(() -> r.cursor() == 24, "back to the latest");
            assertEquals(pcNow, pcOf(proj, pcTunnel));
            assertFalse(view.noticeLabel().isVisible());

            // 지난 사이클에서 Next Cycle 두 번은 보기만, 마지막에서 Next Cycle은 한 사이클 실행
            SwingUtilities.invokeAndWait(() -> view.view(10));
            waitFor(() -> r.cursor() == 20, "cycle 10");
            SwingUtilities.invokeAndWait(() -> view.step(+1));
            SwingUtilities.invokeAndWait(() -> view.step(+1));
            waitFor(() -> r.cursor() == 24, "stepping forward through the record");
            assertEquals(24, r.last());
            SwingUtilities.invokeAndWait(() -> view.step(+1));
            waitFor(() -> r.last() == 26 && r.cursor() == 26, "Next Cycle at the latest runs a cycle");
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    /** factorial(Stack·Console)을 창에서 돌린 뒤 지난 사이클을 봐도 기록 뒤쪽이 그대로다. */
    @Test
    void viewingAPastCycleOfFactorialKeepsTheRecord() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = RecordingTestSupport.openRefMips(tmp);
        RecordingTestSupport.load(file, RecordingTestSupport.program("mips/factorial.s"));
        Project proj = new Project(file);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Frame f = new Frame(proj);
            proj.setFrame(f);
            f.setVisible(true);
            f.setBounds(0, 0, 1400, 900);
            fr.set(f);
        });
        Frame frame = fr.get();
        try {
            CycleView view = CycleView.of(proj);
            Recorder.requestReset(proj);
            waitFor(() -> Recorder.of(proj).current() != null && Recorder.of(proj).current().last() == 0, "reset");
            Thread.sleep(500);
            for (int i = 0; i < 60; i++) {
                SwingUtilities.invokeAndWait(() -> proj.getSimulator().tick());
                Thread.sleep(8);
            }
            waitFor(() -> Recorder.of(proj).current().last() == 60, "30 cycles recorded");
            Recording r = Recorder.of(proj).current();
            for (int st : new int[] {24, 10, 3}) {
                com.cburch.logisim.circuit.CircuitState again = r.reconstruct(st);
                assertEquals(null, r.firstDifference(again, st), "replay equals the record at " + st);
            }
            SwingUtilities.invokeAndWait(() -> view.view(12));
            Thread.sleep(600);
            assertEquals(60, r.last(), "the record is kept");
            assertEquals(24, r.cursor());
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    /** GUI 스레드에서 부른다: 보이는 모든 라벨 글. */
    static String statusNow(Frame frame) {
        StringBuilder sb = new StringBuilder();
        collect(frame.getContentPane(), sb);
        return sb.toString();
    }

    static void collect(java.awt.Container c, StringBuilder sb) {
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof javax.swing.JLabel && k.isShowing()) {
                sb.append(((javax.swing.JLabel) k).getText()).append(" | ");
            }
            if (k instanceof java.awt.Container) {
                collect((java.awt.Container) k, sb);
            }
        }
    }
}
