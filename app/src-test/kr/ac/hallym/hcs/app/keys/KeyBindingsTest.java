/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** E-09: 기본 키, Shift로 반대 방향, 바꾸기·겹침·기본으로, 창에 단 키를 곧바로 다시 담, ? 표가 지금 키를 보임. */
class KeyBindingsTest {
    @AfterEach
    void defaults() {
        KeyBindings.resetAll();
    }

    static KeyEvent press(int code, int mods) {
        return new KeyEvent(new JPanel(), KeyEvent.KEY_PRESSED, 0L, mods, code, KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    void defaultsMatchTheOldKeys() {
        assertTrue(KeyBindings.matches("rotate", press(KeyEvent.VK_R, 0)));
        assertTrue(KeyBindings.matches("rotate", press(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK)), "Shift reverses");
        assertFalse(KeyBindings.matches("rotate", press(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK)));
        assertTrue(KeyBindings.matches("flowToggle",
                press(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
        assertFalse(KeyBindings.matches("flowToggle", press(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK)));
        assertTrue(KeyBindings.matches("find", press(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK)));
        assertTrue(KeyBindings.matches("label", press(KeyEvent.VK_F2, 0)));
        assertEquals("R / Shift+R", KeyBindings.display("rotate"));
        assertEquals("Ctrl+=", KeyBindings.display("zoomIn"));
        assertEquals("[", KeyBindings.display("influenceLess"));
        assertEquals("Ctrl+Shift+F", KeyBindings.display("flowToggle"));
    }

    @Test
    void changeCheckConflictsAndReset() {
        KeyStroke t = KeyStroke.getKeyStroke(KeyEvent.VK_T, 0);
        assertNull(KeyBindings.set("rotate", t));
        assertTrue(KeyBindings.customized("rotate"));
        assertTrue(KeyBindings.matches("rotate", press(KeyEvent.VK_T, 0)));
        assertTrue(KeyBindings.matches("rotate", press(KeyEvent.VK_T, InputEvent.SHIFT_DOWN_MASK)));
        assertFalse(KeyBindings.matches("rotate", press(KeyEvent.VK_R, 0)));
        assertTrue(Shortcuts.table().containsKey("T / Shift+T"), Shortcuts.table().keySet().toString());
        // 다른 명령의 키, 고정 키는 받지 않는다
        assertEquals("influence", KeyBindings.set("label", KeyStroke.getKeyStroke(KeyEvent.VK_I, 0)));
        assertEquals("influence", KeyBindings.set("label",
                KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.SHIFT_DOWN_MASK)), "Shift+I reverses influence");
        assertEquals("fixed", KeyBindings.set("label", KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyBindings.MENU)));
        assertTrue(KeyBindingsDialog.assign("label", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)) != null);
        assertTrue(KeyBindings.matches("label", press(KeyEvent.VK_F2, 0)), "unchanged after a refused key");
        KeyBindings.reset("rotate");
        assertTrue(KeyBindings.matches("rotate", press(KeyEvent.VK_R, 0)));
        assertFalse(KeyBindings.customized("rotate"));
    }

    @Test
    void installedMapsFollowAChange() {
        InputMap im = new InputMap();
        KeyBindings.install(im, "palette", "hcsPalette");
        assertEquals("hcsPalette", im.get(KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyBindings.MENU)));
        KeyStroke k = KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyBindings.MENU | InputEvent.SHIFT_DOWN_MASK);
        assertNull(KeyBindings.set("palette", k));
        assertEquals("hcsPalette", im.get(k));
        assertNull(im.get(KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyBindings.MENU)), "the old key is gone");
        // 다시 실행: 대표 키는 메뉴 항목이 맡고 창에는 나머지만
        InputMap redo = new InputMap();
        KeyBindings.installAlternates(redo, "redo", "hcsRedo");
        assertNull(redo.get(KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyBindings.MENU)));
        assertEquals("hcsRedo", redo.get(KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                KeyBindings.MENU | InputEvent.SHIFT_DOWN_MASK)));
    }
}
