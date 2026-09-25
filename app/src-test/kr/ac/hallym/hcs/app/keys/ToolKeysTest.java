/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.Test;

/** 검토 반영 1: 원조 도구 모음을 숨겨도 Ctrl+2…9는 창의 루트에서 원조 도구 고르기로 동작한다. */
class ToolKeysTest {
    @Test
    void ctrlDigitsAreBoundOnTheRoot() {
        JPanel root = new JPanel();
        ToolKeys.register(root, new com.cburch.draw.toolbar.Toolbar(
                new com.cburch.draw.toolbar.AbstractToolbarModel() {
                    public java.util.List<com.cburch.draw.toolbar.ToolbarItem> getItems() {
                        return java.util.Collections.emptyList();
                    }

                    public boolean isSelected(com.cburch.draw.toolbar.ToolbarItem item) {
                        return false;
                    }

                    public void itemSelected(com.cburch.draw.toolbar.ToolbarItem item) {
                    }
                }), java.awt.event.InputEvent.CTRL_DOWN_MASK);
        int mask = java.awt.event.InputEvent.CTRL_DOWN_MASK;
        for (int i = 2; i <= 9; i++) {
            Object key = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0 + i, mask));
            assertNotNull(key);
            assertNotNull(root.getActionMap().get(key));
        }
        assertNull(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1, mask)), "Ctrl+1 is 100% zoom");
        assertEquals(1, ToolKeys.index(2));
    }
}
