/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import com.cburch.logisim.proj.Projects;

/**
 * 장면 48(Y-04): 환경설정·최근 파일이 없는 새 사용자의 첫 실행. 별도 JVM에서 인자 없이 앱을 띄워 튜토리얼 첫 장이 뜬 화면을
 * 찍는다. 쓰기: java -Dhcs.configDir=<빈 폴더> -cp <jar>:<classes> FirstRun <out.png>
 */
public final class FirstRun {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]).getAbsoluteFile();
        Thread t = new Thread(() -> com.cburch.logisim.Main.main(new String[0]), "app-main");
        t.setDaemon(true);
        t.start();
        for (int i = 0; i < 120 && Projects.getOpenProjects().isEmpty(); i++) {
            Thread.sleep(250);
        }
        boolean tour = false;
        for (int i = 0; i < 40 && !tour; i++) {
            Thread.sleep(250);
            boolean[] up = new boolean[1];
            SwingUtilities.invokeAndWait(() -> {
                java.awt.Frame f = Projects.getTopFrame();
                up[0] = f instanceof javax.swing.JFrame && ((javax.swing.JFrame) f).getRootPane()
                        .getGlassPane() instanceof kr.ac.hallym.hcs.app.tutorial.Tour.Overlay;
            });
            tour = up[0];
        }
        Thread.sleep(1200);
        java.awt.Frame f = Projects.getTopFrame();
        System.out.println("48: first run window " + (f == null ? "none" : f.getBounds()) + " tutorial " + tour);
        Robot robot = new Robot();
        out.getParentFile().mkdirs();
        ImageIO.write(robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())), "png", out);
        Runtime.getRuntime().halt(tour ? 0 : 3);
    }
}
