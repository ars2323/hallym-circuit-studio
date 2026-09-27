/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

import javax.swing.Icon;

import org.junit.jupiter.api.Test;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/** Z-12b: Lucide 아이콘을 번들된 SVG에서 그린다(새로 그리지 않는다). */
class IconsTest {
    @Test
    void everyToolbarIconLoadsAndPaintsInTheRequestedColor() {
        for (String name : Icons.TOOLBAR.keySet()) {
            Icon icon = Icons.toolbar(name, Tokens.BUTTON_ICON);
            assertEquals(Tokens.BUTTON_ICON, icon.getIconWidth(), name);
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            icon.paintIcon(null, g, 0, 0);
            g.dispose();
            int navy = 0;
            int other = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int argb = img.getRGB(x, y);
                    if ((argb >>> 24) > 200) {
                        Color c = new Color(argb, true);
                        int d = Math.abs(c.getRed() - Tokens.NAVY.getRed()) + Math.abs(c.getGreen() - Tokens.NAVY.getGreen())
                                + Math.abs(c.getBlue() - Tokens.NAVY.getBlue());
                        if (d <= 24) { // 가장자리 안티앨리어싱
                            navy++;
                        } else {
                            other++;
                        }
                    }
                }
            }
            assertTrue(navy > 4, name + " paints strokes in navy (" + navy + ")");
            assertEquals(0, other, name + " has no other opaque colour");
        }
        assertThrows(IllegalArgumentException.class, () -> Icons.lucide("no-such-icon", 16));
    }

    /** 번들한 SVG는 assets/icons/lucide와 같은 파일이고, 라이선스(ISC)가 함께 간다. */
    @Test
    void bundledSvgsAreTheAssetFiles() throws Exception {
        File dir = new File(new File(System.getProperty("hcs.testsDir")).getParentFile(), "assets/icons/lucide");
        File[] svgs = dir.listFiles((d, n) -> n.endsWith(".svg"));
        assertNotNull(svgs);
        assertTrue(svgs.length >= 30, "icons: " + svgs.length);
        for (File f : svgs) {
            try (java.io.InputStream in = Icons.class.getClassLoader().getResourceAsStream(Icons.DIR + f.getName())) {
                assertNotNull(in, f.getName());
                assertTrue(java.util.Arrays.equals(Files.readAllBytes(f.toPath()), in.readAllBytes()), f.getName());
            }
            assertTrue(new FlatSVGIcon(Icons.DIR + f.getName(), 16, 16, Icons.class.getClassLoader()).hasFound());
        }
        assertNotNull(Icons.class.getClassLoader().getResourceAsStream(Icons.DIR + "LICENSE.txt"));
        Icon red = Icons.lucide("play", 20, Color.RED);
        assertEquals(20, red.getIconHeight());
    }
}
