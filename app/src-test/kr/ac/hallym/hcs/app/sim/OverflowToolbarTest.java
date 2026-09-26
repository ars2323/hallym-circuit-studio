/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JToggleButton;

import org.junit.jupiter.api.Test;

/** X-02 (D-106): 넘치면 글자부터 숨기고, 그래도 넘치면 우선순위 낮은 것부터 » 메뉴로. 넓히면 되돌아온다. */
class OverflowToolbarTest {
    static OverflowToolbar bar(boolean text) {
        OverflowToolbar tb = new OverflowToolbar(text);
        for (int i = 0; i < 12; i++) {
            tb.addItem(new JButton("Command " + i, new BarIcons("run")), "k" + i, 0);
            if (i % 4 == 3) {
                tb.addGap();
            }
        }
        tb.addItem(new JButton("Run", new BarIcons("run")), "run", OverflowToolbar.KEEP);
        tb.addItem(new JToggleButton("Flow"), "flow", 0);
        tb.addItem(new JButton("Style"), "style", OverflowToolbar.FIRST);
        return tb;
    }

    static void layout(OverflowToolbar tb, int width) {
        tb.setSize(width, 40);
        tb.doLayout();
    }

    static void allInside(OverflowToolbar tb) {
        Rectangle box = new Rectangle(0, 0, tb.getWidth(), tb.getHeight());
        for (Component c : tb.shownComponents()) {
            assertTrue(c.isVisible() && box.contains(c.getBounds()), c + " inside " + box);
        }
        if (tb.moreButton().isVisible()) {
            assertTrue(box.contains(tb.moreButton().getBounds()));
        }
    }

    @Test
    void wideBarShowsEverythingWithText() {
        OverflowToolbar tb = bar(true);
        layout(tb, 3000);
        assertEquals(15, tb.shownKeys().size());
        assertTrue(tb.overflowKeys().isEmpty());
        assertFalse(tb.iconsOnlyNow());
        assertFalse(tb.moreButton().isVisible());
        allInside(tb);
    }

    @Test
    void narrowerBarDropsTextFirstThenLowPriorityItemsFromTheRight() {
        OverflowToolbar tb = bar(true);
        layout(tb, 3000);
        int withText = 0;
        for (Component c : tb.shownComponents()) {
            withText += c.getWidth();
        }
        layout(tb, withText - 40); // 글자 모드로는 안 들어간다
        assertTrue(tb.iconsOnlyNow(), "text hidden first");
        assertTrue(tb.overflowKeys().isEmpty(), "icons alone fit: nothing in the menu");
        allInside(tb);
        layout(tb, 200);
        assertFalse(tb.overflowKeys().isEmpty());
        assertTrue(tb.moreButton().isVisible());
        List<String> hidden = tb.overflowKeys();
        assertTrue(hidden.contains("style"), "the lowest priority goes first");
        assertTrue(tb.shownKeys().contains("run"), "KEEP items stay while others are hidden: " + tb.shownKeys());
        // 메뉴 순서는 도구 모음 순서
        List<String> order = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            order.add("k" + i);
        }
        order.add("run");
        order.add("flow");
        order.add("style");
        int last = -1;
        for (String h : hidden) {
            int at = order.indexOf(h);
            assertTrue(at > last, "toolbar order in the menu: " + hidden);
            last = at;
        }
        assertEquals(hidden.size(), tb.menu().getComponentCount());
        allInside(tb);
        // 다시 넓히면 모두 돌아온다
        layout(tb, 3000);
        assertTrue(tb.overflowKeys().isEmpty());
        assertFalse(tb.iconsOnlyNow());
    }

    @Test
    void iconsOnlySettingNeverShowsText() {
        OverflowToolbar tb = bar(false);
        layout(tb, 3000);
        assertTrue(tb.iconsOnlyNow());
        for (Component c : tb.shownComponents()) {
            if (c instanceof JButton && ((JButton) c).getIcon() != null) {
                assertEquals(null, ((JButton) c).getText());
            }
        }
    }
}
