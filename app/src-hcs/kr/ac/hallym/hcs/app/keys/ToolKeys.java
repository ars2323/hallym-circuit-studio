/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.keys;

import java.awt.Toolkit;

import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import com.cburch.draw.toolbar.Toolbar;
import com.cburch.logisim.gui.main.KeyboardToolSelection;

/**
 * 원조 도구 모음을 화면에서 숨긴 뒤에도(검토 반영 1) 원조 Ctrl+2…Ctrl+9 도구 고르기를 그대로 둔다. 원조
 * {@link KeyboardToolSelection}은 도구 모음 부품에 단축키를 달아, 도구 모음이 화면에 없으면 동작하지 않는다.
 * 같은 동작을 창의 루트에 단다. 도구 순서는 .circ의 {@code <toolbar>} 그대로다. Ctrl+0·Ctrl+1은 배율(D-028)이다.
 */
public final class ToolKeys {
    private ToolKeys() {
    }

    public static void register(JComponent root, Toolbar toolbar) {
        register(root, toolbar, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
    }

    static void register(JComponent root, Toolbar toolbar, int mask) {
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        for (int i = 2; i <= 9; i++) {
            String key = "hcsToolSelect" + i;
            im.put(KeyStroke.getKeyStroke((char) ('0' + i), mask), key);
            im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0 + i, mask), key);
            root.getActionMap().put(key, new KeyboardToolSelection(toolbar, i - 1));
        }
    }

    /** 단축키 i(2~9)가 고르는 도구의 번호(도구 모음에서 고를 수 있는 것 중 0부터). */
    static int index(int digit) {
        return digit - 1;
    }
}
