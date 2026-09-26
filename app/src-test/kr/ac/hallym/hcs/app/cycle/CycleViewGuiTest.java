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

import javax.swing.JComponent;
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
 * C-02·C-03 GUI({@code xvfb-run -a ./gradlew :app:guiTest}): 캔버스 아래 Cycle View 탭. ref-mips가 busy-loop.s를 도는
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
            // 마지막 사이클을 따라갈 때 표 왼쪽 끝은 열 경계(잘린 열 조각이 없다)
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(300);
            AtomicReference<Integer> vx = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> vx.set(view.viewX()));
            assertEquals(0, vx.get() % CycleView.COL_W, "view x " + vx.get());
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

    /** Run Until 단추(C-04): 도는 동안 Stop, 끝나면 되돌아오고 상태 표시줄 알림에 멈춘 사이클과 이유. */
    @Test
    void runUntilButtonRunsAndReports() throws Exception {
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
            Thread.sleep(400);
            AtomicReference<RunUntilRunner> run = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> run.set(view.start(RunUntil.instruction("jal", 1000))));
            assertNotNull(run.get());
            waitFor(() -> !view.isRunningUntil(), "finished");
            Recording r = Recorder.of(proj).current();
            CycleModel m = view.model();
            int c = m.cursorCycle();
            assertEquals("jal", MipsText.mnemonic(m.instruction(c).toIntValue()));
            waitFor(() -> String.valueOf(kr.ac.hallym.hcs.app.sim.SimControls.lastNotice(proj)).equals(
                    Messages.get("runUntil.met", c, Messages.get("runUntil.why.INSTRUCTION", "jal"))), "notice");
            assertEquals(CycleModel.stepOf(c), r.last());
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    Frame show(Project proj) throws Exception {
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Frame f = new Frame(proj);
            proj.setFrame(f);
            f.setVisible(true);
            f.setBounds(0, 0, 1400, 900);
            fr.set(f);
        });
        return fr.get();
    }

    /** C-05: demo의 regfile을 표시하면 역할별 묶음과 $1~$3 값, 누르면 주 진법이 바뀐다. */
    @Test
    void registerPanelWithAMarkedRegisterFile() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = RecordingTestSupport.openCirc(tmp, "demo-datapath.circ");
        Project proj = new Project(file);
        Frame frame = show(proj);
        try {
            CycleView view = CycleView.of(proj);
            Circuit rf = file.getCircuit("regfile");
            SwingUtilities.invokeAndWait(() -> proj.doAction(RegisterFile.markAction(file, rf, true)));
            Recorder.requestReset(proj);
            waitFor(() -> Recorder.of(proj).current() != null && Recorder.of(proj).current().last() == 0, "reset");
            kr.ac.hallym.hcs.app.sim.SimControls.runCycles(proj, 3);
            waitFor(() -> Recorder.of(proj).current().last() == 6, "3 cycles");
            SwingUtilities.invokeAndWait(view::refresh);
            java.util.List<RegisterPanel.Line> lines = view.registerPanel().lines();
            java.util.List<String> heads = new java.util.ArrayList<>();
            java.util.List<String> regs = new java.util.ArrayList<>();
            for (RegisterPanel.Line l : lines) {
                if (l.head != null) {
                    heads.add(l.head);
                } else if (l.reg != null) {
                    regs.add(l.reg.name);
                }
            }
            assertEquals(java.util.Arrays.asList(Messages.get("regs.group.special"),
                    Messages.get("regs.group.Return_values"), Messages.get("regs.group.Arguments"),
                    Messages.get("regs.group.Temporaries"), Messages.get("regs.group.Saved"),
                    Messages.get("regs.group.Pointers"), Messages.get("regs.group.Reserved")), heads);
            assertEquals(33, regs.size(), "PC and $0..$31");
            MachineState.Reg at = null;
            for (RegisterPanel.Line l : lines) {
                if (l.reg != null && "$at".equals(l.reg.name)) {
                    at = l.reg;
                }
            }
            assertNotNull(at.value, "$1 is the regfile register labelled $1");
            MachineState.Reg zero = lines.stream().filter(l -> l.reg != null && "$zero".equals(l.reg.name))
                    .findFirst().get().reg;
            assertEquals(null, zero.value, "no register component for $0 in this regfile");
            // 누를 때마다 주 진법이 16진 → 10진 → 2진
            int row = lines.indexOf(lines.stream().filter(l -> l.reg != null && "$at".equals(l.reg.name))
                    .findFirst().get());
            for (RegisterPanel.Radix want : new RegisterPanel.Radix[] {RegisterPanel.Radix.DEC,
                RegisterPanel.Radix.BIN, RegisterPanel.Radix.HEX}) {
                SwingUtilities.invokeAndWait(() -> view.registerPanel().dispatchEvent(new MouseEvent(
                        view.registerPanel(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                        InputEvent.BUTTON1_DOWN_MASK, 40, row * RegisterPanel.ROW_H + 5, 1, false,
                        MouseEvent.BUTTON1)));
                assertEquals(want, view.registerPanel().radixOf("$at"));
            }
            // 표시를 되돌리면 모든 레지스터를 나열한다
            SwingUtilities.invokeAndWait(proj::undoAction);
            waitFor(() -> view.registerHintShown() && view.registerPanel().isListMode(), "unmarked list with the hint");
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    /** C-06: factorial이 가장 깊을 때 Stack 칸에 $sp 화살표, 머리에 깊이. 레지스터 줄 머리에 $sp와 깊이. */
    @Test
    void memoryPanelShowsTheStack() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = RecordingTestSupport.openRefMips(tmp);
        RecordingTestSupport.load(file, RecordingTestSupport.program("mips/factorial.s"));
        Project proj = new Project(file);
        Frame frame = show(proj);
        try {
            CycleView view = CycleView.of(proj);
            Recorder.requestReset(proj);
            waitFor(() -> Recorder.of(proj).current() != null && Recorder.of(proj).current().last() == 0, "reset");
            Thread.sleep(300);
            // fact의 첫 bne: $sp를 8 내리고 $ra, $a0를 쌓은 뒤(li는 의사 명령어라 기계어 이름이 아니다)
            AtomicReference<RunUntilRunner> run = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> run.set(view.start(RunUntil.instruction("bne", 1000))));
            waitFor(() -> !view.isRunningUntil(), "until bne");
            SwingUtilities.invokeAndWait(view::refresh);
            MemoryPanel.Line head = null;
            MemoryPanel.Line arrow = null;
            for (MemoryPanel.Line l : view.memoryPanel().lines()) {
                if (l.word == null && l.memory.stack) {
                    head = l;
                }
                if (l.word != null && l.word.sp) {
                    arrow = l;
                }
            }
            assertNotNull(head, "a Stack section");
            assertEquals(8, head.memory.depth, "one frame of 8 bytes");
            assertEquals(8, head.memory.peak);
            assertNotNull(arrow, "the $sp arrow");
            RegisterPanel.Line first = view.registerPanel().lines().get(0);
            assertEquals(Messages.get("regs.spDepth", RegisterPanel.hex(view.machine().sp(view.model().cursorCycle())),
                    head.memory.depth), first.text);
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    /** C-07: Instruction 줄을 누르면 Instruction 탭이 열리고, 보이는 동안만 캔버스에 그 명령어의 필드 색이 겹친다. */
    @Test
    void instructionTabShowsFieldColors() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = RecordingTestSupport.openCirc(tmp, "demo-datapath.circ");
        // 스플리터 팔 이름은 hcs 확장에 있다(파일을 열 때처럼 읽는다)
        kr.ac.hallym.hcs.app.ext.CircExtensions.afterOpen(file, file.getLoader().getMainFile());
        Project proj = new Project(file);
        Frame frame = show(proj);
        try {
            CycleView view = CycleView.of(proj);
            SwingUtilities.invokeAndWait(view::open);
            Recorder.requestReset(proj);
            waitFor(() -> Recorder.of(proj).current() != null && Recorder.of(proj).current().last() == 0, "reset");
            kr.ac.hallym.hcs.app.sim.SimControls.runCycles(proj, 2);
            waitFor(() -> Recorder.of(proj).current().last() == 4, "2 cycles");
            SwingUtilities.invokeAndWait(view::refresh);
            Circuit main = file.getMainCircuit();
            assertEquals(null, FieldOverlay.shown(proj, main), "no colors while Registers is shown");
            SwingUtilities.invokeAndWait(() -> {
                JComponent head = view.headComponent();
                int x = (view.model().cursorCycle() - view.model().firstCycle()) * CycleView.COL_W + 6;
                head.dispatchEvent(new MouseEvent(head, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                        InputEvent.BUTTON1_DOWN_MASK, x, 2 * CycleView.ROW_H + 5, 1, false, MouseEvent.BUTTON1));
            });
            assertEquals(CycleView.INSPECT_TAB, view.sideTabs().getSelectedIndex());
            Integer word = view.instructionPanel().word();
            assertNotNull(word, "an instruction in the viewed cycle");
            java.util.Map<String, java.util.Set<com.cburch.logisim.circuit.Wire>> shown = FieldOverlay.shown(proj,
                    main);
            assertNotNull(shown, "overlay shown; panel showing=" + view.component().isShowing());
            assertTrue(FieldPaths.fieldsOf(word).containsAll(shown.keySet()), shown.keySet().toString());
            assertTrue(shown.containsKey("rs"), "the rs arm is named in demo-datapath");
            assertEquals(FieldPaths.fieldsOf(word).size(), InstructionPanel.cells(word).size());
            SwingUtilities.invokeAndWait(() -> view.showSide(0));
            assertEquals(null, FieldOverlay.shown(proj, main), "colors go away with the tab");
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    /**
     * C-08: 사이클 뷰가 보이는 동안 Active Path 덧그림이 켜지고(체크를 끄면 꺼진다), 시뮬레이션 중 버스 값 칩이 더해진다
     * (진법을 끄면 이름 칩만).
     */
    @Test
    void activePathAndBusValues() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = RecordingTestSupport.openCirc(tmp, "demo-datapath.circ");
        Project proj = new Project(file);
        Frame frame = show(proj);
        kr.ac.hallym.hcs.app.labels.BusValues.Mode before = kr.ac.hallym.hcs.app.labels.BusValues.mode();
        try {
            CycleView view = CycleView.of(proj);
            SwingUtilities.invokeAndWait(view::open);
            Recorder.requestReset(proj);
            waitFor(() -> Recorder.of(proj).current() != null && Recorder.of(proj).current().last() == 0, "reset");
            kr.ac.hallym.hcs.app.sim.SimControls.runCycles(proj, 2);
            waitFor(() -> Recorder.of(proj).current().last() == 4, "2 cycles");
            SwingUtilities.invokeAndWait(view::refresh);
            assertTrue(ActivePathOverlay.isShown(proj), "on by default while the cycle view is shown");
            assertFalse(ActivePathOverlay.selected(file.getMainCircuit(), proj.getCircuitState()).isEmpty(),
                    "MemtoReg selects an input");
            SwingUtilities.invokeAndWait(view.activePathBox()::doClick);
            assertFalse(ActivePathOverlay.isShown(proj));
            SwingUtilities.invokeAndWait(view.activePathBox()::doClick);
            assertTrue(ActivePathOverlay.isShown(proj));

            com.cburch.logisim.gui.main.Canvas canvas = frame.getCanvas();
            java.util.function.IntSupplier chips = () -> {
                try {
                    SwingUtilities.invokeAndWait(() -> canvas.paintImmediately(canvas.getVisibleRect()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return kr.ac.hallym.hcs.app.labels.LabelOverlay.chipRects(canvas).size();
            };
            kr.ac.hallym.hcs.app.labels.BusValues.setMode(kr.ac.hallym.hcs.app.labels.BusValues.Mode.OFF);
            int off = chips.getAsInt();
            kr.ac.hallym.hcs.app.labels.BusValues.setMode(kr.ac.hallym.hcs.app.labels.BusValues.Mode.HEX);
            int hex = chips.getAsInt();
            assertTrue(hex > off, "value chips on unnamed buses: " + off + " -> " + hex);
        } finally {
            kr.ac.hallym.hcs.app.labels.BusValues.setMode(before);
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    /**
     * D-01·D-03·D-05: 서브회로 datapath 안에서 꺼진 버퍼가 레지스터 en을 떠 있게 하면, 시뮬레이션 중 Messages에 그
     * 사이클과 원인이 한 줄로 나오고, 누르면 사이클 뷰가 그 사이클로 가고 datapath 인스턴스로 들어가 원인을 고른다.
     */
    @Test
    void dynamicMessageGoesToItsCycleAndCause() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = kr.ac.hallym.hcs.regress.CircuitBuilder.newFile(new com.cburch.logisim.file.Loader(null),
                java.nio.file.Files.createTempDirectory(tmp, "dyn").toFile());
        Circuit dp = new Circuit("datapath");
        file.addCircuit(dp);
        kr.ac.hallym.hcs.regress.CircuitBuilder b = new kr.ac.hallym.hcs.regress.CircuitBuilder(file, dp);
        Component clock = b.add("Wiring", "Clock", 100, 400);
        b.tunnel(clock, 0, "clk");
        Component buf = b.add("Gates", "Controlled Buffer", 300, 300);
        b.constant("one", 1, 1, 100, 100);
        b.constant("off", 1, 0, 100, 200);
        b.tunnel(buf, 1, "one");
        b.tunnel(buf, 2, "off");
        b.tunnel(buf, 0, "we");
        Component reg = b.add("Memory", "Register", 500, 200, "width", "8", "label", "R1");
        b.constant("d", 8, 7, 100, 500);
        b.tunnel(reg, 1, "d");
        b.tunnel(reg, 2, "clk");
        b.tunnel(reg, 4, "we");
        b.tunnel(reg, 0, "q");
        b.output("q", 8, 700, 100);
        b.commit();
        kr.ac.hallym.hcs.regress.CircuitBuilder m = new kr.ac.hallym.hcs.regress.CircuitBuilder(file,
                file.getMainCircuit());
        Component inst = m.addSubcircuit(dp, 400, 300);
        m.tunnel(inst, 0, "q");
        m.output("q", 8, 700, 100);
        m.commit();
        Project proj = new Project(file);
        Frame frame = show(proj);
        try {
            kr.ac.hallym.hcs.app.diag.Diagnostics diags = kr.ac.hallym.hcs.app.diag.Diagnostics.of(proj);
            assertEquals(java.util.List.of(), diags.list(), "no static message: a buffer may float");
            CycleView view = CycleView.of(proj);
            Recorder.requestReset(proj);
            waitFor(() -> Recorder.of(proj).current() != null && Recorder.of(proj).current().last() == 0, "reset");
            kr.ac.hallym.hcs.app.sim.SimControls.runCycles(proj, 3);
            waitFor(() -> Recorder.of(proj).current().last() == 6, "3 cycles");
            waitFor(() -> diags.list().size() == 1, "one dynamic message");
            kr.ac.hallym.hcs.app.diag.Diagnostic d = diags.list().get(0);
            assertEquals(kr.ac.hallym.hcs.app.diag.Diagnostic.Kind.X_WRITE_CONTROL, d.kind);
            assertEquals(java.util.List.of(inst), d.instances);
            assertTrue(d.message().contains("main › datapath › R1"), d.message());
            assertTrue(d.components.contains(buf) && d.components.contains(reg), d.components.toString());
            SwingUtilities.invokeAndWait(() -> diags.go(d));
            assertEquals(0, view.model().cursorCycle(), "the cycle of the write");
            assertEquals(dp, proj.getCurrentCircuit(), "inside the datapath instance");
            assertTrue(proj.getCircuitState().getParentState() != null, "an instance state, not a fresh one");
            assertTrue(proj.getSelection().getComponents().contains(buf), "the cause is selected");
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    /** C-09: Console 탭은 exit까지 모든 출력, .s를 고쳐 저장하면 1.5초 안에 다시 불러오고 알린다. */
    @Test
    void consoleTabAndReloadWatcher() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = RecordingTestSupport.openRefMips(tmp);
        java.io.File circ = file.getLoader().getMainFile();
        java.io.File s = new java.io.File(circ.getParentFile(), "factorial.s");
        java.nio.file.Files.copy(RecordingTestSupport.program("mips/factorial.s"), s.toPath());
        RecordingTestSupport.load(file, s.toPath());
        Circuit main = file.getMainCircuit();
        com.cburch.logisim.circuit.CircuitMutation mut = new com.cburch.logisim.circuit.CircuitMutation(main);
        for (Component c : main.getNonWires()) {
            String f = c.getFactory().getName();
            if (f.equals("Instruction Memory") || f.equals("Data Memory")) {
                @SuppressWarnings("unchecked")
                com.cburch.logisim.data.Attribute<Object> a = (com.cburch.logisim.data.Attribute<Object>) c
                        .getAttributeSet().getAttribute("source");
                mut.set(c, a, "factorial.s");
            }
        }
        mut.execute();
        Project proj = new Project(file);
        Frame frame = show(proj);
        try {
            CycleView view = CycleView.of(proj);
            JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, view.component());
            assertTrue(tabs.indexOfTab(Messages.get("console.tab")) >= 0, "Console tab below the canvas");
            Recorder.requestReset(proj);
            waitFor(() -> Recorder.of(proj).current() != null && Recorder.of(proj).current().last() == 0, "reset");
            Thread.sleep(300);
            SwingUtilities.invokeAndWait(() -> view.start(RunUntil.halt(2000)));
            waitFor(() -> !view.isRunningUntil(), "until exit");
            SwingUtilities.invokeAndWait(view::refresh);
            assertEquals("6! = 720\n-- exit --\n", view.consolePanel().text());

            // .s를 고쳐 저장: 감시가 다시 불러오고 알린다
            String text = new String(java.nio.file.Files.readAllBytes(s.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8).replace("li    $a0, 6", "li    $a0, 5");
            java.nio.file.Files.write(s.toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            s.setLastModified(s.lastModified() + 2000);
            waitFor(() -> Messages.get("reload.done", "factorial.s").equals(
                    kr.ac.hallym.hcs.app.sim.SimControls.lastNotice(proj)), "reload notice");
            // 알림은 상태 표시줄 한 줄뿐, 모달 창은 없다
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                assertFalse(w instanceof java.awt.Dialog && w.isShowing(), "no dialog: " + w);
            }

            // 리셋할 때도 다시 본다(리셋 전 훅): 파일을 고치고 같은 GUI 스레드 차례 안에서 리셋하면, 감시 타이머가
            // 끼어들 틈 없이 곧바로 내용이 바뀐다
            Component imem = null;
            for (Component c : main.getNonWires()) {
                if (c.getFactory().getName().equals("Instruction Memory")) {
                    imem = c;
                }
            }
            @SuppressWarnings("unchecked")
            com.cburch.logisim.data.Attribute<Object> contents = (com.cburch.logisim.data.Attribute<Object>) imem
                    .getAttributeSet().getAttribute("contents");
            Component im = imem;
            AtomicReference<String[]> seen = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    String before = contents.toStandardString(im.getAttributeSet().getValue(contents));
                    String t = new String(java.nio.file.Files.readAllBytes(s.toPath()),
                            java.nio.charset.StandardCharsets.UTF_8).replace("li    $a0, 5", "li    $a0, 4");
                    java.nio.file.Files.write(s.toPath(), t.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    s.setLastModified(s.lastModified() + 4000);
                    Recorder.requestReset(proj);
                    seen.set(new String[] {before,
                        contents.toStandardString(im.getAttributeSet().getValue(contents))});
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            });
            assertFalse(seen.get()[0].equals(seen.get()[1]), "reset reloads the changed .s at once");
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    /** GUI 스레드에서 부른다: 보이는 모든 라벨 글. */
    /**
     * V-03: 메시지를 누르면(31장면 흐름: demo-datapath의 RegWrite 핀을 3상태로 두면 regfile 안 AND에 E) 사이클 표 맨 위에
     * 원인 신호(RegWrite)와 E가 생긴 자리의 임시 줄이 생기고 그 사이클 열이 선택된다. 임시 줄은 관찰 목록에 들어가지 않고
     * ×로 걷힌다.
     */
    @Test
    void clickingAMessagePinsTheCauseAndTheErrorSpot() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        LogisimFile file = RecordingTestSupport.openCirc(tmp, "demo-datapath.circ");
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
            Circuit main = file.getMainCircuit();
            Component pin = null;
            for (Component c : main.getNonWires()) {
                if (c.getFactory().getName().equals("Pin") && "RegWrite".equals(Names.label(c))) {
                    pin = c;
                }
            }
            assertNotNull(pin);
            Component rw = pin;
            SwingUtilities.invokeAndWait(() -> {
                com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(main);
                m.set(rw, com.cburch.logisim.std.wiring.Pin.ATTR_TRISTATE, Boolean.TRUE);
                proj.doAction(m.toAction(null));
            });
            Recorder.requestReset(proj);
            waitFor(() -> Recorder.of(proj).current() != null && Recorder.of(proj).current().last() == 0, "reset");
            kr.ac.hallym.hcs.app.sim.SimControls.runCycles(proj, 3);
            waitFor(() -> Recorder.of(proj).current().last() == 6, "3 cycles recorded");
            kr.ac.hallym.hcs.app.diag.Diagnostics diags = kr.ac.hallym.hcs.app.diag.Diagnostics.of(proj);
            waitFor(() -> diags.list().stream().anyMatch(d -> d.kind == kr.ac.hallym.hcs.app.diag.Diagnostic.Kind.E_APPEARED),
                    "an E message");
            kr.ac.hallym.hcs.app.diag.Diagnostic d = diags.list().stream()
                    .filter(x -> x.kind == kr.ac.hallym.hcs.app.diag.Diagnostic.Kind.E_APPEARED).findFirst().get();
            assertNotNull(d.appeared(), "the message knows where the E appeared");

            CycleView view = CycleView.of(proj);
            assertEquals(0, view.signals().size());
            SwingUtilities.invokeAndWait(() -> diags.go(d));
            SwingUtilities.invokeAndWait(() -> { });
            java.util.List<String> names = new java.util.ArrayList<>();
            for (CycleModel.Signal s : view.pinnedSignals()) {
                names.add(s.name);
            }
            assertEquals(2, names.size(), names.toString());
            assertEquals("RegWrite", names.get(0), "the cause signal first");
            assertTrue(names.get(1).startsWith("regfile › "), "then where the E appeared, inside regfile: " + names.get(1));
            int cycle = kr.ac.hallym.hcs.app.diag.DynamicCheck.cycleOf(d.step);
            assertEquals(cycle, view.pinnedCycle());
            assertEquals(cycle, view.model().cursorCycle(), "the table shows the message's cycle");
            assertTrue(view.rows().get(0).pinned && view.rows().get(1).pinned, "temporary rows sit on top");
            assertEquals(0, view.signals().size(), "not in the saved observation list");

            // ×를 누르면 걷힌다
            int xClose = CycleView.NAME_W - 10;
            SwingUtilities.invokeAndWait(() -> view.rowNames().dispatchEvent(new MouseEvent(view.rowNames(),
                    MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, xClose, 5, 1,
                    false, MouseEvent.BUTTON1)));
            waitFor(() -> view.pinnedSignals().isEmpty(), "unpinned");
            assertEquals(0, view.rows().size());
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

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
