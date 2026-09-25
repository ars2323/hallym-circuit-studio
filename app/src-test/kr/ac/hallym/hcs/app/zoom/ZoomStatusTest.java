/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.zoom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import org.junit.jupiter.api.Test;

import com.cburch.logisim.gui.generic.ZoomModel;

/** 검토 반영 1: 상태 표시줄 배율은 원조 배율 모델을 직접 들어, 누가 배율을 바꾸든 같은 값을 보인다. */
class ZoomStatusTest {
    /** 원조 BasicZoomModel처럼 값이 바뀌면 알리는 모델. */
    static final class Model implements ZoomModel {
        final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
        double zoom = 1.0;
        boolean grid = true;

        public void addPropertyChangeListener(String prop, PropertyChangeListener l) {
            pcs.addPropertyChangeListener(prop, l);
        }

        public void removePropertyChangeListener(String prop, PropertyChangeListener l) {
            pcs.removePropertyChangeListener(prop, l);
        }

        public boolean getShowGrid() {
            return grid;
        }

        public double getZoomFactor() {
            return zoom;
        }

        public double[] getZoomOptions() {
            return new double[] {100};
        }

        public void setShowGrid(boolean v) {
            boolean old = grid;
            grid = v;
            pcs.firePropertyChange(SHOW_GRID, old, v);
        }

        public void setZoomFactor(double v) {
            double old = zoom;
            zoom = v;
            pcs.firePropertyChange(ZOOM, old, v);
        }
    }

    @Test
    void followsTheModelWhoeverChangesIt() {
        ZoomStatus s = new ZoomStatus();
        Model layout = new Model();
        s.setModel(layout, layout::setZoomFactor, null);
        assertEquals("100%", s.text());
        layout.setZoomFactor(0.25); // 02b: 스크립트·휠·단축키가 모델을 바로 바꿔도
        assertEquals("25%", s.text());

        Model appearance = new Model();
        appearance.setZoomFactor(2.0);
        s.setModel(appearance, appearance::setZoomFactor, null); // 모양 편집 화면으로
        assertEquals("200%", s.text());
        layout.setZoomFactor(1.5);
        assertEquals("200%", s.text(), "the old model is no longer followed");
        assertEquals(0, layout.pcs.getPropertyChangeListeners().length);
    }

    @Test
    void menuOffersStepsFitAndGrid() {
        ZoomStatus s = new ZoomStatus();
        Model m = new Model();
        boolean[] fitted = {false};
        s.setModel(m, m::setZoomFactor, () -> fitted[0] = true);
        javax.swing.JPopupMenu menu = s.menu();
        int items = 0;
        for (java.awt.Component c : menu.getComponents()) {
            if (c instanceof javax.swing.JMenuItem) {
                items++;
            }
        }
        assertEquals(ZoomMath.STEPS.length + 2, items);
        ((javax.swing.JMenuItem) menu.getComponent(0)).doClick();
        assertEquals(ZoomMath.STEPS[0], m.getZoomFactor());
        assertEquals("25%", s.text());
        for (java.awt.Component c : menu.getComponents()) {
            if (c instanceof javax.swing.JMenuItem && ((javax.swing.JMenuItem) c).getText().contains("Ctrl+0")) {
                ((javax.swing.JMenuItem) c).doClick();
            }
        }
        assertTrue(fitted[0]);
    }
}
