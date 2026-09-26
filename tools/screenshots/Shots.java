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
            // 원조 2.7.1: 데모 회로를 포크가 "화면 맞춤"으로 정한 배율로, 그리고 200% 부분(라벨 칩 비교용)
            launch("demo-datapath.circ");
            Project p = project();
            File zf = new File(out, "fit-zoom.txt");
            double fit = zf.exists() ? Double.parseDouble(new String(java.nio.file.Files.readAllBytes(zf.toPath()),
                    StandardCharsets.UTF_8).trim()) : 0.5;
            setZoom(p, fit);
            scrollTo(p, 0, 0);
            snapFull("02-demo-fit-orig");
            zoomCrops(p, "orig");
            junctionsAndJumps(p, "orig");
            controlPins(p, "orig");
            portNames(p, "orig");
            return;
        }
        if (want(scenes, "01")) {
            launch();
            sleep(1500);
            closeDialogs();
            snapFull("01-first-screen");
            // 부품 트리 위 검색창(검토 반영 1): "mux"를 치면 트리 자리에 걸러진 목록
            Project first = project();
            JTextField ts = (JTextField) find(first.getFrame(), x -> x instanceof JTextField && x.isShowing()
                    && x.getParent() != null && x.getParent().getParent() != null
                    && x.getParent().getParent().getClass().getSimpleName().equals("ToolboxSearch"));
            if (ts != null) {
                edt(() -> ts.setText("mux"));
                sleep(900);
                Rectangle r = onScreen(ts.getParent().getParent());
                snapCrop(new Rectangle(r.x, r.y, r.width, Math.min(r.height, 360)), "01b-tree-search-mux");
                edt(() -> ts.setText(""));
            } else {
                log.add("01b: no tree search field");
            }
        } else {
            launch();
            closeDialogs();
        }
        Project ref = open("tests/mips/ref-mips.circ");
        Project demo = open("tests/circ/demo-datapath.circ");
        if (want(scenes, "02")) {
            edt(() -> canvas(demo).getHcsZoom().fitCircuit()); // 앱의 "화면 맞춤"(Ctrl+0)
            sleep(1200);
            snapFull("02-demo-fit");
            java.nio.file.Files.write(new File(out, "fit-zoom.txt").toPath(),
                    Double.toString(zoom(demo)).getBytes(StandardCharsets.UTF_8));
            setZoom(demo, 1.0);
        }
        if (want(scenes, "03")) {
            zoomCrops(demo, "");
        }
        if (want(scenes, "05")) {
            quickAttrs(demo);
        }
        if (want(scenes, "12")) {
            hover(demo);
        }
        activate(ref);
        if (want(scenes, "04")) {
            contextMenus(ref);
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
            activate(demo);
            find(demo); // 사람이 그린 회로(체크리스트 10)
        }
        if (want(scenes, "13")) {
            keysTable(ref);
        }
        if (want(scenes, "11")) {
            program(ref);
        }
        if (want(scenes, "25")) {
            cycles(demo);
        }
        if (want(scenes, "26")) {
            runUntil(demo);
        }
        if (want(scenes, "27")) {
            machinePanels(demo);
        }
        if (want(scenes, "28")) {
            consoleAndReload(demo);
        }
        if (want(scenes, "29")) {
            instructionFields(demo);
        }
        if (want(scenes, "30")) {
            busValuesAndActivePath(demo);
        }
        if (want(scenes, "31")) {
            dynamicMessages(demo);
        }
        if (want(scenes, "32")) {
            oscillationAndMips();
        }
        if (want(scenes, "33")) {
            about(demo);
        }
        if (want(scenes, "34")) {
            historyAndKeys(demo);
        }
        if (want(scenes, "35")) {
            arrange(demo);
        }
        if (want(scenes, "36")) {
            submitAndExport(demo);
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
        if (want(scenes, "15")) {
            followingWires(demo);
        }
        if (want(scenes, "16")) {
            junctionsAndJumps(demo, "");
            netHighlight(demo);
        }
        if (want(scenes, "17")) {
            influence(demo);
        }
        if (want(scenes, "18")) {
            signalFlow(demo);
        }
        if (want(scenes, "19")) {
            instanceBanner(demo);
        }
        if (want(scenes, "22")) {
            defaultAppearanceHelp(demo);
        }
        if (want(scenes, "23")) {
            controlPins(demo, "");
        }
        if (want(scenes, "24")) {
            sidePanel(demo);
        }
        if (want(scenes, "20")) {
            crossTabLibraries(demo);
        }
        if (want(scenes, "21")) {
            portNames(demo, "");
        }
        if (want(scenes, "14")) {
            messages(demo);
            gateUndefined(demo);
        }
    }

    // ---- 장면 ----

    /** 3: 200% 부분 확대. 원조 모드도 같은 회로 영역을 찍는다(부품은 원조 API로 찾는다). */
    void zoomCrops(Project p, String suffix) throws Exception {
        setZoom(p, 2.0);
        String s = suffix.isEmpty() ? "" : "-" + suffix;
        Circuit c = p.getCurrentCircuit();
        Object[][] targets = {
            {byLabel(c, "PC"), "03a-pc-adder-200"},
            {widestSplitter(c), "03b-splitter-arms-200"},
            {firstSub(c, "regfile"), "03c-regfile-box-200"},
            {firstSub(c, "alu"), "03d-alu-box-200"},
            {byFactory(c, "Data Memory"), "03e-dmem-tunnels-200"},
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

    /**
     * 16: 연결점과 점프(W-04). 이어지지 않은 교차(1140, 260)를 25·100·400%로, PC 출력 연결점이 있는 영역을 25%로
     * 찍는다. 원조 모드도 같은 자리를 찍는다(원조 API만 쓴다).
     */
    void junctionsAndJumps(Project p, String suffix) throws Exception {
        String s = suffix.isEmpty() ? "" : "-" + suffix;
        if (!orig) {
            activate(p);
            // 앞 장면(15)의 선택 손잡이가 남지 않게 비운다
            edt(() -> p.doAction(com.cburch.logisim.gui.main.SelectionActions.dropAll(p.getSelection())));
        }
        Location x = Location.create(1140, 260);
        for (double z : new double[] {0.25, 1.0, 4.0}) {
            setZoom(p, z);
            int half = (int) Math.max(40, 120 / z);
            // 캔버스 밖(탭 줄)이 찍히지 않게 회로 좌표 0 위로 자른다
            int ax = Math.max(0, x.getX() - 2 * half);
            int ay = Math.max(0, x.getY() - half);
            Bounds area = Bounds.create(ax, ay, 4 * half, 2 * half);
            centerOn(p, area);
            snapLogical(p, area, "16a-crossing-" + Math.round(z * 100) + s);
        }
        setZoom(p, 0.25);
        Bounds j = Bounds.create(100, 60, 900, 400);
        centerOn(p, j);
        snapLogical(p, j, "16b-junctions-25" + s);
        setZoom(p, 1.0);
    }

    /**
     * 21: 원조 부품의 포트 이름(S-06, S-07). PC 레지스터와 PC+4 가산기를 100·200·400%로, 원조와 같은 자리에서. 포크는
     * 100%에서 이름을 숨기고(마우스를 올리면 보임, 21c) 200% 이상에서 부품 바깥에 그린다. 원조는 늘 안쪽에 그린다.
     */
    void portNames(Project p, String suffix) throws Exception {
        String s = suffix.isEmpty() ? "" : "-" + suffix;
        if (!orig) {
            activate(p);
            deselect(p);
        }
        Circuit c = p.getCurrentCircuit();
        com.cburch.logisim.comp.Component pc = byLabel(c, "PC");
        com.cburch.logisim.comp.Component add = null;
        for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals("Adder") && (add == null || x.getLocation().getY() < add
                    .getLocation().getY())) {
                add = x;
            }
        }
        for (double z : new double[] {1.0, 2.0, 4.0}) {
            setZoom(p, z);
            int pct = (int) Math.round(z * 100);
            com.cburch.logisim.comp.Component[] parts = {pc, add};
            String[] names = {"21a-pc-", "21b-adder-"};
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] == null) {
                    log.add("21: no " + names[i]);
                    continue;
                }
                Bounds b = parts[i].getBounds().expand(z >= 4 ? 16 : 30);
                centerOn(p, b);
                snapLogical(p, b, names[i] + pct + s);
            }
        }
        if (!orig && add != null) {
            // 100%에서 가산기에 마우스를 올리면 이름이 보인다
            setZoom(p, 1.0);
            Bounds b = add.getBounds().expand(40);
            centerOn(p, b);
            // 왼쪽 위 모서리 가까이: 마우스 오버 정보 창은 아래로 열려 포트 이름을 가리지 않는다
            Bounds ab = add.getBounds();
            Point at = screen(p, Location.create(ab.getX() + 6, ab.getY() + 4));
            robot.mouseMove(at.x, at.y);
            sleep(350); // 마우스 오버 정보(Swing 도움말, 750ms 뒤)가 뜨기 전
            snapLogical(p, b, "21c-adder-hover-100");
            robot.mouseMove(5, 5);
            sleep(300);
            // 25%에서 마우스를 올려도 이름이 읽힌다(화면 10px 이상). 가산기는 캔버스 맨 위라 PC 레지스터로
            setZoom(p, 0.25);
            Bounds pb = pc.getBounds();
            Bounds b25 = pb.expand(160);
            centerOn(p, b25);
            Point at25 = screen(p, Location.create(pb.getX() + 4, pb.getY() + 4));
            robot.mouseMove(at25.x, at25.y);
            sleep(350);
            snapLogical(p, b25, "21d-pc-hover-25");
            robot.mouseMove(5, 5);
            sleep(300);
        }
        setZoom(p, 1.0);
    }

    /** 23: 제어 핀과 같은 이름의 터널(S-12). 핀 라벨 칩 없이 터널 이름만(포크), 원조는 핀 라벨과 터널 글자. */
    void controlPins(Project p, String suffix) throws Exception {
        String s = suffix.isEmpty() ? "" : "-" + suffix;
        if (!orig) {
            activate(p);
            deselect(p);
        }
        Bounds area = Bounds.create(60, 380, 140, 300);
        for (double z : new double[] {1.0, 2.0, 4.0}) {
            setZoom(p, z);
            Bounds shot = z >= 4 ? Bounds.create(60, 390, 140, 90) : area;
            centerOn(p, shot); // 찍는 곳을 가운데에(400%에서 큰 영역을 맞추면 위가 캔버스 밖으로 잘린다)
            snapLogical(p, shot, "23a-control-pins-" + Math.round(z * 100) + s);
        }
        setZoom(p, 1.0);
    }

    /** 24: 왼쪽 칸 아래 탭(S-11). Tunnels 목록과 Minimap(보이는 영역 네모), 창 전체. */
    void sidePanel(Project p) throws Exception {
        activate(p);
        deselect(p);
        edt(() -> canvas(p).getHcsZoom().fitCircuit());
        sleep(900);
        kr.ac.hallym.hcs.app.side.SidePanel side = (kr.ac.hallym.hcs.app.side.SidePanel) find(p.getFrame(),
                x -> x instanceof kr.ac.hallym.hcs.app.side.SidePanel);
        if (side == null) {
            log.add("24: no side panel");
            return;
        }
        edt(() -> side.tabs().setSelectedIndex(0));
        sleep(700);
        snapFull("24a-full-window-tunnels");
        snapCrop(onScreen(side), "24b-left-panel-tunnels");
        edt(() -> side.tabs().setSelectedIndex(1));
        sleep(700);
        // 확대해 보이는 영역이 회로 일부일 때의 미니맵
        setZoom(p, 1.5);
        centerOn(p, Bounds.create(600, 200, 400, 300));
        sleep(700);
        snapCrop(onScreen(side), "24c-left-panel-minimap");
        setZoom(p, 1.0);
        edt(() -> side.tabs().setSelectedIndex(0));
        sleep(300);
    }

    /** 16c: 우클릭 "Highlight Net"과 같은 강조(PC 출력 넷). */
    void netHighlight(Project p) throws Exception {
        activate(p);
        Circuit c = p.getCurrentCircuit();
        com.cburch.logisim.circuit.Wire w = null;
        for (com.cburch.logisim.circuit.Wire x : c.getWires()) {
            if (x.endsAt(Location.create(330, 200))) {
                w = x;
            }
        }
        if (w == null) {
            log.add("16c: no wire at the PC output");
            return;
        }
        final com.cburch.logisim.circuit.Wire hw = w;
        edt(() -> kr.ac.hallym.hcs.app.wiring.WireMarks.highlight(c,
                kr.ac.hallym.hcs.app.model.Netlist.of(c).netOf(hw)));
        edt(() -> canvas(p).repaint());
        setZoom(p, 1.0);
        Bounds area = Bounds.create(100, 60, 700, 320);
        centerOn(p, area);
        snapLogical(p, area, "16c-net-highlight");
        edt(() -> kr.ac.hallym.hcs.app.wiring.WireMarks.clearHighlight(c));
        edt(() -> canvas(p).repaint());
    }

    /**
     * 17: 영향 경로(P-01). regfile에서 앞으로(alu 안 칩, Data Memory에서 멈춤), 한 단계로 좁힘, Data Memory에서
     * 뒤로, Through Registers, alu 안으로 들어가 본 모습, regfile과 Data Memory 사이 경로.
     */
    void influence(Project p) throws Exception {
        activate(p);
        edt(() -> p.doAction(com.cburch.logisim.gui.main.SelectionActions.dropAll(p.getSelection())));
        Circuit c = p.getCurrentCircuit();
        com.cburch.logisim.comp.Component reg = firstSub(c, "regfile");
        com.cburch.logisim.comp.Component alu = firstSub(c, "alu");
        com.cburch.logisim.comp.Component dm = byFactory(c, "Data Memory");
        com.cburch.logisim.comp.Component pc = byLabel(c, "PC");
        if (reg == null || alu == null || dm == null || pc == null) {
            log.add("17: parts not found");
            return;
        }
        kr.ac.hallym.hcs.app.influence.InfluenceOverlay o = kr.ac.hallym.hcs.app.influence.InfluenceOverlay.of(p);
        setZoom(p, 1.0);
        Bounds area = Bounds.create(560, 100, 820, 380);
        centerOn(p, area);
        edt(() -> o.show(c, java.util.List.of(reg), kr.ac.hallym.hcs.app.model.Influence.Mode.FORWARD));
        sleep(600);
        snapLogical(p, area, "17a-forward-regfile");
        edt(() -> o.widen(-100));
        sleep(600);
        snapLogical(p, area, "17b-forward-one-step");
        edt(() -> o.show(c, java.util.List.of(dm), kr.ac.hallym.hcs.app.model.Influence.Mode.BACKWARD));
        sleep(600);
        Bounds wide = Bounds.create(100, 60, 1300, 420);
        setZoom(p, 0.8);
        centerOn(p, wide);
        snapLogical(p, wide, "17c-backward-dmem");
        edt(() -> o.setThrough(true));
        edt(() -> o.show(c, java.util.List.of(pc), kr.ac.hallym.hcs.app.model.Influence.Mode.FORWARD));
        sleep(600);
        snapLogical(p, wide, "17d-through-registers-pc");
        edt(() -> o.setThrough(false));
        edt(() -> o.between(c, reg, dm));
        sleep(600);
        snapLogical(p, wide, "17e-between-regfile-dmem");
        // alu 안으로: 같은 강조가 이어진다
        edt(() -> o.show(c, java.util.List.of(reg), kr.ac.hallym.hcs.app.model.Influence.Mode.FORWARD));
        Circuit aluCircuit = ((com.cburch.logisim.circuit.SubcircuitFactory) alu.getFactory()).getSubcircuit();
        edt(() -> p.setCurrentCircuit(aluCircuit));
        sleep(800);
        setZoom(p, 1.0);
        Bounds ab = aluCircuit.getBounds().expand(30);
        centerOn(p, ab);
        snapLogical(p, ab, "17f-inside-alu");
        edt(() -> p.setCurrentCircuit(c));
        sleep(600);
        // 배율 25%, 400%(체크리스트 4)
        edt(() -> o.show(c, java.util.List.of(reg), kr.ac.hallym.hcs.app.model.Influence.Mode.FORWARD));
        setZoom(p, 0.25);
        Bounds all = Bounds.create(0, 0, 1500, 700);
        centerOn(p, all);
        snapLogical(p, all, "17g-forward-25");
        setZoom(p, 4.0);
        Bounds near = Bounds.create(850, 190, 200, 110); // regfile RD1·RD2 → alu A·B
        centerOn(p, near);
        snapLogical(p, near, "17h-forward-400");
        // 지우면 강조 전과 같다(원조 덧그림 밖 영역 비교용)
        setZoom(p, 1.0);
        centerOn(p, area);
        edt(o::clear);
        sleep(600);
        snapLogical(p, area, "17i-cleared");
        setZoom(p, 1.0);
    }

    /**
     * 19: 서브회로 인스턴스 안내(P-02). 탐색기에서 regfile을 따로 열면 띠와 "Go to Instance in main", 이어진 입력 핀을
     * 고르면 끊길 연결 수 미리 보기, 핀 도구를 들면 핀을 더할 때의 미리 보기, 인스턴스로 가면 띠가 사라진다. 핀을
     * 더해 끊긴 연결을 되살리는 알림도 찍고 되돌린다.
     */
    void instanceBanner(Project p) throws Exception {
        activate(p);
        edt(() -> p.doAction(com.cburch.logisim.gui.main.SelectionActions.dropAll(p.getSelection())));
        Circuit main = p.getCurrentCircuit();
        com.cburch.logisim.comp.Component regInst = firstSub(main, "regfile");
        Circuit reg = ((com.cburch.logisim.circuit.SubcircuitFactory) regInst.getFactory()).getSubcircuit();
        edt(() -> p.setCurrentCircuit(reg)); // 탐색기에서 연 것과 같다(자기만의 상태)
        sleep(900);
        edt(() -> canvas(p).getHcsZoom().fitCircuit());
        sleep(900);
        snapFull("19a-standalone-banner");
        Rectangle top = new Rectangle(0, 0, W, 260);
        // 이어진 입력 핀(RR1)을 고르면 미리 보기
        com.cburch.logisim.comp.Component rr1 = byLabel(reg, "RR1");
        if (rr1 != null) {
            edt(() -> p.getSelection().add(rr1));
            sleep(700);
            snapCrop(top, "19b-pin-preview");
            edt(() -> p.doAction(com.cburch.logisim.gui.main.SelectionActions.dropAll(p.getSelection())));
        } else {
            log.add("19b: no RR1 pin");
        }
        // 핀 도구
        edt(() -> p.setTool(p.getLogisimFile().getLoader().getBuiltin().getLibrary("Wiring").getTool("Pin")));
        sleep(700);
        snapCrop(top, "19c-pin-tool-preview");
        useTool(p, "Edit Tool");
        // Go to Instance in main
        java.util.List<java.util.List<com.cburch.logisim.comp.Component>> paths =
                kr.ac.hallym.hcs.app.model.InstancePaths.paths(main, reg);
        edt(() -> p.setCircuitState(kr.ac.hallym.hcs.app.model.InstancePaths.stateFor(p.getCircuitState(main),
                paths.get(0))));
        sleep(900);
        snapFull("19d-running-instance");
        // 이어진 핀을 지운 뒤 알림(데모의 regfile은 사용자 모양이라 핀을 더해도 포트가 밀리지 않는다), 그리고 되돌리기
        edt(() -> p.setCurrentCircuit(reg));
        sleep(600);
        edt(() -> {
            com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(reg);
            m.remove(byLabel(reg, "RR1"));
            p.doAction(m.toAction(() -> "Delete Pin"));
        });
        sleep(1500);
        snapFull("19e-cut-connection-notice");
        edt(() -> p.undoAction());
        sleep(600);
        edt(() -> p.setCurrentCircuit(main));
        sleep(600);
    }

    /**
     * 20: 탭 간 라이브러리(P-03). 1bit_adder.circ와 ripple_carry.circ(tests/circ/libs의 복사본)를 열고, 새 파일에서
     * 검색 "adder"의 Open Files 묶음으로 1bit_adder를 놓는다. 1bit_adder의 출력 핀을 지우고 저장하면 끊길 연결을
     * 알리고(취소), 속만 고쳐 저장하면 ripple_carry 탭에 Updated가 붙는다. 인스턴스 우클릭의 Edit Original File.
     */
    void crossTabLibraries(Project base) throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("hcs-p03").toFile();
        for (String n : new String[] {"1bit_adder", "ripple_carry"}) {
            java.nio.file.Files.copy(new File("tests/circ/libs/" + n + ".circ").toPath(),
                    new File(dir, n + ".circ").toPath());
        }
        Project adder = open(new File(dir, "1bit_adder.circ").getPath());
        Project ripple = open(new File(dir, "ripple_carry.circ").getPath());
        edt(() -> canvas(ripple).getHcsZoom().fitCircuit());
        sleep(900);
        snapFull("20a-ripple-uses-adder");
        // 새 파일: 검색 "adder" → Open Files · 1bit_adder.circ
        Project cpu = newProject(base);
        useTool(cpu, "Edit Tool");
        clickCanvas(cpu, Location.create(300, 200));
        keyCombo(KeyEvent.VK_CONTROL, KeyEvent.VK_K);
        sleep(900);
        Window w = window(x -> x.getClass().getSimpleName().equals("PaletteWindow") && x.isShowing());
        if (w == null) {
            log.add("20b: no palette window");
            return;
        }
        JTextField q = (JTextField) find(w, x -> x instanceof JTextField);
        edt(() -> q.setText("adder"));
        sleep(900);
        // 기본 Adder가 먼저다: 한 칸 내려 Open Files 항목을 고른다
        key(KeyEvent.VK_DOWN);
        sleep(400);
        snapCrop(pad(w.getBounds(), 16), "20b-open-files-search");
        key(KeyEvent.VK_ENTER);
        sleep(1500);
        useTool(cpu, "Edit Tool");
        deselect(cpu);
        snapFull("20c-loaded-and-placed");
        // 1bit_adder의 출력 핀 s를 지우고 저장: 끊길 연결 알림(취소)
        activate(adder);
        Circuit add = adder.getLogisimFile().getCircuit("1bit_adder");
        com.cburch.logisim.comp.Component s = byLabel(add, "s");
        edt(() -> {
            com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(add);
            m.remove(s);
            adder.doAction(m.toAction(() -> "Delete Pin"));
        });
        sleep(600);
        SwingUtilities.invokeLater(() -> ProjectActions.doSave(adder));
        sleep(1500);
        Window dlg = window(x -> x instanceof JDialog && x.isShowing());
        if (dlg != null) {
            snapCrop(pad(dlg.getBounds(), 16), "20d-port-change-warning");
        } else {
            log.add("20d: no warning dialog");
        }
        closeDialogs();
        edt(() -> adder.undoAction());
        sleep(600);
        // 속만 고쳐 저장: ripple_carry 탭에 Updated
        edt(() -> {
            com.cburch.logisim.comp.ComponentFactory not = ((com.cburch.logisim.tools.AddTool) adder.getLogisimFile()
                    .getLoader().getBuiltin().getLibrary("Gates").getTool("NOT Gate")).getFactory();
            com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(add);
            m.add(not.createComponent(Location.create(300, 420), not.createAttributeSet()));
            adder.doAction(m.toAction(() -> "Add NOT Gate"));
            ProjectActions.doSave(adder);
        });
        sleep(1500);
        snapCrop(new Rectangle(0, 0, W, 120), "20e-updated-badge");
        // ripple_carry의 fa0 우클릭: Edit Original File
        activate(ripple);
        setZoom(ripple, 1.0);
        com.cburch.logisim.comp.Component fa0 = byLabel(ripple.getLogisimFile().getMainCircuit(), "fa0");
        com.cburch.logisim.data.Bounds b = fa0.getBounds();
        // 오른쪽 아래 모서리 가까이 누른다: 메뉴가 아래·오른쪽으로 열려 부품을 덮지 않는다
        menuAt(ripple, Location.create(b.getX() + b.getWidth() - 3, b.getY() + b.getHeight() - 3),
                "20f-edit-original-menu");
    }

    /**
     * 18: Signal Flow(P-07). demo에서 PC 출력을 눌렀을 때의 프레임 6장과 GIF, 터널 점프, 서브회로 경계, Active Path
     * Only(MemtoReg 0·1), Backward, Reduce Motion, 어두운 바탕 대비 확인 렌더.
     */
    void signalFlow(Project p) throws Exception {
        activate(p);
        edt(() -> p.doAction(com.cburch.logisim.gui.main.SelectionActions.dropAll(p.getSelection())));
        Circuit c = p.getCurrentCircuit();
        Canvas cv = canvas(p);
        kr.ac.hallym.hcs.app.flow.FlowController f = kr.ac.hallym.hcs.app.flow.FlowController.of(cv);
        com.cburch.logisim.comp.Component pc = byLabel(c, "PC");
        setZoom(p, 1.0);
        edt(() -> f.start(pc, 0, false));
        edt(() -> f.freeze(0.0));
        double total = call(() -> f.path().total);
        java.awt.Rectangle box = call(() -> kr.ac.hallym.hcs.app.flow.FlowPainter.bounds(f.path(), c));
        Bounds area = Bounds.create(box.x, box.y, box.width, box.height);
        centerOn(p, area);
        double[] ts = {0, total * 0.2, total * 0.45, total * 0.75, total + 10, total + 260};
        String[] names = {"18a-pc-t0", "18b-pc-front-1", "18c-pc-front-2", "18d-pc-front-3", "18e-pc-reached",
            "18f-pc-continuous"};
        for (int i = 0; i < ts.length; i++) {
            double t = ts[i];
            edt(() -> f.freeze(t));
            sleep(300);
            snapLogical(p, area, names[i]);
        }
        // GIF: 12fps로 앞단이 퍼져 끝까지 닿은 뒤 연속 흐름 1초
        java.util.List<BufferedImage> frames = new java.util.ArrayList<>();
        Rectangle r = screenRect(p, area);
        double step = kr.ac.hallym.hcs.app.flow.FlowSettings.speed().pxPerSecond / 12.0; // 화면 px = 회로 단위(100%)
        for (double t = 0; t <= total + kr.ac.hallym.hcs.app.flow.FlowSettings.speed().pxPerSecond; t += step) {
            double tt = t;
            edt(() -> f.freeze(tt));
            sleep(120);
            frames.add(robot.createScreenCapture(r));
        }
        writeGif(frames, 1000 / 12, "18-pc-flow");
        edt(() -> {
            f.freeze(null);
            f.stop();
        });
        // 터널 점프: MemtoReg 핀에서(같은 이름 터널로 MUX 선택 입력까지 멀리 건너뛴다)
        com.cburch.logisim.comp.Component memtoRegPin = null;
        for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals("Pin") && "MemtoReg".equals(x.getAttributeSet().getValue(
                    com.cburch.logisim.instance.StdAttr.LABEL))) {
                memtoRegPin = x;
            }
        }
        com.cburch.logisim.comp.Component mtr = memtoRegPin;
        edt(() -> f.start(mtr, -1, false));
        edt(() -> f.freeze(100000.0));
        Bounds wide = Bounds.create(100, 60, 1500, 460);
        setZoom(p, 0.75);
        centerOn(p, wide);
        snapLogical(p, wide, "18g-tunnel-jumps");
        // 서브회로 경계: Instruction Memory Instr 출력 → 스플리터 → regfile(안으로)
        com.cburch.logisim.comp.Component im = byFactory(c, "Instruction Memory");
        int instr = -1;
        for (int i = 0; i < im.getEnds().size(); i++) {
            if (im.getEnds().get(i).isOutput()) {
                instr = i;
            }
        }
        int ins = instr;
        edt(() -> f.start(im, ins, false));
        edt(() -> f.freeze(100000.0));
        snapLogical(p, wide, "18h-subcircuit-boundary");
        // Active Path Only: MemtoReg 0과 1에서 Data Memory ReadData 흐름
        com.cburch.logisim.comp.Component dm = byFactory(c, "Data Memory");
        int readData = -1;
        for (int i = 0; i < dm.getEnds().size(); i++) {
            if (kr.ac.hallym.hcs.app.model.Kinds.portName(dm, i).equals("ReadData")) {
                readData = i;
            }
        }
        int rd = readData;
        com.cburch.logisim.comp.Component memtoReg = null;
        for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals("Pin") && "MemtoReg".equals(x.getAttributeSet().getValue(
                    com.cburch.logisim.instance.StdAttr.LABEL))) {
                memtoReg = x;
            }
        }
        com.cburch.logisim.comp.Component sel = memtoReg;
        kr.ac.hallym.hcs.app.flow.FlowSettings.setActivePathOnly(true);
        for (int v = 0; v <= 1; v++) {
            int val = v;
            edt(() -> {
                com.cburch.logisim.circuit.CircuitState st = p.getCircuitState();
                com.cburch.logisim.instance.InstanceState is = st.getInstanceState(sel);
                ((com.cburch.logisim.std.wiring.Pin) sel.getFactory()).setValue(is,
                        val == 1 ? com.cburch.logisim.data.Value.TRUE : com.cburch.logisim.data.Value.FALSE);
                is.fireInvalidated(); // 조작 도구처럼: 핀이 새 값을 내보내게
                p.getSimulator().requestPropagate();
            });
            sleep(900);
            com.cburch.logisim.comp.Component mux = byFactory(c, "Multiplexer");
            log.add("18i: MemtoReg=" + v + " mux select=" + call(() -> p.getCircuitState().getValue(
                    mux.getEnd(2).getLocation())));
            edt(() -> f.start(dm, rd, false));
            edt(() -> f.freeze(100000.0));
            snapLogical(p, wide, "18i-active-memtoreg-" + v);
        }
        kr.ac.hallym.hcs.app.flow.FlowSettings.setActivePathOnly(false);
        // Backward: regfile WD 입력에서
        com.cburch.logisim.comp.Component reg = firstSub(c, "regfile");
        int wd = -1;
        for (int i = 0; i < reg.getEnds().size(); i++) {
            if (kr.ac.hallym.hcs.app.model.Kinds.portName(reg, i).equals("WD")) {
                wd = i;
            }
        }
        int w = wd;
        edt(() -> f.start(reg, w, true));
        edt(() -> f.freeze(100000.0));
        snapLogical(p, wide, "18j-backward-regfile-wd");
        // Reduce Motion
        kr.ac.hallym.hcs.app.flow.FlowSettings.setReduceMotion(true);
        edt(() -> f.start(pc, 0, false));
        sleep(400);
        snapLogical(p, wide, "18k-reduce-motion");
        kr.ac.hallym.hcs.app.flow.FlowSettings.setReduceMotion(false);
        // 배율 25%, 400%(체크리스트 4), 창 전체(빠른 속성 창이 새로 뜨지 않고 Messages가 가려지지 않는다)
        edt(() -> f.start(pc, 0, false));
        edt(() -> f.freeze(100000.0));
        setZoom(p, 0.25);
        Bounds all = Bounds.create(0, 0, 1500, 700);
        centerOn(p, all);
        snapLogical(p, all, "18m-pc-25");
        setZoom(p, 4.0);
        Bounds near = Bounds.create(pc.getLocation().getX() - 60, pc.getLocation().getY() - 50, 200, 110);
        centerOn(p, near);
        snapLogical(p, near, "18n-pc-400");
        setZoom(p, 1.0);
        centerOn(p, area);
        edt(() -> f.start(pc, 0, false)); // 우클릭 "Show Signal Flow"와 같다(선택은 그대로)
        edt(() -> f.freeze(100000.0));
        sleep(400);
        snapFull("18o-full-window");
        // 어두운 바탕 대비(앱에는 다크 테마가 없다: 강조색 대비만 확인하는 렌더)
        BufferedImage dark = call(() -> {
            kr.ac.hallym.hcs.app.flow.SignalFlowPath path = kr.ac.hallym.hcs.app.flow.SignalFlowPath.fromComponent(c,
                    pc, 0, new kr.ac.hallym.hcs.app.flow.SignalFlowPath.Options());
            BufferedImage img = new BufferedImage(1200, 480, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setColor(new java.awt.Color(0x1F2933));
            g.fillRect(0, 0, 1200, 480);
            g.translate(-100, -60);
            g.setColor(new java.awt.Color(0x9AA5B1));
            g.setStroke(new java.awt.BasicStroke(Wire.WIDTH));
            for (Wire x : c.getWires()) {
                g.drawLine(x.getEnd0().getX(), x.getEnd0().getY(), x.getEnd1().getX(), x.getEnd1().getY());
            }
            kr.ac.hallym.hcs.app.flow.FlowPainter.paint(g, path, c, path.total + 120, 1.0, false, null);
            g.dispose();
            return img;
        });
        write(dark, "18l-contrast-dark-background");
        edt(f::stop);
        setZoom(p, 1.0);
    }

    /** ImageIO GIF(외부 의존성 없이): 프레임 사이 delayMs, 무한 반복. */
    void writeGif(java.util.List<BufferedImage> frames, int delayMs, String name) throws IOException {
        javax.imageio.ImageWriter w = ImageIO.getImageWritersByFormatName("gif").next();
        File out = new File(this.out, name + ".gif");
        try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            w.setOutput(ios);
            w.prepareWriteSequence(null);
            for (int i = 0; i < frames.size(); i++) {
                BufferedImage src = frames.get(i);
                BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
                rgb.getGraphics().drawImage(src, 0, 0, null);
                javax.imageio.ImageTypeSpecifier type = javax.imageio.ImageTypeSpecifier.createFromRenderedImage(rgb);
                javax.imageio.metadata.IIOMetadata meta = w.getDefaultImageMetadata(type, null);
                String fmt = meta.getNativeMetadataFormatName();
                javax.imageio.metadata.IIOMetadataNode root = (javax.imageio.metadata.IIOMetadataNode) meta.getAsTree(fmt);
                javax.imageio.metadata.IIOMetadataNode gce = child(root, "GraphicControlExtension");
                gce.setAttribute("disposalMethod", "none");
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", "FALSE");
                gce.setAttribute("delayTime", Integer.toString(Math.max(2, delayMs / 10)));
                gce.setAttribute("transparentColorIndex", "0");
                if (i == 0) {
                    javax.imageio.metadata.IIOMetadataNode apps = child(root, "ApplicationExtensions");
                    javax.imageio.metadata.IIOMetadataNode app = new javax.imageio.metadata.IIOMetadataNode(
                            "ApplicationExtension");
                    app.setAttribute("applicationID", "NETSCAPE");
                    app.setAttribute("authenticationCode", "2.0");
                    app.setUserObject(new byte[] {1, 0, 0}); // 무한 반복
                    apps.appendChild(app);
                }
                meta.setFromTree(fmt, root);
                w.writeToSequence(new javax.imageio.IIOImage(rgb, null, meta), null);
            }
            w.endWriteSequence();
        }
        log.add(name + ".gif " + frames.size() + " frames " + out.length() + "B"
                + (out.length() > MAX_BYTES ? " TOO BIG" : ""));
    }

    static javax.imageio.metadata.IIOMetadataNode child(javax.imageio.metadata.IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (javax.imageio.metadata.IIOMetadataNode) root.item(i);
            }
        }
        javax.imageio.metadata.IIOMetadataNode n = new javax.imageio.metadata.IIOMetadataNode(name);
        root.appendChild(n);
        return n;
    }

    static com.cburch.logisim.comp.Component firstSub(Circuit c, String name) {
        for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
            if (x.getFactory() instanceof com.cburch.logisim.circuit.SubcircuitFactory
                    && x.getFactory().getName().equals(name)) {
                return x;
            }
        }
        return null;
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
    /**
     * 22: 기본 모양 서브회로(S-08). 새 파일에 포트 네 개짜리 회로 "blk"를 만들어 main에 놓고, 고르면 빠른 속성 창에
     * Auto Appearance 단추, 마우스를 올리면 포트 이름 목록. 단추를 누른 뒤 모양.
     */
    void defaultAppearanceHelp(Project base) throws Exception {
        Project p = newProject(base);
        AtomicReference<com.cburch.logisim.comp.Component> inst = new AtomicReference<>();
        edt(() -> {
            com.cburch.logisim.file.LogisimFile f = p.getLogisimFile();
            Circuit blk = new Circuit("blk");
            f.addCircuit(blk);
            Library wiring = f.getLoader().getBuiltin().getLibrary("Wiring");
            com.cburch.logisim.comp.ComponentFactory pin =
                    ((com.cburch.logisim.tools.AddTool) wiring.getTool("Pin")).getFactory();
            com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(blk);
            String[][] pins = {{"A", "100", "100", "east", "false"}, {"B", "100", "140", "east", "false"},
                {"Sel", "100", "180", "east", "false"}, {"Result", "300", "140", "west", "true"}};
            for (String[] d : pins) {
                com.cburch.logisim.data.AttributeSet as = pin.createAttributeSet();
                set(as, "label", d[0]);
                set(as, "facing", d[3]);
                set(as, "output", d[4]);
                m.add(pin.createComponent(Location.create(Integer.parseInt(d[1]), Integer.parseInt(d[2])), as));
            }
            m.execute();
            com.cburch.logisim.circuit.CircuitMutation mm =
                    new com.cburch.logisim.circuit.CircuitMutation(f.getMainCircuit());
            com.cburch.logisim.comp.Component c = blk.getSubcircuitFactory().createComponent(Location.create(300, 200),
                    blk.getSubcircuitFactory().createAttributeSet());
            mm.add(c);
            mm.execute();
            inst.set(c);
        });
        sleep(900);
        setZoom(p, 2.0);
        Bounds b = inst.get().getBounds();
        centerOn(p, b.expand(80));
        useTool(p, "Edit Tool");
        deselect(p);
        edt(() -> p.getSelection().add(inst.get()));
        sleep(1200);
        Rectangle qb = screenRect(p, b.expand(40));
        Component all = find(p.getFrame().getLayeredPane(), x -> x instanceof AbstractButton && x.isShowing()
                && kr.ac.hallym.hcs.app.Messages.get("quick.all").equals(((AbstractButton) x).getText()));
        if (all != null) {
            qb.add(onScreen(all.getParent().getParent()));
        }
        snapCrop(pad(qb, 16), "22a-quick-bar-auto-appearance");
        // 마우스를 올리면 포트 이름 목록(도움말은 750ms 뒤)
        deselect(p);
        Point at = screen(p, Location.create(b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2));
        robot.mouseMove(at.x, at.y);
        sleep(1800);
        Rectangle hov = screenRect(p, b.expand(40));
        for (Window w : Window.getWindows()) {
            if (w.isShowing() && w != p.getFrame() && w.getClass().getName().contains("Popup")) {
                hov.add(w.getBounds());
            }
        }
        Component tip = null;
        for (Window w : Window.getWindows()) {
            Component t = find(w, x -> x instanceof javax.swing.JToolTip && x.isShowing());
            if (t != null) {
                tip = t;
            }
        }
        if (tip != null) {
            hov.add(onScreen(tip));
        } else {
            log.add("22b: no tooltip");
        }
        snapCrop(pad(hov, 16), "22b-hover-port-list");
        robot.mouseMove(5, 5);
        sleep(400);
        // 단추를 누른 뒤: 포트 이름이 보이는 상자
        edt(() -> p.getSelection().add(inst.get()));
        sleep(900);
        AbstractButton auto = (AbstractButton) find(p.getFrame().getLayeredPane(), x -> x instanceof AbstractButton
                && x.isShowing() && kr.ac.hallym.hcs.app.Messages.get("menu.autoAppearance")
                        .equals(((AbstractButton) x).getText()));
        if (auto != null) {
            edt(auto::doClick);
            sleep(1200);
            closeDialogs();
            deselect(p);
            com.cburch.logisim.comp.Component now = p.getCurrentCircuit().getNonWires().iterator().next();
            centerOn(p, now.getBounds().expand(80));
            snapLogical(p, now.getBounds().expand(40), "22c-after-auto-appearance");
        } else {
            log.add("22c: no Auto Appearance button");
        }
        setZoom(p, 1.0);
    }

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

    /**
     * 14: Messages 탭(2c #27). 데모 회로를 일부러 두 곳 망가뜨린다(regfile 옆 RegWrite 터널 이름을 RegWrit로, PC의 clk
     * 터널 지우기). 진단이 뜨면 첫 메시지를 누르고, 끝나면 되돌린다.
     */
    void messages(Project p) throws Exception {
        activate(p);
        Circuit c = p.getCurrentCircuit();
        com.cburch.logisim.comp.Component pc = byLabel(c, "PC");
        com.cburch.logisim.comp.Component rw = null;
        com.cburch.logisim.comp.Component pcClk = null;
        for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
            if (!x.getFactory().getName().equals("Tunnel")) {
                continue;
            }
            String l = x.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.LABEL);
            if ("RegWrite".equals(l) && x.getLocation().getX() > 600) {
                rw = x;
            } else if ("clk".equals(l) && pc != null && x.getLocation().equals(pc.getEnd(2).getLocation())) {
                pcClk = x;
            }
        }
        if (rw == null || pcClk == null) {
            log.add("14: demo tunnels not found");
            return;
        }
        final com.cburch.logisim.comp.Component rwT = rw;
        final com.cburch.logisim.comp.Component clkT = pcClk;
        edt(() -> {
            com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(c);
            m.set(rwT, com.cburch.logisim.instance.StdAttr.LABEL, "RegWrit");
            m.remove(clkT);
            p.doAction(m.toAction(null));
        });
        sleep(2000); // 편집이 멈추면 0.7초 뒤 진단
        edt(() -> canvas(p).getHcsZoom().fitCircuit());
        sleep(1000);
        snapFull("14a-messages");
        @SuppressWarnings("unchecked")
        javax.swing.JList<Object> list = (javax.swing.JList<Object>) find(p.getFrame(),
                x -> x instanceof javax.swing.JList && x.isShowing() && ((javax.swing.JList<?>) x).getModel().getSize() > 0
                        && ((javax.swing.JList<?>) x).getModel().getElementAt(0)
                                instanceof kr.ac.hallym.hcs.app.diag.Diagnostic);
        if (list == null) {
            log.add("14: no messages list");
        } else {
            Rectangle cell = list.getCellBounds(0, 0);
            Point s = list.getLocationOnScreen();
            robot.mouseMove(s.x + cell.x + 40, s.y + cell.y + cell.height / 2);
            sleep(200);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            sleep(1500);
            snapFull("14b-messages-clicked");
            // 원래 크기 자르기(S-13): snapFull은 1600px로 줄여 굵기를 잴 수 없다. 누른 PC(4px)와 누르지 않은 터널(2px)
            com.cburch.logisim.comp.Component pcReg = byLabel(p.getCurrentCircuit(), "PC");
            if (pcReg != null) {
                snapLogical(p, pcReg.getBounds().expand(24), "14g-focused-fit-zoom-native");
            }
            Rectangle panel = onScreen(list.getParent().getParent().getParent());
            snapCrop(pad(panel, 4), "14c-messages-list");
        }
        setZoom(p, 1.5);
        Bounds focus = pc.getBounds().expand(90);
        centerOn(p, focus);
        snapLogical(p, focus, "14d-messages-pc");
        // 14f: 누르지 않아도 보이는 표시(RegWrit 터널)와 누른 표시(PC)가 배율 25~400%에서 보이는지(2c 검토 반영)
        Bounds both = pc.getBounds().add(rwT.getBounds()).expand(60);
        for (double z : new double[] {0.25, 1.0}) {
            setZoom(p, z);
            centerOn(p, both);
            snapLogical(p, both, "14f-marks-" + Math.round(z * 100));
        }
        setZoom(p, 4.0);
        for (Object[] o : new Object[][] {{pc, "14f-marks-400-pc"}, {rwT, "14f-marks-400-tunnel"}}) {
            Bounds b = ((com.cburch.logisim.comp.Component) o[0]).getBounds().expand(25);
            centerOn(p, b);
            snapLogical(p, b, (String) o[1]);
        }
        edt(() -> p.undoAction());
        sleep(1500);
        setZoom(p, 1.0);
    }

    /** 마우스로 끌기: 누르고 조금씩 움직여 놓는다. */
    void drag(Project p, Location from, Location to) throws Exception {
        Point a = screen(p, from);
        Point b = screen(p, to);
        robot.mouseMove(a.x, a.y);
        sleep(200);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= 10; i++) {
            robot.mouseMove(a.x + (b.x - a.x) * i / 10, a.y + (b.y - a.y) * i / 10);
            sleep(60);
        }
        sleep(300);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        sleep(1200);
    }

    /**
     * 15: 따라오는 배선(#81). PC를 위로 끌면 붙은 선이 늘고 꺾인다. regfile로 가는 rs 선의 가운데 세로
     * 선분을 끌면 양쪽 다리가 따라온다. 끝나면 되돌린다.
     */
    void followingWires(Project p) throws Exception {
        activate(p);
        useTool(p, "Edit Tool");
        setZoom(p, 1.5);
        Circuit c = p.getCurrentCircuit();
        com.cburch.logisim.comp.Component pc = byLabel(c, "PC");
        Bounds area = pc.getBounds().expand(120);
        centerOn(p, area);
        snapLogical(p, area, "15a-move-before");
        Location mid = Location.create(pc.getBounds().getX() + pc.getBounds().getWidth() / 2,
                pc.getBounds().getY() + pc.getBounds().getHeight() / 2);
        drag(p, mid, mid.translate(0, -20)); // 위로: 붙은 선이 늘거나 줄어 따라온다(고무줄, D-057)
        snapLogical(p, area, "15b-move-after");
        edt(() -> p.undoAction());
        sleep(800);
        // rs 선의 가운데 세로 선분(x = 710)
        com.cburch.logisim.circuit.Wire seg = null;
        for (com.cburch.logisim.circuit.Wire w : c.getWires()) {
            if (w.isVertical() && w.getEnd0().getX() == 710) {
                seg = w;
            }
        }
        if (seg == null) {
            log.add("15: no rs segment");
            return;
        }
        Bounds sa = seg.getBounds().expand(90).add(seg.getBounds().getX() - 190, seg.getBounds().getY()); // 팔 라벨(막대 왼쪽)까지
        centerOn(p, sa);
        clickCanvas(p, Location.create(160, 900)); // 빈 곳: 선택 비우기
        snapLogical(p, sa, "15c-segment-before");
        Location on = Location.create(710, (seg.getEnd0().getY() + seg.getEnd1().getY()) / 2);
        clickCanvas(p, on); // 선분 고르기
        drag(p, on, on.translate(-30, 0));
        snapLogical(p, sa, "15d-segment-after");
        edt(() -> p.undoAction());
        sleep(800);
        setZoom(p, 1.0);
    }

    /**
     * 14e: 프로젝트 옵션 gateUndefined = error인 회로(2c 검토 반영). 5입력 AND에 입력 둘만 잇고 출력은 핀으로. 빈
     * 입력 in2·in3·in4가 Messages에 한 줄로 나온다. ignore(기본)이면 알리지 않는다.
     */
    void gateUndefined(Project base) throws Exception {
        Project p = newProject(base);
        edt(() -> {
            Library gates = p.getLogisimFile().getLoader().getBuiltin().getLibrary("Gates");
            Library wiring = p.getLogisimFile().getLoader().getBuiltin().getLibrary("Wiring");
            com.cburch.logisim.comp.ComponentFactory and =
                    ((com.cburch.logisim.tools.AddTool) gates.getTool("AND Gate")).getFactory();
            com.cburch.logisim.data.AttributeSet as = and.createAttributeSet();
            set(as, "inputs", "5");
            com.cburch.logisim.comp.Component g = and.createComponent(Location.create(400, 300), as);
            com.cburch.logisim.comp.ComponentFactory pin =
                    ((com.cburch.logisim.tools.AddTool) wiring.getTool("Pin")).getFactory();
            com.cburch.logisim.circuit.CircuitMutation m =
                    new com.cburch.logisim.circuit.CircuitMutation(p.getCurrentCircuit());
            m.add(g);
            for (int i = 1; i <= 2; i++) {
                Location in = g.getEnds().get(i).getLocation();
                com.cburch.logisim.data.AttributeSet ps = pin.createAttributeSet();
                set(ps, "label", i == 1 ? "a" : "b");
                int y = in.getY() + (i == 1 ? -40 : 60); // 핀끼리 겹치지 않게 벌리고 꺾어 잇는다
                int bend = in.getX() - (i == 1 ? 40 : 30);
                m.add(pin.createComponent(Location.create(in.getX() - 160, y), ps));
                m.add(Wire.create(Location.create(in.getX() - 160, y), Location.create(bend, y)));
                m.add(Wire.create(Location.create(bend, y), Location.create(bend, in.getY())));
                m.add(Wire.create(Location.create(bend, in.getY()), in));
            }
            Location out = g.getEnds().get(0).getLocation();
            m.add(Wire.create(out, Location.create(out.getX() + 100, out.getY())));
            com.cburch.logisim.data.AttributeSet ps = pin.createAttributeSet();
            set(ps, "facing", "west");
            set(ps, "output", "true");
            set(ps, "label", "y");
            m.add(pin.createComponent(Location.create(out.getX() + 100, out.getY()), ps));
            p.doAction(m.toAction(null));
            p.getLogisimFile().getOptions().getAttributeSet().setValue(
                    com.cburch.logisim.file.Options.ATTR_GATE_UNDEFINED,
                    com.cburch.logisim.file.Options.GATE_UNDEFINED_ERROR);
            kr.ac.hallym.hcs.app.diag.Diagnostics.of(p).refresh();
        });
        sleep(800);
        // 입력 a=1, b=1(2c 검토 반영 2): 출력 E의 원인이 빈 입력뿐인 상태
        edt(() -> {
            com.cburch.logisim.circuit.CircuitState cs = p.getCircuitState();
            for (com.cburch.logisim.comp.Component x : p.getCurrentCircuit().getNonWires()) {
                String l = x.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.LABEL);
                if (x.getFactory() instanceof com.cburch.logisim.std.wiring.Pin && ("a".equals(l) || "b".equals(l))) {
                    ((com.cburch.logisim.std.wiring.Pin) x.getFactory()).setValue(cs.getInstanceState(x),
                            com.cburch.logisim.data.Value.TRUE);
                    cs.markComponentAsDirty(x); // 원조 Poke Tool처럼 핀을 다시 전파한다
                }
            }
            p.getSimulator().requestPropagate();
        });
        sleep(1500);
        setZoom(p, 1.5);
        Bounds area = p.getCurrentCircuit().getBounds().expand(40); // 핀·게이트·출력 핀 모두
        centerOn(p, area);
        snapFull("14e-gate-undefined-error");
        snapLogical(p, area, "14e-gate-undefined-error-crop");
        setZoom(p, 1.0);
        activate(base);
    }

    /** 5: 빠른 속성 창 + 오른쪽 속성 패널(펼침·접힘). */
    void quickAttrs(Project p) throws Exception {
        setZoom(p, 1.5);
        useTool(p, "Edit Tool");
        // 04e: 서브회로 우클릭(Auto Appearance, 검토 2차 C). 데모 회로가 필요해 여기서 찍는다
        com.cburch.logisim.comp.Component rf = byFactory(p.getCurrentCircuit(), "regfile");
        if (rf != null) {
            Bounds rb = rf.getBounds();
            centerOn(p, rb.expand(150));
            menuAt(p, Location.create(rb.getX() + rb.getWidth() / 2, rb.getY() + rb.getHeight() / 2),
                    "04e-menu-subcircuit");
        }
        com.cburch.logisim.comp.Component reg = byLabel(p.getCurrentCircuit(), "PC");
        if (reg == null) {
            reg = byFactory(p.getCurrentCircuit(), "Register");
        }
        final com.cburch.logisim.comp.Component target = reg;
        centerOn(p, target.getBounds().expand(120));
        deselect(p);
        edt(() -> p.getSelection().add(target));
        sleep(1200);
        snapFull("05a-quick-attrs-dock-open");
        Rectangle qb = screenRect(p, target.getBounds().expand(80));
        Component bar = find(p.getFrame().getLayeredPane(), x -> x instanceof AbstractButton && x.isShowing()
                && kr.ac.hallym.hcs.app.Messages.get("quick.all").equals(((AbstractButton) x).getText()));
        if (bar != null) {
            qb.add(onScreen(bar.getParent().getParent())); // 빠른 속성 창 전체
        }
        snapCrop(pad(qb, 16), "05b-quick-attrs-crop");
        AbstractButton collapse = (AbstractButton) find(p.getFrame(),
                x -> x instanceof AbstractButton && tip(x).equals(kr.ac.hallym.hcs.app.Messages.get("dock.collapse")));
        if (collapse != null) {
            edt(collapse::doClick);
            sleep(900);
            snapFull("05c-dock-collapsed");
            AbstractButton expand = (AbstractButton) find(p.getFrame(),
                    x -> x instanceof AbstractButton && tip(x).equals(kr.ac.hallym.hcs.app.Messages.get("dock.expand")) && x.isShowing());
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
                && ((javax.swing.JLabel) x).getText().startsWith(kr.ac.hallym.hcs.app.Messages.get("bar.cycleCount", "").trim()));
        if (status != null) {
            Rectangle r = onScreen(status.getParent());
            snapCrop(new Rectangle(r.x, r.y, Math.min(r.width, 1300), r.height), "07b-status-bar");
            // 상태 표시줄 배율 단추(옛 왼쪽 아래 배율 칸 대신): 누르면 단계·화면 맞춤·격자
            AbstractButton zoomButton = (AbstractButton) find(status.getParent(), x -> x instanceof AbstractButton
                    && ((AbstractButton) x).getText() != null && ((AbstractButton) x).getText().endsWith("%"));
            if (zoomButton != null) {
                SwingUtilities.invokeLater(zoomButton::doClick);
                sleep(900);
                Rectangle pop = popupBounds();
                if (pop != null) {
                    pop.add(onScreen(zoomButton));
                    snapCrop(pad(pop, 12), "07c-zoom-menu");
                }
                key(KeyEvent.VK_ESCAPE);
                sleep(300);
            }
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
        JMenuItem item = menuItem(x -> x.getText() != null && x.getText().equals(kr.ac.hallym.hcs.app.Messages.get("splitter.edit")));
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
                && comboContains((JComboBox<?>) x, "R-type"));
        if (preset != null) {
            edt(() -> {
                for (int i = 0; i < preset.getItemCount(); i++) {
                    if (String.valueOf(preset.getItemAt(i)).contains("R-type")) {
                        preset.setSelectedIndex(i);
                    }
                }
            });
            sleep(900);
            snapCrop(pad(d.getBounds(), 10), "08c-splitter-editor-r-type");
        }
        JButton apply = (JButton) find(d, x -> x instanceof JButton && kr.ac.hallym.hcs.app.Messages.get("splitter.apply").equals(((JButton) x).getText()));
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
        // 같은 이름 묶음을 펼친 모습(#135)
        javax.swing.JList<?> list = (javax.swing.JList<?>) find(d, x -> x instanceof javax.swing.JList && x.isShowing());
        if (list != null && list.getModel().getSize() > 0) {
            Rectangle cell = list.getCellBounds(0, 0);
            Point lp = list.getLocationOnScreen();
            robot.mouseMove(lp.x + cell.x + 20, lp.y + cell.y + cell.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            sleep(900);
            snapCrop(pad(d.getBounds(), 10), "09b-find-pc-expanded");
        }
        // 선택기 입력에 붙은 이름: "Multiplexer #1 (select)"처럼 읽는 이름(S-09)
        edt(() -> q.setText("MemtoReg"));
        sleep(900);
        if (list != null && list.getModel().getSize() > 0) {
            Rectangle cell = list.getCellBounds(0, 0);
            Point lp = list.getLocationOnScreen();
            robot.mouseMove(lp.x + cell.x + 20, lp.y + cell.y + cell.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            sleep(900);
            snapCrop(pad(d.getBounds(), 10), "09d-find-memtoreg-expanded");
        }
        JTabbedPane tabs = (JTabbedPane) find(d, x -> x instanceof JTabbedPane);
        edt(() -> tabs.setSelectedIndex(1));
        sleep(900);
        snapCrop(pad(d.getBounds(), 10), "09c-tunnel-names");
        edt(d::dispose);
        sleep(300);
    }

    /** 12: 마우스 오버(부품, 포트). */
    void hover(Project p) throws Exception {
        setZoom(p, 1.5);
        useTool(p, "Edit Tool");
        com.cburch.logisim.comp.Component gate = firstSub(p.getCurrentCircuit(), "regfile");
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
    /** 우클릭 ".s 불러오기"와 같은 길로 path를 불러온다(파일 고르기 창을 스크립트가 고른다). summary: 결과 창 그림 이름. */
    boolean chooseProgram(Project p, String path, String summary, String scene) throws Exception {
        Thread loader = new Thread(() -> {
            try {
                SwingUtilities.invokeAndWait(() -> kr.ac.hallym.hcs.app.palette.PaletteActions.loadProgram(p));
            } catch (Exception e) {
                log.add(scene + ": " + e);
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
            log.add(scene + ": no file chooser");
            return false;
        }
        final JFileChooser chooser = fc;
        SwingUtilities.invokeLater(() -> {
            chooser.setSelectedFile(new File(path).getAbsoluteFile());
            chooser.approveSelection();
        });
        sleep(4000);
        Window msg = window(x -> x instanceof JDialog && x.isShowing());
        if (msg != null) {
            if (summary != null) {
                snapCrop(pad(msg.getBounds(), 10), summary);
            }
            edt(msg::dispose);
        }
        return true;
    }

    /**
     * 25: 캔버스 아래 Cycle View 탭(C-02, C-03). 사람이 그린 demo-datapath(체크리스트 10)를 리셋 뒤 6사이클 돌리고
     * 신호 줄 다섯(clk, pc, halt, ALU Result 선, regfile RD1 선), 사이클 2 보기. 캔버스가 그 사이클 값이 되는 것은
     * PC 둘레 확대 두 장(마지막, 사이클 2)으로 보인다.
     */
    void cycles(Project p) throws Exception {
        activate(p);
        deselect(p);
        edt(() -> kr.ac.hallym.hcs.app.record.Recorder.requestReset(p));
        sleep(900);
        for (int i = 0; i < 12; i++) {
            edt(() -> p.getSimulator().tick());
            sleep(40);
        }
        sleep(800);
        Circuit c = p.getCurrentCircuit();
        for (String name : new String[] {"clk", "pc"}) {
            com.cburch.logisim.comp.Component t = null;
            for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
                if (x.getFactory().getName().equals("Tunnel")
                        && name.equals(kr.ac.hallym.hcs.app.model.Names.label(x))) {
                    t = x;
                    break;
                }
            }
            if (t == null) {
                log.add("25: no tunnel " + name);
                continue;
            }
            final com.cburch.logisim.comp.Component tt = t;
            edt(() -> kr.ac.hallym.hcs.app.cycle.CycleView.addPort(p, c, tt.getEnd(0).getLocation()));
        }
        for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals("Pin") && "halt".equals(kr.ac.hallym.hcs.app.model.Names.label(x))) {
                edt(() -> kr.ac.hallym.hcs.app.cycle.CycleView.addPort(p, c, x.getEnd(0).getLocation()));
            }
        }
        // 서브회로 alu의 Result, regfile의 RD1에 이어진 선
        for (String[] want : new String[][] {{"alu", "Result"}, {"regfile", "RD1"}}) {
            com.cburch.logisim.circuit.Wire found = null;
            for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
                if (!(x.getFactory() instanceof com.cburch.logisim.circuit.SubcircuitFactory)
                        || !x.getFactory().getName().equals(want[0])) {
                    continue;
                }
                for (int e = 0; e < x.getEnds().size(); e++) {
                    if (!want[1].equals(kr.ac.hallym.hcs.app.model.Kinds.portName(x, e))) {
                        continue;
                    }
                    com.cburch.logisim.data.Location at = x.getEnd(e).getLocation();
                    for (com.cburch.logisim.circuit.Wire w : c.getWires()) {
                        if (w.getEnd0().equals(at) || w.getEnd1().equals(at)) {
                            found = w;
                        }
                    }
                }
            }
            if (found == null) {
                log.add("25: no wire at " + want[0] + " " + want[1]);
                continue;
            }
            final com.cburch.logisim.circuit.Wire w = found;
            edt(() -> kr.ac.hallym.hcs.app.cycle.CycleView.addWire(p, c, w));
        }
        sleep(900);
        kr.ac.hallym.hcs.app.cycle.CycleView v = kr.ac.hallym.hcs.app.cycle.CycleView.of(p);
        edt(() -> canvas(p).getHcsZoom().fitCircuit());
        sleep(900);
        snapFull("25a-cycles-full");
        snapCrop(onScreen(v.component()), "25b-cycles-table");
        Bounds pcArea = Bounds.create(250, 130, 420, 150);
        setZoom(p, 2.0);
        centerOn(p, pcArea);
        snapLogical(p, pcArea, "25e-canvas-latest-200");
        edt(() -> v.view(2));
        sleep(1200);
        snapLogical(p, pcArea, "25f-canvas-cycle2-200");
        setZoom(p, 1.0);
        edt(() -> canvas(p).getHcsZoom().fitCircuit());
        sleep(900);
        snapFull("25c-past-cycle-full");
        snapCrop(onScreen(v.component()), "25d-past-cycle-table");
        edt(v::showLatest);
        sleep(500);
    }

    /**
     * 26: Run Until(C-04). demo-datapath를 리셋하고 Cycle View 탭의 Run Until…에서 "PC Is" 0x10을 고른 창, 그리고 멈춘
     * 뒤의 표와 상태 표시줄 알림.
     */
    void runUntil(Project p) throws Exception {
        activate(p);
        deselect(p);
        edt(() -> kr.ac.hallym.hcs.app.record.Recorder.requestReset(p));
        sleep(900);
        kr.ac.hallym.hcs.app.cycle.CycleView v = kr.ac.hallym.hcs.app.cycle.CycleView.of(p);
        Circuit c = p.getCurrentCircuit();
        for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
            String label = kr.ac.hallym.hcs.app.model.Names.label(x);
            if (x.getFactory().getName().equals("Tunnel") && ("clk".equals(label) || "pc".equals(label))
                    || x.getFactory().getName().equals("Pin") && "halt".equals(label)) {
                edt(() -> kr.ac.hallym.hcs.app.cycle.CycleView.addPort(p, c, x.getEnd(0).getLocation()));
            }
        }
        edt(v::open);
        sleep(500);
        SwingUtilities.invokeLater(v::askRunUntil);
        Window w = null;
        for (int i = 0; i < 40 && w == null; i++) {
            sleep(250);
            w = window(x -> x instanceof JDialog && x.isShowing() && find(x, y -> y instanceof JComboBox) != null);
        }
        if (w == null) {
            log.add("26: no Run Until dialog");
            return;
        }
        final Window dialog = w;
        edt(() -> {
            @SuppressWarnings("unchecked")
            JComboBox<Object> kind = (JComboBox<Object>) find(dialog, y -> y instanceof JComboBox);
            kind.setSelectedIndex(0); // PC Is
            javax.swing.JTextField f = (javax.swing.JTextField) find(dialog, y -> y instanceof javax.swing.JTextField
                    && y.isShowing());
            f.setText("0x10");
        });
        sleep(500);
        snapCrop(dialog.getBounds(), "26a-run-until-dialog");
        edt(() -> {
            javax.swing.JButton ok = (javax.swing.JButton) find(dialog, y -> y instanceof javax.swing.JButton
                    && ("OK".equals(((javax.swing.JButton) y).getText())
                    || "확인".equals(((javax.swing.JButton) y).getText())));
            ok.doClick();
        });
        sleep(2500);
        snapCrop(onScreen(v.component()), "26b-run-until-stopped");
        snapFull("26d-full-window");
        Rectangle status = onScreen(p.getFrame().getContentPane());
        status = new Rectangle(status.x, status.y + status.height - 34, status.width, 34);
        snapCrop(status, "26c-status-notice");
    }

    /**
     * 27: 레지스터·메모리 패널(C-05, C-06). demo-datapath(사람이 그린 회로)의 regfile을 "Mark as Register File"로
     * 표시하고 6사이클 돈 뒤 Registers 탭과 Register Mapping 창. 스택은 작은 회로 stack-demo를 6사이클 돌린 뒤 Memory 탭과
     * Registers 탭(표시 없음: 모든 레지스터 나열).
     */
    void machinePanels(Project demo) throws Exception {
        activate(demo);
        deselect(demo);
        Circuit rf = demo.getLogisimFile().getCircuit("regfile");
        edt(() -> demo.doAction(kr.ac.hallym.hcs.app.cycle.RegisterFile.markAction(demo.getLogisimFile(), rf,
                true)));
        edt(() -> kr.ac.hallym.hcs.app.record.Recorder.requestReset(demo));
        sleep(900);
        for (int i = 0; i < 12; i++) {
            edt(() -> demo.getSimulator().tick());
            sleep(40);
        }
        sleep(800);
        kr.ac.hallym.hcs.app.cycle.CycleView v = kr.ac.hallym.hcs.app.cycle.CycleView.of(demo);
        edt(v::open);
        edt(() -> v.showSide(0));
        sleep(700);
        snapFull("27a-registers-full");
        snapCrop(onScreen(v.sideComponent()), "27b-registers-panel");
        SwingUtilities.invokeLater(() -> kr.ac.hallym.hcs.app.cycle.RegisterMappingDialog.show(demo, rf));
        Window w = null;
        for (int i = 0; i < 40 && w == null; i++) {
            sleep(250);
            w = window(x -> x instanceof JDialog && x.isShowing() && find(x, y -> y instanceof JComboBox) != null);
        }
        if (w != null) {
            snapCrop(w.getBounds(), "27c-register-mapping");
            final Window dialog = w;
            edt(dialog::dispose);
        } else {
            log.add("27: no mapping dialog");
        }
        sleep(400);

        // 스택: 사람이 그린 작은 회로 stack-demo(체크리스트 10). 사이클마다 $sp가 4 내려가고 그 칸에 count를 쓴다
        Project sd = open("tests/circ/stack-demo.circ");
        activate(sd);
        deselect(sd);
        edt(() -> kr.ac.hallym.hcs.app.record.Recorder.requestReset(sd));
        sleep(900);
        for (int i = 0; i < 12; i++) {
            edt(() -> sd.getSimulator().tick());
            sleep(40);
        }
        sleep(800);
        kr.ac.hallym.hcs.app.cycle.CycleView sv = kr.ac.hallym.hcs.app.cycle.CycleView.of(sd);
        edt(sv::open);
        edt(() -> sv.showSide(1));
        edt(() -> canvas(sd).getHcsZoom().fitCircuit());
        sleep(900);
        snapCrop(onScreen(sv.sideComponent()), "27d-stack");
        edt(() -> sv.showSide(0));
        sleep(500);
        snapCrop(onScreen(sv.sideComponent()), "27e-registers-unmarked");
        edt(() -> sv.showSide(1));
        sleep(500);
        snapFull("27f-stack-demo-full");
    }

    /**
     * 36: 제출 파일(E-06)과 그림 내보내기(E-07). demo-datapath와 sum.s를 출력 폴더에 복사해 열고(저장소 파일을
     * 건드리지 않게), Instruction Memory가 sum.s를 가리키게 저장한 뒤 File › Create Submission… 점검 창, 그리고 File ›
     * Export Image… 창.
     */
    void submitAndExport(Project base) throws Exception {
        File dir = new File(out, "submit-demo");
        dir.mkdirs();
        java.nio.file.Files.copy(new File("tests/circ/demo-datapath.circ").toPath(), new File(dir,
                "demo-datapath.circ").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        java.nio.file.Files.copy(new File("tests/mips/sum.s").toPath(), new File(dir, "sum.s").toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Project p = open(new File(dir, "demo-datapath.circ").getPath());
        activate(p);
        Circuit c = p.getCurrentCircuit();
        edt(() -> {
            for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
                if (x.getFactory().getName().equals("Instruction Memory")) {
                    @SuppressWarnings("unchecked")
                    com.cburch.logisim.data.Attribute<Object> a = (com.cburch.logisim.data.Attribute<Object>) x
                            .getAttributeSet().getAttribute("source");
                    com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(c);
                    m.set(x, a, "sum.s");
                    p.doAction(m.toAction(null));
                }
            }
        });
        sleep(3000); // .s 자동 재로드(C-09)가 sum.s를 불러온 뒤 저장한다
        edt(() -> com.cburch.logisim.proj.ProjectActions.doSave(p));
        sleep(1500);
        edt(() -> canvas(p).getHcsZoom().fitCircuit());
        kr.ac.hallym.hcs.app.submit.Submission plan = kr.ac.hallym.hcs.app.submit.Submission.plan(p.getLogisimFile(),
                kr.ac.hallym.hcs.app.diag.Diagnostics.of(p).list().size(), p.isFileDirty());
        SwingUtilities.invokeLater(() -> kr.ac.hallym.hcs.app.submit.SubmissionDialog.showPlan(p, plan));
        Window w = null;
        for (int i = 0; i < 40 && w == null; i++) {
            sleep(250);
            w = window(x -> x instanceof JDialog && x.isShowing()
                    && kr.ac.hallym.hcs.app.Messages.get("submit.title").equals(((JDialog) x).getTitle()));
        }
        if (w == null) {
            log.add("36: no submission window");
        } else {
            sleep(500);
            snapCrop(w.getBounds(), "36a-submission-checks");
            final Window sw = w;
            edt(sw::dispose);
        }
        SwingUtilities.invokeLater(() -> kr.ac.hallym.hcs.app.export.ImageExport.show(p));
        Window e = null;
        for (int i = 0; i < 40 && e == null; i++) {
            sleep(250);
            e = window(x -> x instanceof JDialog && x.isShowing()
                    && kr.ac.hallym.hcs.app.Messages.get("export.title").equals(((JDialog) x).getTitle()));
        }
        if (e == null) {
            log.add("36: no export window");
        } else {
            sleep(500);
            snapCrop(e.getBounds(), "36b-export-image");
            final Window ew = e;
            edt(ew::dispose);
        }
        // 내보낸 결과 한 장(SVG를 같은 내용 PNG로 보이기 위해 2배 PNG)
        File png = new File(out, "36c-export-png-2x.png");
        kr.ac.hallym.hcs.app.export.ImageExport.write(png, kr.ac.hallym.hcs.app.export.ImageExport.Format.PNG,
                canvas(p), c, p.getCircuitState(), null, true, 2);
    }

    /**
     * 35: 배치 편집(E-01·E-02). 새 파일에 레지스터 R0 하나를 두고 우클릭 Duplicate N…(3개, 아래로)으로 R1~R3을 만든다.
     * 흩어진 NOT 게이트 셋을 골라 우클릭(Align·Distribute·선택 필터)하고 Align › Left.
     */
    void arrange(Project base) throws Exception {
        Project p = newProject(base);
        Circuit c = p.getCurrentCircuit();
        edt(() -> {
            com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(c);
            com.cburch.logisim.comp.ComponentFactory reg = find(p, "Memory", "Register");
            com.cburch.logisim.data.AttributeSet as = reg.createAttributeSet();
            as.setValue(com.cburch.logisim.instance.StdAttr.WIDTH, com.cburch.logisim.data.BitWidth.create(32));
            as.setValue(com.cburch.logisim.instance.StdAttr.LABEL, "R0");
            m.add(reg.createComponent(Location.create(200, 120), as));
            com.cburch.logisim.comp.ComponentFactory not = find(p, "Gates", "NOT Gate");
            int[][] at = {{520, 110}, {570, 190}, {500, 280}};
            for (int[] xy : at) {
                m.add(not.createComponent(Location.create(xy[0], xy[1]), not.createAttributeSet()));
            }
            p.doAction(m.toAction(null));
        });
        sleep(600);
        com.cburch.logisim.comp.Component r0 = byLabel(c, "R0");
        setZoom(p, 1.5);
        scrollTo(p, 0, 0);
        menuAt(p, r0.getLocation().translate(-20, 0), "35a-menu-duplicate-n");
        final com.cburch.logisim.comp.Component r = r0;
        SwingUtilities.invokeLater(() -> kr.ac.hallym.hcs.app.edit.ArrangeActions.duplicateN(p, c,
                java.util.Collections.singletonList(r)));
        Window d = null;
        for (int i = 0; i < 40 && d == null; i++) {
            sleep(250);
            d = window(x -> x instanceof JDialog && x.isShowing() && find(x, y -> y instanceof javax.swing.JSpinner)
                    != null);
        }
        if (d == null) {
            log.add("35: no Duplicate N dialog");
            return;
        }
        sleep(500);
        snapCrop(d.getBounds(), "35b-duplicate-n-dialog");
        // 글자 있는 첫 단추가 OK(스피너의 화살표 단추는 글자가 없다)
        javax.swing.JButton ok = (javax.swing.JButton) find(d, y -> y instanceof javax.swing.JButton
                && !((javax.swing.JButton) y).getText().isEmpty());
        if (ok == null) {
            log.add("35: no OK button");
            return;
        }
        edt(ok::doClick);
        sleep(900);
        List<com.cburch.logisim.comp.Component> nots = new java.util.ArrayList<>();
        for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals("NOT Gate")) {
                nots.add(x);
            }
        }
        edt(() -> {
            p.doAction(com.cburch.logisim.gui.main.SelectionActions.dropAll(p.getSelection()));
            p.getSelection().addAll(nots);
        });
        sleep(500);
        snapFull("35c-copies-and-loose-gates");
        menuAt(p, nots.get(0).getLocation().translate(-15, 0), "35d-menu-arrange");
        edt(() -> kr.ac.hallym.hcs.app.edit.ArrangeActions.align(p, c, nots, kr.ac.hallym.hcs.app.edit.Arrange.Align.LEFT));
        sleep(700);
        snapFull("35e-aligned-left");
    }

    /** 원조 라이브러리의 부품 팩토리. */
    static com.cburch.logisim.comp.ComponentFactory find(Project p, String lib, String name) {
        com.cburch.logisim.tools.AddTool t = (com.cburch.logisim.tools.AddTool) p.getLogisimFile().getLibrary(lib)
                .getTool(name);
        return t.getFactory();
    }

    /**
     * 34: Undo History(E-05)와 단축키 표·설정(E-09). demo-datapath에 선 세 개를 그린 뒤 하나를 되돌린 상태의 기록
     * 창, ? 표, Customize…로 연 설정 창. 끝나면 그린 선을 되돌린다.
     */
    void historyAndKeys(Project p) throws Exception {
        activate(p);
        deselect(p);
        Circuit c = p.getCurrentCircuit();
        String[] names = {"Add Wire", "Add Wire", "Move Selection"};
        for (int i = 0; i < 3; i++) {
            final int y = 640 + 20 * i;
            final String n = names[i];
            edt(() -> {
                com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(c);
                m.add(com.cburch.logisim.circuit.Wire.create(Location.create(200, y), Location.create(300, y)));
                p.doAction(m.toAction(() -> n));
            });
            sleep(200);
        }
        edt(p::undoAction);
        sleep(400);
        SwingUtilities.invokeLater(() -> kr.ac.hallym.hcs.app.edit.UndoHistory.show(p, p.getFrame()));
        Window h = null;
        for (int i = 0; i < 40 && h == null; i++) {
            sleep(250);
            h = window(x -> x instanceof JDialog && x.isShowing() && find(x, y -> "history.list".equals(y.getName()))
                    != null);
        }
        if (h == null) {
            log.add("34: no history window");
        } else {
            sleep(500);
            snapCrop(h.getBounds(), "34a-undo-history");
            final Window hw = h;
            edt(hw::dispose);
        }
        // ? 표와 Customize…
        SwingUtilities.invokeLater(() -> kr.ac.hallym.hcs.app.keys.Shortcuts.showTable(p.getFrame()));
        Window t = null;
        for (int i = 0; i < 40 && t == null; i++) {
            sleep(250);
            t = window(x -> x instanceof JDialog && x.isShowing()
                    && kr.ac.hallym.hcs.app.Messages.get("keys.title").equals(((JDialog) x).getTitle()));
        }
        if (t == null) {
            log.add("34: no shortcut table");
        } else {
            sleep(500);
            snapCrop(t.getBounds(), "34b-shortcut-table");
            javax.swing.JButton custom = (javax.swing.JButton) find(t, y -> y instanceof javax.swing.JButton
                    && kr.ac.hallym.hcs.app.Messages.get("keys.customize").equals(((javax.swing.JButton) y).getText()));
            SwingUtilities.invokeLater(custom::doClick);
            Window k = null;
            for (int i = 0; i < 40 && k == null; i++) {
                sleep(250);
                k = window(x -> x instanceof JDialog && x.isShowing() && find(x, y -> "keys.table".equals(y.getName()))
                        != null);
            }
            if (k == null) {
                log.add("34: no settings window");
            } else {
                sleep(500);
                snapCrop(k.getBounds(), "34c-shortcut-settings");
                final Window kw = k;
                edt(kw::dispose);
            }
        }
        edt(p::undoAction);
        edt(p::undoAction);
        sleep(300);
    }

    /** 33: About 창(E-11). 학교 엠블럼, 이름·버전, 설명, 캐릭터 한 장, License·Notices 탭. */
    void about(Project p) throws Exception {
        activate(p);
        SwingUtilities.invokeLater(() -> kr.ac.hallym.hcs.app.about.AboutDialog.show(p.getFrame()));
        Window w = null;
        for (int i = 0; i < 40 && w == null; i++) {
            sleep(250);
            w = window(x -> x instanceof JDialog && x.isShowing() && find(x, y -> y instanceof JTabbedPane
                    && "about.tabs".equals(y.getName())) != null);
        }
        if (w == null) {
            log.add("33: no About window");
            return;
        }
        sleep(600);
        snapCrop(w.getBounds(), "33a-about-license");
        final Window dialog = w;
        JTabbedPane tabs = (JTabbedPane) find(dialog, y -> y instanceof JTabbedPane && "about.tabs".equals(y.getName()));
        edt(() -> tabs.setSelectedIndex(1));
        sleep(500);
        snapCrop(dialog.getBounds(), "33b-about-notices");
        edt(dialog::dispose);
        sleep(300);
    }

    /**
     * 32: 진동(D-02)과 MIPS 부품 값 문제(D-04). 고장 회로 모음(D-06)의 작은 회로 두 개: NAND 되먹임이 클럭 1에서
     * 진동하면 Messages에 고리와 Reset 단추, 정렬 안 된 주소를 읽는 Data Memory는 몸체의 빨간 글자와 같은 문구.
     */
    void oscillationAndMips() throws Exception {
        Project osc = open("tests/circ/faults/dynamic-oscillation.circ");
        activate(osc);
        deselect(osc);
        edt(() -> kr.ac.hallym.hcs.app.record.Recorder.requestReset(osc));
        sleep(900);
        // 첫 틱에서 발진해 시뮬레이션이 꺼진다. 꺼진 뒤의 틱은 보호기(D-091)가 거절한다
        edt(() -> osc.getSimulator().tick());
        sleep(1800);
        edt(() -> canvas(osc).getHcsZoom().fitCircuit());
        messagesTab(osc);
        sleep(900);
        snapFull("32a-oscillation");
        javax.swing.JList<?> list = diagList(osc);
        if (list != null) {
            snapCrop(pad(onScreen(list.getParent().getParent().getParent()), 4), "32b-oscillation-message");
        } else {
            log.add("32: no oscillation message");
        }

        Project mem = open("tests/circ/faults/mips-unaligned.circ");
        activate(mem);
        deselect(mem);
        edt(() -> kr.ac.hallym.hcs.app.record.Recorder.requestReset(mem));
        sleep(900);
        for (int i = 0; i < 2; i++) {
            edt(() -> mem.getSimulator().tick());
            sleep(200);
        }
        sleep(1500);
        edt(() -> kr.ac.hallym.hcs.app.cycle.CycleView.of(mem).open()); // 아래 패널을 펴서 메시지가 보이게
        sleep(400);
        messagesTab(mem);
        edt(() -> canvas(mem).getHcsZoom().fitCircuit());
        sleep(900);
        snapFull("32c-mips-unaligned");
        com.cburch.logisim.comp.Component dm = null;
        for (com.cburch.logisim.comp.Component x : mem.getCurrentCircuit().getNonWires()) {
            if (x.getFactory().getName().equals("Data Memory")) {
                dm = x;
            }
        }
        if (dm != null) {
            snapLogical(mem, dm.getBounds().expand(20), "32d-mips-body");
        }
    }

    /** 아래 Messages 탭을 고른다. */
    void messagesTab(Project p) throws Exception {
        javax.swing.JList<?> list = diagList(p);
        if (list != null) {
            edt(() -> {
                JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, list);
                tabs.setSelectedIndex(0);
            });
        }
    }

    /** 진단이 든 Messages 목록(없으면 null). */
    javax.swing.JList<?> diagList(Project p) {
        return (javax.swing.JList<?>) find(p.getFrame(), x -> x instanceof javax.swing.JList
                && ((javax.swing.JList<?>) x).getModel().getSize() > 0
                && ((javax.swing.JList<?>) x).getModel().getElementAt(0) instanceof kr.ac.hallym.hcs.app.diag.Diagnostic);
    }

    /**
     * 31: 동적 진단(D-01·D-03·D-05). 사람이 그린 demo-datapath에서 학생이 RegWrite 입력 핀을 3상태로 두어 값이 정해지지
     * 않은 경우(파랑 X). 몇 사이클 돌리면 Messages에 그 사이클과 원인이 한 줄로 나오고, 누르면 사이클 뷰가 그 사이클로
     * 가며 원인 핀을 고른다. 선 우클릭 Find E/X Origin과 그 알림. 끝나면 되돌린다.
     */
    void dynamicMessages(Project p) throws Exception {
        activate(p);
        deselect(p);
        Circuit c = p.getCurrentCircuit();
        com.cburch.logisim.comp.Component rw = null;
        for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals("Pin")
                    && "RegWrite".equals(x.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.LABEL))) {
                rw = x;
            }
        }
        if (rw == null) {
            log.add("31: RegWrite pin not found");
            return;
        }
        final com.cburch.logisim.comp.Component pin = rw;
        edt(() -> {
            com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(c);
            m.set(pin, com.cburch.logisim.std.wiring.Pin.ATTR_TRISTATE, Boolean.TRUE);
            p.doAction(m.toAction(null));
        });
        sleep(1200);
        edt(() -> kr.ac.hallym.hcs.app.record.Recorder.requestReset(p));
        sleep(900);
        for (int i = 0; i < 6; i++) {
            edt(() -> p.getSimulator().tick());
            sleep(60);
        }
        sleep(1500);
        edt(() -> canvas(p).getHcsZoom().fitCircuit());
        @SuppressWarnings("unchecked")
        javax.swing.JList<Object> list = (javax.swing.JList<Object>) find(p.getFrame(),
                x -> x instanceof javax.swing.JList && ((javax.swing.JList<?>) x).getModel().getSize() > 0
                        && ((javax.swing.JList<?>) x).getModel().getElementAt(0)
                                instanceof kr.ac.hallym.hcs.app.diag.Diagnostic);
        if (list == null) {
            log.add("31: no dynamic message");
        } else {
            edt(() -> {
                JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, list);
                tabs.setSelectedIndex(0);
            });
            sleep(900);
            snapFull("31a-dynamic-message");
            Rectangle panel = onScreen(list.getParent().getParent().getParent());
            snapCrop(pad(panel, 4), "31b-message-row");
            Rectangle cell = list.getCellBounds(0, 0);
            Point s = list.getLocationOnScreen();
            robot.mouseMove(s.x + cell.x + 40, s.y + cell.y + cell.height / 2);
            sleep(200);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            sleep(1500);
            snapFull("31c-message-clicked");
        }
        // 파랑 X 선 우클릭: Find E/X Origin
        com.cburch.logisim.circuit.Wire blue = null;
        for (com.cburch.logisim.circuit.Wire w : c.getWires()) {
            com.cburch.logisim.data.Value v = p.getCircuitState().getValue(w.getEnd0());
            if (v != null && !v.isFullyDefined() && w.getLength() >= 30) {
                blue = w;
                break;
            }
        }
        if (blue == null) {
            log.add("31: no blue wire");
        } else {
            deselect(p);
            Location mid = Location.create((blue.getEnd0().getX() + blue.getEnd1().getX()) / 2,
                    (blue.getEnd0().getY() + blue.getEnd1().getY()) / 2);
            menuAt(p, mid, "31d-menu-find-origin");
            final com.cburch.logisim.circuit.Wire bw = blue;
            edt(() -> kr.ac.hallym.hcs.app.diag.FindOrigin.run(p, c, bw));
            sleep(900);
            Rectangle all = onScreen(p.getFrame().getContentPane());
            snapCrop(new Rectangle(all.x, all.y + all.height - 34, all.width, 34), "31e-origin-notice");
        }
        edt(p::undoAction);
        sleep(600);
        edt(() -> kr.ac.hallym.hcs.app.record.Recorder.requestReset(p));
        sleep(900);
        deselect(p);
    }

    /**
     * 30: 버스 값 칩과 활성 경로(C-08). 사람이 그린 demo-datapath를 두 사이클 돌린 뒤 사이클 뷰를 연다. 버스마다 지금
     * 값 칩(16진, 이름 있는 버스는 이름 옆), MemtoReg MUX가 고른 입력(ALU Result)이 진한 띠. 진법을 바꾼 모습과
     * Active Path를 끈 모습도 찍는다.
     */
    void busValuesAndActivePath(Project demo) throws Exception {
        activate(demo);
        deselect(demo);
        kr.ac.hallym.hcs.app.labels.BusValues.Mode before = kr.ac.hallym.hcs.app.labels.BusValues.mode();
        edt(() -> kr.ac.hallym.hcs.app.labels.BusValues.setMode(kr.ac.hallym.hcs.app.labels.BusValues.Mode.HEX));
        edt(() -> kr.ac.hallym.hcs.app.record.Recorder.requestReset(demo));
        sleep(900);
        for (int i = 0; i < 4; i++) {
            edt(() -> demo.getSimulator().tick());
            sleep(40);
        }
        sleep(800);
        kr.ac.hallym.hcs.app.cycle.CycleView v = kr.ac.hallym.hcs.app.cycle.CycleView.of(demo);
        edt(v::open);
        edt(() -> v.showSide(0));
        edt(() -> canvas(demo).getHcsZoom().fitCircuit());
        sleep(900);
        snapFull("30a-bus-values-full");
        snapCrop(onScreen(canvas(demo)), "30b-bus-values-canvas");
        // 배율(체크리스트 4): 25%와 400%에서 칩 글자와 활성 경로 띠. 스플리터 → regfile → ALU 구간
        com.cburch.logisim.data.Bounds span = null;
        for (com.cburch.logisim.comp.Component x : demo.getCurrentCircuit().getNonWires()) {
            String f = x.getFactory().getName();
            if (f.equals("Splitter") || f.equals("regfile")) {
                span = span == null ? x.getBounds() : span.add(x.getBounds());
            }
        }
        setZoom(demo, 0.25);
        centerOn(demo, span);
        sleep(700);
        snapCrop(onScreen(canvas(demo).getParent()), "30e-bus-values-25");
        setZoom(demo, 4.0);
        centerOn(demo, span);
        sleep(700);
        snapCrop(onScreen(canvas(demo).getParent()), "30f-bus-values-400");
        edt(() -> canvas(demo).getHcsZoom().fitCircuit());
        sleep(700);
        edt(() -> kr.ac.hallym.hcs.app.labels.BusValues.setMode(kr.ac.hallym.hcs.app.labels.BusValues.Mode.SIGNED));
        sleep(600);
        snapCrop(onScreen(canvas(demo)), "30c-bus-values-signed");
        edt(() -> kr.ac.hallym.hcs.app.labels.BusValues.setMode(kr.ac.hallym.hcs.app.labels.BusValues.Mode.OFF));
        edt(() -> v.setActivePath(false));
        sleep(600);
        snapCrop(onScreen(canvas(demo)), "30d-off");
        edt(() -> v.setActivePath(true));
        edt(() -> kr.ac.hallym.hcs.app.labels.BusValues.setMode(before));
    }

    /**
     * 29: Instruction 탭과 필드 색(C-07). 사람이 그린 demo-datapath(스플리터 팔 op·rs·rt·rd·shamt·funct)에서 R 형식
     * 명령어의 사이클을 고르면 캔버스의 필드 선이 필드 색 띠를 두른다.
     */
    void instructionFields(Project demo) throws Exception {
        activate(demo);
        deselect(demo);
        // 필드 색만 보이게 버스 값 칩은 이 장면에서 끈다(C-08 기본값은 켬)
        kr.ac.hallym.hcs.app.labels.BusValues.Mode busBefore = kr.ac.hallym.hcs.app.labels.BusValues.mode();
        edt(() -> kr.ac.hallym.hcs.app.labels.BusValues.setMode(kr.ac.hallym.hcs.app.labels.BusValues.Mode.OFF));
        edt(() -> kr.ac.hallym.hcs.app.record.Recorder.requestReset(demo));
        sleep(900);
        for (int i = 0; i < 4; i++) {
            edt(() -> demo.getSimulator().tick());
            sleep(40);
        }
        sleep(800);
        kr.ac.hallym.hcs.app.cycle.CycleView v = kr.ac.hallym.hcs.app.cycle.CycleView.of(demo);
        edt(v::open);
        edt(() -> v.showSide(2));
        edt(() -> canvas(demo).getHcsZoom().fitCircuit());
        sleep(900);
        snapFull("29a-instruction-fields-full");
        snapCrop(onScreen(v.sideComponent()), "29b-instruction-tab");
        snapCrop(onScreen(canvas(demo)), "29c-field-colors-canvas");
        // 배율(체크리스트 4): 25%에서 띠가 뭉치지 않는지, 400%에서 포트 글자를 덮지 않는지. 스플리터 → regfile 구간
        com.cburch.logisim.data.Bounds span = null;
        for (com.cburch.logisim.comp.Component x : demo.getCurrentCircuit().getNonWires()) {
            String f = x.getFactory().getName();
            if (f.equals("Splitter") || f.equals("regfile")) {
                span = span == null ? x.getBounds() : span.add(x.getBounds());
            }
        }
        setZoom(demo, 0.25);
        centerOn(demo, span);
        sleep(700);
        snapCrop(onScreen(canvas(demo).getParent()), "29d-field-colors-25");
        setZoom(demo, 4.0);
        centerOn(demo, span);
        sleep(700);
        snapCrop(onScreen(canvas(demo).getParent()), "29e-field-colors-400");
        // 비교(체크리스트 5): 같은 장면에서 Registers 탭을 고르면 띠가 없다(원조에 없는 덧그림이라 -orig 대신)
        edt(() -> canvas(demo).getHcsZoom().fitCircuit());
        edt(() -> v.showSide(0));
        sleep(900);
        snapCrop(onScreen(canvas(demo)), "29f-no-field-colors");
        edt(() -> kr.ac.hallym.hcs.app.labels.BusValues.setMode(busBefore));
    }

    /**
     * 28: Console 탭과 .s 자동 재로드(C-09). 사람이 그린 작은 회로 console-demo(A0 = 'A' + count, V0 = 11, count 6에서
     * exit)를 끝까지 돌린 뒤 Console 탭, 그리고 demo-datapath에 불러온 .s(임시 복사본)를 고쳐 저장했을 때의 상태
     * 표시줄 알림.
     */
    void consoleAndReload(Project demo) throws Exception {
        Project cd = open("tests/circ/console-demo.circ");
        activate(cd);
        deselect(cd);
        edt(() -> kr.ac.hallym.hcs.app.record.Recorder.requestReset(cd));
        sleep(900);
        for (int i = 0; i < 20; i++) {
            edt(() -> cd.getSimulator().tick());
            sleep(40);
        }
        sleep(800);
        javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) find(cd.getFrame(),
                x -> x instanceof javax.swing.JTabbedPane
                        && ((javax.swing.JTabbedPane) x).indexOfTab(kr.ac.hallym.hcs.app.Messages.get("console.tab")) >= 0);
        if (tabs == null) {
            log.add("28: no Console tab");
            return;
        }
        edt(() -> {
            tabs.setSelectedIndex(tabs.indexOfTab(kr.ac.hallym.hcs.app.Messages.get("console.tab")));
            kr.ac.hallym.hcs.app.cycle.CycleView.of(cd).open();
            tabs.setSelectedIndex(tabs.indexOfTab(kr.ac.hallym.hcs.app.Messages.get("console.tab")));
        });
        sleep(600);
        // 아래 패널을 편 뒤에 화면 맞춤(C-09 검토: 맞춘 뒤 패널이 열리면 캔버스가 줄어 가운데에서 벗어난다)
        edt(() -> canvas(cd).getHcsZoom().fitCircuit());
        sleep(900);
        snapFull("28a-console-full");
        snapCrop(onScreen(tabs), "28b-console-tab");

        // .s 자동 재로드: 임시 복사본을 불러와 고쳐 저장한다
        File copy = new File(out, "reload-demo.s");
        java.nio.file.Files.copy(new File("tests/mips/sum.s").toPath(), copy.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        activate(demo);
        if (!chooseProgram(demo, copy.getAbsolutePath(), null, "28")) {
            return;
        }
        sleep(2500); // 감시가 처음 한 번 본다
        String text = new String(java.nio.file.Files.readAllBytes(copy.toPath()), StandardCharsets.UTF_8)
                .replace("li    $t2, 11", "li    $t2, 5");
        java.nio.file.Files.write(copy.toPath(), text.getBytes(StandardCharsets.UTF_8));
        copy.setLastModified(System.currentTimeMillis() + 2000);
        sleep(3000);
        Rectangle all = onScreen(demo.getFrame().getContentPane());
        snapCrop(new Rectangle(all.x, all.y + all.height - 34, all.width, 34), "28c-reload-notice");
        copy.delete();
    }

    void program(Project p) throws Exception {
        if (!chooseProgram(p, "tests/mips/factorial.s", "11a-load-summary", "11")) {
            return;
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
        int ran = runCycles(p, 400); // halt(= Console Exit)에서 멈춘다. 끝난 뒤에도 돌리면 exit 뒤 코드가 돈다
        System.out.println("program halted after " + (45 + ran) + " cycles");
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

    /**
     * 클럭을 최대 n 사이클 돌린다. 회로에 "halt" 출력 핀이 있으면 1이 되는 사이클에서 멈춘다(검토 2차 B: exit 뒤에도
     * 돌리면 CPU가 syscall 10 다음 코드를 계속 실행해 Stack이 자란다). 돌린 사이클 수를 돌려준다.
     */
    int runCycles(Project p, int n) throws Exception {
        com.cburch.logisim.comp.Component halt = null;
        for (com.cburch.logisim.comp.Component x : p.getCurrentCircuit().getNonWires()) {
            if (x.getFactory().getName().equals("Pin") && "halt".equals(x.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.LABEL))) {
                halt = x;
            }
        }
        int cycles = 0;
        while (cycles < n) {
            edt(() -> p.getSimulator().tick());
            sleep(8);
            edt(() -> p.getSimulator().tick());
            sleep(8);
            cycles++;
            if (halt != null) {
                final com.cburch.logisim.data.Location at = halt.getEnd(0).getLocation();
                final com.cburch.logisim.data.Value[] v = new com.cburch.logisim.data.Value[1];
                edt(() -> v[0] = p.getCircuitState().getValue(at));
                if (v[0] == com.cburch.logisim.data.Value.TRUE) {
                    break;
                }
            }
        }
        sleep(1500);
        return cycles;
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
            int cx = (int) ((b.getX() + b.getWidth() / 2.0) * z) + (orig ? 0 : c.getHcsOriginX());
            int cy = (int) ((b.getY() + b.getHeight() / 2.0) * z) + (orig ? 0 : c.getHcsOriginY());
            c.scrollRectToVisible(new Rectangle(Math.max(0, cx - vis.width / 2), Math.max(0, cy - vis.height / 2),
                    vis.width, vis.height));
        });
        sleep(900);
    }

    Point screen(Project p, Location l) throws Exception {
        double z = zoom(p);
        return call(() -> {
            Point o = canvas(p).getLocationOnScreen();
            // 포크의 화면 맞춤 원점 이동(S-10). 원조에는 없다
            int ox = orig ? 0 : canvas(p).getHcsOriginX();
            int oy = orig ? 0 : canvas(p).getHcsOriginY();
            return new Point(o.x + ox + (int) Math.round(l.getX() * z), o.y + oy + (int) Math.round(l.getY() * z));
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
