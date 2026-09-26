/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.about;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Container;
import java.awt.Image;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JTabbedPane;

import org.junit.jupiter.api.Test;

import com.cburch.logisim.circuit.Circuit;

/** E-11·E-12: 창 제목, 앱 아이콘 여섯 크기, 번들된 LICENSE·NOTICE, About 내용(이름·버전·엠블럼·캐릭터·탭 둘). */
class AppIdentityTest {
    @Test
    void windowTitleNamesTheFileAndTheApp() {
        Circuit main = new Circuit("main");
        Circuit alu = new Circuit("alu");
        assertEquals("datapath — Hallym Circuit Studio", AppIdentity.title("datapath", main, main));
        assertEquals("datapath › alu — Hallym Circuit Studio", AppIdentity.title("datapath", alu, main));
    }

    @Test
    void iconsInSixSizes() {
        List<Image> icons = AppIdentity.icons();
        assertEquals(6, icons.size());
        int[] expected = {16, 24, 32, 48, 64, 256};
        for (int i = 0; i < icons.size(); i++) {
            assertEquals(expected[i], icons.get(i).getWidth(null), "icon " + i);
        }
    }

    @Test
    void licenseAndNoticesAreBundled() {
        assertTrue(AppIdentity.text("LICENSE").contains("GNU GENERAL PUBLIC LICENSE"));
        String notice = AppIdentity.text("NOTICE");
        assertTrue(notice.contains("Logisim 2.7.1") && notice.contains("Pretendard") && notice.contains("SPIM")
                && notice.contains("FlatLaf"), notice.substring(0, Math.min(200, notice.length())));
    }

    @Test
    void aboutShowsNameVersionMarksAndTwoTabs() {
        Container c = (Container) AboutDialog.content(() -> { });
        List<java.awt.Component> all = new ArrayList<>();
        collect(c, all);
        JLabel name = null;
        int images = 0;
        JTabbedPane tabs = null;
        for (java.awt.Component x : all) {
            if (x instanceof JLabel && "about.name".equals(x.getName())) {
                name = (JLabel) x;
            }
            if (x instanceof JLabel && ((JLabel) x).getIcon() != null) {
                images++;
            }
            if (x instanceof JTabbedPane) {
                tabs = (JTabbedPane) x;
            }
        }
        assertNotNull(name);
        assertEquals("Hallym Circuit Studio", name.getText());
        assertEquals(2, images, "the emblem and one character");
        assertEquals(2, tabs.getTabCount());
        assertNotNull(AboutDialog.emblem());
        assertEquals(AboutDialog.CHARACTER, AboutDialog.character().getIconWidth());
    }

    static void collect(Container c, List<java.awt.Component> out) {
        for (java.awt.Component x : c.getComponents()) {
            out.add(x);
            if (x instanceof Container) {
                collect((Container) x, out);
            }
        }
    }
}
