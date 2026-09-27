/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;

import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.Projects;

/**
 * Windows 실제 실행 검증(R-02): 포장한 런타임으로 앱을 띄워 demo-datapath를 열고, 실행 이미지(.hmx)를 불러오고, 10사이클 돌린 뒤 화면을
 * 찍는다. 배율은 -Dsun.java2d.uiScale로 준다. 쓰기: java -cp "<app jar>;tools/winsmoke" Smoke <circ> <hmx> <out.png>
 * 실패하면 0이 아닌 코드로 끝난다. 환경설정 폴더가 비어 있으면(첫 실행) 창이 작업 영역의 90% 이상인지, 도구 모음 단추가
 * 잘리지 않고 Run·Load Program이 보이는지, 캔버스가 창 내부의 절반 이상인지도 확인한다(v1.0.2 X-05, v1.0.3 Y-01).
 */
public final class Smoke {
    static Project project() {
        java.awt.Frame top = Projects.getTopFrame();
        if (top instanceof com.cburch.logisim.gui.main.Frame) {
            return ((com.cburch.logisim.gui.main.Frame) top).getProject();
        }
        java.util.List<Project> ps = Projects.getOpenProjects();
        return ps.isEmpty() ? null : ps.get(ps.size() - 1);
    }

    static java.awt.Component find(java.awt.Container root, Class<?> type) {
        for (java.awt.Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return c;
            }
            if (c instanceof java.awt.Container) {
                java.awt.Component r = find((java.awt.Container) c, type);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    static String pc(Project p) throws Exception {
        AtomicReference<String> r = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> r.set(kr.ac.hallym.hcs.app.sim.StatusModel.pc(p.getCircuitState())));
        return r.get();
    }

    static void checkFirstRunWindow() throws Exception {
        java.awt.Frame top = Projects.getTopFrame();
        Rectangle work = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        java.awt.Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle b = top.getBounds();
        boolean max = (top.getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) != 0;
        System.out.println("SMOKE: screen " + screen.width + "x" + screen.height + " work " + work.width + "x"
                + work.height + " window " + b.width + "x" + b.height + " at " + b.x + "," + b.y + " maximized="
                + max);
        if (b.width < work.width * 0.9 || b.height < work.height * 0.9) {
            System.err.println("SMOKE: first-run window smaller than 90% of the work area");
            System.exit(5);
        }
        java.util.List<String> problems = new java.util.ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Component tb = find(top, kr.ac.hallym.hcs.app.sim.OverflowToolbar.class);
            if (tb == null) {
                problems.add("no toolbar");
                return;
            }
            kr.ac.hallym.hcs.app.sim.OverflowToolbar ot = (kr.ac.hallym.hcs.app.sim.OverflowToolbar) tb;
            Rectangle box = new Rectangle(0, 0, tb.getWidth(), tb.getHeight());
            for (java.awt.Component c : ot.shownComponents()) {
                if (!c.isVisible() || !box.contains(c.getBounds())) {
                    problems.add("clipped toolbar button " + c.getBounds() + " in " + box);
                }
            }
            if (ot.moreButton().isVisible() && !box.contains(ot.moreButton().getBounds())) {
                problems.add("clipped » button");
            }
            for (String keep : new String[] {"bar.run", "bar.program"}) {
                if (!ot.shownKeys().contains(keep)) {
                    problems.add(keep + " is not on the toolbar: " + ot.shownKeys());
                }
            }
            System.out.println("SMOKE: toolbar " + tb.getWidth() + "px shown " + ot.shownKeys().size() + " icons-only "
                    + ot.iconsOnlyNow() + " overflow " + ot.overflowKeys());
        });
        // Y-01(v1.0.3): 캔버스(스크롤 영역)가 창 내부의 절반 이상(가로·세로)이어야 한다
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Component canvas = find(top, com.cburch.logisim.gui.main.Canvas.class);
            java.awt.Component sp = canvas == null ? null
                    : SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, canvas);
            java.awt.Container content = ((javax.swing.JFrame) top).getContentPane();
            if (sp == null) {
                problems.add("no canvas");
                return;
            }
            System.out.println("SMOKE: canvas " + sp.getWidth() + "x" + sp.getHeight() + " of content "
                    + content.getWidth() + "x" + content.getHeight());
            if (sp.getWidth() < content.getWidth() / 2) {
                problems.add("canvas width " + sp.getWidth() + " < half of " + content.getWidth());
            }
            if (sp.getHeight() < content.getHeight() / 2) {
                problems.add("canvas height " + sp.getHeight() + " < half of " + content.getHeight());
            }
        });
        if (!problems.isEmpty()) {
            System.err.println("SMOKE: " + String.join("; ", problems));
            System.exit(6);
        }
    }

    static void checkTutorial() throws Exception {
        java.awt.Frame top = Projects.getTopFrame();
        java.util.List<String> problems = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) { // 튜토리얼은 창이 활성화된 뒤 잠시 뒤에 뜬다
            boolean[] up = new boolean[1];
            SwingUtilities.invokeAndWait(() -> up[0] = ((javax.swing.JFrame) top).getRootPane()
                    .getGlassPane() instanceof kr.ac.hallym.hcs.app.tutorial.Tour.Overlay);
            if (up[0]) {
                break;
            }
            Thread.sleep(250);
        }
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Component glass = ((javax.swing.JFrame) top).getRootPane().getGlassPane();
            if (!(glass instanceof kr.ac.hallym.hcs.app.tutorial.Tour.Overlay)) {
                problems.add("the first-run tutorial did not open");
                return;
            }
            kr.ac.hallym.hcs.app.tutorial.Tour.Overlay o = (kr.ac.hallym.hcs.app.tutorial.Tour.Overlay) glass;
            Rectangle pane = new Rectangle(0, 0, o.getWidth(), o.getHeight());
            Rectangle b = o.bubbleBounds();
            Rectangle work = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            Rectangle onScreen = new Rectangle(b);
            java.awt.Point p = o.getLocationOnScreen();
            onScreen.translate(p.x, p.y);
            System.out.println("SMOKE: tutorial bubble " + b + " pane " + pane + " on screen " + onScreen + " work " + work);
            if (!pane.contains(b)) {
                problems.add("tutorial bubble " + b + " leaves the window " + pane);
            }
            if (!work.contains(onScreen)) {
                problems.add("tutorial bubble " + onScreen + " leaves the work area " + work);
            }
            if (!o.buttonsShowing()) {
                problems.add("tutorial Next/Close buttons are not visible");
            }
        });
        if (!problems.isEmpty()) {
            System.err.println("SMOKE: " + String.join("; ", problems));
            System.exit(7);
        }
    }

    public static void main(String[] args) throws Exception {
        File circ = new File(args[0]).getAbsoluteFile();
        File image = new File(args[1]).getAbsoluteFile();
        File out = new File(args[2]).getAbsoluteFile();
        Thread t = new Thread(() -> com.cburch.logisim.Main.main(new String[] {circ.getPath()}), "app-main");
        t.setDaemon(true);
        t.start();
        for (int i = 0; i < 240 && project() == null; i++) {
            Thread.sleep(250);
        }
        Project p = project();
        if (p == null) {
            System.err.println("SMOKE: window did not open");
            System.exit(2);
        }
        Thread.sleep(4000);
        SwingUtilities.invokeAndWait(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog && w.isShowing()) {
                    w.dispose(); // 첫 실행 안내 등
                }
            }
        });
        // X-01/X-02(v1.0.2): 환경설정이 없는 첫 실행 창은 작업 영역의 90% 이상이고, 도구 모음 단추는 잘리지 않으며
        // Run·Load Program은 도구 모음에 남아 있어야 한다. 아니면 실패로 끝난다
        checkFirstRunWindow();
        // Y-04(v1.0.3): "tutorial" 모드는 첫 실행 튜토리얼이 떠 있는지, 말풍선이 창 안에 들어오고 Next·Close가 보이는지
        // 검사하고 화면을 찍은 뒤 끝난다
        if (System.getProperty("smoke.mode", "").equals("tutorial")) {
            checkTutorial();
            Robot robot0 = new Robot();
            BufferedImage img0 = robot0.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
            out.getParentFile().mkdirs();
            ImageIO.write(img0, "png", out);
            System.out.println("SMOKE: wrote " + out);
            System.exit(0);
        }
        // Load Program(.hmx, D-141): 부품 메뉴가 여는 파일 선택 창에 경로를 넣는다
        Thread loader = new Thread(() -> {
            try {
                SwingUtilities.invokeAndWait(() -> kr.ac.hallym.hcs.app.palette.PaletteActions.loadProgram(p));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        loader.start();
        JFileChooser fc = null;
        for (int i = 0; i < 80 && fc == null; i++) {
            Thread.sleep(250);
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog && w.isShowing()) {
                    java.awt.Component c = find((JDialog) w, JFileChooser.class);
                    if (c != null) {
                        fc = (JFileChooser) c;
                    }
                }
            }
        }
        if (fc == null) {
            System.err.println("SMOKE: no program chooser");
            System.exit(3);
        }
        final JFileChooser chooser = fc;
        SwingUtilities.invokeLater(() -> {
            chooser.setSelectedFile(image);
            chooser.approveSelection();
        });
        Thread.sleep(5000);
        SwingUtilities.invokeAndWait(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog && w.isShowing()) {
                    w.dispose(); // 불러오기 요약 창
                }
            }
        });
        String before = pc(p);
        for (int i = 0; i < 10; i++) {
            SwingUtilities.invokeAndWait(() -> kr.ac.hallym.hcs.app.sim.SimControls.runCycles(p, 1));
            Thread.sleep(150);
        }
        Thread.sleep(1500);
        String after = pc(p);
        System.out.println("SMOKE: PC " + before + " -> " + after);
        Robot robot = new Robot();
        Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage img = robot.createScreenCapture(screen);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("SMOKE: wrote " + out);
        if (after == null || after.equals(before)) {
            System.err.println("SMOKE: PC did not advance");
            System.exit(4);
        }
        System.exit(0);
    }
}
