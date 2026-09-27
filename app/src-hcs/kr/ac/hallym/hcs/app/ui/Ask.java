/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.ui;

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

import kr.ac.hallym.hcs.app.theme.Theme;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 묻는 대화상자 한 부품(Z-10, Hallym MIPS {@code panels/ask.ts}·{@code .modal.ask}와 같은 모양): 창 전체를 navy로
 * 어둡게 덮고(튜토리얼 카드 위까지), 가운데 카드에 캐릭터(왼쪽), 질문인 제목, 필요하면 "File: 이름" 한 줄, 본문, 보조 단추(왼쪽)와
 * 주 단추(오른쪽). 카드 바깥을 눌러도 닫히지 않는다. Esc는 보조, Enter는 주 단추. 저장·닫기·예제 다른 이름 저장·jar 복사·
 * 튜토리얼 그만두기 같은 모든 확인에 쓴다. 진단 메시지와 오류 알림에는 쓰지 않는다(캐릭터가 나오므로, CLAUDE.md 8절).
 */
public final class Ask {
    /** 창의 겹 순서: 튜토리얼 덮개(TUTORIAL_LAYER)보다 위. */
    public static final Integer LAYER = JLayeredPane.DRAG_LAYER + 20;
    public static final Integer TUTORIAL_LAYER = JLayeredPane.DRAG_LAYER + 10;

    /** 무엇을 물을지. */
    public static final class Question {
        final String title;
        final String body;
        final String file;
        final String primary;
        final String secondary;
        String pose = "haram";

        /** secondary가 null이면 단추 하나(알림). */
        public Question(String title, String body, String file, String primary, String secondary) {
            this.title = title;
            this.body = body;
            this.file = file;
            this.primary = primary;
            this.secondary = secondary;
        }

        public Question pose(String p) {
            pose = p;
            return this;
        }
    }

    /** 테스트가 답을 미리 정한다(null: 실제로 묻는다). */
    static volatile Boolean testAnswer;

    private Ask() {
    }

    /**
     * 창 안에 묻고 답을 기다린다(GUI 스레드에서 부르면 이차 이벤트 루프로 기다린다). true = 주 단추. 창이 없으면 false.
     */
    public static boolean ask(Component owner, Question q) {
        if (testAnswer != null) {
            return testAnswer;
        }
        JRootPane root = owner == null ? null : SwingUtilities.getRootPane(owner);
        if (root == null) {
            return false;
        }
        boolean[] answer = {false};
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        Overlay overlay = show(root, q, a -> {
            answer[0] = a;
            loop.exit();
        });
        if (!loop.enter()) {
            // 이차 루프를 못 들어갔다(드묾): 닫고 거절로 본다
            overlay.close();
        }
        return answer[0];
    }

    /** 기다리지 않고 띄운다. 답을 고르면 done을 부르고 닫힌다. */
    public static Overlay show(JRootPane root, Question q, Consumer<Boolean> done) {
        Overlay o = new Overlay(root, q, done);
        JLayeredPane lp = root.getLayeredPane();
        o.setBounds(0, 0, lp.getWidth(), lp.getHeight());
        lp.add(o, LAYER);
        o.install();
        lp.revalidate();
        lp.repaint();
        return o;
    }

    /** 창을 덮는 막과 카드. */
    public static final class Overlay extends JComponent {
        private final JRootPane root;
        private final Consumer<Boolean> done;
        private final Card card;
        private final KeyEventDispatcher keys;
        private final java.awt.event.ComponentListener follow;
        private final Component focusBefore;
        private boolean closed;

