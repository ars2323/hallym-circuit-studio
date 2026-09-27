/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import kr.ac.hallym.hcs.app.theme.Theme;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 패널 머리 한 부품(Z-08, Hallym MIPS {@code ui.ts}의 {@code .phead}): 높이 {@link Tokens#HEAD}, 왼쪽 여백 12·오른쪽 8,
 * 제목(600, navy), 가운데 보조 글(12px, muted), 오른쪽 단추 묶음, 아래 테두리 1px. 탭 머리는 JTabbedPane이 같은 값으로 그린다
 * (FlatLightLaf.properties의 TabbedPane.*). 패널마다 따로 만들지 않는다.
 */
public class PanelHead extends JPanel {
    private final JLabel title;
    private final JLabel meta = new JLabel();
    private final JPanel aside = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

    public PanelHead(String title) {
        super(new BorderLayout(10, 0));
        setName("panel.head");
        this.title = new JLabel(title);
        this.title.setFont(Theme.uiFont(600, Tokens.FONT_UI));
        this.title.setForeground(Tokens.NAVY);
        meta.setFont(Theme.uiFont(400, Tokens.FONT_SMALL));
        meta.setForeground(Tokens.TEXT_MUTED);
        aside.setOpaque(false);
        setBackground(Tokens.WHITE);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Tokens.BORDER),
                BorderFactory.createEmptyBorder(0, Tokens.HEAD_PAD_LEFT, 0, Tokens.HEAD_PAD_RIGHT)));
        add(this.title, BorderLayout.WEST);
        add(meta, BorderLayout.CENTER);
        add(aside, BorderLayout.EAST);
    }

    public JLabel title() {
        return title;
    }

    public void setMeta(String text) {
        meta.setText(text == null ? "" : text);
    }

    /** 오른쪽 단추(높이 {@link Tokens#HEAD_BUTTON}). */
    public void addAside(JComponent c) {
        aside.add(c);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, Tokens.HEAD);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, Tokens.HEAD);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }
}
