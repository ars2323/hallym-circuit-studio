/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

/**
 * 스크린샷 시나리오 실행기(docs/SCREENSHOTS.md). 앱을 같은 JVM에서 띄우고, 회로 모델에서 계산한 화면 좌표로 Robot
 * 마우스·키를 보내고, 앱 창 영역만 찍는다(가상 화면 1920×1080, 창 관리자 없음). 같은 코드로 원조 2.7.1 jar도
 * 띄워 같은 장면을 {@code -orig}로 찍는다(포크 전용 코드는 원조 모드에서 부르지 않는다).
 *
 * <p>쓰기: {@code java -cp <shots>:<jar> Shots <fork|orig> <출력 폴더> [장면 번호...]}. 작업 폴더는 저장소 루트이고
 * 회로는 상대 경로로 연다(창 제목에 개인 경로가 나오지 않게).
 */
public final class Shots {
    static final int W = 1920;
    static final int H = 1080;
    static final int FULL_WIDTH = 1600;
    static final long MAX_BYTES = 1_000_000;

    final boolean orig;
    final File out;
    final Robot robot;
    final List<String> log = new ArrayList<>();

    Shots(boolean orig, File out) throws AWTException {
        this.orig = orig;
        this.out = out;
        this.robot = new Robot();
        robot.setAutoDelay(40);
    }

    public static void main(String[] args) throws Exception {
        boolean orig = args[0].equals("orig");
        File out = new File(args[1]);
        out.mkdirs();
        List<String> scenes = new ArrayList<>(Arrays.asList(args).subList(2, args.length));
        Shots s = new Shots(orig, out);
        int code = 0;
        try {
            s.run(scenes);
        } catch (Throwable t) {
            t.printStackTrace();
            s.log.add("FAILED: " + t);
            code = 1;
        } finally {
            try (PrintWriter w = new PrintWriter(new File(out, orig ? "log-orig.txt" : "log-fork.txt"),
                    StandardCharsets.UTF_8.name())) {
                for (String l : s.log) {
                    w.println(l);
                }
            }
        }
        Runtime.getRuntime().halt(code);
    }

    boolean want(List<String> scenes, String id) {
        return scenes.isEmpty() || scenes.contains(id);
    }

    void run(List<String> scenes) throws Exception {
        if (orig) {
            // 원조 2.7.1: 참조 회로 전체와 200% 부분(라벨 칩 비교용)
            launch("ref-mips.circ");
            Project p = project();
            setZoom(p, 1.0);
            scrollTo(p, 0, 0);
            snapFull("02-ref-mips-100-orig");
            setZoom(p, 0.25);
            scrollTo(p, 0, 0);
            snapFull("02b-ref-mips-25-orig");
            zoomCrops(p, "orig");
            return;
        }
        if (want(scenes, "01")) {
            launch();
            sleep(1500);
            closeDialogs();
            snapFull("01-first-screen");
        } else {
            launch();
            closeDialogs();
        }
        Project ref = open("tests/mips/ref-mips.circ");
        if (want(scenes, "02")) {
            setZoom(ref, 1.0);
            scrollTo(ref, 0, 0);
            snapFull("02-ref-mips-100");
            setZoom(ref, 0.25); // 포크의 최소 배율(D-028), 원조도 같은 배율로
            scrollTo(ref, 0, 0);
            snapFull("02b-ref-mips-25");
            setZoom(ref, 1.0);
        }
        if (want(scenes, "03")) {
            zoomCrops(ref, "");
            Project sub = open("tests/circ/subcircuit.circ");
            setZoom(sub, 2.0);
            Bounds b = firstSubcircuit(sub);
            if (b != null) {
                centerOn(sub, b);
                snapLogical(sub, b.expand(60), "03d-subcircuit-200");
            }
            activate(ref);
        }
        if (want(scenes, "04")) {
            contextMenus(ref);
        }
        if (want(scenes, "05")) {
            quickAttrs(ref);
        }
        if (want(scenes, "06")) {
            palette(ref);
        }
        if (want(scenes, "07")) {
            bars(ref);
        }
        if (want(scenes, "08")) {
            splitterEditor(ref);
        }
        if (want(scenes, "09")) {
            find(ref);
        }
        if (want(scenes, "12")) {
            hover(ref);
        }
        if (want(scenes, "13")) {
            keysTable(ref);
        }
        if (want(scenes, "11")) {
            program(ref);
        }
        if (want(scenes, "10")) {
            open("tests/circ/register.circ");
            open("tests/circ/values.circ");
            Project last = open("tests/circ/gates.circ");
            sleep(800);
            snapFull("10-file-tabs");
            Rectangle top = new Rectangle(0, 0, W, 170);
            snapCrop(top, "10-file-tabs-top");
            activate(last);
        }
    }