        Overlay(JRootPane root, Question q, Consumer<Boolean> done) {
            this.root = root;
            this.done = done;
            this.focusBefore = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            setName("ask.overlay");
            setOpaque(false);
            setLayout(null);
            card = new Card(q, this::answer);
            add(card);
            // 카드 바깥 누르기·끌기·굴리기는 여기서 끝난다(뒤로 가지 않고, 닫지도 않는다)
            MouseAdapter swallow = new MouseAdapter() {
            };
            addMouseListener(swallow);
            addMouseMotionListener(swallow);
            addMouseWheelListener(swallow);
            enableEvents(AWTEvent.MOUSE_EVENT_MASK);
            keys = e -> {
                if (closed || e.getComponent() == null || !SwingUtilities.isDescendingFrom(e.getComponent(), root)) {
                    return false; // 다른 창의 키
                }
                if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    answer(card.secondary == null); // 보조 단추가 있으면 보조, 알림이면 확인
                    return true;
                }
                if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    answer(true);
                    return true;
                }
                // 카드 밖으로 가는 키(메뉴 단축키, 캔버스 단축키)는 먹는다
                return !SwingUtilities.isDescendingFrom(e.getComponent(), card);
            };
            follow = new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    setBounds(0, 0, root.getLayeredPane().getWidth(), root.getLayeredPane().getHeight());
                    doLayout();
                }
            };
        }

        void install() {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keys);
            root.getLayeredPane().addComponentListener(follow);
            doLayout();
            card.primary.requestFocusInWindow();
        }

        /** 카드(테스트). */
        public Card card() {
            return card;
        }

        void answer(boolean a) {
            if (closed) {
                return;
            }
            close();
            done.accept(a);
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keys);
            root.getLayeredPane().removeComponentListener(follow);
            JLayeredPane lp = root.getLayeredPane();
            lp.remove(this);
            lp.revalidate();
            lp.repaint();
            if (focusBefore != null && focusBefore.isShowing()) {
                focusBefore.requestFocusInWindow();
            }
        }

        @Override
        public void doLayout() {
            Dimension d = card.getPreferredSize();
            int w = Math.min(d.width, getWidth() - 32);
            int h = card.heightFor(w);
            card.setBounds((getWidth() - w) / 2, Math.max(Tokens.SPACE_4, (getHeight() - h) / 2), w, h);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setColor(new Color(Tokens.NAVY.getRed(), Tokens.NAVY.getGreen(), Tokens.NAVY.getBlue(),
                    Math.round(255 * Tokens.BACKDROP_ALPHA / 100f)));
            g.fillRect(0, 0, getWidth(), getHeight());
            // 카드 그림자(Hallym MIPS: 0 16px 48px rgba(0,32,91,.20))를 몇 겹으로 흉내 낸다
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            java.awt.Rectangle c = card.getBounds();
            float r = 2f * Tokens.DIALOG_RADIUS;
            for (int i = 12; i >= 1; i -= 2) {
                g.setColor(new Color(0, 32, 91, 8));
                g.fill(new RoundRectangle2D.Float(c.x - i, c.y - i + 8, c.width + 2f * i, c.height + 2f * i, r + i, r + i));
            }
            g.dispose();
        }
    }

    /** 카드: 캐릭터(왼쪽) · 제목 · (File) · 본문 · 단추. */
    public static final class Card extends JComponent {
        static final int PAD_V = 20;
        static final int PAD_H = 22;
        final JLabel character;
        final WrapText title;
        final WrapText file;
        final WrapText body;
        public final JButton primary;
        public final JButton secondary;

        Card(Question q, Consumer<Boolean> answer) {
            setName("ask.card");
            setOpaque(false);
            setLayout(null);
            setFocusCycleRoot(true);
            character = new JLabel(Characters.icon(q.pose, Tokens.ASK_CHARACTER));
            title = new WrapText(q.title, Theme.uiFont(600, Tokens.ASK_TITLE), Tokens.NAVY, 1.35f);
            title.setName("ask.title");
            file = q.file == null ? null : new WrapText("File: " + q.file, Theme.uiFont(400, Tokens.FONT_SMALL),
                    Tokens.TEXT_MUTED, 1.4f);
            body = new WrapText(q.body == null ? "" : q.body, Theme.uiFont(400, Tokens.FONT_UI), Tokens.TEXT_2, 1.55f);
            body.setName("ask.body");
            primary = new JButton(q.primary);
            primary.setName("ask.primary");
            primary.putClientProperty("JButton.buttonType", null);
            primary.addActionListener(e -> answer.accept(true));
            add(character);
            add(title);
            if (file != null) {
                file.setName("ask.file");
                add(file);
            }
            add(body);
            add(primary);
            if (q.secondary != null) {
                secondary = new JButton(q.secondary);
                secondary.setName("ask.secondary");
                secondary.addActionListener(e -> answer.accept(false));
                add(secondary);
            } else {
                secondary = null;
            }
            // 주 단추는 파랑 채움(FlatLaf 기본 단추 모양)
            primary.setBackground(Tokens.BLUE);
            primary.setForeground(Tokens.WHITE);
        }

        int textWidth(int w) {
            return w - 2 * PAD_H - character.getPreferredSize().width - Tokens.ASK_GAP;
        }

        int heightFor(int w) {
            int tw = textWidth(w);
            int text = title.heightFor(tw) + (file != null ? 4 + file.heightFor(tw) : 0)
                    + (body.getText().isEmpty() ? 0 : 6 + body.heightFor(tw));
            int top = Math.max(character.getPreferredSize().height, text);
            return PAD_V + top + Tokens.SPACE_4 + Tokens.BUTTON_HEIGHT + PAD_V;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Tokens.ASK_WIDTH, heightFor(Tokens.ASK_WIDTH));
        }

        @Override
        public void doLayout() {
            int w = getWidth();
            int tw = textWidth(w);
            Dimension cd = character.getPreferredSize();
            int th = title.heightFor(tw);
            int fh = file != null ? file.heightFor(tw) : 0;
            int bh = body.getText().isEmpty() ? 0 : body.heightFor(tw);
            int text = th + (file != null ? 4 + fh : 0) + (bh > 0 ? 6 + bh : 0);
            int top = Math.max(cd.height, text);
            character.setBounds(PAD_H, PAD_V + (top - cd.height) / 2, cd.width, cd.height);
            int x = PAD_H + cd.width + Tokens.ASK_GAP;
            int y = PAD_V + (top - text) / 2;
            title.setBounds(x, y, tw, th);
            y += th;
            if (file != null) {
                file.setBounds(x, y + 4, tw, fh);
                y += 4 + fh;
            }
            body.setBounds(x, y + 6, tw, bh);
            int by = PAD_V + top + Tokens.SPACE_4;
            Dimension pd = primary.getPreferredSize();
            primary.setBounds(w - PAD_H - pd.width, by, pd.width, Tokens.BUTTON_HEIGHT);
            if (secondary != null) {
                Dimension sd = secondary.getPreferredSize();
                secondary.setBounds(PAD_H, by, sd.width, Tokens.BUTTON_HEIGHT);
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float r = 2f * Tokens.DIALOG_RADIUS;
            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, getWidth() - 1f, getHeight() - 1f, r, r);
            g.setColor(Tokens.WHITE);
            g.fill(shape);
            g.setColor(Tokens.BORDER);
            g.setStroke(new BasicStroke(1f));
            g.draw(shape);
            g.dispose();
        }
    }
}
