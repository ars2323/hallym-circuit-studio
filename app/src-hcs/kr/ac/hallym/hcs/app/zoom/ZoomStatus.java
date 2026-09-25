/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.zoom;

import java.beans.PropertyChangeListener;
import java.util.function.DoubleConsumer;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import com.cburch.logisim.gui.generic.ZoomModel;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 상태 표시줄의 배율 단추(검토 반영 1). 옛 왼쪽 아래 배율 칸을 대신한다. 보이는 값은 지금 편집 화면의 원조
 * {@link ZoomModel}을 직접 듣는다. 그래서 휠·단축키·메뉴·화면 맞춤 어느 쪽으로 바꿔도 같은 값을 보인다. 누르면
 * 배율 단계, 화면 맞춤, 격자 켜기를 고른다.
 */
public final class ZoomStatus {
    private final JButton button = new JButton();
    private final PropertyChangeListener listener = e -> update();
    private ZoomModel model;
    private DoubleConsumer setter;
    private Runnable fit;

    public ZoomStatus() {
        button.setFocusable(false);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setForeground(Tokens.TEXT_2);
        button.setToolTipText(Messages.get("zoom.tip"));
        button.addActionListener(e -> menu().show(button, 0, -menu().getPreferredSize().height));
    }

    public JButton component() {
        return button;
    }

    /**
     * 보일 배율 모델. setter는 배율 바꾸기(레이아웃 화면은 가운데를 고정하는 {@link ZoomController#zoomTo}),
     * fit은 화면 맞춤(없으면 null).
     */
    public void setModel(ZoomModel m, DoubleConsumer setter, Runnable fit) {
        if (model != null) {
            model.removePropertyChangeListener(ZoomModel.ZOOM, listener);
            model.removePropertyChangeListener(ZoomModel.SHOW_GRID, listener);
        }
        model = m;
        this.setter = setter;
        this.fit = fit;
        if (m != null) {
            m.addPropertyChangeListener(ZoomModel.ZOOM, listener);
            m.addPropertyChangeListener(ZoomModel.SHOW_GRID, listener);
        }
        update();
    }

    /** 보이는 글자(예: "25%"). */
    public String text() {
        return button.getText();
    }

    static String format(double z) {
        return Math.round(z * 100) + "%";
    }

    void update() {
        button.setText(model == null ? "" : format(model.getZoomFactor()));
    }

    JPopupMenu menu() {
        JPopupMenu m = new JPopupMenu();
        if (model == null) {
            return m;
        }
        for (double z : ZoomMath.STEPS) {
            JCheckBoxMenuItem it = new JCheckBoxMenuItem(format(z), Math.abs(model.getZoomFactor() - z) < 1e-6);
            it.addActionListener(e -> setter.accept(z));
            m.add(it);
        }
        if (fit != null) {
            m.addSeparator();
            JMenuItem f = new JMenuItem(Messages.get("zoom.fit"));
            f.addActionListener(e -> fit.run());
            m.add(f);
        }
        m.addSeparator();
        JCheckBoxMenuItem grid = new JCheckBoxMenuItem(Messages.get("zoom.grid"), model.getShowGrid());
        grid.addActionListener(e -> model.setShowGrid(grid.isSelected()));
        m.add(grid);
        return m;
    }
}
