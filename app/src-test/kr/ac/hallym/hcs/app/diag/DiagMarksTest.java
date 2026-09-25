/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.app.theme.Tokens;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** 2c 검토 반영: 메시지가 있는 부품의 표시는 누르지 않아도 보이고, 배율 25~400%에서 크기가 같다. */
class DiagMarksTest {
    @TempDir
    Path tmp;

    /** 부품 오른쪽 위 점 둘레(화면 좌표)의 빨간 화소 수. */
    static int redAround(BufferedImage img, int cx, int cy, int r) {
        int n = 0;
        int red = Tokens.ERROR.getRGB() & 0xFFFFFF;
        for (int y = Math.max(0, cy - r); y < Math.min(img.getHeight(), cy + r); y++) {
            for (int x = Math.max(0, cx - r); x < Math.min(img.getWidth(), cx + r); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == red) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    void marksKeepTheirScreenSizeFrom25To400Percent() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component and = b.add("Gates", "AND Gate", 200, 200, "inputs", "2");
        b.commit();
        Diagnostic d = new Diagnostic(Diagnostic.Kind.INPUT_UNCONNECTED, f.getMainCircuit(),
                Collections.singletonList(and), Collections.emptyList(), and.getLocation(), "main › AND #1", "in1");
        Bounds bb = and.getBounds();
        int[] dots = new int[3];
        double[] zooms = {0.25, 1.0, 4.0};
        for (int i = 0; i < zooms.length; i++) {
            double z = zooms[i];
            BufferedImage img = new BufferedImage((int) (400 * z) + 40, (int) (400 * z) + 40,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.scale(z, z);
            DiagMarks.paint(g, Collections.singletonList(d), null, z); // 누르지 않은 상태
            g.dispose();
            float gap = DiagMarks.px(DiagMarks.GAP_PX, z) + 1;
            int cx = (int) Math.round((bb.getX() + bb.getWidth() + gap) * z);
            int cy = (int) Math.round((bb.getY() - gap) * z);
            dots[i] = redAround(img, cx, cy, 6);
            assertTrue(dots[i] >= 15, "dot visible at " + z + ": " + dots[i]);
            // 테두리: 부품 왼쪽 가운데 바깥에 빨간 화소
            int lx = (int) Math.round((bb.getX() - gap) * z);
            int ly = (int) Math.round((bb.getY() + bb.getHeight() / 2.0) * z);
            assertTrue(redAround(img, lx, ly, 3) > 0, "border visible at " + z);
        }
        assertTrue(Math.abs(dots[0] - dots[2]) <= 12, "same screen size: " + java.util.Arrays.toString(dots));
    }

    @Test
    void screenPixelsBecomeCircuitUnits() {
        assertTrue(Math.abs(DiagMarks.px(2f, 0.25) - 8f) < 1e-6);
        assertTrue(Math.abs(DiagMarks.px(2f, 4.0) - 0.5f) < 1e-6);
    }

    /** 테두리 두께(화면 px): 부품 왼쪽 가운데를 가로로 지나며 빨간 화소를 센다. */
    static int borderWidth(BufferedImage img, int x, int y) {
        int n = 0;
        for (int dx = -8; dx <= 8; dx++) {
            int xx = x + dx;
            if (xx < 0 || xx >= img.getWidth()) {
                continue;
            }
            java.awt.Color c = new java.awt.Color(img.getRGB(xx, y));
            if (c.getRed() > 150 && c.getGreen() < 130 && c.getBlue() < 130) {
                n++;
            }
        }
        return n;
    }

    /** 2c 검토 반영 2: 누른 항목의 테두리는 배율과 무관하게 화면 3px 이상이고 누르지 않은 표시보다 확실히 굵다. */
    @Test
    void focusedBorderIsAtLeastThreeScreenPixelsAndThicker() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component and = b.add("Gates", "AND Gate", 200, 200, "inputs", "2");
        b.commit();
        Diagnostic d = new Diagnostic(Diagnostic.Kind.INPUT_UNCONNECTED, f.getMainCircuit(),
                Collections.singletonList(and), Collections.emptyList(), and.getLocation(), "main › AND #1", "in1");
        Bounds bb = and.getBounds();
        for (double z : new double[] {0.25, 0.5, 1.0, 2.0, 4.0}) {
            int[] w = new int[2];
            for (int k = 0; k < 2; k++) {
                BufferedImage img = new BufferedImage((int) (400 * z) + 40, (int) (400 * z) + 40,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, img.getWidth(), img.getHeight());
                g.scale(z, z);
                DiagMarks.paint(g, Collections.singletonList(d), k == 1 ? d : null, z);
                g.dispose();
                float gap = DiagMarks.px(DiagMarks.GAP_PX, z) + 1;
                int lx = (int) Math.round((bb.getX() - gap) * z);
                int ly = (int) Math.round((bb.getY() + bb.getHeight() / 2.0) * z);
                w[k] = borderWidth(img, lx, ly);
            }
            assertTrue(w[1] >= 3, "focused border at " + z + ": " + w[1] + "px");
            assertTrue(w[1] >= w[0] + 1, "focused thicker than unfocused at " + z + ": " + w[1] + " vs " + w[0]);
            assertTrue(w[0] >= 1, "unfocused border visible at " + z);
        }
    }
}
