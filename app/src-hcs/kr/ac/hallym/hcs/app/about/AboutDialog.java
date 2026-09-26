/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.about;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Window;
import java.awt.image.BaseMultiResolutionImage;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * About 창(E-11): 학교 엠블럼(원형 그대로), 이름·버전·한 줄 설명, 바탕이 된 Logisim 2.7.1과 라이선스, 탭으로
 * License(GPL 원문)와 Notices(NOTICE 원문: 서드파티 라이선스, 학교 식별요소는 한림대학교 소유·상업적 사용 금지). 캐릭터는
 * 기본형 한 장을 흰 바탕에 여백을 두고 크기만 줄여 둔다(요소 더하기·색 바꾸기 없음, CLAUDE.md 8절).
 */
public final class AboutDialog {
    static final int CHARACTER = 120;

    private AboutDialog() {
    }

    /** 엠블럼(112px, 고해상도 화면에는 224px 원본). 없으면 null. */
    static ImageIcon emblem() {
        URL one = AppIdentity.logo("emblem-a-navy-112.png");
        URL two = AppIdentity.logo("emblem-a-navy-112@2x.png");
        if (one == null) {
            return null;
        }
        Image base = new ImageIcon(one).getImage();
        if (two == null) {
            return new ImageIcon(base);
        }
        return new ImageIcon(new BaseMultiResolutionImage(base, new ImageIcon(two).getImage()));
    }

    /** 캐릭터 기본형(하람과 하리), 가로 CHARACTER px로 줄임. 없으면 null. */
    static ImageIcon character() {
        URL url = AboutDialog.class.getResource("/kr/ac/hallym/hcs/app/character/haram-hari.png");
        if (url == null) {
            return null;
        }
        Image full = new ImageIcon(url).getImage();
        int w = full.getWidth(null);
        int h = full.getHeight(null);
        if (w <= 0 || h <= 0) {
            return null;
        }
        return new ImageIcon(full.getScaledInstance(CHARACTER, CHARACTER * h / w, Image.SCALE_SMOOTH));
    }

    /** 창 내용(테스트가 글을 읽는다). */
    static JComponent content(Runnable close) {
        JPanel top = new JPanel(new BorderLayout(20, 0));
        top.setBackground(Tokens.WHITE);
        top.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        ImageIcon em = emblem();
        if (em != null) {
            JLabel mark = new JLabel(em);
            mark.setVerticalAlignment(JLabel.TOP);
            top.add(mark, BorderLayout.WEST);
        }
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(AppIdentity.NAME);
        name.setName("about.name");
        name.setFont(new Font(Tokens.UI_FONT, Font.BOLD, 20));
        name.setForeground(Tokens.NAVY);
        JLabel version = new JLabel(Messages.get("about.version", AppIdentity.version()));
        version.setName("about.version");
        version.setForeground(Tokens.TEXT_2);
        text.add(name);
        text.add(Box.createVerticalStrut(4));
        text.add(version);
        text.add(Box.createVerticalStrut(12));
        // 설명 세 문장: 좁으면 줄을 바꾼다(잘리지 않게)
        JTextArea lines = new JTextArea(Messages.get("about.line1") + "\n" + Messages.get("about.line2") + "\n"
                + Messages.get("about.line3"));
        lines.setName("about.lines");
        lines.setEditable(false);
        lines.setFocusable(false);
        lines.setOpaque(false);
        lines.setLineWrap(true);
        lines.setWrapStyleWord(true);
        lines.setFont(new Font(Tokens.UI_FONT, Font.PLAIN, Tokens.FONT_UI));
        lines.setForeground(Tokens.TEXT);
        lines.setBorder(null);
        lines.setColumns(34);
        text.add(lines);
        for (Component c : text.getComponents()) {
            if (c instanceof JComponent) {
                ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }
        top.add(text, BorderLayout.CENTER);
        ImageIcon ch = character();
        if (ch != null) {
            JLabel pic = new JLabel(ch);
            pic.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0)); // 최소 여백
            pic.setVerticalAlignment(JLabel.BOTTOM);
            top.add(pic, BorderLayout.EAST);
        }

        JTabbedPane tabs = new JTabbedPane();
        tabs.setName("about.tabs");
        tabs.addTab(Messages.get("about.license"), textTab(AppIdentity.text("LICENSE")));
        tabs.addTab(Messages.get("about.notices"), textTab(AppIdentity.text("NOTICE")));
        tabs.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 10));
        JButton ok = new JButton(Messages.get("about.close"));
        ok.addActionListener(e -> close.run());
        buttons.add(ok);

        JPanel all = new JPanel(new BorderLayout());
        all.setBackground(Tokens.WHITE);
        all.add(top, BorderLayout.NORTH);
        all.add(tabs, BorderLayout.CENTER);
        all.add(buttons, BorderLayout.SOUTH);
        all.setPreferredSize(new Dimension(720, 560));
        return all;
    }

    private static JComponent textTab(String body) {
        JTextArea area = new JTextArea(body);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Tokens.FONT_SMALL));
        area.setCaretPosition(0);
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return new JScrollPane(area);
    }

    /** Help › About. */
    public static void show(Window owner) {
        JDialog d = new JDialog(owner, Messages.get("about.title", AppIdentity.NAME));
        d.setModal(true);
        d.setContentPane(content(d::dispose));
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }
}
