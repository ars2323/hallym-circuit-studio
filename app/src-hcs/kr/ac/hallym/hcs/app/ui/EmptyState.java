/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;

import javax.swing.CellRendererPane;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import kr.ac.hallym.hcs.app.theme.Theme;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 빈 상태 한 부품(Z-09, Hallym MIPS {@code notice.ts}·{@code .notice}와 같은 모양): 패널 가운데에 제목, 본문,
 * (단추), 그리고 먼 쪽 끝에 캐릭터. 빈 패널을 그냥 두지 않고 무엇을 하면 채워지는지 말한다. 칸이 {@link Tokens#NOTICE_MIN_WIDTH}
 * 보다 좁거나 {@link Tokens#NOTICE_MIN_HEIGHT}보다 낮으면 캐릭터를 빼고 글만 둔다(Hallym MIPS {@code @container} 규칙).
 * 오류 알림에는 쓰지 않는다(캐릭터가 나오므로, CLAUDE.md 8절).
 */
public class EmptyState extends JComponent {
    private final WrapText title;
    private final WrapText body;
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_2, 0));
    private final JLabel character;
    private boolean characterAllowed;

    /**
     * pose가 null이면 캐릭터 없이(좁은 곳이 늘 좁은 패널, 예: Console은 옆 패널의 캐릭터와 겹치지 않게).
     */
    public EmptyState(String title, String body, String pose, JButton... actions) {
        this.title = new WrapText(title, Theme.uiFont(600, Tokens.NOTICE_TITLE), Tokens.NAVY, 1.35f);
        this.body = new WrapText(body, Theme.uiFont(400, Tokens.FONT_UI), Tokens.TEXT_2, 1.5f);
        this.title.setName("empty.title");
        this.body.setName("empty.body");
        buttons.setOpaque(false);
        for (JButton b : actions) {
            buttons.add(b);
        }
        character = pose == null ? null : new JLabel(Characters.icon(pose, Tokens.NOTICE_CHARACTER));
        if (character != null) {
            character.setName("empty.character");
            add(character);
        }
        add(this.title);
        add(this.body);
        add(buttons);
        setOpaque(false);
    }

    public void setTexts(String title, String body) {
        this.title.setText(title);
        this.body.setText(body);
        revalidate();
        repaint();
    }

    public String titleText() {
        return title.getText();
    }

    public String bodyText() {
        return body.getText();
    }

    /** 지금 크기에서 캐릭터가 보이는가(테스트). */
    public boolean characterShown() {
        return character != null && character.isVisible() && characterAllowed;
    }

    @Override
    public void doLayout() {
        Insets in = getInsets();
        int w = getWidth() - in.left - in.right;
        int h = getHeight() - in.top - in.bottom;
        characterAllowed = character != null && w >= Tokens.NOTICE_MIN_WIDTH && h >= Tokens.NOTICE_MIN_HEIGHT;
        int padH = characterAllowed ? Tokens.SPACE_6 : Tokens.SPACE_4;
        int padV = characterAllowed ? Tokens.SPACE_4 : Tokens.SPACE_2;
        int block = Math.min(Tokens.NOTICE_MAX_WIDTH, w) - 2 * padH;
        int charW = 0;
        if (character != null) {
            character.setVisible(characterAllowed);
            if (characterAllowed) {
                charW = character.getPreferredSize().width;
            }
        }
        int sayW = Math.max(40, block - (characterAllowed ? charW + Tokens.NOTICE_GAP : 0));
        int titleH = title.heightFor(sayW);
        int bodyH = body.getText().isEmpty() ? 0 : body.heightFor(sayW);
        int buttonsH = buttons.getComponentCount() == 0 ? 0 : buttons.getPreferredSize().height;
        int sayH = titleH + (bodyH > 0 ? 6 + bodyH : 0) + (buttonsH > 0 ? Tokens.SPACE_3 + buttonsH : 0);
        int charH = characterAllowed ? character.getPreferredSize().height : 0;
        int blockH = Math.max(sayH, charH) + 2 * padV;
        int blockW = sayW + (characterAllowed ? Tokens.NOTICE_GAP + charW : 0) + 2 * padH;
        int x0 = in.left + Math.max(0, (w - blockW) / 2) + padH;
        int y0 = in.top + Math.max(0, (h - blockH) / 2) + padV;
        int sayTop = y0 + (Math.max(sayH, charH) - sayH) / 2;
        title.setBounds(x0, sayTop, sayW, titleH);
        body.setBounds(x0, sayTop + titleH + 6, sayW, bodyH);
        buttons.setBounds(x0, sayTop + titleH + (bodyH > 0 ? 6 + bodyH : 0) + Tokens.SPACE_3, sayW, buttonsH);
        if (characterAllowed) {
            character.setBounds(x0 + sayW + Tokens.NOTICE_GAP, y0 + (Math.max(sayH, charH) - charH) / 2, charW, charH);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        int sayW = 320;
        int h = title.heightFor(sayW) + 6 + body.heightFor(sayW) + 2 * Tokens.SPACE_4;
        return new Dimension(sayW + 2 * Tokens.SPACE_6, h);
    }

    private static final CellRendererPane RENDERER = new CellRendererPane();

    /**
     * 부품으로 넣지 않는 곳(캔버스처럼 스스로 그리는 칸)에 같은 빈 상태를 그린다: area 크기로 배치해 g에 그린다.
     */
    public void paintInto(Graphics g, Component host, Rectangle area) {
        setSize(area.width, area.height);
        doLayout();
        RENDERER.paintComponent(g, this, host instanceof java.awt.Container ? (java.awt.Container) host : null,
                area.x, area.y, area.width, area.height, true);
    }
}
