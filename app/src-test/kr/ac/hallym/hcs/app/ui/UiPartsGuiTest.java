/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import kr.ac.hallym.hcs.app.theme.Theme;
import kr.ac.hallym.hcs.app.theme.Tokens;

/** Z-09 빈 상태, Z-10 묻는 대화상자, Z-08 패널 머리: 한 부품의 모양 규칙. */
@Tag("gui")
class UiPartsGuiTest {
    static JFrame frame(int w, int h) throws Exception {
        AtomicReference<JFrame> f = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Theme.install();
            JFrame j = new JFrame("ui parts");
            j.setContentPane(new JPanel(new java.awt.BorderLayout()));
            j.setSize(w, h);
            j.setVisible(true);
            f.set(j);
        });
        return f.get();
    }

    @Test
    void emptyStateShowsTheCharacterOnlyWhereThereIsRoom() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        EmptyState[] s = new EmptyState[1];
        SwingUtilities.invokeAndWait(() -> s[0] = new EmptyState("알릴 것이 없습니다",
                "Messages에는 동작할 수 없는 연결만 나옵니다. 회로를 고치면 이 칸이 비어 있습니다.", "ok"));
        int[][] sizes = {{900, 400, 1}, {600, 200, 1}, {379, 300, 0}, {900, 155, 0}, {300, 120, 0}};
        for (int[] sz : sizes) {
            SwingUtilities.invokeAndWait(() -> {
                s[0].setSize(sz[0], sz[1]);
                s[0].doLayout();
            });
            assertEquals(sz[2] == 1, s[0].characterShown(), sz[0] + "×" + sz[1]);
            // 글은 늘 칸 안에(잘리지 않는다), 가운데에
            Rectangle title = null;
            for (java.awt.Component c : s[0].getComponents()) {
                if ("empty.title".equals(c.getName())) {
                    title = c.getBounds();
                }
            }
            assertTrue(title.x >= 0 && title.x + title.width <= sz[0], "title inside " + title);
        }
        EmptyState plain = new EmptyState("t", "b", null);
        plain.setSize(900, 400);
        plain.doLayout();
        assertFalse(plain.characterShown(), "no character when there is no pose");
    }

    @Test
    void askDimsTheWholeWindowAboveTheTutorialAndIgnoresOutsideClicks() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        JFrame f = frame(1000, 700);
        try {
            AtomicReference<Boolean> answer = new AtomicReference<>();
            AtomicReference<Ask.Overlay> overlay = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> overlay.set(Ask.show(f.getRootPane(),
                    new Ask.Question("튜토리얼을 그만둘까요?", "예제는 내려가고 튜토리얼을 시작하기 전의 화면으로 돌아갑니다.",
                            "tutorial-logic.circ", "그만두기", "계속하기"), answer::set)));
            Thread.sleep(300);
            Ask.Overlay o = overlay.get();
            JLayeredPane lp = f.getRootPane().getLayeredPane();
            assertEquals(Ask.LAYER.intValue(), lp.getLayer(o));
            assertTrue(Ask.LAYER > Ask.TUTORIAL_LAYER, "above the tutorial's layer");
            assertEquals(lp.getSize(), o.getSize(), "covers the whole window");
            Rectangle card = o.card().getBounds();
            assertEquals(Tokens.ASK_WIDTH, card.width);
            assertTrue(card.x > 0 && card.y > 0 && card.x + card.width < o.getWidth(), "the card in the middle " + card);
            // 바깥 누르기: 닫히지 않는다
            java.awt.Robot robot = new java.awt.Robot();
            java.awt.Point p = o.getLocationOnScreen();
            robot.mouseMove(p.x + 20, p.y + 20);
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(300);
            assertNull(answer.get(), "an outside click does not answer");
            assertNotNull(o.getParent(), "still open");
            // 보조 단추는 왼쪽, 주 단추는 오른쪽
            assertTrue(o.card().secondary.getX() < o.card().primary.getX());
            assertEquals(Tokens.BUTTON_HEIGHT, o.card().primary.getHeight());
            // Esc: 보조
            SwingUtilities.invokeAndWait(() -> o.card().primary.requestFocusInWindow());
            Thread.sleep(200);
            robot.keyPress(KeyEvent.VK_ESCAPE);
            robot.keyRelease(KeyEvent.VK_ESCAPE);
            Thread.sleep(300);
            assertEquals(Boolean.FALSE, answer.get(), "Esc picks the secondary button");
            assertNull(o.getParent(), "closed");
        } finally {
            SwingUtilities.invokeAndWait(f::dispose);
        }
    }

    @Test
    void askBlocksTheCallerUntilAnswered() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        JFrame f = frame(900, 600);
        try {
            AtomicReference<Boolean> got = new AtomicReference<>();
            SwingUtilities.invokeLater(() -> got.set(Ask.ask(f.getContentPane(),
                    new Ask.Question("저장할까요?", "바뀐 것이 있습니다.", "lab04.circ", "Save", "Don't Save"))));
            Thread.sleep(500);
            assertNull(got.get(), "waiting");
            SwingUtilities.invokeAndWait(() -> {
                for (java.awt.Component c : f.getRootPane().getLayeredPane().getComponents()) {
                    if (c instanceof Ask.Overlay) {
                        ((Ask.Overlay) c).card().primary.doClick();
                    }
                }
            });
            Thread.sleep(300);
            assertEquals(Boolean.TRUE, got.get());
        } finally {
            SwingUtilities.invokeAndWait(f::dispose);
        }
    }

    @Test
    void panelHeadHasTheCommonHeight() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        PanelHead[] h = new PanelHead[1];
        SwingUtilities.invokeAndWait(() -> {
            Theme.install();
            h[0] = new PanelHead("Attributes");
        });
        assertEquals(Tokens.HEAD, h[0].getPreferredSize().height);
        assertEquals(Tokens.NAVY, h[0].title().getForeground());
        assertFalse(h[0].title().getFont().getFamily().isEmpty());
    }
}
