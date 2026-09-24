/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tutorial;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.cburch.logisim.proj.Projects;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.Settings;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 첫 실행 안내(#23, PLAN.md 2장). 몇 장의 카드로 부품 놓기, 선 잇기, 값 바꾸기, MIPS 부품, 저장을 보여 준다.
 * 처음 실행할 때 한 번 뜨고, 도움말 메뉴에서 다시 볼 수 있다. 캐릭터는 첫 장과 마지막 장에만 흰 바탕에
 * 원형 그대로(크기만 줄여) 둔다(CLAUDE.md 8절).
 */
public final class QuickStart {
    static final String SEEN = "quickstart.seen";
    static final String IMAGE_DIR = "/kr/ac/hallym/hcs/app/character/";
    static final int IMAGE_SIZE = 160;

    /** 카드: 제목·본문 키와 캐릭터 그림(없으면 null). */
    static final class Page {
        final String key;
        final String image;

        Page(String key, String image) {
            this.key = key;
            this.image = image;
        }
    }

    static final List<Page> PAGES = new ArrayList<>();
    static {
        PAGES.add(new Page("quickstart.welcome", "haram-hari-greeting.png"));
        PAGES.add(new Page("quickstart.place", null));
        PAGES.add(new Page("quickstart.wire", null));
        PAGES.add(new Page("quickstart.poke", null));
        PAGES.add(new Page("quickstart.mips", null));
        PAGES.add(new Page("quickstart.save", "haram-hari-ok.png"));
    }

    private QuickStart() {
    }

    /** 처음 실행이면 맨 앞 창 위에 띄운다. */
    public static void showOnFirstRun() {
        if (markSeen(Settings.get())) {
            SwingUtilities.invokeLater(() -> show(Projects.getTopFrame()));
        }
    }

    /** 처음이면 본 것으로 적고 true. 저장하지 못하면 다음에 또 보일 뿐이다. */
    static boolean markSeen(Settings s) {
        if (s.getBoolean(SEEN, false)) {
            return false;
        }
        s.set(SEEN, true);
        try {
            s.save();
        } catch (IOException e) {
            // 무시
        }
        return true;
    }

    public static void show(Window owner) {
        JDialog d = create(owner);
        d.setVisible(true);
    }

    static JDialog create(Window owner) {
        JDialog d = new JDialog(owner, Messages.get("quickstart.title"));
        d.setModal(false);
        CardLayout cards = new CardLayout();
        JPanel deck = new JPanel(cards);
        deck.setBackground(Tokens.WHITE);
        for (int i = 0; i < PAGES.size(); i++) {
            deck.add(card(PAGES.get(i), i), Integer.toString(i));
        }

        JLabel pageNo = new JLabel();
        pageNo.setForeground(Tokens.TEXT_2);
        JButton prev = new JButton(Messages.get("quickstart.prev"));
        JButton next = new JButton(Messages.get("quickstart.next"));
        JButton close = new JButton(Messages.get("quickstart.close"));
        int[] at = {0};
        Runnable update = () -> {
            cards.show(deck, Integer.toString(at[0]));
            pageNo.setText(Messages.get("quickstart.page", at[0] + 1, PAGES.size()));
            prev.setEnabled(at[0] > 0);
            next.setVisible(at[0] < PAGES.size() - 1);
            close.setVisible(at[0] == PAGES.size() - 1);
            JRootPane root = d.getRootPane();
            root.setDefaultButton(at[0] < PAGES.size() - 1 ? next : close);
        };
        prev.addActionListener(e -> {
            at[0]--;
            update.run();
        });
        next.addActionListener(e -> {
            at[0]++;
            update.run();
        });
        close.addActionListener(e -> d.dispose());

        JCheckBox again = new JCheckBox(Messages.get("quickstart.showAgain"),
                !Settings.get().getBoolean(SEEN, false));
        again.setOpaque(false);
        again.addActionListener(e -> {
            Settings.get().set(SEEN, !again.isSelected());
            try {
                Settings.get().save();
            } catch (IOException ex) {
                // 다음 실행 때 적용되지 않을 뿐이다
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.SPACE_2, 0));
        buttons.setOpaque(false);
        buttons.add(prev);
        buttons.add(next);
        buttons.add(close);
        JPanel bottom = new JPanel(new BorderLayout(Tokens.SPACE_2, 0));
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Tokens.BORDER),
                BorderFactory.createEmptyBorder(Tokens.SPACE_3, Tokens.SPACE_4, Tokens.SPACE_3, Tokens.SPACE_4)));
        bottom.setBackground(Tokens.WINDOW);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_3, 0));
        left.setOpaque(false);
        left.add(pageNo);
        left.add(again);
        bottom.add(left, BorderLayout.WEST);
        bottom.add(buttons, BorderLayout.EAST);

        d.getContentPane().setLayout(new BorderLayout());
        d.getContentPane().add(deck, BorderLayout.CENTER);
        d.getContentPane().add(bottom, BorderLayout.SOUTH);
        update.run();
        d.setSize(new Dimension(560, 400));
        d.setLocationRelativeTo(owner);
        return d;
    }

    private static JPanel card(Page page, int index) {
        JPanel p = new JPanel(new BorderLayout(Tokens.SPACE_6, 0));
        p.setBackground(Tokens.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_6, Tokens.SPACE_6, Tokens.SPACE_6, Tokens.SPACE_6));
        JLabel title = new JLabel(Messages.get(page.key + ".title"));
        title.setForeground(Tokens.NAVY);
        title.setFont(title.getFont().deriveFont(Font.BOLD, (float) Tokens.FONT_TITLE + 1));
        // HTML 라벨은 폭 계산이 글꼴 배율에 따라 어긋나 글이 잘린다. 줄바꿈 텍스트 영역을 쓴다.
        JTextArea body = new JTextArea(Messages.get(page.key + ".body"));
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setEditable(false);
        body.setFocusable(false);
        body.setOpaque(false);
        body.setBorder(null);
        body.setForeground(Tokens.TEXT);
        body.setFont(title.getFont().deriveFont(Font.PLAIN, (float) Tokens.FONT_TITLE));
        JPanel text = new JPanel(new BorderLayout(0, Tokens.SPACE_3));
        text.setOpaque(false);
        text.add(title, BorderLayout.NORTH);
        text.add(body, BorderLayout.CENTER);
        p.add(text, BorderLayout.CENTER);
        ImageIcon icon = page.image == null ? null : character(page.image);
        if (icon != null) {
            JLabel img = new JLabel(icon);
            img.setVerticalAlignment(SwingConstants.TOP);
            JPanel holder = new JPanel(new BorderLayout());
            holder.setOpaque(false);
            holder.add(img, BorderLayout.NORTH);
            holder.add(Box.createHorizontalStrut(IMAGE_SIZE), BorderLayout.SOUTH);
            p.add(holder, BorderLayout.EAST);
        }
        p.setName("page" + index);
        return p;
    }

    /** 캐릭터 PNG를 비율 그대로 줄인다. 색·선은 바꾸지 않는다. */
    static ImageIcon character(String name) {
        try (InputStream in = QuickStart.class.getResourceAsStream(IMAGE_DIR + name)) {
            if (in == null) {
                return null;
            }
            BufferedImage src = ImageIO.read(in);
            int w = IMAGE_SIZE;
            int h = Math.round((float) src.getHeight() * IMAGE_SIZE / src.getWidth());
            return new ImageIcon(src.getScaledInstance(w, h, Image.SCALE_SMOOTH));
        } catch (IOException e) {
            return null;
        }
    }
}