    // ---- 장면 ----

    /** 3: 200% 부분 확대. 원조 모드도 같은 회로 영역을 찍는다(부품은 원조 API로 찾는다). */
    void zoomCrops(Project p, String suffix) throws Exception {
        setZoom(p, 2.0);
        String s = suffix.isEmpty() ? "" : "-" + suffix;
        Circuit c = p.getCurrentCircuit();
        Object[][] targets = {
            {byLabel(c, "$29"), "03a-register-sp-200"},
            {byFactory(c, "Adder"), "03b-adder-200"},
            {byFactory(c, "Instruction Memory"), "03c-imem-tunnels-200"},
            {widestSplitter(c), "03e-splitter-bus-200"},
        };
        for (Object[] t : targets) {
            com.cburch.logisim.comp.Component x = (com.cburch.logisim.comp.Component) t[0];
            if (x == null) {
                log.add(t[1] + ": not found");
                continue;
            }
            Bounds area = around(x.getBounds(), 360, 220);
            centerOn(p, area);
            snapLogical(p, area, t[1] + s);
        }
        setZoom(p, 1.0);
    }

    static com.cburch.logisim.comp.Component widestSplitter(Circuit c) {
        com.cburch.logisim.comp.Component sp = null;
        for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals("Splitter") && x.getEnds().get(0).getWidth().getWidth() == 32
                    && (sp == null || x.getEnds().size() > sp.getEnds().size())) {
                sp = x;
            }
        }
        return sp;
    }

    /** 4: 우클릭 메뉴(포트, 게이트, 선, 빈 곳). 테스트 회로는 포트마다 터널이 붙어 있어 새 파일에 작은 회로를 만든다. */
    void contextMenus(Project base) throws Exception {
        Project p = newProject(base);
        edt(() -> {
            Library gates = p.getLogisimFile().getLoader().getBuiltin().getLibrary("Gates");
            Library wiring = p.getLogisimFile().getLoader().getBuiltin().getLibrary("Wiring");
            com.cburch.logisim.comp.ComponentFactory and =
                    ((com.cburch.logisim.tools.AddTool) gates.getTool("AND Gate")).getFactory();
            com.cburch.logisim.data.AttributeSet as = and.createAttributeSet();
            set(as, "inputs", "2");
            com.cburch.logisim.comp.Component g = and.createComponent(Location.create(400, 300), as);
            com.cburch.logisim.circuit.CircuitMutation m =
                    new com.cburch.logisim.circuit.CircuitMutation(p.getCurrentCircuit());
            m.add(g);
            Location in0 = g.getEnds().get(1).getLocation();
            Location out = g.getEnds().get(0).getLocation();
            m.add(Wire.create(in0, Location.create(in0.getX() - 100, in0.getY())));
            m.add(Wire.create(out, Location.create(out.getX() + 100, out.getY())));
            com.cburch.logisim.comp.ComponentFactory pin =
                    ((com.cburch.logisim.tools.AddTool) wiring.getTool("Pin")).getFactory();
            com.cburch.logisim.data.AttributeSet ps = pin.createAttributeSet();
            set(ps, "facing", "west");
            set(ps, "output", "true");
            set(ps, "label", "Y");
            m.add(pin.createComponent(Location.create(out.getX() + 100, out.getY()), ps));
            p.doAction(m.toAction(null));
        });
        useTool(p, "Edit Tool");
        setZoom(p, 1.5);
        com.cburch.logisim.comp.Component gate = byFactory(p.getCurrentCircuit(), "AND Gate");
        centerOn(p, gate.getBounds().expand(150));
        menuAt(p, gate.getEnds().get(2).getLocation(), "04a-menu-port");
        Bounds gb = gate.getBounds();
        menuAt(p, Location.create(gb.getX() + gb.getWidth() / 2, gb.getY() + gb.getHeight() / 2), "04b-menu-gate");
        Location in0 = gate.getEnds().get(1).getLocation();
        menuAt(p, Location.create(in0.getX() - 50, in0.getY()), "04c-menu-wire");
        menuAt(p, Location.create(gb.getX() + 60, gb.getY() + 200), "04d-menu-empty");
        setZoom(p, 1.0);
        activate(base);
    }

    /** 새 파일(탭)을 연다. */
    Project newProject(Project base) throws Exception {
        AtomicReference<Project> ref = new AtomicReference<>();
        edt(() -> ref.set(ProjectActions.doNew(base)));
        sleep(2500);
        Project p = ref.get();
        edt(() -> {
            p.getFrame().setBounds(0, 0, W, H);
            p.getFrame().validate();
        });
        activate(p);
        return p;
    }

    void menuAt(Project p, Location at, String name) throws Exception {
        Point s = screen(p, at);
        robot.mouseMove(s.x, s.y);
        sleep(200);
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        sleep(900);
        Rectangle r = popupBounds();
        if (r == null) {
            log.add(name + ": no popup");
        } else {
            r.add(new Rectangle(s.x - 60, s.y - 60, 120, 120));
            snapCrop(pad(r, 20), name);
        }
        key(KeyEvent.VK_ESCAPE);
        sleep(300);
    }

    /** 5: 빠른 속성 창 + 오른쪽 속성 패널(펼침·접힘). */
    void quickAttrs(Project p) throws Exception {
        setZoom(p, 1.5);
        useTool(p, "Edit Tool");
        com.cburch.logisim.comp.Component reg = byLabel(p.getCurrentCircuit(), "$29");
        if (reg == null) {
            reg = byFactory(p.getCurrentCircuit(), "Register");
        }
        final com.cburch.logisim.comp.Component target = reg;
        centerOn(p, target.getBounds().expand(120));
        deselect(p);
        edt(() -> p.getSelection().add(target));
        sleep(1200);
        snapFull("05a-quick-attrs-dock-open");
        Rectangle qb = screenRect(p, target.getBounds().expand(150));
        snapCrop(qb, "05b-quick-attrs-crop");
        AbstractButton collapse = (AbstractButton) find(p.getFrame(),
                x -> x instanceof AbstractButton && tip(x).contains("접기"));
        if (collapse != null) {
            edt(collapse::doClick);
            sleep(900);
            snapFull("05c-dock-collapsed");
            AbstractButton expand = (AbstractButton) find(p.getFrame(),
                    x -> x instanceof AbstractButton && tip(x).contains("펴기") && x.isShowing());
            if (expand != null) {
                edt(expand::doClick);
                sleep(600);
            }
        }
        deselect(p);
        setZoom(p, 1.0);
    }

    /** 6: 검색 팔레트 "mux 32", 명령 "리셋". */
    void palette(Project p) throws Exception {
        useTool(p, "Edit Tool");
        deselect(p);
        clickCanvas(p, emptySpot(p));
        keyCombo(KeyEvent.VK_CONTROL, KeyEvent.VK_K);
        sleep(900);
        Window w = window(x -> x.getClass().getSimpleName().equals("PaletteWindow") && x.isShowing());
        if (w == null) {
            log.add("06: no palette window");
            return;
        }
        JTextField q = (JTextField) find(w, x -> x instanceof JTextField);
        for (String[] t : new String[][] {{"mux 32", "06a-palette-mux32"}, {"리셋", "06b-palette-reset"}}) {
            edt(() -> q.setText(t[0]));
            sleep(900);
            snapCrop(pad(w.getBounds(), 16), t[1]);
        }
        key(KeyEvent.VK_ESCAPE);
        sleep(300);
    }

    /** 7: 도구 모음·상태 표시줄 부분. */
    void bars(Project p) throws Exception {
        Frame f = p.getFrame();
        Component tb = find(f, x -> x instanceof JToolBar && x.isShowing() && x.getWidth() > 800);
        if (tb != null) {
            Rectangle r = onScreen(tb);
            snapCrop(new Rectangle(r.x, r.y, Math.min(r.width, 1300), r.height), "07a-toolbar");
        }
        Component status = find(f, x -> x instanceof javax.swing.JLabel && ((javax.swing.JLabel) x).getText() != null
                && ((javax.swing.JLabel) x).getText().startsWith("사이클"));
        if (status != null) {
            Rectangle r = onScreen(status.getParent());
            snapCrop(new Rectangle(r.x, r.y, Math.min(r.width, 1300), r.height), "07b-status-bar");
        }
    }

    /** 8: 스플리터 편집기(R형 프리셋, 범위 입력)와 적용 뒤 팔 라벨. 새 파일에 32비트 스플리터를 놓고 한다. */
    void splitterEditor(Project base) throws Exception {
        Project p = newProject(base);
        edt(() -> {
            Library wiring = p.getLogisimFile().getLoader().getBuiltin().getLibrary("Wiring");
            com.cburch.logisim.comp.ComponentFactory f =
                    ((com.cburch.logisim.tools.AddTool) wiring.getTool("Splitter")).getFactory();
            com.cburch.logisim.data.AttributeSet as = f.createAttributeSet();
            set(as, "incoming", "32");
            set(as, "fanout", "4");
            com.cburch.logisim.circuit.CircuitMutation m =
                    new com.cburch.logisim.circuit.CircuitMutation(p.getCurrentCircuit());
            m.add(f.createComponent(Location.create(300, 200), as));
            p.doAction(m.toAction(null));
        });
        useTool(p, "Edit Tool");
        setZoom(p, 2.0);
        com.cburch.logisim.comp.Component sp = widestSplitter(p.getCurrentCircuit());
        centerOn(p, sp.getBounds().expand(80));
        snapLogical(p, sp.getBounds().expand(60), "08a-splitter-before-200");
        Point s = screen(p, Location.create(sp.getBounds().getX() + 3, sp.getBounds().getY() + 3));
        robot.mouseMove(s.x, s.y);
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        sleep(900);
        JMenuItem item = menuItem(x -> x.getText() != null && x.getText().startsWith("스플리터 편집"));
        if (item == null) {
            log.add("08: no editor item");
            key(KeyEvent.VK_ESCAPE);
            return;
        }
        SwingUtilities.invokeLater(item::doClick); // 모달 창을 여므로 기다리지 않는다
        sleep(1500);
        Window d = window(x -> x instanceof JDialog && x.isShowing()
                && x.getClass().getSimpleName().equals("SplitterEditor"));
        if (d == null) {
            log.add("08: no editor dialog");
            return;
        }
        JTextField ranges = (JTextField) find(d, x -> x instanceof JTextField && x.isShowing());
        edt(() -> {
            ranges.setText("31:26, 25:21, 20:16, 15:0");
            ranges.postActionEvent();
        });
        sleep(900);
        snapCrop(pad(d.getBounds(), 10), "08b-splitter-editor-ranges");
        @SuppressWarnings("unchecked")
        JComboBox<Object> preset = (JComboBox<Object>) find(d, x -> x instanceof JComboBox
                && comboContains((JComboBox<?>) x, "R형"));
        if (preset != null) {
            edt(() -> {
                for (int i = 0; i < preset.getItemCount(); i++) {
                    if (String.valueOf(preset.getItemAt(i)).contains("R형")) {
                        preset.setSelectedIndex(i);
                    }
                }
            });
            sleep(900);
            snapCrop(pad(d.getBounds(), 10), "08c-splitter-editor-r-type");
        }
        JButton apply = (JButton) find(d, x -> x instanceof JButton && "적용".equals(((JButton) x).getText()));
        if (apply != null) {
            SwingUtilities.invokeLater(apply::doClick);
            sleep(1500);
            com.cburch.logisim.comp.Component now = widestSplitter(p.getCurrentCircuit());
            centerOn(p, now.getBounds().expand(80));
            snapLogical(p, now.getBounds().expand(70), "08d-splitter-arm-labels-200");
        }
        setZoom(p, 1.0);
        activate(base);
    }

    @SuppressWarnings("unchecked")
    static void set(com.cburch.logisim.data.AttributeSet as, String name, String value) {
        com.cburch.logisim.data.Attribute<Object> a = (com.cburch.logisim.data.Attribute<Object>) as.getAttribute(name);
        as.setValue(a, a.parse(value));
    }

    /** 9: Ctrl+F 찾기와 터널 이름 목록. */
    void find(Project p) throws Exception {
        clickCanvas(p, emptySpot(p));
        keyCombo(KeyEvent.VK_CONTROL, KeyEvent.VK_F);
        sleep(1000);
        Window d = window(x -> x instanceof JDialog && x.isShowing() && x.getClass().getSimpleName().equals("FindDialog"));
        if (d == null) {
            log.add("09: no find dialog");
            return;
        }
        JTextField q = (JTextField) find(d, x -> x instanceof JTextField);
        edt(() -> q.setText("PC"));
        sleep(900);
        snapCrop(pad(d.getBounds(), 10), "09a-find-pc");
        JTabbedPane tabs = (JTabbedPane) find(d, x -> x instanceof JTabbedPane);
        edt(() -> tabs.setSelectedIndex(1));
        sleep(900);
        snapCrop(pad(d.getBounds(), 10), "09b-tunnel-names");
        edt(d::dispose);
        sleep(300);
    }

    /** 12: 마우스 오버(부품, 포트). */
    void hover(Project p) throws Exception {
        setZoom(p, 1.5);
        useTool(p, "Edit Tool");
        com.cburch.logisim.comp.Component gate = byFactory(p.getCurrentCircuit(), "AND Gate");
        centerOn(p, gate.getBounds().expand(120));
        clickCanvas(p, emptySpot(p)); // 창에 초점이 있어야 툴팁이 뜬다
        Bounds b = gate.getBounds();
        Point s = screen(p, Location.create(b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2));
        hoverAt(s, "12a-hover-component");
        Point pt = screen(p, gate.getEnds().get(1).getLocation());
        hoverAt(pt, "12b-hover-port");
        setZoom(p, 1.0);
    }

    void hoverAt(Point s, String name) throws Exception {
        robot.mouseMove(s.x - 30, s.y - 30);
        sleep(300);
        robot.mouseMove(s.x, s.y);
        sleep(2600);
        snapCrop(new Rectangle(s.x - 260, s.y - 140, 700, 300), name);
    }

    /** 13: ? 단축키 표. */
    void keysTable(Project p) throws Exception {
        clickCanvas(p, emptySpot(p));
        robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(KeyEvent.VK_SLASH);
        robot.keyRelease(KeyEvent.VK_SLASH);
        robot.keyRelease(KeyEvent.VK_SHIFT);
        sleep(1200);
        Window d = window(x -> x instanceof JDialog && x.isShowing());
        if (d == null) {
            log.add("13: no keys dialog");
            return;
        }
        snapCrop(pad(d.getBounds(), 10), "13-keys-table");
        edt(d::dispose);
        sleep(300);
    }

    /** 11: .s 불러오기(재귀 factorial) 뒤 MIPS 부품과 실행 중 스택. */
    void program(Project p) throws Exception {
        Thread loader = new Thread(() -> {
            try {
                SwingUtilities.invokeAndWait(() -> kr.ac.hallym.hcs.app.palette.PaletteActions.loadProgram(p));
            } catch (Exception e) {
                log.add("11: " + e);
            }
        });
        loader.start();
        JFileChooser fc = null;
        for (int i = 0; i < 40 && fc == null; i++) {
            sleep(250);
            Window w = window(x -> x instanceof JDialog && x.isShowing()
                    && find(x, y -> y instanceof JFileChooser) != null);
            if (w != null) {
                fc = (JFileChooser) find(w, y -> y instanceof JFileChooser);
            }
        }
        if (fc == null) {
            log.add("11: no file chooser");
            return;
        }
        final JFileChooser chooser = fc;
        SwingUtilities.invokeLater(() -> {
            chooser.setSelectedFile(new File("tests/mips/factorial.s").getAbsoluteFile());
            chooser.approveSelection();
        });
        sleep(4000);
        Window msg = window(x -> x instanceof JDialog && x.isShowing());
        if (msg != null) {
            snapCrop(pad(msg.getBounds(), 10), "11a-load-summary");
            edt(msg::dispose);
        }
        sleep(500);
        // 재귀가 깊어졌을 때와 끝났을 때
        Circuit c = p.getCurrentCircuit();
        edt(() -> p.getSimulator().requestReset());
        sleep(800);
        runCycles(p, 45);
        setZoom(p, 2.0);
        String[][] parts = {{"Instruction Memory", "11b-imem"}, {"Data Memory", "11c-dmem"},
            {"Stack", "11d-stack-mid-recursion"}, {"Console", "11e-console"}};
        for (String[] t : parts) {
            com.cburch.logisim.comp.Component x = byFactory(c, t[0]);
            if (x != null) {
                Bounds area = x.getBounds().expand(60);
                centerOn(p, area);
                snapLogical(p, area, t[1]);
            }
        }
        runCycles(p, 400);
        for (String[] t : new String[][] {{"Stack", "11f-stack-end"}, {"Console", "11g-console-end"}}) {
            com.cburch.logisim.comp.Component x = byFactory(c, t[0]);
            if (x != null) {
                Bounds area = x.getBounds().expand(60);
                centerOn(p, area);
                snapLogical(p, area, t[1]);
            }
        }
        setZoom(p, 1.0);
        snapFull("11h-ref-mips-after-run");
    }

    void runCycles(Project p, int n) throws Exception {
        for (int i = 0; i < 2 * n; i++) {
            edt(() -> p.getSimulator().tick());
            sleep(8);
        }
        sleep(1500);
    }

    // ---- 앱 ----

    void launch(String... files) throws Exception {
        Thread t = new Thread(() -> com.cburch.logisim.Main.main(files), "app-main");
        t.start();
        for (int i = 0; i < 120 && project() == null; i++) {
            sleep(250);
        }
        sleep(3000);
        edt(() -> {
            Frame f = project().getFrame();
            f.setBounds(0, 0, W, H);
            f.validate();
        });
        sleep(1500);
    }

    Project project() {
        try {
            return call(() -> {
                Frame top = Projects.getTopFrame();
                if (top instanceof com.cburch.logisim.gui.main.Frame) {
                    return ((com.cburch.logisim.gui.main.Frame) top).getProject();
                }
                List<Project> ps = Projects.getOpenProjects();
                return ps.isEmpty() ? null : ps.get(ps.size() - 1);
            });
        } catch (Exception e) {
            return null;
        }
    }

    /** 파일 탭 누르기와 같게 그 파일의 창을 앞에 둔다(포크는 활성 탭의 창만 보인다). */
    void activate(Project p) throws Exception {
        edt(() -> kr.ac.hallym.hcs.app.tabs.FileTabs.get().model().activate(p));
        sleep(1200);
    }

    Project open(String path) throws Exception {
        AtomicReference<Project> ref = new AtomicReference<>();
        edt(() -> ref.set(ProjectActions.doOpen(project().getFrame(), project(), new File(path))));
        sleep(3000);
        Project p = ref.get();
        edt(() -> {
            p.getFrame().setBounds(0, 0, W, H);
            p.getFrame().validate();
            p.getFrame().toFront();
        });
        sleep(1500);
        return p;
    }

    void closeDialogs() throws Exception {
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog && w.isShowing()) {
                edt(w::dispose);
            }
        }
        sleep(500);
    }

    /** 원조 선택 도구가 빈 곳을 누를 때처럼 선택을 비운다. */
    void deselect(Project p) throws Exception {
        edt(() -> {
            com.cburch.logisim.proj.Action a = com.cburch.logisim.gui.main.SelectionActions.dropAll(p.getSelection());
            if (a != null) {
                p.doAction(a);
            }
        });
    }

    void useTool(Project p, String name) throws Exception {
        edt(() -> {
            Library base = p.getLogisimFile().getLoader().getBuiltin().getLibrary("Base");
            Tool t = base.getTool(name);
            if (t != null) {
                p.setTool(t);
            }
        });
    }

    Canvas canvas(Project p) {
        return ((com.cburch.logisim.gui.main.Frame) p.getFrame()).getCanvas();
    }

    double zoom(Project p) throws Exception {
        return call(() -> {
            Object m = zoomModel(p);
            return (Double) m.getClass().getMethod("getZoomFactor").invoke(m);
        });
    }

    Object zoomModel(Project p) throws Exception {
        Field f = com.cburch.logisim.gui.main.Frame.class.getDeclaredField("layoutZoomModel");
        f.setAccessible(true);
        return f.get(p.getFrame());
    }

    void setZoom(Project p, double z) throws Exception {
        edt(() -> {
            try {
                Object m = zoomModel(p);
                Method set = m.getClass().getMethod("setZoomFactor", double.class);
                set.setAccessible(true);
                set.invoke(m, z);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        sleep(1200);
    }

    void scrollTo(Project p, int x, int y) throws Exception {
        edt(() -> canvas(p).scrollRectToVisible(new Rectangle(x, y, 10, 10)));
        sleep(700);
    }

    /** 회로 영역 b가 캔버스 가운데 보이게. */
    void centerOn(Project p, Bounds b) throws Exception {
        double z = zoom(p);
        edt(() -> {
            Canvas c = canvas(p);
            Rectangle vis = c.getVisibleRect();
            int cx = (int) ((b.getX() + b.getWidth() / 2.0) * z);
            int cy = (int) ((b.getY() + b.getHeight() / 2.0) * z);
            c.scrollRectToVisible(new Rectangle(Math.max(0, cx - vis.width / 2), Math.max(0, cy - vis.height / 2),
                    vis.width, vis.height));
        });
        sleep(900);
    }

    Point screen(Project p, Location l) throws Exception {
        double z = zoom(p);
        return call(() -> {
            Point o = canvas(p).getLocationOnScreen();
            return new Point(o.x + (int) Math.round(l.getX() * z), o.y + (int) Math.round(l.getY() * z));
        });
    }

    Rectangle screenRect(Project p, Bounds b) throws Exception {
        Point a = screen(p, Location.create(b.getX(), b.getY()));
        Point c = screen(p, Location.create(b.getX() + b.getWidth(), b.getY() + b.getHeight()));
        return new Rectangle(a.x, a.y, c.x - a.x, c.y - a.y);
    }

    void clickCanvas(Project p, Location at) throws Exception {
        Point s = screen(p, at);
        robot.mouseMove(s.x, s.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        sleep(400);
    }

    /** 보이는 캔버스 안에서 부품·선이 없는 격자점. */
    Location emptySpot(Project p) throws Exception {
        double z = zoom(p);
        return call(() -> {
            Canvas c = canvas(p);
            Rectangle vis = c.getVisibleRect();
            Circuit circ = p.getCurrentCircuit();
            for (int y = (int) (vis.y / z) + 40; y < (vis.y + vis.height) / z - 40; y += 10) {
                for (int x = (int) (vis.x / z) + 40; x < (vis.x + vis.width) / z - 40; x += 10) {
                    Location l = Location.create(x - x % 10, y - y % 10);
                    boolean free = true;
                    for (com.cburch.logisim.comp.Component o : circ.getNonWires()) {
                        if (o.getBounds().expand(30).contains(l)) {
                            free = false;
                            break;
                        }
                    }
                    for (Wire w : circ.getWires()) {
                        if (free && w.getBounds().expand(20).contains(l)) {
                            free = false;
                        }
                    }
                    if (free) {
                        return l;
                    }
                }
            }
            return Location.create((int) (vis.x / z) + 20, (int) (vis.y / z) + 20);
        });
    }

    // ---- 회로 찾기 ----

    static com.cburch.logisim.comp.Component byLabel(Circuit c, String label) {
        for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
            com.cburch.logisim.data.Attribute<?> a = x.getAttributeSet().getAttribute("label");
            if (a != null && label.equals(x.getAttributeSet().getValue(a))
                    && !x.getFactory().getName().equals("Tunnel")) {
                return x;
            }
        }
        return null;
    }

    static com.cburch.logisim.comp.Component byFactory(Circuit c, String name) {
        com.cburch.logisim.comp.Component best = null;
        for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals(name)) {
                if (best == null || x.getLocation().getY() < best.getLocation().getY()
                        || (x.getLocation().getY() == best.getLocation().getY()
                                && x.getLocation().getX() < best.getLocation().getX())) {
                    best = x;
                }
            }
        }
        return best;
    }

    static Wire longestWire(Circuit c) {
        Wire best = null;
        for (Wire w : c.getWires()) {
            if (best == null || w.getLength() > best.getLength()) {
                best = w;
            }
        }
        return best;
    }

    Bounds firstSubcircuit(Project p) throws Exception {
        return call(() -> {
            for (com.cburch.logisim.comp.Component x : p.getCurrentCircuit().getNonWires()) {
                if (x.getFactory() instanceof com.cburch.logisim.circuit.SubcircuitFactory) {
                    return x.getBounds();
                }
            }
            return null;
        });
    }

    static Bounds around(Bounds b, int w, int h) {
        int cx = b.getX() + b.getWidth() / 2;
        int cy = b.getY() + b.getHeight() / 2;
        return Bounds.create(cx - w / 2, cy - h / 2, w, h);
    }

    // ---- 스윙 찾기 ----

    static Component find(Component root, Predicate<Component> p) {
        if (p.test(root)) {
            return root;
        }
        if (root instanceof Container) {
            for (Component c : ((Container) root).getComponents()) {
                Component r = find(c, p);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    static Window window(Predicate<Window> p) {
        for (Window w : Window.getWindows()) {
            if (p.test(w)) {
                return w;
            }
        }
        return null;
    }

    static String tip(Component c) {
        String t = c instanceof javax.swing.JComponent ? ((javax.swing.JComponent) c).getToolTipText() : null;
        return t == null ? "" : t;
    }

    static boolean comboContains(JComboBox<?> c, String s) {
        for (int i = 0; i < c.getItemCount(); i++) {
            if (String.valueOf(c.getItemAt(i)).contains(s)) {
                return true;
            }
        }
        return false;
    }

    static Rectangle popupBounds() {
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        for (MenuElement e : path) {
            if (e instanceof JPopupMenu && ((JPopupMenu) e).isShowing()) {
                return onScreen((JPopupMenu) e);
            }
        }
        for (Window w : Window.getWindows()) {
            Component pm = find(w, x -> x instanceof JPopupMenu && x.isShowing());
            if (pm != null) {
                return onScreen(pm);
            }
        }
        return null;
    }

    static JMenuItem menuItem(Predicate<JMenuItem> p) {
        for (Window w : Window.getWindows()) {
            Component c = find(w, x -> x instanceof JMenuItem && x.isShowing() && p.test((JMenuItem) x));
            if (c != null) {
                return (JMenuItem) c;
            }
        }
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        for (MenuElement e : path) {
            if (e instanceof JPopupMenu) {
                Component c = find((JPopupMenu) e, x -> x instanceof JMenuItem && p.test((JMenuItem) x));
                if (c != null) {
                    return (JMenuItem) c;
                }
            }
        }
        return null;
    }

    static Rectangle onScreen(Component c) {
        Point o = c.getLocationOnScreen();
        return new Rectangle(o.x, o.y, c.getWidth(), c.getHeight());
    }

    static Rectangle pad(Rectangle r, int n) {
        Rectangle x = new Rectangle(r);
        x.grow(n, n);
        return x;
    }

    // ---- 찍기 ----

    /** 앱 창 전체(1920×1080)를 1600px 폭으로. */
    void snapFull(String name) throws Exception {
        sleep(400);
        BufferedImage img = robot.createScreenCapture(new Rectangle(0, 0, W, H));
        int h = Math.round(H * (FULL_WIDTH / (float) W));
        BufferedImage small = new BufferedImage(FULL_WIDTH, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, FULL_WIDTH, h, null);
        g.dispose();
        write(small, name);
    }

    /** 화면 영역을 원본 해상도로(화면 밖은 잘라 낸다). */
    void snapCrop(Rectangle r, String name) throws Exception {
        sleep(300);
        Rectangle c = r.intersection(new Rectangle(0, 0, W, H));
        if (c.isEmpty()) {
            log.add(name + ": empty crop");
            return;
        }
        write(robot.createScreenCapture(c), name);
    }

    void snapLogical(Project p, Bounds b, String name) throws Exception {
        snapCrop(screenRect(p, b), name);
    }

    void write(BufferedImage img, String name) throws IOException {
        File f = new File(out, name + ".png");
        ImageIO.write(img, "png", f);
        log.add(name + ".png " + img.getWidth() + "x" + img.getHeight() + " " + f.length() + "B"
                + (f.length() > MAX_BYTES ? " TOO BIG" : ""));
    }

    // ---- 도움 ----

    void key(int code) {
        robot.keyPress(code);
        robot.keyRelease(code);
    }

    void keyCombo(int mod, int code) {
        robot.keyPress(mod);
        robot.keyPress(code);
        robot.keyRelease(code);
        robot.keyRelease(mod);
    }

    static void sleep(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    static void edt(Runnable r) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeAndWait(r);
        }
    }

    static <T> T call(Callable<T> c) throws Exception {
        AtomicReference<T> ref = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        edt(() -> {
            try {
                ref.set(c.call());
            } catch (Exception e) {
                err.set(e);
            }
        });
        if (err.get() != null) {
            throw err.get();
        }
        return ref.get();
    }
}
