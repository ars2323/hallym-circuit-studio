/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /** 라벨 칩 자리에는 테두리·점을 그리지 않는다(칩 글자를 가리지 않게, P-03 검토). */
    @Test
    void marksStayOffLabelChips() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component and = b.add("Gates", "AND Gate", 200, 200, "inputs", "2");
        b.commit();
        Diagnostic d = new Diagnostic(Diagnostic.Kind.INPUT_UNCONNECTED, f.getMainCircuit(),
                Collections.singletonList(and), Collections.emptyList(), and.getLocation(), "main › AND #1", "in1");
        Bounds bb = and.getBounds();
        // 부품 오른쪽 위(점 자리)를 덮는 칩
        java.awt.Rectangle chip = new java.awt.Rectangle(bb.getX() + bb.getWidth() - 20, bb.getY() - 16, 40, 16);
        BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 400, 400);
        DiagMarks.paint(g, Collections.singletonList(d), null, 1.0, Collections.singletonList(chip));
        g.dispose();
        for (int y = chip.y; y < chip.y + chip.height; y++) {
            for (int x = chip.x; x < chip.x + chip.width; x++) {
                assertTrue((img.getRGB(x, y) & 0xFFFFFF) == 0xFFFFFF, "nothing drawn on the chip at " + x + "," + y);
            }
        }
        int lx = bb.getX() - (int) DiagMarks.px(DiagMarks.GAP_PX, 1) - 1;
        assertTrue(redAround(img, lx, bb.getY() + bb.getHeight() / 2, 3) > 0, "the border is still drawn elsewhere");
    }

    /**
     * S-13: 테두리 굵기를 화면에서 잰다. 누르지 않은 항목은 꽉 찬 빨간 픽셀 2px, 누른 항목은 4px(25·100·400%).
     * 부품 왼쪽 가운데 줄에서 테두리를 가로질러 연속한 빨간 픽셀 수를 센다.
     */
    @Test
    void borderWidthsAreExactOnScreen() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component and = b.add("Gates", "AND Gate", 200, 200, "inputs", "2");
        b.commit();
        Diagnostic d = new Diagnostic(Diagnostic.Kind.INPUT_UNCONNECTED, f.getMainCircuit(),
                Collections.singletonList(and), Collections.emptyList(), and.getLocation(), "main › AND #1", "in1");
        Bounds bb = and.getBounds();
        int red = DiagMarks.MARK.getRGB() & 0xFFFFFF;
        for (double z : new double[] {0.25, 1.0, 4.0}) {
            for (boolean strong : new boolean[] {false, true}) {
                BufferedImage img = new BufferedImage((int) (400 * z) + 60, (int) (400 * z) + 60,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, img.getWidth(), img.getHeight());
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.translate(7, 5); // 캔버스 안 자리처럼 정수가 아닌 곳에서도
                g.scale(z, z);
                DiagMarks.paint(g, Collections.singletonList(d), strong ? d : null, z);
                g.dispose();
                int y = (int) Math.round((bb.getY() + bb.getHeight() / 2.0) * z) + 5;
                int run = 0;
                int best = 0;
                for (int x = 0; x < (int) Math.round(bb.getX() * z) + 7; x++) {
                    if ((img.getRGB(x, y) & 0xFFFFFF) == red) {
                        run++;
                        best = Math.max(best, run);
                    } else {
                        run = 0;
                    }
                }
                int want = strong ? (int) DiagMarks.FOCUS_BORDER_PX : (int) DiagMarks.BORDER_PX;
                assertEquals(want, best, (strong ? "focused" : "normal") + " border at " + z);
            }
        }
    }

    /** S-13: 선 덧칠 굵기도 화면에서 3px(누르지 않음)·6px(누름). 가로선을 세로로 가로질러 센다. */
    @Test
    void wireHighlightWidthsAreExactOnScreen() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        b.wire(com.cburch.logisim.data.Location.create(100, 200), com.cburch.logisim.data.Location.create(300, 200));
        b.commit();
        com.cburch.logisim.circuit.Wire w = f.getMainCircuit().getWires().iterator().next();
        Diagnostic d = new Diagnostic(Diagnostic.Kind.INPUT_UNDRIVEN, f.getMainCircuit(), Collections.emptyList(),
                Collections.singletonList(w), w.getEnd0(), "main", "x");
        for (double z : new double[] {0.25, 1.0, 4.0}) {
            for (boolean strong : new boolean[] {false, true}) {
                BufferedImage img = new BufferedImage((int) (400 * z) + 60, (int) (400 * z) + 60,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, img.getWidth(), img.getHeight());
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.translate(3, 9);
                g.scale(z, z);
                DiagMarks.paint(g, Collections.singletonList(d), strong ? d : null, z);
                g.dispose();
                int x = (int) Math.round(200 * z) + 3;
                int want = (int) (strong ? DiagMarks.FOCUS_WIRE_PX : DiagMarks.WIRE_PX);
                int color = (strong ? DiagMarks.FOCUS_WIRE : DiagMarks.WIRE).getRGB();
                int run = 0;
                int best = 0;
                int first = -1;
                for (int y = 0; y < img.getHeight(); y++) {
                    int c = img.getRGB(x, y);
                    boolean full = near(c, blendOnWhite(color), 3);
                    if (full) {
                        run++;
                        best = Math.max(best, run);
                    } else {
                        run = 0;
                    }
                }
                assertEquals(want, best, (strong ? "focused" : "normal") + " wire at " + z);
            }
        }
    }

    static boolean near(int c, int want, int tol) {
        for (int sh = 0; sh <= 16; sh += 8) {
            if (Math.abs(((c >> sh) & 255) - ((want >> sh) & 255)) > tol) {
                return false;
            }
        }
        return true;
    }

    /** 반투명 색을 흰 바탕에 칠한 결과(ARGB → RGB). */
    static int blendOnWhite(int argb) {
        int a = argb >>> 24;
        int r = ((argb >> 16) & 255) * a / 255 + 255 * (255 - a) / 255;
        int gg = ((argb >> 8) & 255) * a / 255 + 255 * (255 - a) / 255;
        int bl = (argb & 255) * a / 255 + 255 * (255 - a) / 255;
        return (r << 16) | (gg << 8) | bl;
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
