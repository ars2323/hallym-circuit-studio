/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.props;

import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLayeredPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Frame;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 제자리 편집 칸(#74). 라벨은 부품 위에서(더블클릭·F2), 빠른 속성 창의 글자 속성은 단추 아래에서 고친다.
 * Enter나 다른 곳 클릭은 적용, Esc는 취소다. 적용은 원조 속성 표와 같은 Action({@link QuickAttrs#parse})이다.
 */
public final class InlineEditor {
    private static JTextField open;

    private InlineEditor() {
    }

    /** 부품 라벨을 부품 자리에서 고친다. 라벨 속성이 없으면 false. */
    public static boolean editLabel(Frame frame, Canvas canvas, Component c) {
        Attribute<Object> a = QuickAttrs.labelAttr(c);
        if (a == null) {
            return false;
        }
        double z = zoom(canvas);
        Bounds b = c.getBounds();
        int w = Math.max(96, (int) (b.getWidth() * z) + 16);
        Rectangle sb = canvas.hcsToScreen(new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
        int cx = sb.x + sb.width / 2;
        int cy = sb.y + sb.height / 2;
        JLayeredPane layer = frame.getLayeredPane();
        java.awt.Point p = SwingUtilities.convertPoint(canvas, cx - w / 2, cy - 12, layer);
        start(frame, canvas, Collections.singletonList(c), a, new Rectangle(p.x, p.y, w, 24));
        return true;
    }

    /** at(창의 층 좌표) 자리에 칸을 띄워 comps의 속성 a를 고친다. */
    static void start(Frame frame, Canvas canvas, List<Component> comps, Attribute<Object> a, Rectangle at) {
        close();
        Circuit circuit = canvas.getCircuit();
        Object v = comps.get(0).getAttributeSet().getValue(a);
        JTextField f = new JTextField(v == null ? "" : a.toStandardString(v));
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Tokens.BLUE, 1),
                BorderFactory.createEmptyBorder(1, 4, 1, 4)));
        f.setToolTipText(Messages.get("quick.editTip", a.getDisplayName()));
        f.getAccessibleContext().setAccessibleName(a.getDisplayName());
        JLayeredPane layer = frame.getLayeredPane();
        Rectangle r = new Rectangle(at);
        r.x = Math.max(0, Math.min(r.x, layer.getWidth() - r.width));
        f.setBounds(r);
        boolean[] done = {false};
        Runnable commit = () -> {
            if (done[0]) {
                return;
            }
            try {
                com.cburch.logisim.tools.SetAttributeAction act = QuickAttrs.parse(circuit, comps, a, f.getText());
                if (!QuickAttrs.unchanged(comps, a, f.getText())) {
                    frame.getProject().doAction(act);
                }
            } catch (IllegalArgumentException ex) {
                f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Tokens.ERROR, 1),
                        BorderFactory.createEmptyBorder(1, 4, 1, 4)));
                f.setToolTipText(Messages.get("quick.badValue", f.getText()));
                f.requestFocusInWindow();
                return;
            }
            done[0] = true;
            finish(layer, f, canvas);
        };
        f.addActionListener(e -> commit.run());
        f.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    done[0] = true;
                    finish(layer, f, canvas);
                }
            }
        });
        f.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!e.isTemporary()) {
                    SwingUtilities.invokeLater(commit);
                }
            }
        });
        layer.add(f, JLayeredPane.POPUP_LAYER);
        open = f;
        f.selectAll();
        f.requestFocusInWindow();
        layer.repaint();
    }

    private static void finish(JLayeredPane layer, JTextField f, Canvas canvas) {
        layer.remove(f);
        layer.repaint();
        if (open == f) {
            open = null;
        }
        canvas.requestFocusInWindow();
    }

    /** 열린 칸이 있으면 닫는다(적용하지 않음). */
    static void close() {
        if (open != null && open.getParent() != null) {
            java.awt.Container p = open.getParent();
            p.remove(open);
            p.repaint();
        }
        open = null;
    }

    static double zoom(Canvas canvas) {
        return canvas.getHcsZoom() == null ? 1.0 : canvas.getHcsZoom().zoomFactor();
    }
}
