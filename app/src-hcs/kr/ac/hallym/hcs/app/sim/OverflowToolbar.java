/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.sim;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 넘치지 않는 도구 모음(X-02, D-106). 창이 좁으면 (1) 단추 글자를 숨겨 아이콘만 남기고(Icons Only 자동), (2) 그래도
 * 넘치면 들어가지 않는 항목을 오른쪽 끝 "»" 단추의 메뉴로 보낸다(도구 모음 순서). 우선순위가 높은 항목(Run, 1 Cycle,
 * Reset, Load .s)은 가장 늦게 숨긴다. 창을 넓히면 되돌아온다. 단추 bounds는 늘 도구 모음 안에 완전히 든다.
 */
public final class OverflowToolbar extends JToolBar {
    private static final long serialVersionUID = 1L;
    /** 가장 늦게 숨기는 항목. */
    public static final int KEEP = 1;
    /** 가장 먼저 숨기는 항목(도구 모음 모양 단추 등). */
    public static final int FIRST = -1;

    /** 항목: 부품, 이름 열쇠(메뉴 글자·테스트), 우선순위, 글자를 숨길 수 있는지. */
    public static final class Item {
        final Component comp;
        final String key;
        final int priority;
        final String text;
        boolean shown = true;

        Item(Component comp, String key, int priority, String text) {
            this.comp = comp;
            this.key = key;
            this.priority = priority;
            this.text = text;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final JButton more = new JButton("»");
    private final boolean textPreferred;
    private boolean iconsOnlyNow;

    public OverflowToolbar(boolean textPreferred) {
        this.textPreferred = textPreferred;
        setFloatable(false);
        more.setFocusable(false);
        more.setToolTipText(Messages.get("bar.more"));
        more.setForeground(Tokens.TEXT_2);
        more.addActionListener(e -> menu().show(more, 0, more.getHeight()));
        super.add(more);
        setLayout(new Overflow());
    }

    /** 항목을 더한다. key는 문구 열쇠(메뉴·테스트 이름), 글자가 있는 단추는 넘칠 때 글자를 숨긴다. */
    public Component addItem(Component c, String key, int priority) {
        // 아이콘이 있는 단추만 글자를 숨길 수 있다(아이콘 없는 토글은 글자가 곧 이름)
        String text = c instanceof AbstractButton && ((AbstractButton) c).getIcon() != null
                ? ((AbstractButton) c).getText() : null;
        Item it = new Item(c, key, priority, text);
        items.add(it);
        super.add(c, getComponentCount() - 1); // » 앞에
        return c;
    }

    public void addGap() {
        JToolBar.Separator s = new JToolBar.Separator();
        items.add(new Item(s, null, 0, null));
        super.add(s, getComponentCount() - 1);
    }

    /** 지금 도구 모음에 보이는 항목의 열쇠(구분선 제외). */
    public List<String> shownKeys() {
        List<String> out = new ArrayList<>();
        for (Item it : items) {
            if (it.key != null && it.shown) {
                out.add(it.key);
            }
        }
        return out;
    }

    /** » 메뉴로 간 항목의 열쇠(도구 모음 순서). */
    public List<String> overflowKeys() {
        List<String> out = new ArrayList<>();
        for (Item it : items) {
            if (it.key != null && !it.shown) {
                out.add(it.key);
            }
        }
        return out;
    }

    public boolean iconsOnlyNow() {
        return iconsOnlyNow;
    }

    public JButton moreButton() {
        return more;
    }

    /** » 메뉴: 숨긴 항목을 도구 모음 순서로. 토글은 체크 항목, 선택 상자는 하위 메뉴. */
    JPopupMenu menu() {
        JPopupMenu m = new JPopupMenu();
        for (Item it : items) {
            if (it.key == null || it.shown) {
                continue;
            }
            if (it.comp instanceof JToggleButton) {
                JToggleButton t = (JToggleButton) it.comp;
                JCheckBoxMenuItem mi = new JCheckBoxMenuItem(Messages.get(it.key), t.getIcon(), t.isSelected());
                mi.addActionListener(e -> t.doClick());
                m.add(mi);
            } else if (it.comp instanceof AbstractButton) {
                AbstractButton b = (AbstractButton) it.comp;
                JMenuItem mi = new JMenuItem(Messages.get(it.key), b.getIcon());
                mi.setEnabled(b.isEnabled());
                mi.addActionListener(e -> b.doClick());
                m.add(mi);
            } else if (it.comp instanceof JComboBox) {
                @SuppressWarnings("unchecked")
                JComboBox<Object> cb = (JComboBox<Object>) it.comp;
                JMenu sub = new JMenu(Messages.get(it.key));
                for (int i = 0; i < cb.getItemCount(); i++) {
                    final int idx = i;
                    JCheckBoxMenuItem mi = new JCheckBoxMenuItem(String.valueOf(cb.getItemAt(i)),
                            cb.getSelectedIndex() == i);
                    mi.addActionListener(e -> cb.setSelectedIndex(idx));
                    sub.add(mi);
                }
                m.add(sub);
            }
        }
        return m;
    }

    private static int width(Component c, boolean withText, String text) {
        if (c instanceof AbstractButton && text != null) {
            AbstractButton b = (AbstractButton) c;
            String before = b.getText();
            b.setText(withText ? text : null);
            int w = b.getPreferredSize().width;
            b.setText(before);
            return w;
        }
        return c.getPreferredSize().width;
    }

    /** 배치: 글자 모드 → 아이콘 모드 → 우선순위 낮은 것부터 오른쪽에서 » 메뉴로. */
    private final class Overflow implements LayoutManager {
        static final int GAP = 2;

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            int h = 0;
            for (Item it : items) {
                h = Math.max(h, it.comp.getPreferredSize().height);
            }
            h = Math.max(h, more.getPreferredSize().height);
            // 가로는 아이콘 모드의 최소 몇 개면 족하다: 창 폭을 도구 모음이 늘리지 않게(작은 창에서도 창이 커지지 않음)
            return new Dimension(in.left + in.right + 200, in.top + in.bottom + h + 2 * GAP);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int avail = parent.getWidth() - in.left - in.right;
            int h = parent.getHeight() - in.top - in.bottom;
            boolean text = textPreferred;
            for (Item it : items) {
                it.shown = true;
            }
            if (text && total(true) > avail) {
                text = false; // 1. 글자를 숨긴다
            }
            iconsOnlyNow = !text;
            int moreW = more.getPreferredSize().width + GAP;
            boolean overflow = false;
            while (total(text) + (overflow ? moreW : 0) > avail) {
                // 2. 우선순위가 가장 낮은 것 중 가장 오른쪽 항목을 숨긴다
                Item victim = null;
                for (Item it : items) {
                    if (it.key != null && it.shown && (victim == null || it.priority <= victim.priority)) {
                        victim = it;
                    }
                }
                if (victim == null) {
                    break;
                }
                victim.shown = false;
                overflow = true;
            }
            // 숨긴 항목 사이의 구분선: 양옆이 모두 안 보이면 같이 숨긴다
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                if (it.key == null) {
                    boolean leftShown = false;
                    boolean rightShown = false;
                    for (int j = i - 1; j >= 0; j--) {
                        if (items.get(j).key != null) {
                            leftShown = items.get(j).shown;
                            break;
                        }
                    }
                    for (int j = i + 1; j < items.size(); j++) {
                        if (items.get(j).key != null) {
                            rightShown = items.get(j).shown;
                            break;
                        }
                    }
                    it.shown = leftShown && rightShown;
                }
            }
            int x = in.left + GAP;
            for (Item it : items) {
                if (it.comp instanceof AbstractButton && it.text != null) {
                    ((AbstractButton) it.comp).setText(text ? it.text : null);
                }
                if (!it.shown) {
                    it.comp.setVisible(false);
                    continue;
                }
                int w = it.comp.getPreferredSize().width;
                int ch = Math.min(h, it.comp.getPreferredSize().height);
                it.comp.setVisible(true);
                it.comp.setBounds(x, in.top + (h - ch) / 2, w, ch);
                x += w + GAP;
            }
            more.setVisible(overflow);
            if (overflow) {
                int w = more.getPreferredSize().width;
                int ch = Math.min(h, more.getPreferredSize().height);
                more.setBounds(in.left + avail - w, in.top + (h - ch) / 2, w, ch);
            }
        }

        private int total(boolean text) {
            int sum = GAP;
            for (Item it : items) {
                if (it.shown) {
                    sum += width(it.comp, text, it.text) + GAP;
                }
            }
            return sum;
        }
    }

    /** 테스트: 보이는 항목의 부품(순서대로). */
    public List<Component> shownComponents() {
        List<Component> out = new ArrayList<>();
        for (Item it : items) {
            if (it.key != null && it.shown) {
                out.add(it.comp);
            }
        }
        return Collections.unmodifiableList(out);
    }
}
